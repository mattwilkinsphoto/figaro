"""80-digit independent equation (5.7) fixtures; mpmath==1.3.0, no files written.

Run with python -B. Uses direct Bessel deficits with high precision, not the
production cancellation-avoiding asymptotic coefficient combination.
"""
import mpmath as mp

mp.mp.dps = 80
for value in ['0','0.1','4.5','50','50.0001','1000','100000000']:
    k = mp.mpf(value)
    b1 = 1-mp.besseli(1,k)/mp.besseli(0,k)
    b2 = 1-mp.besseli(2,k)/mp.besseli(0,k)
    offset = mp.acos(b2/(2*b1)-1)
    weight = b1*b1/(4*b1-b2)
    print(value,'offset',mp.nstr(offset,40),'angularWeight',mp.nstr(weight,40), flush=True)
