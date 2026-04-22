package app.lawnchair.ui.preferences.about

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import com.patrykmichalik.opto.core.firstBlocking
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _uiState = MutableStateFlow(AboutUiState())
    val uiState = _uiState.asStateFlow()
    val updateState = nightlyBuildsRepository.updateState

    init {
        _uiState.update {
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

        viewModelScope.launch(Dispatchers.Default) {
            val activeContributors = fetchActiveContributors()
            val updatedCoreTeam = _uiState.value.coreTeam.map { member ->
                val status = if (member.githubUsername != null && activeContributors.contains(member.githubUsername.lowercase())) ContributorStatus.Active else ContributorStatus.Idle
                member.copy(status = status)
            }
            _uiState.update { it.copy(coreTeam = updatedCoreTeam) }
        }

        // Check if the build variant is Nightly
        // AND check if user has enabled auto updater (available to Nightly variant)
        // OR check if user has overridden it in debug flags (available to All variant)
        if (BuildConfig.APPLICATION_ID.contains("nightly") && prefs2.autoUpdaterNightly.firstBlocking()) {
            nightlyBuildsRepository.checkForUpdate()
            viewModelScope.launch {
                nightlyBuildsRepository.updateState.collect { state ->
                    _uiState.update { it.copy(updateState = state) }
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
            nightlyBuildsRepository.api.getRepositoryEvents("LawnchairLauncher", "lawnchair")
                .map { it.actor.login.lowercase() }
                .toSet()
        }.getOrDefault(emptySet())
    }

    companion object {
        private val team = listOf(
            TeamMember(
                name = "Amogh Lele",
                role = Role.Development,
                photoUrl = "https://avatars.githubusercontent.com/u/31761843",
                socialUrl = "https://www.linkedin.com/in/amogh-lele/",
            ),
            TeamMember(
                name = "Antonio J. Roa Valverde",
                role = Role.Development,
                photoUrl = "https://avatars.githubusercontent.com/u/914983",
                socialUrl = "https://x.com/6020peaks",
            ),
            TeamMember(
                name = "David Sn",
                role = Role.DevOps,
                photoUrl = "https://i.imgur.com/b65akTl.png",
                socialUrl = "https://codebucket.de",
            ),
            TeamMember(
                name = "Zongle Wang",
                role = Role.Development,
                photoUrl = "https://avatars.githubusercontent.com/u/10363352",
                socialUrl = "https://github.com/Goooler",
                githubUsername = "Goooler",
            ),
            TeamMember(
                name = "Harsh Shandilya",
                role = Role.Development,
                photoUrl = "https://avatars.githubusercontent.com/u/13348378",
                socialUrl = "https://github.com/msfjarvis",
            ),
            TeamMember(
                name = "John Andrew Camu (MrSluffy)",
                role = Role.Development,
                photoUrl = "https://avatars.githubusercontent.com/u/36076410",
                socialUrl = "https://github.com/MrSluffy",
                githubUsername = "MrSluffy",
            ),
            TeamMember(
                name = "Kshitij Gupta",
                role = Role.Development,
                photoUrl = "https://avatars.githubusercontent.com/u/18647641",
                socialUrl = "https://x.com/Agent_Fabulous",
            ),
            TeamMember(
                name = "Manuel Lorenzo",
                role = Role.Development,
                photoUrl = "https://avatars.githubusercontent.com/u/183264",
                socialUrl = "https://x.com/noloman",
            ),
            TeamMember(
                name = "paphonb",
                role = Role.Development,
                photoUrl = "https://avatars.githubusercontent.com/u/8080853",
                socialUrl = "https://x.com/paphonb",
            ),
            TeamMember(
                name = "raphtlw",
                role = Role.Development,
                photoUrl = "https://avatars.githubusercontent.com/u/47694127",
                socialUrl = "https://x.com/raphtlw",
            ),
            TeamMember(
                name = "Rhyse Simpson",
                role = Role.QuickSwitchMaintenance,
                photoUrl = "https://avatars.githubusercontent.com/u/7065700",
                socialUrl = "https://x.com/skittles9823",
            ),
            TeamMember(
                name = "Pun Butrach",
                role = Role.Development,
                photoUrl = "https://avatars.githubusercontent.com/u/93124920",
                socialUrl = "https://github.com/validcube",
            ),
            TeamMember(
                name = "SuperDragonXD",
                role = Role.Development,
                photoUrl = "https://avatars.githubusercontent.com/u/70206496",
                socialUrl = "https://github.com/SuperDragonXD",
                githubUsername = "SuperDragonXD",
            ),
            TeamMember(
                name = "Yasan Glass",
                role = Role.Development,
                photoUrl = "https://avatars.githubusercontent.com/u/41836211",
                socialUrl = "https://yasan.glass",
                githubUsername = "yasanglass",
            ),
        )

        private val topLinks = listOf(
            Link(
                iconResId = R.drawable.ic_new_releases,
                labelResId = R.string.news,
                url = "https://t.me/lawnchairci",
            ),
            Link(
                iconResId = R.drawable.ic_help,
                labelResId = R.string.support,
                url = "https://lawnchair.app/support",
            ),
            Link(
                iconResId = R.drawable.ic_github,
                labelResId = R.string.github,
                url = "https://github.com/LawnchairLauncher/lawnchair",
            ),
            Link(
                iconResId = R.drawable.ic_translate,
                labelResId = R.string.translate,
                url = "https://lawnchair.crowdin.com/lawnchair",
            ),
            Link(
                iconResId = R.drawable.ic_open_collective,
                labelResId = R.string.donate,
                url = "https://opencollective.com/lawnchair",
            ),
        )

        private val bottomLinks = listOf(
            Link(
                iconResId = R.drawable.ic_telegram,
                labelResId = R.string.telegram,
                url = "https://t.me/lccommunity",
            ),
            Link(
                iconResId = R.drawable.ic_discord,
                labelResId = R.string.discord,
                url = "https://discord.com/invite/3x8qNWxgGZ",
            ),
            Link(
                iconResId = R.drawable.ic_x_twitter,
                labelResId = R.string.x_twitter,
                url = "https://x.com/lawnchairapp",
            ),
        )

        private val supportAndPr = listOf(
            TeamMember(
                name = "Daniel Souza",
                role = Role.Support,
                photoUrl = "https://avatars.githubusercontent.com/u/32078304",
                socialUrl = "https://github.com/DanGLES3",
            ),
            TeamMember(
                name = "Giuseppe Longobardo",
                role = Role.Support,
                photoUrl = "https://avatars.githubusercontent.com/u/49398464",
                socialUrl = "https://github.com/joseph-20",
            ),
            TeamMember(
                name = "Rik Koedoot",
                role = Role.SupportAndPr,
                photoUrl = "https://avatars.githubusercontent.com/u/29402532",
                socialUrl = "https://x.com/rikkoedoot",
            ),
        )
    }
}
