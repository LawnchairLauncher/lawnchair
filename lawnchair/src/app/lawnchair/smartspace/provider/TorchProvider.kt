package app.lawnchair.smartspace.provider

import android.content.Context
import android.graphics.drawable.Icon
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.getSystemService
import app.lawnchair.smartspace.model.SmartspaceAction
import app.lawnchair.smartspace.model.SmartspaceScores
import app.lawnchair.smartspace.model.SmartspaceTarget
import com.android.launcher3.R
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map

class TorchProvider(context: Context) :
    SmartspaceDataSource(
        context,
        R.string.smartspace_torch,
        { smartspaceTorch },
    ) {
    private val cameraManager = context.getSystemService<CameraManager>()

    override val internalTargets = torchFlow()
        .map { enabled -> listOfNotNull(if (enabled) getSmartspaceTarget() else null) }

    private fun torchFlow(): Flow<Boolean> = callbackFlow {
        if (cameraManager == null) {
            trySend(false)
            close()
            return@callbackFlow
        }

        // Collect all flash-capable camera IDs
        val flashCameraIds = cameraManager.cameraIdList
            .filter { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            .toSet()

        if (flashCameraIds.isEmpty()) {
            trySend(false)
            close()
            return@callbackFlow
        }

        val enabledCameraIds = mutableSetOf<String>()

        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(id: String, enabled: Boolean) {
                if (id in flashCameraIds) {
                    if (enabled) {
                        enabledCameraIds.add(id)
                    } else {
                        enabledCameraIds.remove(id)
                    }
                    trySend(enabledCameraIds.intersect(flashCameraIds).isNotEmpty())
                }
            }

            override fun onTorchModeUnavailable(id: String) {
                if (id in flashCameraIds) {
                    enabledCameraIds.remove(id)
                    trySend(enabledCameraIds.intersect(flashCameraIds).isNotEmpty())
                }
            }
        }

        cameraManager.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { cameraManager.unregisterTorchCallback(callback) }
    }

    private fun getSmartspaceTarget(): SmartspaceTarget {
        return SmartspaceTarget(
            id = "torchStatus",
            headerAction = SmartspaceAction(
                id = "torchStatusAction",
                icon = Icon.createWithResource(context, R.drawable.ic_flashlight_off),
                title = context.getString(R.string.torch_status_on),
                subtitle = context.getString(R.string.torch_action_off),
                onClick = Runnable {
                    val cameraManager = cameraManager ?: return@Runnable
                    try {
                        cameraManager.cameraIdList.forEach { cameraId ->
                            try {
                                val hasFlash = cameraManager
                                    .getCameraCharacteristics(cameraId)
                                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                                if (hasFlash) {
                                    cameraManager.setTorchMode(cameraId, false)
                                }
                            } catch (e: CameraAccessException) {
                                Log.e(
                                    TAG,
                                    "Failed to turn off torch for camera $cameraId: ${e.message}",
                                    e,
                                )
                            }
                        }
                    } catch (e: CameraAccessException) {
                        Log.e(TAG, "Failed to access camera list", e)
                    }
                },
            ),
            score = SmartspaceScores.SCORE_FLASHLIGHT,
            featureType = SmartspaceTarget.FeatureType.FEATURE_FLASHLIGHT,
        )
    }

    companion object {
        private const val TAG = "TorchProvider"
    }
}
