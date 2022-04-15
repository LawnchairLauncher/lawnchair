package app.lawnchair.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.android.launcher3.InvariantDeviceProfile

@Composable
fun rememberGridOption(): InvariantDeviceProfile.GridOption {
    val context = LocalContext.current
    return remember { InvariantDeviceProfile.INSTANCE.get(context).closestProfile }
}
