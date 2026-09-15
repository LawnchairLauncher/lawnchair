package app.mica.ui.preferences.about

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import app.mica.preferences.PreferenceManager
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class AboutViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val prefs: PreferenceManager = PreferenceManager.getInstance(application)

    val uiState: StateFlow<AboutUiState>
        field = MutableStateFlow(AboutUiState())

    init {
        uiState.update {
            it.copy(
                versionName = if (prefs.hideVersionInfo.get()) {
                    prefs.pseudonymVersion.get() + " (pseudonym)"
                } else {
                    BuildConfig.VERSION_NAME
                },
                links = links,
            )
        }
    }

    companion object {
        private val links = listOf(
            Link(
                iconResId = R.drawable.ic_github,
                labelResId = R.string.github,
                url = "https://github.com/arcbaseproject/Mica-Launcher",
            ),
        )
    }
}
