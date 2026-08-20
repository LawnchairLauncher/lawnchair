package app.lawnchair.drivingmode

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.lawnchair.ui.theme.LawnchairTheme

/**
 * Standalone settings screen (its own app-drawer entry, see AndroidManifest)
 * for picking which paired Bluetooth device triggers driving mode.
 */
class DrivingModeSettingsActivity : ComponentActivity() {

    private val requestBluetoothPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) recreate()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            requestBluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }

        setContent {
            LawnchairTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DrivingModeSettingsScreen(this)
                }
            }
        }
    }
}

@Composable
private fun DrivingModeSettingsScreen(context: Context) {
    var currentTarget by remember { mutableStateOf(DrivingModePrefs.getTargetDeviceAddress(context)) }
    val pairedDevices = remember { getPairedDevices(context) }

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
        Text(
            text = if (pairedDevices == null) {
                "Turn Bluetooth on, then reopen this screen."
            } else if (currentTarget != null) {
                "Currently watching: $currentTarget\nTap a device below to change it."
            } else {
                "Tap your car stereo below to select it."
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
            items(pairedDevices.orEmpty()) { device ->
                Text(
                    text = "${device.name}  (${device.address})",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            DrivingModePrefs.setTargetDeviceAddress(context, device.address)
                            currentTarget = device.address
                        }
                        .padding(vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Text(
            text = "Manual test (no Bluetooth needed):",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Button(onClick = { DrivingModeController.simulateConnect() }) {
                Text("Show driving mode")
            }
            OutlinedButton(onClick = { DrivingModeController.simulateDisconnect() }) {
                Text("Hide driving mode")
            }
        }
    }
}

@Suppress("MissingPermission") // checked by caller / not needed pre-S
private fun getPairedDevices(context: Context): List<BluetoothDevice>? {
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val adapter: BluetoothAdapter? = bluetoothManager.adapter
    if (adapter == null || !adapter.isEnabled) return null
    return adapter.bondedDevices.toList()
}
