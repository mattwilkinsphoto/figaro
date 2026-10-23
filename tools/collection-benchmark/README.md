# Standalone enumeration benchmark

See [method, protocol and interpretation](../../docs/CI_VALIDATION.md). This tool
uses JMH instead of hand-written JVM timing. It does not add a Figaro dependency.

From the repository root, with Python 3.11+ and JDK 17 on PATH:

```text
python -B tools/collection-benchmark/run.py --candidate target/out/jvm/scala-3.9.0/figaro/figaro_3-7.4.0.jar --output .cache/enumeration --profile full
```

The output must not exist; its parent must exist. `--java` and `--javac` can select
explicit JDK executables. Use `--profile smoke` only to validate the harness.
Windows access and optional offline pinned inputs are described in the guide.
Outputs include a frozen protocol, exact commands and logs, all JMH JSON, allocation
measurements and descriptive comparisons. No timing threshold is a pass criterion.

The Java harness is original Figaro validation code. Downloaded JMH components are
OpenJDK JMH 1.37 (GPLv2 with Classpath Exception); jopt-simple is MIT, Commons Math
is Apache-2.0, and Scala is Apache-2.0. Dependencies remain isolated, unmodified
benchmark inputs; preserve their embedded notices if redistributing evidence.
