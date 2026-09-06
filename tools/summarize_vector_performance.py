"""Validate complete fixed-protocol vector scaling reports; standard library only."""
import argparse
import csv
import io
import itertools
import math
from pathlib import Path
import re
import shutil
import statistics as stats
import subprocess

FIXTURES = ("gaussian8", "gaussian32", "correlated32", "positive32", "likelihood8", "mixture8")
METHODS = ("GPSS", "Quantile")
WORKERS = (1, 2, 4)
FIELDS = ("vectorPerformance fixture method workers round seed draws warmUp status wallSeconds "
          "constructionSeconds samplingSeconds diagnosticsSeconds cpuSeconds gcSeconds evaluations alignedDraws "
          "minMeanEss minBulkEss minTailEss maxRHat maxMeanError warningCoordinates fingerprint error").split()


def load(texts, repetitions, draws, warm):
    if not (1 <= repetitions <= 100 and 4 <= draws <= 100000 and 0 <= warm <= 100000):
        raise ValueError("Invalid protocol parameters")
    records = {}
    for text in texts:
        for values in csv.reader(io.StringIO(text)):
            if not values or values[0] != "vectorPerformance":
                continue
            if values == FIELDS:
                continue
            if len(values) != len(FIELDS):
                raise ValueError("Wrong field count")
            row = dict(zip(FIELDS, values))
            f, m, w, r = row["fixture"], row["method"], int(row["workers"]), int(row["round"])
            key = (f, m, w, r)
            if key in records or f not in FIXTURES or m not in METHODS or w not in WORKERS or not -2 <= r < repetitions:
                raise ValueError("Duplicate/unexpected case")
            if (int(row["seed"]), int(row["draws"]), int(row["warmUp"])) != (420013 + 7919 * r, draws, warm):
                raise ValueError("Seed/work mismatch")
            state = row["status"]
            if state not in ("Complete", "Incomplete", "Failed"):
                raise ValueError("Unknown status")
            def number(field, optional=False):
                v = float(row[field])
                if optional and math.isnan(v):
                    return v
                if not math.isfinite(v) or v < 0:
                    raise ValueError("Invalid numeric value: " + field)
                return v
            number("cpuSeconds", True)
            number("gcSeconds")
            if state == "Failed":
                if row["fingerprint"] or not row["error"] or any(int(row[k]) != -1 for k in ("evaluations", "alignedDraws", "warningCoordinates")):
                    raise ValueError("Invalid failure accounting")
                if not all(math.isnan(float(row[k])) for k in ("wallSeconds", "constructionSeconds", "samplingSeconds", "diagnosticsSeconds", "minMeanEss", "minBulkEss", "minTailEss", "maxRHat", "maxMeanError")):
                    raise ValueError("Failed run fabricated estimates")
            else:
                wall = number("wallSeconds")
                phases = sum(number(k) for k in ("constructionSeconds", "samplingSeconds", "diagnosticsSeconds"))
                if wall <= 0 or float(row["samplingSeconds"]) <= 0 or not math.isclose(wall, phases, rel_tol=1e-10, abs_tol=1e-12):
                    raise ValueError("Timing phases do not partition wall time")
                n, cost = int(row["alignedDraws"]), int(row["evaluations"])
                if not 0 <= n <= draws or not 4 <= cost <= 400000000:
                    raise ValueError("Invalid work accounting")
                if state == "Complete" and (n != draws or row["error"]):
                    raise ValueError("Inconsistent completion")
                if state == "Incomplete" and (n >= draws or not row["error"]):
                    raise ValueError("Inconsistent cap status")
                if not re.fullmatch(r"[0-9a-f]{64}", row["fingerprint"]):
                    raise ValueError("Invalid trace fingerprint")
                d = 32 if f.endswith("32") else 8
                if not 0 <= int(row["warningCoordinates"]) <= d:
                    raise ValueError("Invalid warning count")
                for field in ("minMeanEss", "minBulkEss", "minTailEss"):
                    ess = number(field, True)
                    if math.isfinite(ess) and not 0 < ess <= 4 * n:
                        raise ValueError("Invalid ESS")
                number("maxRHat", True); number("maxMeanError", n < 4)
                if n < 4 and any(not math.isnan(float(row[k])) for k in ("minMeanEss", "minBulkEss", "minTailEss", "maxRHat", "maxMeanError")):
                    raise ValueError("Insufficient trace fabricated diagnostics")
            records[key] = row
    expected = set(itertools.product(FIXTURES, METHODS, WORKERS, range(-2, repetitions)))
    if set(records) != expected:
        raise ValueError("Missing case/worker/round, including JVM warm-up rounds")
    invariant = ("status", "evaluations", "alignedDraws", "minMeanEss", "minBulkEss", "minTailEss", "maxRHat", "maxMeanError", "warningCoordinates", "fingerprint", "error")
    for f, m, r in itertools.product(FIXTURES, METHODS, range(-2, repetitions)):
        good = [records[f, m, w, r] for w in WORKERS if records[f, m, w, r]["status"] != "Failed"]
        if any(any(x[k] != good[0][k] for k in invariant) for x in good[1:]):
            raise ValueError("Worker-dependent non-timing result")
    return records


def summarize(records, repetitions):
    print("| Fixture / method / workers | Complete | Wall ms | Paired wall speedup | Sampling speedup | Diagnostic % | Min mean ESS/s | Max R-hat | Max mean error | Warning runs |")
    print("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
    for f, m, w in itertools.product(FIXTURES, METHODS, WORKERS):
        rows = [records[f, m, w, r] for r in range(repetitions)]
        bases = [records[f, m, 1, r] for r in range(repetitions)]
        complete = sum(x["status"] == "Complete" for x in rows)
        def med(values):
            return f"{stats.median(values):.2f}" if all(math.isfinite(v) for v in values) else "N/A"
        if complete != repetitions or any(x["status"] != "Complete" for x in bases):
            cells = ["N/A"] * 8
        else:
            cells = [med([1000 * float(x["wallSeconds"]) for x in rows]),
                     med([float(b["wallSeconds"]) / float(x["wallSeconds"]) for b, x in zip(bases, rows)]),
                     med([float(b["samplingSeconds"]) / float(x["samplingSeconds"]) for b, x in zip(bases, rows)]),
                     med([100 * float(x["diagnosticsSeconds"]) / float(x["wallSeconds"]) for x in rows]),
                     med([float(x["minMeanEss"]) / float(x["wallSeconds"]) for x in rows]),
                     f"{max(float(x['maxRHat']) for x in rows):.3f}" if all(math.isfinite(float(x['maxRHat'])) for x in rows) else "N/A",
                     f"{max(float(x['maxMeanError']) for x in rows):.4f}" if all(math.isfinite(float(x['maxMeanError'])) for x in rows) else "N/A",
                     str(sum(int(x["warningCoordinates"]) > 0 for x in rows))]
        print(f"| {f} / {m} / {w} | {complete}/{repetitions} | " + " | ".join(cells) + " |")
    print("\nAll measured rounds retained; timing comparisons unavailable if any paired run failed/incomplete. Warm-up rows are validated but not summarized.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("logs", nargs="+", type=Path)
    parser.add_argument("--repetitions", type=int, required=True)
    parser.add_argument("--draws", type=int, default=4000)
    parser.add_argument("--warm-up", type=int, default=500)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--acl-script", type=Path)
    args = parser.parse_args()
    records = load([p.read_text(encoding="utf-8-sig") for p in args.logs], args.repetitions, args.draws, args.warm_up)
    if args.output:
        with args.output.open("x", encoding="utf-8", newline="") as stream:
            writer = csv.DictWriter(stream, FIELDS, quoting=csv.QUOTE_ALL, lineterminator="\n")
            writer.writeheader(); writer.writerows(records[k] for k in sorted(records))
        if args.acl_script:
            shell = shutil.which("pwsh") or shutil.which("powershell")
            if not shell:
                raise ValueError("PowerShell required for ACL hook")
            subprocess.run([shell, "-NoProfile", "-File", str(args.acl_script.resolve()), "-Paths", str(args.output.resolve())], check=True)
    print(f"Validated {len(records)} run records for the full expected grid, including all JVM warm-ups.")
    print("All-round statuses: " + ", ".join(f"{s}={sum(r['status'] == s for r in records.values())}"
          for s in ("Complete", "Incomplete", "Failed")))
    summarize(records, args.repetitions)


if __name__ == "__main__":
    main()
