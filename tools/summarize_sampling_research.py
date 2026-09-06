"""Validate complete research logs and summarize accuracy and counted density-evaluation costs."""
import argparse
import csv
import io
import math
from pathlib import Path
import shutil
import statistics as stats
import subprocess

TARGETS = ("gaussian", "banana", "unequal-modes")
SAMPLERS = ("figaro-block", "mess-1", "mess-4", "mess-8", "qslice-cauchy")
QUERIES = ("x", "y", "xSquared", "ySquared", "event")


def load(texts, repetitions, maximum):
    lines = [line for text in texts for line in text.splitlines() if line.startswith('"research",')]
    headers = [line for line in lines if line.startswith('"research","target"')]
    if not headers or len(set(headers)) != 1:
        raise ValueError("Missing/inconsistent header")
    reader = csv.DictReader([headers[0]] + [line for line in lines if line not in headers])
    rows = list(reader)
    groups = {}
    for row in rows:
        if None in row or None in row.values():
            raise ValueError("Malformed research row")
        key = row["target"], row["sampler"], int(row["round"]), row["method"]
        groups.setdefault(key, []).append(row)
    expected = {(t, s, r, m) for t in TARGETS for s in SAMPLERS for r in range(repetitions) for m in ("fixed", "stopped")}
    if set(groups) != expected:
        raise ValueError("Incomplete or unexpected research runs")
    for (target, sampler, rep, method), group in groups.items():
        if len(group) != 5 or {r["query"] for r in group} != set(QUERIES):
            raise ValueError("Missing/duplicate query")
        for key in ("draws", "seed", "reason", "evaluationsFullRun"):
            if len({r[key] for r in group}) != 1:
                raise ValueError("Misaligned query metadata")
        n = int(group[0]["draws"])
        if n < 2000 or n > maximum or n % 2000 or (method == "fixed" and n != maximum):
            raise ValueError("Invalid checkpoint")
        if int(group[0]["seed"]) != 141011 + rep * 7919 or int(group[0]["evaluationsFullRun"]) <= 0:
            raise ValueError("Invalid seed/evaluation count")
        reached = all(r["criteriaMet"] == "true" for r in group)
        reason = "FixedBudget" if method == "fixed" else "PrecisionReached" if reached else "MaxDrawsReached"
        if group[0]["reason"] != reason or (method == "stopped" and not reached and n != maximum):
            raise ValueError("Invalid stop decision")
        for row in group:
            truth, estimate, width = (float(row[k]) for k in ("truth", "estimate", "fullWidth"))
            if any(row[k] not in ("true", "false") for k in ("covered", "criteriaMet")):
                raise ValueError("Invalid boolean flag")
            if not math.isfinite(truth) or not math.isfinite(estimate):
                raise ValueError("Nonfinite truth/estimate")
            ess = float(row["meanEss"])
            if math.isinf(width) or width < 0 or math.isinf(ess) or ess < 0:
                raise ValueError("Invalid interval width/ESS")
            if (row["covered"] == "true") != (math.isfinite(width) and abs(estimate - truth) <= width / 2):
                raise ValueError("Incorrect coverage")
            if (row["criteriaMet"] == "true") != (row["failureReasons"] == ""):
                raise ValueError("Incorrect failure reasons")
        if method == "stopped":
            fixed = groups[target, sampler, rep, "fixed"]
            if group[0]["evaluationsFullRun"] != fixed[0]["evaluationsFullRun"]:
                raise ValueError("Full-run evaluation costs differ across replayed records")
            if n == maximum:
                original = {r["query"]: r for r in fixed}
                for row in group:
                    for key in ("estimate", "fullWidth", "criteriaMet", "meanEss", "failureReasons"):
                        if row[key] != original[row["query"]][key]:
                            raise ValueError("Cap replay differs from fixed trace")
    return reader.fieldnames, rows, groups


def summarize(groups, repetitions):
    print("| Target / sampler | Fixed joint coverage | Stopped joint coverage | Precision reached | Coverage among successes | Median full-run evaluations | Median minimum mean ESS / 1000 evaluations |")
    print("| --- | --- | --- | --- | --- | --- | --- |")
    for target in TARGETS:
        for sampler in SAMPLERS:
            fixed = [groups[target, sampler, r, "fixed"] for r in range(repetitions)]
            stopped = [groups[target, sampler, r, "stopped"] for r in range(repetitions)]
            successes = [g for g in stopped if g[0]["reason"] == "PrecisionReached"]
            coverage = lambda gs: sum(all(r["covered"] == "true" for r in g) for g in gs)
            costs = [int(g[0]["evaluationsFullRun"]) for g in fixed]
            ess_values = [[float(r["meanEss"]) for r in g] for g in fixed]
            rates = [min(values) * 1000 / cost if all(math.isfinite(v) for v in values) else math.nan
                     for values, cost in zip(ess_values, costs)]
            rate = f"{stats.median(rates):.2f}" if all(math.isfinite(r) for r in rates) else "unavailable"
            print(f"| {target} / {sampler} | {coverage(fixed)}/{repetitions} | {coverage(stopped)}/{repetitions} | "
                  f"{len(successes)}/{repetitions} | {coverage(successes)}/{len(successes)} | {stats.median(costs):.0f} | {rate} |")
    print("\n| Target / sampler | Fixed Y² mean error | Fixed Y² RMSE | Fixed event-probability RMSE |")
    print("| --- | --- | --- | --- |")
    for target in TARGETS:
        for sampler in SAMPLERS:
            errors = lambda query: [float(row["estimate"]) - float(row["truth"]) for rep in range(repetitions)
                for row in groups[target, sampler, rep, "fixed"] if row["query"] == query]
            y, event = errors("ySquared"), errors("event")
            rms = lambda x: math.sqrt(stats.mean(v * v for v in x))
            print(f"| {target} / {sampler} | {stats.mean(y):.5f} | {rms(y):.5f} | {rms(event):.5f} |")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("logs", nargs="+", type=Path)
    parser.add_argument("--repetitions", type=int, required=True)
    parser.add_argument("--draws", type=int, default=12000)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--acl-script", type=Path)
    args = parser.parse_args()
    if args.repetitions <= 0 or args.draws < 2000 or args.draws % 2000:
        raise ValueError("Invalid experiment budget")
    fields, rows, groups = load([p.read_text(encoding="utf-8-sig") for p in args.logs], args.repetitions, args.draws)
    if args.output:
        if args.output.exists():
            raise ValueError("Refusing to overwrite existing research output")
        stream = io.StringIO(newline="")
        writer = csv.DictWriter(stream, fields, quoting=csv.QUOTE_ALL, lineterminator="\n")
        writer.writeheader()
        writer.writerows(sorted(rows, key=lambda r: (r["target"], r["sampler"], int(r["round"]), r["method"], r["query"])))
        args.output.write_text(stream.getvalue(), encoding="utf-8", newline="\n")
        if args.acl_script:
            shell = shutil.which("pwsh") or shutil.which("powershell")
            if not shell:
                raise ValueError("PowerShell required for ACL hook")
            subprocess.run([shell, "-NoProfile", "-File", str(args.acl_script.resolve()), "-Paths", str(args.output.resolve())], check=True)
    print(f"Validated {len(rows)} rows across {len(groups)} complete fixed/stopped groups.")
    summarize(groups, args.repetitions)


if __name__ == "__main__":
    main()
