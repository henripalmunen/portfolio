import inspect
import pandas as pd
import numpy as np
from fastai.tabular.all import *
import sklearn.metrics as skm


class MetricsParser:
    def __init__(self):
        pass

    def parse_regression(pred: Tensor, targ: Tensor) -> "dict[str, list[str]]":
        return dict(
            RegressionMetrics(pred, targ).__get_all__(),
            **ClassificationMetrics(pred, targ).__get_empty__()
        )

    def parse_classification(pred: Tensor, targ: Tensor) -> "dict[str, list[str]]":
        return dict(
            RegressionMetrics(pred, targ).__get_empty__(),
            **ClassificationMetrics(pred, targ).__get_all__()
        )


class Metrics:
    def __init__(self, pred: Tensor, targ: Tensor):
        self.pred = pred
        self.targ = targ

    def __get_all__(self) -> "dict[str, list[str]]":
        # Get all methods that do not have a name starting with "__"
        members = filter(
            lambda tup: tup[0][:2] != "__",
            inspect.getmembers(self, predicate=inspect.isroutine),
        )
        def string_wrap(m: Any):
            try:
                return str(m())
            except Exception as e:
                return str(e).replace(",", "")
        ret_value = dict([(name, string_wrap(method)) for name, method in members])
        return ret_value

    def __get_empty__(self) -> "dict[str, list[str]]":
        members = filter(
            lambda tup: tup[0][:2] != "__",
            inspect.getmembers(self, predicate=inspect.isroutine),
        )
        ret_value = dict([(name, "") for name, method in members])
        return ret_value  


class ClassificationMetrics(Metrics):
    def ConfusionMatrix(self):
        # zip predictions with targets
        zipped = list(
            zip([l.tolist()[0] for l in self.pred], [l.tolist()[0] for l in self.targ])
        )
        criteria = [
            ("tp", (1, 1)),
            ("tn", (0, 0)),
            ("fp", (1, 0)),
            ("fn", (0, 1)),
        ]
        return ";".join(
            [f"{key}:{sum(pair == t_pair for pair in zipped)}" for key, t_pair in criteria]
        )

    def Accuracy(self):
        return skm.accuracy_score(self.targ, self.pred)

    def Precision(self):
        return skm.precision_score(self.targ, self.pred)

    def Recall(self):
        return skm.recall_score(self.targ, self.pred)

    def F1(self):
        return skm.f1_score(self.targ, self.pred)

    def AUC(self):
        import matplotlib.pyplot as plt
        y_pred_proba = self.pred
        y_test = self.targ
        fpr, tpr, _ = skm.roc_curve(y_test,  y_pred_proba)
        auc = skm.roc_auc_score(y_test, y_pred_proba)
        #plt.plot(fpr,tpr,label="data 1, auc="+str(auc))
        #plt.legend(loc=4)
        #plt.show()
        return skm.roc_auc_score(self.targ, self.pred)
    
    


class RegressionMetrics(Metrics):
    """
    Initialized with de-normalized values
    """

    def MSE(self):
        return skm.mean_squared_error(self.targ, self.pred, squared=True)

    def RMSE(self):
        return skm.mean_squared_error(self.targ, self.pred, squared=False)

    def MAE(self):
        return skm.mean_absolute_error(self.targ, self.pred)
    
    def R2(self):
        return skm.r2_score(self.targ, self.pred)

    


@delegates()
class TstLearner(Learner):
    def __init__(self, dls=None, model=None, **kwargs):
        self.pred, self.xb, self.yb = None, None, None


def compute_val(met, x1, x2):
    met.reset()
    vals = [0, 6, 15, 20]
    learn = TstLearner()
    for i in range(3):
        learn.pred, learn.yb = x1[vals[i] : vals[i + 1]], (x2[vals[i] : vals[i + 1]],)
        met.accumulate(learn)
    return met.value
