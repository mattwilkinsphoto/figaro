"""Validate and summarize sanitized JDK vector profile aggregates (standard library only)."""
import argparse
from collections import Counter
import csv
import io
import math
from pathlib import Path
import re
import shutil
import subprocess

FIELDS = "vectorProfile kind group detail site count value".split()
METRICS = {"eventSpanSeconds", "gcCount", "gcPauseSeconds", "longestGcPauseSeconds",
           "heapSummaryCount", "maxObservedHeapBytes", "maxObservedAfterGcHeapBytes", "lostBytes"}
GROUPS = {"diagnostics", "sampling", "other", "unknown"}


def load(text):
    records = {}
    for values in csv.reader(io.StringIO(text)):
        if not values or values[0] != "vectorProfile" or values == FIELDS:
            continue
        if len(values) != len(FIELDS):
            raise ValueError("Wrong profile field count")
        row = dict(zip(FIELDS, values))
        kind, group, detail, site = (row[k] for k in ("kind", "group", "detail", "site"))
        key = kind, group, detail, site
        if key in records:
            raise ValueError("Duplicate profile record")
        count, value = int(row["count"]), float(row["value"])
        if count <= 0 or not math.isfinite(value) or value < 0:
            raise ValueError("Invalid count/value")
        if kind == "metric":
            if group not in METRICS or detail or site or count != 1:
                raise ValueError("Invalid metric")
            if not group.endswith("Seconds") and int(row["value"]) < 0:
                raise ValueError("Invalid integer metric")
        elif kind in ("allocation", "execution"):
            if group not in GROUPS or not detail or not site or int(row["value"]) <= 0:
                raise ValueError("Invalid aggregate")
            # Only JVM type/method identifiers and source line numbers, never paths or free text.
            if any(not re.fullmatch(r"[A-Za-z0-9_.$;\[\]<>?:-]+", field) for field in (detail, site)):
                raise ValueError("Non-identifier profile data")
            if kind == "execution" and int(row["value"]) != count:
                raise ValueError("Execution value must equal sample count")
        else:
            raise ValueError("Unknown profile kind")
        records[key] = row
    metrics = {r["group"]: float(r["value"]) for r in records.values() if r["kind"] == "metric"}
    if set(metrics) != METRICS or metrics["eventSpanSeconds"] <= 0 or metrics["lostBytes"] != 0:
        raise ValueError("Missing metrics, empty recording or JFR data loss")
    if metrics["longestGcPauseSeconds"] > metrics["gcPauseSeconds"] or metrics["maxObservedAfterGcHeapBytes"] > metrics["maxObservedHeapBytes"]:
        raise ValueError("Inconsistent GC/heap metrics")
    if (metrics["gcCount"] == 0) != (metrics["heapSummaryCount"] == 0):
        raise ValueError("Missing GC/heap events")
    for kind in ("allocation", "execution"):
        if not any(r["kind"] == kind for r in records.values()):
            raise ValueError("Missing allocation or execution samples")
    return records


def summarize(records):
    metrics = {r["group"]: float(r["value"]) for r in records.values() if r["kind"] == "metric"}
    print("Validated complete profile aggregates with zero reported lost bytes.")
    for name in sorted(metrics):
        print(f"{name}: {metrics[name]:.6f}")
    for kind in ("allocation", "execution"):
        rows = [r for r in records.values() if r["kind"] == kind]
        total = sum(int(r["value"]) for r in rows)
        print(f"\n{kind}: total {'sample weight (bytes)' if kind == 'allocation' else 'Java execution samples'} = {total}")
        print("| Group | Samples | Value | Share % |")
        print("| --- | --- | --- | --- |")
        for group in sorted(GROUPS):
            selected = [r for r in rows if r["group"] == group]
            value = sum(int(r["value"]) for r in selected)
            print(f"| {group} | {sum(int(r['count']) for r in selected)} | {value} | {100*value/total:.2f} |")
        # Inclusive site attribution, with classes/leaves retained in the checked CSV.
        sites = Counter()
        for row in rows:
            sites[row["group"], row["site"]] += int(row["value"])
        print("\n| Group / nearest site | Value | Share % |")
        print("| --- | --- | --- |")
        for (group, site), value in sites.most_common(12):
            print(f"| {group} / {site} | {value} | {100*value/total:.2f} |")
    print("\nWeights estimate allocation pressure, not exact allocated/live bytes. Java samples are not wall-time shares. No hardware bandwidth measurement.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--acl-script", type=Path)
    args = parser.parse_args()
    records = load(args.log.read_text(encoding="utf-8-sig"))
    if args.output:
        with args.output.open("x", encoding="utf-8", newline="") as stream:
            writer = csv.DictWriter(stream, FIELDS, quoting=csv.QUOTE_ALL, lineterminator="\n")
            writer.writeheader(); writer.writerows(records[k] for k in sorted(records))
        if args.acl_script:
            shell = shutil.which("pwsh") or shutil.which("powershell")
            if not shell:
                raise ValueError("PowerShell required for ACL hook")
            subprocess.run([shell, "-NoProfile", "-File", str(args.acl_script.resolve()), "-Paths", str(args.output.resolve())], check=True)
    summarize(records)


if __name__ == "__main__":
    main()
