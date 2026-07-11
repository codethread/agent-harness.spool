
-----
# <a name="skein.api.relations.alpha">skein.api.relations.alpha</a>


Advisory relation catalog for common Skein relation vocabulary.

  The catalog is source-visible data for agents, config, and REPL workflows. It
  is not a storage allowlist or runtime relation-semantics registry; valid
  relation names outside this catalog remain valid userland annotations.




## <a name="skein.api.relations.alpha/annotation-relations">`annotation-relations`</a>
``` clojure
(annotation-relations)
```
Function.

Return catalog entries for behavior-free annotation relation conventions.
<p><sub><a href="https://github.com/codethread/skein/blob/main/src/skein/api/relations/alpha.clj#L77-L80">Source</a></sub></p>

## <a name="skein.api.relations.alpha/catalog">`catalog`</a>




Shipped operational relation batteries and behavior-free annotation
  conventions.
<p><sub><a href="https://github.com/codethread/skein/blob/main/src/skein/api/relations/alpha.clj#L8-L70">Source</a></sub></p>

## <a name="skein.api.relations.alpha/operational-relations">`operational-relations`</a>
``` clojure
(operational-relations)
```
Function.

Return catalog entries for shipped operational relation batteries.
<p><sub><a href="https://github.com/codethread/skein/blob/main/src/skein/api/relations/alpha.clj#L82-L85">Source</a></sub></p>

## <a name="skein.api.relations.alpha/relation">`relation`</a>
``` clojure
(relation relation-name)
```
Function.

Return the advisory catalog entry for relation-name, or nil when uncataloged.
<p><sub><a href="https://github.com/codethread/skein/blob/main/src/skein/api/relations/alpha.clj#L72-L75">Source</a></sub></p>
