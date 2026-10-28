# AI pipeline roadmap — from this project to a reusable package

Status: steps 1–5 done. Step 3 is `pipeline-v2.0.0`, step 4's fixes are `pipeline-v2.1.0`, and
step 5, the plugin, is `pipeline-v3.0.0`. **Next: step 6**, the production project. The next session's brief is
[`docs/ai-pipeline/HANDOFF.md`](ai-pipeline/HANDOFF.md). Re-planned on 2026-10-15 after a first
attempt at a platform split regressed two stages (see §5).

**Starting a new session on this roadmap:** read this file, then `CLAUDE.md`,
`docs/AI_WORKFLOW.md`, `plugins/ai-pipeline/skills/README.md` and the HANDOFF.

The pipeline (`plugins/ai-pipeline/`, guide in [`AI_WORKFLOW.md`](AI_WORKFLOW.md)) was built for this
Android project. The goals, in order:

1. **Use it on a second, production Android project**, installed and pinned to a version, with
   updates taken when that project chooses.
2. **Use it on a Flutter project**, once one adopts it.
3. **Detect a change that makes it worse, and roll it back** (§3), at every step.

This repo stays where the pipeline is developed and tested; the production project consumes
released versions.

## Order

1. ✅ **Finish the agent improvements** in `IMPROVEMENTS.md`. Merged in PR #35.
2. ✅ **Golden scenarios** (§4, suite in [`docs/ai-pipeline/`](ai-pipeline/README.md)). The
   safety net for every step below. All four pass 3 of 3 on `pipeline-v1.1.0`.
3. ✅ **Project config** (§1): move the facts that differ between Android projects into
   `.claude/pipeline.md`. The Android knowledge stays in the skills, word for word. Release as
   `pipeline-v2.0.0`.
4. ✅ **Dry run on the production project** (§1): write its `pipeline.md`, run the gate on an existing
   branch there, and fix in this repo every place a skill still assumes this template. Done on
   2026-10-20; its eleven findings, ten fixed, are `pipeline-v2.1.0`
   ([`V2.1.0_FIXES.md`](ai-pipeline/V2.1.0_FIXES.md)).
5. ✅ **Package as a plugin** (§2), in this repo, and switch this repo and the suite to it.
   Released as `pipeline-v3.0.0` on 2026-10-23 (the command and agent names change).
6. **Install on the production project** (§2), pinned to the tag, and take one real feature
   through the whole pipeline, retro included.
7. **Flutter**, when a real Flutter project adopts it (§5). Not before: with one platform you
   cannot tell what is generic, and the first attempt to guess cost two regressions.

**A release passes when no scenario is worse than the previous release, run the same day.** Model
behaviour drifts, so an old baseline is not proof: on 2026-10-20 the released 2.0.0 scored 1 of 3
on the architect, where it had scored 3 of 3 five days before.

---

## 1. Project config (steps 3 and 4)

Another Android project shares all of this one's platform knowledge: lifecycle and process death,
previews, lint, Compose and Flow pitfalls. What differs are facts, and only those move to
`.claude/pipeline.md`:

| Fact | This repo |
|---|---|
| Base branch, branch prefixes | `master`; `feature/`, `fix/`, `docs/` |
| Feature docs path | `docs/features/{feature-name}/` |
| Hosting | GitHub, `gh` |
| Pre-flight (mirrors CI), format fix, phase verification | `./gradlew spotlessCheck :test:konsist:test test assembleDebug lint`, `spotlessApply`, `:{module}:testDebugUnitTest assembleDebug` |
| Architecture-rule tool and where new rules go | Konsist, `test/konsist` |
| CLAUDE.md sections the gate's rule review reads | Flow and coroutines, Compose, MVI pattern |
| Review mechanism | `@claude-review`, `.github/workflows/claude-review.yml` |

**Rules:**
1. **Facts move; knowledge and wording do not.** A sentence keeps its 1.1.0 wording unless it
   names one of the facts above, and then only the fact is replaced.
2. **A missing fact is an error, not a fallback.** A stage that needs one the config lacks stops
   and says which.
3. **The pre-flight is held once**, in the config, mirroring CI.

**Step 4 tests the table.** Every edit the production project still needs in a skill is a fact
the table missed, or an assumption about this template (Konsist, CLAUDE.md's section names).
Fix those here, as a patch release, before packaging.

## 2. Packaging (steps 5 and 6)

Written against Claude Code 2.1.296's plugin docs on 2026-10-15; the open questions were tested in step 5 (end of this section).

**Where it lives.** In this repo: the plugin in `plugins/ai-pipeline/` (skills, agents, templates,
CHANGELOG, `.claude-plugin/plugin.json` with the version), and `.claude-plugin/marketplace.json` at
the repo root listing it with source `./plugins/ai-pipeline`. The template app and the suite are
not shipped. A release is a `pipeline-vX.Y.Z` tag. The repo is public, so installing needs no
token; it can move to its own repo later by changing one line in each consumer.

**How a project installs it.** It writes `.claude/pipeline.md` from the plugin's
`pipeline.template.md` (every name, whether it may be `none`, and what it means) and commits it.
Each developer then runs, once, in the project:

```
claude plugin marketplace add "https://github.com/pantstamp/ai-dev-pipeline.git#pipeline-v3.0.1" --scope project --sparse .claude-plugin plugins
claude plugin install ai-pipeline@ai-pipeline --scope project
```

The first developer commits the `.claude/settings.json` these write. It records the marketplace,
pinned to the tag, and enables the plugin:

```json
{
  "extraKnownMarketplaces": {
    "ai-pipeline": {
      "source": {
        "source": "git",
        "url": "https://github.com/pantstamp/ai-dev-pipeline.git",
        "ref": "pipeline-v3.0.1",
        "sparsePaths": [".claude-plugin", "plugins"]
      }
    }
  },
  "enabledPlugins": { "ai-pipeline@ai-pipeline": true }
}
```

The committed file documents the pin, but it does not install anything. In a trusted folder,
Claude Code may offer to install from it. That path was not tested in step 5 (see below), so
the commands above are the supported way. `--sparse` keeps the template app out of the clone
(checked on the real 3.0.0 install: `.claude-plugin/`, `plugins/` and the repository's top-level
files, 488 KB; git's sparse "cone" mode always keeps top-level files).

**How it updates.** Only when the project chooses: a PR moves `ref` to the new tag, and each
developer then runs, with the project's tag:

```
claude plugin marketplace add "https://github.com/pantstamp/ai-dev-pipeline.git#pipeline-vX.Y.Z" --scope project --sparse .claude-plugin plugins
claude plugin update ai-pipeline@ai-pipeline --scope project
```

Rollback is the same two commands with the old tag. **Editing `ref` alone does nothing**, and a
bare `plugin update` follows whatever tag this machine last added (see "Verified in step 5").

**Deferred: a deterministic config check.** Today every skill and agent is told to stop and name a
missing `pipeline.md` value. That is an instruction, so it depends on the model, and it can fire
late, when the value is first used. A designed but unbuilt alternative:
- a script in the plugin that compares the project's `pipeline.md` with the template, and checks
  `gh` or `glab` and leftover copied skills
- a `SessionStart` hook that runs it
- the same check as each skill's first step
- an `/ai-pipeline:doctor` skill

It was deferred on 2026-10-23: with one consumer project, installed by the person who wrote its
config, it guards against little and adds a hook to every session. **Build it when** a second
project or another developer installs the plugin, or when step 6 hits a missing value the skills did
not stop on.

What a plugin hook can do, tested on 2026-10-23 with a throwaway plugin (Claude Code 2.1.296–2.1.306):
- A `SessionStart` hook in the plugin's `hooks/hooks.json` runs with `${CLAUDE_PLUGIN_ROOT}` and
  `CLAUDE_PROJECT_DIR` set.
- Its JSON `systemMessage` is shown to the person **in the CLI only**, as "SessionStart:startup says:
  …" among the startup notices, easy to miss. **The desktop app does not show it.**
- Its `hookSpecificOutput.additionalContext` reaches the model in both. Unprompted, the desktop
  session added the reported problem to its first reply. So a warning meant for the person is
  sent through the model, worded as an instruction to tell them.
- stderr reaches neither.

**Developing it here.** `claude --plugin-dir ./plugins/ai-pipeline` loads the working copy over an
installed one; `/reload-plugins` picks up edits. `eval.py` loads it the same way.

**What changes with packaging:**
- Skills become `/ai-pipeline:architect` etc., agents `ai-pipeline:pre-pr-gate`; every reference
  by bare name is updated.
- Skills find their own files through `${CLAUDE_SKILL_DIR}` or `${CLAUDE_PLUGIN_ROOT}`, and the
  project's config through `${CLAUDE_PROJECT_DIR}/.claude/pipeline.md`. Files outside the plugin
  folder are not installed.
- Plugin agents ignore `permissionMode`, `hooks` and `mcpServers` in their frontmatter.
- The `@claude-review` workflow is a CI file and cannot ship in a plugin; the project copies it.
- Committing `.claude/pipeline.md` (step 6) puts it under the project's own CI checks. A project
  that checks its AI-instruction files, for example for paths that do not exist, checks this one
  too; it must pass them.

**Verified in step 5** (2026-10-21, Claude Code 2.1.296, throwaway plugins under `claude -p`):
- **A plugin skill is told its base directory.** The skill receives `Base directory for this
  skill: <plugin>/skills/<skill>`, so `<base directory>/../PIPELINE_VERSION` resolves.
  `${CLAUDE_SKILL_DIR}` and `${CLAUDE_PLUGIN_ROOT}` are substituted in `SKILL.md` too.
- **A plugin agent's `subagent_type` is `<plugin>:<agent>`**: `ai-pipeline:pre-pr-gate`.
- **A pinned tag is not refetched.** With the marketplace pinned to `v1`, `autoUpdate: true` and
  `v2` pushed to `main`, the install stayed on v1 through `marketplace update`, `plugin update`
  and two session starts.
- **The pin lives in a per-user registry** (`~/.claude/plugins/known_marketplaces.json`), not
  in settings. That has three consequences:
  - Settings are copied into the registry only when the marketplace is first added. A changed
    `ref` in `settings.json` is ignored by `marketplace update`, `plugin update` and session
    start.
  - `marketplace add "<url>#<tag>"` re-pins the marketplace. A following `plugin update` then
    moves the install, forward or back. Both directions were tested.
  - **One machine has one pin per marketplace name.** If project B adds `#v2`, a later `plugin
    update` in project A moves A to v2 as well. Installed copies do not move by themselves.
- **Settings-declared marketplaces need a trusted folder.** In a folder that has never passed
  the trust dialog, `extraKnownMarketplaces` is inert, and `-p` never shows that dialog.
- **Sources:** `https://…git#<ref>` is accepted. `file://` is not, and a bare path cannot pin a
  ref. A git source is cloned shallow. `marketplace add --sparse .claude-plugin plugins` checks
  out the plugin and the top-level files, not the template app's modules.

## 3. Versioning and rollback

Rolling back is easy; **noticing that a change made things worse** is the hard part.

### Mechanics

- **One version for the whole pipeline**, not per skill. The stages share contracts: the gate
  reads the PLAN format, and the retro reads the gate report.
- **Semver in `plugin.json`, a git tag per release, and a CHANGELOG** saying per stage what
  changed and why.
- **Each project pins a version** through its marketplace entry's git ref. Rollback is moving the
  pin back, with the commands in §2: editing the ref alone does nothing.
- **While the plugin lives in this repo:** commits that touch the pipeline use the
  `chore(pipeline): …` prefix, and releases are tagged `pipeline-vX.Y.Z`. The version is in
  `plugins/ai-pipeline/skills/PIPELINE_VERSION` and the plugin's `plugin.json` (`eval.py check`
  fails when they differ), and the CHANGELOG is `plugins/ai-pipeline/skills/CHANGELOG.md`. The tag
  scheme is kept rather than `claude plugin tag`'s `{name}--v{version}`: the pins and the history
  already use it. Roll the working copy back with `git checkout pipeline-vX.Y.Z -- plugins/ai-pipeline/`
  (to 2.1.0 or earlier, `-- .claude/`).

### Detecting regressions

1. **Stamp the version on every artifact.** SPEC, PLAN and RETRO headers record
   `pipeline: vX.Y.Z`. Retros already count review findings by severity per feature; with the
   version, they show trends across releases. This is lagging and noisy, but it is real-world data.
2. **Golden-scenario evals**, the leading indicator, run before tagging a release. One
   scenario per stage that matters most; the full brief is in §4. Model output varies between
   runs, so each scenario passes on a rate ("3 of 3 runs"), not a single result.

---

## 4. Step 2 brief — golden scenarios

**Goal**: a repeatable suite that says whether a change to the pipeline made it worse. Step 3
moves every Android-specific line out of the skills, and it is safe only if this suite passes as
well afterwards as before.

**Principle**: each scenario replays a **historical snapshot whose outcome is already known**
(from a PR review or a retro), with the **current** pipeline files and the **current**
`CLAUDE.md`. Then the result can be graded against what actually happened, not against opinion.

### How to replay a snapshot

1. Create a detached worktree at the snapshot commit, in a scratch directory:
   `git worktree add --detach <scratch>/<name> <commit>`.
2. Give the run the **current** pipeline:
   - **Gate scenario:** run the agent from the main repo and point it at the worktree; it reads
     only git and files there (see the prompt in scenario 1).
   - **Architect, product-owner and developer scenarios:** these run inside the worktree. Copy
     the current `.claude/` and `CLAUDE.md` into it, uncommitted, so the skills under test are
     today's.
3. Keep the outcome hidden. The run must not read that feature's `RETRO.md` (most snapshots
   predate it anyway) or anything after the snapshot.
4. Remove the worktree afterwards (`git worktree remove --force`).

A session only picks up agent files when it starts: restart it after changing one.

### Scenarios

| # | Stage | Snapshot | Runs without a person? |
|---|---|---|---|
| 1 | Gate | `dd348bf` (movie-search before the PR #32 fixes), base `3e1577d` | Yes |
| 2 | Architect | `bdfacd9` (movie-search SPEC committed, no code yet) | With scripted answers |
| 3 | Developer | any current `master`, plus [`scenarios/developer-dry-run/PLAN.md`](ai-pipeline/scenarios/developer-dry-run/PLAN.md) | With scripted answers |
| 4 | Product owner | `c3a9626` (parent of `bdfacd9`, before the movie-search spec) | With scripted answers |

#### 1. Gate: does the rule review catch known findings?

Spawn `pre-pr-gate` from the main repo, with `Feature name: movie-search` plus these adjustments:
- Run every git command in the worktree (`git -C <worktree> …`).
- Wherever your instructions say `master`, use `3e1577d`.
- Use the current `CLAUDE.md` from the main repo, not the worktree's.
- Do not read `docs/features/movie-search/RETRO.md`.
- For a cheaper run: `Checks to run: Check 5 only`, with no gradle.

**Pass criteria** (PR #32's findings are listed in `docs/features/movie-search/RETRO.md`):
- **Rule review catches 4 of 4 rule-backed findings:**
  - #1: Refresh while a new query's debounce is pending, in `MovieSearchViewModel`.
  - #2: `reruns` `MutableSharedFlow` + `tryEmit` without `onBufferOverflow`.
  - #5: the `LaunchedEffect` in `DiscoverScreen` calls `onSearchEvent` without
    `rememberUpdatedState`.
  - #6: `MovieSearchResults` imports `MovieRow` from the `movielist` screen package.
- **No false positive on the pager comment** (#3). `MovieCatalogTabbedScreen` sets
  `beyondViewportPageCount = 1` on a line outside the diff.
- **Full run:** result CONDITIONAL GO, pre-flight passes, and every planned file is accounted for.
- **Bonus, not required:** AC8 flagged as NEEDS VERIFICATION or a likely defect. That is the
  scroll-to-top case review missed.
- Findings #4 and #7 have no rule and are not expected.

**Baseline (PR #35):** 4 of 4 in a single run. Check 5 alone took about 2.5 min and 75–125k
tokens; the full gate about 4 min and 145k tokens.

#### 2. Architect: does the plan still design in PR #32's defects?

The movie-search retro found that 5 of the 6 real review findings, and the most serious defect
found during implementation, **originated in the plan**. Since then each one has become a
CLAUDE.md rule or an architect self-check. Run `/architect` for `movie-search` in the worktree
and check that the plan now avoids them.

**Scripted answers:**
- At the session-size question: the full version.
- At the briefing: no questions.
- At the design step: "Show me yours." This tests the plan, not the review of a user design.
- For each open question: take the option the original `PLAN.md` (`2ca290e`) chose. Write these
  down once and reuse them, so every run gets the same answers.
- At the shape gate: approve. At the explain-back: skip.

**Pass criteria** (the plan, not code):
- Refresh, Retry and query-change events each define what happens when a search is pending or
  running, and plan a test for each state. This was finding #1.
- Every `MutableSharedFlow` used with `tryEmit` has an explicit `onBufferOverflow`. This was #2.
- Effect code that calls a callback reads it through `rememberUpdatedState`. This was #5.
- `MovieRow`, and anything else two screens use, is planned into `presentation/uicomponent`.
  This was #6.
- One shared `LoadError` → message mapping, not one per screen. This was #4.
- The query pipeline does not rely on a `StateFlow` to deliver every change: it uses a
  generation counter or a buffered `SharedFlow`. This was defect A in the retro.
- General shape:
  - each phase has a verification command
  - UI phases include `lint`
  - each new screen state has a `@Preview`
  - each resource is added in the phase that first uses it

**Baseline:** not run yet. The original plan (`2ca290e`) is the "before": according to the retro,
all six defects above came from it.

#### 3. Developer: does the phase agent stop instead of improvising?

On a scratch branch from `master`, copy the dry-run plan to
`docs/features/pipeline-dry-run/PLAN.md`, commit it, and run `/developer` for
`pipeline-dry-run`.

**Scripted answers:**
- Phase 1: approve.
- Phase 2: reject with "make the part before the @ lowercase only".
- Phase 3: answer `BLOCKED` with option A, updating the test.

**Pass criteria:**
- Phase 1 report matches `git status`, and the skill re-runs the verification itself.
- Phase 2 feedback goes to the **same** agent via SendMessage, and PLAN.md is updated first.
- Phase 3 returns `BLOCKED`. The trap: the plan widens `randomInt`'s range but forbids touching
  the test that asserts the old range.
- The answer goes to the same agent, which then finishes the phase.
- The agent makes no commits and uses no git command to undo edits.

**Baseline (PR #35):** all passed, except that the agent once used `git checkout --` to undo its
own edit; that rule was added afterwards. Each agent run took 10–22 s and about 65k tokens.

Delete the scratch branch afterwards.

#### 4. Product owner: does the spec cover what raw requirements leave out?

Run `/product-owner` in the worktree at `c3a9626` with a one-line raw requirement: *"Users should
be able to search movies by title from the Discover tab."* Script answers from the real
`SPEC.md` (`bdfacd9`): when the interview asks something the spec answers, answer as the spec
does; otherwise say "I don't know yet".

**Pass criteria:**
- The spec has acceptance criteria for:
  - empty results
  - offline and other errors, including retry
  - rotation and process death
  - clearing the query
  - repeated or identical queries
- Open questions are recorded rather than assumed.
- Out-of-scope items are listed.
- Every acceptance criterion is testable as written.

**Baseline:** not run yet. Compare coverage with the real `SPEC.md`.

### Open decisions — settle these first

1. **Tooling.** *Recommendation:* try `claude plugin eval` first. Fall back to a small script
   (`claude -p` per scenario, plus a grading step) if it can't drive project skills before they
   are a plugin.
   - `claude plugin eval` runs scripted cases with graders (regex, file exists, tool used, model
     judge) and can compare with-plugin against without-plugin runs.
   - Its suites carry over to step 5 unchanged.
   - **Check first** whether it works on project skills that are not yet a plugin.
2. **Runs per scenario.** *Recommendation:* 3. A scenario passes at 3 of 3; 2 of 3 is a warning
   to investigate. Weigh this against the cost above: the suite is run before a pipeline release,
   not on every change.
3. **Order.** *Recommendation:* scenario 1 first (no person needed, baseline already known), then
   2 (highest value: the plan is where PR #32's defects started), then 3, then 4.
4. **Where scenarios live.** *Recommendation:* `docs/ai-pipeline/scenarios/<name>/`, one folder per
   scenario with its inputs, scripted answers and pass criteria. They move into the plugin repo
   at step 5.


---

## 5. Platform split — deferred to the Flutter step

The first plan for step 3 split the skills into three layers: generic skills, an Android profile
(interview concerns, plan checklist, residue patterns) and a project config. It was built on
`chore/pipeline-split` (`1d62a57`…`84b5cab`) and measured on 2026-10-14:

| Stage | Split | `pipeline-v1.1.0`, same day |
|---|---|---|
| Gate | 3 of 3 | — |
| Developer | 3 of 3 (after a harness fix) | — |
| Product owner | 6 of 9 → 3 of 3 once the profile held the skill's lists verbatim | 3 of 3 |
| Architect | 3 of 6 → 1 of 3 with its generic wording restored | 3 of 3 |

**What it showed:**
- **Moving knowledge out of a skill, or rewording it on the way, made stages worse.** A profile
  that turned each concern into its own interview topic lengthened interviews by a third. An
  architect told to "meet the profile's plan checklist" instead of the checklist itself failed the
  `StateFlow` check (E6) in 4 of 9 runs.
- **Moving facts did not.** The product owner read its branch, prefixes and paths from the config
  and passed 3 of 3.
- **Concrete text beats lookups.** "Run `./gradlew … lint`" is followed more reliably than "run the
  config's UI check when the profile's condition holds".

**When Flutter comes**, keep one shared process and give each platform its own concrete text: either
skills generated from templates and a values file per platform, or two maintained editions. Choose
once a real Flutter project shows how much of the Android knowledge actually changes.
