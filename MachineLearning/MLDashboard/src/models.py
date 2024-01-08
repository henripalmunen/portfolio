import pandas as pd

import numpy as np
from fastai.tabular.all import *
from xgboost import XGBClassifier, XGBRegressor
from sklearn.metrics import mean_absolute_error
from sklearn.preprocessing import StandardScaler, LabelEncoder
from sklearn.ensemble import RandomForestClassifier, RandomForestRegressor
from sklearn.naive_bayes import GaussianNB
from metrics import MetricsParser


class DataSetup:
    def __init__(self, cwd: str):
        self.cwd = cwd
        target_features = list(pd.read_csv(cwd + "target.csv", sep=chr(9)))
        cat_features = list(pd.read_csv(cwd + "categorical.csv", sep=chr(9)))
        cont_features = list(pd.read_csv(cwd + "continuous.csv", sep=chr(9)))
        model_names = list(pd.read_csv(cwd + "algorithm.csv", sep=chr(9)))
        assert len(target_features) == 1
        self.target_features = target_features
        self.cat_features = cat_features
        self.cont_features = cont_features
        self.model_names = model_names
        self.model_name = model_names[0]  # Only train one model at a time
        self.features = (cat_features, cont_features, target_features)
        # If feature lists overlap, raise error
        for i_1, i_2 in [(0, 1), (0, 2), (1, 2)]:
            if bool(set(self.features[i_1]) & set(self.features[i_2])):
                names = ("cat_features", "cont_features", "target_features")
                raise Exception(
                    f"Error: Feature lists <{names[i_1]}> and <{names[i_2]}> overlap"
                )

        self.y_std: float = None
        self.y_mean: float = None

    def load_data(self, max_rows=5000) -> "tuple[pd.DataFrame, pd.DataFrame]":
        """
        Loads and returns all rows (or as determined by max_rows, default 50000). Doesn't store any data.
        """
        exportCSVFull = pd.read_csv(self.cwd + "export.csv")
        illegal_features = list(
            filter(
                lambda f: f not in exportCSVFull.columns,
                self.cat_features + self.cont_features + self.target_features,
            )
        )

        if illegal_features:
            raise Exception(
                "The following features not found in export file: "
                + ", ".join(illegal_features)
            )

        if exportCSVFull.shape[0] > max_rows:
            exportCSVFull = exportCSVFull.sample(n=max_rows, random_state=42)

        return exportCSVFull


def split_data(data: pd.DataFrame, test_data_percentage=0.15):
    """
    Splits data in two parts randomly by test_data_percentage (default 0.15)
    """
    rows = data.shape[0]
    training_data, test_data = tuple(
        pd.DataFrame(arr)
        for arr in np.split(  # Halves array
            data.sample(n=rows, random_state=42),
            [int(rows * (1 - test_data_percentage))],
        )
    )
    return training_data, test_data


def dict_values_to_str(d: dict):
    def val_to_str(val: Any) -> str:
        if val is None:
            return ""
        else:
            try:
                return str(val)
            except:
                return ""

    ret_val = dict(
        [
            (hp_key, val_to_str(hp_value))
            for hp_key, hp_value in zip(d.keys(), d.values())
        ]
    )
    assert sum(val is None for val in ret_val.values()) == 0
    return ret_val


class Models:
    """
    Metrics getter functions
    """

    def getters(dsu: DataSetup, data: pd.DataFrame):
        return {
            "Random Forest Classification": lambda: Models.sklearner(
                dsu=dsu,
                y_is_cat=True,
                model=RandomForestClassifier(n_estimators=100, random_state=42),
                data=data,
            ),
            "Random Forest Regression": lambda: Models.sklearner(
                dsu=dsu,
                y_is_cat=False,
                model=RandomForestRegressor(n_estimators=100, random_state=42),
                data=data,
            ),
            "XGBoost Classification": lambda: Models.sklearner(
                dsu=dsu,
                y_is_cat=True,
                model=XGBClassifier(random_state=42),
                do_one_hot=True,
                data=data,
            ),
            "XGBoost Regression": lambda: Models.sklearner(
                dsu=dsu,
                model=XGBRegressor(
                    tree_method="hist", eval_metric=mean_absolute_error, random_state=42
                ),
                y_is_cat=False,
                data=data,
            ),
            "Gaussian Naive Bayes Classification": lambda: Models.sklearner(
                dsu=dsu, y_is_cat=True, model=GaussianNB(), data=data
            ),
            "FastAI Tabular Classification": lambda: Models.fastai_tabular(
                dsu=dsu, y_is_cat=True, data=data
            ),
            "FastAI Tabular Regression": lambda: Models.fastai_tabular(
                dsu=dsu, y_is_cat=False, data=data
            ),
        }

    def fastai_tabular(
        dsu: DataSetup, y_is_cat: bool, data: pd.DataFrame
    ) -> "dict[str, list[str]]":
        cat_features, cont_features, target_features = dsu.features
        # data = dsu.load_data()
        cont_to_fill = cont_features
        if not y_is_cat:
            cont_to_fill = cont_to_fill + target_features
        prep_df(df=data, cont=cont_to_fill)

        training_data, test_data = split_data(data=data)

        target_feature = target_features[0]
        y_block = RegressionBlock
        if y_is_cat:
            y_block = CategoryBlock

        dls = TabularPandas(
            df=training_data,
            procs=[FillMissing, Categorify, Normalize],
            cat_names=cat_features,
            cont_names=cont_features,
            y_names=target_feature,
            y_block=y_block,
            splits=RandomSplitter(valid_pct=0.2)(range_of(training_data)),
        ).dataloaders(bs=64)

        learn = tabular_learner(dls, metrics=accuracy, default_cbs=True)

        learn.fit_one_cycle(
            8,
            cbs=EarlyStoppingCallback(monitor="valid_loss", min_delta=0.01, patience=3),
        )

        test_data.drop([target_feature], axis=1)

        _, targ, test_pred_decoded = learn.get_preds(
            dl=learn.dls.test_dl(test_data), with_decoded=True
        )
        pred = torch.tensor([[num] for num in test_pred_decoded])

        if y_is_cat:
            return MetricsParser.parse_classification(pred=pred, targ=targ)
        else:
            return MetricsParser.parse_regression(pred=pred, targ=targ)

    def sklearner(
        dsu: DataSetup, model: Any, y_is_cat: bool, data: pd.DataFrame, do_one_hot=False
    ) -> "dict[str, list[str]]":
        # data: pd.DataFrame = dsu.load_data()
        cat_features, cont_features, target_features = dsu.features
        target_feature = target_features[0]
        for c in data.columns:
            if c != target_feature and c not in cat_features and c not in cont_features:
                data.drop([c], axis=1, inplace=True)
        cont_to_fill = cont_features
        if not y_is_cat:
            cont_to_fill += target_features
        prep_df(df=data, cont=cont_to_fill)

        if do_one_hot:
            for col in cat_features:
                s = data[col].unique()
                # Create a One Hot Dataframe with 1 row for each unique value
                one_hot_df = pd.get_dummies(s, prefix="%s_" % col)
                one_hot_df[col] = s
                pre_len = len(data)
                # Merge the one hot columns
                data = data.merge(one_hot_df, on=[col], how="left")
                data.drop(
                    [col], axis=1, inplace=True
                )  # Drop the col, it's no longer needed
                assert len(data) == pre_len

        training_data, test_data = split_data(data=data)

        x_train = training_data.drop([target_feature], axis=1)
        x_test = test_data.drop([target_feature], axis=1)
        y_train = training_data[target_feature]
        y_test = test_data[target_feature]

        scaler = StandardScaler()
        x_train = scaler.fit_transform(x_train)
        x_test = scaler.transform(x_test)

        mdl = model
        mdl.fit(x_train, y_train)
        xgbpreds = mdl.predict(x_test)
        pred = torch.tensor([[num] for num in xgbpreds])
        targ = torch.tensor([[num] for num in list(y_test)])

        hpd: "dict[str, str]" = dict_values_to_str(mdl.get_params())

        metrics: "dict[str, str]" = None
        if y_is_cat:
            metrics = MetricsParser.parse_classification(pred=pred, targ=targ)
        else:
            metrics = MetricsParser.parse_regression(pred=pred, targ=targ)
        # print(dict(metrics, **hpd))
        return dict(metrics, **hpd)


def prep_df(df: pd.DataFrame, cont: "list[str]"):
    """
    Fills missing values (median for continuous columns, empty string for other) and ensure that continuous columns as processed as numeric
    """

    missing_value_indicators = ["-"]
    # Remove dashes
    for f in list(df.columns):
        for mvi in missing_value_indicators:
            df[f].replace([mvi], None, inplace=True)

    for f in cont:
        df[f] = df[f].to_frame().apply(lambda x: x.fillna(str(x.median())), axis=0)
        df[f] = pd.to_numeric(df[f])
    # Exception(f"Exception occurred while trying to fill missing values in continuous column <{current}>. Make sure the column does not contain NaN-values.")
    for f in list(df.columns):
        if f not in cont:
            df[f] = df[f].to_frame().apply(lambda x: x.fillna(""), axis=0)
            df[f] = df[f].astype("category").cat.codes

    if df.isnull().values.any():
        raise Exception(f"Filling missing values failed")
