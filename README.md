Figaro Programming Language & Core Libraries
=
Figaro is a probabilistic programming language that supports development of very rich probabilistic models and provides reasoning algorithms that can be applied to models to draw useful conclusions from evidence. Both model representation and reasoning algorithm development can be challenging tasks.

Modernization status: this branch builds with JDK 17, sbt 1.13.0, and Scala 2.13.18. It preserves the original `com.cra.figaro` API namespace while replacing obsolete JSci and Akka runtime dependencies, removing unused legacy dependencies, and adding deterministic regression and reproducible-artifact CI gates. See [MODERNIZATION.md](MODERNIZATION.md) and [DEPENDENCIES.md](DEPENDENCIES.md) for decisions and evidence.

Build and verify from the repository root:

```text
sbt clean compile Test/compile
sbt "figaro / Test / testOnly com.cra.figaro.test.modernization.ProbabilityRegressionTest com.cra.figaro.test.modernization.SpecialFunctionsRegressionTest com.cra.figaro.test.learning.SerializationTest"
sbt "figaro / Compile / packageBin" "figaro / assembly"
```

The snapshot Maven coordinate is `io.github.mattwilkinsphoto:figaro_2.13:5.0.0-modern.2-SNAPSHOT`. Use `figaro / publishLocal` for local integration testing.

Figaro makes it possible to express probabilistic models using the power of programming languages, giving the modeler the expressive tools to create a wide variety of models. Figaro comes with a number of built-in reasoning algorithms that can be applied automatically to new models. In addition, Figaro models are data structures in the Scala programming language, which is interoperable with Java, and can be constructed, manipulated, and used directly within any Scala or Java program.

Figaro is free and is released under an [open-source license](https://github.com/p2t2/figaro/blob/master/LICENSE). For more information please see the [Figaro Release Notes](https://github.com/charles-river-analytics/figaro/releases/download/5.0.0.0/Figaro_Release_Notes.pdf) and [Figaro Tutorial](https://github.com/charles-river-analytics/figaro/releases/download/5.0.0.0/Figaro_Tutorial.pdf).
