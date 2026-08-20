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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import app.lawnchair.drivingmode.DrivingModeController
import app.lawnchair.drivingmode.DrivingModePrefs
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.R

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
    }
}

@Suppress("MissingPermission") // permission checked by caller / not needed pre-S
private fun getPairedDevices(context: Context): List<BluetoothDevice> {
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val adapter: BluetoothAdapter? = bluetoothManager.adapter
    if (adapter == null || !adapter.isEnabled) return emptyList()
    return adapter.bondedDevices.toList()
}
