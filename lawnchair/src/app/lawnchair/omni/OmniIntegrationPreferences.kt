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

package app.lawnchair.omni

import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun OmniIntegrationPreferences() {
    val prefs = preferenceManager()
    val context = LocalContext.current
    // PackageManager Binder calls belong off Main, including on the settings screen.
    // The SDK independently repeats signer and capability checks for every incoming call.
    val sharedSigner = produceState<Boolean?>(initialValue = null, key1 = context) {
        value = withContext(Dispatchers.IO) {
            OmniAskActivity.WORKSPACE_PACKAGES.any { workspacePackage ->
                runCatching {
                    context.packageManager.checkSignatures(
                        context.packageName,
                        workspacePackage,
                    ) == PackageManager.SIGNATURE_MATCH
                }.getOrDefault(false)
            }
        }
    }.value
    val trustStatus = when (sharedSigner) {
        null -> stringResource(R.string.omni_trust_checking)
        true -> stringResource(R.string.omni_trust_verified)
        false -> stringResource(R.string.omni_trust_unverified)
    }
    val remoteControl = prefs.omniRemoteControl.getAdapter()
    PreferenceGroup(
        heading = stringResource(R.string.omni_integration),
        description = trustStatus,
    ) {
        SwitchPreference(
            adapter = prefs.omniSearchSuggestions.getAdapter(),
            label = stringResource(R.string.omni_suggestions),
            description = stringResource(R.string.omni_suggestions_description),
        )
        SwitchPreference(
            adapter = prefs.omniAlwaysSuggest.getAdapter(),
            label = stringResource(R.string.omni_always_suggest),
            enabled = prefs.omniSearchSuggestions.getAdapter().state.value,
        )
        SwitchPreference(
            adapter = remoteControl,
            label = stringResource(R.string.omni_remote_control),
            description = stringResource(R.string.omni_remote_control_description),
            // Always permit revocation. Do not enable a new privileged opt-in without a verified signer.
            enabled = sharedSigner == true || remoteControl.state.value,
        )
    }
}
