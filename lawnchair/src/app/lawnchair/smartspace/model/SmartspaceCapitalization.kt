package app.lawnchair.smartspace.model

import androidx.annotation.StringRes
import com.android.launcher3.R

/**
 * Contains the data of how smartspace must capitalize its text.
 */
sealed class SmartspaceCapitalization(@StringRes val nameResourceId: Int) {
    companion object {

        fun fromString(value: String): SmartspaceCapitalization = when (value) {
            "default" -> Default
            "titlecase" -> Titlecase
            "uppercase" -> Uppercase
            "lowercase" -> Lowercase
            else -> Default
        }

        /**
         * @return The list of all capitalization methods.
         */
        fun values() = listOf(Default, Titlecase, Uppercase, Lowercase)
    }

    object Default : SmartspaceCapitalization(nameResourceId = R.string.smartspace_capitalization_default) {
        override fun toString() = "default"
    }
    object Titlecase : SmartspaceCapitalization(nameResourceId = R.string.smartspace_capitalization_titlecase) {
        override fun toString() = "titlecase"
    }
    object Uppercase : SmartspaceCapitalization(nameResourceId = R.string.smartspace_capitalization_uppercase) {
        override fun toString() = "uppercase"
    }
    object Lowercase : SmartspaceCapitalization(nameResourceId = R.string.smartspace_capitalization_lowercase) {
        override fun toString() = "lowercase"
    }
}
