# Retrospective: Movie Search

PR: [#32](https://github.com/pantstamp/ai-dev-pipeline/pull/32) (fixes [#29](https://github.com/pantstamp/ai-dev-pipeline/issues/29))
Merged: 2026-09-27
Retrospective written: 2026-09-27

## Summary

A feature. The Discover tab gains a search field that searches TMDB by title as the user types. The
search waits for a 300 ms pause and a query of at least two characters, and results replace the
Discover list in place. Search never writes to the database. Movie Details now reads a movie from
the `movies` table and otherwise fetches it from the network without storing it, which also fixes
opening any movie that is not in Discover. Details gained offline and generic error states with a
Retry button, and every movie row gained a poster placeholder. It went through the full pipeline:
product-owner spec, architect outline then plan, six developer phases, the pre-PR gate, and one
review round.

The automated review raised **7 inline findings: 0 Critical, 0 Major, 3 Minor, 4 Suggestions.**
- **Five were fixed** in one follow-up commit.
- **One was a false positive.** It was answered, and the comment it questioned was reworded to be
  easier to verify.
- **One was deferred** to [#30](https://github.com/pantstamp/ai-dev-pipeline/issues/30), a
  spec gap, and recorded in the spec's Out of Scope.

The review ran once, and no finding arrived after the last commit. **Zero findings were merged
unaddressed.**

**Five of the six real findings came from the plan, not the code.** That is the main lesson of this
retro.

## Compared with earlier retros

| Metric | watched-movies (#14) | discover-load-error-recovery (#24) | This feature (#32) |
|---|---|---|---|
| Total review findings | 16 | 4 | 7 |
| Critical / Major | 1 / 8 | 0 / 0 | 0 / 0 |
| Minor / Suggestion | 5 / 2 | 2 / 2 | 3 / 4 |
| Fixed before merge | 12 (75%) | 4 (100%) | 5 fixed, 1 false positive, 1 deferred |
| Merged unaddressed | 4 | 0 | **0** |
| Findings that originated in the plan | — | 1 | **5** |
| Found outside review | — | 2 (lint, preview render) | 0 after merge; 4 during implementation (below) |
| Review waves / invocations | 2 / 11 | 1 / 1 | 1 / 1 |
| Diff size (code) | +3,495 / −123, 55 files | +499 / −141, 11 files | +2,715 / −258, 53 files |
| Fix-commit size | — | +103 / −10, 4 files | +202 / −148, 8 files |

**All three targets from the last retro were met:**
- zero findings merged unaddressed;
- no repeat of a finding already recorded in a retro;
- every preview added in the PR opened and seen to render.

**What changed is where findings come from.** The watched-movies findings were mostly coroutine and
lifecycle mistakes in the code. Here there were none: the diff is about five times the size of the
last feature, yet produced no Critical or Major findings and no coroutine or Compose rule violation
in the code itself. Instead, five of the six real findings were designed into the plan and
implemented faithfully. The developer skill says to implement the plan exactly, so a plan that
contains code is effectively code that nothing reviews before the PR.

## What went well

**The rules added after the last two retros held, across a much larger change.**
- The search pipeline is subscribed once in `init {}`.
- Details cancels its previous `movieLoadJob` before a Retry.
- `isSearching` and `isRefreshing` are reset on every exit path of `reduce`.
- No user-facing strings are in the ViewModels.
- `MovieSearchUiState` defaults to a neutral state.
- Absence is `Success(emptyList())`, never an error.
- Screens take only state and callbacks, with no Koin lookups.

The review summary confirmed each of these.

**The spec interview found what the issue could not.** Issue #29 described the symptom. Reading the
code during the spec stage showed that the `movies` table *is* the Discover list. That made the
obvious fix, caching search results in that table so Details could find them, a direct violation of
the requirement that search never adds to Discover. The whole data design (read the database first,
fetch from the network, never write) came from that reading. The code reading also found that the
poster placeholder the issue asked for did not exist yet, and that Details showed raw error text
with no Retry.

**Implementation caught two plan defects before review.**
- **Phase 5:** the plan relied on `MutableStateFlow` to ignore repeated queries. A `StateFlow`
  collector skips any value equal to the last one it saw, so "matrix" → "" → "matrix" processed
  before the collector resumed never searched again, and the spinner stayed up forever. This was
  caught by reasoning through the pipeline, fixed with a generation counter, and covered by a test
  that fails when the counter is removed.
- **Phase 4:** the plan's placeholder approach, Coil's painter slots, could not work. Painters are
  drawn with the image's `ContentScale.Crop`, and `colorFilter` would tint the real poster.

Both were raised as deviations, approved, and written back into PLAN.md, so the gate compared the
diff against the plan as agreed.

**Tests were proved able to fail.** For each test that guards one specific behaviour, that behaviour
was broken on purpose and the test confirmed red:
- `include_adult=false`;
- the database not being written, for success, offline and 404;
- dropping duplicate ids;
- Details Retry reloading only the movie, and clearing its error while loading;
- the generation counter;
- a failed refresh keeping the results;
- ignoring repeated text;
- the review-round refresh guard.

**Manual testing was done on an emulator, not left for later.** The 10-step checklist from the plan
was driven over `adb`, with screenshots and OkHttp logs as evidence:
- one request per query;
- no request when clearing, rotating or switching tabs;
- the scroll position kept across a trip to Details and after process death;
- the database checked directly for searched movies (none).

The three offline cases ran in airplane mode with the user's permission. AC12, TalkBack and the
previews were then checked by the user, and all passed.

**The out-of-scope problems found on the way were split off, not absorbed.**
- **The TMDB bearer token logged in release builds, and wrong logger tags:** fixed in a separate PR,
  [#31](https://github.com/pantstamp/ai-dev-pipeline/pull/31).
- **Discover's missing order and stale rows:** filed as #30.

## What the reviewer caught

Review of `be634c1` (plus the `master` merge); fixed in `86121a5`.

**1. 🟡 Refresh while a new query was waiting for its pause.** `MovieSearchViewModel.refresh()`
- **Issue**: a pull-to-refresh during the 300 ms pause sent the search immediately, then again when
  the pause ended. It also showed the refresh indicator for what was really a new search.
- **Root cause**: **the plan.** It defined Refresh's behaviour by whether results were on screen,
  not by whether a search was already pending, and its test list never put a Refresh into the
  pending state.
- **Fix**: Refresh is ignored while `isSearching` is true. New test
  `shouldIgnoreRefreshWhileNewQueryIsPending`, confirmed to fail without the guard.
- **Prevention**: a CLAUDE.md rule (an event that starts work defines what happens when work is
  already pending), and an architect self-check.
- The reviewer's secondary point, that a failed refresh "fails the wrong way", was not accepted. The
  field already held the new query, and the spec (AC24) says a failed new query replaces the results
  with the full-screen error.

**2. 🟢 A retry or refresh request could be silently dropped.** `reruns` in `MovieSearchViewModel`
- **Issue**: `MutableSharedFlow(extraBufferCapacity = 1)` with the default `SUSPEND` loses the value
  when `tryEmit` hits a full buffer, and the `false` it returns was ignored. Each emit follows a flag
  being set, so a dropped emit could leave a spinner stuck.
- **Root cause**: **the plan.** It specified the buffer and `tryEmit` and even noted the dropping
  behaviour, but chose no overflow policy. It was safe today only because every emit carried the
  same query.
- **Fix**: `BufferOverflow.DROP_OLDEST`, so the newest request wins, matching `flatMapLatest`. No
  test, because both policies produce the same visible result today.
- **Prevention**: a CLAUDE.md Flow rule.

**3. 🟡 Pager comment "probably not true".** `MovieCatalogTabbedScreen.kt`
- **Issue**: the reviewer said `HorizontalPager` defaults to `beyondViewportPageCount = 0`, so
  Discover would leave composition while Watched is shown.
- **Assessment**: **a false positive.** This pager sets `beyondViewportPageCount = 1`, in a line
  that predates the PR and so wasn't in the diff. The reviewer judged from the default without
  reading the call site.
- **Change**: the comment now names the setting, so removing it visibly invalidates the comment.
- **Prevention**: a review-prompt instruction.

**4. 🟡 The same `LoadError` → message mapping written three times.** `DiscoverScreen`,
`MovieListScreen`, `MovieSearchResults`
- **Root cause**: **the plan.** It said Discover's message function "stays per screen because the
  generic text differs", which is true only for Details. Search and the Discover Toasts then each
  got a copy.
- **Fix**: one shared `LoadError.listMessageRes()`. Details keeps its own, because its generic text
  refers to one movie.

**5. 🟢 A long-running effect captured `onSearchEvent`.** `DiscoverScreen`
- **Issue**: a `LaunchedEffect` keyed only on the field state keeps calling the lambda it captured
  when it launched.
- **Root cause**: **the plan**, whose code for this effect was copied verbatim.
- **Fix**: `rememberUpdatedState(onSearchEvent)`.
- **Prevention**: a CLAUDE.md Compose rule.
- The same pattern exists in the shared `ObserveEffects`. It predates this PR and is noted below.

**6. 🟢 One screen package imported a component from another.** `MovieSearchResults` imported
`MovieRow` from `movielist`
- **Root cause**: **the plan.** It moved every other shared piece (`StatusMessage`,
  `PullToRefreshStatus`, `LoadError`) into shared packages, but told search to reuse `MovieRow` in
  place.
- **Fix**: `MovieRow` and `MoviePoster` moved to `presentation/uicomponent`.
- **Prevention**: a Konsist rule and a CLAUDE.md rule.

**7. 🟢 Details for a watched movie is network-only when offline.** `MoviesRepositoryImpl.getMovie()`
- **Issue**: a movie opened from the Watched tab that is not in the Discover table now goes to the
  network, so offline it shows the offline state, although `watched_movies` stores most of it.
- **Root cause**: **the spec.** The interview considered Details only as reached from Discover or
  search, not from Watched.
- **Decision**: deferred. A fallback would build a partial movie (no genre, no original title), which
  is a product decision tied to Discover removing stale rows. It was moved to #30, with a comment
  there and a line in the spec's Out of Scope.

## Issues found outside review

After merge: none. The user confirmed that all previews render, that AC12 works (a movie rated from
search appears in Watched with the right title, poster and rating), and that TalkBack reads the
field, the ✕ button and the status messages correctly.

During implementation, before review:

- **A. The `StateFlow` conflation defect in the plan's pipeline** (Phase 5), described under "What
  went well". It is the most serious defect in the feature, and no reviewer saw it, because it never
  reached the PR.
- **B. The Coil placeholder approach in the plan was not implementable** (Phase 4).
- **C. Two test-harness surprises** (Phase 5), which cost several test runs:
  - Mockative seems to count an invocation as used once one `verify` has matched it. A second
    `verify` for the same call saw zero invocations.
  - `runTest` advances all pending virtual time before it finishes. Queries typed at the end of a
    test were still searched after their debounce, and failed as unstubbed calls.
- **D. A false alarm in the gate's device check.** The first keyboard-on-scroll check swiped while
  the loading spinner was still showing, so nothing scrolled and the keyboard stayed open. A re-test
  with the results confirmed on screen passed. Acting on a state before confirming it is on screen
  produces a result about the wrong state.

## Patterns to watch

**The plan is now where defects start.** The last retro recorded one finding from the plan; this
one has five review findings from the plan, plus the two implementation found. The plan here was
detailed down to code: pipeline code, `refresh()`'s logic, the buffer declaration, the effect's
body. That detail made the developer stage fast and predictable, but it moved the design review into
the plan without adding a review of the plan. The CLAUDE.md rules held wherever the developer wrote
code from scratch. They were broken wherever the developer copied code the plan had written. **A
code block in a plan needs the same scrutiny against CLAUDE.md as a diff**, because it becomes one
unchanged.

**Event handling needs a state matrix, not a list.** Finding 1 and defect A are the same kind of
miss: an event (Refresh; retyping a cleared query) arriving in a state the design never considered
(a pending search; a collector that hasn't resumed yet). The "test the branches" rule covers
conditions in the code, but not events arriving in each possible state. The plan's 30-odd tests
were listed by feature, and none crossed an event with an in-flight state.

**Compose and Flow APIs with silent defaults.** `SharedFlow`'s `SUSPEND` overflow with `tryEmit`,
`StateFlow` dropping equal values, a `LaunchedEffect` keeping the lambda it first captured, and
`HorizontalPager`'s page retention (misread by the reviewer) all compile, pass every check, and
behave differently from how they read. Three of the four were caught before merge by reasoning
rather than tooling.

**A shared component in a screen package pulls screens together.** One import is harmless; nothing
prevents the next one, and screen packages gradually become coupled. This is mechanically
checkable.

**Not a problem, but notable:** `ObserveEffects` in `:core:presentation:mvi` keeps the `onEffect`
lambda it first captured, like finding 5. It predates this PR, affects every screen, and is harmless
today because every caller passes a stable lambda. It is a candidate for its own small fix.

## Recommended changes

All of the changes below were approved and applied in the retro PR.

### CLAUDE.md

Under **Architecture → Flow and coroutines**:

> - **A `StateFlow` is state, not an event stream.** A collector skips a value equal to the last one
>   it saw, so a change and its reversal (A → B → A) made before the collector resumes arrive as
>   nothing. If every change must trigger work, make each value distinct (e.g. wrap it with a
>   generation counter) or use a buffered `SharedFlow`.
> - **Give every `MutableSharedFlow` used with `tryEmit` an explicit `onBufferOverflow`.** With the
>   default `SUSPEND`, `tryEmit` on a full buffer returns `false` and the value is lost. When a flag
>   was set just before the emit, that is a stuck spinner. `DROP_OLDEST` makes the newest request
>   win, which is what a downstream `flatMapLatest` does anyway.
> - **An event that starts work defines what happens when work is already pending or running**:
>   ignore it, cancel and restart, or queue. Choose deliberately, and test the event in that state.
>   Retry, Refresh and query changes are the usual cases.

Under **Compose**:

> - **A long-running effect reads callbacks through `rememberUpdatedState`.** A `LaunchedEffect`
>   keyed on something other than the callback keeps calling the lambda it captured at launch.
> - **A composable used by more than one screen lives in `presentation/uicomponent`**, not in one
>   screen's package. A screen may compose another screen (`FooScreen`), but may not import another
>   screen's building blocks. Konsist-enforced (see below).

Under **Testing**:

> - **Mockative consumes an invocation once a `verify` matches it.** A second `verify` on the same
>   call sees nothing. Verify each call once, with the most specific matcher, and rely on unstubbed
>   calls throwing (`MissingExpectationException`) to prove nothing else ran.
> - **`runTest` advances all pending virtual time before it finishes**, so work queued at the end of
>   a test (a debounce, a `delay`) still runs. Stub every call that work will make, or the test fails
>   with a missing expectation.

### Konsist / lint

`PresentationLayerKonsistTest`: **a screen package imports only another screen's entry points.**
A file in `..presentation.screen.<a>..` may import from `..presentation.screen.<b>..` only names
ending in `Screen`, `UiState`, `Event`, `SideEffect` or `ViewModel`.

*Applied.* Run on `be634c1`, the rule flags `MovieSearchResults.kt` for its `screen.movielist.MovieRow`
import, the case review finding 6 was about. On `master` it found a **second case that review
missed**: `DiscoverScreen` imported `moviesearch.MovieSearchResults`, which was the search screen's
entry composable but not named like one. It was renamed `MovieSearchScreen` in the retro PR, to match
`MovieListScreen`, and the rule then passed unchanged.

### Skills

**architect**, added to "Before presenting it, answer these":

> - **Is every code block in the plan held to CLAUDE.md?** The developer implements plan code
>   verbatim, so a rule broken in the plan ships. Check each snippet as you would a diff: flows,
>   effects, buffers, flags.
> - **For each event a new ViewModel handles, what happens in every state it can arrive in** (idle,
>   pending, running, error, content)? Is there a test per state that matters?
> - **Does anything two screens use stay in one screen's package?**

**developer**, in step 2b (verification):

> When checking behaviour on a device or emulator, confirm the precondition is on screen (screenshot
> or log) before acting. An action taken while a spinner is still showing tests the spinner.

### Review prompt

In `.github/workflows/claude-review.yml`, in the review instructions:

> Before saying that a library default applies (for example a pager's page retention or a flow's
> buffer policy), check the call site and the surrounding file for an override. The diff shows only
> changed lines, and the relevant argument may predate the PR.

## Baseline for next time

| Metric | This feature |
|---|---|
| Total review findings | 7 |
| Critical / Major | 0 / 0 |
| Minor / Suggestion | 3 / 4 |
| Fixed / false positive / deferred | 5 / 1 / 1 |
| Findings merged unaddressed | 0 |
| Findings that originated in the plan | 5 (of 6 real) |
| Plan defects caught during implementation | 2 |
| Issues found after merge | 0 |
| Review waves / invocations | 1 / 1 |
| Diff size (code) | +2,715 / −258 across 53 files |

Targets for the next feature:
- still **zero findings merged unaddressed**, with every added preview seen to render;
- **fewer findings that originate in the plan**: the new architect self-checks should catch at the
  outline or plan stage what reached review here;
- **no repeat** of the event-in-a-pending-state miss or the silent-default Flow mistakes now recorded
  as rules.
