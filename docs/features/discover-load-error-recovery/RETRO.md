# Retrospective: Discover Load Error Recovery

PR: [#24](https://github.com/pantstamp/ai-dev-pipeline/pull/24) (fixes [#23](https://github.com/pantstamp/ai-dev-pipeline/issues/23))
Merged: 2026-09-26
Retrospective written: 2026-09-26

## Summary

A bug fix. With no cached movies, a failed first load left the Discover tab blank with no way to
recover. The fix adds error and empty states with Retry and pull-to-refresh, separates full-screen
loading from refreshing, keeps cached movies when a refresh fails or comes back empty, and removes
hardcoded strings from the ViewModel. It went through the full pipeline: product-owner spec,
architect outline then plan, three developer phases, the pre-PR gate, one review.

The automated review raised **4 inline findings: 0 Critical, 0 Major, 2 Minor, 2 Suggestions.** All
four were accepted and fixed in one follow-up commit. The review ran once, and no comment arrived
after the last commit. **Zero findings were merged unaddressed.**

Two further problems surfaced outside the review: a lint error during implementation, and preview
render failures that the user found in Android Studio. Both are covered below. The second one turned
out to be a defect that has been in the codebase since PR #14.

## Compared with the baseline

| Metric | watched-movies (#14) | This feature (#24) |
|---|---|---|
| Total review findings | 16 | 4 |
| Critical | 1 | 0 |
| Major | 8 | 0 |
| Minor | 5 | 2 |
| Suggestion | 2 | 2 |
| Findings fixed before merge | 12 (75%) | 4 (100%) |
| Findings merged unaddressed | 4 | **0** |
| Review waves | 2 | 1 |
| Review invocations needed | 11 | 1 |
| Comments newer than the last commit at merge | 4 | 0 |
| Diff size (code, excluding docs) | +3,495 / −123, 55 files | +499 / −141, 11 files |
| Fix-commit size | — | +103 / −10, 4 files |

The watched-movies retro set the target as "zero findings merged unaddressed, and a Critical/Major
count that falls as the CLAUDE.md rules start doing their work". **Both targets were met.** The
features differ a lot in size, so the drop in the total count means little. What does mean something
is *which kind* of finding disappeared, covered in the next section.

## What went well

**The Flow and coroutine rules added after the last retro held.** In watched-movies, four of the six
wave-1 Critical/Major findings were lifecycle and cancellation mistakes. This feature touched exactly
that area: a ViewModel load, a refresh, a state flag set before `launch`, and a load triggered by a
lifecycle event. It produced none of those findings. Each rule is visible in the diff:

- The load moved from `LifecycleEventEffect(ON_CREATE)` to `init {}` ("subscribe once in `init`").
- A tracked `loadJob` is cancelled before each new load ("cancel the previous `Job` first").
- Both `isLoading` and `isRefreshing` are reset on every terminal branch ("reset on every exit path").
- There are no strings in the ViewModel: a typed `MovieListError` plus `messageRes()` / `iconRes()`
  in the composable ("keep user-facing strings out of ViewModels").

The review's summary called this out itself: the fix "avoids the coroutine and flow mistakes
CLAUDE.md warns about".

**The spec interview found more than the issue did.** Issue #23 listed three root causes. Reading the
code during the product-owner step found four more, all of which went into the spec and got fixed:

- A refresh hid the list, because `isLoading` was checked first.
- Overlapping loads raced each other.
- `ErrorClassifier` put hardcoded English into `DomainError.message`.
- A successful but empty response gave the same blank screen.

**The plan recorded its deviations, and the implementation followed them.** The architect found that
`shouldEmitErrorStateWhenFetchingMoviesFails` *asserted the bug* (`errorMessage` null after an error)
and told the developer to delete it rather than adjust it. It also recorded the deviations from
existing patterns: loading from `init`, the `when` order, and not copying the old `onError` block.
None of them were silently reverted to the old pattern.

**The tests were checked against deliberate breakage.** Removing the `loadJob?.cancel()` line makes
`shouldApplyOnlyLatestLoadWhenRefreshStartsDuringLoad` fail. Changing `hasMovies` to `data != null`
makes the test added in review fail. Both were run to confirm the tests catch the regression they
exist for.

**Unrelated problems were reported, not fixed.** The `MviViewModel.setState` race, `getMovie` returning
`NotFound` for a missing row, and cached movies never being deleted were all recorded in PLAN.md and
the PR description for separate triage. None were folded into the bug fix.

## What the reviewer caught

No Critical or Major findings.

### Minor and suggestions (all fixed in `a1e3723`)

**1. 🟢 A `UiState` default encoded ViewModel behaviour.** `MovieListViewModel.kt:82`
- **Issue**: `MovieListUiState` defaulted to `isLoading = true` so that the first frame showed a
  spinner. As a result, `MovieListUiState(data = emptyList())` and `MovieListUiState(error = …)` both
  rendered a spinner instead of what they described.
- **Root cause**: **the plan.** The architect put "the ViewModel loads on start" into the data class,
  because that was the nearest place to fix a blank first frame. The developer implemented the plan
  exactly, as the developer skill requires. The defect was designed in, not coded in.
- **Fix**: the default is back to `false`, and the ViewModel passes
  `initialState = MovieListUiState(isLoading = true)`.
- **Prevention**: a CLAUDE.md rule (below). A `UiState` default describes a neutral state. What
  depends on the ViewModel's behaviour goes in `initialState`.

**2. 🟡 No previews for the new screen states.** `MovieListScreen.kt:148`
- **Issue**: the error and empty states and `MovieListStatus` had no `@Preview`, though the Watched
  screen sets that pattern. The empty state was the one acceptance criterion never seen running.
- **Root cause**: the plan's UI phase listed composables and a manual emulator check, but no previews.
  The pre-PR gate *did* notice the empty state was never seen running. It proposed a throwaway code
  hack to see it, rather than a preview.
- **Fix**: three previews were added, plus a preview-only Koin wrapper. See "Issues found outside
  review" for why the wrapper was needed.
- **Prevention**: a CLAUDE.md rule making every visual state a screen can render get a preview.

**3. 🟡 No test for retry from the empty state.** `MovieListViewModel.kt:49`
- **Issue**: `hasMovies` is false both when `data` is null and when it is an empty list. Retry from
  the error state (`null`) was tested, but retry from the empty state (empty list) wasn't. That second
  case is the only thing distinguishing `hasMovies` from `data != null`.
- **Root cause**: the test table was built from the spec's "Testing" list, not from the branches in
  the code. A condition that treats two different states the same was never broken down into both.
- **Fix**: `shouldShowLoadingWhenRetryingFromEmptyState`, which was confirmed to fail if `hasMovies`
  becomes `data != null`.
- **Prevention**: a CLAUDE.md testing rule. When one condition covers two states, test both.

**4. 🟢 Repository test gaps.** `MoviesRepositoryImplTest.kt:179`
- **Issue**: the empty-network, empty-cache test did not assert that nothing was written, although
  its sibling test did. It also covered only `ignoreCache = false`, while Retry takes the `true` path.
- **Root cause**: the same as #3. The tests covered the plan's list rather than every way into the
  new branch, and two neighbouring tests asserted different things.
- **Fix**: both tests now share an `assertEmptyResultWithoutWrite(ignoreCache)` helper, and a second
  test covers `ignoreCache = true`.
- **Prevention**: covered by the same testing rule.

## Issues found outside review

**A. Lint: `LocalContext.current.getString()` in a composable** (caught by lint during Phase 3)

Phase 2 wrote the refresh-failed toast as `context.getString(sideEffect.error.messageRes())`. Lint
rejects that (`LocalContextGetResourceValueCall`), and because lint runs with `warningsAsErrors` it
failed the build. It surfaced only in Phase 3, because **the plan's Phase 2 check was
`testDebugUnitTest assembleDebug spotlessCheck`, without `lint`**. The fix was `LocalResources.current`.

**This is a repeat.** The watched-movies retro lists "`LocalContext.current.getString()` in a
composable → `stringResource()`" as a fixed Minor finding. It did not become a CLAUDE.md rule, so it
happened again. Lint caught it this time, so it never reached review, but it cost a failed check and
it will keep happening.

**B. Previews failed to render** (found by the user in Android Studio)

The three new previews threw `KoinApplication has not been started`. The screen resolves its effect
dispatcher with `getKoin()` inside the composable, and a preview never starts Koin. The fix in this
PR is a preview-only `KoinApplicationPreview` wrapper (`MovieListPreviewKoin`). The root cause is
tracked in [#25](https://github.com/pantstamp/ai-dev-pipeline/issues/25).

**This also rewrites a claim in the watched-movies retro.** That retro lists "Missing `@Preview`
composables → added for three composables — fixed". `WatchedMovieListScreen` makes the same
`getKoin()` call, so those previews have almost certainly never rendered. They compiled, and nothing
in CI renders a preview, so "fixed" was never true. The reviewer who suggested previews for this PR
didn't spot it either.

I (Claude) flagged the risk when adding the previews ("they will behave exactly like the Watched ones
do"), but assumed the Watched previews worked and didn't check. A caveat that depends on an untested
assumption is not a verification.

**C. Manual testing**: the user's seven-step emulator run found no functional problems.

## Patterns to watch

**CI can't see UI rendering, and the gaps stack up unnoticed.** Previews are compiled but never
rendered. The empty state was reachable only through a preview. The Watched previews have been
broken for five months. Every visual check in this pipeline depends on a human opening Android
Studio or an emulator. Until something renders previews in CI (a screenshot-test tool such as
Paparazzi or Roborazzi), treat any claim that a preview or visual state works, including "fixed" in
an earlier retro, as unverified until someone has looked.

**Tests follow the plan's list instead of the code's branches.** Findings #3 and #4 are the same
miss. The plan listed test cases, the developer wrote exactly those, and nobody checked them against
every way into the new code. The tests that do exist are strong: both were confirmed to fail when the
code they guard was broken on purpose. The gap is which cases got chosen, not how well they were
written.

**Plan-level defects get implemented faithfully.** Finding #1 came from the plan. The developer
skill's "implement exactly what the plan specifies" is the right rule, and it means the architect's
self-checks are the only place a design mistake like this gets caught before review.

**A lesson learned without a rule gets relearned.** `LocalContext.getString()` was fixed in #14 and
reappeared here. The Flow rules that *were* written into CLAUDE.md after #14 held. This retro
supports the practice of turning findings into rules.

## Recommended CLAUDE.md updates

Ready to paste.

Under **Architecture → MVI pattern**:

> - `UiState` defaults describe a neutral state (not loading, no error, no data). If the ViewModel
>   starts in a particular state — e.g. loading, because `init {}` starts a fetch — pass it as
>   `initialState = FooUiState(isLoading = true)`. A default that encodes ViewModel behaviour makes
>   every other construction (`FooUiState(data = …)` in a preview or test) render something else.

Under **Architecture → Flow and coroutines** (next to the strings rule), or a new **Compose** section:

> - **Read resources through Compose, not `LocalContext`.** In composition use `stringResource()`;
>   in a callback outside composition (e.g. an `ObserveEffects` handler showing a Toast) capture
>   `val resources = LocalResources.current` during composition and call `resources.getString()`.
>   `LocalContext.current.getString()` fails lint (`LocalContextGetResourceValueCall`) with
>   `warningsAsErrors`. This has come up in two features.
> - **Every visual state a screen can render gets a `@Preview`** — loading, error (each variant),
>   empty, content. CI compiles previews but never renders them; open them in Android Studio before
>   calling them done. Screens that call `getKoin()` throw in a preview until
>   [#25](https://github.com/pantstamp/ai-dev-pipeline/issues/25) lands; wrap them in
>   `KoinApplicationPreview` (see `MovieListPreviewKoin` in `MovieListScreen.kt`).

Under **Testing**:

> - **Test the branches, not just the listed cases.** When a condition treats two different states
>   the same (e.g. `hasMovies` is false for both `null` and an empty list), test both sides. When a
>   new code path can be reached from more than one caller (e.g. `ignoreCache = true` and `false`),
>   test each one. Assert the same side effects (e.g. "nothing written") across neighbouring tests.

## Recommended skill and review updates

- **architect skill**: a phase's check must include `lint` whenever the phase touches a composable
  or a resource file. Phase 2's `testDebugUnitTest assembleDebug spotlessCheck` missed a lint error
  that one extra task would have caught at the right time.
- **architect skill**: a UI phase lists the `@Preview`s to add, one per visual state, alongside the
  composables.
- **pre-pr-checklist skill**: when an acceptance criterion about a UI state can't be reached on a
  device, suggest a `@Preview` before any throwaway code change.
- **review prompt**: when suggesting or checking previews, also check whether the composable
  resolves dependencies itself (`getKoin()`, `koinInject()`). If it does, the preview will compile
  and still fail to render.

## Baseline for next time

| Metric | This feature |
|---|---|
| Total review findings | 4 |
| Critical / Major | 0 / 0 |
| Minor / Suggestion | 2 / 2 |
| Findings fixed before merge | 4 (100%) |
| Findings merged unaddressed | 0 |
| Findings that originated in the plan | 1 |
| Issues found outside review | 2 (lint, preview render) |
| Review waves / invocations | 1 / 1 |
| Diff size (code) | +499 / −141 across 11 files |

The target for the next feature:
- still **zero findings merged unaddressed**;
- **no repeat** of a finding already recorded in a retro (this feature had one: `LocalContext.getString()`);
- **every preview added in the PR opened and seen to render** before it counts as done.
