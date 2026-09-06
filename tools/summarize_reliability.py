#!/usr/bin/env python3
"""Validate complete paired reliability audits and print coverage/error summaries (stdlib only)."""
import argparse
import csv
import io
import math
from pathlib import Path
import shutil
import statistics as stats
import subprocess

STRATEGIES = ("iid", "reparameterized", "default", "joint-prior", "manual", "calibrated")
RULES = ("legacy-batch", "mcse-floor")
QUERIES = ("x", "y", "xSquared", "ySquared", "xTail")


def load(texts, repetitions, maximum):
    lines = [line for text in texts for line in text.splitlines() if line.startswith('"reliability",')]
    headers = [line for line in lines if line.startswith('"reliability","strategy"')]
    if not headers or len(set(headers)) != 1:
        raise ValueError("Missing or inconsistent audit headers")
    reader = csv.DictReader([headers[0]] + [line for line in lines if line not in headers])
    fields, rows = reader.fieldnames, list(reader)
    runs = {}
    for row in rows:
        if None in row or None in row.values():
            raise ValueError("Malformed CSV row")
        key = row["strategy"], int(row["round"]), row["rule"]
        runs.setdefault(key, []).append(row)
    expected = {(s, r, p) for s in STRATEGIES for r in range(repetitions) for p in RULES}
    if set(runs) != expected:
        raise ValueError("Incomplete/unexpected audit runs; partial logs must not be reported")
    for (strategy, _, rule), group in runs.items():
        rejected = group[0]["method"] == "rejected"
        if rejected:
            if strategy != "calibrated" or len(group) != 1 or group[0]["reason"] != "PilotRejected" or group[0]["query"]:
                raise ValueError("Invalid rejection record")
            continue
        fixed = [r for r in group if r["method"] == "fixed"]
        stopped = [r for r in group if r["method"] == "stopped"]
        budgets = {2000, 12000, maximum}
        if len(group) != 5 * (len(budgets) + 1) or len(stopped) != 5:
            raise ValueError("Wrong query/group counts")
        expected_fixed = {(n, q) for n in budgets for q in QUERIES}
        if {(int(r["draws"]), r["query"]) for r in fixed} != expected_fixed:
            raise ValueError("Missing/duplicate fixed queries")
        if {r["query"] for r in stopped} != set(QUERIES) or len({r["draws"] for r in stopped}) != 1:
            raise ValueError("Misaligned stopped queries")
        count = int(stopped[0]["draws"])
        if count < 2000 or count > maximum or count % 2000:
            raise ValueError("Invalid stopping checkpoint")
        for row in group:
            if not all(math.isfinite(float(row[k])) for k in ("truth", "estimate", "error")):
                raise ValueError("Nonfinite target estimate")
            if not math.isclose(float(row["error"]), float(row["estimate"]) - float(row["truth"]), abs_tol=1e-12):
                raise ValueError("Incorrect target error")
            width = float(row["fullWidth"])
            covered = math.isfinite(width) and abs(float(row["error"])) <= width / 2
            if (row["covered"] == "true") != covered:
                raise ValueError("Incorrect coverage flag")
            if rule == "mcse-floor" and (row["criteriaMet"] == "true") != (row["failureReasons"] == ""):
                raise ValueError("Inconsistent failure reasons")
        for method, n in {("fixed", n) for n in budgets} | {("stopped", count)}:
            block = [r for r in group if r["method"] == method and int(r["draws"]) == n]
            all_met = all(r["criteriaMet"] == "true" for r in block)
            if any((r["allCriteriaMet"] == "true") != all_met for r in block):
                raise ValueError("Incorrect all-query decision")
            expected_reason = "FixedBudget" if method == "fixed" else "PrecisionReached" if all_met else "MaxDrawsReached"
            if any(r["reason"] != expected_reason for r in block) or (method == "stopped" and not all_met and n != maximum):
                raise ValueError("Incorrect stopping reason")
    # These invariants distinguish a safeguard from a change to the sampled trajectories.
    for strategy in STRATEGIES:
        for round_id in range(repetitions):
            old, new = (runs[strategy, round_id, rule] for rule in RULES)
            if (old[0]["method"] == "rejected") != (new[0]["method"] == "rejected"):
                raise ValueError("Pilot selection differs between rules")
            if old[0]["method"] == "rejected":
                continue
            before = {(r["draws"], r["query"]): r for r in old if r["method"] == "fixed"}
            for row in (r for r in new if r["method"] == "fixed"):
                baseline = before[row["draws"], row["query"]]
                if row["estimate"] != baseline["estimate"]:
                    raise ValueError("Fixed trajectories differ")
                if float(row["fullWidth"]) + 1e-14 < float(baseline["fullWidth"]):
                    raise ValueError("MCSE floor narrowed a fixed interval")
                if row["criteriaMet"] == "true" and baseline["criteriaMet"] != "true":
                    raise ValueError("MCSE floor passed a previously failed fixed checkpoint")
            times = [int(next(r for r in group if r["method"] == "stopped")["draws"]) for group in (old, new)]
            if times[1] < times[0]:
                raise ValueError("MCSE floor stopped earlier")
    return fields, rows, runs


def summarize(runs, repetitions, maximum):
    print("| Strategy / rule | Pilot rejected | Joint coverage at 2k / 12k / cap | Stopped joint coverage | Precision reached | Median stopping draws |")
    print("| --- | --- | --- | --- | --- | --- |")
    for strategy in STRATEGIES:
        for rule in RULES:
            all_runs = [runs[strategy, r, rule] for r in range(repetitions)]
            available = [g for g in all_runs if g[0]["method"] != "rejected"]
            covers = []
            for n in (2000, 12000, maximum):
                covers.append(sum(all(r["covered"] == "true" for r in g if r["method"] == "fixed" and int(r["draws"]) == n) for g in available))
            stops = [[r for r in g if r["method"] == "stopped"] for g in available]
            coverage = sum(all(r["covered"] == "true" for r in g) for g in stops)
            reached = sum(g[0]["reason"] == "PrecisionReached" for g in stops)
            median = f"{stats.median(int(g[0]['draws']) for g in stops):.0f}" if stops else "unavailable"
            print(f"| {strategy} / {rule} | {repetitions - len(available)}/{repetitions} | "
                  f"{' / '.join(str(c) for c in covers)} of {len(available)} | {coverage}/{len(available)} | "
                  f"{reached}/{repetitions} | {median} |")
    print("\n| Strategy at cap: ySquared | Mean error | Empirical error SD | RMS batch MCSE | RMS spectral MCSE |")
    print("| --- | --- | --- | --- | --- |")
    for strategy in STRATEGIES:
        rows = [r for rep in range(repetitions) for r in runs[strategy, rep, "mcse-floor"]
                if r["method"] == "fixed" and int(r["draws"]) == maximum and r["query"] == "ySquared"]
        if len(rows) >= 2:
            errors = [float(r["error"]) for r in rows]
            rms = lambda key: math.sqrt(stats.mean(float(r[key]) ** 2 for r in rows))
            print(f"| {strategy} | {stats.mean(errors):.5f} | {stats.stdev(errors):.5f} | {rms('batchMcse'):.5f} | {rms('spectralMcse'):.5f} |")
    print("\nIID oracle joint coverage at 2k / 12k / cap (known variance, Normal approximation):")
    for strategy in ("iid", "reparameterized"):
        counts = [sum(all(r["iidOracleCovered"] == "true" for r in runs[strategy, rep, "mcse-floor"]
                          if r["method"] == "fixed" and int(r["draws"]) == n) for rep in range(repetitions))
                  for n in (2000, 12000, maximum)]
        print(f"{strategy}: {counts} of {repetitions}")
    print("\nJoint coverage conditional on PrecisionReached (not including capped runs):")
    for strategy in STRATEGIES:
        for rule in RULES:
            successful = [[r for r in runs[strategy, rep, rule] if r["method"] == "stopped"]
                          for rep in range(repetitions)]
            successful = [g for g in successful if g and g[0]["reason"] == "PrecisionReached"]
            covered = sum(all(r["covered"] == "true" for r in g) for g in successful)
            print(f"{strategy} / {rule}: {covered}/{len(successful)}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("logs", nargs="+", type=Path)
    parser.add_argument("--repetitions", type=int, required=True)
    parser.add_argument("--max-draws", type=int, default=48000)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--acl-script", type=Path)
    args = parser.parse_args()
    if args.repetitions < 1 or args.max_draws < 12000 or args.max_draws % 2000:
        raise ValueError("Invalid audit budget")
    fields, rows, runs = load([p.read_text(encoding="utf-8-sig") for p in args.logs], args.repetitions, args.max_draws)
    if args.output:
        if args.output.exists() and not args.output.read_text(encoding="utf-8").startswith('"reliability",'):
            raise ValueError("Refusing to overwrite a non-reliability CSV")
        stream = io.StringIO(newline="")
        writer = csv.DictWriter(stream, fields, quoting=csv.QUOTE_ALL, lineterminator="\n")
        writer.writeheader()
        writer.writerows(sorted(rows, key=lambda r: (r["strategy"], int(r["round"]), r["rule"], r["method"], int(r["draws"]), r["query"])))
        args.output.write_text(stream.getvalue(), encoding="utf-8", newline="\n")
        if args.acl_script:
            shell = shutil.which("pwsh") or shutil.which("powershell")
            if not shell:
                raise ValueError("PowerShell required for ACL hook")
            subprocess.run([shell, "-NoProfile", "-File", str(args.acl_script.resolve()), "-Paths", str(args.output.resolve())], check=True)
    print(f"Validated {len(rows)} query/rejection rows across {len(runs)} paired-rule runs.")
    summarize(runs, args.repetitions, args.max_draws)


if __name__ == "__main__":
    main()
