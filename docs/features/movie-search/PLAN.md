# Implementation Plan: Movie Search

## Overview

Adds a title search to the Discover tab. The query is typed into a Compose `TextFieldState` owned by the
UI. A new `MovieSearchViewModel` receives each change, and after a 300 ms pause on a query of at least two
trimmed characters, runs a network-only TMDB search. Results replace the Discover list in place. Nothing
from search is written to the database. Movie Details reads a movie from the local `movies` table and,
when it is missing, fetches it from TMDB without saving it. Details also gets typed offline/generic error
states with Retry, and `MovieRow` gets a poster placeholder shared by Discover and search.

Phases:
1. **TMDB search and movie-by-id network calls** — two endpoints, API models, fixtures.
2. **Search and Details fallback in data and domain** — `searchMovies`, `getMovie` network fallback,
   `SearchMoviesUseCase`.
3. **Movie Details error states with Retry** — shared `LoadError` and `StatusMessage`, typed Details
   errors.
4. **Poster placeholder in `MovieRow`**.
5. **Search ViewModel** — debounce, minimum length, stale-response handling, retry/refresh, effects.
6. **Search UI on Discover** — field, result states, back/keyboard handling, scroll positions, wiring.

## References
- Spec: `docs/features/movie-search/SPEC.md`
- Project conventions: `CLAUDE.md`

## Decisions

| # | Decision | Reason |
|---|---|---|
| D1 | The search field's text is owned by the UI (`rememberTextFieldState()`); a `snapshotFlow` sends each change to the ViewModel as `QueryChanged`. No `SavedStateHandle`. | Typing never round-trips through the MVI event loop (no cursor jumps / dropped characters with IMEs). `TextFieldState` is saveable, so process death restores the text and the new ViewModel re-runs the search (AC29) with no extra code. |
| D2 | A separate `MovieSearchViewModel`; `MovieListViewModel` is unchanged. | Search has its own state, timing and effects; the Discover ViewModel is already hardened and tested. |
| D3 | `GET movie/{id}` is modelled by a new `MovieDetailsApiModel` + `GenreApiModel`, not by `MovieApiModel`. | That endpoint returns `genres: [{id, name}]`, not `genre_ids`. Reusing `MovieApiModel` would silently give every fetched movie an unknown genre. |
| D4 | `MovieListError` becomes `LoadError` in a shared package; the private `MovieListStatus` becomes a shared `StatusMessage`. | Discover, search and Details all use them. |
| D5 | Movie Details: database first, then network fallback, never written (from the spec). | Keeps searched movies out of the Discover table (AC13, AC21). |

## Observations on existing code

### Deviations
- **Search results are mapped API → domain directly.** Discover goes API → `DbModel` → domain because it
  stores what it fetches. Search stores nothing, so routing it through `DbModel` would name a database type
  for data that never reaches the database. New mappers: `MovieSearchDomainMapper`,
  `MovieDetailsDomainMapper` (both `ApiToDomainMapper`). The poster-URL and blank-release-date rules move
  to shared helpers so the three API mappers cannot drift apart.
- **Movie Details stops rendering `DomainError.message`.** `MovieDetailsUiState.errorMessage: String?`
  becomes `error: LoadError?`, and the text is resolved in the composable. CLAUDE.md already forbids
  user-facing strings from the ViewModel; `ErrorClassifier` puts hardcoded English into that field.
- **Movie Details gets a `Retry(movieId)` event that reloads only the movie.** Re-sending `Init` would also
  relaunch the watched-rating collection.
- **Discover's side-effect collector moves up.** `MovieListScreen` currently runs `ObserveEffects` itself.
  In search mode `MovieListScreen` leaves composition, which would hold Discover's effects (navigation,
  refresh Toast) until the search is cleared. The new `DiscoverScreen` observes both ViewModels' effects,
  and `MovieListScreen` loses its `effect` and `onMovieClicked` parameters.
- **The query is not in the ViewModel's `UiState` as typed.** The ViewModel holds only the *trimmed,
  active* query (D1). This is the one piece of screen state not owned by a ViewModel, deliberately.

### Notes (not addressed here — for separate triage)
- `DomainModules` registers `GetMovieUseCase` and `RateMovieUseCase` with the logger tag
  `"GetMoviesUseCase"` (copy-paste). New registrations in this plan use their own names.
  → Separate `fix/network-logging-and-logger-tags` PR.
- `networkModule` installs `HttpLoggingInterceptor` at `Level.BODY` in every build type, so request headers
  — including the TMDB bearer token added by `HeaderInterceptor` — are logged in release builds
  (`HeaderInterceptor` runs before the logger). → Same separate fix PR.
- `MovieDao.getMovieEntities()` has no `ORDER BY`, so Discover's order is unspecified, and rows from
  earlier Discover fetches are never evicted. → [pantstamp/ai-dev-pipeline#30](https://github.com/pantstamp/ai-dev-pipeline/issues/30).

---

## Phase 1: TMDB search and movie-by-id network calls

Adds the two endpoints the rest of the feature needs, to `:core:network:*` only. Nothing calls them yet.

### Files to modify
- `core/network/retrofit/src/main/kotlin/.../network/RetrofitNetworkApi.kt`
  ```kotlin
  @GET(value = "search/movie")
  suspend fun searchMovies(
      @Query("query") query: String,
      @Query("include_adult") includeAdult: Boolean = false,
      @Query("page") page: Int = 1,
  ): NetworkResult<ApiDataResponse<List<MovieApiModel>>>

  @GET(value = "movie/{movie_id}")
  suspend fun getMovie(@Path("movie_id") movieId: Int): NetworkResult<MovieDetailsApiModel>
  ```
- `core/network/api/src/main/kotlin/.../network/NetworkDataSource.kt` — add
  `suspend fun searchMovies(query: String): NetworkResult<List<MovieApiModel>>` and
  `suspend fun getMovie(movieId: Int): NetworkResult<MovieDetailsApiModel>`.
- `core/network/retrofit/src/main/kotlin/.../network/RetrofitNetworkDataSource.kt` — implement both.
  `searchMovies` unwraps `results` exactly like `getMovies` (Success → `result.data.results`, Error and
  Exception passed through). `getMovie` returns the API result unchanged. `searchMovies` always passes
  `includeAdult = false` explicitly (AC9); do not rely on the TMDB default.
- `core/network/noop/src/main/kotlin/.../network/NoopNetworkDataSource.kt` — `searchMovies` returns
  `NetworkResult.Success(emptyList())`; `getMovie` returns `NetworkResult.Error(code = 404, message = null)`.

### Files to create
- `core/network/api/src/main/kotlin/.../network/model/GenreApiModel.kt`
  ```kotlin
  @Serializable
  data class GenreApiModel(
      @SerialName("id") val id: Int,
      @SerialName("name") val name: String,
  )
  ```
- `core/network/api/src/main/kotlin/.../network/model/MovieDetailsApiModel.kt` — same fields and defaults
  as `MovieApiModel`, except `genreIds` is replaced by
  `@SerialName("genres") val genres: List<GenreApiModel> = emptyList()`. Make `overview` default to `""`
  (TMDB returns `""` or omits it for obscure titles).
- `core/network/retrofit/src/test/resources/json/tmdb_search_movie_success.json` — a trimmed real
  `search/movie` response: 3 results, one with `poster_path: null`, one with `release_date: ""`.
- `core/network/retrofit/src/test/resources/json/tmdb_search_movie_empty.json` —
  `{"page":1,"results":[],"total_pages":0,"total_results":0}`.
- `core/network/retrofit/src/test/resources/json/tmdb_movie_details_success.json` — a trimmed real
  `movie/{id}` response with two `genres` entries and extra fields the model ignores
  (`budget`, `runtime`, `production_companies`).

### Tests
`core/network/retrofit/src/test/kotlin/.../network/RetrofitNetworkDataSourceTest.kt` (extend; follow the
existing backtick naming in this file):
- `test searchMovies success` — parses 3 results; the first id matches the fixture.
- `test searchMovies sends query, include_adult=false and page=1` — `mockWebServer.takeRequest()`; the
  path starts with `/search/movie`, and `requestUrl.queryParameter(...)` returns `"the matrix"` for a
  query with a space (proves encoding), `"false"` and `"1"`.
- `test searchMovies empty results` — `Success(emptyList())`.
- `test searchMovies http error` — 401 → `NetworkResult.Error(code = 401)`.
- `test searchMovies no connection` — `SocketPolicy.DISCONNECT_AT_START` → `NetworkResult.Exception`.
- `test getMovie success` — parses `genres` (2 entries, first id matches), ignores unknown fields.
- `test getMovie requests movie path` — `takeRequest().path` is `/movie/603`.
- `test getMovie not found` — 404 → `NetworkResult.Error(code = 404)`.

### Verification
```bash
./gradlew :core:network:retrofit:testDebugUnitTest :core:network:noop:assembleDebug :test:konsist:test spotlessCheck
```

---

## Phase 2: Search and Details fallback in data and domain

The repository gains a network-only search and a network fallback for `getMovie`. Neither writes to the
database. A new use case exposes search.

### Files to modify
- `core/domain/src/main/kotlin/.../domain/repository/MoviesRepository.kt`
  ```kotlin
  /**
   * Searches TMDB by title. Network only: results are never written to the database, so they
   * can never appear in the Discover list. An empty result is `Success(emptyList())`.
   */
  fun searchMovies(query: String): Flow<ResultState<List<Movie>>>
  ```
  Update the KDoc on `getMovie`: database first, network fallback, never written.
- `core/data/src/main/kotlin/.../data/repository/MoviesRepositoryImpl.kt`
  - `getMovie(movieId)`:
    ```kotlin
    override fun getMovie(movieId: Int): Flow<ResultState<Movie>> = flow {
        val movieDbModel = databaseDataSource.getMovie(movieId)
        if (movieDbModel != null) {
            emit(ResultState.Success(mappers.movieDomainMapper.fromDbToDomain(movieDbModel)))
        } else {
            emit(fetchMovieFromNetwork(movieId))
        }
    }
    ```
    `fetchMovieFromNetwork` is a private `suspend fun` returning `ResultState<Movie>`: on Success it maps
    with `mappers.movieDetailsDomainMapper`; otherwise it returns
    `ResultState.Error(mappers.errorClassifier.toDomainError(result) ?: DomainError.Unknown())`. It makes
    **no** `databaseDataSource` call.
  - `searchMovies(query)`:
    ```kotlin
    override fun searchMovies(query: String): Flow<ResultState<List<Movie>>> = flow {
        when (val result = networkDataSource.searchMovies(query)) {
            is NetworkResult.Success -> emit(
                ResultState.Success(
                    result.data
                        .distinctBy { it.id } // TMDB can repeat a movie; list keys must be unique
                        .map(mappers.movieSearchDomainMapper::fromApiToDomain),
                ),
            )
            is NetworkResult.Error, is NetworkResult.Exception -> emit(
                ResultState.Error(mappers.errorClassifier.toDomainError(result) ?: DomainError.Unknown()),
            )
        }
    }
    ```
    The repository does not trim or validate the query; that is the ViewModel's rule.
- `core/data/src/main/kotlin/.../data/mapper/Mappers.kt` — add
  `val movieSearchDomainMapper: MovieSearchDomainMapper` and
  `val movieDetailsDomainMapper: MovieDetailsDomainMapper`.
- `core/data/src/main/kotlin/.../data/di/DataModules.kt` — construct both in `mappersModule`.
- `core/data/src/main/kotlin/.../data/mapper/movie/MovieDataMapper.kt` — use the new shared helpers for
  `posterPath` and `releaseDate` (no behaviour change; existing `MovieDataMapperTest` must still pass).
- `core/domain/src/main/kotlin/.../domain/di/DomainModules.kt` — register
  `SearchMoviesUseCaseImpl` with the IO dispatcher and `getWith("SearchMoviesUseCase")`, bound to
  `SearchMoviesUseCase`.

### Files to create
- `core/data/src/main/kotlin/.../data/mapper/movie/TmdbFieldMappers.kt`
  ```kotlin
  /** TMDB returns a relative poster path; the app stores and displays the full URL. */
  internal fun String?.toPosterUrl(): String? = this?.let { IMAGE_URL + it }

  /** TMDB sends "" for an unknown release date. */
  internal fun String?.toReleaseDateOrNull(): String? = this?.takeIf { it.isNotBlank() }
  ```
- `core/data/src/main/kotlin/.../data/mapper/movie/MovieSearchDomainMapper.kt` —
  `internal class MovieSearchDomainMapper : ApiToDomainMapper<MovieApiModel, Movie>`. Same field rules as
  `MovieDataMapper`: `genreId = genreIds.firstOrNull()`, `posterPath.toPosterUrl()`,
  `releaseDate.toReleaseDateOrNull()`.
- `core/data/src/main/kotlin/.../data/mapper/movie/MovieDetailsDomainMapper.kt` —
  `internal class MovieDetailsDomainMapper : ApiToDomainMapper<MovieDetailsApiModel, Movie>`, with
  `genreId = genres.firstOrNull()?.id` and the same poster and date rules.
- `core/domain/src/main/kotlin/.../domain/usecase/movies/SearchMoviesUseCase.kt`
  ```kotlin
  interface SearchMoviesUseCase : UseCase<String, List<Movie>>

  internal class SearchMoviesUseCaseImpl(
      private val moviesRepository: MoviesRepository,
      private val coroutineContext: CoroutineContext,
      private val logger: Logger,
  ) : SearchMoviesUseCase {
      override operator fun invoke(input: String): Flow<ResultState<List<Movie>>> =
          moviesRepository.searchMovies(query = input)
              .onStartCatch(coroutineContext = coroutineContext, logger = logger)
  }
  ```
- `test/doubles/network/.../NetworkTestDoubleFactory.kt` — add `provideMovieDetailsApiModel()` (with
  `genres = listOf(GenreApiModel(randomInt(1, 100), randomString()))`) and `provideGenreApiModel()`.

### Tests
- `core/data/src/test/kotlin/.../data/repository/MoviesRepositoryImplTest.kt`
  - **Replace** the existing "movie not in database → NotFound" test (it asserts behaviour this phase
    removes) with `shouldFetchMovieFromNetworkWhenNotInDatabase` — DB returns `null`; network returns a
    details model; emits `Success` mapped by `movieDetailsDomainMapper`; `networkDataSource.getMovie`
    invoked once.
  - `shouldNotFetchFromNetworkWhenMovieIsInDatabase` — DB hit; `networkDataSource.getMovie`
    `wasNotInvoked()` (AC11).
  - `shouldNotWriteToDatabaseWhenMovieFetchedFromNetwork` — after success, `insertMovies` and
    `insertWatchedMovie` `wasNotInvoked()` (AC13).
  - `shouldEmitNoNetworkErrorWhenMovieFetchFailsOffline` — `NetworkResult.Exception(UnknownHostException())`
    → `Error(NoNetworkConnection)`; nothing written.
  - `shouldEmitNotFoundWhenNetworkReturns404ForMovie` — `Error(404)` → `Error(NotFound)`; nothing written.
  - `shouldEmitSearchResultsInNetworkOrder` — 3 API models → 3 domain movies, same order, poster URLs
    prefixed.
  - `shouldDropDuplicateIdsFromSearchResultsKeepingFirst` — ids `[1, 2, 1]` → `[1, 2]`, and the kept `1`
    is the first one's title.
  - `shouldEmitEmptySuccessWhenSearchHasNoResults` — `Success(emptyList())`, not an error.
  - `shouldEmitNoNetworkErrorWhenSearchFailsOffline` and `shouldEmitServerErrorWhenSearchReturns500`.
  - `shouldNeverTouchDatabaseWhenSearching` — for both a success and a failure, `databaseDataSource` has
    no `getMovies`, `getMovie` or `insertMovies` invocations (AC21).
- `core/data/src/test/kotlin/.../data/mapper/movie/MovieSearchDomainMapperTest.kt` — full mapping;
  `null` poster → `null`; `""` release date → `null`; empty `genreIds` → `null` genre.
- `core/data/src/test/kotlin/.../data/mapper/movie/MovieDetailsDomainMapperTest.kt` — first genre id
  used; empty `genres` → `null`; poster and date rules as above.
- `core/domain/src/test/kotlin/.../domain/usecase/movies/SearchMoviesUseCaseImplTest.kt` — follow
  `GetWatchedMovieUseCaseImplTest` (inline no-op `Logger`, `UnconfinedTestDispatcher`):
  Loading then Success; Loading then Error; a repository flow that throws → Loading then
  `Error(Unknown)`; the query is passed to the repository unchanged.

### Verification
```bash
./gradlew :core:data:testDebugUnitTest :core:domain:testDebugUnitTest :test:konsist:test assembleDebug spotlessCheck
```

---

## Phase 3: Movie Details error states with Retry

Details shows the same offline/generic states as Discover, with a Retry button, for every movie. Together
with Phase 2, a search result that was never in Discover now opens (AC10, AC15, AC16).

### Files to modify
- Move `feature/movie-catalog/.../presentation/screen/movielist/MovieListError.kt` to
  `feature/movie-catalog/.../presentation/error/LoadError.kt`:
  ```kotlin
  enum class LoadError { Offline, Generic }

  internal fun DomainError.toLoadError(): LoadError = when (this) {
      is DomainError.NoNetworkConnection -> LoadError.Offline
      else -> LoadError.Generic
  }
  ```
  Update `MovieListViewModel`, `MovieListUiState`, `MovieListSideEffect.RefreshFailed`,
  `MovieListScreen` and `MovieListViewModelTest` to the new name. No behaviour change in Discover.
- `feature/movie-catalog/src/main/res/values/strings.xml`
  - Rename `movie_list_error_offline` → `error_offline` (it is no longer Discover-specific).
  - Add `<string name="movie_details_error_generic">Couldn\'t load this movie. Please try again.</string>`.
- `feature/movie-catalog/.../presentation/screen/movielist/MovieListScreen.kt` — the private
  `MovieListStatus` keeps only the `PullToRefreshBox` + scrollable `LazyColumn` wrapper and puts
  `StatusMessage` inside it. `LoadError.iconRes()` moves next to `StatusMessage`; `messageRes()` stays
  per screen because the generic text differs.
- `feature/movie-catalog/.../presentation/screen/moviedetails/MovieDetailsViewModel.kt`
  - `MovieDetailsUiState.errorMessage: String?` → `error: LoadError? = null`.
  - Extract the `getMovieUseCase` collection from `Init` into `private fun loadMovie(movieId: Int)`, which
    cancels a previous `movieLoadJob` before launching (declare the `Job?` property before any `init`).
    `onLoading` sets `isLoading = true, error = null`; `onSuccess` sets `error = null`; `onError` sets
    `error = domainError.toLoadError()`. `isLoading` is reset on both exit paths.
  - `Init` calls `loadMovie(event.movieId)` and launches the watched-rating collection as before.
  - `Retry` calls `loadMovie(event.movieId)` only.
- `feature/movie-catalog/.../presentation/screen/moviedetails/MovieDetailsEvent.kt` — add
  `data class Retry(val movieId: Int) : MovieDetailsEvent`.
- `feature/movie-catalog/.../presentation/screen/moviedetails/MovieDetailsScreen.kt` — the error branch
  renders `StatusMessage(message = stringResource(error.detailsMessageRes()), iconRes = error.iconRes(),
  onRetry = { onEvent(MovieDetailsEvent.Retry(movieId)) })`, where `detailsMessageRes()` maps
  `Offline → R.string.error_offline`, `Generic → R.string.movie_details_error_generic`. Replace the
  `PreviewMovieDetailsError` preview (see below).

### Files to create
- `feature/movie-catalog/.../presentation/uicomponent/StatusMessage.kt`
  ```kotlin
  @Composable
  fun StatusMessage(
      message: String,
      @DrawableRes iconRes: Int?,
      onRetry: (() -> Unit)?,
      modifier: Modifier = Modifier,
  )
  ```
  A centred column: optional 48 dp icon (`contentDescription = null`), the message text with
  `liveRegion = LiveRegionMode.Polite`, and a Retry `Button` only when `onRetry != null`. This is the
  body of today's `MovieListStatus`, lifted unchanged apart from the nullable retry. Also defines
  `@DrawableRes internal fun LoadError.iconRes()`.

### Previews to add
In `MovieDetailsScreen.kt`, replacing `PreviewMovieDetailsError`:
- `PreviewMovieDetailsOfflineError` — `MovieDetailsUiState(error = LoadError.Offline)`
- `PreviewMovieDetailsGenericError` — `MovieDetailsUiState(error = LoadError.Generic)`

The existing Discover offline/generic previews must still render identically; open them in Android Studio.

### Tests
- `feature/movie-catalog/src/test/.../moviedetails/MovieDetailsViewModelTest.kt`
  - Replace `shouldEmitErrorStateWhenFetchingMovieDetailsFails` with
    `shouldShowOfflineErrorWhenMovieLoadFailsWithNoNetwork` (`error == LoadError.Offline`,
    `isLoading == false`) and `shouldShowGenericErrorWhenMovieLoadFailsWithNotFound`
    (`DomainError.NotFound` → `LoadError.Generic`).
  - `shouldClearErrorAndShowMovieWhenRetrySucceeds` — first call errors, second succeeds; after `Retry`,
    `error == null` and `data` is set.
  - `shouldReloadOnlyTheMovieOnRetry` — `getWatchedMovieUseCase` invoked exactly once across `Init` then
    `Retry`; `getMovieUseCase` invoked twice.
  - `shouldShowLoadingWithoutErrorWhileRetrying` — during the retry's `Loading`, `isLoading == true` and
    `error == null`.
  - Update `shouldEmitSuccessStateWhenFetchingMovieDetails` to assert `error == null`.
- `MovieListViewModelTest` — rename only (`LoadError`); all existing tests must pass unchanged in intent.

### Verification
```bash
./gradlew :feature:movie-catalog:testDebugUnitTest :test:konsist:test lint assembleDebug spotlessCheck
```

---

## Phase 4: Poster placeholder in `MovieRow`

Every movie row has a fixed-size poster area, showing a film icon when the poster is missing or fails to
load. Discover gets it immediately; search reuses it in Phase 6.

### Files to modify
- `feature/movie-catalog/.../presentation/screen/movielist/MovieListScreen.kt` — in `MovieRow`, replace
  the bare `AsyncImage` with a private `MoviePoster(posterUrl)`: a `Box` sized
  `fillMaxHeight().aspectRatio(2f / 3f)` with a `surfaceVariant` background, holding a centred 40 dp
  `Icon(ic_movie_placeholder)` tinted `onSurfaceVariant`, with the `AsyncImage` (`ContentScale.Crop`,
  `matchParentSize()`) layered on top. While the poster loads, when it fails, or when there is none,
  `AsyncImage` draws nothing and the icon shows; a loaded poster is opaque and covers it. The poster
  area is always 100 × 150 dp.

  *Changed during implementation (approved):* the original plan used `AsyncImage`'s
  `placeholder`/`error`/`fallback` painters, with `SubcomposeAsyncImage` as a fallback. Painter slots are
  drawn with the image's `ContentScale.Crop`, so the icon would be stretched and cropped, and
  `colorFilter` would tint the real poster too. Coil advises against `SubcomposeAsyncImage` in lazy
  lists.

### Files to create
- `feature/movie-catalog/src/main/res/drawable/ic_movie_placeholder.xml` — Material Symbols "movie"
  vector, 24 dp, `android:fillColor="#FF000000"` (tinted at runtime).

### Previews to add
In `MovieListScreen.kt`:
- `PreviewMovieRowPosterPlaceholder` — `MovieRow(movie = previewMovie.copy(posterPath = null))`.
- Check that `PreviewMovieListWithData` now shows placeholders (its preview movies have `posterPath = null`).

### Tests
No logic changes. Visual check of the previews above, plus the app on a device with a real Discover list
(posters load; the area stays 100 × 150 dp while loading).

### Verification
```bash
./gradlew :feature:movie-catalog:testDebugUnitTest lint assembleDebug spotlessCheck
```

---

## Phase 5: Search ViewModel

All search behaviour except rendering, fully unit-tested with virtual time. Registered in Koin but not yet
shown.

### Files to create
- `feature/movie-catalog/.../presentation/screen/moviesearch/MovieSearchEvent.kt`
  ```kotlin
  sealed interface MovieSearchEvent : Event {
      /** The raw field text, sent on every change and again after rotation or process death. */
      data class QueryChanged(val text: String) : MovieSearchEvent
      data object Retry : MovieSearchEvent
      data object Refresh : MovieSearchEvent
      data class ShowMovieDetails(val movieId: Int) : MovieSearchEvent
  }
  ```
- `feature/movie-catalog/.../presentation/screen/moviesearch/MovieSearchSideEffect.kt`
  ```kotlin
  sealed interface MovieSearchSideEffect : SideEffect {
      data class NavigateToMovieDetails(val movieId: Int) : MovieSearchSideEffect
      data class RefreshFailed(val error: LoadError) : MovieSearchSideEffect
      /** Results for a different query than the ones on screen have arrived. */
      data class ScrollResultsToTop(val query: String) : MovieSearchSideEffect
  }
  ```
- `feature/movie-catalog/.../presentation/screen/moviesearch/MovieSearchViewModel.kt`

  ```kotlin
  class MovieSearchViewModel(
      private val searchMoviesUseCase: SearchMoviesUseCase,
      private val mapper: MovieUiMapper,
      private val debounceMillis: Long = SEARCH_DEBOUNCE_MILLIS,
  ) : MviViewModel<MovieSearchEvent, MovieSearchUiState, MovieSearchSideEffect>(
      initialState = MovieSearchUiState(),
  )

  data class MovieSearchUiState(
      /** Trimmed query that is in effect; "" means search is inactive and Discover is shown. */
      val activeQuery: String = "",
      /** Results on screen, and the query they belong to. Null until a search has succeeded. */
      val results: ImmutableList<MovieUiModel>? = null,
      val resultsQuery: String? = null,
      /** A search for [activeQuery] is waiting for the debounce or is running. */
      val isSearching: Boolean = false,
      val isRefreshing: Boolean = false,
      /** Set only when there are no results to show for [activeQuery]. */
      val error: LoadError? = null,
  ) : UiState {
      val isActive: Boolean get() = activeQuery.isNotEmpty()
  }
  ```
  The defaults describe a neutral state, as CLAUDE.md requires. `SEARCH_DEBOUNCE_MILLIS = 300L` and
  `MIN_QUERY_LENGTH = 2` go in a `companion object`, which must be the last declaration in the class.

  **Pipeline.** Subscribed once in `init {}`; the `Job` and `MutableStateFlow` properties are declared
  before `init`.
  ```kotlin
  private data class SearchRequest(val query: String, val kind: Kind) { enum class Kind { New, Retry, Refresh } }

  private val activeQueries = MutableStateFlow("")        // trimmed; "" when < MIN_QUERY_LENGTH
  private val reruns = MutableSharedFlow<SearchRequest>(extraBufferCapacity = 1)

  init {
      viewModelScope.launch {
          merge(
              activeQueries
                  .debounce { if (it.isEmpty()) 0L else debounceMillis }
                  .map { SearchRequest(it, Kind.New) },
              reruns,
          )
              .flatMapLatest { request ->
                  if (request.query.isEmpty()) emptyFlow() else search(request)
              }
              .collect { (request, result) -> reduce(request, result) }
      }
  }
  ```
  - `flatMapLatest` cancels the previous search whenever a newer request arrives, which drops stale
    responses (AC4). Clearing the query sends `""` with no debounce, which cancels any in-flight search.
  - `search(request)` returns
    `searchMoviesUseCase(request.query).map { request to it }`. The pairing tags every result with the
    request that produced it, so `reduce` never has to guess.
  - A repeated `QueryChanged` with the same trimmed text is a no-op because the handler returns early
    when it equals the current active query (AC27–AC28).

  *Changed during implementation:* `activeQueries` holds `ActiveQuery(text, generation)` rather than a
  bare `String`, and every real change bumps `generation`. A `StateFlow` collector skips a value equal
  to the last one it saw, so "matrix" → "" → "matrix" handled before the pipeline resumes would never
  search again, leaving the spinner up. This is covered by
  `shouldSearchAgainWhenClearedAndRetypedBeforeThePipelineRuns`. Two further additions: `reduce` ignores
  a result whose query is no longer `activeQuery`, and `QueryChanged` also clears `error`, so a pending
  new query shows the spinner rather than the previous query's error
  (`shouldShowSpinnerRatherThanPreviousErrorWhileNextQueryIsPending`).

  **`QueryChanged(text)`** — synchronous state update, then feed the pipeline:
  ```kotlin
  val trimmed = event.text.trim()
  val active = if (trimmed.length >= MIN_QUERY_LENGTH) trimmed else ""
  if (active == activeQueries.value) return
  activeQueries.value = active
  setState {
      if (active.isEmpty()) {
          MovieSearchUiState() // back to Discover immediately; forget results (AC17, AC18)
      } else {
          // F21: previous results stay on screen while the new search is pending.
          copy(activeQuery = active, isSearching = true, isRefreshing = false)
      }
  }
  ```
  `activeQuery` and `isSearching` are set here, before the debounce, so search mode and the loading
  indicator appear on the same frame as the keystroke. Otherwise Discover would show through for 300 ms.

  **`Retry`** — no-op when inactive; otherwise `setState { copy(isSearching = true, error = null) }` and
  `reruns.tryEmit(SearchRequest(activeQuery, Kind.Retry))`. No debounce.

  **`Refresh`** — no-op when inactive. With results on screen:
  `setState { copy(isRefreshing = true) }` and emit `Kind.Refresh`. With no results on screen (error or
  still pending), it behaves like `Retry`.

  **`ShowMovieDetails`** → `setEffect { NavigateToMovieDetails(id) }`.

  **`reduce(request, result)`**:
  - `Loading` → no state change (`isSearching` / `isRefreshing` were set by the event).
  - `Success(movies)` →
    `results = movies.map(mapper::fromDomainToUi).toImmutableList()`, `resultsQuery = request.query`,
    `isSearching = false`, `isRefreshing = false`, `error = null`. If the previous `resultsQuery` was
    different from `request.query`, `setEffect { ScrollResultsToTop(request.query) }` (AC8). A Retry or
    Refresh of the same query does not scroll.
  - `Error(domainError)` →
    - `Kind.Refresh` with results for this query on screen: keep `results`, set
      `isRefreshing = false`, `setEffect { RefreshFailed(domainError.toLoadError()) }` (AC25).
    - otherwise: `results = null`, `resultsQuery = null`, `error = domainError.toLoadError()`,
      `isSearching = false`, `isRefreshing = false` (AC22–AC24).

  Every exit path resets both `isSearching` and `isRefreshing`. A cancelled search needs no reset: the
  request that cancelled it set the flags itself.

### Files to modify
- `feature/movie-catalog/.../presentation/di/FeatureMovieCatalogPresentationModule.kt` —
  `viewModel { MovieSearchViewModel(searchMoviesUseCase = get(), mapper = get()) }`.

### Tests
`feature/movie-catalog/src/test/.../moviesearch/MovieSearchViewModelTest.kt`. Follow `MovieListViewModelTest`:
`StandardTestDispatcher` set as Main, Koin module with the mocked `SearchMoviesUseCase` and a real
`MovieUiMapper`, `every {}` (not `coEvery`) for the use case. Use `advanceTimeBy` / `runCurrent` for the
debounce. Helper `delayedSuccess(movies, delayMs)` / `delayedError(...)` returning
`flow { emit(Loading); delay(ms); emit(...) }`.

Activation and debounce:
- `shouldStartInactiveAndNotSearch` — initial state is the default; the use case is never invoked.
- `shouldStayInactiveForOneCharacter` — `QueryChanged("m")`; after 1 s no invocation; `isActive` false (AC2).
- `shouldStayInactiveForOneCharacterSurroundedBySpaces` — `QueryChanged("  m ")` (AC2).
- `shouldActivateImmediatelyButSearchOnlyAfterDebounce` — `QueryChanged("ma")`: immediately
  `activeQuery == "ma"`, `isSearching`; at +299 ms not invoked; at +300 ms invoked once with `"ma"` (AC1).
- `shouldSendTrimmedQuery` — `QueryChanged("  matrix ")` → invoked with `"matrix"`.
- `shouldSearchOnceForFastTyping` — `"ma"`, `"mat"`, `"matr"`, `"matri"`, `"matrix"` 100 ms apart →
  exactly one invocation, with `"matrix"` (AC3).
- `shouldNotSearchAgainWhenOnlySurroundingSpacesChange` — after results for `"matrix"`,
  `QueryChanged(" matrix ")` → still one invocation (AC5).
- `shouldNotSearchAgainWhenSameTextIsResent` — after results, `QueryChanged("matrix")` again (simulates
  rotation / tab return re-emission) → one invocation, state unchanged (AC27, AC28).

Results and ordering:
- `shouldShowResultsForQuery` — `results` mapped via `MovieUiMapper`, in order; `resultsQuery == "matrix"`;
  `isSearching` false.
- `shouldShowEmptyResultsAsSuccess` — `results` is an empty list (not null), `error == null` (AC6).
- `shouldDiscardResultsOfSupersededQuery` — `"mat"` returns after 1 000 ms, `"matrix"` after 10 ms; after
  both deadlines only the `"matrix"` results are in state, and `"mat"` results never appear (AC4).
- `shouldKeepPreviousResultsWhileNextQueryIsPending` — results for `"mat"` on screen; `QueryChanged("matrix")`
  → `results` still the `"mat"` list, `isSearching == true` (AC7).
- `shouldEmitScrollToTopWhenResultsForNewQueryArrive` — one `ScrollResultsToTop(query)` per new query (AC8).
- `shouldNotEmitScrollToTopWhenSameQueryIsRefreshed` and `…WhenSameQueryIsRetried`.

Clearing:
- `shouldReturnToInactiveImmediatelyWhenCleared` — with results on screen, `QueryChanged("")` → state
  equals the default immediately, before any time advances (AC17).
- `shouldReturnToInactiveWhenQueryDropsBelowMinimum` — `QueryChanged("m")` after results (AC18).
- `shouldCancelInFlightSearchWhenCleared` — a slow search is running; clear; after its deadline, state
  is still the default.
- `shouldSearchAgainWhenSameQueryIsTypedAfterClearing` — `"matrix"` → results → `""` → `"matrix"` → two
  invocations.

Errors, retry and refresh:
- `shouldShowOfflineErrorWhenSearchFailsWithNoNetwork` — `error == Offline`, `results == null` (AC22).
- `shouldShowGenericErrorWhenSearchFailsOtherwise` — `ServerError` → `Generic` (AC23).
- `shouldReplacePreviousResultsWithErrorWhenNewQueryFails` — `"mat"` results, `"matrix"` fails →
  `results == null`, `error` set, no `RefreshFailed` effect (AC24).
- `shouldRerunCurrentQueryOnRetryWithoutDebounce` — after an error, `Retry` → invoked again immediately
  (`runCurrent`, no time advance) with the same query; results replace the error (AC22).
- `shouldIgnoreRetryAndRefreshWhenInactive` — no invocations.
- `shouldRerunCurrentQueryOnRefresh` — with results, `Refresh` → `isRefreshing` true, then results
  updated and `isRefreshing` false (AC25).
- `shouldKeepResultsAndEmitRefreshFailedWhenRefreshFails` — results unchanged, `isRefreshing` false,
  `error == null`, effect `RefreshFailed(Offline)` (AC25).
- `shouldTreatRefreshWithoutResultsAsRetry` — from an error state, `Refresh` → error cleared,
  `isSearching` true, search runs.
- `shouldResetRefreshingWhenNewQueryInterruptsRefresh` — refresh in flight, `QueryChanged("other")` →
  `isRefreshing` false.

Navigation:
- `shouldEmitNavigateToMovieDetailsOnShowMovieDetails`.

### Verification
```bash
./gradlew :feature:movie-catalog:testDebugUnitTest :test:konsist:test assembleDebug spotlessCheck
```

---

## Phase 6: Search UI on Discover

The search field and every search state, rendered in the Discover tab. Includes back handling, keyboard
behaviour, per-list scroll positions and the navigation wiring. After this phase the feature is complete.

### Files to create
- `feature/movie-catalog/.../presentation/screen/discover/DiscoverScreen.kt`
  ```kotlin
  @Composable
  fun DiscoverScreen(
      searchFieldState: TextFieldState,
      movieListState: MovieListUiState,
      movieListEffect: Flow<MovieListSideEffect>,
      onMovieListEvent: (MovieListEvent) -> Unit,
      searchState: MovieSearchUiState,
      searchEffect: Flow<MovieSearchSideEffect>,
      onSearchEvent: (MovieSearchEvent) -> Unit,
      onMovieClicked: (Int) -> Unit,
      modifier: Modifier = Modifier,
  )
  ```
  - Layout: `Column { MovieSearchField(...); Box(Modifier.weight(1f)) { content } }`. The field is outside
    the lists, so it never scrolls (F1).
  - Content: `if (searchState.isActive) MovieSearchResults(...) else MovieListScreen(...)`.
  - **Scroll positions (AC14, AC20):** hoist
    `val discoverListState = rememberLazyListState()` and `val resultsListState = rememberLazyListState()`
    here. Both are saveable, so each list keeps its position when the other is shown, and across a trip to
    Details and back.
  - **Query → ViewModel (D1):**
    `LaunchedEffect(searchFieldState) { snapshotFlow { searchFieldState.text.toString() }.collect { onSearchEvent(QueryChanged(it)) } }`.
    This re-emits the current text after rotation and process death. The ViewModel ignores the repeat
    after rotation and re-searches after process death.
  - **Effects:** `ObserveEffects(movieListEffect)` (moved from `MovieListScreen`; same Toast and
    navigation handling) and `ObserveEffects(searchEffect)`:
    `NavigateToMovieDetails` → `onMovieClicked(id)`; `RefreshFailed` → the same Toast as Discover, using
    `LocalResources` captured in composition; `ScrollResultsToTop(query)` → scroll
    `resultsListState` to item 0 **only if** `query != resultsScrolledFor`, then set
    `resultsScrolledFor = query`, where `var resultsScrolledFor by rememberSaveable { mutableStateOf<String?>(null) }`.
    See the "Scroll after process death" implementation note.
  - **Keyboard on scroll (F7):** a private
    `@Composable fun HideKeyboardOnScroll(listState: LazyListState)` —
    `LaunchedEffect(listState) { snapshotFlow { listState.isScrollInProgress }.filter { it }.collect { focusManager.clearFocus() } }`.
    Applied to both list states.
- `feature/movie-catalog/.../presentation/screen/discover/MovieSearchField.kt`
  ```kotlin
  @Composable
  fun MovieSearchField(state: TextFieldState, modifier: Modifier = Modifier)
  ```
  - `OutlinedTextField(state = state, lineLimits = TextFieldLineLimits.SingleLine, placeholder = { Text(stringResource(R.string.search_movies_placeholder)) }, leadingIcon = search icon (contentDescription = null), trailingIcon = …, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), onKeyboardAction = { keyboardController?.hide() })`.
    `onKeyboardAction` only hides the keyboard and does not trigger an extra search (F6).
  - Trailing icon, shown only when `state.text.isNotEmpty()` (F4): an `IconButton` (48 dp touch target by
    default) with `contentDescription = stringResource(R.string.search_clear)` (F5), whose `onClick` is
    `state.clearText()`. Clearing flows to the ViewModel through the `snapshotFlow`.
  - The field is not focused on launch: do not request focus anywhere.
  - Padding: 16 dp horizontal, 8 dp vertical; `fillMaxWidth()`.
- `feature/movie-catalog/.../presentation/screen/moviesearch/MovieSearchResults.kt`
  ```kotlin
  @Composable
  fun MovieSearchResults(
      state: MovieSearchUiState,
      listState: LazyListState,
      onEvent: (MovieSearchEvent) -> Unit,
      modifier: Modifier = Modifier,
  )
  ```
  Branches, in this order:
  1. `state.results != null` →
     - Non-empty: `PullToRefreshLazyColumn(items = results, key = { it.id }, lazyListState = listState, isRefreshing = state.isRefreshing, onRefresh = { onEvent(Refresh) })`,
       with each item a `MovieRow` that sends `ShowMovieDetails`. When `state.isSearching`, a
       `LinearProgressIndicator` sits overlaid at the top of the list (F21).
     - Empty: `StatusMessage(message = stringResource(R.string.search_no_results, state.resultsQuery), iconRes = null, onRetry = null)`
       inside the same pull-to-refresh wrapper Discover uses. Also show the linear indicator when
       `isSearching`.
  2. `state.error != null` → `StatusMessage` with `error_offline` / `movie_list_error_generic`,
     `error.iconRes()`, `onRetry = { onEvent(Retry) }`, in the pull-to-refresh wrapper (pull → `Refresh`).
  3. Otherwise (pending, nothing to show) → centred `CircularProgressIndicator` (F20).

  To give search the same empty and error layout as Discover, lift `MovieListStatus`'s pull-to-refresh
  wrapper from `MovieListScreen` into a shared `internal fun PullToRefreshStatus(onRefresh, content)`
  in `presentation/uicomponent/`, and use it from both screens.

### Files to modify
- `feature/movie-catalog/.../presentation/screen/movielist/MovieListScreen.kt`
  - Signature becomes
    `MovieListScreen(state: MovieListUiState, onEvent: (MovieListEvent) -> Unit, lazyListState: LazyListState, modifier: Modifier = Modifier)`.
    Drop `effect` and `onMovieClicked`; `ObserveEffects` moves to `DiscoverScreen`.
  - Pass `lazyListState` through `MovieList` to `PullToRefreshLazyColumn`.
  - Update the existing previews: remove `effect` and `onMovieClicked`, and pass `rememberLazyListState()`.
- `feature/movie-catalog/.../presentation/screen/MovieCatalogTabbedScreen.kt`
  - New parameters: `searchState: MovieSearchUiState`, `searchEffect: Flow<MovieSearchSideEffect>`,
    `onSearchEvent: (MovieSearchEvent) -> Unit`.
  - `val searchFieldState = rememberTextFieldState()` lives **here**, not in the pager page, so that
    `BackHandler` can see it.
  - Page 0 renders `DiscoverScreen(...)`.
  - **Back (AC19):**
    `BackHandler(enabled = pagerState.currentPage == 0 && searchFieldState.text.isNotEmpty()) { searchFieldState.clearText() }`.
    The `currentPage` check is required: the pager keeps page 0 composed while Watched is shown
    (`beyondViewportPageCount = 1`), so a handler inside page 0 would intercept back on the Watched tab.
    An open keyboard takes the first back press itself, before this handler.
  - **Tab switch hides the keyboard (AC28):**
    `LaunchedEffect(pagerState.currentPage) { focusManager.clearFocus() }`.
  - Update `PreviewMovieCatalogTabbedScreen` with `searchState = MovieSearchUiState()`.
- `feature/movie-catalog/.../navigation/MovieCatalogNavigation.kt` — in `addMovieListScreen`, add
  `val movieSearchViewModel = koinViewModel<MovieSearchViewModel>()` and collect its `viewState` with
  `collectAsStateWithLifecycle()`. Pass `searchState`, `searchEffect = movieSearchViewModel.effect` and
  `onSearchEvent = movieSearchViewModel::setEvent`. All three ViewModels are scoped to the same
  back-stack entry, so they survive Details and back.
- `feature/movie-catalog/build.gradle.kts` — add `implementation(libs.androidx.activity.compose)` for
  `BackHandler`. It is already on the classpath through navigation-compose, but the module should declare
  what it imports.
- `feature/movie-catalog/src/main/res/values/strings.xml` — add:
  ```xml
  <!-- Discover search -->
  <string name="search_movies_placeholder">Search movies</string>
  <string name="search_clear">Clear search</string>
  <string name="search_no_results">No movies found for “%1$s”</string>
  ```

### Previews to add
In `DiscoverScreen.kt`, each built with `rememberTextFieldState("matrix")` (or `""` for the first), static
states and `emptyFlow()` effects:
- `PreviewDiscoverSearchInactive` — `MovieSearchUiState()` with Discover data; the field is empty.
- `PreviewDiscoverSearchPending` — `MovieSearchUiState(activeQuery = "matrix", isSearching = true)` (F20).
- `PreviewDiscoverSearchLoadingOverResults` — results for `"mat"`, `activeQuery = "matrix"`,
  `isSearching = true` (F21).
- `PreviewDiscoverSearchResults` — two results, one with `posterPath = null` (F22, placeholder).
- `PreviewDiscoverSearchNoResults` — `results = persistentListOf()`, `resultsQuery = "zzzzqqq"` (F23).
- `PreviewDiscoverSearchOffline` — `error = LoadError.Offline` (F24).
- `PreviewDiscoverSearchGenericError` — `error = LoadError.Generic` (F25).
- `PreviewDiscoverSearchRefreshing` — results with `isRefreshing = true`.

Open every one of them in Android Studio before calling the phase done (AC14 in the issue).

### Tests
- Konsist (`PresentationLayerKonsistTest`) must pass: no Koin Compose imports under
  `..feature..presentation..` (the ViewModel is resolved only in `MovieCatalogNavigation.kt`).
- No new unit tests: the logic is covered in Phase 5, and CI does not run UI tests. Run this manual check
  on a device or emulator, and record the result in the PR:
  1. Type "ma" → results after a pause (AC1); "m" → Discover (AC2).
  2. Scroll Discover halfway, search, tap ✕ → Discover at the same position, with no network request in
     Logcat (AC17, AC20).
  3. Scroll results, open one, press back → same results and position (AC14).
  4. Open a result that is not in Discover and rate it → it shows in Watched with poster and rating
     (AC10, AC12). Restart the app → it is not in Discover (AC21).
  5. Airplane mode: type a query → offline state; turn it off, tap Retry → results (AC22). Open a result
     that is not in Discover while offline → Details offline state, then Retry (AC15).
  6. Pull to refresh on results → search re-runs; in airplane mode → results stay, Toast shown (AC25).
  7. Rotate with results → same results, with no new request in Logcat (AC27). Switch to Watched and
     back → same, and the keyboard is hidden on the switch (AC28).
  8. Developer options → "Don't keep activities": search, press Home, return → the query is restored and
     the search runs again (AC29). Swipe the app away and relaunch → the field is empty (AC30).
  9. With a query and the keyboard closed, press back → search cleared; press back again → the app
     closes. On the Watched tab, with a query on Discover, back behaves as it does today (AC19).
  10. TalkBack: the field reads "Search movies", ✕ reads "Clear search", and the no-results and error
      messages are announced.

### Verification
```bash
./gradlew :feature:movie-catalog:testDebugUnitTest :test:konsist:test lint assembleDebug spotlessCheck
```

---

## Implementation Notes

- **Main dispatcher in tests.** `MovieSearchViewModel`'s pipeline runs on `viewModelScope`
  (`Dispatchers.Main`), so `debounce` uses the test scheduler's virtual time once `Dispatchers.setMain`
  receives a `StandardTestDispatcher`. Create the ViewModel inside `runTest` after `setMain`, as
  `MovieListViewModelTest` does. `debounce` and `flatMapLatest` need
  `@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)`.
- **`debounce` selector.** `debounce { if (it.isEmpty()) 0L else debounceMillis }` makes clearing
  instant. A fixed `debounce(300)` would keep the stale search alive for 300 ms after ✕, and its result
  could land after the user is back on Discover.
- **`reruns` buffer.** `MutableSharedFlow(extraBufferCapacity = 1)` with `tryEmit`: `Retry` and
  `Refresh` come from `handleEvents`, which is not suspending. Without the buffer, `tryEmit` drops the
  value whenever the collector is busy.
- **`Retry` right after the query changes.** `Retry` uses `activeQuery`, which `QueryChanged` sets
  synchronously, so it always re-runs the query the user sees.
- **Konsist naming.** `MovieSearchDomainMapper` and `MovieDetailsDomainMapper` must implement
  `ApiToDomainMapper`. `GenreApiModel` and `MovieDetailsApiModel` live in `..network.model`, are
  `@Serializable data class`es, and have `@SerialName` on every `val`.
- **Instrumented tests.** No constructor used by `androidTest` sources changes in this plan
  (`RoomDataSource` and the DAOs are untouched), so the `compileDebugAndroidTestKotlin` check in
  CLAUDE.md is not needed.
- **Scroll after process death.** A ViewModel recreated after process death has no `resultsQuery`, so
  it sees the restored query's results as new and emits `ScrollResultsToTop`. The spec says re-running
  the same query keeps the scroll position, including after a restore, and the restored
  `resultsListState` already holds that position. `resultsScrolledFor` is saveable, so it survives the
  same restore and suppresses that one scroll. During normal use the ViewModel's own check already
  prevents a scroll for Retry and Refresh; the UI check only matters after a restore.
- **String rename.** `movie_list_error_offline` → `error_offline` in Phase 3 touches only
  `MovieListScreen.kt`. Rename in the same commit, or lint fails on the missing or unused resource.
