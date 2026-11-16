package com.example.data

import com.example.model.SearchSuggestion
import com.yandex.mapkit.geometry.BoundingBox
import com.yandex.mapkit.geometry.Geometry
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.search.Response
import com.yandex.mapkit.search.SearchFactory
import com.yandex.mapkit.search.SearchManagerType
import com.yandex.mapkit.search.SearchOptions
import com.yandex.mapkit.search.SearchType
import com.yandex.mapkit.search.Session
import com.yandex.mapkit.search.SuggestOptions
import com.yandex.mapkit.search.SuggestResponse
import com.yandex.mapkit.search.SuggestSession
import com.yandex.mapkit.search.SuggestType
import com.yandex.runtime.Error
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class SearchRepository {

  private val searchManager by lazy {
    SearchFactory.getInstance().createSearchManager(SearchManagerType.COMBINED)
  }

  private val suggestSession: SuggestSession by lazy { searchManager.createSuggestSession() }

  suspend fun fetchSuggestions(
    query: String,
    userPoint: Point,
    boundingBox: BoundingBox = ALMATY_BOUNDING_BOX,
  ): Result<List<SearchSuggestion>> = suspendCancellableCoroutine { cont ->
    if (query.isBlank()) {
      cont.resume(Result.success(emptyList()))
      return@suspendCancellableCoroutine
    }

    val options =
      SuggestOptions()
        .setSuggestTypes(SuggestType.GEO.value or SuggestType.BIZ.value)
        .setUserPosition(userPoint)
        .setSuggestWords(true)

    suggestSession.suggest(
      query,
      boundingBox,
      options,
      object : SuggestSession.SuggestListener {
        override fun onResponse(response: SuggestResponse) {
          val mapped =
            response.items.take(8).mapIndexed { index, item ->
              SearchSuggestion(
                id = "${item.logId ?: index}_${item.title.text}",
                title = item.title.text,
                subtitle = item.subtitle?.text.orEmpty(),
                point = item.center,
                searchUri = item.uri,
                distanceText = item.distance?.text,
              )
            }
          if (cont.isActive) cont.resume(Result.success(mapped))
        }

        override fun onError(error: Error) {
          if (cont.isActive) cont.resume(Result.failure(RuntimeException("Ошибка поиска подсказок")))
        }
      },
    )

    cont.invokeOnCancellation { suggestSession.reset() }
  }

  suspend fun searchTopPlaces(
    query: String,
    boundingBox: BoundingBox = ALMATY_BOUNDING_BOX,
  ): Result<List<SearchSuggestion>> = suspendCancellableCoroutine { cont ->
    val options =
      SearchOptions()
        .setSearchTypes(SearchType.GEO.value or SearchType.BIZ.value)
        .setResultPageSize(6)

    val session =
      searchManager.submit(
        query,
        Geometry.fromBoundingBox(boundingBox),
        options,
        object : Session.SearchListener {
          override fun onSearchResponse(response: Response) {
            val items =
              response.collection.children.mapIndexedNotNull { index, child ->
                val obj = child.obj ?: return@mapIndexedNotNull null
                val point =
                  obj.geometry.firstOrNull()?.point ?: return@mapIndexedNotNull null
                val title = obj.name ?: query
                val subtitle = obj.descriptionText ?: "Алматы"
                SearchSuggestion(
                  id = "search_${index}_$title",
                  title = title,
                  subtitle = subtitle,
                  point = point,
                )
              }
            if (cont.isActive) cont.resume(Result.success(items))
          }

          override fun onSearchError(error: Error) {
            if (cont.isActive) {
              cont.resume(Result.failure(RuntimeException("Не удалось найти объект")))
            }
          }
        },
      )

    cont.invokeOnCancellation { session.cancel() }
  }

  suspend fun resolveSuggestionPoint(suggestion: SearchSuggestion): Point? {
    suggestion.point?.let { return it }
    val uri = suggestion.searchUri
    if (!uri.isNullOrBlank()) {
      return resolveByUri(uri)
    }
    return searchTopPlaces(suggestion.title).getOrNull()?.firstOrNull()?.point
  }

  private suspend fun resolveByUri(uri: String): Point? =
    suspendCancellableCoroutine { cont ->
      val session =
        searchManager.resolveURI(
          uri,
          SearchOptions(),
          object : Session.SearchListener {
            override fun onSearchResponse(response: Response) {
              val point =
                response.collection.children
                  .firstOrNull()
                  ?.obj
                  ?.geometry
                  ?.firstOrNull()
                  ?.point
              if (cont.isActive) cont.resume(point)
            }

            override fun onSearchError(error: Error) {
              if (cont.isActive) cont.resume(null)
            }
          },
        )
      cont.invokeOnCancellation { session.cancel() }
    }

  suspend fun reverseGeocode(point: Point): String =
    suspendCancellableCoroutine { cont ->
      val session =
        searchManager.submit(
          point,
          16,
          SearchOptions().setSearchTypes(SearchType.GEO.value),
          object : Session.SearchListener {
            override fun onSearchResponse(response: Response) {
              val name =
                response.collection.children.firstOrNull()?.obj?.name
                  ?: "Точка (${"%.4f".format(point.latitude)}, ${"%.4f".format(point.longitude)})"
              if (cont.isActive) cont.resume(name)
            }

            override fun onSearchError(error: Error) {
              if (cont.isActive) {
                cont.resume(
                  "Точка (${"%.4f".format(point.latitude)}, ${"%.4f".format(point.longitude)})"
                )
              }
            }
          },
        )
      cont.invokeOnCancellation { session.cancel() }
    }

  companion object {
    val ALMATY_BOUNDING_BOX =
      BoundingBox(
        Point(43.1300, 76.7600),
        Point(43.3500, 77.0600),
      )
  }
}
