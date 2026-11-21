package app.lawnchair.ui.preferences.destinations

import android.app.Activity
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.data.iconoverride.IconOverrideRepository
import app.lawnchair.findActivity
import app.lawnchair.icons.iconpack.LocalIconPack
import app.lawnchair.icons.picker.IconPickerItem
import app.lawnchair.icons.picker.IconType
import app.lawnchair.preferences2.asState
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.LocalPreferenceInteractor
import app.lawnchair.ui.preferences.components.AppItem
import app.lawnchair.ui.preferences.components.CropIconDialog
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.PreferenceLayoutLazyColumn
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import app.lawnchair.ui.preferences.navigation.IconPicker
import app.lawnchair.ui.util.OnResult
import app.lawnchair.util.requireSystemService
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.android.msdl.data.model.MSDLToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "SelectIconPreference"

@Composable
fun SelectIconPreference(
    componentKey: ComponentKey,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(context)
    val label = remember(componentKey) {
        resolveAppLabel(context.requireSystemService(), componentKey)
    }
    val iconPacks by LocalPreferenceInteractor.current.iconPacks.collectAsStateWithLifecycle()

    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val launcherAppState = LauncherAppState.getInstance(context)
    val model = launcherAppState.model

    val prefs2 = preferenceManager2()
    val currentIconShape by prefs2.iconShape.asState()

    var currentCropSession by remember { mutableStateOf<CropSession?>(null) }
    var nextSessionId by remember { mutableIntStateOf(1) }
    var cropJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            cropJob?.cancel()
            currentCropSession?.cancelAndRecycle()
        }
    }

    val repo = IconOverrideRepository.INSTANCE.get(context)
    OnResult<IconPickerItem> { item ->
        scope.launch {
            repo.setOverride(componentKey, item)
            // Refresh package icons while the model is still loaded; forceReload alone can
            // skip PackageUpdatedTask and leave icon-cache entries that look "fresh".
            // onAppIconChanged is @WorkerThread (blocking ShortcutManager query).
            withContext(Dispatchers.IO) {
                model.onAppIconChanged(componentKey.componentName.packageName, componentKey.user)
            }
            context.findActivity()?.run {
                setResult(Activity.RESULT_OK)
                finish()
            }
        }
    }

    val overrideItem by repo.observeTarget(componentKey).collectAsStateWithLifecycle(initialValue = null)
    val hasOverride = overrideItem != null

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            cropJob?.cancel()
            currentCropSession?.cancelAndRecycle()
            currentCropSession = null
            cropJob = scope.launch {
                val loadedBitmap = withContext(Dispatchers.IO) {
                    LocalIconPack.loadBitmapForCropping(context, uri)
                }
                if (!isActive) {
                    if (loadedBitmap != null && !loadedBitmap.isRecycled) {
                        loadedBitmap.recycle()
                    }
                    return@launch
                }
                if (loadedBitmap != null) {
                    currentCropSession = CropSession(nextSessionId++, loadedBitmap)
                }
            }
        }
    }

    val activeSession = currentCropSession
    if (activeSession != null && !activeSession.isCancelled && !activeSession.isSaving) {
        CropIconDialog(
            bitmap = activeSession.sourceBitmap,
            iconShape = currentIconShape,
            onDismissRequest = {
                cropJob?.cancel()
                activeSession.cancelAndRecycle()
                if (currentCropSession === activeSession) {
                    currentCropSession = null
                }
            },
            onConfirm = { croppedSquareBitmap ->
                if (activeSession.isCancelled || activeSession.isSaving) {
                    if (!croppedSquareBitmap.isRecycled) {
                        croppedSquareBitmap.recycle()
                    }
                    return@CropIconDialog
                }
                activeSession.isSaving = true
                cropJob = scope.launch {
                    try {
                        val fileName = withContext(Dispatchers.IO) {
                            LocalIconPack.saveSquareBitmap(context, croppedSquareBitmap)
                        }
                        if (activeSession.isCancelled || currentCropSession !== activeSession) {
                            return@launch
                        }
                        activeSession.cancelAndRecycle()
                        if (currentCropSession === activeSession) {
                            currentCropSession = null
                        }
                        if (fileName != null) {
                            val item = IconPickerItem(
                                packPackageName = LocalIconPack.PACKAGE_NAME,
                                drawableName = fileName,
                                label = "Local image",
                                type = IconType.Normal,
                            )
                            repo.setOverride(componentKey, item)
                            withContext(Dispatchers.IO) {
                                model.onAppIconChanged(componentKey.componentName.packageName, componentKey.user)
                            }
                            context.findActivity()?.run {
                                setResult(Activity.RESULT_OK)
                                finish()
                            }
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.icon_picker_load_failed),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    } finally {
                        if (!croppedSquareBitmap.isRecycled) {
                            croppedSquareBitmap.recycle()
                        }
                        if (activeSession.isCancelled || currentCropSession !== activeSession) {
                            activeSession.cancelAndRecycle()
                        }
                    }
                }
            },
        )
    }

    PreferenceLayoutLazyColumn(
        label = label,
        modifier = modifier,
    ) {
        if (hasOverride) {
            preferenceGroupItems(1, isFirstChild = true) {
                ClickablePreference(
                    label = stringResource(id = R.string.icon_picker_reset_to_default),
                    onClick = {
                        scope.launch {
                            repo.deleteOverride(componentKey)
                            withContext(Dispatchers.IO) {
                                model.onAppIconChanged(
                                    componentKey.componentName.packageName,
                                    componentKey.user,
                                )
                            }
                            context.findActivity()?.run {
                                setResult(Activity.RESULT_OK)
                                finish()
                            }
                        }
                    },
                )
            }
        }

        preferenceGroupItems(
            count = 1,
            isFirstChild = !hasOverride,
        ) {
            ClickablePreference(
                label = stringResource(id = R.string.select_image_from_local_folder),
                startWidget = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_select_image_folder),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                onClick = {
                    if (currentCropSession?.isSaving == true) return@ClickablePreference
                    mMSDLPlayerWrapper.playToken(MSDLToken.TAP_MEDIUM_EMPHASIS)
                    try {
                        imagePickerLauncher.launch("image/*")
                    } catch (t: Throwable) {
                        Log.e(TAG, "Failed to launch image picker", t)
                    }
                },
            )
        }

        preferenceGroupItems(
            items = iconPacks,
            isFirstChild = false,
            heading = { stringResource(id = R.string.pick_icon_from_label) },
        ) { _, iconPack ->
            AppItem(
                label = iconPack.name,
                icon = remember(iconPack) { iconPack.icon.toBitmap() },
                onClick = {
                    mMSDLPlayerWrapper.playToken(MSDLToken.TAP_MEDIUM_EMPHASIS)
                    if (iconPack.packageName.isEmpty()) {
                        navController.navigate(IconPicker())
                    } else {
                        navController.navigate(IconPicker(iconPack.packageName))
                    }
                },
            )
        }
    }
}

/**
 * Resolves a display label for [componentKey], including work/private profiles.
 *
 * [LauncherApps.resolveActivity] can return null or throw for non-current users; fall back to
 * [LauncherApps.getActivityList] and finally the activity class name.
 */
private fun resolveAppLabel(launcherApps: LauncherApps, componentKey: ComponentKey): String {
    val componentName = componentKey.componentName
    val user = componentKey.user
    try {
        val intent = Intent().setComponent(componentName)
        launcherApps.resolveActivity(intent, user)?.label?.toString()?.let { return it }
    } catch (t: Throwable) {
        Log.w(TAG, "resolveActivity failed for $componentKey", t)
    }
    try {
        val activities = launcherApps.getActivityList(componentName.packageName, user)
        activities
            .firstOrNull { it.componentName == componentName }
            ?.label
            ?.toString()
            ?.let { return it }
        activities
            .firstOrNull()
            ?.label
            ?.toString()
            ?.let { return it }
    } catch (t: Throwable) {
        Log.w(TAG, "getActivityList failed for $componentKey", t)
    }
    return componentName.shortClassName?.trimStart('.') ?: componentName.packageName
}

private class CropSession(
    val id: Int,
    val sourceBitmap: Bitmap,
) {
    var isCancelled: Boolean = false
    var isSaving: Boolean = false

    fun cancelAndRecycle() {
        if (!isCancelled) {
            isCancelled = true
            if (!sourceBitmap.isRecycled) {
                sourceBitmap.recycle()
            }
        }
    }
}
