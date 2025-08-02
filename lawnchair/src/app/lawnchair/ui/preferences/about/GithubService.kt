package app.lawnchair.ui.preferences.about

import app.lawnchair.util.kotlinxJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * Interface for interacting with the GitHub API.
 *
 * This interface defines methods for fetching data from GitHub, such as releases and repository events.
 * It uses Retrofit for making HTTP requests and kotlinx.serialization for JSON parsing.
 */
interface GitHubService {
    @GET("repos/LawnchairLauncher/lawnchair/releases")
    suspend fun getReleases(): List<GitHubRelease>

    @GET("repos/{owner}/{repo}/events")
    suspend fun getRepositoryEvents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): List<GitHubEvent>

    @Streaming
    @GET
    suspend fun downloadFile(@Url fileUrl: String): ResponseBody
}

/**
 * Represents a GitHub release.
 *
 * @property tagName The tag name of the release.
 * @property assets A list of assets associated with the release.
 */
@Serializable
data class GitHubRelease(
    @SerialName("tag_name")
    val tagName: String,
    val assets: List<GitHubAsset>,
) {
    /**
     * Represents an asset associated with a GitHub release.
     *
     * @property name The name of the asset.
     * @property browserDownloadUrl The URL to download the asset from a browser.
     */
    @Serializable
    data class GitHubAsset(
        val name: String,
        @SerialName("browser_download_url")
        val browserDownloadUrl: String,
    )
}

/**
 * Represents a GitHub event.
 *
 * @property type The type of the event (e.g., "PushEvent", "PullRequestEvent").
 * @property actor The actor who triggered the event.
 * @property createdAt The timestamp when the event was created.
 */
@Serializable
data class GitHubEvent(
    val type: String,
    val actor: Actor,
    @SerialName("created_at")
    val createdAt: String,
) {
    /**
     * Represents the actor (user) who triggered a GitHub event.
     *
     * @property login The username of the actor.
     */
    @Serializable
    data class Actor(
        val login: String,
    )
}

internal val gitHubApiRetrofit: Retrofit by lazy {
    Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(kotlinxJson.asConverterFactory("application/json".toMediaType()))
        .build()
}

private const val BASE_URL = "https://api.github.com/"
