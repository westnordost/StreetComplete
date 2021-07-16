package de.westnordost.streetcomplete.data.maptiles

import android.util.Log
import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.ktx.format
import de.westnordost.streetcomplete.map.VectorTileProvider
import de.westnordost.streetcomplete.util.enclosingTilesRect
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.internal.Version
import java.io.IOException
import java.lang.System.currentTimeMillis
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MapTilesDownloader @Inject constructor(
    private val vectorTileProvider: VectorTileProvider,
    private val cacheConfig: MapTilesDownloadCacheConfig
) {
    private val okHttpClient = OkHttpClient.Builder().cache(cacheConfig.cache).build()

    suspend fun download(bbox: BoundingBox) = withContext(Dispatchers.IO) {
        var tileCount = 0
        var failureCount = 0
        var downloadedSize = 0
        var cachedSize = 0
        val time = currentTimeMillis()

        downloadTilesFlow(bbox).collect { result ->
            ++tileCount
            when (result) {
                is DownloadFailure -> ++failureCount
                is DownloadSuccess -> {
                    if (result.alreadyCached) cachedSize += result.size
                    else downloadedSize += result.size
                }
            }
        }

        val seconds = (currentTimeMillis() - time) / 1000.0
        val failureText = if (failureCount > 0) ". $failureCount tiles failed to download" else ""
        Log.i(TAG, "Downloaded $tileCount tiles (${downloadedSize / 1000}kB downloaded, ${cachedSize / 1000}kB already cached) in ${seconds.format(1)}s$failureText")
    }

    // Android Studio incorrectly wants to annotate this with @ExperimentalCoroutinesApi.
    // The type of the block is ProducerScope<T>.() -> Unit, and we use ProducerScope.send().
    // ProducerScope is indeed experimental, but `send` is inherited from SendChannel, which isn't.
    private fun downloadTilesFlow(bbox: BoundingBox): Flow<DownloadResult> = channelFlow {
        /* tiles for the highest zoom (=likely current or near current zoom) first,
           because those are the tiles that are likely to be useful first */
        (vectorTileProvider.maxZoom downTo 0).forEach { zoom ->
            bbox.enclosingTilesRect(zoom).asTilePosSequence().forEach { (x, y) ->
                launch {
                    try {
                        val result = downloadTile(zoom, x, y)
                        val logText = if (result.alreadyCached) "in cache" else "downloaded"
                        Log.v(TAG, "Tile $zoom/$x/$y $logText")
                        send(result)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error retrieving tile $zoom/$x/$y: ${e.message}")
                        send(DownloadFailure)
                    }
                }
            }
        }
    }

    private suspend fun downloadTile(zoom: Int, x: Int, y: Int): DownloadSuccess = suspendCancellableCoroutine { cont ->
        /* adding trailing "&" because Tangram-ES also puts this at the end and the URL needs to be
           identical in order for the cache to work */
        val url = vectorTileProvider.getTileUrl(zoom, x, y) + "&"
        val httpUrl = HttpUrl.parse(url)
        require(httpUrl != null) { "Invalid URL: $url" }

        val request = Request.Builder()
            .url(httpUrl)
            .cacheControl(cacheConfig.cacheControl)
            .header("User-Agent", ApplicationConstants.USER_AGENT + " / " + Version.userAgent())
            .build()
        val call = okHttpClient.newCall(request)

        /* since we use coroutines and this is in the background anyway, why not use call.execute()?
        *  Because we want to let the OkHttp dispatcher control how many HTTP requests are made in
        *  parallel */
        val callback = object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                // just get the bytes and let the cache magic do the rest...
                val size = response.body()?.use { it.bytes().size } ?: 0
                val alreadyCached = response.cacheResponse() != null
                cont.resume(DownloadSuccess(alreadyCached, size))
            }
        }
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(callback)
    }

    companion object {
        private const val TAG = "MapTilesDownload"
    }
}

private sealed class DownloadResult
private data class DownloadSuccess(val alreadyCached: Boolean, val size: Int) : DownloadResult()
private object DownloadFailure : DownloadResult()
