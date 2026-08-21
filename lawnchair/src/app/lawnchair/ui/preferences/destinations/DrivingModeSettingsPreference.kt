package app.lawnchair.ui.preferences.destinations

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import app.lawnchair.drivingmode.DrivingModeController
import app.lawnchair.drivingmode.DrivingModePrefs
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreference
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.util.DrawerBackgroundImageStore
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DrivingModeSettingsScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var permissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> permissionGranted = granted }
    if (!permissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        LaunchedEffect(Unit) { requestPermission.launch(Manifest.permission.BLUETOOTH_CONNECT) }
    }

    // Requested here (once, up front) rather than from the driving-mode overlay itself, since
    // that overlay is hosted in Launcher - a plain Activity, not a ComponentActivity - so it has
    // no ActivityResultRegistry to request permissions through. The speedometer tile just checks
    // this permission and shows nothing if it's still missing by the time it's actually visible.
    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val requestLocationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> locationPermissionGranted = granted }
    if (!locationPermissionGranted) {
        LaunchedEffect(Unit) { requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
    }

    var targetAddress by remember { mutableStateOf(DrivingModePrefs.getTargetDeviceAddress(context)) }
    val pairedDevices = remember(permissionGranted) {
        if (permissionGranted) getPairedDevices(context) else null
    }

    PreferenceLayout(
        label = stringResource(R.string.driving_mode_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        PreferenceGroup {
            val entries = pairedDevices.orEmpty().map { device ->
                ListPreferenceEntry(value = device.address) { "${device.name}  (${device.address})" }
            }
            val description = when {
                pairedDevices == null -> stringResource(R.string.driving_mode_bluetooth_disabled)
                pairedDevices.isEmpty() -> stringResource(R.string.driving_mode_no_paired_devices)
                else -> pairedDevices.firstOrNull { it.address == targetAddress }
                    ?.let { "${it.name}  (${it.address})" }
                    ?: stringResource(R.string.driving_mode_no_target_device)
            }
            ListPreference(
                entries = entries,
                value = targetAddress ?: "",
                onValueChange = { address ->
                    targetAddress = address
                    DrivingModePrefs.setTargetDeviceAddress(context, address)
                },
                label = stringResource(R.string.driving_mode_bluetooth_device_label),
                enabled = entries.isNotEmpty(),
                description = description,
            )
        }

        PreferenceGroup {
            ClickablePreference(
                label = stringResource(R.string.driving_mode_show_test),
                subtitle = stringResource(R.string.driving_mode_show_test_description),
                onClick = { DrivingModeController.simulateConnect() },
            )
        }

        val prefs2 = preferenceManager2()
        PreferenceGroup(heading = stringResource(R.string.driving_mode_layout_label)) {
            SliderPreference(
                label = stringResource(R.string.driving_mode_rows_label),
                adapter = prefs2.drivingModeRows.getAdapter(),
                step = 1,
                valueRange = 2..4,
            )
            SliderPreference(
                label = stringResource(R.string.driving_mode_columns_label),
                adapter = prefs2.drivingModeColumns.getAdapter(),
                step = 1,
                valueRange = 1..3,
            )
            SliderPreference(
                label = stringResource(R.string.driving_mode_pages_label),
                adapter = prefs2.drivingModePages.getAdapter(),
                step = 1,
                valueRange = 1..5,
            )
        }

        PreferenceGroup(heading = stringResource(R.string.driving_mode_style_label)) {
            ColorPreference(preference = prefs2.drivingModeBackgroundColor)
            DrivingModeBackgroundImagePreference()
            SliderPreference(
                label = stringResource(id = R.string.background_opacity),
                adapter = prefs2.drivingModeBackgroundOpacity.getAdapter(),
                step = 0.1f,
                valueRange = 0F..1F,
                showAsPercentage = true,
            )
        }

        PreferenceGroup {
            SwitchPreference(
                adapter = prefs2.drivingModeSpeedUnitMph.getAdapter(),
                label = stringResource(R.string.driving_mode_speed_unit_mph_label),
                description = stringResource(R.string.driving_mode_speed_unit_mph_description),
            )
        }
    }
}

@Composable
private fun DrivingModeBackgroundImagePreference() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val adapter = preferenceManager2().drivingModeBackgroundImage.getAdapter()
    val fileName = adapter.state.value
    val hasImage = fileName.isNotEmpty()

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val newFileName = withContext(Dispatchers.IO) {
                DrawerBackgroundImageStore.saveImage(context, uri)
            } ?: return@launch
            val previousFileName = fileName
            adapter.onChange(newFileName)
            if (previousFileName.isNotEmpty()) {
                withContext(Dispatchers.IO) { DrawerBackgroundImageStore.deleteImage(context, previousFileName) }
            }
        }
    }

    ClickablePreference(
        label = stringResource(id = R.string.app_drawer_background_image),
        subtitle = stringResource(
            id = if (hasImage) R.string.app_drawer_background_image_set else R.string.app_drawer_background_image_none,
        ),
        onClick = { pickImageLauncher.launch("image/*") },
    )
    ExpandAndShrink(visible = hasImage) {
        ClickablePreference(
            label = stringResource(id = R.string.app_drawer_background_image_remove),
            onClick = {
                scope.launch {
                    adapter.onChange("")
                    withContext(Dispatchers.IO) { DrawerBackgroundImageStore.deleteImage(context, fileName) }
                }
            },
        )
    }
}

@Suppress("MissingPermission") // permission checked by caller / not needed pre-S
private fun getPairedDevices(context: Context): List<BluetoothDevice> {
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val adapter: BluetoothAdapter? = bluetoothManager.adapter
    if (adapter == null || !adapter.isEnabled) return emptyList()
    return adapter.bondedDevices.toList()
}
