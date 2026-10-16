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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
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
    var revision by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val sharedSigner = produceState<Boolean?>(initialValue = null, key1 = context, key2 = revision) {
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
    var showDetails by rememberSaveable { mutableStateOf(false) }
    PreferenceGroup(
        heading = stringResource(R.string.omni_integration),
    ) {
        SwitchPreference(
            adapter = prefs.omniSearchSuggestions.getAdapter(),
            label = stringResource(R.string.omni_suggestions),
            description = stringResource(R.string.omni_suggestions_short),
        )
        SwitchPreference(
            adapter = prefs.omniAlwaysSuggest.getAdapter(),
            label = stringResource(R.string.omni_always_suggest_short),
            description = stringResource(R.string.omni_always_suggest_summary),
            enabled = prefs.omniSearchSuggestions.getAdapter().state.value,
        )
        SwitchPreference(
            adapter = remoteControl,
            label = stringResource(R.string.omni_remote_control_short),
            description = stringResource(
                when (sharedSigner) {
                    null -> R.string.omni_trust_checking
                    true -> R.string.omni_remote_control_short_summary
                    false -> R.string.omni_control_signer_missing
                },
            ),
            // Always permit revocation. Do not enable a new privileged opt-in without a verified signer.
            enabled = sharedSigner == true || remoteControl.state.value,
        )
        ClickablePreference(
            label = stringResource(R.string.omni_integration_details),
            subtitle = stringResource(
                when (sharedSigner) {
                    null -> R.string.omni_trust_checking
                    true -> R.string.omni_control_signer_verified
                    false -> R.string.omni_control_signer_missing
                },
            ),
            onClick = { showDetails = true },
        )
    }
    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text(stringResource(R.string.omni_integration_details)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(trustStatus)
                    Text(stringResource(R.string.omni_suggestions_description))
                    Text(stringResource(R.string.omni_remote_control_description))
                }
            },
            confirmButton = { TextButton(onClick = { showDetails = false }) { Text(stringResource(android.R.string.ok)) } },
        )
    }
}
