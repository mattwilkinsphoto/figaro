# GVM Bhattacharyya divergence: numerical-method assessment

Status: research prototype and 12 tests, with [passing CI at `b5da340c`](https://github.com/mattwilkinsphoto/figaro/actions/runs/34185820661).
This document records the research, **not the public Scala API contract**. The follow-on
[guarded Scala API](GVM_BHATTACHARYYA.md) now has local acceptance and its own gates.
Reviewed 2026-09-07. The research itself changes no runtime or compiled release.
The prototype is independently written; no third-party implementation was copied.

## Outcome and intended use

A general fixed-kernel GVM comparison has a useful exact reduction: integrate out the
angle, factor out the Gaussian overlap, then evaluate an expectation of a bounded
positive function of the linear state. A second reduction expresses that expectation
as a convergent Fourier series with analytic Gaussian quadratic-phase terms.

The series is the preferred **production candidate**, subject to numerical hardening.
Unlike tensor integration, it does not require a grid exponential in linear dimension.
There is an analytic truncation bound, but it is not by itself a floating-point error
certificate. Severe cancellation and inefficient high-concentration tail bounds remain
explicit acceptance gaps. A finite closed-form expression for the general case is not
claimed, nor is a measured speedup.

This compares two already-defined probability laws; it neither ingests reports nor
constructs a fused or posterior GVM. [Existing scope boundaries](GAUSS_VON_MISES.md)
remain unchanged. Mutual information remains a separate, lower-priority research item.

## Definitions and sources

For joint densities p and q on the same real-vector/circle space:

```text
BC(p,q) = integral sqrt(p(x,theta) q(x,theta)) dx dtheta
DB(p,q) = -log BC(p,q)
```

BC is an affinity in (0,1] for these nonsingular fixed GVMs; DB is nonnegative and
symmetric, in nats, and is not generally a metric satisfying the triangle inequality.
These are the unskewed, alpha=1/2 definitions, not optimized Chernoff information.
See [Nielsen (2022), definitions in section 1.1](https://arxiv.org/html/2207.03745v2).

The derivation below combines Figaro's [joint GVM definition](GAUSS_VON_MISES.md)
with the [Bessel integral representation, DLMF 10.32.1](https://dlmf.nist.gov/10.32.E1)
and [Fourier expansion, DLMF 10.35.2](https://dlmf.nist.gov/10.35.E2).
The GVM specialization, quadratic-phase reduction and elementary tail bound below
are derived here; they are not attributed to Nielsen as a published GVM algorithm.
The reference sources are openly readable; no additional paywalled PDF is needed
for this assessment.

## Derivation

### 1. Eliminate the angular integral exactly

Write the two laws as Gaussian marginals Np(x), Nq(x) times conditional von Mises
densities, with centers mp(x), mq(x) and constant concentrations kp, kq. Set:

```text
a = kp/2; b = kq/2; delta(x) = mp(x) - mq(x)
D = sqrt(I0(kp) I0(kq))
r(x) = sqrt((a-b)^2 + 4ab cos(delta(x)/2)^2)
h(x) = I0(r(x)) / D
```

Adding the two cosine exponents inside the square root produces one sinusoid of
amplitude r. Its integral gives I0(r). Consequently:

```text
BC = integral sqrt(Np(x) Nq(x)) h(x) dx
0 < h(x) <= 1
```

This removes an entire numerical integration dimension. In particular, no angular
grid or angular quadrature-order agreement test is necessary for this method.
Computing r using the displayed sum of nonnegative terms also avoids subtracting
nearly equal squared concentrations for opposed centers.

### 2. Factor the Gaussian overlap

For means mup, muq and positive-definite covariances P,Q, let:

```text
S = (P+Q)/2
d = mup-muq
DG = (d' inverse(S) d)/8 + log(det(S)/sqrt(det(P) det(Q)))/2
C = 2 inverse(inverse(P)+inverse(Q))
m = inverse(inverse(P)+inverse(Q)) (inverse(P)mup + inverse(Q)muq)
```

Completing the square gives `sqrt(Np Nq) = exp(-DG) N(x;m,C)`. Thus:

```text
F = E[X~N(m,C)] h(X)
BC = exp(-DG) F
DB = DG - log(F)
```

N(m,C) is an auxiliary integration measure, not an output state estimate or fitted
GVM. The explicit inverses/determinants here are mathematical notation and research
oracle operations; a production implementation should use factorizations and solves.
Keep DG separate in log space: Gaussian separation should not underflow the entire
overlap before taking a logarithm.

### 3. Replace the remaining integral with a Fourier series

Expanding both angular exponentials and integrating their product gives:

```text
w0 = I0(a) I0(b) / D
wj = 2 Ij(a) Ij(b) / D, j >= 1
F = w0 + sum[j>=1] wj Re(phi(j))
phi(j) = E[exp(i j delta(X))], X~N(m,C)
```

The coefficients wj are nonnegative, and their full sum is `I0(a+b)/D <= 1`, so the
series is absolutely convergent. However, Re(phi(j)) can be negative: evaluating F
by this representation can have severe cancellation even though h itself is positive.

Because each GVM conditional center is quadratic in the linear state, under
`X=m+LZ`, `LL'=C`, `Z~N(0,I)`, its difference has the form:

```text
delta(X) = c + l'Z + Z'HZ/2
H = U diag(lambda_t) U'; v = U'l
log phi(j) = i j c
             - sum[t] log(1-i j lambda_t)/2
             - j^2 sum[t] v_t^2/(2(1-i j lambda_t))
```

Sum the eigenvalue-wise complex logarithms, continuously anchored at j=0. A single
principal square root of the determinant can choose the wrong branch in higher
dimensions; the eight-dimensional regression deliberately catches that error.
This extends the quadratic-phase machinery used in [GVM moments](GVM_MOMENTS.md),
but does not assume that the joint family is closed under density products.

### 4. Bound truncation without relying on order agreement

Retain harmonics 0 through M. Since `abs(phi(j)) <= 1`, the omitted contribution is
at most the sum of omitted positive coefficients. An elementary bound follows
directly from the positive power series for Ij:

```text
I(j+1,x) / I(j,x) <= x / (2(j+1)), x > 0
R = ab / (4(M+2)^2)
T_M = w(M+1)/(1-R), provided R < 1
abs(F - F_M) <= T_M
```

For each power-series summand the ratio is `x/(2(j+k+1))`; averaging these ratios
with positive weights proves the inequality. Successive coefficient ratios beyond
M+1 are bounded by R, giving the geometric tail. When either concentration is zero,
all positive harmonics vanish. If R>=1 the prototype reports an unavailable bound
(`infinity`), **not divergence of the series**.

If arithmetic were exact, intersect `[F_M-T_M,F_M+T_M]` with [0,1]. Positive endpoints
yield a DB interval by applying `DG-log(F)` in reverse endpoint order. If its lower
endpoint is zero, the upper DB bound is unresolved, not a reliable infinite distance.
Finite nonsingular GVMs here have positive overlap.

This is a truncation-only statement. The current arbitrary-precision prototype does
not propagate rounding or special-function error intervals. Tiny displayed tails,
or collapsed interval endpoints below the working precision, are **not** precision
certificates. Production must add numeric error accounting and explicit resolution status.

## Reproducible experiments

The [prototype](../tools/gvm_bhattacharyya_research.py) uses `mpmath==1.3.0`, generally
60 decimal digits. It is an optional research dependency, not a Figaro JVM dependency.
Use an isolated Python environment with that version available; the commands write
no files when run with `-B`:

```sh
python -B tools/gvm_bhattacharyya_research.py
python -B -m unittest discover -s tools -p 'test_gvm_bhattacharyya_research.py' -v
```

The CI workflow adds a separate Python research job for these tests. The Scala API
and runtime are unchanged; the research tests are not part of the 281 Scala regression count.

| Case | Retained highest harmonic M | Observation |
| --- | --- | --- |
| Curved one-dimensional pair | 8 | F=0.767698821999035501; angular truncation bound about 2.01e-16 |
| Same curved pair | 32 | DB=0.355509912840583168; agrees with independent positive adaptive integration |
| Equal Gaussian marginals, opposed angular centers, kp=kq=50 | 32 | Angular interval still includes zero; finite upper distance bound unresolved |
| Same opposed pair | 128 | At 60 digits, BC=3.409997134604561e-21 and DB=47.127575501871805; agrees with the exact circular reduction |
| kp=kq=1000 with linear angular coupling | 128 | Series value appears settled, but the elementary tail bound remains unavailable |
| Same concentrated pair | 512 | Elementary bound becomes finite; DB=0.629709489536869764 |

The full decimal fixtures are defined in `main()` in the prototype. The curved pair
uses `(mu, sd, alpha, beta, gamma, kappa)` equal to `(0.3,1.1,0.2,0.7,0.3,4.5)` and
`(-0.4,0.8,-0.5,-0.2,-0.15,1.2)`. Standard deviations, not variances, are passed to
the scalar fixture constructor. The concentrated pair has standard Gaussian marginals,
kp=kq=1000, and centers 0 and `0.01+0.1x`.

A precision stress run at 17 decimal digits gives roughly 2.12e-19 instead of
3.41e-21 for the opposed-k50 overlap, despite a formal truncation tail around
3.06e-172. That is about a 62-fold error in the overlap. This is an arbitrary-precision
stress experiment, not a measured Scala Double result; it demonstrates why merely
porting the formula and tail bound would be unsafe. The unchanged-center circular
special case can avoid this cancellation analytically.

The [12 tests](../tools/test_gvm_bhattacharyya_research.py) cover Gaussian reductions,
identical curved laws, identical physical angular conditionals despite different
canonical parameters, circular and one-uniform-conditional reductions, direct periodic
integration, pointwise Gaussian overlap factorization, positive adaptive linear
integration, symmetry, unit/angle shifts, two-dimensional tensor comparison, the
eight-dimensional branch case, tail bounds, unresolved intervals, finite-precision
cancellation and basic budgets.
The positive adaptive integral uses a 12-standard-deviation truncation with omitted
probability bounded by `erfc(12/sqrt(2))`, because h is at most one. Its quadrature
roundoff is checked at high precision, not certified by interval arithmetic.

## Expected value and remaining gates

The tensor reference needs order^n linear points after angle elimination. The series
instead needs dense O(n^3) preprocessing and O(M*n) quadratic-phase work, plus Bessel
coefficient evaluation. This is a substantial structural opportunity in moderate and
higher dimensions, not an order-of-magnitude benchmark result. Harmonic count depends
on concentrations and the demanded accuracy; cancellation may dominate either method.

The original production gates below now have a first bounded implementation in the
[Scala API](GVM_BHATTACHARYYA.md). It explicitly limits nonidentity comparisons to
concentrations through 50 and dimensions through 32, uses estimated rounding allowances
and can return unresolved outcomes. Wider ranges and certified rounding remain open.
The original gates remain useful when evaluating extensions:

1. Implement scaled/log-domain Bessel coefficients with tested errors and a hard
   harmonic budget. The elementary tail bound becomes inefficient as concentration
   grows: for kp=kq=1000 it needs M>248 to be available, even when the sum settles earlier.
   At concentrations allowed by the GVM kernel (up to 1e8), tighter bounds or an explicit
   unsupported/unresolved outcome are essential. Do not promise universal resolution.
2. Carry truncation and floating-point effects separately. Return resolution status,
   coefficient/log-coefficient information and distance uncertainty, not just one Double.
   Add exact Gaussian, circular and one-uniform shortcuts; avoid silent clipping as a
   substitute for resolving cancellation. Validate near-identical and tiny-overlap cases.
3. Port factorization-based Gaussian overlap and continuous-branch phase evaluation;
   test ill-conditioning, mismatched dimensions, extreme units, cancellation, memory
   budgets and concurrent reuse. The trusted Python fixture classes do not validate
   arbitrary user input and are not production-ready APIs.
4. Benchmark against the positive reference at matched **verified error**, including
   six-plus-dimensional cases, and retain independent high-precision fixtures. A fallback
   quadrature is not automatically certified just because the series was unresolved.

For equal Gaussian covariances and identical angular conditionals, DB reduces to
one eighth of squared Mahalanobis separation; directed Gaussian KL is one half.
Thus DB=KL/4 in that special case, not generally. This supplies a useful check without
conflating the two divergences.

The first bounded Scala implementation is now locally validated. Next validation work
is measured matched-accuracy performance, tighter concentration-dependent bounds and
stronger numerical error accounting. Mutual information and report-fusion functionality
are not included.

Related: [GVM diagnostics and KL](GVM_DIAGNOSTICS.md), [moments](GVM_MOMENTS.md),
[tensor reference](GVM_TENSOR_QUADRATURE.md), [order-comparison limitations](GVM_QUADRATURE_COMPARISON.md),
[roadmap](../ROADMAP.md), [wishlist](../WISHLIST.md).
