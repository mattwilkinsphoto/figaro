package com.cra.figaro.test.modernization

import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.library.atomic.discrete.*

// Independent 50-digit density integrals and count sums; tools/common_metrics_reference.py.
private[modernization] object CommonMetricFixtures {
  val scalar: Vector[(ScalarDistribution,ScalarDistribution,Double,Double)] = Vector(
    (StudentTDistribution(5.0,0.0,1.0),StudentTDistribution(8.0,0.4,1.2),0.0599146463826821,0.01588196195197922),
    (StudentTDistribution(1.0,0.0,1.0),StudentTDistribution(2.0,1.0,2.0),0.2090445803883217,0.053300983172327426),
    (CauchyDistribution(0.0,1.0),CauchyDistribution(1.0,2.0),0.22314355131420976,0.05656365211426578),
    (LaplaceDistribution(0.0,1.0),LaplaceDistribution(0.7,2.0),0.2914398324556501,0.08500353668399535),
    (LaplaceDistribution(0.0,1.0),LaplaceDistribution(1.0,1.0),0.36787944117144233,0.09453489189183562),
    (LogNormalDistribution(0.3,0.8),LogNormalDistribution(-0.2,1.3),0.2488214252491564,0.08357422126695524),
    (WeibullDistribution(1.7,2.3),WeibullDistribution(2.4,1.4),1.8220944873645857,0.15927632877769818),
    (WeibullDistribution(2.0,1.0),WeibullDistribution(2.0,3.0),1.3083356884473305,0.5108256237659907),
    (TriangularDistribution(0.0,0.3,1.0),TriangularDistribution(0.0,0.7,1.0),0.20830091697691272,0.052774100722482876),
    (TriangularDistribution(0.0,0.3,1.0),TriangularDistribution(-0.5,0.4,1.5),0.39131777848902,0.17577538303071658),
    (KumaraswamyDistribution(1.4,2.3),KumaraswamyDistribution(2.2,1.3),0.5508466300428785,0.14153484884079515),
    (KumaraswamyDistribution(1.4,2.3),KumaraswamyDistribution(1.4,3.1),0.04933309840052522,0.011096155797181114)
  )
  val count: Vector[(CountDistribution,CountDistribution,Double,Double)] = Vector(
    (NegativeBinomialDistribution(2.0,0.4),NegativeBinomialDistribution(2.0,0.6),0.40546510810816416,0.08082741081628106),
    (NegativeBinomialDistribution(2.5,0.4),NegativeBinomialDistribution(3.2,0.6),0.2796974092865718,0.055580170021492036),
    (HypergeometricDistribution(20,7,5),HypergeometricDistribution(20,9,5),0.13004467600217962,0.033282026577171206)
  )
}
