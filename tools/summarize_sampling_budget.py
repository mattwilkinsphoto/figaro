"""Validate matched-budget experiment records and report all-run accuracy, work, and stopping."""
import argparse
import csv
import io
import math
from pathlib import Path
import shutil
import statistics
import subprocess

TARGETS = ("gaussian", "rotated", "banana", "student5", "unequal-modes")
METHODS = ("rwm", "quantile", "gpss", "affine-gpss")
QUERIES = ("x", "y", "xSquared", "ySquared", "event")
FIELDS = ("budgetResearch,target,sampler,round,seed,record,budgetPerChain,drawsPerChain,availableDraws,"
          "evaluations,pilotEvaluations,warmupEvaluations,status,reason,query,truth,estimate,fullWidth,"
          "meanEss,covered,criteriaMet,failureReasons").split(",")
theta = math.atan(2 / math.sqrt(5))
student_tail = 1 - 2 * (theta / math.pi + 2 * math.sin(2 * theta) / (3 * math.pi)
                       + math.sin(4 * theta) / (12 * math.pi))
TRUTHS = dict(zip(TARGETS, (
    (0, 0, 1, 1, math.erfc(math.sqrt(2))),
    (0, 0, 4.505, 4.505, math.erfc(2 / math.sqrt(2 * 4.505))),
    (0, 0, 1, 0.57, math.erfc(math.sqrt(2))),
    (0, 0, 5 / 3, 5 / 3, student_tail),
    (-2.4, 0, 16.25, 1, 0.2 + 0.3 * math.erfc(8 / math.sqrt(2))),
)))


def load(texts, repetitions, cap, first=0):
    if repetitions <= 0 or first < 0 or cap < 20000 or cap > 1000000 or cap % 4:
        raise ValueError("Invalid protocol budget")
    rows = []
    for text in texts:
        lines = [line for line in text.splitlines() if line.startswith('"budgetResearch",')]
        if not lines:
            raise ValueError("Missing research header/data")
        reader = csv.DictReader(lines)
        if reader.fieldnames != FIELDS:
            raise ValueError("Invalid research header")
        rows.extend(reader)
    checkpoints = (cap // 4, cap // 2, cap)
    groups = {}
    for row in rows:
        if set(row) != set(FIELDS) or None in row.values():
            raise ValueError("Malformed row")
        key = row["target"], row["sampler"], int(row["round"]), row["record"], int(row["budgetPerChain"])
        groups.setdefault(key, []).append(row)
    expected = {(t, m, r, "fixed", b) for t in TARGETS for m in METHODS
                for r in range(first, first + repetitions) for b in checkpoints}
    if {key for key in groups if key[3] == "fixed"} != expected:
        raise ValueError("Missing/unexpected fixed runs")
    if any(key[3] not in ("fixed", "stopped") for key in groups):
        raise ValueError("Unknown record type")
    for (target, method, rep, kind, budget), group in groups.items():
        if target not in TARGETS or method not in METHODS or rep not in range(first, first + repetitions) or budget not in checkpoints:
            raise ValueError("Unexpected experiment")
        if len(group) != 5 or {r["query"] for r in group} != set(QUERIES):
            raise ValueError("Missing/duplicate query")
        for field in FIELDS[:14]:
            if len({r[field] for r in group}) != 1:
                raise ValueError("Misaligned query metadata")
        row = group[0]
        if int(row["seed"]) != 812031 + 104729 * rep:
            raise ValueError("Wrong predeclared seed")
        n, available, spent, pilot, warm = (int(row[k]) for k in
            ("drawsPerChain", "availableDraws", "evaluations", "pilotEvaluations", "warmupEvaluations"))
        if not 0 <= pilot <= warm <= spent <= 4 * budget or not 0 <= 4 * n <= available <= spent:
            raise ValueError("Invalid budget/trace accounting")
        if row["status"] == "Ok" and (n < 4 or spent != 4 * budget):
            raise ValueError("Invalid successful execution accounting")
        reached = row["status"] == "Ok" and all(r["criteriaMet"] == "true" for r in group)
        reason = ("RunFailure" if row["status"] != "Ok" else "FixedBudget" if kind == "fixed"
                  else "PrecisionReached" if reached else "MaxEvaluationsReached")
        if row["reason"] != reason:
            raise ValueError("Wrong decision label")
        for row in group:
            if any(row[k] not in ("true", "false") for k in ("covered", "criteriaMet")):
                raise ValueError("Invalid boolean flag")
            truth, mean, width, ess = (float(row[k]) for k in ("truth", "estimate", "fullWidth", "meanEss"))
            if not math.isclose(truth, TRUTHS[target][QUERIES.index(row["query"])], abs_tol=1e-13, rel_tol=1e-13):
                raise ValueError("Wrong analytic truth")
            if math.isinf(width) or width < 0 or math.isinf(ess) or ess < 0:
                raise ValueError("Invalid width/ESS")
            if row["status"] == "Ok" and not math.isfinite(mean):
                raise ValueError("Nonfinite estimate in successful execution")
            if row["status"] != "Ok" and (not all(math.isnan(v) for v in (mean, width, ess)) or row["criteriaMet"] != "false"):
                raise ValueError("Failed run has fabricated statistics")
            if row["criteriaMet"] == "true" and (n < 2000 or not math.isfinite(width) or not math.isfinite(ess) or ess < 400):
                raise ValueError("Precision success contradicts minimum-work/error guards")
            covered = math.isfinite(width) and math.isfinite(mean) and abs(mean - truth) <= width / 2
            if (row["covered"] == "true") != covered:
                raise ValueError("Incorrect coverage flag")
            if (row["criteriaMet"] == "true") != (row["failureReasons"] == ""):
                raise ValueError("Incorrect failure reasons")
    for t in TARGETS:
        for m in METHODS:
            for rep in range(first, first + repetitions):
                fixed = [groups[t, m, rep, "fixed", b] for b in checkpoints]
                picked = next((g for g in fixed if g[0]["status"] == "Ok" and all(r["criteriaMet"] == "true" for r in g)), fixed[-1])
                selected = [g for k, g in groups.items() if k[:4] == (t, m, rep, "stopped")]
                if len(selected) != 1:
                    raise ValueError("Missing/duplicate stopped record")
                by_query = {r["query"]: r for r in picked}
                for row in selected[0]:
                    if any(row[k] != by_query[row["query"]][k] for k in FIELDS if k not in ("record", "reason")):
                        raise ValueError("Stopped record is not earliest successful checkpoint/cap")
                for a, b in zip(fixed, fixed[1:]):
                    for key in ("drawsPerChain", "availableDraws", "evaluations", "pilotEvaluations", "warmupEvaluations"):
                        if int(a[0][key]) > int(b[0][key]):
                            raise ValueError("Nonmonotonic matched prefixes")
    return rows, groups


def summarize(groups, repetitions, cap, first=0):
    def covered(gs):
        return sum(all(r["covered"] == "true" for r in g) for g in gs)
    def rmse(gs, query):
        errors = [float(r["estimate"]) - float(r["truth"]) for g in gs for r in g if r["query"] == query]
        return f"{math.sqrt(statistics.mean(v * v for v in errors)):.5f}" if all(math.isfinite(v) for v in errors) else "unavailable"
    print("| Target / method | Cap coverage | Stopped coverage | Precision reached | Success coverage | Run failures | Median aligned draws/chain | Median warm-up evaluations (4 chains) | Y² RMSE | Event RMSE |")
    print("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
    for t in TARGETS:
        for m in METHODS:
            fixed = [groups[t, m, r, "fixed", cap] for r in range(first, first + repetitions)]
            stopped = [g for k, g in groups.items() if k[:2] == (t, m) and k[3] == "stopped"]
            successes = [g for g in stopped if g[0]["reason"] == "PrecisionReached"]
            failures = sum(g[0]["status"] != "Ok" for g in fixed)
            draws = statistics.median(int(g[0]["drawsPerChain"]) for g in fixed)
            warm = statistics.median(int(g[0]["warmupEvaluations"]) for g in fixed)
            print(f"| {t} / {m} | {covered(fixed)}/{repetitions} | {covered(stopped)}/{repetitions} | "
                  f"{len(successes)}/{repetitions} | {covered(successes)}/{len(successes)} | {failures} | {draws:.0f} | {warm:.0f} | "
                  f"{rmse(fixed, 'ySquared')} | {rmse(fixed, 'event')} |")
    print("\n| Target / method | Coverage at quarter / half / full cap | Y² RMSE at quarter / half / full cap |")
    print("| --- | --- | --- |")
    for t in TARGETS:
        for m in METHODS:
            prefixes = [[groups[t, m, r, "fixed", b] for r in range(first, first + repetitions)] for b in (cap // 4, cap // 2, cap)]
            print(f"| {t} / {m} | " + " / ".join(f"{covered(gs)}/{repetitions}" for gs in prefixes)
                  + " | " + " / ".join(rmse(gs, "ySquared") for gs in prefixes) + " |")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("logs", nargs="+", type=Path)
    parser.add_argument("--repetitions", type=int, required=True)
    parser.add_argument("--cap", type=int, default=100000)
    parser.add_argument("--first-round", type=int, default=0)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--acl-script", type=Path)
    args = parser.parse_args()
    rows, groups = load([p.read_text(encoding="utf-8-sig") for p in args.logs], args.repetitions, args.cap, args.first_round)
    if args.output:
        stream = io.StringIO(newline="")
        writer = csv.DictWriter(stream, FIELDS, quoting=csv.QUOTE_ALL, lineterminator="\n")
        writer.writeheader()
        writer.writerows(sorted(rows, key=lambda r: (r["target"], r["sampler"], int(r["round"]), r["record"], int(r["budgetPerChain"]), r["query"])))
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
