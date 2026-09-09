# Fixed-budget numerical information metrics

## Overview: when to use this

`MonteCarloInformation` estimates directed KL, Bhattacharyya divergence and mutual
information for explicitly normalized continuous vector laws. It reuses the
`VectorImportance.Proposal` sampling/log-density contract, so Gaussian mixtures,
elliptical t, Gaussian, box and caller-defined laws can share it.

Prefer an existing analytic API for Gaussians and supported scalar families. Use
this opt-in API when an analytic reduction is unavailable, especially for mixtures.
It is neither a generic exact divergence nor an automatic precision policy.

## Quick start

1. Supply two normalized laws in the same coordinates/base measure.
2. Choose a fixed draw budget and seed.
3. Inspect both the estimate and its **Monte Carlo** standard error.

```scala
import com.cra.figaro.algorithm.sampling.{VectorImportance as V,MonteCarloInformation as I}
import com.cra.figaro.library.atomic.continuous.MultivariateGaussianDistribution as G
def g(m: Double)=V.Gaussian(G(Vector(m),Vector(Vector(1.0))))
val p=V.Mixture(Vector(.3,.7),Vector(g(-4),g(4)))
val q=V.Mixture(Vector(.7,.3),Vector(g(-4),g(4)))
val result=I.kl(p,q,I.Config(draws=50000,seed=93))
println((result.value,result.mcse,result.status))
```

## API reference

| Function | Parameters | Returns / example |
| --- | --- | --- |
| `Config(draws=10000,seed=43,randomAlgorithm=default)` | 2–1,000,000 fixed draws, private stream seed, named RNG | Validated immutable policy; `I.Config(draws=50000)` |
| `kl(p,q,config)` | Matching normalized laws; q positive where p contributes | `Result` for E_p[log p−log q]; `I.kl(p,q)` |
| `bhattacharyya(p,q,config)` | Matching normalized laws | `Result` for negative log affinity; `I.bhattacharyya(p,q)` |
| `mutualInformation(joint,first,second,config)` | One normalized joint and its **exact** leading/trailing marginals | `Result` for KL(joint, product marginals); see pattern 3 |

`Result` fields:

- `status`: `Estimated` or `NumericallyUnresolved`. Estimated means computation
  completed, not that the answer met a requested tolerance.
- `value: Option[Double]`: nats. KL/MI estimates retain negative sampling noise;
  they are not clipped to manufacture nonnegativity.
- `mcse: Option[Double]`: plug-in standard error; for Bhattacharyya this is a delta
  approximation after applying negative log. Not a confidence interval or bound.
- `rawMean`, `rawMcse`: sampled log-ratio mean/SE for KL and MI; affinity mean/SE
  for Bhattacharyya. Keeping affinity visible helps diagnose tiny-overlap results.
- `evaluations`: two complete law-density calls per KL/Bhattacharyya draw; three
  per MI draw. Internal mixture components, factorization and callback work are
  not individually counted.
- `method`, `config`, `randomProvider`: provenance. Record the input laws too.

The runner retains only streaming moments, not all draws. Invalid arguments throw
`IllegalArgumentException`; nonfinite integrands/moment overflow throw
`ArithmeticException`, without claiming mathematical infinity. Interruption
throws and preserves the flag; callbacks that never return cannot be preempted.

## Three common patterns

### 1. Compare mixture weights, means and covariances

Use the quick start to compare full mixture densities. The calculation evaluates
**every active component**, not the selected component alone. Reordering a mixture
does not change its density. Moment matching it to a Gaussian would answer a
different question and is not done here.

For an existing GMM kernel, an adapter can preserve zero-weight components by
filtering them before constructing the positive-weight proposal mixture:

```scala
import com.cra.figaro.library.atomic.continuous.GaussianMixtureDistribution
def adapt(law: GaussianMixtureDistribution): V.Proposal = {
  val active=law.weights.indices.filter(i => law.weights(i)>0).toVector
  V.Mixture(active.map(law.weights),active.map(i => V.Gaussian(law.components(i))))
}
// Proposal mixtures allow at most 32 active components; larger GMMs need a custom adapter.
```

### 2. Compare overlap with Bhattacharyya

```scala
val overlap=I.bhattacharyya(p,q,I.Config(draws=50000,seed=5))
println((overlap.rawMean,overlap.rawMcse,overlap.value))
```

The sampler draws from `(p+q)/2`, making the affinity integrand
`2 sqrt(p q)/(p+q)` bounded in [0,1]. This avoids an unbounded one-sided importance
ratio. Negative log introduces finite-sample bias; delta MCSE can be poor near
zero overlap. Zero estimated affinity, relative affinity SE at least one, or a
non-unit constant affinity with zero empirical SE returns an unresolved result.
These are engineering guards, not certified accuracy tests. Identical laws can
correctly return zero divergence and zero empirical SE.

### 3. Dependence between coordinate blocks

```scala
val joint=G(Vector(0.0,0.0),Vector(Vector(1.0,.8),Vector(.8,1.0)))
val mi=I.mutualInformation(V.Gaussian(joint),
  V.Gaussian(joint.marginal(Vector(0))),V.Gaussian(joint.marginal(Vector(1))))
// For this Gaussian prefer GaussianInformation.mutualInformation for an analytic answer.
```

For a GMM use `adapt(jointMixture)` and adapters for its `.marginal(...)` kernels;
for multivariate t use `V.StudentT` on the joint and its marginals. Coordinates
must be leading/trailing blocks in the same order. Supplying arbitrary separately
fitted laws estimates a different KL, **not** the true MI. No callback inspection
can prove the marginal relationship.

## Gotchas

- Laws must be normalized; unlike posterior importance, unknown normalizers do
  not cancel between two arbitrary distributions. Do not pass unnormalized
  posterior targets as proposals.
- MCSE assumes independent draws and finite integrand variance. A supplied sampler
  can violate this, and rare contributions can be missed. Zero MCSE does not prove
  exactness or independence. No health-based filtering of runs is performed.
- The mathematical Bhattacharyya divergence is symmetric, but separate stochastic
  evaluations in reverse order need not produce identical finite-sample estimates.
- Support failure encountered at sampled points is not a proof of infinite KL;
  it may reflect underflow or a broken density callback. Use an analytic support
  argument/API when infinity must be established.
- Work limits bound draws, not time or arbitrary nested callback cost. Callbacks
  and custom proposals must be pure/thread-safe for concurrent independent runs.
- There is no universal continuous/discrete/mixed-measure adapter. Both laws must
  use the same vector Lebesgue measure. Use discrete metric APIs for masses.

## Related and evidence

[Gaussian information](DISTRIBUTION_CONSTRUCTIONS.md), [GVM diagnostics](GVM_DIAGNOSTICS.md),
[multivariate t](MULTIVARIATE_STUDENT_T.md), [cross-family roadmap](INFORMATION_METRICS_ROADMAP.md).
[Hershey and Olsen](https://research.ibm.com/publications/approximating-the-kullback-leibler-divergence-between-gaussian-mixture-models)
motivate explicit approximations for mixtures; this implementation uses independent
sampling of the full laws, not their variational approximation. Gaussian analytic
controls, separated-mixture limits, label permutation, joint Gaussian MI, support
failure and unresolved-overlap tests accompany the API. These tests do not certify
every caller-defined law.
