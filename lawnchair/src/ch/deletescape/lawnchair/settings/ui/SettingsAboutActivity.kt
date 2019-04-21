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

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import me.jfenn.attribouter.Attribouter

class SettingsAboutActivity : SettingsActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun createLaunchFragment(intent: Intent): Fragment {
        return Attribouter.from(this).withGitHubToken(BuildConfig.GITHUB_TOKEN).withFile(R.xml.attribouter).toFragment()
    }

    override fun shouldUseLargeTitle(): Boolean {
        return false
    }

    override fun shouldShowSearch(): Boolean {
        return false
    }
}
