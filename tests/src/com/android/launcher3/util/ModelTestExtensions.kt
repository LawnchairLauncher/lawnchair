package com.android.launcher3.util

import com.android.launcher3.LauncherModel
import com.android.launcher3.model.BgDataModel

object ModelTestExtensions {
    /** Clears and reloads Launcher db to cleanup the workspace */
    fun LauncherModel.clearModelDb() {
        // Load the model once so that there is no pending migration:
        loadModelSync()
        TestUtil.runOnExecutorSync(Executors.MODEL_EXECUTOR) {
            modelDbController.run {
                tryMigrateDB()
                createEmptyDB()
                clearEmptyDbFlag()
            }
        }
        // Reload model
        TestUtil.runOnExecutorSync(Executors.MAIN_EXECUTOR) { forceReload() }
        loadModelSync()
    }

    fun LauncherModel.loadModelSync() {
        val mockCb: BgDataModel.Callbacks = object : BgDataModel.Callbacks {}
        TestUtil.runOnExecutorSync(Executors.MAIN_EXECUTOR) { addCallbacksAndLoad(mockCb) }
        TestUtil.runOnExecutorSync(Executors.MODEL_EXECUTOR) {}
        TestUtil.runOnExecutorSync(Executors.MAIN_EXECUTOR) {}
        TestUtil.runOnExecutorSync(Executors.MAIN_EXECUTOR) { removeCallbacks(mockCb) }
    }
}
