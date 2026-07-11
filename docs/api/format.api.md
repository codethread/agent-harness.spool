
-----
# <a name="skein.api.format.alpha">skein.api.format.alpha</a>


Blessed `|`-margin doc-block helpers for any tier that publishes prose as data.

  Long strings in source hurt readability and IDE viewports; author them as
  `|`-margin blocks instead and reflow with these helpers. Spool authors may
  equally use the `skein.spools.format` names — both surfaces share one
  implementation and one contract.




## <a name="skein.api.format.alpha/fill">`fill`</a>
``` clojure
(fill block)
```
Function.

Reflow a `|`-margin doc block into a vector of item strings.

  The bar marks column 0, a bare `|` line separates items, flush-left prose
  soft-wraps into one line per item, and any indentation past the bar keeps the
  whole item verbatim for command samples and other intentional layout. Throws
  when no line carries a bar — a bar-less block is an authoring error.
<p><sub><a href="https://github.com/codethread/skein/blob/main/src/skein/api/format/alpha.clj#L10-L18">Source</a></sub></p>

## <a name="skein.api.format.alpha/reflow">`reflow`</a>
``` clojure
(reflow block)
```
Function.

Soft-wrap a single-paragraph `|`-margin block into one string.

  The single-item companion to `fill` for a lone prose value; item and verbatim
  semantics do not apply — every barred line is trimmed and space-joined.
  Throws when no line carries a bar, like `fill`.
<p><sub><a href="https://github.com/codethread/skein/blob/main/src/skein/api/format/alpha.clj#L20-L27">Source</a></sub></p>
