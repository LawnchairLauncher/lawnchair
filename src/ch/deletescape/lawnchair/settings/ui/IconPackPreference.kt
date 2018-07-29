package ch.deletescape.lawnchair.settings.ui

import android.content.Context
import android.support.v7.preference.DialogPreference
import android.util.AttributeSet
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.getIcon
import ch.deletescape.lawnchair.iconpack.IconPackManager
import com.android.launcher3.R
import com.android.launcher3.Utilities

class IconPackPreference(context: Context, attrs: AttributeSet?) : DialogPreference(context, attrs), LawnchairPreferences.OnPreferenceChangeListener {
    private val prefs by lazy { Utilities.getLawnchairPrefs(context) }
    private val packs by lazy { IconPackManager.getInstance(context).getPackProviderInfos() }
    private val default by lazy { IconPackManager.IconPackInfo("", context.getIcon(), context.getString(R.string.iconpack_none)) }

    init {
        IconPackManager.getInstance(context)
        layoutResource = R.layout.pref_with_preview_icon
        Utilities.getLawnchairPrefs(context).addOnPreferenceChangeListener("pref_icon_pack", this)
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        updatePreview()
    }

    private fun updatePreview() {
        val pack = if (prefs.iconPack == "") default else packs[prefs.iconPack]
        summary = pack?.label
        icon = pack?.icon
    }

    override fun getDialogLayoutResource() = R.layout.pref_dialog_icon_pack
}