# Information metrics across common families

## Overview

`ScalarDivergence` compares two new scalar kernels, `CountDivergence` compares two new
count kernels, and `DiscreteInformation` operates on explicit finite probability
tables. This extends information diagnostics beyond GVM without pretending all
families have one closed-form formula or every scalar marginal specifies a joint MI.

KL is directed; Bhattacharyya is symmetric negative-log overlap; MI is dependence
between two variable blocks **within one joint law**. All values are in nats. Divide
by `math.log(2)` for bits. None is a replacement for a calibrated Mahalanobis score.
Existing [GVM diagnostics](GVM_DIAGNOSTICS.md) and [GVM MI](GVM_MUTUAL_INFORMATION.md)
remain separate, unchanged entry points.

## Quick start: three steps

1. Construct two laws in the same units:

   ```scala
   import com.cra.figaro.library.atomic.continuous.*
   val p = StudentTDistribution(5)
   val q = StudentTDistribution(8, .4, 1.2)
   ```

2. Compute the requested quantity:

   ```scala
   val comparison = ScalarDivergence.kl(p, q)
   ```

3. Inspect status before using its optional value:

   ```scala
   import com.cra.figaro.library.atomic.InformationMetricStatus.*
   comparison.status match {
     case Analytic | Estimated => println(s"${comparison.value.get} nats; error estimate ${comparison.errorEstimate}")
     case Infinite => println("Mathematically infinite divergence")
     case other => println(s"No usable value: $other")
   }
   ```

The example gives about `0.0599146464` nats. It is a guarded numerical result, not an
exact formula or statistical confidence interval. Run `CommonDistributionsExample`
as shown in the [family guide](COMMON_DISTRIBUTIONS.md).

## Support matrix

Comparisons are **within the same family**. Unlike-family comparisons return
`Unsupported`, including Student t versus Cauchy even when df=1. No implicit unit,
support or parameter conversion is performed.

| Family | KL(P||Q) | Bhattacharyya |
| --- | --- | --- |
| Student t | Guarded numerical integration | Guarded numerical integration |
| Cauchy | Analytic | Guarded numerical integration |
| Laplace | Analytic | Analytic for equal location or equal scale; numerical otherwise |
| Lognormal | Analytic through Gaussian log coordinates | Analytic through Gaussian log coordinates |
| Weibull | Analytic for arbitrary admitted shapes/scales | Analytic for equal shape; numerical otherwise |
| Triangular | Support infinity or numerical integration | Disjoint-support infinity or numerical integration |
| Kumaraswamy | Analytic for common a; numerical otherwise | Analytic for common a; numerical otherwise |
| Negative binomial | Point-mass/common-r analytic reductions; tail-bounded sum otherwise | Point-mass/common-r analytic reductions; tail-bounded sum otherwise |
| Hypergeometric | Finite sum or support infinity | Finite sum or disjoint-support infinity |
| Explicit finite table | Finite-category sum | Stable log-overlap sum |

Every same-object/identically parameterized pair has an exact-zero shortcut. Analytic
does not mean exact binary arithmetic: a requested tolerance below its roundoff allowance
can be refused. Finite sums are labeled `Estimated` because their floating-point
evaluation is not a symbolic exact number.

MI support added here is **explicit finite joint tables**. A negative-binomial marginal
and another marginal do not determine their dependence; supplying two marginal kernels
is insufficient. Scalar-family joint models, copulas, multivariate Gaussian partitions,
conditional MI and sample-based MI estimators remain in the [cross-family roadmap](INFORMATION_METRICS_ROADMAP.md).

## Public API reference

| Method | Parameters and defaults | Returns |
| --- | --- | --- |
| `ScalarDivergence.kl(p,q,tolerance=1e-6,maxEvaluations=16384,cancelled=()=>false)` | Non-null `ScalarDistribution` pair; positive finite tolerance; work budget 1–65536; non-null cancellation callback | `InformationMetricResult`, directed KL |
| `ScalarDivergence.bhattacharyya(p,q,...)` | Same arguments/defaults; both orientations are charged during numeric integration | Same result type, symmetric divergence |
| `CountDivergence.kl(p,q,tolerance=1e-8,maxTerms=10000,cancelled=()=>false)` | Non-null `CountDistribution` pair; positive finite tolerance; 1–100000 count terms | Same result type, directed KL |
| `CountDivergence.bhattacharyya(p,q,...)` | Same count arguments/defaults | Same result type, symmetric divergence |
| `DiscreteInformation.kl(p,q,tolerance=1e-8)` | Matching nonempty sequences in **explicit category order**, at most 100000 entries | Same result type, directed finite-table KL |
| `DiscreteInformation.bhattacharyya(p,q,tolerance=1e-8)` | Same category requirements | Same result type; rare overlap is retained in log space |
| `DiscreteInformation.mutualInformation(joint,tolerance=1e-8)` | Nonempty rectangular joint probability table, at most 100000 cells | Same result type, MI between row and column variables |

Table entries must be finite and nonnegative and sum to one within `1e-12`. Only that
rounding discrepancy is normalized; these are probability inputs, not arbitrary
unnormalized weights. Zero categories/rows/columns are allowed. Ragged, empty or
oversized tables and invalid controls throw `IllegalArgumentException`.

`InformationMetricResult` is immutable with these public fields:

- `status`: `Analytic`, `Estimated`, `Infinite`, `Unsupported`, `BudgetExhausted`,
  or `NumericallyUnresolved`.
- `value`: finite `Some(nats)` for successful calculations; `Some(+infinity)` only
  for `Infinite`; None for every refusal.
- `errorEstimate`: absolute allowance in nats; positive infinity if unavailable.
  It is a numerical estimate, not a confidence interval or certified enclosure.
- `evaluations`: actual integrand/count/table terms; analytic setup and preflight
  calculations are excluded.
- `method`: diagnostic label such as `analytic`, `probability-quadrature`,
  `bounded-count-tail`, `finite-sum`, `finite-table`, `identity` or `support`.

Standard case-class construction/copy/matching does not perform a calculation or
certify manually supplied values. Compiler signatures are in the
[shared atomic](api/com.cra.figaro.library.atomic.md),
[continuous](api/com.cra.figaro.library.atomic.continuous.md) and
[discrete](api/com.cra.figaro.library.atomic.discrete.md) references.

## Three common patterns

### 1. Compare continuous uncertainty assumptions

```scala
val robust = ScalarDivergence.kl(StudentTDistribution(5), StudentTDistribution(8,.4,1.2))
val overlap = ScalarDivergence.bhattacharyya(WeibullDistribution(1.7,2.3), WeibullDistribution(2.4,1.4))
println((robust.status, robust.value, overlap.status, overlap.value))
```

Use KL when the direction P-to-Q matters; use Bhattacharyya for symmetric overlap.
Do not compare laws with inconsistent physical units or interpret a scalar difference
in means as a full distribution comparison. Numerical methods may refuse valid inputs.

### 2. Compare overdispersion, including a changed shape

```scala
import com.cra.figaro.library.atomic.discrete.*
val counts = CountDivergence.kl(
  NegativeBinomialDistribution(2.5,.4), NegativeBinomialDistribution(3.2,.6))
println((counts.value, counts.errorEstimate, counts.evaluations))
```

This gives about `0.2796974093` nats. Common-r pairs use a closed form; unequal-r pairs
sum terms with an explicit omitted-tail allowance. The remaining tail is never
renormalized away. If a larger count range is needed, increase `maxTerms` within its
cap and inspect the new status; increasing it cannot fix floating-point precision.

### 3. Quantify dependence in a finite joint model

```scala
import com.cra.figaro.library.atomic.DiscreteInformation
val independent = Vector(Vector(.12,.28), Vector(.18,.42))
val perfectlyLinked = Vector(Vector(.5,0.0), Vector(0.0,.5))
println(DiscreteInformation.mutualInformation(independent).value) // approximately zero
println(DiscreteInformation.mutualInformation(perfectlyLinked).value) // log(2) nats = 1 bit
```

Both examples specify a joint table. Two separate equal marginals would not distinguish
independence from perfect linkage. The implementation checks MI against KL of the
joint versus the product of its marginals in regression tests.

## Numerical method, budgets and gotchas

Scalar numerical paths integrate in probability coordinates, using
`u=t^2/(t^2+(1-t)^2)` to soften endpoint behavior. Triangular support/mode and Laplace
center breakpoints are split explicitly. Gauss-Legendre order doubles from 8 to a hard
cap of 512; at least three grids are required. The estimate uses eight times the larger
of two successive differences plus operand-sensitive roundoff. Exhausting the caller
budget gives `BudgetExhausted`; reaching the fixed numerical-order/precision limit
gives `NumericallyUnresolved`. There is no certified general quadrature error bound.

Bhattacharyya uses the bounded identity
`affinity = E_m[2*sqrt(p*q)/(p+q)]`, where `m=(p+q)/2`. This avoids an unbounded
`sqrt(q/p)` integrand. The mixture expectation is the average of the P and Q
expectations, so no new mixture sampler is needed. Small coefficient errors are
converted to divergence-nat errors before accepting a result.

Numeric Student t comparisons initially require df in `[.25,1000]`; Weibull and
Kumaraswamy numerical shapes are in `[.1,100]`. Analytic reductions retain the kernel
ranges. IQR scale contrast and median separation in units of the smaller IQR are
limited to `1e6`; collapsed/nonfinite quantile geometry is refused. Work budgets count
integrand terms (one quantile and two log densities each), not preflight IQR/median
queries or bounded quadrature-node setup. They are not wall-clock limits.

For negative binomial, consecutive probabilities have ratio
`(1-p)*(k+r)/(k+1)`. Beyond the current cut, a geometric upper ratio bounds the mass
tail. For KL the change in the log mass ratio is also bounded, controlling the omitted
absolute contribution; Bhattacharyya uses the geometric ratio of square-root products.
The analytic tail inequalities are evaluated in floating point with an additional
heuristic allowance. Finite hypergeometric sums include all required support terms.

Support mismatches can make KL mathematically infinite even when the missing mass is
tiny. Do not replace that result with a finite penalty by dropping categories. Conversely,
overflow, tiny overlap or insufficient work is **not** proof of an infinite divergence.
Close-to-equal laws may be indistinguishable within the numerical allowance; a rounded
zero is not an exact identity claim unless `method` is `identity`.

Cancellation callbacks are cooperative and their exceptions propagate. Thread
interruption is honored without clearing its flag. Finite-table operations have entry/
loop interrupt checks rather than a callback. There is no shared mutable integration
state, sample-based estimator, automatic algorithm selection outside the documented
analytic reductions, or performance-speedup claim.

## References, tests and related work

Analytic formulas are independently implemented and checked against direct integrals:
[Bauckhage, Weibull KL](https://arxiv.org/abs/1310.3713),
[Chyzak and Nielsen, Cauchy KL](https://arxiv.org/abs/1905.10965), and
[information-measure definitions](https://ocw.mit.edu/courses/6-441-information-theory-spring-2016/pages/lecture-notes/).
Lognormal invariance gives the Gaussian formulas in log coordinates. Common-shape
Weibull/Kumaraswamy cases reduce to exponential/beta-like transforms. The implementation
does not copy research-source code or add a runtime dependency.

[Independent 50-digit density integrals and count sums](../tools/common_metrics_reference.py)
cover twelve scalar pairs and three count pairs. Tests check those oracles, symmetry,
direction, equal-variance Gaussian/Mahalanobis reductions, support infinity, work caps,
weak separation, cancellation, concurrency and joint-table identities. Selected
integrals are refined to 70 digits. Run the checks from the
[family guide](COMMON_DISTRIBUTIONS.md); see [milestone acceptance](COMMON_DISTRIBUTIONS_ACCEPTANCE.md)
and the [cross-family roadmap](INFORMATION_METRICS_ROADMAP.md) for delivery status and
remaining scope.
