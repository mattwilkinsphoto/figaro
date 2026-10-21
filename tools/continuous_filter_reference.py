"""Independent deterministic continuous-state references and all-outcome analysis (INF-12).

No Figaro or particle algorithm is used to construct reference posteriors.
Run prepare before Scala production; analyze never tunes the frozen acceptance gates.
"""
import argparse
import csv
import hashlib
import io
import json
import math
import os
from pathlib import Path
import platform
import subprocess
import sys
import time
import numpy as np

SCENARIOS = ("growth", "growth-sharp", "persistent-modes")
MODES = ("bootstrap", "guided", "auxiliary", "calibrated-auxiliary", "poor-auxiliary")
METRICS = ("mean", "second", "absolute", "positive")
TOL = np.array([1e-5, 1e-5, 1e-4, 1e-5, 2e-4])


class Output:
    def __init__(self, root, acl=None):
        self.root = Path(root).resolve()
        self.acl = acl
        if not self.root.exists():
            self.root.mkdir()
            self.grant(self.root)

    def grant(self, path):
        path = Path(path).resolve()
        assert path == self.root or self.root in path.parents
        if self.acl:
            subprocess.run(["pwsh", "-NoProfile", "-File", self.acl, "-Paths", str(path)], check=True,
                           stdout=subprocess.DEVNULL)

    def write(self, name, text):
        path = self.root / name
        with path.open("x", encoding="utf-8"):
            pass
        self.grant(path)
        path.write_text(text, encoding="utf-8")
        return path

    def json(self, name, value):
        return self.write(name, json.dumps(value, indent=2, allow_nan=False))


def normal(x, mu, sd):
    return np.exp(-.5 * ((x-mu)/sd)**2) / (math.sqrt(2*math.pi)*sd)


def simpson_uniform(values, dx):
    assert len(values)%2 == 1
    return float(dx/3*(values[0]+values[-1]+4*values[1:-1:2].sum()+2*values[2:-1:2].sum()))


def parameters(name):
    if name == "persistent-modes":
        return dict(a=.25, process_sd=.3, noise_sd=.5, initial_means=[-4., 4.], initial_sd=.5)
    return dict(a=.05, process_sd=math.sqrt(10), noise_sd=.25 if name == "growth-sharp" else 1.,
                initial_means=[0.], initial_sd=2.)


def drift(name, x, t):
    return x if name == "persistent-modes" else .5*x + 25*x/(1+x*x) + 8*math.cos(1.2*(t-1))


def fixture():
    rng = np.random.Generator(np.random.PCG64(20261022))
    result = {}
    for name in SCENARIOS:
        p = parameters(name)
        x = p["initial_means"][int(rng.integers(len(p["initial_means"])))] + p["initial_sd"]*rng.normal()
        rows = []
        for t in range(20):
            if t:
                x = drift(name, x, t) + p["process_sd"]*rng.normal()
            rows.append(dict(t=t, state=float(x), observation=float(p["a"]*x*x+p["noise_sd"]*rng.normal())))
        result[name] = dict(parameters=p, rows=rows)
    return result


def grid_filter(name, ys, limit, spacing, linear=False):
    x = np.linspace(-limit, limit, round(2*limit/spacing)+1)
    dx = x[1]-x[0]
    quadrature = np.full(x.size, dx)
    quadrature[[0, -1]] *= .5
    p = parameters(name)
    prior = sum(normal(x, m, p["initial_sd"]) for m in p["initial_means"])/len(p["initial_means"])
    mass = None
    logz = 0.
    rows = []
    for t, y in enumerate(ys):
        if t == 0:
            prediction = prior
        else:
            means = .8*x if linear else drift(name, x, t)
            sd = math.sqrt(.3) if linear else p["process_sd"]
            prediction = np.empty_like(x)
            # Bounded scratch allocation, no resampling or particle approximation.
            for lo in range(0, x.size, 128):
                kernel = normal(x[lo:lo+128, None], means[None, :], sd)
                prediction[lo:lo+128] = kernel @ mass
        prediction_mass = float(prediction @ quadrature)
        hx = x if linear else p["a"]*x*x
        sd_y = math.sqrt(.5) if linear else p["noise_sd"]
        weighted = prediction * normal(y, hx, sd_y) * quadrature
        z = float(weighted.sum())
        assert z > 0 and math.isfinite(z)
        mass = weighted/z
        logz += math.log(z)
        density = mass/quadrature
        middle = x.size//2
        pos = simpson_uniform(density[middle:], dx)
        absolute = simpson_uniform(-x[:middle+1]*density[:middle+1],dx)+simpson_uniform(x[middle:]*density[middle:],dx)
        rows.append(dict(t=t, logZ=logz, mean=float(mass@x), second=float(mass@(x*x)),
                         absolute=absolute, positive=pos, predictive_mass=prediction_mass))
    return rows


def self_test():
    points=np.linspace(0,2,101)
    assert abs(simpson_uniform(points**3,.02)-4) < 1e-12
    ys = [.2, -.3, 1.5, .8, -.2]
    rows = grid_filter("growth", ys, 20, .025, linear=True)
    m, v, logz = 0., 4., 0.
    for t, y in enumerate(ys):
        if t:
            m, v = .8*m, .64*v+.3
        logz += -.5*(math.log(2*math.pi*(v+.5))+(y-m)**2/(v+.5))
        k = v/(v+.5)
        m, v = m+k*(y-m), (1-k)*v
        assert abs(rows[t]["mean"]-m) < 1e-10
        assert abs(rows[t]["second"]-(v+m*m)) < 1e-10
        assert abs(rows[t]["logZ"]-logz) < 1e-10
    x = np.linspace(-15, 15, 2401)
    density = .5*normal(x,-4,.5)+.5*normal(x,4,.5)
    assert abs(np.trapezoid(density,x)-1) < 1e-12
    assert abs(np.trapezoid(density*x*x,x)-16.25) < 1e-12
    return dict(linear_gaussian_steps=len(ys), mixture_normalization_and_second_moment=True)


def prepare(out):
    started = time.time()
    controls = self_test()
    data = fixture()
    out.json("fixtures.json", data)
    lines = ["scenario\tt\tobservation\tstate"]
    for name, d in data.items():
        lines += [f'{name}\t{r["t"]}\t{r["observation"]:.17g}\t{r["state"]:.17g}' for r in d["rows"]]
    out.write("fixtures.tsv", "\n".join(lines)+"\n")
    refs, convergence = {}, []
    for name, d in data.items():
        specs = [(15,.05),(15,.025),(15,.0125),(20,.0125)] if name == "persistent-modes" else [(60,.1),(60,.05),(60,.025),(80,.025)]
        variants = []
        for limit, dx in specs:
            rows = grid_filter(name, [r["observation"] for r in d["rows"]], limit, dx)
            variants.append(rows)
            print(f"REFERENCE {name} limit={limit} dx={dx} logZ={rows[-1]['logZ']:.12g}", flush=True)
        array = lambda rows: np.array([[r[k] for k in ("logZ",)+METRICS] for r in rows])
        changes = [np.max(np.abs(array(variants[2])-array(variants[1])), axis=0),
                   np.max(np.abs(array(variants[3])-array(variants[2])), axis=0)]
        mass_error = max(abs(r["predictive_mass"]-1) for rows in variants for r in rows)
        symmetry = max(abs(r["positive"]-.5) for r in variants[-1]) if name == "persistent-modes" else 0.
        passed = all(np.all(change <= TOL) for change in changes) and mass_error <= 1e-8 and symmetry <= 1e-10
        convergence.append(dict(scenario=name, grids=specs, fine_medium=changes[0].tolist(),
                                expanded_fine=changes[1].tolist(), predictive_mass_error=mass_error,
                                symmetry_error=symmetry, passed=bool(passed), variants=variants))
        refs[name] = variants[-1]
    result = dict(passed=all(c["passed"] for c in convergence), references=refs, convergence=convergence,
                  self_tests=controls, python=sys.version, numpy=np.__version__, platform=platform.platform(),
                  elapsed_seconds=time.time()-started, generated_utc=time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()))
    out.json("reference.json", result)
    assert result["passed"], "Reference convergence failed; preserve output and investigate"


def analyze(out, reference_path, log_path):
    reference = json.loads(Path(reference_path).read_text())
    assert reference["passed"]
    raw = Path(log_path).read_bytes()
    records = [line.split("CONTINUOUS_ROW ",1)[1] for line in raw.decode("utf-8-sig").splitlines() if "CONTINUOUS_ROW " in line]
    names = "scenario budget mode seed t status logZ mean second absolute positive ess parents candidates pilots likelihoods targetDensities proposalDensities guides lookahead nanos error".split()
    rows = []
    for line in records:
        fields = line.split("\t")
        assert len(fields) == len(names), fields
        row = dict(zip(names,fields))
        for key in ("budget","seed","t","candidates","pilots","likelihoods","targetDensities","proposalDensities","guides","lookahead","nanos"):
            row[key] = int(row[key])
        for key in ("logZ",)+METRICS+("ess","parents"):
            row[key] = float(row[key]) if row[key] != "NA" else None
        rows.append(row)
    keys = [(r["scenario"],r["budget"],r["mode"],r["seed"],r["t"]) for r in rows]
    expected = {(s,n,m,k,t) for s in SCENARIOS for n in (256,1024) for m in MODES for k in range(1,257) for t in range(20)}
    assert len(keys)==len(set(keys)) and set(keys)==expected, "Missing, duplicate or unexpected production outcomes"
    # All outcomes retained as machine-readable TSV, including unavailable post-failure steps.
    out.write("outcomes.tsv", "\t".join(names)+"\n"+"\n".join(records)+"\n")
    summaries, gates = [], []
    for scenario in SCENARIOS:
        for budget in (256,1024):
            for mode in MODES:
                group = [r for r in rows if (r["scenario"],r["budget"],r["mode"])==(scenario,budget,mode)]
                failures = [r for r in group if r["status"] not in ("ok","unavailable")]
                by_step=[]
                for t in range(20):
                    step = [r for r in group if r["t"]==t]
                    valid = [r for r in step if r["status"]=="ok"]
                    truth = reference["references"][scenario][t]
                    errors = {key:math.sqrt(sum((r[key]-truth[key])**2 for r in valid)/len(valid)) if valid else None for key in METRICS}
                    losses = sum(r["positive"]<.01 or r["positive"]>.99 for r in valid) if .1<=truth["positive"]<=.9 else None
                    by_step.append(dict(t=t, available=len(valid), conditional_rmse=errors, mode_losses=losses,
                                        conditional_mean_ess=sum(r["ess"] for r in valid)/len(valid) if valid else None,
                                        conditional_mean_distinct_parents=sum(r["parents"] for r in valid)/len(valid) if valid else None))
                    if t in (0,9,19):
                        # Undefined refused outcomes cannot be converted into zero evidence or silently excluded.
                        defined = all(r["status"]=="ok" or r["status"]=="zero" or
                                      (r["status"]=="unavailable" and r["error"]=="zero") for r in step)
                        for metric in ("evidence",)+METRICS:
                            expected_value = 1. if metric=="evidence" else truth[metric]
                            if defined:
                                values = np.array([math.exp(r["logZ"]-truth["logZ"])*(1 if metric=="evidence" else r[metric]) if r["status"]=="ok" else 0. for r in step])
                                mean = float(values.mean()); se=float(values.std(ddof=1)/math.sqrt(256))
                                passed=abs(mean-expected_value)<=6*se+1e-4*max(1,abs(expected_value))
                            else:
                                mean=se=None; passed=None
                            gates.append(dict(scenario=scenario,budget=budget,mode=mode,t=t,metric=metric,
                                              expected=expected_value,mean=mean,se=se,passed=passed,
                                              status="checked" if defined else "blocked_by_refusal"))
                final = [r for r in group if r["t"]==19]
                costs={key:sum(r[key] for r in final) for key in ("candidates","pilots","likelihoods","targetDensities","proposalDensities","guides","lookahead","nanos")}
                causes={}
                for f in failures:
                    causes[f["error"]]=causes.get(f["error"],0)+1
                summaries.append(dict(scenario=scenario,budget=budget,mode=mode,runs=256,
                                      completed=sum(r["status"]=="ok" for r in final),failures=len(failures),
                                      failure_causes=causes,failure_steps=[f["t"] for f in failures],
                                      inclusive_costs=costs,by_step=by_step))
    result=dict(input_sha256=hashlib.sha256(raw).hexdigest(),reference_sha256=hashlib.sha256(Path(reference_path).read_bytes()).hexdigest(),
                rows=len(rows),runs=7680,summaries=summaries,gates=gates,
                checked=sum(g["status"]=="checked" for g in gates),blocked=sum(g["status"]!="checked" for g in gates),
                failed=sum(g["passed"] is False for g in gates),
                broader_readiness=not any(s["failures"] for s in summaries) and all(g["passed"] is True for g in gates))
    out.json("analysis.json",result)
    print(json.dumps({k:result[k] for k in ("runs","rows","checked","blocked","failed","broader_readiness")}),flush=True)
    # Successful analysis does NOT mean the benchmark or release gate passed.


def report(out, analysis_path):
    result=json.loads(Path(analysis_path).read_text())
    config=out.root/"matplotlib-config"
    config.mkdir(exist_ok=False)
    out.grant(config)
    os.environ["MPLCONFIGDIR"]=str(config)
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    labels=["Bootstrap","Guided","Auxiliary","Calibrated APF","Poor APF"]
    fig,axes=plt.subplots(1,3,figsize=(13,4.3),sharey=True)
    for ax,scenario in zip(axes,SCENARIOS):
        for i,budget in enumerate((256,1024)):
            data=[next(s for s in result["summaries"] if (s["scenario"],s["budget"],s["mode"])==(scenario,budget,m)) for m in MODES]
            values=[s["completed"] for s in data]
            bars=ax.bar(np.arange(5)+(i-.5)*.36,values,width=.36,label=f"Budget {budget}",color=("#287a9f","#dd883c")[i])
            ax.bar_label(bars,fontsize=8,padding=2)
        ax.set_title(scenario)
        ax.set_xticks(range(5),labels,rotation=35,ha="right")
        ax.set_ylim(0,285);ax.grid(axis="y",alpha=.2);ax.set_axisbelow(True)
    axes[0].set_ylabel("Completed 20-step runs / 256")
    handles,legend_labels=axes[0].get_legend_handles_labels()
    fig.legend(handles,legend_labels,loc="lower center",ncol=2,fontsize=9)
    fig.suptitle("Continuous filtering: all outcomes retained",fontsize=13)
    fig.tight_layout(rect=(0,.06,1,.95))
    target=out.write("completion.png","")
    fig.savefig(target,dpi=160);plt.close(fig)
    fig,axes=plt.subplots(1,2,figsize=(11,4.3),sharey=True)
    for ax,budget in zip(axes,(256,1024)):
        for mode,label in zip(MODES,labels):
            s=next(s for s in result["summaries"] if (s["scenario"],s["budget"],s["mode"])==("persistent-modes",budget,mode))
            ax.plot(range(20),[r["conditional_rmse"]["positive"] for r in s["by_step"]],label=f'{label} ({s["completed"]}/256 finish)')
        ax.set_title(f"Persistent modes / budget {budget}")
        ax.set_xlabel("Observation index");ax.grid(alpha=.2);ax.legend(fontsize=7)
    axes[0].set_ylabel("RMSE of positive-side mass (reference = 0.5)")
    fig.suptitle("All 256 seeds per configuration completed" if all(s["completed"]==256 for s in result["summaries"])
                 else "Conditional on available estimates: failures excluded from these curves",fontsize=11)
    fig.tight_layout()
    target=out.write("mode-error.png","")
    fig.savefig(target,dpi=160);plt.close(fig)
    lines=["# Measured continuous benchmark summary", "", "All rows: 256 seeds, 20 observations. No failed run was retried.", "",
           "| Scenario | Budget | Method | Completed | Refused | Final sign-mass RMSE (available only) |",
           "| --- | ---: | --- | ---: | ---: | ---: |"]
    for s in result["summaries"]:
        rmse=s["by_step"][-1]["conditional_rmse"]["positive"]
        value="unavailable" if rmse is None else f"{rmse:.5f}"
        lines.append(f'| {s["scenario"]} | {s["budget"]} | {s["mode"]} | {s["completed"]} | {s["failures"]} | {value} |')
    lines += ["", "Partial-run costs are not successful-inference efficiency. No APF speedup claim is justified.", "",
              "Fully completed persistent-mode baseline/guided comparison (all seeds):", "",
              "| Budget | Method | Mean candidates/run | Mean likelihood calls/run | Mean target + proposal density calls/run | Mean time/run (ms) | Final mean RMSE | Final sign-mass RMSE |",
              "| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: |"]
    for s in result["summaries"]:
        if s["scenario"]=="persistent-modes" and s["mode"] in ("bootstrap","guided"):
            c=s["inclusive_costs"];e=s["by_step"][-1]["conditional_rmse"]
            lines.append(f'| {s["budget"]} | {s["mode"]} | {c["candidates"]/256:.0f} | {c["likelihoods"]/256:.0f} | {(c["targetDensities"]+c["proposalDensities"])/256:.0f} | {c["nanos"]/256/1e6:.3f} | {e["mean"]:.5f} | {e["positive"]:.5f} |')
    out.write("summary.md","\n".join(lines)+"\n")


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("command",choices=("prepare","analyze","report"))
    parser.add_argument("--output",required=True)
    parser.add_argument("--acl-script")
    parser.add_argument("--reference")
    parser.add_argument("--log")
    parser.add_argument("--analysis")
    args=parser.parse_args()
    out=Output(args.output,args.acl_script)
    if args.command=="prepare": prepare(out)
    elif args.command=="analyze": analyze(out,args.reference,args.log)
    else: report(out,args.analysis)


if __name__=="__main__": main()
