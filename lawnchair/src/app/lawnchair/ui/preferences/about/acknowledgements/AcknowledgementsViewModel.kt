package app.lawnchair.ui.preferences.about.acknowledgements

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AcknowledgementsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val acknowledgementsRepository = AcknowledgementsRepository.getInstance(application)

    val ossLibraries: StateFlow<List<OssLibrary>> = acknowledgementsRepository.ossLibraries
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList(),
        )
}
