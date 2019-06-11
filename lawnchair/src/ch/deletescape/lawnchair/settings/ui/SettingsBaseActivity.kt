/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.settings.ui

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import ch.deletescape.lawnchair.LawnchairLayoutInflater
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.getBooleanAttr
import ch.deletescape.lawnchair.launcherAppState
import ch.deletescape.lawnchair.theme.ThemeManager
import ch.deletescape.lawnchair.theme.ThemeOverride
import com.android.launcher3.InsettableFrameLayout
import com.android.launcher3.R
import com.android.launcher3.Utilities

@SuppressLint("Registered")
open class SettingsBaseActivity : AppCompatActivity(), ColorEngine.OnColorChangeListener, ThemeManager.ThemeableActivity {
    val dragLayer by lazy { SettingsDragLayer(this, null) }
    val decorLayout by lazy { DecorLayout(this, window) }

    protected open val themeSet: ThemeOverride.ThemeSet get() = ThemeOverride.Settings()
    private lateinit var themeOverride: ThemeOverride
    private var currentTheme = 0
    private var paused = false

    private val customLayoutInflater by lazy {
        LawnchairLayoutInflater(super.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater, this)
    }

    private val fromSettings by lazy { intent.getBooleanExtra(EXTRA_FROM_SETTINGS, false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        (layoutInflater as LawnchairLayoutInflater).installFactory(delegate)
        themeOverride = ThemeOverride(themeSet, this)
        themeOverride.applyTheme(this)
        currentTheme = themeOverride.getTheme(this)

        super.onCreate(savedInstanceState ?: intent.getBundleExtra("state"))
        dragLayer.addView(decorLayout, InsettableFrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        super.setContentView(dragLayer)

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

    protected fun overrideOpenAnim() {
        if (fromSettings) {
            overridePendingTransition("activity_open_enter", "activity_open_exit")
        }
    }

    protected fun overrideCloseAnim() {
        if (fromSettings) {
            overridePendingTransition("activity_close_enter", "activity_close_exit")
        }
    }

    private fun getAndroidAnimRes(name: String): Int {
        return resources.getIdentifier(name, "anim", "android")
    }

    private fun overridePendingTransition(enter: String, exit: String) {
        val enterRes = getAndroidAnimRes(enter)
        val exitRes = getAndroidAnimRes(exit)
        if (enterRes != 0 && exitRes != 0) {
            overridePendingTransition(enterRes, exitRes)
        }
    }

    override fun onBackPressed() {
        dragLayer.getTopOpenView()?.let {
            it.close(true)
            return
        }
        super.onBackPressed()
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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ColorEngine.getInstance(this).addColorChangeListeners(this, ColorEngine.Resolvers.ACCENT)
    }

    override fun onColorChange(resolveInfo: ColorEngine.ResolveInfo) {
        when (resolveInfo.key) {
            ColorEngine.Resolvers.ACCENT -> {
                val arrowBack = resources.getDrawable(R.drawable.ic_arrow_back, null)
                arrowBack?.setTint(resolveInfo.color)
                supportActionBar?.setHomeAsUpIndicator(arrowBack)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ColorEngine.getInstance(this).removeColorChangeListeners(this, ColorEngine.Resolvers.ACCENT)
    }

    override fun onResume() {
        super.onResume()
        paused = false
    }

    override fun onPause() {
        super.onPause()
        paused = true
    }

    protected open fun createRelaunchIntent(): Intent {
        val state = Bundle()
        onSaveInstanceState(state)
        return intent.putExtra("state", state)
    }

    protected fun getRelaunchInstanceState(savedInstanceState: Bundle?): Bundle? {
        return savedInstanceState ?: intent.getBundleExtra("state")
    }

    override fun onThemeChanged() {
        if (currentTheme == themeOverride.getTheme(this)) return
        if (paused) {
            recreate()
        } else {
            finish()
            startActivity(createRelaunchIntent(), ActivityOptions.makeCustomAnimation(
                    this, android.R.anim.fade_in, android.R.anim.fade_out).toBundle())
        }
    }

    override fun getSystemService(name: String): Any? {
        if (name == Context.LAYOUT_INFLATER_SERVICE) {
            return customLayoutInflater
        }
        return super.getSystemService(name)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        launcherAppState.launcher?.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    companion object {

        const val EXTRA_FROM_SETTINGS = "fromSettings"

        fun getActivity(context: Context): SettingsBaseActivity {
            return context as? SettingsBaseActivity ?: (context as ContextWrapper).baseContext as SettingsBaseActivity
        }
    }
}
