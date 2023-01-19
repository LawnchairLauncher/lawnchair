/*
 * Copyright 2022, Lawnchair
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
import app.lawnchair.ossnotices.OssLibrary
import app.lawnchair.ossnotices.getOssLibraries
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import app.lawnchair.util.getPackageVersionCode
import app.lawnchair.util.Constants.LAWNICONS_PACKAGE_NAME
import kotlinx.coroutines.flow.StateFlow

private val iconPackIntents = listOf(
    Intent("com.novalauncher.THEME"),
    Intent("org.adw.launcher.icons.ACTION_PICK_ICON"),
    Intent("com.dlto.atom.launcher.THEME"),
    Intent("android.intent.action.MAIN").addCategory("com.anddoes.launcher.THEME")
)

class PreferenceViewModel(private val app: Application) : AndroidViewModel(app), PreferenceInteractor {

    private val themedIconPackIntents = listOf(Intent(app.getString(R.string.icon_packs_intent_name)))
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

    override val themedIconPacks = flow {
        val pm = app.packageManager
        val themedIconPacks = themedIconPackIntents
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
        var defaultIconPack = listOf(
            IconPackInfo(
                name = app.getString(R.string.system_icons),
                packageName = "",
                icon = lawnchairIcon
            )
        )
        if (app.packageManager.getPackageVersionCode(LAWNICONS_PACKAGE_NAME) in 1..3) {
            val info = app.packageManager.getApplicationInfo(LAWNICONS_PACKAGE_NAME, 0)
            defaultIconPack = defaultIconPack + listOf(
                IconPackInfo(
                    name = pm.getApplicationLabel(info).toString(),
                    packageName = info.packageName,
                    icon = CustomAdaptiveIconDrawable.wrapNonNull(pm.getApplicationIcon(info))
                )
            )
        }
        val withSystemIcons = defaultIconPack + themedIconPacks.sortedBy { it.name }
        emit(withSystemIcons)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Lazily, listOf())

    override val ossLibraries: StateFlow<List<OssLibrary>> = flow {
        val ossLibraries = app.getOssLibraries(R.raw.third_party_license_metadata)
            .distinctBy { it.name }
        emit(ossLibraries)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
