package ch.deletescape.lawnchair.settings.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.android.launcher3.R
import com.android.launcher3.Utilities

@SuppressLint("Registered")
open class SettingsBaseActivity : AppCompatActivity() {
    private val decorLayout by lazy { DecorLayout(this, window) }
    var actionBarElevation: Float
        get() = decorLayout.actionBarElevation
        set(value) { decorLayout.actionBarElevation = value }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        super.setContentView(decorLayout)

        Utilities.setLightUi(window)
        window.statusBarColor = 0
        window.navigationBarColor = 0

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
    }

    override fun setContentView(v: View) {
        val contentParent = decorLayout.findViewById(android.R.id.content) as ViewGroup
        contentParent.removeAllViews()
        contentParent.addView(v)
    }

    override fun setContentView(resId: Int) {
        val contentParent = decorLayout.findViewById(android.R.id.content) as ViewGroup
        contentParent.removeAllViews()
        LayoutInflater.from(this).inflate(resId, contentParent)
    }

    override fun setContentView(v: View, lp: ViewGroup.LayoutParams) {
        val contentParent = decorLayout.findViewById(android.R.id.content) as ViewGroup
        contentParent.removeAllViews()
        contentParent.addView(v, lp)
    }
}