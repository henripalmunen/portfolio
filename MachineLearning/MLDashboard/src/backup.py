#! /usr/bin/env python3
import argparse
import json
import logging
import logging.config
import os
import sys
import time
from concurrent import futures
import pandas as pd
import numpy as np
from fastai.tabular.all import *
import itertools
import torch


# Add Generated folder to module path.
PARENT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.append(os.path.join(PARENT_DIR, "generated"))

import ServerSideExtension_pb2 as SSE
import grpc
from scripteval import ScriptEval
from ssedata import FunctionType

_ONE_DAY_IN_SECONDS = 60 * 60 * 24
_MINFLOAT = float("-inf")

procs = [FillMissing, Categorify, Normalize]


class TstLearner:
    def __init__(self):
        self.pred, self.y = None, None


def compute_val(met, x1, x2):
    met.reset()
    vals = [0, 6, 15, 20]
    learn = TstLearner()
    for i in range(3):
        learn.pred, learn.y = x1[vals[i] : vals[i + 1]], x2[vals[i] : vals[i + 1]]
        met.accumulate(learn)
    return met.value


class DataSetup:
    def __init__(self, cwd: str):
        self.cwd = cwd
        target_features = list(pd.read_csv(cwd + "target.csv", sep=chr(9)))
        cat_features = list(pd.read_csv(cwd + "categorical.csv", sep=chr(9)))
        cont_features = list(pd.read_csv(cwd + "continuous.csv", sep=chr(9)))
        assert all((tf in cat_features) for tf in target_features) or (
            len(target_features) == 1 and target_features[0] in cont_features
        )
        y_block: DataBlock = None
        y_is_cat = target_features[0] in cat_features
        # Remove target(s) from cat_features or cont_features depending on type
        if y_is_cat:
            cat_features = list(
                itertools.filterfalse(lambda x: x in target_features, cat_features)
            )
            y_block = DataBlock(blocks=[CategoryBlock for _ in target_features])
        else:
            cont_features = list(
                itertools.filterfalse(lambda x: x == target_features[0], cont_features)
            )
            y_block = RegressionBlock
        print(len(cat_features), len(cont_features), len(target_features))
        self.target_features = target_features
        self.cat_features = cat_features
        self.cont_features = cont_features
        self.features = (target_features, cat_features, cont_features)
        self.y_block = y_block
        self.y_is_cat = y_is_cat
        self.y_std = None
        self.y_mean = None

    def load_data(
        self, test_data_percentage=0.15, max_rows=200000
    ) -> "tuple":
        exportCSVFull = pd.read_csv(self.cwd + "export.csv")
        rows = min(exportCSVFull.shape[0], max_rows)
        training_data, test_data = tuple(
            pd.DataFrame(arr)
            for arr in np.split(
                exportCSVFull.sample(n=rows), [int(rows * (1 - test_data_percentage))]
            )
        )
        test_close(
            test_data.shape[0] / (training_data.shape[0] + test_data.shape[0]),
            test_data_percentage,
            eps=0.02,
        )
        if self.y_is_cat:
            self.y_std = training_data[self.target_features[0]].std()
            self.y_mean = training_data[self.target_features[0]].mean()
        return (training_data, test_data)
    

class metricsJSONThing:
    pass
def get_metrics() -> metricsJSONThing:
    pass


class ClassificationMetrics:
    def __init__(self, pred: tensor, targ: tensor):
        self.pred = pred
        self.targ = targ

    def get_confusion_matrix(learner: Learner):
        return str(
            ClassificationInterpretation.from_learner(learner).confusion_matrix()
        )


class RegressionMetrics:
    def __init__(self, pred: tensor, targ: tensor, std, mean):
        self.pred = torch.tensor(list(x * std + mean for x in pred))
        self.targ = targ
        self.std = std
        self.mean = mean

    def mse(self):
        return mse(self.pred, self.targ)

    def rmse(self):
        return compute_val(rmse, self.pred, self.targ)


class ExtensionService(SSE.ConnectorServicer):
    """
    A simple SSE-plugin created for the Column Operations example.
    """

    def __init__(self, funcdef_file):
        """
        Class initializer.
        :param funcdef_file: a function definition JSON file
        """
        self._function_definitions = funcdef_file
        self.scriptEval = ScriptEval()
        os.makedirs("logs", exist_ok=True)
        log_file = os.path.join(
            os.path.dirname(os.path.abspath(__file__)), "logger.config"
        )
        logging.config.fileConfig(log_file)
        logging.info("Logging enabled")

    @property
    def function_definitions(self):
        """
        :return: json file with function definitions
        """
        return self._function_definitions

    @property
    def functions(self):
        """
        :return: Mapping of function id and implementation
        """
        return {
            0: "_sum_of_rows",
            1: "_sum_of_column",
            2: "_max_of_columns_2",
            3: "_random_forest",
        }

    @staticmethod
    def _random_forest(request, context):
        """
        :param request: an iterable sequence of RowData
        :param context:
        :return:
        """
        procs = [FillMissing, Categorify, Normalize]
        params = []

        # Iterate over bundled rows
        for request_rows in request:
            # Iterating over rows
            for row in request_rows.rows:
                # Retrieve numerical value of parameter and append to the params variable
                # Length of param is 1 since one column is received, the [0] collects the first value in the list
                for d in row.duals:
                    param = [d.strData for d in row.duals][0]
                params.append(param)

        if len(params) != 1:
            raise ValueError(
                f"Parameter column contained [{len(params)}] parameters, expected 1"
            )

        cwd = params[0]

        '''
        # Separator not specified here, because export.csv uses comma as separator
        exportCSVFull = pd.read_csv(cwd + "export.csv")
        rows = min(exportCSVFull.shape[0], 200000)

        test_data_percentage = 0.15
        # Split into training and test data
        training_data, test_data = tuple(
            pd.DataFrame(arr)
            for arr in np.split(
                exportCSVFull.sample(n=rows), [int(rows * (1 - test_data_percentage))]
            )
        )
        

        categoricalCSV = pd.read_csv(cwd + "categorical.csv", sep=chr(9))
        continuousCSV = pd.read_csv(cwd + "continuous.csv", sep=chr(9))
        targetCSV = pd.read_csv(cwd + "target.csv", sep=chr(9))

        cat_features = list(categoricalCSV)
        cont_features = list(continuousCSV)
        target_feature = list(targetCSV)[0]

        target_is_cat = target_feature in cat_features
        # remove target feature
        if target_is_cat:
            cat_features = list(
                itertools.filterfalse(lambda x: x == target_feature, cat_features)
            )
        else:
            cont_features = list(
                itertools.filterfalse(lambda x: x == target_feature, cont_features)
            )

        # Will always be assigned a value
        y_block: DataBlock = None
        # These two will be assigned value only if target is continuous
        y_std = None
        y_mean = None

        if target_is_cat:
            y_block = CategoryBlock
        else:
            y_block = RegressionBlock
            y_std = training_data[target_feature].std()
            y_mean = training_data[target_feature].mean()
        '''

        dsu = DataSetup(cwd)
        training_data, test_data = dsu.load_data()
        cat_features, cont_features, target_features = dsu.features
        y_block = dsu.y_block

        print(training_data.head())
        print(test_data.head())
        print(training_data.shape[0], test_data.shape[0])
        #print(1 / 0)


        dls = TabularPandas(
            df=training_data,
            procs=procs,
            cat_names=cat_features,
            cont_names=cont_features,
            y_names=target_features,
            y_block=y_block,
            splits=RandomSplitter(valid_pct=0.2)(range_of(training_data)),
        ).dataloaders(bs=64)

        learn = tabular_learner(dls, metrics=accuracy, default_cbs=True)

        learn.fit_one_cycle(
            1,
            cbs=EarlyStoppingCallback(monitor="valid_loss", min_delta=0.01, patience=3),
        )  # , lr_max=lr)

        preds, targs, test_preds_decoded = learn.get_preds(dl=dls, with_decoded=True)
        # confusion_matrix = get_confusion_matrix(learn)

        yield SSE.BundledRows(rows=[SSE.Row(duals=iter([SSE.Dual(strData="")]))])

    """
    Implementation of added functions.
    """

    @staticmethod
    def _sum_of_rows(request, context):
        """
        Summarize two parameters row wise. Tensor function.
        :param request: an iterable sequence of RowData
        :param context:
        :return: the same iterable sequence of row data as received
        """
        # Iterate over bundled rows
        for request_rows in request:
            response_rows = []
            # Iterating over rows
            for row in request_rows.rows:
                # Retrieve the numerical value of the parameters
                # Two columns are sent from the client, hence the length of params will be 2
                params = [d.numData for d in row.duals]

                # Sum over each row
                result = sum(params)

                # Create an iterable of Dual with a numerical value
                duals = iter([SSE.Dual(numData=result)])

                # Append the row data constructed to response_rows
                response_rows.append(SSE.Row(duals=duals))

            # Yield Row data as Bundled rows
            yield SSE.BundledRows(rows=response_rows)

    @staticmethod
    def _sum_of_column(request, context):
        """
        Summarize the column sent as a parameter. Aggregation function.
        :param request: an iterable sequence of RowData
        :param context:
        :return: int, sum if column
        """
        params = []

        # Iterate over bundled rows
        for request_rows in request:
            # Iterating over rows
            for row in request_rows.rows:
                # Retrieve numerical value of parameter and append to the params variable
                # Length of param is 1 since one column is received, the [0] collects the first value in the list
                param = [d.numData for d in row.duals][0]
                params.append(param)

        # Sum all rows collected the the params variable
        result = sum(params)

        # Create an iterable of dual with numerical value
        duals = iter([SSE.Dual(numData=result)])

        # Yield the row data constructed
        yield SSE.BundledRows(rows=[SSE.Row(duals=duals)])

        ## Tämä toimisi myös:
        # yield SSE.BundledRows(rows=[SSE.Row(duals=iter([SSE.Dual(numData=request.sum())]))])

    @staticmethod
    def _max_of_columns_2(request, context):
        """
        Find max of each column. This is a table function.
        :param request: an iterable sequence of RowData
        :param context:
        :return: a table with numerical values, two columns and one row
        """

        result = [_MINFLOAT] * 2

        # Iterate over bundled rows
        for request_rows in request:
            # Iterating over rows
            for row in request_rows.rows:
                # Retrieve the numerical value of each parameter
                # and update the result variable if it's higher than the previously saved value
                for i in range(0, len(row.duals)):
                    result[i] = max(result[i], row.duals[i].numData)

        # Create an iterable of dual with numerical value
        duals = iter([SSE.Dual(numData=r) for r in result])

        # Set and send Table header
        table = SSE.TableDescription(name="MaxOfColumns", numberOfRows=1)
        table.fields.add(name="Max1", dataType=SSE.NUMERIC)
        table.fields.add(name="Max2", dataType=SSE.NUMERIC)
        md = (("qlik-tabledescription-bin", table.SerializeToString()),)
        context.send_initial_metadata(md)

        # Yield the row data constructed
        yield SSE.BundledRows(rows=[SSE.Row(duals=duals)])

    @staticmethod
    def _get_function_id(context):
        """
        Retrieve function id from header.
        :param context: context
        :return: function id
        """
        metadata = dict(context.invocation_metadata())
        header = SSE.FunctionRequestHeader()
        header.ParseFromString(metadata["qlik-functionrequestheader-bin"])

        return header.functionId

    """
    Implementation of rpc functions.
    """

    def GetCapabilities(self, request, context):
        """
        Get capabilities.
        Note that either request or context is used in the implementation of this method, but still added as
        parameters. The reason is that gRPC always sends both when making a function call and therefore we must include
        them to avoid error messages regarding too many parameters provided from the client.
        :param request: the request, not used in this method.
        :param context: the context, not used in this method.
        :return: the capabilities.
        """
        logging.info("GetCapabilities")

        # Create an instance of the Capabilities grpc message
        # Enable(or disable) script evaluation
        # Set values for pluginIdentifier and pluginVersion
        capabilities = SSE.Capabilities(
            allowScript=True,
            pluginIdentifier="Column Operations - Qlik",
            pluginVersion="v1.1.0",
        )

        # If user defined functions supported, add the definitions to the message
        with open(self.function_definitions) as json_file:
            # Iterate over each function definition and add data to the Capabilities grpc message
            for definition in json.load(json_file)["Functions"]:
                function = capabilities.functions.add()
                function.name = definition["Name"]
                function.functionId = definition["Id"]
                function.functionType = definition["Type"]
                function.returnType = definition["ReturnType"]

                # Retrieve name and type of each parameter
                for param_name, param_type in sorted(definition["Params"].items()):
                    function.params.add(name=param_name, dataType=param_type)

                logging.info(
                    "Adding to capabilities: {}({})".format(
                        function.name, [p.name for p in function.params]
                    )
                )

        return capabilities

    def ExecuteFunction(self, request_iterator, context):
        """
        Call corresponding function based on function id sent in header.
        :param request_iterator: an iterable sequence of RowData.
        :param context: the context.
        :return: an iterable sequence of RowData.
        """
        # Retrieve function id
        func_id = self._get_function_id(context)
        logging.info("ExecuteFunction (functionId: {})".format(func_id))

        return getattr(self, self.functions[func_id])(request_iterator, context)

    def EvaluateScript(self, request, context):
        """
        Support script evaluation, based on different function and data types.
        :param request:
        :param context:
        :return:
        """
        # Retrieve header from request
        metadata = dict(context.invocation_metadata())
        header = SSE.ScriptRequestHeader()
        header.ParseFromString(metadata["qlik-scriptrequestheader-bin"])

        # Retrieve function type
        func_type = self.scriptEval.get_func_type(header)

        # Verify function type
        if (func_type == FunctionType.Tensor) or (
            func_type == FunctionType.Aggregation
        ):
            return self.scriptEval.EvaluateScript(request, context, header, func_type)
        else:
            # This plugin does not support other function types than tensor and aggregation.
            # Make sure the error handling, including logging, works as intended in the client
            msg = "Function type {} is not supported in this plugin.".format(
                func_type.name
            )
            context.set_code(grpc.StatusCode.UNIMPLEMENTED)
            context.set_details(msg)
            # Raise error on the plugin-side
            raise grpc.RpcError(grpc.StatusCode.UNIMPLEMENTED, msg)

    """
    Implementation of the Server connecting to gRPC.
    """

    def Serve(self, port, pem_dir):
        """
        Server
        :param port: port to listen on.
        :param pem_dir: Directory including certificates
        :return: None
        """
        server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
        SSE.add_ConnectorServicer_to_server(self, server)

        if pem_dir:
            # Secure connection
            with open(os.path.join(pem_dir, "sse_server_key.pem"), "rb") as f:
                private_key = f.read()
            with open(os.path.join(pem_dir, "sse_server_cert.pem"), "rb") as f:
                cert_chain = f.read()
            with open(os.path.join(pem_dir, "root_cert.pem"), "rb") as f:
                root_cert = f.read()
            credentials = grpc.ssl_server_credentials(
                [(private_key, cert_chain)], root_cert, True
            )
            server.add_secure_port("[::]:{}".format(port), credentials)
            logging.info(
                "*** Running server in secure mode on port: {} ***".format(port)
            )
        else:
            # Insecure connection
            server.add_insecure_port("[::]:{}".format(port))
            logging.info(
                "*** Running server in insecure mode on port: {} ***".format(port)
            )

        server.start()
        try:
            while True:
                time.sleep(_ONE_DAY_IN_SECONDS)
        except KeyboardInterrupt:
            server.stop(0)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", nargs="?", default="50052")
    parser.add_argument("--pem_dir", nargs="?")
    parser.add_argument("--definition_file", nargs="?", default="functions.json")
    args = parser.parse_args()

    # need to locate the file when script is called from outside it's location dir.
    def_file = os.path.join(
        os.path.dirname(os.path.abspath(__file__)), args.definition_file
    )

    calc = ExtensionService(def_file)
    calc.Serve(args.port, args.pem_dir)
