# Implementation Plan: Screen Koin-Free Previews

## Overview

The three movie-catalog screens resolve their effect dispatcher with `getKoin()` inside the
composable, which throws in every Compose preview. `ObserveEffects` becomes self-sufficient: it
always collects on `Dispatchers.Main.immediate`, and its `lifecycleOwner` defaults to
`LocalLifecycleOwner.current`. The screens then take only state, effects and callbacks. The
missing previews are added, and a Konsist rule keeps Compose-side Koin out of feature
presentation code.

- **Phase 1** — `ObserveEffects` owns its dispatcher; screens stop calling Koin
- **Phase 2** — A preview for every missing screen state
- **Phase 3** — Konsist guard against Compose-side Koin, and the CLAUDE.md rule

## References

- Spec: `docs/features/screen-koin-free-previews/SPEC.md`
- Issue: https://github.com/pantstamp/ai-dev-pipeline/issues/25
- Project conventions: `CLAUDE.md`

## Decisions

- **Hard-code `Dispatchers.Main.immediate` in `ObserveEffects` and remove the parameter.**
  Nobody varies it, and `Dispatchers.setMain` already replaces `Main.immediate` in unit tests.
- **`lifecycleOwner` defaults to `LocalLifecycleOwner.current`.** All three callers pass exactly
  that.
- **Keep every `CoroutinesDispatchers` binding.** `dispatcherModule` is a general catalogue for
  injected non-UI classes, including `MainImmediate`, which is now unused.
- **One private `previewMovie: MovieUiModel` per screen file**, as `WatchedMovieListScreen.kt`
  already does. There is no shared preview-data file.
- **The Konsist rule checks imports only.** A fully qualified `org.koin.compose.getKoin()` with
  no import would bypass it. That gap is accepted, because a text scan would be fragile and
  would also match comments.
- **The rule covers all of `..feature..presentation..`**, not just `..screen..`. It bans
  `org.koin.compose..` and `org.koin.androidx.compose..` only, so the Koin DSL imports in
  `presentation/di` remain legal.
- **No new automated tests** besides the Konsist rule. Effects are verified manually (Phase 1),
  and previews are verified by opening them in Android Studio (Phase 2).

## Observations on existing code

- **Deviations**
  - The screens currently obtain the effect dispatcher from Koin
    (`getKoin().get<CoroutineContext>(named(CoroutinesDispatchers.MainImmediate))`) and wrap
    previews in `KoinApplicationPreview`. **Do not copy either into any screen or preview.**
    After Phase 1 the only call form is `ObserveEffects(effect = effect) { … }`, and previews
    call the screen directly.
- **Notes** (not touched by this plan)
  - `MovieDetailsScreen` sends `Init` from `LifecycleEventEffect(ON_CREATE)`, so every rotation
    re-runs both one-shot loads without cancelling the previous ones. Filed as [#27](https://github.com/pantstamp/ai-dev-pipeline/issues/27); see Implementation Notes.
  - `MovieDetailsUiState.errorMessage: String` carries user-facing text from the ViewModel, and
    `MovieRepositoryImpl.getMovie` reports a missing row as `Error(DomainError.NotFound())`.
    Both contradict CLAUDE.md, and both are out of scope here.
  - `LibraryFeaturePlugin` still adds `:core:dispatcher:api` to every feature module. After
    Phase 1, `:feature:movie-catalog` no longer imports from it. This is harmless, and it is a
    convention-plugin change that belongs in its own PR.

---

## Phase 1: `ObserveEffects` owns its dispatcher; screens stop calling Koin

These changes must land together. Removing the parameter breaks every caller until the
callers are updated.

### Files to modify

- `core/presentation/mvi/src/main/kotlin/com/pantelisstampoulis/androidtemplateproject/presentation/mvi/MviUiHelpers.kt`
  - New signature:
    ```kotlin
    @Composable
    fun <T> ObserveEffects(
        effect: Flow<T>,
        lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
        onEffect: (T) -> Unit,
    )
    ```
  - Body unchanged except `withContext(Dispatchers.Main.immediate) { effect.collect(onEffect) }`.
  - Imports: add `androidx.lifecycle.compose.LocalLifecycleOwner` and
    `kotlinx.coroutines.Dispatchers`, and remove `kotlin.coroutines.CoroutineContext`. Both
    new imports are already on the module's classpath, through the Compose convention
    plugin's `compose-common` bundle and through `kotlinx-coroutines-core`.
  - KDoc: drop `@param coroutineContext` and document the default of `@param lifecycleOwner`.
    Add a paragraph explaining two things:
    - Effects are collected on `Dispatchers.Main.immediate`, so an effect is handled in the
      same frame it is sent, without re-dispatch.
    - Tests control this dispatcher through `Dispatchers.setMain`, so there is no need to
      inject it.
- `feature/movie-catalog/.../screen/movielist/MovieListScreen.kt`
  - Replace the call with `ObserveEffects(effect = effect) { sideEffect -> … }`. The handler
    body is unchanged.
  - Delete `MovieListPreviewKoin` together with its comment. Unwrap the three previews
    (`PreviewMovieListOfflineError`, `PreviewMovieListGenericError`, `PreviewMovieListEmpty`)
    so they call `MovieListScreen(…)` directly.
  - Remove imports that are now unused: `getKoin`, `KoinApplicationPreview`, `named`,
    `module`, `CoroutinesDispatchers`, `CoroutineContext`, `Dispatchers`, and
    `LocalLifecycleOwner`. Check each one; ktlint fails on unused imports.
- `feature/movie-catalog/.../screen/watchedmovielist/WatchedMovieListScreen.kt`
  - Same call change. Remove `getKoin`, `named`, `CoroutinesDispatchers`, `CoroutineContext`
    and `LocalLifecycleOwner` if unused.
- `feature/movie-catalog/.../screen/moviedetails/MovieDetailsScreen.kt`
  - Same call change. Remove the same imports if unused. Keep the imports that
    `LifecycleEventEffect` still needs (`Lifecycle`).

`feature/movie-catalog/...` abbreviates
`feature/movie-catalog/src/main/kotlin/com/pantelisstampoulis/androidtemplateproject/feature/moviecatalog/presentation`.

### Previews to add

None in this phase. The existing previews must now render without a wrapper:
- `PreviewMovieListOfflineError`, `PreviewMovieListGenericError`, `PreviewMovieListEmpty`
- `PreviewWatchedMovieRow`, `PreviewWatchedMovieListLoading`, `PreviewWatchedMovieListEmpty`,
  `PreviewWatchedMovieListWithData`
- `PreviewMovieCatalogTabbedScreen`
- `PreviewUserRatingBar` (unaffected, listed for completeness)

### Tests

No new tests. `ObserveEffects` has no callers in tests.

### Verification

```bash
./gradlew spotlessCheck :core:presentation:mvi:assembleDebug :feature:movie-catalog:testDebugUnitTest :test:konsist:test assembleDebug lint
grep -rn "getKoin\|KoinApplicationPreview" feature/movie-catalog/src   # expect no output
```

Manual checks:

- **Android Studio.** Open every preview listed above and confirm that each one renders.
- **Emulator** (SPEC US-4):
  - Discover: tap a movie. Details opens.
  - Discover: with movies shown, go offline and pull to refresh. The `RefreshFailed` toast
    appears.
  - Watched: tap a movie. Details opens.
  - Details: rate a movie. The "Rating saved" snackbar appears.
  - Details: rotate after rating. The snackbar does not appear again.
  - `RatingError` is accepted by inspection, because it shares the `ObserveEffects` call with
    `RatingSaved`.

---

## Phase 2: A preview for every missing screen state

### Files to modify

- `feature/movie-catalog/.../screen/movielist/MovieListScreen.kt`
  - Add a private sample below the existing previews:
    ```kotlin
    private val previewMovie = MovieUiModel(
        id = 1,
        adult = false,
        backdropPath = null,
        genreStringId = R.string.genre_science_fiction,
        originalLanguage = "en",
        originalTitle = "Interstellar",
        overview = "A team of explorers travel through a wormhole in space.",
        popularity = 100.0,
        posterPath = null,
        releaseYear = "2014",
        title = "Interstellar",
        video = false,
        voteAverage = 8.6,
        voteCount = 30000,
    )
    ```
- `feature/movie-catalog/.../screen/moviedetails/MovieDetailsScreen.kt`
  - Add the same `private val previewMovie`. The duplication is deliberate; see Decisions.
- `feature/movie-catalog/.../screen/watchedmovielist/WatchedMovieListScreen.kt`
  - Add the error preview listed below.

### Previews to add

All previews are `@Preview @Composable fun`, match the naming of their neighbours, pass
`effect = emptyFlow()`, and use no-op callbacks.

| Preview | File | `UiState` |
|---|---|---|
| `PreviewMovieListLoading` | `MovieListScreen.kt` | `MovieListUiState(isLoading = true)` |
| `PreviewMovieListWithData` | `MovieListScreen.kt` | `MovieListUiState(data = persistentListOf(previewMovie, previewMovie.copy(id = 2, title = "Dune")))` |
| `PreviewMovieListRefreshing` | `MovieListScreen.kt` | same `data`, `isRefreshing = true` |
| `PreviewWatchedMovieListError` | `WatchedMovieListScreen.kt` | `WatchedMovieListUiState(errorRes = R.string.error_generic)` |
| `PreviewMovieDetailsLoading` | `MovieDetailsScreen.kt` | `MovieDetailsUiState(isLoading = true)` |
| `PreviewMovieDetailsError` | `MovieDetailsScreen.kt` | `MovieDetailsUiState(errorMessage = "Movie not found")` |
| `PreviewMovieDetailsWithData` | `MovieDetailsScreen.kt` | `MovieDetailsUiState(data = previewMovie, userRating = 8)` |
| `PreviewMovieDetailsRatingInProgress` | `MovieDetailsScreen.kt` | `MovieDetailsUiState(data = previewMovie, isRatingInProgress = true)` |

The `MovieDetailsScreen` previews also pass `movieId = 1`. Their `LifecycleEventEffect(ON_CREATE)`
sends `Init` to the no-op `onEvent`, which is harmless.

The literal string in `PreviewMovieDetailsError` mirrors the current `String` state type (see
Notes). No new resources are needed; `error_generic` and `genre_science_fiction` already
exist.

### Tests

None; previews are verified manually.

### Verification

```bash
./gradlew spotlessCheck :feature:movie-catalog:testDebugUnitTest assembleDebug lint
```

Open each new preview in Android Studio. Confirm it renders and shows the intended state:
- a spinner for the loading previews
- the error text for the error previews
- two rows for the list previews
- the refresh indicator for the refreshing preview
- the rating bar in its in-progress state

---

## Phase 3: Konsist guard against Compose-side Koin, and the CLAUDE.md rule

### Files to modify

- `test/konsist/src/test/kotlin/com/pantelisstampoulis/androidtemplateproject/PresentationLayerKonsistTest.kt`
  - Add a test:
    ```kotlin
    @Test
    fun `Feature presentation files should NOT import Koin's Compose APIs`() {
        Konsist
            .scopeFromProduction()
            .files
            .withPackage(FeaturePresentationPackage)
            .assertFalse { file ->
                file.imports.any { import ->
                    KoinComposePackages.any { prefix -> import.name.startsWith(prefix) }
                }
            }
    }
    ```
  - Add to the companion object, which stays the last declaration:
    ```kotlin
    private const val FeaturePresentationPackage = "..feature..presentation.."
    private val KoinComposePackages = listOf("org.koin.compose", "org.koin.androidx.compose")
    ```
  - Add a class-level or test-level KDoc in the style of `DaoKonsistTest`. It should say that
    screens take state, effects and callbacks only; that a Compose-side Koin lookup throws in
    every preview because a preview never starts Koin; that CI compiles previews but never
    renders them; and link issue #25.
  - Adjust the Konsist API names (`files`, `withPackage`, `imports`) to Konsist 0.17.3 if they
    differ. The intent is: every file whose package matches `..feature..presentation..` has no
    import starting with either prefix.
- `CLAUDE.md`, in the Compose section. Replace the sentences from "Screens that call
  `getKoin()` throw in a preview…" to the end of that bullet with:
  > Previews render only because screens take state, effects and callbacks and nothing else.
  > No Koin lookups in presentation composables: `getKoin()`, `koinInject()` and
  > `KoinApplicationPreview` throw or mask the problem in a preview, which never starts Koin.
  > Resolve dependencies at the navigation layer (`koinViewModel()` in `*Navigation.kt`).
  > Konsist-enforced (`PresentationLayerKonsistTest`).

### Tests

- The new Konsist test above. It must pass on this branch.

To prove the rule can fail:
1. Temporarily add `import org.koin.compose.getKoin` to `WatchedMovieListScreen.kt`.
2. Run the Konsist tests and confirm the new test fails.
3. Revert the import. Do not commit it.

### Verification

```bash
./gradlew spotlessCheck :test:konsist:test test
```

---

## Implementation Notes

- **Konsist and empty scopes.** If `withPackage("..feature..presentation..")` matched no files,
  the assertion would not be meaningful. The red check in Phase 3 guards against this, so do
  not skip it.
- **`AppNavigation.kt` in `:app`** calls `getKoin()` and `koinInject()`. It is outside the rule's
  package, so it is untouched. That is intended; see SPEC Out of Scope.
- **Instrumented tests.** No `androidTest` source references `ObserveEffects` or the screens'
  signatures, so no `compileDebugAndroidTestKotlin` check is needed.
- **Details `Init` on rotation** (the note above, recorded for triage only). `Init` runs from
  `LifecycleEventEffect(ON_CREATE)`, which fires again on every rotation. Each run launches a
  fresh `getMovieUseCase` collector and a fresh `getWatchedMovieUseCase` collector without
  cancelling earlier ones. Both are one-shot reads that complete, so nothing leaks today. The
  costs are:
  - redundant reads and an `isLoading` flip on every rotation
  - a race in which the re-read of the watched movie can overwrite `userRating` with a stale
    value while a rating is in progress
  - a latent leak, since this is the exact CLAUDE.md anti-pattern and becomes one as soon as
    either load turns into an observing `Flow`
