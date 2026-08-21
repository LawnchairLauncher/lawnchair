package app.lawnchair.drivingmode

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.data.drivingmode.DrivingModeButtonAssignment
import app.lawnchair.data.drivingmode.DrivingModeButtonRepository
import app.lawnchair.data.drivingmode.DrivingModeSlot
import app.lawnchair.preferences2.asState
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.util.App
import app.lawnchair.util.DrawerBackgroundImageStore
import app.lawnchair.views.ComposeBottomSheet
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val TAG = "DrivingModeScreen"

/**
 * The fullscreen driving UI: a swipeable grid of user-assignable buttons, matching the paged
 * app drawer's page mechanic. Every slot exists whether assigned or not (unlike the drawer,
 * which packs a dynamic app list into however many pages it needs) - long-press any tile to
 * assign it via [DrivingModeButtonPickerContent].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrivingModeScreen(
    launcher: Launcher,
    goToFirstPageSignal: State<Int>,
    onExit: () -> Unit,
) {
    LawnchairTheme {
        val context = LocalContext.current
        val prefs2 = preferenceManager2()
        val rows by prefs2.drivingModeRows.asState()
        val columns by prefs2.drivingModeColumns.asState()
        val pages by prefs2.drivingModePages.asState()
        val backgroundColorOption by prefs2.drivingModeBackgroundColor.asState()
        val backgroundImageFileName by prefs2.drivingModeBackgroundImage.asState()
        val backgroundOpacity by prefs2.drivingModeBackgroundOpacity.asState()

        val repository = remember { DrivingModeButtonRepository.INSTANCE.get(context) }
        val assignments by repository.assignments.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()

        // Rotating re-triggers Launcher.onHandleConfigurationChanged(), which re-applies the
        // current LauncherState's own hotseat/workspace visibility defaults - undoing the manual
        // hide from DrivingModeOverlay.show() - and calls dragLayer.recreateControllers(), which
        // re-adds Launcher3's own swipe TouchControllers (open all apps, etc.) on top of our
        // overlay. Those then fight the pager's own drag handling for every touch, breaking both
        // page snapping and tile clicks until the app is killed and relaunched. Re-assert on every
        // configuration change (including orientation) while this screen is up.
        val configuration = LocalConfiguration.current
        LaunchedEffect(configuration) {
            launcher.workspace.visibility = View.INVISIBLE
            launcher.hotseat.visibility = View.INVISIBLE
            launcher.hotseat.alpha = 0f
            launcher.dragLayer.recreateControllers()
        }

        LaunchedEffect(rows, columns) {
            val slots = buildList {
                for (r in 0 until rows) for (c in 0 until columns) add(DrivingModeSlot(0, r, c))
            }
            val defaultActions = listOf(
                DrivingModeSpecialAction.NAVIGATION,
                DrivingModeSpecialAction.PHONE,
                DrivingModeSpecialAction.MUSIC,
                DrivingModeSpecialAction.CONTACTS,
                DrivingModeSpecialAction.SETTINGS,
            )
            val defaults = defaultActions.zip(slots) { action, slot -> Triple(slot, "special", action.id) }
            repository.seedDefaultsIfEmpty(defaults)
        }

        val backgroundBitmap by produceState<Bitmap?>(initialValue = null, backgroundImageFileName) {
            value = if (backgroundImageFileName.isEmpty()) {
                null
            } else {
                withContext(Dispatchers.IO) { DrawerBackgroundImageStore.loadBitmap(context, backgroundImageFileName) }
            }
        }

        val scrimColor = remember(backgroundColorOption, backgroundOpacity) {
            val base = backgroundColorOption.colorPreferenceEntry.lightColor.invoke(context)
            val baseColor = if (base != 0) Color(base) else Color.Black
            baseColor.copy(alpha = backgroundOpacity)
        }

        // The system nav bar is drawn by SystemUI on top of our content, not by Compose - tinting
        // only the in-app scrim leaves the gesture-nav strip showing the raw, untinted wallpaper.
        // Push the same color to the window directly, and restore whatever was there before.
        DisposableEffect(Unit) {
            val original = launcher.window?.navigationBarColor
            onDispose { if (original != null) launcher.window?.navigationBarColor = original }
        }
        LaunchedEffect(scrimColor) {
            launcher.window?.navigationBarColor = scrimColor.toArgb()
        }

        Box(modifier = Modifier.fillMaxSize()) {
            backgroundBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimColor),
            )

            // Slot coordinates (page, row, col) are always defined in terms of the portrait
            // rows x columns from settings - that stays the source of truth regardless of
            // orientation. In landscape we just transpose which stored coordinate plots as a
            // display row vs. column, so a grid configured tall-for-portrait reflows to fit a
            // wide screen instead of rendering as short, overflowing rows.
            val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

            val pagerState = rememberPagerState(pageCount = { pages })
            val goToFirstPage by goToFirstPageSignal
            LaunchedEffect(goToFirstPage) {
                // Signal starts at 0 and is only ever bumped by an explicit Home-button request -
                // skip the initial emission so this doesn't animate on first composition.
                if (goToFirstPage > 0) {
                    pagerState.animateScrollToPage(0)
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(16.dp),
            ) { page ->
                DrivingModePage(
                    page = page,
                    isCurrentPage = page == pagerState.currentPage,
                    rows = rows,
                    columns = columns,
                    isLandscape = isLandscape,
                    assignments = assignments,
                    context = context,
                    launcher = launcher,
                    onExit = onExit,
                    onPickerRequest = { slot, current ->
                        showPicker(launcher, repository, scope, slot, current, onExit)
                    },
                )
            }
        }
    }
}

@Composable
private fun DrivingModePage(
    page: Int,
    isCurrentPage: Boolean,
    rows: Int,
    columns: Int,
    isLandscape: Boolean,
    assignments: Map<DrivingModeSlot, DrivingModeButtonAssignment>,
    context: Context,
    launcher: Launcher,
    onExit: () -> Unit,
    onPickerRequest: (DrivingModeSlot, DrivingModeButtonAssignment?) -> Unit,
) {
    val displayRows = if (isLandscape) columns else rows
    val displayColumns = if (isLandscape) rows else columns
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        for (displayRow in 0 until displayRows) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (displayCol in 0 until displayColumns) {
                    val storedRow = if (isLandscape) displayCol else displayRow
                    val storedCol = if (isLandscape) displayRow else displayCol
                    val slot = DrivingModeSlot(page, storedRow, storedCol)
                    val assignment = assignments[slot]
                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        DrivingModeTile(
                            assignment = assignment,
                            isVisible = isCurrentPage,
                            context = context,
                            onClick = {
                                if (assignment != null) {
                                    executeAssignment(context, assignment, onExit)
                                } else {
                                    onPickerRequest(slot, null)
                                }
                            },
                            onLongClick = { onPickerRequest(slot, assignment) },
                        )
                    }
                }
            }
        }
    }
}

// Dimmer than pure white, for the special-action icons/labels specifically. Both 0xFFE0E0E0 and
// the canonical "light gray" 0xFFCCCCCC still read as white at a glance on-device - needs to be
// this much darker before the difference from the white app-icon labels is actually visible.
private val SpecialTileColor = Color(0xFF9E9E9E)

// Fixed size (not a fraction of tile width) so icons don't overflow short, wide tiles in
// landscape - a fraction-of-width size assumes a roughly square tile, which breaks down once
// rows/columns get transposed for landscape.
private val TileIconSize = 48.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrivingModeTile(
    assignment: DrivingModeButtonAssignment?,
    isVisible: Boolean,
    context: Context,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val special = assignment?.takeIf { it.targetType == TARGET_TYPE_SPECIAL }
        ?.let { DrivingModeSpecialAction.fromId(it.targetValue) }
    val app by produceState<App?>(initialValue = null, assignment) {
        value = if (assignment?.targetType == TARGET_TYPE_APP) {
            // Launcher3's IconCache asserts it's only ever touched from MODEL_EXECUTOR - a plain
            // Dispatchers.IO thread trips that assertion and silently fails app resolution.
            withContext(MODEL_EXECUTOR.asCoroutineDispatcher()) { resolveApp(context, assignment.targetValue) }
        } else {
            null
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            special == DrivingModeSpecialAction.SPEEDOMETER -> {
                SpeedometerTileContent(context = context, isVisible = isVisible)
            }
            special != null -> {
                special.icon.Render(
                    tint = SpecialTileColor,
                    modifier = Modifier.size(TileIconSize),
                )
                Text(
                    text = context.getString(special.labelRes),
                    color = SpecialTileColor,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
            app != null -> {
                val grayscaleIcon = remember(app) { toGrayscale(app!!.icon) }
                Image(
                    bitmap = grayscaleIcon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(TileIconSize),
                )
                Text(
                    text = app!!.label,
                    color = SpecialTileColor,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
            assignment == null -> {
                Icon(
                    imageVector = Icons.Rounded.Apps,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(TileIconSize * 0.85f),
                )
            }
        }
    }
}

@Composable
private fun SpeedometerTileContent(context: Context, isVisible: Boolean) {
    val useMph by preferenceManager2().drivingModeSpeedUnitMph.asState()
    val speedMetersPerSecond by rememberSpeedMetersPerSecond(context, isVisible)
    val displaySpeed = speedMetersPerSecond?.let { mps ->
        val converted = if (useMph) mps * 2.23694f else mps * 3.6f
        converted.roundToInt().coerceAtLeast(0)
    }
    Text(
        text = displaySpeed?.toString() ?: "–",
        color = SpecialTileColor,
        style = MaterialTheme.typography.displaySmall,
        maxLines = 1,
    )
    Text(
        text = stringResource(
            if (useMph) R.string.driving_mode_speed_unit_mph else R.string.driving_mode_speed_unit_kmh,
        ),
        color = SpecialTileColor,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
    )
}

/**
 * Only listens for GPS updates while [isVisible] - i.e. while this tile's page is the pager's
 * current page, not just while driving mode is up - since a paged grid can have several
 * speedometer tiles assigned but only one actually on screen at a time.
 */
@Composable
private fun rememberSpeedMetersPerSecond(context: Context, isVisible: Boolean): State<Float?> {
    val speed = remember { mutableStateOf<Float?>(null) }
    DisposableEffect(isVisible) {
        if (!isVisible) {
            return@DisposableEffect onDispose {}
        }
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            speed.value = null
            return@DisposableEffect onDispose {}
        }
        val locationManager = context.getSystemService(LocationManager::class.java)
        // An explicit object, not a SAM lambda: on API levels before 33, this interface's
        // onStatusChanged/onProviderEnabled/onProviderDisabled aren't default methods, so a
        // lambda covering only onLocationChanged wouldn't actually satisfy it on-device pre-33
        // even if it compiles fine against a newer compileSdk stub.
        val listener = object : LocationListener {
            override fun onLocationChanged(location: android.location.Location) {
                if (location.hasSpeed()) speed.value = location.speed
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        try {
            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to request location updates for speedometer", e)
        }
        onDispose {
            locationManager?.removeUpdates(listener)
            speed.value = null
        }
    }
    return speed
}

private fun toGrayscale(source: Bitmap): Bitmap {
    // App icons from IconCache are hardware bitmaps - a software Canvas can only draw those via
    // drawBitmap after copying to a CPU-backed config, never directly.
    val software = if (source.config == Bitmap.Config.HARDWARE) {
        source.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        source
    }
    val result = Bitmap.createBitmap(software.width, software.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(result)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = android.graphics.ColorMatrixColorFilter(
            android.graphics.ColorMatrix().apply { setSaturation(0f) },
        )
    }
    canvas.drawBitmap(software, 0f, 0f, paint)
    return result
}

private const val TARGET_TYPE_APP = "app"
private const val TARGET_TYPE_SPECIAL = "special"

private fun resolveApp(context: Context, componentKeyString: String): App? {
    val componentKey = ComponentKey.fromString(componentKeyString) ?: return null
    val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return null
    return try {
        launcherApps.getActivityList(componentKey.componentName.packageName, componentKey.user)
            .firstOrNull { it.componentName == componentKey.componentName }
            ?.let { App(context, it) }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to resolve app for $componentKeyString", e)
        null
    }
}

private fun executeAssignment(context: Context, assignment: DrivingModeButtonAssignment, onExit: () -> Unit) {
    when (assignment.targetType) {
        TARGET_TYPE_SPECIAL -> {
            val action = DrivingModeSpecialAction.fromId(assignment.targetValue) ?: return
            DrivingModeActionExecutor.execute(context, action, onExit)
        }
        TARGET_TYPE_APP -> {
            val componentKey = ComponentKey.fromString(assignment.targetValue) ?: return
            val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return
            try {
                launcherApps.startMainActivity(componentKey.componentName, componentKey.user, null, null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch $componentKey", e)
            }
        }
    }
}

private fun showPicker(
    launcher: Launcher,
    repository: DrivingModeButtonRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    slot: DrivingModeSlot,
    current: DrivingModeButtonAssignment?,
    onExit: () -> Unit,
) {
    ComposeBottomSheet.show(context = launcher) {
        DrivingModeButtonPickerContent(
            hasCurrentAssignment = current != null,
            onSelectApp = { app ->
                scope.launch { repository.setAssignment(slot, TARGET_TYPE_APP, app.key.toString()) }
                close(true)
            },
            onSelectSpecial = { action ->
                scope.launch { repository.setAssignment(slot, TARGET_TYPE_SPECIAL, action.id) }
                close(true)
            },
            onRemove = {
                scope.launch { repository.removeAssignment(slot) }
                close(true)
            },
        )
    }
}
