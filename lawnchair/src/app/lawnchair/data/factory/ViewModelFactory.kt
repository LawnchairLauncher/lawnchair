package app.lawnchair.data.factory

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ViewModelFactory<T : ViewModel>(
    private val context: Context,
    private val creator: (Context) -> T,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return creator(context) as? T
            ?: throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
