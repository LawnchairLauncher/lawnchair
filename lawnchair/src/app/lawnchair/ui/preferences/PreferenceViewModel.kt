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
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import app.lawnchair.icons.CustomAdaptiveIconDrawable
import app.lawnchair.ui.preferences.about.licenses.License
import com.android.launcher3.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.InputStreamReader

private val iconPackIntents = listOf(
    Intent("com.novalauncher.THEME"),
    Intent("org.adw.launcher.icons.ACTION_PICK_ICON"),
    Intent("com.dlto.atom.launcher.THEME"),
    Intent("android.intent.action.MAIN").addCategory("com.anddoes.launcher.THEME")
)

class PreferenceViewModel(application: Application) : AndroidViewModel(application), PreferenceInteractor {
    override fun getIconPacks(): List<IconPackInfo> {
        val context = getApplication<Application>()
        val pm = context.packageManager

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
            ContextCompat.getDrawable(context, R.drawable.ic_launcher_home)!!
        )
        val defaultIconPack = IconPackInfo(context.getString(R.string.system_icons), "", lawnchairIcon)

        return listOf(defaultIconPack) + iconPacks.sortedBy { it.name }
    }

    override val licenses by lazy {
        runBlocking {
            val licensesState = mutableStateOf<List<License>?>(null)
            launch {
                val res = application.resources
                val reader = BufferedReader(InputStreamReader(res.openRawResource(R.raw.third_party_license_metadata)))
                val licenses = reader.readLines().map { line ->
                    val parts = line.split(" ")
                    val startEnd = parts[0].split(":")
                    val start = startEnd[0].toLong()
                    val length = startEnd[1].toInt()
                    val name = parts.subList(1, parts.size).joinToString(" ")
                    License(name, start, length)
                }.sortedBy { it.name.lowercase() }
                licensesState.value = licenses
            }
            licensesState
        }
    }
}
