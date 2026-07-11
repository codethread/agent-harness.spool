
-----
# <a name="skein.api.peers.alpha">skein.api.peers.alpha</a>


Discover and call local sibling weavers from mill-published runtime metadata.




## <a name="skein.api.peers.alpha/call!">`call!`</a>
``` clojure
(call! peerish op)
(call! peerish op args)
```
Function.

Invoke a named op on a resolved peer over the `invoke` envelope, or `status`.

  `peerish` may be a row from `peer`/`peers`, a friendly name, or an existing
  workspace path resolvable by `peer`. `op` is an op name (string or unqualified
  symbol/keyword); pass `"status"` for the minimal lifecycle op. Optional `args`
  is a map with `:argv` (vector of strings) and `:payloads` (name→value map) for
  the invoke envelope. Domain error envelopes become `ExceptionInfo` with
  `:code :peer/domain-error`; a peer that answers with a stream header fails
  loudly with `:code :peer/stream-unsupported` (streams are out of scope for
  `call!`). Transport failures are loud and include peer identity. No retries or
  peer lifecycle management are attempted.
<p><sub><a href="https://github.com/codethread/skein/blob/main/src/skein/api/peers/alpha.clj#L272-L307">Source</a></sub></p>

## <a name="skein.api.peers.alpha/peer">`peer`</a>
``` clojure
(peer name-or-workspace)
```
Function.

Resolve exactly one running peer by friendly name or selected workspace path.

  Explicitly path-like input (contains `/`, or starts with `~`) matches the
  canonical selected workspace path; any bare token matches friendly names, so
  a local directory named like a peer never shadows the logical name. Stale,
  missing, and ambiguous matches fail loudly with domain-style `:code` data.
<p><sub><a href="https://github.com/codethread/skein/blob/main/src/skein/api/peers/alpha.clj#L93-L133">Source</a></sub></p>

## <a name="skein.api.peers.alpha/peers">`peers`</a>
``` clojure
(peers)
```
Function.

Return data-first rows for weaver metadata under the mill state root.

  Stale rows are included with `:running? false`. Present malformed metadata
  throws with `:code :peer/malformed-metadata` rather than being skipped.
<p><sub><a href="https://github.com/codethread/skein/blob/main/src/skein/api/peers/alpha.clj#L70-L79">Source</a></sub></p>
