# Step 4 guide: try the pipeline on another project

Your checklist for a dry run of the pipeline on another project, roadmap step 4. Claude's own
brief is [`HANDOFF.md`](HANDOFF.md); this file is what **you** do, and what to check at each point.
It was first used on a production Android project with `pipeline-v2.0.0`, and updated from what
that run found. Use it again for the next project.

**What this phase is for:** find every place the pipeline still assumes this template project,
before a project relies on it. It ends with a small release here.

**Which version:** the newest `pipeline-vX.Y.Z` tag of this repo. The commands below say
`pipeline-v2.1.0`; use the newest one.

**The pipeline stays local to that laptop.** Nothing from it is committed or pushed in the
project's repository. It is installed outside the project, or copied in uncommitted and hidden
from git by a local-only exclude list (4.2), and removed at the end (part 6). Only the fixes are
pushed, to this repo.

**Time:** one session for parts 2–4, part of another for part 5. **Cost:** a few dollars on the
project's side; $5–50 per golden-scenario re-run for the release.

---

## Part 1 — Before you leave this laptop

- [ ] This repo's `master` has the release you will use, and its tag is pushed.

## Part 2 — Prepare the other laptop

- [ ] **Allowed?** Confirm that running Claude Code on the project's code is allowed (company
      policy, client contract). If not, stop here.
- [ ] **Claude Code** is installed and logged in. Update it, then check the version (2.1.280 or newer)
      and that the pinned model answers:

      ```bash
      claude update
      ```

      ```bash
      claude --version
      ```

      ```bash
      claude -p "reply with ok" --model claude-opus-5-5
      ```
- [ ] **Toolchain:** the project builds from the command line, with the JDK its CI uses and the
      Android SDK. Try its CI's main task once by hand, e.g. `./gradlew assembleDebug`.
- [ ] **GitHub CLI** is logged in with the account that owns this repo (it pushes the fixes):

      ```bash
      gh auth status
      ```
- [ ] **Clone this repo** next to the project:

      ```bash
      git clone https://github.com/pantstamp/ai-dev-pipeline.git ~/src/ai-dev-pipeline
      ```
- [ ] **The project is clean:** `git status` shows nothing, and you are on its base branch or a
      branch freshly cut from it. Write down:
  - the base branch (`main` / `master` / `develop`)
  - its branch naming, if any
  - the CI workflow file
  - its code host (GitHub or GitLab) and where its issues live
- [ ] **Where the build root is.** If the Gradle project is in a subfolder of the git repository,
      note the subfolder. Claude Code runs in the build root, and every git path below is
      relative to the git root. This prints the subfolder (empty when they are the same):

      ```bash
      git rev-parse --show-prefix
      ```
- [ ] **The project does not track what the pipeline will add.** Run this from the build root.
      It must print nothing:

      ```bash
      git ls-files .claude/skills .claude/agents .claude/pipeline.md docs/features
      ```

      If it prints anything, the project already has files there of its own. Tell Claude at the
      start, so the install and the exclude list leave them alone.
- [ ] **Note what is there now**, to check against in part 6:

      ```bash
      git branch --list
      ```

      ```bash
      git log --branches --not --remotes --oneline
      ```

      The second lists local commits that are on no remote. Usually it prints nothing.
- [ ] **Pick a feature branch** for the gate: one that is **not merged yet**, with a change of a few
      files to a few dozen. Write down its name. (A merged branch gives a NO GO that says nothing
      about the gate.)
- [ ] Optional: **pick a small upcoming change** for the architect, if one is coming anyway.

## Part 3 — Start the session

From the project's build root, with this repo added (adjust both paths):

```bash
cd ~/src/the-project && claude --add-dir ~/src/ai-dev-pipeline
```

First message:

> Read `~/src/ai-dev-pipeline/docs/ai-pipeline/HANDOFF.md` and its `STEP4_GUIDE.md`,
> then run step 4 on this project with `pipeline-v2.1.0`, stopping at each of the guide's
> checkpoints for me to check. The base branch is `…`, and the feature branch for the gate is
> `…`. Keep every pipeline file local to this laptop: nothing is committed or pushed in this
> repository. Before using the `.claude/pipeline.md` you propose, show it to me. Nothing from
> this project's code, names or paths goes into the template repo: it is public.

## Part 4 — Your checkpoints during the session

Claude does the work; you check these.

### 4.1 The config it proposes

Claude reads the project's CI, build files and its rules (`CLAUDE.md`, and whatever that points
to), and proposes the project's `.claude/pipeline.md`. Check each value against what you know.
A tool the project does not have is `none`, not empty and not invented.

- [ ] `{{base}}` and the branch prefixes match how the team works
- [ ] `{{hosting}}` is the project's code host (GitHub or GitLab), and `{{issues}}` is where its
      issues live, or `none`
- [ ] `{{pre-flight}}` runs the **same tasks, in the same order, as CI**. Open the workflow file and
      compare.
- [ ] `{{formatter}}` and `{{format-fix}}`: the project's formatter, or `none`. If there is one,
      `{{format-fix}}` really fixes what its check flags.
- [ ] `{{verification}}` is a command that runs one module's unit tests. Try it once. If the task
      name differs per module (product flavours), the value says how to choose it.
- [ ] `{{lint}}`: the lint task a UI phase adds, and whether warnings fail it, or `none`
- [ ] `{{architecture-rules}}`: Konsist, detekt, something else, or `none`
- [ ] `{{rules}}`, `{{rules-home}}` and `{{rule-review}}` point to where the project really keeps
      its coding rules: `CLAUDE.md` sections, or the files it points to. If the project has no
      such rules, write that down; it is a finding, not something to paper over.
- [ ] `{{review}}` / `{{review-prompt}}`: the project's automated review, or `none`
- [ ] **The project's own checks pass on `pipeline.md`.** If its CI checks AI-instruction or docs
      files (for example, for paths that do not exist), run that check now and reword
      `pipeline.md` until it passes. The project's code stays as it is.

### 4.2 The install

Pick one way to install the pipeline:

| | **A. Machine-wide** (closest to the plugin) | **B. In the project** |
|---|---|---|
| Skills and agents go to | `~/.claude/skills/`, `~/.claude/agents/` | the project's `.claude/skills/`, `.claude/agents/` |
| In the project | only `.claude/pipeline.md` | those folders, plus `.claude/pipeline.md` |
| Also seen by | every project on this laptop, until removed | this project only |
| Before installing | back up any skill or agent of the same name already in `~/.claude` | nothing |

Either way, Claude copies the files from the tag, not from this repo's `master`. The list of what
the tag holds is also what part 6 removes:

```bash
git -C ~/src/ai-dev-pipeline ls-tree -r --name-only pipeline-v2.1.0 .claude/skills .claude/agents
```

- [ ] Right after the install, `git status` shows only new (`??`) files under `.claude/`, and **no
      modified file**. If the project already had a `.claude/settings.json` or skills of its own,
      nothing of it changed.

Then Claude adds the new project paths to `.git/info/exclude`: `.claude/pipeline.md`, with
`.claude/skills/` and `.claude/agents/` for option B, and `docs/features/` for the optional spec
and plan. That is git's ignore list that lives only in this clone; it is never committed or
pushed. From then on, `git add -A` and Android Studio's commit dialog skip the files, and the
skills still load, because Claude Code reads them from disk.

**Lines in `.git/info/exclude` are relative to the git root.** If the build root is a subfolder
(part 2), prefix each line with it. For option B, from the git root, with no subfolder:

```bash
printf '%s\n' '.claude/skills/' '.claude/agents/' '.claude/pipeline.md' 'docs/features/' >> .git/info/exclude
```

(Leave out a line for any path the project already tracks; see part 2.)

- [ ] `git status` is now **clean**, with the pipeline files still on disk.

### 4.3 The gate on the feature branch

Check out the feature branch you picked; the excluded files come along. Then:

> /pre-pr-checklist — feature name: `…`

**Expected:** the branch has no `SPEC.md` or `PLAN.md`, so the report's first line says Checks 1
(plan coverage) and 2 (acceptance criteria) were not run, and the verdict covers code quality and
CI only. That's fine. Look at the rest:

- [ ] It fetched the base branch and diffed against `origin/<base>`, and the diff holds only the
      branch's own files
- [ ] The residue scan (Check 3) and git state (Check 4) make sense, and Check 4 does not report
      the pipeline files (they are excluded)
- [ ] The rule review (Check 5) quoted the project's rules, not this template's
- [ ] The pre-flight ran the project's CI tasks and read the result correctly; any tool set to
      `none` is reported as skipped
- [ ] Anything it did that only makes sense for the template (Konsist, `test/konsist`,
      `warningsAsErrors`, `@claude-review`) → a finding

### 4.4 Optional: the architect on a small real change

Only if a small change is coming anyway. Start with `/product-owner` (it creates a local feature
branch and the spec), then `/architect`. Stop after the plan; don't build it. This is the stage
most sensitive to wording, so it is worth one real run.

Both skills normally **commit** their SPEC.md and PLAN.md. Here they must not, so say it when you
start each one:

> /product-owner … — dry run: write SPEC.md, but do not commit it.

> /architect … — dry run: write PLAN.md, but do not commit it.

The files sit in `docs/features/`, which the exclude list hides. If a skill tries to commit
them anyway, git refuses: it will not add an excluded path without `-f`.

- [ ] Neither skill ran `git commit`, and `git status` is still clean
- [ ] The plan's verification commands are the project's, with every task name resolved, not
      `./gradlew :test:konsist:test` or a guessed task
- [ ] The phases follow the project's module structure

### 4.5 The findings list

Ask Claude for the list. Each finding is one of:

| Kind | Example | Where it is fixed |
|---|---|---|
| A fact the config missed | the project runs a check that no name covers | a new name in `pipeline.md`'s table |
| A process gap | the gate cannot handle a case the project has | a skill, in this repo |
| A gap in the project | it keeps no rules about coroutines | the project's rules, by the team, later |

- [ ] **Read every finding for leaks before it goes anywhere.** This repo is public. Write
      findings as mechanisms ("a project without an architecture-rule tool"), never the project's
      class names, paths, rules or code.

## Part 5 — Bring the fixes back here

In the same session (it can see this repo), or a new one in this repo:

- [ ] Claude writes the agreed fixes into a brief here (like `V2.1.0_FIXES.md`), then makes them on
      a branch. Read the diff: **no names from the project**.
- [ ] Claude runs the golden scenarios for the stages the fixes touch. Each must pass 3 of 3. If one
      misses a run, it compares with the previous release the same day before changing anything
      (suite README, "Status").
- [ ] Release it following [`.claude/skills/README.md`](../../.claude/skills/README.md#versions): a
      patch for wording only, a minor for new behaviour or new config names. The CHANGELOG entry
      and `PIPELINE_VERSION` (both in `.claude/skills/`) go in the PR.
- [ ] Merge the PR, then tag the merge commit `pipeline-vX.Y.Z`.

## Part 6 — Clean up the project

- [ ] **Keep the project's `pipeline.md`**: copy it somewhere safe outside the repo. It is needed again
      when the project installs the plugin and commits its config.
- [ ] Ask Claude to **remove exactly what the install added**: the tag's file list from 4.2 (in
      `~/.claude` for option A, in the project for option B), `.claude/pipeline.md`, and the optional
      run's `docs/features/{feature-name}/` folder. Excluded files don't show in `git status`, so
      this list, not `git status`, says what to delete. If `.claude/settings.local.json` did not
      exist before the session, it can go too; it holds only this laptop's permission approvals.
- [ ] Option A: **restore the backups** of any skill or agent the install replaced in `~/.claude`.
- [ ] **Remove the lines added to `.git/info/exclude`.** Leave any lines that were there before.
- [ ] Delete any local branch the optional run created (`git branch -D …`). It was never pushed.
- [ ] **Check nothing is left.** `git status` is clean. `git branch --list` and `git log --branches
      --not --remotes --oneline` show what they showed in part 2.

## Done when

- [ ] The project's `pipeline.md` is written, reviewed, passes the project's own checks, and is
      saved.
- [ ] The gate (and optionally the architect) ran on the project, and every finding is listed.
- [ ] Every finding is fixed here or recorded as the project's own gap.
- [ ] The release is tagged, and the suite still passes.
- [ ] The project is as it was: its working tree is clean, its branches and local commits match
      part 2, and nothing was pushed to it.

Next is step 5, packaging the plugin, in this repo. See the roadmap's §2.
