package ch.deletescape.lawnchair.iconpack
import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import ch.deletescape.lawnchair.reloadIcons
import ch.deletescape.lawnchair.theme.ThemeManager
import ch.deletescape.lawnchair.theme.ThemeOverride
import com.android.launcher3.*

class ApplyIconPackActivity : Activity() {
    private val prefs by lazy { Utilities.getLawnchairPrefs(this) }
    private val themeOverride: ThemeOverride get() = ThemeOverride.SettingsTransparent(this)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.getInstance(this).addOverride(themeOverride)

        prefs.iconPack = intent.getStringExtra("packageName")
        reloadIcons(this)
        val packName = IconPackManager.getInstance(this).currentPack.displayName
        val message = String.format(getString(R.string.icon_pack_applied_toast), packName)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
        Utilities.goToHome(this)
    }
}
