"""Compare captured Figaro numerical-study output with independent 350-digit formulas.

Usage: python -B tools/capability_reference.py STUDY_LOG
Writes machine-readable results to stdout; never manufactures missing Figaro output.
"""
import hashlib
import json
import platform
import sys
from pathlib import Path
import mpmath as m


def compare(path):
    raw = Path(path).read_bytes()
    rows = []
    with m.workdps(350):
        for line in raw.decode("utf-8-sig").splitlines():
            start = line.find("CAPABILITY,")
            if start < 0:
                continue
            _, family, parameter, point, *values = line[start:].split(",")
            s, x = m.mpf(parameter), m.mpf(point)
            if family in ("GEV", "GPD"):
                a = m.log(1+s*x)/s  # High precision, not the implementation's series.
                if family == "GEV":
                    cdf = m.exp(-m.exp(-a))
                    expected = [-(1+s)*a-m.exp(-a), cdf, 1-cdf]
                else:
                    survival = m.exp(-a)
                    expected = [-(1+s)*a, 1-survival, survival]
            else:
                lower = m.matrix([[1, 0], [1, x]])
                matrix = lower*lower.T
                d = 2
                partition = s*d/2*m.log(2)+m.log(m.pi)/2+sum(m.loggamma((s-i)/2) for i in range(d))
                if family == "WISHART":
                    density = (s-d-1)/2*m.log(m.det(matrix))-sum(matrix[i,i] for i in range(d))/2-partition
                elif family == "INVERSE_WISHART":
                    inverse = matrix**-1
                    density = -(s+d+1)/2*m.log(m.det(matrix))-sum(inverse[i,i] for i in range(d))/2-partition
                else:
                    raise ValueError(f"Unknown family: {family}")
                expected = [density+d*m.log(2)+m.log(x)]
            actual = [m.mpf(v) for v in values]
            if len(actual) != len(expected):
                raise ValueError("Unexpected output column count")
            errors = [abs(a-e)/max(1, abs(e)) for a, e in zip(actual, expected)]
            passed = all(m.isfinite(a) and err < m.mpf("5e-14") for a, err in zip(actual, errors))
            rows.append(dict(family=family, parameter=parameter, point=point,
                             actual=values, expected=[m.nstr(v, 45) for v in expected],
                             normalized_errors=[float(e) for e in errors], passed=passed))
    if len(rows) != 38:
        raise ValueError(f"Expected all 38 Figaro rows; got {len(rows)}")
    return dict(input_file=Path(path).name, input_sha256=hashlib.sha256(raw).hexdigest(),
                python=platform.python_version(), mpmath=m.__version__, precision_digits=350,
                tolerance="5e-14 * max(1, abs(reference))", rows=rows,
                passed=all(r["passed"] for r in rows))


if __name__ == "__main__":
    result = compare(sys.argv[1])
    print(json.dumps(result, indent=2, allow_nan=False))
    sys.exit(0 if result["passed"] else 1)
