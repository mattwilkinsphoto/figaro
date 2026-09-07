"""Print independent Scala fixtures; requires test-only mpmath==1.3.0. Writes no files."""
import mpmath as mp

mp.mp.dps = 80
for text in ("0", "1e-10", "0.1", "1", "1.0001", "3", "50", "50.0001", "100", "10000", "100000000"):
    kappa = mp.mpf(text)
    peak = kappa - mp.log(2 * mp.pi * mp.besseli(0, kappa))
    resultant = mp.besseli(1, kappa) / mp.besseli(0, kappa)
    print(f"({text}, {mp.nstr(peak, 20)}, {mp.nstr(resultant, 20)}),")
