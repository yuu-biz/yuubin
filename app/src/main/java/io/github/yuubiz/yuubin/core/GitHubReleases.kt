package io.github.yuubiz.yuubin.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/** Reads `releases/latest` of a catalog app's repository, unauthenticated. */
class GitHubReleases(
    private val client: OkHttpClient,
    private val apiBase: HttpUrl = "https://api.github.com/".toHttpUrl(),
) {
    suspend fun latest(app: CatalogApp): ReleaseLookup = withContext(Dispatchers.IO) {
        val url = apiBase.newBuilder()
            .addPathSegment("repos")
            .addPathSegment(app.repo.owner)
            .addPathSegment(app.repo.name)
            .addPathSegments("releases/latest")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Yuubin")
            .build()
        try {
            client.newCall(request).execute().use { interpret(it, app.apkAsset) }
        } catch (_: IOException) {
            ReleaseLookup.Unavailable(LookupFailure.NETWORK)
        }
    }

    private fun interpret(response: Response, apkAsset: Regex): ReleaseLookup = when {
        response.isSuccessful -> parse(response.body.string(), apkAsset)
        response.code == 404 -> ReleaseLookup.Unavailable(LookupFailure.NOT_FOUND)
        response.code in RATE_LIMIT_CODES &&
            (response.header("x-ratelimit-remaining") == "0" || response.header("retry-after") != null) ->
            ReleaseLookup.Unavailable(LookupFailure.RATE_LIMITED)
        // Other errors (5xx, a 403 without rate-limit headers) are treated like an unreachable server: retrying later may help.
        else -> ReleaseLookup.Unavailable(LookupFailure.NETWORK)
    }

    private fun parse(body: String, apkAsset: Regex): ReleaseLookup {
        val release = try {
            JSON.decodeFromString<ReleaseJson>(body)
        } catch (_: IllegalArgumentException) {
            return ReleaseLookup.Unavailable(LookupFailure.MALFORMED)
        }
        val candidates = release.assets.filter { apkAsset.matches(it.name) }
        val asset = candidates.singleOrNull()
            ?: return ReleaseLookup.Unavailable(
                if (candidates.isEmpty()) LookupFailure.NO_APK_ASSET else LookupFailure.AMBIGUOUS_APK_ASSET,
            )
        val sha256 = asset.digest?.takeIf { it.startsWith("sha256:") }?.let(Sha256::parse)
            ?: return ReleaseLookup.Unavailable(LookupFailure.NO_DIGEST)
        if (asset.size <= 0) return ReleaseLookup.Unavailable(LookupFailure.MALFORMED)
        return ReleaseLookup.Found(
            Release(
                tag = release.tagName,
                version = Version.parse(release.tagName),
                apk = ApkAsset(asset.name, asset.browserDownloadUrl, asset.size, sha256),
                notes = release.body.orEmpty(),
                pageUrl = release.htmlUrl,
            ),
        )
    }

    @Serializable
    private class ReleaseJson(
        @SerialName("tag_name") val tagName: String,
        val body: String? = null,
        @SerialName("html_url") val htmlUrl: String,
        val assets: List<AssetJson>,
    )

    @Serializable
    private class AssetJson(
        val name: String,
        @SerialName("browser_download_url") val browserDownloadUrl: String,
        val size: Long,
        val digest: String? = null,
    )

    private companion object {
        val RATE_LIMIT_CODES = setOf(403, 429)
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
