"""Validate complete higher-dimensional sampling records and report coverage and accuracy."""
import argparse
import csv
import io
import math
from pathlib import Path
import shutil
import statistics as stats
import subprocess

from summarize_sampling_budget import student_tail

TARGETS = ("gaussian", "correlated", "banana", "student5", "positive", "asymmetric")
METHODS = ("gpss", "quantile")
DIMENSIONS = (8, 32)
QUERIES = ("first", "last", "firstSquared", "meanSquare", "cross", "event")
FIELDS = ("highDimensional,dimension,target,sampler,round,seed,record,budgetPerChain,drawsPerChain,"
          "availableDraws,evaluations,warmupEvaluations,status,reason,query,truth,estimate,fullWidth,"
          "meanEss,covered,criteriaMet,failureReasons").split(",")
TRUTHS = dict(zip(TARGETS, (
    (0, 0, 1, 1, 0, math.erfc(math.sqrt(2))),
    (0, 0, 1, 1, 0.95, math.erfc(math.sqrt(2))),
    (0, 0, 1, 0.785, 0, math.erfc(math.sqrt(2))),
    (0, 0, 5 / 3, 5 / 3, 0, student_tail),
    (1, 1, 2, 2, 1, math.exp(-2)),
    (-1.5, -1.5, 4.75, 4.75, 4.5, 0.45 * math.erfc(4 / math.sqrt(2)) + 0.1 * (1 - 0.5 * math.erfc(6 / math.sqrt(2)))),
)))
CASES = tuple((d, t, m) for d in DIMENSIONS for t in TARGETS for m in METHODS)


def load(texts, repetitions, cap, first=0):
    if repetitions <= 0 or first < 0 or cap < 20000 or cap > 1000000 or cap % 4:
        raise ValueError("Invalid experiment budget")
    rows = []
    for text in texts:
        lines = [line for line in text.splitlines() if line.startswith('"highDimensional",')]
        reader = csv.DictReader(lines)
        if reader.fieldnames != FIELDS:
            raise ValueError("Missing/invalid header")
        rows.extend(reader)
    budgets = (cap // 4, cap // 2, cap)
    groups = {}
    for row in rows:
        if set(row) != set(FIELDS) or None in row.values():
            raise ValueError("Malformed row")
        key = int(row["dimension"]), row["target"], row["sampler"], int(row["round"]), row["record"], int(row["budgetPerChain"])
        groups.setdefault(key, []).append(row)
    expected = {(*case, r, "fixed", b) for case in CASES for r in range(first, first + repetitions) for b in budgets}
    if {k for k in groups if k[4] == "fixed"} != expected:
        raise ValueError("Missing/unexpected fixed runs")
    for (d, t, m, rep, kind, budget), group in groups.items():
        if (d, t, m) not in CASES or rep not in range(first, first + repetitions) or kind not in ("fixed", "stopped") or budget not in budgets:
            raise ValueError("Unexpected experiment")
        if len(group) != 6 or {r["query"] for r in group} != set(QUERIES):
            raise ValueError("Missing/duplicate query")
        if any(len({r[k] for r in group}) != 1 for k in FIELDS[:14]):
            raise ValueError("Misaligned query metadata")
        row = group[0]
        if int(row["seed"]) != 1700113 + 130363 * rep:
            raise ValueError("Wrong predeclared seed")
        n, available, spent, warm = (int(row[k]) for k in ("drawsPerChain", "availableDraws", "evaluations", "warmupEvaluations"))
        if not 0 <= warm <= spent <= 4 * budget or not 0 <= 4 * n <= available <= spent:
            raise ValueError("Invalid work accounting")
        if row["status"] == "Ok" and (n < 4 or spent != 4 * budget):
            raise ValueError("Invalid successful execution accounting")
        reached = row["status"] == "Ok" and all(r["criteriaMet"] == "true" for r in group)
        reason = ("RunFailure" if row["status"] != "Ok" else "FixedBudget" if kind == "fixed"
                  else "PrecisionReached" if reached else "MaxEvaluationsReached")
        if row["reason"] != reason:
            raise ValueError("Incorrect stop reason")
        for row in group:
            if any(row[k] not in ("true", "false") for k in ("covered", "criteriaMet")):
                raise ValueError("Invalid boolean flag")
            truth, mean, width, ess = (float(row[k]) for k in ("truth", "estimate", "fullWidth", "meanEss"))
            if not math.isclose(truth, TRUTHS[t][QUERIES.index(row["query"])], rel_tol=1e-13, abs_tol=1e-13):
                raise ValueError("Wrong analytic truth")
            if math.isinf(width) or width < 0 or math.isinf(ess) or ess < 0:
                raise ValueError("Invalid width/ESS")
            if row["status"] == "Ok" and not math.isfinite(mean):
                raise ValueError("Invalid successful estimate")
            if row["status"] != "Ok" and (not all(math.isnan(v) for v in (mean, width, ess)) or row["criteriaMet"] != "false"):
                raise ValueError("Failed run has fabricated statistics")
            if row["criteriaMet"] == "true" and (n < 2000 or not math.isfinite(width) or not math.isfinite(ess) or ess < 400):
                raise ValueError("Success contradicts minimum-work/error guards")
            covered = math.isfinite(mean) and math.isfinite(width) and abs(mean - truth) <= width / 2
            if (row["covered"] == "true") != covered or (row["criteriaMet"] == "true") != (row["failureReasons"] == ""):
                raise ValueError("Incorrect coverage/failure flags")
    for case in CASES:
        for rep in range(first, first + repetitions):
            fixed = [groups[(*case, rep, "fixed", b)] for b in budgets]
            selected = [g for k, g in groups.items() if k[:5] == (*case, rep, "stopped")]
            if len(selected) != 1:
                raise ValueError("Missing/duplicate selected stop")
            chosen = next((g for g in fixed if g[0]["status"] == "Ok" and all(r["criteriaMet"] == "true" for r in g)), fixed[-1])
            by_query = {r["query"]: r for r in chosen}
            for row in selected[0]:
                if any(row[k] != by_query[row["query"]][k] for k in FIELDS if k not in ("record", "reason")):
                    raise ValueError("Selected stop is not earliest success/cap")
            for a, b in zip(fixed, fixed[1:]):
                if any(int(a[0][k]) > int(b[0][k]) for k in ("drawsPerChain", "availableDraws", "evaluations", "warmupEvaluations")):
                    raise ValueError("Nonmonotonic prefixes")
    return rows, groups


def summarize(groups, repetitions, cap, first=0):
    def coverage(gs):
        return sum(all(r["covered"] == "true" for r in g) for g in gs)
    print("| Dimension / target / sampler | Cap coverage | Stopped coverage | Precision reached | Success coverage | Run failures | Median aligned draws | Median warm-up evaluations |")
    print("| --- | --- | --- | --- | --- | --- | --- | --- |")
    for case in CASES:
        fixed = [groups[(*case, r, "fixed", cap)] for r in range(first, first + repetitions)]
        stopped = [g for k, g in groups.items() if k[:3] == case and k[4] == "stopped"]
        successes = [g for g in stopped if g[0]["reason"] == "PrecisionReached"]
        label = " / ".join(map(str, case))
        print(f"| {label} | {coverage(fixed)}/{repetitions} | {coverage(stopped)}/{repetitions} | {len(successes)}/{repetitions} | "
              f"{coverage(successes)}/{len(successes)} | {sum(g[0]['status'] != 'Ok' for g in fixed)} | "
              f"{stats.median(int(g[0]['drawsPerChain']) for g in fixed):.0f} | {stats.median(int(g[0]['warmupEvaluations']) for g in fixed):.0f} |")
    print("\n| Dimension / target / sampler | First RMSE | Last RMSE | First² RMSE | Mean square RMSE | Cross RMSE | Event RMSE |")
    print("| --- | --- | --- | --- | --- | --- | --- |")
    for case in CASES:
        errors = []
        for q in QUERIES:
            values = [float(r["estimate"]) - float(r["truth"]) for rep in range(first, first + repetitions)
                      for r in groups[(*case, rep, "fixed", cap)] if r["query"] == q]
            errors.append(f"{math.sqrt(stats.mean(v * v for v in values)):.5f}" if all(math.isfinite(v) for v in values) else "unavailable")
        print("| " + " / ".join(map(str, case)) + " | " + " | ".join(errors) + " |")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("logs", nargs="+", type=Path)
    parser.add_argument("--repetitions", type=int, required=True)
    parser.add_argument("--cap", type=int, default=300000)
    parser.add_argument("--first-round", type=int, default=0)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--acl-script", type=Path)
    args = parser.parse_args()
    rows, groups = load([p.read_text(encoding="utf-8-sig") for p in args.logs], args.repetitions, args.cap, args.first_round)
    if args.output:
        stream = io.StringIO(newline="")
        writer = csv.DictWriter(stream, FIELDS, quoting=csv.QUOTE_ALL, lineterminator="\n")
        writer.writeheader()
        writer.writerows(sorted(rows, key=lambda r: (int(r["dimension"]), r["target"], r["sampler"], int(r["round"]), r["record"], int(r["budgetPerChain"]), r["query"])))
        with args.output.open("x", encoding="utf-8", newline="") as output:
            output.write(stream.getvalue())
        if args.acl_script:
            shell = shutil.which("pwsh") or shutil.which("powershell")
            if not shell:
                raise ValueError("PowerShell required for ACL hook")
            subprocess.run([shell, "-NoProfile", "-File", str(args.acl_script.resolve()), "-Paths", str(args.output.resolve())], check=True)
    print(f"Validated {len(rows)} query rows in {len(groups)} complete checkpoint/stopped groups.")
    summarize(groups, args.repetitions, args.cap, args.first_round)


if __name__ == "__main__":
    main()
