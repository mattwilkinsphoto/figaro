#!/usr/bin/env python3
"""Validate benchmark rows and summarize complete repeated-seed groups; standard library only.

Usage: python -B tools/summarize_calibration.py benchmark.log --repetitions 30
Optional --output writes normalized non-warm-up CSV; --acl-script grants each generated file.
Existing output is replaced only when it is already a calibration CSV.
"""
import argparse
import csv
import io
import math
from pathlib import Path
import shutil
import statistics
import subprocess


def load(text, repetitions):
    lines = [line for line in text.splitlines() if line.startswith('"calibration",')]
    if not lines:
        raise ValueError("No calibration CSV header/rows found")
    reader = csv.DictReader(lines)
    fields = reader.fieldnames
    rows = [r for r in reader if int(r["round"]) >= 0]
    groups = {}
    geometries = {"independent-2": 2, "correlated-2": 2, "narrow-2": 2,
                  "scaled-2": 2, "correlated-6": 6, "banana-2": 2}
    strategies = ("default", "joint-prior", "manual", "calibrated")
    for row in rows:
        if None in row or None in row.values():
            raise ValueError("Malformed row")
        key = row["geometry"], row["strategy"], int(row["round"]), row["method"]
        groups.setdefault(key, []).append(row)
    expected = {(g, s, r, m) for g in geometries for s in strategies
                for r in range(repetitions) for m in ("fixed", "precision")}
    if set(groups) != expected:
        raise ValueError("Missing/unexpected run groups; do not summarize a partial log")
    for (geometry, strategy, _, _), group in groups.items():
        rejected = group[0]["reason"] == "PilotRejected"
        if rejected:
            if strategy != "calibrated" or len(group) != 1 or group[0]["query"]:
                raise ValueError("Invalid pilot rejection row")
        else:
            d = geometries[geometry]
            expected_queries = {f"{prefix}{i}" for prefix in ("x", "square") for i in range(d)}
            if len(group) != 2 * d or {r["query"] for r in group} != expected_queries:
                raise ValueError("Missing/duplicate query rows")
        for r in group:
            total = float(r["totalSeconds"])
            if not math.isfinite(total) or total <= 0:
                raise ValueError("Invalid timing")
            if not math.isclose(total, float(r["productionSeconds"]) + float(r["pilotSeconds"]), abs_tol=1e-9):
                raise ValueError("Pilot-inclusive timing mismatch")
    return fields, rows, groups


def table(groups, repetitions):
    print("| Geometry / strategy | Pilot rejected | Fixed joint coverage | Fixed min ESS/total s | Precision success | Successful total ms | Precision joint coverage |")
    print("| --- | --- | --- | --- | --- | --- | --- |")
    pairs = sorted({(g, s) for g, s, _, _ in groups})
    for geometry, strategy in pairs:
        fixed = [groups[geometry, strategy, r, "fixed"] for r in range(repetitions)]
        adaptive = [groups[geometry, strategy, r, "precision"] for r in range(repetitions)]
        available = [g for g in fixed if g[0]["reason"] != "PilotRejected"]
        successes = [g for g in adaptive if g[0]["reason"] == "PrecisionReached"]
        adaptive_available = [g for g in adaptive if g[0]["reason"] != "PilotRejected"]
        cover = lambda runs: sum(all(r["covered"] == "true" for r in g) for g in runs)
        ess = [min(float(r["meanEssPerTotalSecond"]) for r in g) for g in available]
        ess = [v for v in ess if math.isfinite(v)]
        median_ess = f"{statistics.median(ess):.0f}" if ess else "unavailable"
        ms = f"{statistics.median(float(g[0]['totalSeconds']) * 1000 for g in successes):.1f}" if successes else "not reached"
        print(f"| {geometry} / {strategy} | {repetitions - len(available)}/{repetitions} | "
              f"{cover(available)}/{len(available)} | {median_ess} | {len(successes)}/{repetitions} | {ms} | "
              f"{cover(adaptive_available)}/{len(adaptive_available)} |")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=Path)
    parser.add_argument("--repetitions", type=int, required=True)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--acl-script", type=Path)
    args = parser.parse_args()
    if args.repetitions <= 0:
        raise ValueError("Repetitions must be positive")
    fields, rows, groups = load(args.log.read_text(encoding="utf-8-sig"), args.repetitions)
    if args.output:
        if args.output.exists() and not args.output.read_text(encoding="utf-8").startswith('"calibration",'):
            raise ValueError("Refusing to replace a non-calibration CSV")
        stream = io.StringIO(newline="")
        writer = csv.DictWriter(stream, fields, quoting=csv.QUOTE_ALL, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
        args.output.write_text(stream.getvalue(), encoding="utf-8", newline="\n")
        if args.acl_script:
            shell = shutil.which("pwsh") or shutil.which("powershell")
            if not shell:
                raise ValueError("PowerShell required for ACL hook")
            subprocess.run([shell, "-NoProfile", "-File", str(args.acl_script.resolve()),
                            "-Paths", str(args.output.resolve())], check=True)
    print(f"Validated {len(rows)} query/rejection rows in {len(groups)} complete run groups; warm-up excluded.")
    table(groups, args.repetitions)


if __name__ == "__main__":
    main()
