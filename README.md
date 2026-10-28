# ai-dev-pipeline

[![Build](https://github.com/pantstamp/ai-dev-pipeline/actions/workflows/build.yml/badge.svg)](https://github.com/pantstamp/ai-dev-pipeline/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/License-MIT-lightgrey)](LICENSE)

**An eval-gated AI development pipeline for production codebases, shipped as a Claude Code plugin.**

ai-dev-pipeline takes a feature from ticket to retrospective in six stages. AI agents do the work
inside each stage, and an engineer approves the result before the next one starts. Every release is
regression-tested against replayed scenarios before it ships. It is developed on a 30-module Android
app in this repository, its test bed, and in use on a production Android app.

Created and maintained by [Pantelis Stampoulis](https://www.linkedin.com/in/pantelisstampoulis/).

## The pipeline

| Stage | Skill | Produces | You decide |
|---|---|---|---|
| 1. Spec | `/ai-pipeline:product-owner` | the branch + `SPEC.md` | Are the acceptance criteria right? |
| 2. Plan | `/ai-pipeline:architect` | `PLAN.md` | The design — you propose it first |
| 3. Build | `/ai-pipeline:developer` | a commit per phase, then the PR | After **every phase** |
| 4. Gate | `/ai-pipeline:pre-pr-checklist` | GO / NO GO | Ship it or fix it |
| 5. Review | `@claude-review`, then `/ai-pipeline:review-triage` | PR comments → fixes | Which findings to act on |
| 6. Learn | `/ai-pipeline:retrospective` | `RETRO.md` + rule changes | Which lessons to keep |

Two stages hand bounded work to an agent: the developer implements each phase through
`ai-pipeline:phase-implementer`, and the gate runs as the read-only `ai-pipeline:pre-pr-gate`.
Each project describes itself (build commands, rules, issue tracker) in `.claude/pipeline.md`, so the
skills carry no project's names. [`docs/AI_WORKFLOW.md`](docs/AI_WORKFLOW.md) is the step-by-step
guide.

## How it is tested

A change to a skill is a change to a program whose output varies, so every release is regression
tested before it is tagged. The suite is in [`docs/ai-pipeline/`](docs/ai-pipeline/README.md).

- **Golden scenarios.** Four scenarios — [gate](docs/ai-pipeline/scenarios/gate-movie-search/),
  [architect](docs/ai-pipeline/scenarios/architect-movie-search/),
  [developer](docs/ai-pipeline/scenarios/developer-dry-run/) and
  [product owner](docs/ai-pipeline/scenarios/product-owner-movie-search/) — each replay a
  historical snapshot whose outcome is already known, with the current pipeline, and a judge model
  grades the result against criteria the run never sees. Multi-turn stages are answered by a
  simulated user working from scripted answers.
- **Three runs per scenario.** 3 of 3 passes, 2 of 3 is a warning, less fails.
- **The release rule:** no scenario may score worse than the previous release, **run the same day**.
- **Why the same day:** on 2026-10-20 the released 2.0.0 scored 1 of 3 on the architect, where it
  had scored 3 of 3 five days earlier, with no change to the pipeline. Model behaviour drifts, so an
  old baseline proves nothing about today.
- **A second codebase.** A dry run on a production Android project found eleven places where a
  skill still assumed this repository; ten were fixed in 2.1.0
  ([`V2.1.0_FIXES.md`](docs/ai-pipeline/V2.1.0_FIXES.md)).

The harness is [`eval.py`](docs/ai-pipeline/eval.py), a script around `claude -p`; each scenario's
`RESULTS.md` records its baselines.

## Install it

In the project, once per developer:

```bash
claude plugin marketplace add "https://github.com/pantstamp/ai-dev-pipeline.git#pipeline-v3.0.1" --scope project --sparse .claude-plugin plugins
claude plugin install ai-pipeline@ai-pipeline --scope project
```

The first developer commits the `.claude/settings.json` these write, which pins the version:

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

The project also writes its `.claude/pipeline.md` from the plugin's
[`pipeline.template.md`](plugins/ai-pipeline/pipeline.template.md).

**Update or roll back** only when the project chooses: move `ref` to the new tag in a PR, and each
developer runs `marketplace add` with that tag, then `claude plugin update ai-pipeline@ai-pipeline
--scope project`. Editing `ref` alone does nothing.

[`PLUGIN_GUIDE.pdf`](docs/ai-pipeline/PLUGIN_GUIDE.pdf) covers using, releasing, installing and
updating the plugin; [`AI_PIPELINE_ROADMAP.md`](docs/AI_PIPELINE_ROADMAP.md) §2 has the details.

## Versions

Each release is a `pipeline-vX.Y.Z` tag. The
[CHANGELOG](plugins/ai-pipeline/skills/CHANGELOG.md) says what changed, why, which test rule the
release passed and what a project must do to take it.

| Version | Date | What |
|---|---|---|
| 3.0.1 | 2026-10-28 | The repository renamed to `ai-dev-pipeline` |
| 3.0.0 | 2026-10-23 | Shipped as the `ai-pipeline` Claude Code plugin |
| 2.1.0 | 2026-10-21 | Fixes from the dry run on a second Android project |
| 2.0.0 | 2026-10-15 | Project facts moved into `.claude/pipeline.md` |
| 1.1.0 | 2026-10-13 | SPEC and PLAN record the pipeline version |
| 1.0.0 | 2026-10-13 | First release with a golden-scenario baseline |

## Roadmap

From [`AI_PIPELINE_ROADMAP.md`](docs/AI_PIPELINE_ROADMAP.md):

- **In progress: production use.** Installed on a production Android app, pinned to a release tag,
  and used for real tickets. Next is the first retrospective from it, which will show whether the
  results below hold outside this repository.
- **Then other platforms, Flutter first**, but only once a real project on that platform adopts it.
  With one platform there is no way to tell which parts are generic, and a first attempt to guess
  regressed two stages. Android first, deliberately.

## The test bed

The pipeline is built and measured on a Kotlin Android app that browses TMDB movies, kept to
production standards so that the pipeline's results mean something.

- **Clean Architecture + MVI** across **30 Gradle modules**. Features depend only on core modules;
  network, database and logging are each an `api` module plus an implementation and a `noop`.
  See [`ARCHITECTURE.md`](docs/ARCHITECTURE.md) and [`MODULARIZATION.md`](docs/MODULARIZATION.md).
- **Executable architecture rules.** The `:test:konsist` module fails the build when a rule is
  broken — a ViewModel taking a repository, a `RepositoryImpl` without a test, a mapper that
  declares no mapper interface. Retrospectives add new rules here.
- **153 unit tests** (JUnit4, Truth, Turbine, Mockative) and **22 architecture tests**.
- **CI on every PR:** unit tests, Konsist, Spotless and Android Lint with warnings as errors.
- **Toolchain:** Kotlin 2.4.20, Jetpack Compose, Koin, Retrofit 3 + OkHttp 5, Room, Coil 3. All
  versions are in [`libs.versions.toml`](gradle/libs.versions.toml). AGP stays on 8.x and Mockative
  on 2.x on purpose; [`CLAUDE.md`](CLAUDE.md) says why.

<img src="docs/images/architecture_overall.png" width="600px" alt="Diagram showing overall app architecture" />

### Building it

Gradle runs on **JDK 21**; the compile JDK is provisioned automatically. Add a
[TMDB](https://developer.themoviedb.org/docs/getting-started) API Read Access Token to
`local.properties` as `TMDB_API_KEY=<token>`, then build in Android Studio or run:

```bash
./gradlew test                 # unit tests
./gradlew :test:konsist:test   # architecture rules
./gradlew spotlessCheck        # formatting (spotlessApply to fix)
./gradlew lint                 # Android Lint
```

## Worked examples and results

Each feature's folder in [`docs/features/`](docs/features/) holds the SPEC, PLAN and RETRO the
pipeline produced, newest first:

- **[movie-search](docs/features/movie-search/)** — the one to read. Search as you type, offline
  details, one review round, zero findings merged unaddressed, and a retro tracing five of six
  findings back to the plan.
- [screen-koin-free-previews](docs/features/screen-koin-free-previews/) — spec and plan only.
- [discover-load-error-recovery](docs/features/discover-load-error-recovery/) — error recovery and
  retry on the Discover tab.
- [watched-movies](docs/features/watched-movies/) — the first run, and the baseline the others are
  measured against.

The automated review's findings on the three features with a retrospective, as the pipeline stood
at the time:

| | [watched-movies](docs/features/watched-movies/RETRO.md) (#14) | [discover-load-error-recovery](docs/features/discover-load-error-recovery/RETRO.md) (#24) | [movie-search](docs/features/movie-search/RETRO.md) (#32) |
|---|---|---|---|
| Review findings | 16 | 4 | 7 |
| Critical / Major | 1 / 8 | 0 / 0 | 0 / 0 |
| Merged unaddressed | 4 | 0 | 0 |
| Findings that originated in the plan | — | 1 | 5 |

On movie-search, **five of the six real findings came from the plan, not the code**, which is why
the architect stage has its own golden scenario replaying that feature.

Limits: three features, one developer, and the counts are not normalised by the size of each diff.
Nothing here measures speed; the claim is about quality.

## Where the work is

- Every change reaches `master` through a pull request:
  [merged PRs](https://github.com/pantstamp/ai-dev-pipeline/pulls?q=is%3Apr+is%3Amerged).
- The dated [CHANGELOG](plugins/ai-pipeline/skills/CHANGELOG.md), with the reason behind each
  change.
- [`V2.1.0_FIXES.md`](docs/ai-pipeline/V2.1.0_FIXES.md): what moving to a second codebase broke,
  and how each was fixed.
- The eval suite: [`eval.py`](docs/ai-pipeline/eval.py) and its
  [scenarios](docs/ai-pipeline/scenarios/).

## Contributing

The branch model, CI gates and release process are in [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[MIT](LICENSE)
