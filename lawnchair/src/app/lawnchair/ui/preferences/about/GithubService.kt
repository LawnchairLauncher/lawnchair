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
import retrofit2.http.Query
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

    @GET("repos/{owner}/{repo}/commits")
    suspend fun getRepositoryCommits(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("sha") sha: String? = null,
    ): List<GitHubCommit>

    @GET("repos/{owner}/{repo}/compare/{base}...{head}")
    suspend fun compareCommits(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("base") base: String,
        @Path("head") head: String,
    ): GitHubCompareResponse

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
    @SerialName("target_commitish")
    val targetCommitish: String,
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

/**
 * Represents a GitHub commit.
 *
 * @property sha The SHA hash of the commit.
 * @property commit The commit details.
 * @property author The author of the commit.
 */
@Serializable
data class GitHubCommit(
    val sha: String,
    val commit: CommitDetails,
    val author: Author? = null,
) {
    @Serializable
    data class CommitDetails(
        val message: String,
        val author: CommitAuthor,
    )

    @Serializable
    data class CommitAuthor(
        val name: String,
        val date: String,
    )

    @Serializable
    data class Author(
        val login: String,
    )
}

/**
 * Represents a GitHub comparison response between two commits.
 *
 * @property commits List of commits between the base and head.
 * @property aheadBy Number of commits ahead.
 * @property behindBy Number of commits behind.
 */
@Serializable
data class GitHubCompareResponse(
    val commits: List<GitHubCommit>,
    @SerialName("ahead_by")
    val aheadBy: Int,
    @SerialName("behind_by")
    val behindBy: Int,
)

internal val gitHubApiRetrofit: Retrofit by lazy {
    Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(kotlinxJson.asConverterFactory("application/json".toMediaType()))
        .build()
}

private const val BASE_URL = "https://api.github.com/"
