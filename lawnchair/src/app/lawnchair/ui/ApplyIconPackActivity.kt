/*
 * Copyright 2026, Lawnchair
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

package app.lawnchair.ui

import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.icons.CustomAdaptiveIconDrawable
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.iconPackIntents
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import com.android.launcher3.R
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ApplyIconPackActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "packageName"
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val packPackageName = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        if (packPackageName.isEmpty()) {
            finish()
            return
        }

        setContent {
            var packInfo by remember { mutableStateOf<Pair<String, Drawable>?>(null) }
            var resolved by remember { mutableStateOf(false) }

            LaunchedEffect(packPackageName) {
                val result = withContext(Dispatchers.IO) {
                    resolveIconPackInfo(packPackageName)
                }
                packInfo = result
                resolved = true
                if (result == null) finish()
            }

            val info = packInfo
            if (resolved && info != null) {
                LawnchairTheme {
                    EdgeToEdge()
                    ApplyIconPackSheet(
                        packName = info.first,
                        packIcon = info.second,
                        onConfirm = {
                            PreferenceManager.getInstance(this@ApplyIconPackActivity)
                                .iconPackPackage.set(packPackageName)
                            finish()
                        },
                        onDismiss = { finish() },
                    )
                }
            }
        }
    }

    private fun resolveIconPackInfo(packageName: String): Pair<String, Drawable>? {
        val pm = this.packageManager
        val resolveInfo = iconPackIntents
            .flatMap { pm.queryIntentActivities(it, 0) }
            .firstOrNull { it.activityInfo.packageName == packageName }
            ?: return null
        val name = resolveInfo.loadLabel(pm).toString()
        val icon = CustomAdaptiveIconDrawable.wrapNonNull(resolveInfo.loadIcon(pm))
        return name to icon
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ApplyIconPackSheet(
    packName: String,
    packIcon: Drawable,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        ModalBottomSheetContent(
            title = { Text(text = stringResource(id = R.string.apply_icon_pack_title)) },
            content = {
                PreferenceGroup {
                    PreferenceTemplate(
                        title = { Text(text = packName) },
                        startWidget = {
                            Image(
                                painter = rememberDrawablePainter(drawable = packIcon),
                                contentDescription = packName,
                                modifier = Modifier.size(36.dp),
                            )
                        },
                    )
                }
            },
            buttons = {
                OutlinedButton(
                    onClick = onDismiss,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(id = android.R.string.cancel))
                }
                Spacer(modifier = Modifier.requiredWidth(8.dp))
                Button(
                    onClick = onConfirm,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(id = R.string.action_apply))
                }
            },
        )
    }
}
