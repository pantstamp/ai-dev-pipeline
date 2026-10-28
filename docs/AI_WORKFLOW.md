# Implementing a feature with the AI pipeline

This project ships six Claude Code skills that cover a feature or fix from raw idea
to post-merge retrospective, as the `ai-pipeline` plugin in
[`plugins/ai-pipeline/`](../plugins/ai-pipeline/). This is the guide to using them. Two of them
hand bounded work to an agent (`ai-pipeline:phase-implementer`, `ai-pipeline:pre-pr-gate`): the
developer skill implements each phase through one, and the pre-PR gate runs as one.

This repo installs the released plugin, pinned to a tag, as any project would (the commands are
in [`AI_PIPELINE_ROADMAP.md`](AI_PIPELINE_ROADMAP.md) §2). To work on the pipeline itself, start
Claude Code with `claude --plugin-dir ./plugins/ai-pipeline`, which loads the working copy over the
installed one.

The skills are not a replacement for judgment. Each stage ends at a point where
you decide whether to continue, and the guide calls out what to actually look at
before you do.

---

## The pipeline

| Stage | Skill | Produces | You decide |
|---|---|---|---|
| 1. Spec | `/ai-pipeline:product-owner` | the branch + `SPEC.md` | Are the acceptance criteria right? |
| 2. Plan | `/ai-pipeline:architect` | `PLAN.md` | The design — you propose it first |
| 3. Build | `/ai-pipeline:developer` | a commit per phase, then the PR | After **every phase** |
| 4. Gate | `/ai-pipeline:pre-pr-checklist` | GO / NO GO | Ship it or fix it |
| 5. Review | `@claude-review`, then `/ai-pipeline:review-triage` | PR comments → fixes | Which findings to act on |
| 6. Learn | `/ai-pipeline:retrospective` | `RETRO.md` + rule changes | Which lessons to keep |

The whole change happens on one branch — `feature/{name}` or `fix/{name}` — created in
stage 1. Each stage commits its output there, so nothing sits untracked on `master` between
stages. Everything for one change lives in `docs/features/{feature-name}/`:

```
docs/features/movie-search/
├── SPEC.md      ← stage 1
├── PLAN.md      ← stage 2
└── RETRO.md     ← stage 6
```

`docs/features/movie-search/` is a real worked example — read it before your
first run to see what each document looks like when finished.

---

## Before you start

- **JDK 21** to run Gradle.
- **`TMDB_API_KEY=<token>`** in `local.properties`. A missing key gives you an
  empty `BuildConfig` field rather than a build failure, so the app builds either
  way — only live API calls fail.
- **`gh` authenticated** (`gh auth status`). Stages 5 and 6 read the PR through it.
- A clean working tree. Stage 1 checks out an up-to-date `master` and branches from it.

---

## Stage 1 — Spec it

```
/ai-pipeline:product-owner

Feature: user profiles
Users should be able to create a profile with a display name,
an avatar, and a list of favourite genres.
```

Claude reads `CLAUDE.md` and any existing specs, then **interviews you one topic
at a time**. It is looking for the things raw requirements always leave out:
error paths, offline behaviour, lifecycle and permission concerns, empty states,
accessibility, what happens on the second launch.

Answer honestly, including "I don't know yet" — an open question recorded in the
spec is better than an assumption baked into the code. If a question reveals the
feature is bigger than you thought, this is the cheapest possible moment to cut
scope.

You can also start from a GitHub issue ("we want to work on issue 23"). Claude reads it,
then checks the causes the issue names against the code before interviewing you — issues
describe symptoms well and causes only partially.

Before the interview, Claude creates the branch from an up-to-date `master`. It will stop
and ask if your working tree is dirty.

**Output**: `docs/features/{feature-name}/SPEC.md`, committed on the branch once you approve it.

**Check before moving on**: the acceptance criteria. Stage 4 will test the
implementation against them one by one, so a vague criterion becomes an
unverifiable check later. "Profile loads quickly" is not a criterion; "profile
screen renders from cache without a network call" is.

---

## Stage 2 — Plan it

```
/ai-pipeline:architect

Create an implementation plan for the user-profiles feature.
```

Claude reads the spec, `CLAUDE.md`, the architecture docs and the parts of the
codebase the feature touches, and quietly writes down its own design. Then it stops —
because **you design first**. The aim is a plan that is yours: one you could have argued
for, and can explain to someone else, including why the alternatives lost.

For a small change Claude recommends a short version of what follows, and asks.

### It briefs you on what exists

Facts, no recommendations: a diagram of the modules, classes and data flow the feature
touches today; the closest existing equivalent, walked through; the rules that bind the
feature; known weaknesses it could copy; and the design questions the spec raises —
as open questions:

> - Where do ratings live — the existing movie repository or their own?
> - The spec doesn't say whether ratings sync or stay local.
> - A rating needs a schema migration. That's the one irreversible step.

Ask questions until you have the picture.

### You propose; it reviews

Sketch your design, in any form. Claude reviews it one decision at a time: a verdict
(sound, a trade-off, or a problem, and how serious), the evidence behind it, the
principle by name, and what best practice says versus what fits this codebase. Only then
does it show its own design and compare. Where yours is better, it says so and uses yours.

### It agrees the shape with you before writing the detail

You decide each fork. Then you get the shape to approve: a target-state diagram coloured
by phase, a state diagram per new ViewModel, two or three plain sentences per phase, and
a decision record with the alternatives you rejected.

**This is your decision point**, and it is deliberately before the detail exists: a plan
for a feature of ordinary size runs past a thousand lines — the watched-movies spec was
156 lines and produced a 1,313-line plan. Nobody meaningfully approves a thousand-line
document, and changing the phasing once it is written throws away everything downstream.

### Then the detailed plan

Phases are dependency-ordered — usually data, then domain, then presentation, using
only the layers the change touches. Each phase includes its own tests and is
independently buildable, because a defect found in the data layer is far cheaper than
the same defect found after the UI is on top of it. A phase that adds a screen also
lists a `@Preview` for each visual state.

Before showing you the detail, it checks the plan against a few design questions
the Konsist rules cannot: whether each use case does one thing, whether an
interface is growing past what its callers need, whether an abstraction is being
introduced for a single implementor that will never have another, and whether a
library-specific type is leaking into a module meant to outlive that library.

Most of Clean Architecture and dependency inversion here is already enforced by
the build, so the plan does not restate it in prose — the layer rules, ViewModels
taking use cases rather than repositories, mappers implementing an interface and
`@Dao` functions being suspend all fail CI if broken. The four questions above are
the ones that need a reader instead. **If a plan smells structurally wrong to you,
those are the questions to push on.**

> **Tip**: switch to Opus for this stage (`/model opus`). Planning is where
> deeper reasoning pays for itself; the plan's quality bounds everything after it.

Last, Claude asks you to explain the plan back in your own words, and points out what
differs from what is written. Skip it if you like; it is the cheapest check that the plan
is now yours.

**Output**: `docs/features/{feature-name}/PLAN.md`, committed on the branch once you approve it.

**Check before moving on**: read the whole plan. Specifically —

- **Files to create / modify.** Stage 4 checks the diff against these lists, so
  they need to be real paths.
- **Does it follow existing patterns?** The plan should reuse what the codebase
  already does. A new library or a new pattern appearing here is worth
  questioning — it is easier to argue about now than after it is written.
- **The `Observations on existing code` section**, if there is one. Two kinds of
  entry: *deviations*, where the plan deliberately does not follow an existing
  pattern and says why, and *notes*, where it spotted a problem nearby that this
  feature does not touch. Deviations need your agreement. Notes are yours to
  triage — they are not work for this feature, and the plan will not do them.

  This section exists because "follow the existing pattern" propagates existing
  defects. `WatchedMovieDao.getWatchedMovieEntity()` was written as a blocking
  query because `MovieDao.getMovieEntity()` already was one, and the same defect
  shipped twice.
- **Is each phase independently verifiable?** Each should end with a command that
  passes.

The plan must be self-contained. Stage 3 implements every phase in a fresh agent
that reads only the plan, so a gap in it shows up as soon as that phase starts.

---

## Stage 3 — Build it

```
/ai-pipeline:developer

Implement the user-profiles feature.
```

Claude checks out the branch from stage 1, then works **one phase at a time**:

1. Hands the phase to the **`phase-implementer` agent**, which implements exactly
   what the phase specifies (no extras, no "while I'm here") and runs its
   verification
2. Checks the agent's report against `git status` and re-runs the verification
3. Reports files changed, decisions made, and the verification result
4. **Stops and waits for you**
5. Commits the phase once you approve it

### Why each phase runs in an agent

Implementation is the bulky part of a feature: file reads, build output, fix loops,
over several phases. The agent does it in its own context, on Sonnet, and returns a
short report. Your conversation holds the plan, the reports and your decisions,
so it stays focused from the first phase to the PR, with no `/compact` or `/clear`
and no `/model` switch. Execution against a good plan does not need the heavier
model.

The agent works from PLAN.md alone. It never commits and never asks you anything.
When the plan looks wrong it does not improvise: it stops with **`BLOCKED`** and a
question, which Claude puts to you. Your answer, or your feedback on a finished
phase, goes back to the same agent, which continues where it left off. If either
changes what the phase specifies, Claude updates PLAN.md first, so the plan stays
the agreed record.

### The checkpoint is the point

Between phases, do the thing the AI cannot: open Android Studio, run the app,
look at the screen. A phase that compiles and passes its tests can still be
wrong in ways no test in the plan covers.

Say "continue" when you are satisfied. Say what is wrong when you are not.

**Tell Claude what you checked by hand** ("empty state looks right on the
emulator"). It records each check in the phase commit as a `Verified-manually:`
trailer. The stage 4 gate cannot see this conversation, and reads these trailers
to count an acceptance criterion as covered.

### Per-phase verification is deliberately narrow

Each phase runs only what the plan specifies — typically its module's tests and
`assembleDebug`. It does not run the full CI set: failures you are trained to ignore
are worse than no check at all.

The exception is `lint`, for any phase that touches a composable or `res/`. Lint is
the only check that sees Compose and resource misuse, so leaving it to the end moves
the failure a phase or more away from its cause. The plan makes this possible by
adding each resource in the phase that first uses it — otherwise a half-built feature
would fail lint on a resource that a later phase consumes.

### Resuming in a new session

Every approved phase is a commit, so the branch records the progress:

```
/ai-pipeline:developer

Implement the user-profiles feature.
```

Claude reads `git log` to see which phases are done and continues with the next.
Uncommitted changes in the working tree are a phase you never approved, and
Claude shows them to you before going on.

---

## Stage 4 — The quality gate

```
/ai-pipeline:pre-pr-checklist

Run pre-PR checks for the user-profiles feature.
```

**This is the stage people skip, and it is the one that earns its keep.**

### Why it exists when CI already runs

CI and this gate answer different questions, and the split is the whole point:

| CI (`.github/workflows/build.yml`) | The gate |
|---|---|
| Does it compile? | Did you build what the plan specified? |
| Do the tests pass? | Do the acceptance criteria hold? |
| Do the architecture rules hold? | Is there debug residue in the diff? |
| Is it formatted, is lint clean? | Is the branch worth someone's review time? |
| | Does the diff repeat a mistake CLAUDE.md already records? |

CI checks that the code is **correct**. The gate checks that it is **the code you
said you would write** — which needs the SPEC and PLAN as a statement of intent
and a judgment about whether the diff honours it. No CI runner has either.

So the gate leads with the checks CI cannot do — plan coverage, acceptance
criteria, a debug/TODO scan, git hygiene, and a rule review — and then runs one
pre-flight command mirroring CI so you do not push something CI will reject:

```bash
./gradlew spotlessCheck :test:konsist:test test assembleDebug lint
```

(`build.yml` is the source of truth for that list. The pipeline reads it from
[`.claude/pipeline.md`](../.claude/pipeline.md), the one copy to change when the
workflow does.)

### It runs as an independent agent

The checks run in the **`ai-pipeline:pre-pr-gate` agent**, not in your conversation. That
conversation just built the feature, and is the one most likely to read the code
generously. The agent is given the feature name and nothing else, judges the
branch only from what is on disk and in git, and is read-only: it reports
problems and cannot fix its way to GO. Fixes and re-runs happen back in your
conversation.

### The rule review

Every rule in CLAUDE.md's Flow, Compose and MVI sections records a mistake that
compiled, passed CI, and was caught only at code review. The rule review checks
the diff against those rules, so **a known mistake does not reach review twice**.
A finding does not need to fail today: matching the rule's pattern is enough,
because "safe today" is how each of those mistakes got through the first time.

It catches repeats, not new kinds of mistake. It can only enforce what is written
down; finding new problems is still the review's job (stage 5), and turning them
into rules is the retrospective's (stage 6). Run against movie-search before its
review fixes, it caught all four of that review's findings that had a rule by
then, and neither of the two that had none.

### Reading the result

- **GO** — proceed.
- **CONDITIONAL GO** — usually unverified acceptance criteria or rule review
  findings. These need a decision from you, not an automatic fix: Claude takes
  them one at a time. Check the criteria; fix or dismiss each finding.
- **NO GO** — the pre-flight failed, or the plan and the diff disagree.

Two failures want a specific response rather than the fastest route to green:

- **Konsist** — fix the code to match the convention. If a rule genuinely needs
  to change, that is a conversation, not a silent edit to the rule.
- **Android Lint** — `warningsAsErrors` is on, so one warning fails the build.
  Fix the cause. Do not add a baseline or a suppression to go green; that is a
  decision about the project's standards.

### One thing the gate does not cover

Nothing in CI compiles `src/androidTest`. If your feature touched a constructor
that instrumented tests use, check it yourself:

```bash
./gradlew :core:database:room:compileDebugAndroidTestKotlin
```

---

## Stage 5 — Review

On GO, the developer skill pushes and opens the PR against `master`.

The automated review is **opt-in and costs API credits**. It does not run on
push. To request one, comment on the PR:

```
@claude-review
```

It posts inline comments tagged by severity, plus a summary.

### Triage the findings

```
/ai-pipeline:review-triage

Go through the review comments on PR #XX.
```

Claude presents one finding at a time — the code, the claim, its own assessment and a
proposed change — and waits for your decision. Not every finding deserves a fix; some are
wrong. After the last one it verifies, commits, pushes, and (with your go-ahead) replies on
each thread.

### Before you merge, check for comments newer than your last commit

This is the one that bit PR #14: a second review wave arrived 25 minutes after
the fix commit and 5 minutes before the merge. Four findings — three of them
Major — went in unaddressed and survived in the codebase for five months.

```bash
gh api repos/{owner}/{repo}/pulls/{n}/comments \
  --jq '.[] | select(.created_at > "<your last commit time>") | {path, line, body}'
```

`/ai-pipeline:review-triage` runs this check for you before you merge. If it returns anything, it
is by definition unaddressed. A second review round
is exactly when attention is lowest — the work feels finished and the new
comments look like an echo of ones you already handled.

---

## Stage 6 — Retrospective

After merging:

```
/ai-pipeline:retrospective

Run a retrospective for the user-profiles feature, PR #XX.
```

Claude works on a new `docs/{feature-name}-retro` branch (the feature branch is merged).
It pulls the review comments, counts findings by severity, compares against earlier
retros, and writes `RETRO.md` with proposed changes, then opens a docs PR with the ones
you approve.

**This is the step that closes the loop.** A mistake Claude made once it will
make again, unless something changes. Two things can change:

- **A `CLAUDE.md` rule** — for judgment the toolchain cannot check. The Flow and
  coroutine rules in `CLAUDE.md` all came from findings that compiled, passed
  every CI check, and failed only under cancellation, a race, or repeated
  navigation.
- **A Konsist rule** — for anything mechanically checkable. Prose gets violated;
  a rule cannot be. `DaoKonsistTest` and `MapperKonsistTest` both exist because a
  documented convention was broken and nothing caught it.

Prefer the Konsist rule whenever the finding can be expressed as one. When you
add a rule, check it fails on the code that prompted it before you fix that code
— a rule that cannot go red is not enforcing anything.

A third destination exists for lessons about the **process**: the skill for that stage.
Keep skills free of history. A skill gets a general step ("add each resource in the
phase that first uses it"), never an incident ("in feature X, phase 2 missed…"). The
test for anything going into `CLAUDE.md` or a skill: would it still be true and useful if
that feature had never existed? If not, it belongs in `RETRO.md`.

`docs/features/watched-movies/RETRO.md` is a worked example, including the
baseline numbers a future retrospective is compared against.

---

## The short version

```
/ai-pipeline:product-owner    → SPEC.md      → read the acceptance criteria
/ai-pipeline:architect        → PLAN.md      → design it first, then read the plan and explain it back
/ai-pipeline:developer        → code         → review after every phase
/ai-pipeline:pre-pr-checklist → GO / NO GO   → act on CONDITIONAL GO
@claude-review                → PR comments
/ai-pipeline:review-triage    → fixes        → decide per finding, check for late ones
/ai-pipeline:retrospective    → RETRO.md     → turn lessons into rules
```

Skipping a stage is allowed and sometimes correct — a one-line fix does not need
a spec. Skipping the **checkpoints** inside a stage is where things go wrong.
