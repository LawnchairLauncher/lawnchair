/*
 * Copyright 2021, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.ui.preferences

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lawnchair.icons.CustomAdaptiveIconDrawable
import app.lawnchair.ui.preferences.about.licenses.License
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.io.BufferedReader
import java.io.InputStreamReader

private val iconPackIntents = listOf(
    Intent("com.novalauncher.THEME"),
    Intent("org.adw.launcher.icons.ACTION_PICK_ICON"),
    Intent("com.dlto.atom.launcher.THEME"),
    Intent("android.intent.action.MAIN").addCategory("com.anddoes.launcher.THEME")
)

class PreferenceViewModel(private val app: Application) : AndroidViewModel(app), PreferenceInteractor {

    override val iconPacks = flow {
        val pm = app.packageManager
        val iconPacks = iconPackIntents
            .flatMap { pm.queryIntentActivities(it, 0) }
            .associateBy { it.activityInfo.packageName }
            .mapTo(mutableSetOf()) { (_, info) ->
                IconPackInfo(
                    info.loadLabel(pm).toString(),
                    info.activityInfo.packageName,
                    CustomAdaptiveIconDrawable.wrapNonNull(info.loadIcon(pm))
                )
            }
        val lawnchairIcon = CustomAdaptiveIconDrawable.wrapNonNull(
            ContextCompat.getDrawable(app, R.drawable.ic_launcher_home)!!
        )
        val defaultIconPack = IconPackInfo(
            name = app.getString(R.string.system_icons),
            packageName = "",
            icon = lawnchairIcon
        )
        val withSystemIcons = listOf(defaultIconPack) + iconPacks.sortedBy { it.name }
        emit(withSystemIcons)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Lazily, listOf())

    override val licenses = flow {
        val res = app.resources
        val reader = BufferedReader(InputStreamReader(res.openRawResource(R.raw.third_party_license_metadata)))
        val licenses = reader.readLines().map { line ->
            val parts = line.split(" ")
            val startEnd = parts[0].split(":")
            val start = startEnd[0].toLong()
            val length = startEnd[1].toInt()
            val name = parts.subList(1, parts.size).joinToString(" ")
            License(name, start, length)
        }.sortedBy { it.name.lowercase() }
        emit(licenses)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
}
