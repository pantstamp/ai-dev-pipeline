# Golden scenarios

A suite that says whether a change to the pipeline (the plugin in `plugins/ai-pipeline/`,
`.claude/pipeline.md`, the pipeline sections of `CLAUDE.md`) made it worse. It is step 2 of
[`AI_PIPELINE_ROADMAP.md`](../AI_PIPELINE_ROADMAP.md); §4 there is the brief.

Each scenario replays a **historical snapshot whose outcome is already known**, with the
**current** pipeline files, and grades the result against what actually happened.

Run it before tagging a pipeline release (`pipeline-vX.Y.Z`), not on every change: a full run of
one scenario costs minutes and well over 100k tokens.

## Decisions

| Decision | Choice | Why |
|---|---|---|
| Tooling | `eval.py`, a script around `claude -p` | `claude plugin eval` runs each case in an empty workspace and cannot answer a skill's questions mid-run, and the scenarios need a snapshot worktree and a simulated user. Rechecked at step 5, with the plugin in place: still true. |
| Runs per scenario | 3 | Model output varies. 3 of 3 passes; 2 of 3 is `WARN`, a reason to look; less is `FAIL`. |
| Order | gate → architect → developer → product owner | Cheapest and best-known baseline first; the architect next, because PR #32's defects started in the plan. |
| Location | `scenarios/<name>/`, one folder each | Stays in this repo; the plugin ships without it. |

## A scenario folder

| File | Holds | Seen by the run? |
|---|---|---|
| `scenario.json` | snapshot and base commits, mode, agent, tools, runs, variants | — |
| `prompt.md` | the prompt; `{{WORKTREE}}`, `{{BASE}}`, `{{BRANCH}}` and `{{SCRATCH}}` are filled in | yes |
| `criteria.md` | pass criteria, one `- [ID] (required\|bonus[, variant]) …` line each | **no** |
| `answers.md` | scripted answers for a multi-turn scenario, read by the simulated user | **no** |
| `RESULTS.md` | recorded baselines, newest first | **no** |

**Mode** says where the run starts:
- `outside` — in this repo, pointed at the snapshot worktree by the prompt. Used by the gate,
  which only reads git and files, so the current `CLAUDE.md` and agent are the ones loaded.
- `inside` — in the worktree, with today's `.claude/pipeline.md` and `CLAUDE.md` committed onto
  the run's branch (see below). Used by the stages that write files.

Either way, the pipeline is loaded as a plugin: every run gets a copy of `plugins/ai-pipeline/`
taken when it starts, passed with `--plugin-dir`. Editing the working copy cannot change a run
already going, and a skill that reaches outside the plugin folder fails as it would installed.
A `prompt.md` invokes a skill as `/ai-pipeline:<skill>`, and `agent` names one as
`ai-pipeline:<agent>`.

Other `scenario.json` settings:
- **`seed_files`**: files copied from the scenario folder into the run's tree before it starts, such
  as a plan to execute. They are committed with the pipeline.
- **`hidden_paths`**: snapshot files that describe the scenario itself (this folder, the roadmap).
  They are deleted in that same commit. A seed or snapshot that tells the run what is being tested
  makes the result worthless. The first developer runs quoted the trap back from the plan's
  header.
- **`done_when_committed`** (a file) or **`done_when_subjects`** (a commit subject containing
  each of these strings, such as the phase titles): when a multi-turn
  run has finished.
- **`grade_file`** or **`grade_git`**: what the judge reads besides the dialogue. That is either
  one committed file, or the run's commits, diff and status.
- **`judge_references`**: files only the judge reads, as `{"commit", "path", "label"}` read from
  git. The product owner's coverage bonus compares against the real spec this way.
- **`stamped`**: the `STAMP` check. The committed `grade_file` must have
  `pipeline: vX.Y.Z` under its title, matching `plugins/ai-pipeline/skills/PIPELINE_VERSION`.
- **`read_only`** and **`nested_no_git`**: the `READONLY` and `NESTEDGIT` checks. `NESTEDGIT`
  fails a run in which an agent spawned by the session (a phase implementer) ran a git command
  that changes history or undoes edits.
- **`variants`**: named versions of the scenario. A value is extra prompt text, or
  `{"extra": text, "settings": {...}}` whose settings override the scenario's for that variant. A
  criterion tagged `(required, full/elsewhere)` is graded only in those variants.
- **`install: "elsewhere"`** (an `outside` setting): the run is in a project that installs the
  plugin, not in this repo. It starts in a scratch folder holding only the project's `CLAUDE.md`,
  `.claude/pipeline.md` and Gradle wrapper; the pipeline's files are only in the plugin.
- **`pipeline_values`** (with `install: "elsewhere"`): `{name: value}` replacing rows of that
  folder's `pipeline.md`, such as a tool set to `none`.
- **`stale_base`**: the branch is checked out in a separate clone, whose `origin` is a scratch
  bare repository holding only the base branch at `base`. The clone's own base branch is left at
  this older commit, and it has no `origin/<base>` until it fetches.
- **`prompt_file`**: a variant's own prompt, in place of `prompt.md`.

`forbidden_paths` are files that would reveal the outcome (a feature's `RETRO.md`), matched by
their full repo path; `forbidden_patterns` are other strings (a later commit, `master`). The
run is denied reading forbidden paths, and any reading tool call that names either fails the
run as `LEAK`.

## Running

`eval.py check` runs the static checks on the pipeline files: free, no model call. `eval.py run`
runs them first and stops if one fails.

```bash
python3 docs/ai-pipeline/eval.py list
python3 docs/ai-pipeline/eval.py run gate-movie-search                    # 3 full runs
python3 docs/ai-pipeline/eval.py run gate-movie-search --variant check5   # rule review only
```

Each run gets its own worktree, so runs never share Gradle state. Results go to
`$TMPDIR/ai-pipeline-evals/<scenario>/<timestamp>-<variant>/`: per run the prompt, the full
transcript, the output and `grade.json`, and a `summary.md` for the set. Worktrees are removed
afterwards unless `--keep` is passed.

A judge model grades the output against `criteria.md`; deterministic checks (`LEAK`) are added
by the runner. To grade an output produced some other way, for example by spawning the agent
from an interactive session:

```bash
python3 docs/ai-pipeline/eval.py setup gate-movie-search <dir>     # worktree + prompt, no model call
python3 docs/ai-pipeline/eval.py grade gate-movie-search <output.md>
```

`claude -p` must be logged in and recent enough for the pinned model. If `claude -p "hi"` answers
with a 401, run `claude auth login`. If a run fails with "does not support this model", run
`claude update`. The pinned `claude-opus-5-5` needs Claude Code 2.1.280 or newer. The runner
stops at the first of either error instead of failing every run.

After a pipeline change, record the new numbers at the top of the scenario's `RESULTS.md`, with
the commit they were taken at.

## Status

`pipeline-v3.0.1` (2026-10-28) only follows the repository's rename and needed no run under the
packaging-only rule below. `pipeline-v3.0.0` was released on 2026-10-23 on that rule; `pipeline-v2.1.0`,
on 2026-10-21, was the last release run on the full rule. Each `RESULTS.md` has the details,
newest first. Prefer the `eval.py` numbers over the in-session entries further down.

Models are pinned: `--model` and `--judge-model` default to `claude-opus-5-5`, and `--user-model`
to `claude-sonnet-5-5`. Every summary records the model that actually ran. Results taken with a
different model are not comparable.

| # | Scenario | Folder | 3.0.0 (plugin, 1 run) | 2.1.0 | 2.0.0, same day as 2.1.0 |
|---|---|---|---|---|---|
| 1 | Gate | [`gate-movie-search`](scenarios/gate-movie-search/) | PASS, `full` and `elsewhere` | PASS, 3 of 3 in all 6 variants | — |
| 2 | Architect | [`architect-movie-search`](scenarios/architect-movie-search/) | not run | 2 of 3 (E6) | 1 of 3 (E6 twice) |
| 3 | Developer | [`developer-dry-run`](scenarios/developer-dry-run/) | PASS | PASS, 3 of 3 | — |
| 4 | Product owner | [`product-owner-movie-search`](scenarios/product-owner-movie-search/) | PASS | 4 of 6 (P6) | 3 of 3 |

**A release passes when no scenario is worse than the previous release, run the same day.** 3 of 3
everywhere was the bar until 2026-10-20, when the released 2.0.0 scored 1 of 3 on the architect:
model behaviour drifts, so an old baseline proves nothing about today. A scenario at 3 of 3 needs
no comparison; one below it is run on the previous release the same day, and is released only if
that does no better, or the gap is recorded and accepted.

**That rule is for a release that changes what a skill or agent says or does.** A release that only
changes packaging or names, as 3.0.0 does, needs **one passing run of each scenario that could break**.
Those are the gate in its default and `elsewhere` variants, and the developer, for the agents it spawns.
The suite measures behaviour, which such a release does not touch, so three runs and a same-day
comparison would mostly measure the model's run-to-run variation. Decided on 2026-10-23; the
release's CHANGELOG entry says which rule it used.

**When a scenario drops below 3 of 3, compare with the previous release the same day** before
changing a skill. Model behaviour drifts, and one failing run in three is common for P6 and F5:

```bash
git worktree add --detach <scratch>/prev pipeline-vX.Y.Z
grep '^sdk.dir' local.properties > <scratch>/prev/local.properties   # Gradle needs the SDK path
python3 <scratch>/prev/docs/ai-pipeline/eval.py run <scenario>
```

The comparison then runs with that release's skills and harness. Remove the worktree afterwards.

A scenario that declares `answers` is multi-turn. `eval.py` starts a session, then loops: the
skill's last message and the dialogue so far go to a second model (`--user-model`, default
Sonnet) that replies from the scripted answers, and the reply is sent with `claude -p --resume`.
The loop ends when `done_when_committed` shows up in a commit, or after `max_turns`. The judge
then reads the dialogue and the committed `grade_file`.

A scenario with `branch` gets its own branch at the snapshot, named
`<branch>-eval-<run>`, because branch names are shared by every worktree of the repo. In
`inside` mode, today's pipeline is committed onto it as `eval: use the current pipeline`, so the
run starts from a clean tree. The branch is deleted with the worktree.
