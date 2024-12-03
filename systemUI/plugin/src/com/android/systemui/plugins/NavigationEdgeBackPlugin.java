/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.systemui.plugins;

import android.graphics.Point;
import android.view.MotionEvent;
import android.view.WindowManager;

import com.android.systemui.plugins.annotations.ProvidesInterface;

import java.io.PrintWriter;

/** Plugin to handle navigation edge gestures for Back. */
@ProvidesInterface(
        action = NavigationEdgeBackPlugin.ACTION,
        version = NavigationEdgeBackPlugin.VERSION)
public interface NavigationEdgeBackPlugin extends Plugin {
    String ACTION = "com.android.systemui.action.PLUGIN_NAVIGATION_EDGE_BACK_ACTION";
    int VERSION = 1;


    /** Specifies if the UI should be rendered on the left side of the screen. */
    void setIsLeftPanel(boolean isLeftPanel);

    /** Sets the insets for the gesture handling area. */
    void setInsets(int leftInset, int rightInset);

    /** Sets the display size. */
    void setDisplaySize(Point displaySize);

    /** Sets the callback that should be invoked when a Back gesture is detected. */
    void setBackCallback(BackCallback callback);

    /** Sets the base LayoutParams for the UI. */
    void setLayoutParams(WindowManager.LayoutParams layoutParams);

    /** Updates the UI based on the motion events passed in device coordinates. */
    void onMotionEvent(MotionEvent motionEvent);

    /** Dumps info about the back gesture plugin. */
    void dump(PrintWriter pw);

    /** Callback to let the system react to the detected back gestures. */
    interface BackCallback {
        /** Indicates that a Back gesture was recognized and the system should go back. */
        void triggerBack();

        /** Indicates that the gesture was cancelled and the system should not go back. */
        void cancelBack();

        /**
         * Indicates if back will be triggered if committed in current state.
         *
         * @param triggerBack if back will be triggered in current state.
         */
        void setTriggerBack(boolean triggerBack);
    }
}
