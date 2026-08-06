(ns workflows.feature-iteration
  "The FEATURE ITERATION workflow (family \"feature-iteration\").

  Loaded by `.skein/config/workflows.clj`, which is also what registers the
  `feature-iteration` Var below under the name a worker starts it by; this file
  registers nothing itself. Drive a run with `strand workflow`."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [millstrand.api.format.alpha :as format-alpha]
            [millstrand.spools.workflow :as workflow]))

(s/def ::brief (s/and string? (complement str/blank?)))
(s/def ::cwd (s/and string? (complement str/blank?)))
(s/def ::diff-mode #{"branch" "patch"})
(s/def ::diff-base (s/and string? (complement str/blank?)))
(s/def ::revision boolean?)

(s/def ::params
  (s/keys :req-un [::brief ::cwd]
          :opt-un [::diff-mode ::diff-base ::revision]))

(s/def ::iterate-input (s/keys :req-un [::brief]))

(defn- prose
  "Render a `|`-margin block as its paragraphs, with `args` filled into its
  `%s` slots.

  `format-alpha/fill` soft-wraps each paragraph and keeps an indented one
  verbatim; joining them back with blank lines is what lets a whole multi-
  paragraph prompt — dynamic parts included — be authored as one block. Unlike
  the house `(reflow (format block id))` shape, the fill runs first: an arg here
  can be a user's brief, and free text run through the block parser loses
  everything before a `|` and has its line breaks folded."
  [block & args]
  (apply format (str/join "\n\n" (format-alpha/fill block)) args))

(defn- diff-command
  "Return the git command that shows the change a review round judges.

  `\"branch\"` reviews everything the branch adds over its base; `\"patch\"`
  reviews only what is uncommitted, which is the right scope while a round's
  changes are still in the working tree."
  [{:keys [diff-mode diff-base]}]
  (if (= "patch" diff-mode)
    "git diff HEAD"
    (str "git diff " diff-base "...HEAD")))

(defn- brief-instruction
  "Return the brief step's instruction for one iteration round.

  A revision round is briefed on the user's feedback rather than on the
  original ask, so the two readings need different instructions."
  [{:keys [revision]}]
  (let [[first-round revision-round]
        (format-alpha/fill
         "|Say the brief back in your own words, name the files and behaviour in
          |scope, and state the assumption you are making wherever the brief is
          |ambiguous. Stop and ask only where proceeding under any reading would
          |make the work useless if wrong; otherwise decide, and record the
          |decision here.
          |
          |Read the brief on this round's root: it is the user's feedback on the
          |previous round, not the original ask. Say back what changed about the
          |target, name the files still in scope, and drop anything the feedback
          |made moot. Earlier rounds stay in the graph — read them with `strand
          |workflow` rather than re-deriving what was already settled.")]
    (if revision revision-round first-round)))

(defn- review-prompt
  "Return the review seat's prompt for one iteration round."
  [{:keys [brief] :as params}]
  (prose
   "|Review one iteration of work as a cross-vendor second opinion. You are in a
    |read-only sandbox: read and analyse, write nothing.
    |
    |The change under review:
    |
    |    %s
    |
    |The brief it was made against:
    |
    |%s
    |
    |`make quality` has already passed on this change, so do not re-run the gate
    |or re-litigate formatting and lint. Judge the diff itself: does it do what
    |the brief asked, does it break a caller it did not look at, and does it
    |carry a failure mode the author would want to know about before showing
    |this to a human?
    |
    |Answer with a verdict line — `VERDICT: ship` or `VERDICT: fix` — then the
    |findings behind it, most severe first, each as `<file>:<line> — <finding>`.
    |Say plainly that there is nothing to fix when there is nothing to fix; an
    |invented finding costs more than a quiet review."
   (diff-command params)
   brief))

(def ^:private contract-doc
  "The contract `strand workflow show feature-iteration` prints."
  "Run one reviewed iteration of a feature brief, looping until the user accepts.

  A round is: take the brief, change the code, prove the change through `make
  quality`, get a cheap cross-vendor read on the diff, then hand it back to the
  user. The closing checkpoint is the loop — accepting ends the run, and
  iterating re-pours this definition with the user's feedback as the next
  round's brief, so a feature's whole history stays under one run id.

  Two of the five steps are machine gates rather than driver work. The quality
  gate is a `:shell` gate, so the run records the exit code and output of the
  gate that actually ran instead of an agent's account of it; the review gate is
  a `:subagent` gate on a read-only seat, so the reviewer cannot repair what it
  is reviewing. Both close themselves, and a red one stamps `gate/error` and
  stays put until a coordinator clears the key.

  Params: `brief` (what this round should achieve — on a later round, the user's
  feedback), `cwd` (the worktree the gates run in), and optionally `diff-mode`
  (`\"branch\"` or `\"patch\"`, default `\"branch\"`) and `diff-base` (default
  `\"main\"`). Start and drive it through `strand workflow`.")

(def feature-iteration
  "The FEATURE ITERATION static workflow definition; `contract-doc` is the
  contract it advertises.

  Building the value rather than using `defworkflow` keeps this namespace inert:
  it registers nothing on load, and `.skein/config/workflows.clj` is the one
  place that binds it to the name `:feature-iteration`."
  (workflow/static-definition
   contract-doc
   {:entrypoints #{:start}
    :param-spec ::params
    :defaults {:diff-mode "branch"
               :diff-base "main"
               :revision false}}
   (workflow/workflow
    (fn [{:keys [brief]}] (str "Feature iteration: " (first (str/split-lines brief))))
    {:attributes {"workflow/family" "feature-iteration"}}

    (workflow/step :receive-brief "Take the brief and scope the round" :self
                   :attributes
                   {"workflow/action-ref" "feature-iteration.brief.receive"
                    "workflow/instruction" brief-instruction})

    (workflow/step :implement "Make the code changes" :self
                   :depends-on [:receive-brief]
                   :attributes
                   {"workflow/action-ref" "feature-iteration.change.implement"
                    "workflow/instruction"
                    (format-alpha/reflow
                     "|Deliver the scope agreed at the brief step — the whole of it, not the
                      |easy part of it. Match the surrounding code's idiom, naming, and comment
                      |density. If part of the scope turns out to be blocked, finish everything
                      |else and record what you left out and why on this step's attributes, so
                      |the presentation step reports it rather than discovering it.")})

    (workflow/gate :quality "Run make quality" :shell
                   :depends-on [:implement]
                   :attributes
                   {"workflow/action-ref" "feature-iteration.quality.gate"
                    "shell/argv" ["make" "quality"]
                    "shell/cwd" (fn [{:keys [cwd]}] cwd)
                    "shell/timeout-secs" 1800
                    "workflow/instruction"
                    (format-alpha/reflow
                     "|Machine gate: the shell executor runs `make quality` in the run's cwd —
                      |format, clj-kondo, Splint, repository conventions, reflection warnings,
                      |and the cold test suite. A zero exit closes the gate and the run moves
                      |on. A non-zero exit or a timeout stamps `gate/error` with the exit code
                      |and output tail and leaves the gate ready, so a red gate blocks the
                      |review rather than shipping past it. Fix the code, then clear the key
                      |with `strand update <gate-id> --attributes '{\"gate/error\":null}'` to
                      |re-run. Do not run the gate by hand to satisfy it.")})

    (workflow/gate :review "Cross-vendor review of the diff" :subagent
                   :depends-on [:quality]
                   :attributes
                   {"workflow/action-ref" "feature-iteration.review.gate"
                    "agent-run/harness" "terra-low-ro"
                    "agent-run/cwd" (fn [{:keys [cwd]}] cwd)
                    "agent-run/prompt" review-prompt
                    "workflow/instruction"
                    (format-alpha/reflow
                     "|Machine gate: the subagent executor spawns a :terra-low-ro run against
                      |the diff this round's `diff-mode` selects, and closes the gate with the
                      |reviewer's verdict and findings on `agent-run/result`. The seat is a
                      |cheap cross-vendor read in a read-only sandbox — it can see the change
                      |and cannot repair it, which is the point. Read the result before the
                      |presentation step; a failed or exhausted run surfaces through `strand
                      |list --query stalled-subagent-gates` and is recovered with `agent retry
                      |<run-id>`, not by closing the gate yourself.")})

    (workflow/checkpoint :present "Present the round back to the user"
                         :depends-on [:review]
                         :kind :human
                         :attributes
                         {"workflow/action-ref" "feature-iteration.present"
                          "workflow/decision-point" "iteration-accepted"
                          "workflow/instruction"
                          (format-alpha/reflow
                           "|Show the user what changed, that the quality gate passed, and what
                            |the review found — including a clean review, and including any
                            |finding you disagree with and why. Name anything you left out of
                            |the agreed scope. Then hold here: accepting or iterating is the
                            |user's call, not yours.")}
                         :choices
                         [{:key :accepted
                           :label "Accept"
                           :description "The user accepts this round; the run is done."}
                          {:key :iterate
                           :label "Iterate"
                           :description
                           "The user wants another round; their feedback becomes its brief."
                           :input {:spec ::iterate-input
                                   :doc (str "Supply `brief`: the user's feedback, stated as"
                                             " what the next round should achieve.")}
                           :revise {:params {:revision true}}}]))))
