# Retrospective: Watched Movies

PR: [#14](https://github.com/pantstamp/ai-dev-pipeline/pull/14)
Feature merged: 2026-04-12
Retrospective written: 2026-09-22

> **This is a backfill.** The retrospective skill did not exist when this feature
> shipped, so this was reconstructed five months later from the PR's review
> comments, its fix commit, and the state of the code today. It is written from
> durable artifacts rather than memory: every count below comes from the GitHub
> API, and every claim about what was or was not fixed was checked against the
> current source. What it cannot reconstruct is the conversation — what was
> discussed and decided in the session itself is gone.
>
> It exists to be the baseline the next retrospective is measured against.

## Summary

The watched-movies feature added a rating flow, a Room-backed watched list, a
tabbed catalog screen and a schema migration: +3,495 / −123 across 55 files. It
was reviewed in two waves and merged about twelve hours after it opened.

The automated review raised **16 inline findings**. Twelve arrived in the first
wave and all twelve were fixed in a single commit. Four arrived in a second wave
twenty-five minutes after that fix commit and five minutes before the merge —
**none of those four were addressed, and all four are still in the code today.**

## Findings by severity

The baseline. Counts are from `gh api repos/.../pulls/14/comments`, tallied by
the severity tag on each inline comment.

| Severity | Wave 1 (09:43) | Wave 2 (17:41) | Total | Fixed |
|---|---|---|---|---|
| 🔴 Critical | 1 | 0 | **1** | 1 |
| 🟠 Major | 5 | 3 | **8** | 5 |
| 🟡 Minor | 4 | 1 | **5** | 4 |
| 🟢 Suggestion | 2 | 0 | **2** | 2 |
| **Total** | **12** | **4** | **16** | **12 (75%)** |

Two caveats on these numbers, both worth knowing before comparing a future
retro against them:

- **The review's own summary table disagrees with its inline tags on one
  finding.** The `LifecycleEventEffect` redundant-loads issue is tagged
  `🟠 Major` inline and listed under `🟡 Minor` in the summary comment. The
  table above uses the inline tags, since those are the per-finding record.
  The summary therefore reports 4 Major / 4 Minor for wave 1 where this reports
  5 / 4.
- **75% is a fix rate, not a quality score.** It is 100% of wave 1 and 0% of
  wave 2. Averaging the two hides the only thing that matters here.

## What went well

From the review's own "what looks good" section, and confirmed in the code:

- Clean Architecture layering respected end to end — DB model → domain model →
  UI model, no layer skipping
- The Room migration (`MIGRATION_1_2`) is correct and the exported schema JSON
  was committed
- `WatchedMovieUiModel` annotated `@Immutable`, and `ImmutableList` used in
  `WatchedMovieListUiState` — both correct for recomposition stability
- Use-case and repository tests written with Mockative + Turbine, consistent
  with existing conventions
- `WatchedMovieDao` uses the `ABORT` conflict strategy, correct for immutable
  ratings
- Both ViewModels correctly scoped to the same `NavBackStackEntry`

And the fix commit itself ([`c08fc00`](https://github.com/pantstamp/ai-dev-pipeline/commit/c08fc00a48992b082fa0e9bcb32085404ece666f))
was thorough: twelve of twelve wave-1 findings addressed, including both
optional suggestions, with tests updated to match the renamed side effects and
removed event. When findings were acted on, they were acted on properly.

## What the reviewer caught

### Critical (fixed)

**Stuck UI state on a null-data race** — `MovieDetailsViewModel.kt:74`

- **Issue**: `isRatingInProgress` was set to `true` before launching, and the
  `viewState.value.data == null` branch did a bare `return@onSuccess`. The
  rating controls stayed permanently disabled with no feedback.
- **Root cause**: an early-return guard written for the happy path, in a
  ViewModel with two concurrent coroutines launched from `Init` — the race
  that makes `data` null was not considered.
- **Fix**: reset the flag and emit an error side effect in the null branch.
- **Prevention**: any state flag set before a `launch` needs a reset on *every*
  exit path, including early returns.

### Major (5 of 8 fixed)

Fixed in wave 1:

1. **Coroutine accumulation on repeated events** — `WatchedMovieListViewModel.kt:25`.
   `GetWatchedMovies` fired on every `ON_RESUME`, including every tab switch,
   each time launching a new collector of a Room flow that never completes.
   After *N* tab switches, *N* collectors raced to update state. Fixed by moving
   the load into `init {}` and deleting the event and the `LifecycleEventEffect`.
2. **Nested `.collect` inside a suspend callback** — `MovieDetailsViewModel.kt:85`.
   Fixed by extracting a `saveRating()` helper to flatten the control flow.
3. **`flow { inner.collect { emit(it) } }` breaks cancellation** —
   `MoviesRepositoryImpl.kt:94`. Fixed with `emitAll`.
4. **`System.currentTimeMillis()` not injectable** — `MoviesRepositoryImpl.kt:87`,
   which made `ratedAt` untestable. Fixed by injecting `clock: () -> Long`.
5. **`LifecycleEventEffect(ON_RESUME)` redundant loads** —
   `WatchedMovieListScreen.kt:87`. Fixed with the same change as #1.

**Still unfixed — see the section below**: the DAO threading issue, the
`releaseYear`/`releaseDate` data-integrity bug, and `NotFound` used as control
flow.

### Minor and suggestions (all wave-1 items fixed)

- Hardcoded strings in both ViewModels → replaced with typed side effects and
  `@StringRes` ids — fixed
- Shadowed `it` lambda parameter → renamed to `movie` — fixed
- `LocalContext.current.getString()` in a composable → `stringResource()` — fixed
- `HorizontalPager` instead of `if/else` tab switching — adopted
- Missing `@Preview` composables → added for three composables — fixed
- Mapper convention violation (wave 2) → **not fixed**

## The four that got away

This is the finding that matters most, and it is a process failure rather than a
coding one.

The fix commit landed at **17:16**. The second review wave posted at **17:41**.
The PR merged at **17:46** — five minutes later, with no commit after the fix.
Three Major findings and one Minor were raised and merged past. Checked against
the current source, **all four are still present**:

| Finding | Location today | Still there? |
|---|---|---|
| `getWatchedMovieEntity()` is neither `suspend` nor returns `Flow` | `WatchedMovieDao.kt:20` | yes |
| `WatchedMovieDbMapper` is public and implements no mapper interface | `WatchedMovieDbMapper.kt:6` | yes |
| `releaseDate = movie.releaseYear` stores a truncated year in a date field | `MovieDetailsViewModel.kt:101` | yes |
| `ErrorModel.NotFound()` used to signal "not rated yet" | `MoviesRepositoryImpl.kt:107` | yes |

The third is a live data-integrity bug: `MovieUiModel` carries only the
formatted `releaseYear` (`"2014"`), and that is what gets written to a column
holding a full release date (`"2014-03-07"`). It reads back correctly today only
because the UI mapper takes the first four characters either way. Any feature
that needs the real date gets a wrong value.

**Root cause**: there was no checkpoint between "the last review comment
arrived" and "merge". A second review round is exactly when attention is
lowest — the work feels finished, the first round was cleared, and the new
comments look like an echo of the ones already handled.

**Prevention**: before merging, re-read the review comments posted *after* the
most recent commit. If any exist, they are by definition unaddressed.

## Patterns to watch

**Flow and coroutine lifecycle is the dominant failure mode.** Four of the six
Critical/Major findings in wave 1 were the same class of mistake: nested
`collect` instead of `emitAll`, nested `collect` instead of `flatMapLatest`,
a long-lived collector relaunched per lifecycle event, and a state flag not
reset on an early return from a coroutine. These are not careless errors — each
one compiles, passes every check the project runs, and works on the happy path.
They fail only under cancellation, races, or repeated navigation. CLAUDE.md
currently has no guidance on any of them.

**A convention with no mechanical enforcement gets violated and stays
violated.** `WatchedMovieDbMapper` breaks two documented CLAUDE.md rules: the
`internal` visibility rule for `:core:database:room`, and "all mappers implement
typed interfaces from `:architecture:mapper`". It was caught by review, never
fixed, and is still public and interface-less five months later. No Konsist rule
mentions mappers at all — verified by grep across `test/konsist/`. The rule
exists in prose and nowhere else, which is why nothing stopped it.

**Strings in ViewModels.** Two separate files hardcoded user-facing English in
the ViewModel layer. Both were fixed, but it happened twice in one feature.

## Recommended CLAUDE.md updates

Ready to paste into the MVI / architecture sections:

> **Flow composition in repositories**: build a derived flow with `emitAll`, not
> `flow { inner.collect { emit(it) } }`. The nested form does not propagate
> upstream cancellation reliably.

> **Nested collection in ViewModels**: never call `.collect` on a second flow
> inside the collector of a first. Chain with `flatMapLatest`, or extract a
> private helper that launches the second collection separately.

> **Long-lived flows and lifecycle events**: a Room-backed `Flow` never
> completes, so collecting it from a lifecycle event (`ON_RESUME`,
> `LifecycleEventEffect`) leaks one collector per event. Subscribe once in
> `init {}`; if a reload really must be triggerable, cancel the previous `Job`
> first.

> **State flags around coroutines**: a flag set before `launch` must be reset on
> every exit path, including early returns inside `onSuccess` / `onError`.

> **Time is injected, never read**: inject `clock: () -> Long` rather than
> calling `System.currentTimeMillis()` in a repository, or the value cannot be
> asserted in tests.

> **Absence is not an error**: "no row found" is a normal outcome. Model it as
> `Success(null)` with a nullable type, not `Error(ErrorModel.NotFound())`.
> Reserving `Error` for real failures keeps it meaningful when one occurs.

## Recommended Konsist rules

Two of this feature's findings are mechanically checkable, and mechanical
enforcement is the only kind that survives a merge under time pressure:

1. **Mappers implement a mapper interface.** Every class whose name ends in
   `Mapper` under `:core:*` should implement one of the `:architecture:mapper`
   interfaces. Would have caught `WatchedMovieDbMapper` — and would still fail
   today, which makes it easy to verify the rule works.
2. **Room DAO functions are `suspend` or return `Flow`.** A DAO function that is
   neither is a blocking main-thread query. Would have caught
   `getWatchedMovieEntity()`, and also still fails today.

Both rules would go red on the current codebase. That is the point: the findings
are real and unfixed, so the rules have something to catch on their first run.

## Recommended process updates

- **Check for review comments newer than your last commit before merging.** This
  feature's only real miss came from not doing it. It is one API call:
  `gh api repos/{owner}/{repo}/pulls/{n}/comments --jq '.[] | select(.created_at > "<last commit time>")'`
- **The review workflow was unreliable at the time.** `@claude Please review
  this PR` was posted eleven times between 07:28 and 09:31 before a review
  landed at 09:43. That has since been addressed in
  [#19](https://github.com/pantstamp/ai-dev-pipeline/pull/19), which
  made the review opt-in, API-key authenticated, and honest about failures — but
  it is worth recording that roughly two hours of this feature's cycle time went
  to retrying a silent failure.

## Baseline for next time

The numbers a future retrospective should be compared against:

| Metric | This feature |
|---|---|
| Total review findings | 16 |
| Critical | 1 |
| Major | 8 |
| Minor | 5 |
| Suggestion | 2 |
| Findings fixed before merge | 12 (75%) |
| Findings merged unaddressed | 4 |
| Findings still live today | 4 |
| Review waves | 2 |
| Review invocations needed | 11 |
| Diff size | +3,495 / −123 across 55 files |

The target for the next feature is not "fewer findings" — a smaller diff would
achieve that without meaning anything. It is **zero findings merged
unaddressed**, and a Critical/Major count that falls as the CLAUDE.md rules
above start doing their work.
