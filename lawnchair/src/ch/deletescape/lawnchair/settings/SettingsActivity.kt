package ch.deletescape.lawnchair.settings

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.launcher3.R
import com.google.android.material.appbar.MaterialToolbar

class SettingsActivity : AppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    private lateinit var topAppBar: MaterialToolbar

    private val fragmentListener = object: FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentViewCreated(fm: FragmentManager, fragment: Fragment, v: View, savedInstanceState: Bundle?) {
            topAppBar.title = (fragment as? TitledFragment)?.title
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        topAppBar = findViewById(R.id.top_app_bar)
        supportFragmentManager.registerFragmentLifecycleCallbacks(fragmentListener, false)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.content, SettingsFragment())
            .commit()
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            pref.fragment)
        fragment.arguments = args
        fragment.setTargetFragment(caller, 0)
        supportFragmentManager.beginTransaction()
            .replace(R.id.content, fragment)
            .addToBackStack(null)
            .commit()
        return true
    }
}