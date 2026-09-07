# Figaro LaTeX documentation

This directory preserves the original tutorial sources and adds a concise modernization
bridge. The old modeling explanations are valuable; their installation instructions and
Scala syntax are not the current contract. Start with the [repository README](../README.md)
and [documentation migration map](../docs/DOCUMENTATION_MIGRATION.md).

## Contents

- [Modernization.tex](Modernization.tex): shared preface for both active documents;
  toolchain, current learning path, optional performance APIs, limitations and credits.
- [FigaroGuide/FigaroGuide.tex](FigaroGuide/FigaroGuide.tex): short modern guide that
  points to maintained documentation rather than repeating the original book.
- [Tutorial/FigaroTutorial.tex](Tutorial/FigaroTutorial.tex): original tutorial chapters,
  preceded by the shared modernization preface. Chapter numbering stays unchanged.
- [archive/FigaroGuide-3.0.tex](archive/FigaroGuide-3.0.tex): original guide source,
  preserved unchanged for historical reference, not an active installation guide.
- [PDF archive](../doc/archive/README.md): original guides and both distinct tutorial
  editions, preserved byte-for-byte. They do **not** contain the modernization preface.

## Optional typesetting

The maintained Markdown is usable without LaTeX. With a TeX distribution installed,
run these commands from the indicated directory:

```sh
# From FigaroLaTeX/FigaroGuide (requires article and hyperref):
pdflatex -interaction=nonstopmode -halt-on-error FigaroGuide.tex
pdflatex -interaction=nonstopmode -halt-on-error FigaroGuide.tex

# From FigaroLaTeX/Tutorial (requires packages in tutorial-config.tex):
pdflatex -interaction=nonstopmode -halt-on-error FigaroTutorial.tex
pdflatex -interaction=nonstopmode -halt-on-error FigaroTutorial.tex
```

The historical tutorial uses classicthesis and additional packages/fonts. Its original
template license and Figaro notices remain intact. Review warnings and render every
page before distributing a newly compiled PDF. This cleanup has static source and
preservation checks, **not a verified LaTeX build**; no newly generated PDF is checked in.
Generated PDF/build intermediates must not replace the archival PDFs.

## Maintenance

Keep setup and API instructions in the maintained Markdown guides. Update the shared
preface when those entry points or compatibility boundaries change. Add small, tested
Scala 3 examples when a legacy listing needs a runnable replacement; avoid transcribing
the entire historical tutorial. Run `python -B tools/docs/check_links.py` and the
[documentation tests](../tools/docs/README.md) from the repository root.
