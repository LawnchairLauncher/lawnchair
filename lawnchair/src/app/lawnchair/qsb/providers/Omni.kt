/*
 * Copyright 2026 Abdelrahman Hussein (عبدالرحمن حسين)
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

package app.lawnchair.qsb.providers

import app.lawnchair.omni.OmniAskActivity
import com.android.launcher3.Launcher
import com.android.launcher3.R

/** Opens the installed Workspace variant through the SDK's public, user-visible entry point. */
data object Omni : QsbSearchProvider(
    id = "omni",
    name = R.string.omni_search_provider,
    icon = R.drawable.ic_omni_assistant,
    packageName = "",
    website = "",
    type = QsbSearchProviderType.LOCAL,
) {
    override suspend fun launch(launcher: Launcher) {
        launcher.startActivity(OmniAskActivity.createIntent(launcher))
    }

    override suspend fun launch(launcher: Launcher, forceWebsite: Boolean) {
        launch(launcher)
    }
}
