# Pipeline CHANGELOG

One version for the whole pipeline: the `ai-pipeline` plugin's `skills/` and `agents/` (until
2.1.0, `.claude/skills/` and `.claude/agents/`), the shape of `.claude/pipeline.md` and the
pipeline sections of `CLAUDE.md`. Each release is tagged
`pipeline-vX.Y.Z` and passes the golden scenarios in `docs/ai-pipeline/` first. How to version and release: [`README.md`](README.md#versions).

Entries are grouped by stage and say what changed and why.

## Unreleased

## 3.0.1 — 2026-10-28

The repository is renamed from `android-template-project` to `ai-dev-pipeline`, so the plugin is
found by the pipeline, not by the app it was built on. **Released on the packaging-only rule** with
no scenario run: no skill or agent file changed, and no scenario reads the repository's URL, so
none could break. **Patch**, because nothing a skill says or does changed. **Upgrading a
project:** point the marketplace `url` at `https://github.com/pantstamp/ai-dev-pipeline.git` and
move `ref` to `pipeline-v3.0.1`, then run the two update commands (`docs/AI_PIPELINE_ROADMAP.md`
§2). GitHub redirects the old URL, so a project left on 3.0.0 keeps working, but it should not
depend on the redirect.

### All stages
- **`plugin.json`'s `repository` and the marketplace's description name the new repository.** The
  install commands in the roadmap and `PLUGIN_GUIDE.pdf` now use the new URL and this tag.

## 3.0.0 — 2026-10-23

Roadmap step 5: the pipeline is a plugin. **Released on the packaging-only rule** (suite README,
"Status"): no skill's process changed, so one run of each scenario the move could break, not
three runs and a same-day comparison. Gate (`full`, `elsewhere`), developer and product owner each
passed 1 of 1 on the plugin; the architect was not run. **Major**, because every name a user types or a skill
spawns changes. **Upgrading a project:** delete its copies of the skills and agents
(`.claude/skills/`, `.claude/agents/`, or the machine-wide `~/.claude/` ones), keep its
`.claude/pipeline.md`, checking it has every name in the new `pipeline.template.md`, and install
the plugin pinned to the tag
(`docs/AI_PIPELINE_ROADMAP.md` §2). No skill's process changed.

### All stages
- **Shipped as the `ai-pipeline` plugin** in `plugins/ai-pipeline/`, listed by the repo's
  `.claude-plugin/marketplace.json`. A project pins it to a `pipeline-vX.Y.Z` tag and moves the
  pin when it chooses. The skills already found their own files from their base directory
  (2.1.0), which a plugin skill is also told, so no path changed.
- **Skills are `/ai-pipeline:<skill>`, agents `ai-pipeline:<agent>`.** The developer spawns
  `ai-pipeline:phase-implementer`, the pre-PR checklist `ai-pipeline:pre-pr-gate`, and the hand-offs
  name the prefixed skills. A bare name would find nothing in a project that installs the plugin.
- **`pipeline.template.md` ships in the plugin**: every config name, whether it may be `none`, and
  what it means, with **Fill in:** values. A new project starts from it, and an upgrading one
  compares against it; before, the only copy was this repo's own `pipeline.md`, which is not
  installed. `eval.py check` now checks the skills' names against the template, and this repo's
  `pipeline.md` against the template.
- **The version is in `plugin.json` as well as `PIPELINE_VERSION`.** `eval.py check` fails when
  they differ, so a project's pin and the `pipeline:` stamp on its SPEC and PLAN agree.

## 2.1.0 — 2026-10-21

The fixes from the step-4 dry run on a second Android project (`docs/ai-pipeline/V2.1.0_FIXES.md`).
Released on a new rule: no scenario worse than 2.0.0 run the same day. Gate 3 of 3 in all six
variants, developer 3 of 3; architect 2 of 3 against 2.0.0's 1 of 3 that day; product owner 4 of 6
against 2.0.0's 3 of 3, recorded and accepted (see its `RESULTS.md`). **Upgrading a project:** add
the new names `{{rules}}`, `{{rules-home}}`, `{{lint}}` and `{{issues}}` to its `pipeline.md`, and the
"may be `none`" column; until then, a stage that needs one stops and says which. `PIPELINE_VERSION`
and this file are now in `skills/`.

### Architect
- **Resolves every placeholder in a config command before writing it into the plan**, from the
  build files or the task list, and asks when it cannot. A per-module test task differs with a
  module's product flavours, and a guessed name gives a phase whose command does not exist.
- **Adds `{{lint}}` to a UI phase only when the project has lint**, and splits resources across
  phases only when warnings fail it. Plans for a project without lint, or without
  `warningsAsErrors`, got a slow task and phase splits they did not need.

### Developer
- **Phase implementer: a verification task that does not exist is BLOCKED**, never swapped for
  another task. A wrong command in the plan is a planning error the user should see.
- Creates a missing branch from `origin/{{base}}` after fetching, and skips the format fix when
  `{{format-fix}}` is `none`.

### Pre-PR gate
- **Fetches the base and diffs against `origin/{{base}}`.** A stale local base put other merged
  work into the diff, so the rule review judged code that was not the branch's. If the fetch
  fails, the gate uses the local base and says so at the top. Check 4 reports how far the branch
  is behind, and a conflict with the base is NO GO.
- **A branch without SPEC.md or PLAN.md gets "not applicable" for Checks 1–2**, and the report's
  first line says the verdict covers code quality and CI only. In a dry run the gate used another
  document in the feature folder as a stand-in, which no contract covers and another run may not
  pick.
- **Skips a tool whose value is `none`**, and builds its pre-flight line from the steps that ran.

### Product owner
- **Reads an issue from `{{issues}}`**: GitHub issues, GitLab issues, or `none` (the user pastes
  it). A project's issues are not always on its code host.
- Creates the branch from `origin/{{base}}` after fetching, instead of pulling the local base.

### Developer, review triage, retrospective
- **GitLab support: written, not yet run.** Each has a GitLab step beside its GitHub one, chosen
  by `{{hosting}}`: creating the merge request, reading and replying to discussions, reading the
  diff. On a GitLab project these stages used to stop. The commands follow GitLab's CLI and API
  docs; the suite cannot run them (this repo is on GitHub), so their first real run is on a GitLab
  project.

### All stages
- **The project's coding rules are `{{rules}}`, and new ones go to `{{rules-home}}`.** Both are
  `CLAUDE.md` here. Some projects keep their rules in files `CLAUDE.md` only points to, which a
  stage reading `CLAUDE.md` never saw, and where the retrospective would have written new rules
  against the project's own instructions. The gate's rule review reads what `{{rule-review}}`
  names, sections or files.
- **The pipeline finds its own files wherever it is installed.** Skills and agents no longer open
  pipeline files by project-relative paths, which resolved only when the pipeline was copied into
  the project. The gate's report format is part of the `pre-pr-gate` agent;
  `PIPELINE_VERSION` and this CHANGELOG are in `skills/`, read from a skill's base directory; the
  architect opens `PLAN_TEMPLATE.md` from its own. The only project path left is
  `.claude/pipeline.md`. Installed machine-wide in a dry run, the gate fell back to a plain report
  layout because it could not find the format file.
- **The "Pipeline context" note reads correctly.** In 2.0.0 its example showed `{base}` with single
  braces, and the agents' copy said "a value a check uses uses". Every run read the table right
  anyway; the note now matches the names it describes.
- **`none` means the project has no such tool.** A step that uses it is skipped and the report
  says so; an empty or missing value still stops the stage. `pipeline.md`'s table marks which
  names may be `none`. A project without a formatter, an architecture-rule tool or an automated
  review had no defined way to say so.
- **`pipeline.md` names a path only if it exists in the project.** A project may check its
  AI-instruction files for paths that do not exist, and this file is one.

## 2.0.0 — 2026-10-15

All four golden scenarios pass 3 of 3 through `eval.py` on `claude-opus-5-5`, at `32f5662`. The
gate and product owner each needed a re-run after one failing run on a criterion with a history
of occasional misses (F5, P6); `pipeline-v1.1.0` was compared the same day.

### All stages
- **Project facts moved into `.claude/pipeline.md`.** The base branch and branch prefixes, the
  feature docs path, hosting, the pre-flight, format-fix and verification commands, the
  architecture-rule tool and where its rules live, the CLAUDE.md sections the rule review reads,
  and the review mechanism. Skills and agents name each one in double braces (`{{base}}`,
  `{{pre-flight}}`) and stop when a value is missing instead of guessing. Everything else keeps
  its 1.1.0 wording, the Android knowledge included. This is roadmap step 3: another Android
  project adopts the pipeline by writing its own `pipeline.md`, not by editing skills.
  Breaking: a project without `.claude/pipeline.md` cannot run any stage.
- **The pre-flight command is held once**, in the config. The gate agent and `AI_WORKFLOW.md`
  each had their own copy of `build.yml`'s task list.
- **Not done: a platform profile.** Moving the Android knowledge into one, or rewording it,
  regressed the product owner (6 of 9) and the architect (3 of 6) while 1.1.0, re-run the same
  day, passed 3 of 3. Deferred to the Flutter step; see the roadmap's §5.

## 1.1.0 — 2026-10-13

Product owner and architect pass 3 of 3 through `eval.py` on `claude-opus-5-5`; the gate and
developer did not change. Tagged on `07ceff3`.

### All stages
- **Artifacts record the pipeline version.** SPEC.md and PLAN.md headers carry
  `pipeline: vX.Y.Z`, read from `.claude/PIPELINE_VERSION`. Without it, a retro cannot tell
  whether a change in findings came with a pipeline release.

### Retrospective
- **RETRO.md records the version that built the feature**, taken from PLAN.md (`unstamped` for
  earlier plans). The comparison with earlier retros shows each feature's version, and whether
  a change in findings came with a release.

## 1.0.0 — 2026-10-13

The first release with a golden-scenario baseline: gate, architect, developer and product owner
each pass 3 of 3 through `eval.py` on `claude-opus-5-5`. Tagged on `34baf3f`.

### Product owner
- The interview covers repeated or equivalent input (the same value again, a change of only
  spaces or case) and late responses. It asks about every gap it notices, and records one as an
  open question only when the user doesn't know. Undecided points stay out of the acceptance
  criteria. The baseline's specs missed the unchanged-query case in 2 of 3 runs.

### Architect
- A design session the user leads: a briefing on the current state with a diagram, the user's
  own proposal first, a review of it one decision at a time, and only then the architect's
  design. The plan records target-state diagrams and a decision record with rejected
  alternatives.
- A ViewModel whose events start work gets an event × state table, with a test named in every
  cell where work is pending or running. "A test per state that matters" was read narrowly in
  every baseline run.

### Developer
- Each phase is implemented by a `phase-implementer` agent (Sonnet), which stops with BLOCKED
  rather than deviate from the plan. The skill re-runs the phase's verification itself, and
  sends feedback to the same agent after updating PLAN.md.

### Pre-PR gate
- Runs as an independent, read-only `pre-pr-gate` agent, with a review of the changed code
  against CLAUDE.md's judgement rules.
- Pipes the CI pre-flight through `tail` instead of redirecting it to a file.

### Earlier history
Changes before 1.0.0 are in `git log -- .claude`, and the agent-based pipeline in PR #35.
