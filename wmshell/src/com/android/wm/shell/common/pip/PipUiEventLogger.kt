/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.wm.shell.common.pip

import android.app.TaskInfo
import android.content.pm.PackageManager
import com.android.internal.logging.UiEvent
import com.android.internal.logging.UiEventLogger

/**
 * Helper class that ends PiP log to UiEvent, see also go/uievent
 */
class PipUiEventLogger(
    private val mUiEventLogger: UiEventLogger,
    private val mPackageManager: PackageManager
) {
    private var mPackageName: String? = null
    private var mPackageUid = INVALID_PACKAGE_UID
    fun setTaskInfo(taskInfo: TaskInfo?) {
        if (taskInfo?.topActivity != null) {
            // safe because topActivity is guaranteed non-null here
            mPackageName = taskInfo.topActivity!!.packageName
            mPackageUid = getUid(mPackageName!!, taskInfo.userId)
        } else {
            mPackageName = null
            mPackageUid = INVALID_PACKAGE_UID
        }
    }

    /**
     * Sends log via UiEvent, reference go/uievent for how to debug locally
     */
    fun log(event: PipUiEventEnum?) {
        if (mPackageName == null || mPackageUid == INVALID_PACKAGE_UID) {
            return
        }
        mUiEventLogger.log(event!!, mPackageUid, mPackageName)
    }

    private fun getUid(packageName: String, userId: Int): Int {
        var uid = INVALID_PACKAGE_UID
        try {
            uid = mPackageManager.getApplicationInfoAsUser(
                packageName, 0 /* ApplicationInfoFlags */, userId
            ).uid
        } catch (e: PackageManager.NameNotFoundException) {
            // do nothing.
        }
        return uid
    }

    /**
     * Enums for logging the PiP events to UiEvent
     */
    enum class PipUiEventEnum(private val mId: Int) : UiEventLogger.UiEventEnum {
        @UiEvent(doc = "Activity enters picture-in-picture mode")
        PICTURE_IN_PICTURE_ENTER(603),

        @UiEvent(doc = "Activity enters picture-in-picture mode with auto-enter-pip API")
        PICTURE_IN_PICTURE_AUTO_ENTER(1313),

        @UiEvent(doc = "Activity enters picture-in-picture mode from content-pip API")
        PICTURE_IN_PICTURE_ENTER_CONTENT_PIP(1314),

        @UiEvent(doc = "Expands from picture-in-picture to fullscreen")
        PICTURE_IN_PICTURE_EXPAND_TO_FULLSCREEN(604),

        @UiEvent(doc = "Removes picture-in-picture by tap close button")
        PICTURE_IN_PICTURE_TAP_TO_REMOVE(605),

        @UiEvent(doc = "Removes picture-in-picture by drag to dismiss area")
        PICTURE_IN_PICTURE_DRAG_TO_REMOVE(606),

        @UiEvent(doc = "Shows picture-in-picture menu")
        PICTURE_IN_PICTURE_SHOW_MENU(607),

        @UiEvent(doc = "Hides picture-in-picture menu")
        PICTURE_IN_PICTURE_HIDE_MENU(608),

        @UiEvent(
            doc = "Changes the aspect ratio of picture-in-picture window. This is inherited" +
                    " from previous Tron-based logging and currently not in use."
        )
        PICTURE_IN_PICTURE_CHANGE_ASPECT_RATIO(609),

        @UiEvent(doc = "User resize of the picture-in-picture window")
        PICTURE_IN_PICTURE_RESIZE(610),

        @UiEvent(doc = "User unstashed picture-in-picture")
        PICTURE_IN_PICTURE_STASH_UNSTASHED(709),

        @UiEvent(doc = "User stashed picture-in-picture to the left side")
        PICTURE_IN_PICTURE_STASH_LEFT(710),

        @UiEvent(doc = "User stashed picture-in-picture to the right side")
        PICTURE_IN_PICTURE_STASH_RIGHT(711),

        @UiEvent(doc = "User taps on the settings button in PiP menu")
        PICTURE_IN_PICTURE_SHOW_SETTINGS(933),

        @UiEvent(doc = "Closes PiP with app-provided close action")
        PICTURE_IN_PICTURE_CUSTOM_CLOSE(1058);

        override fun getId(): Int {
            return mId
        }
    }

    companion object {
        private const val INVALID_PACKAGE_UID = -1
    }
}