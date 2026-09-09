# GVM mixtures versus Gaussian mixtures: research backlog

Status: modern.23 delivers the [fixed mixture kernel](GVM_MIXTURES.md), a focused
literature baseline and an [initial controlled GMM comparison](MODELING_CAPABILITIES_ACCEPTANCE.md).
Production parameter fitting, stronger matched-accuracy/parameter-budget studies and
application tracking systems remain research. Neither novelty nor broad superiority
has been established. The initial comparison's GVM-generated truths and simple fitters
must not be mistaken for a conclusive application study.

## Scientific question

Can mixtures of linear-angular GVM components represent curved, wrapped and
multimodal uncertainty more economically than traditional Gaussian mixtures?
Space situational awareness and space-object tracking motivate the comparison,
but generic density representation and inference are the initial library scope.

## Proposed order

1. Literature baseline: Horwood-Poore and subsequent cylindrical/directional mixture
   work, Gaussian-mixture uncertainty representations, fitting and identifiability.
   Record prior art, open implementations and licenses; a missing license is not
   permission to reuse code. Distinguish ordinary von Mises mixtures from joint GVM.
2. Generic immutable mixture kernel: normalized weights, full log-sum density,
   sampling, component responsibilities and explicit common linear-angular coordinates.
   Test one-component reduction, permutation invariance, duplicate components,
   angular seam invariance and Gaussian/local-concentration limits.
3. Fair representation study: matched parameter/storage and training budgets, followed
   by matched held-out accuracy and total runtime. Include local nearly Gaussian,
   wrapped seam-crossing, curved and separated-mode synthetic reference laws.
   Compare ordinary GMMs in declared local charts, appropriately wrapped controls,
   and GVM mixtures in a common measure; do not punish GMMs with an invalid chart.
4. Metrics and fitting: KL in both directions, Bhattacharyya overlap, linear-angular
   MI, held-out log score and declared-region probability error. Componentwise
   divergence averages are not full-mixture divergences. Keep training, evaluation
   and reference samples independent; retain every failure and numerical refusal.
5. Application assessment: determine whether the generic findings transfer to
   representative uncertainty states relevant to tracking, using independently
   specified reference data and a separately approved application scope.

Report cases where GMMs win, not only curved/wrapped examples favorable to GVM.
Measure component count needed for comparable accuracy, tail and region errors,
training cost, density cost, sampling cost and memory. Neither model family gets
automatic credit for tracking accuracy from density-fit results alone.

## Boundaries

No sensor-report ingestion, PDF fusion, filtering update, orbit propagator or
published GVM tracking pipeline is authorized by this backlog entry. Those require
separate application and publication review consistent with the existing project
boundary; this document makes no patent-scope determination.

Related: [GVM](GAUSS_VON_MISES.md), [Gaussian mixtures](DISTRIBUTION_CONSTRUCTIONS.md),
[Monte Carlo information](MONTE_CARLO_INFORMATION.md), [roadmap](../ROADMAP.md).
