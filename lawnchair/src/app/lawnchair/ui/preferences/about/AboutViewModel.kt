package app.lawnchair.ui.preferences.about

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lawnchair.SoraBranding
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.create

class AboutViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val api: GitHubService = gitHubApiRetrofit.create()
    private val prefs: PreferenceManager = PreferenceManager.getInstance(application)
    private val prefs2: PreferenceManager2 = PreferenceManager2.getInstance(application)

    private val nightlyBuildsRepository = NightlyBuildsRepository(
        applicationContext = application,
        api = api,
    )

    val uiState: StateFlow<AboutUiState>
        field = MutableStateFlow(AboutUiState())

    val updateState = nightlyBuildsRepository.updateState

    init {
        uiState.update {
            it.copy(
                versionName = if (prefs.hideVersionInfo.get()) {
                    prefs.pseudonymVersion.get() + " (pseudonym)"
                } else {
                    BuildConfig.VERSION_NAME
                },
                commitHash = BuildConfig.COMMIT_HASH,
                coreTeam = team,
                supportAndPr = supportAndPr,
                topLinks = topLinks,
                bottomLinks = bottomLinks,
            )
        }

        if (SoraBranding.ENABLE_CONTRIBUTORS) viewModelScope.launch(Dispatchers.Default) {
            val activeContributors = fetchActiveContributors()
            val updatedCoreTeam = uiState.value.coreTeam.map { member ->
                val status = if (member.githubUsername != null && activeContributors.contains(member.githubUsername.lowercase())) ContributorStatus.Active else ContributorStatus.Idle
                member.copy(status = status)
            }
            uiState.update { it.copy(coreTeam = updatedCoreTeam) }
        }

        // Check if the build variant is Nightly
        // AND check if user has enabled auto updater (available to Nightly variant)
        // OR check if user has overridden it in debug flags (available to All variant)
        if (SoraBranding.ENABLE_AUTO_UPDATER &&
            BuildConfig.APPLICATION_ID.contains("nightly") &&
            prefs2.autoUpdaterNightly.firstCached()
        ) {
            nightlyBuildsRepository.checkForUpdate()
            viewModelScope.launch {
                nightlyBuildsRepository.updateState.collect { state ->
                    uiState.update { it.copy(updateState = state) }
                }
            }
        }
    }

    fun downloadUpdate() {
        nightlyBuildsRepository.downloadUpdate()
    }

    fun installUpdate(file: File, forceInstall: Boolean = false) {
        nightlyBuildsRepository.installUpdate(file, forceInstall)
    }

    fun resetToDownloaded(file: File) {
        nightlyBuildsRepository.resetToDownloaded(file)
    }

    private suspend fun fetchActiveContributors(): Set<String> {
        return runCatching {
            nightlyBuildsRepository.api.getRepositoryEvents(SoraBranding.GITHUB_OWNER, SoraBranding.GITHUB_REPO)
                .map { it.actor.login.lowercase() }
                .toSet()
        }.getOrDefault(emptySet())
    }

    companion object {
        /*
         * Upstream Lawnchair populated these with its own team, community links
         * and donation pages. They are emptied out rather than re-pointed:
         * Sora Launcher has no equivalents yet, and shipping another project's
         * people and URLs would be misleading. The original lists are in git
         * history if they are ever needed for reference.
         *
         * To restore a section, add its entries here and flip the matching flag
         * in `SoraBranding`.
         */

        private val team = emptyList<TeamMember>()

        private val supportAndPr = emptyList<TeamMember>()

        private val topLinks = if (!SoraBranding.ENABLE_COMMUNITY_LINKS) {
            emptyList()
        } else {
            listOf(
                Link(
                    iconResId = R.drawable.ic_new_releases,
                    labelResId = R.string.news,
                    url = SoraBranding.NEWS_URL,
                ),
                Link(
                    iconResId = R.drawable.ic_help,
                    labelResId = R.string.support,
                    url = SoraBranding.SUPPORT_URL,
                ),
                Link(
                    iconResId = R.drawable.ic_github,
                    labelResId = R.string.github,
                    url = SoraBranding.githubUrl,
                ),
                Link(
                    iconResId = R.drawable.ic_translate,
                    labelResId = R.string.translate,
                    url = SoraBranding.TRANSLATE_URL,
                ),
                Link(
                    iconResId = R.drawable.ic_open_collective,
                    labelResId = R.string.donate,
                    url = SoraBranding.DONATE_URL,
                ),
            )
        }

        private val bottomLinks = if (!SoraBranding.ENABLE_COMMUNITY_LINKS) {
            emptyList()
        } else {
            listOf(
                Link(
                    iconResId = R.drawable.ic_telegram,
                    labelResId = R.string.telegram,
                    url = SoraBranding.TELEGRAM_URL,
                ),
                Link(
                    iconResId = R.drawable.ic_discord,
                    labelResId = R.string.discord,
                    url = SoraBranding.DISCORD_URL,
                ),
                Link(
                    iconResId = R.drawable.ic_x_twitter,
                    labelResId = R.string.x_twitter,
                    url = SoraBranding.X_URL,
                ),
            )
        }
    }
}
