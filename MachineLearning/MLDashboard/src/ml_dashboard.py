import inspect
import pandas as pd
import numpy as np
from fastai.tabular.all import *


def parse_request(request):
    param: str = None
    for request_rows in request:
        for row in request_rows.rows:
            param = [d.strData for d in row.duals][0]
    return param
    # return tuple(param.split("|"))


def update_metrics_csv(cwd: str, model_name: str, metrics: "dict[str, list[str]]"):
    f = None
    file_created = None
    metrics_fp = cwd + "metrics.csv"
    try:
        f = open(metrics_fp, "r")
        file_created = False
    except Exception as e:
        f = open(metrics_fp, "x")
        file_created = True
        f.close()

    # read and write tings

    if file_created == True:
        out_columns = ["Algorithm"] + list(metrics)
        out_vals = [model_name] + [metrics[col] for col in list(metrics)]
        s = ",".join(out_columns) + chr(10) + ",".join(out_vals)
        f = open(metrics_fp, "w")
        f.write(s)
        f.close()
    else:
        in_str = f.read()
        f.close()
        # Split into rows with each row split by commas
        rows_split: list[list[str]] = [row.split(",") for row in in_str.split(chr(10))]
        row_first_vals: list[str] = [row[0] for row in rows_split]
        in_columns: list[str] = rows_split[0][1:]  # Excludes "Algorithm"
        new_ones = list(filter(lambda c: c not in in_columns, list(metrics)))
        metrics_updated: list[str] = in_columns + new_ones

        def get_metric(col: str):
            try:
                return metrics[col]
            except KeyError:
                return ""

        vals = [model_name] + [get_metric(col) for col in metrics_updated]

        # Append new column names to first row
        rows_split[0] = rows_split[0] + new_ones

        assert len(rows_split[0]) == len(metrics_updated) + 1

        # Get list of indices of rows with model_name as first value
        o_row_indeces = [
            i for i in range(len(rows_split)) if row_first_vals[i] == model_name
        ]
        # If found, update
        if o_row_indeces:
            i = o_row_indeces[0]
            rows_split[i] = vals
            assert len(rows_split[i]) == len(metrics_updated) + 1
        # If not, append
        else:
            rows_split.append(vals)

        # Add empty values to new columns
        for i in range(len(rows_split)):
            if i != 0:
                if len(rows_split[i]) != len(metrics_updated) + 1:
                    rows_split[i] = rows_split[i] + [""] * len(new_ones)
                    if len(rows_split[i]) != len(metrics_updated) + 1:
                        print(
                            f"Invalid metrics row found for algorithm <{row_first_vals[i]}> (exptected length {len(metrics_updated)+1}, actual length {len(rows_split[i])}), deleting row"
                        )
                        del rows_split[i]

        s = chr(10).join([",".join(row) for row in rows_split])
        f = open(metrics_fp, "w")
        f.write(s)
        f.close()
