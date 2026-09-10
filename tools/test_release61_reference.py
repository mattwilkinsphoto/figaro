"""Independent 60-digit conditional-density and circular-regression controls."""
import unittest
import mpmath as m


class Release61Reference(unittest.TestCase):
    def setUp(self):
        m.mp.dps = 60

    def test_conditional_t_density_ratio(self):
        def t1(x, df, mu, shape):
            return m.gamma((df+1)/2)/(m.gamma(df/2)*m.sqrt(df*m.pi*shape)) * (1+(x-mu)**2/(df*shape))**(-(df+1)/2)
        x, y, rho, df = map(m.mpf, ('.4', '2', '.6', '5'))
        square = (x*x-2*rho*x*y+y*y)/(1-rho*rho)
        joint = m.gamma((df+2)/2)/(m.gamma(df/2)*df*m.pi*m.sqrt(1-rho*rho)) * (1+square/df)**(-(df+2)/2)
        conditional = t1(x, df+1, rho*y, (df+y*y)/(df+1)*(1-rho*rho))
        self.assertLess(abs(joint/t1(y,df,0,1)-conditional), m.mpf('1e-55'))
        self.assertLess(abs(m.log(conditional)-m.mpf('-1.30876906329386316159812749523427892347321940306595533487257')),m.mpf('1e-55'))

    def test_phase_reparameterization(self):
        old_mu, new_mu, old_sd, new_sd = map(m.mpf, ('1','-.3','2','.7'))
        alpha, beta, gamma = map(m.mpf, ('.4','1.3','-.8'))
        a, b = (new_mu-old_mu)/old_sd, new_sd/old_sd
        anew, bnew, gnew = alpha+beta*a+gamma*a*a/2, b*(beta+gamma*a), gamma*b*b
        for value in ('-3','.2','5'):
            x=m.mpf(value); zold=(x-old_mu)/old_sd; znew=(x-new_mu)/new_sd
            self.assertLess(abs(alpha+beta*zold+gamma*zold*zold/2-(anew+bnew*znew+gnew*znew*znew/2)),m.mpf('1e-55'))

    def test_resultant_is_periodic_likelihood_objective(self):
        residuals=list(map(m.mpf, ('3.1','-3.0','2.9')))
        weights=list(map(m.mpf, ('.2','.5','.3')))
        c=sum(w*m.cos(t) for w,t in zip(weights,residuals))
        s=sum(w*m.sin(t) for w,t in zip(weights,residuals))
        alpha=m.atan2(s,c)
        optimum=sum(w*m.cos(t-alpha) for w,t in zip(weights,residuals))
        self.assertLess(abs(optimum-m.sqrt(c*c+s*s)),m.mpf('1e-55'))
        for delta in ('-.4','.2','2'):
            candidate=sum(w*m.cos(t-alpha-m.mpf(delta)) for w,t in zip(weights,residuals))
            self.assertLess(candidate,optimum)


if __name__ == '__main__':
    unittest.main()
