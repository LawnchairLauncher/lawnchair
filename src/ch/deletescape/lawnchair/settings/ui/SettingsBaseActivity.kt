package ch.deletescape.lawnchair.settings.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import ch.deletescape.lawnchair.getBooleanAttr
import ch.deletescape.lawnchair.hookGoogleSansDialogTitle
import ch.deletescape.lawnchair.theme.ThemeManager
import ch.deletescape.lawnchair.theme.ThemeOverride
import com.android.launcher3.R
import com.android.launcher3.Utilities

@SuppressLint("Registered")
open class SettingsBaseActivity : AppCompatActivity() {
    val decorLayout by lazy { DecorLayout(this, window) }
    var actionBarElevation: Float
        get() = decorLayout.actionBarElevation
        set(value) { decorLayout.actionBarElevation = value }

    override fun onCreate(savedInstanceState: Bundle?) {
        hookGoogleSansDialogTitle()
        ThemeManager.getInstance(this).addOverride(ThemeOverride.Settings(this))

        super.onCreate(savedInstanceState)
        super.setContentView(decorLayout)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        var flags = window.decorView.systemUiVisibility
        if (Utilities.ATLEAST_MARSHMALLOW) {
            val useLightBars = getBooleanAttr(R.attr.useLightSystemBars)
            flags = Utilities.setFlag(flags, View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR, useLightBars)
            if (Utilities.ATLEAST_OREO) {
                flags = Utilities.setFlag(flags, View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR, useLightBars)
            }
        }
        flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        window.decorView.systemUiVisibility = flags
    }

    override fun setContentView(v: View) {
        val contentParent = getContentFrame()
        contentParent.removeAllViews()
        contentParent.addView(v)
    }

    override fun setContentView(resId: Int) {
        val contentParent = getContentFrame()
        contentParent.removeAllViews()
        LayoutInflater.from(this).inflate(resId, contentParent)
    }

    override fun setContentView(v: View, lp: ViewGroup.LayoutParams) {
        val contentParent = getContentFrame()
        contentParent.removeAllViews()
        contentParent.addView(v, lp)
    }

    fun getContentFrame(): ViewGroup {
        return decorLayout.findViewById(android.R.id.content)
    }
}