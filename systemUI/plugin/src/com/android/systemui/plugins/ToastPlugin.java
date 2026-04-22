/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.animation.Animator;
import android.annotation.NonNull;
import android.view.View;

import com.android.systemui.plugins.annotations.ProvidesInterface;

/**
 * Customize toasts displayed by SystemUI (via Toast#makeText)
 */
@ProvidesInterface(action = ToastPlugin.ACTION, version = ToastPlugin.VERSION)
public interface ToastPlugin extends Plugin {

    String ACTION = "com.android.systemui.action.PLUGIN_TOAST";
    int VERSION = 1;

    /**
     * Creates a CustomPluginToast.
     */
    @NonNull Toast createToast(CharSequence text, String packageName, int userId);

    /**
     * Custom Toast with the ability to change toast positioning, styling and animations.
     */
    interface Toast {
        /**
         * Retrieve the Toast view's gravity.
         * If no changes, returns null.
         */
        default Integer getGravity() {
            return null;
        }

        /**
         * Retrieve the Toast view's X-offset.
         * If no changes, returns null.
         */
        default Integer getXOffset() {
            return null;
        }

        /**
         * Retrieve the Toast view's Y-offset.
         * If no changes, returns null.
         */
        default Integer getYOffset() {
            return null;
        }

        /**
         * Retrieve the Toast view's horizontal margin.
         * If no changes, returns null.
         */
        default Integer getHorizontalMargin()  {
            return null;
        }

        /**
         * Retrieve the Toast view's vertical margin.
         * If no changes, returns null.
         */
        default Integer getVerticalMargin()  {
            return null;
        }

        /**
         * Retrieve the Toast view to show.
         * If no changes, returns null.
         */
        default View getView() {
            return null;
        }

        /**
         * Retrieve the Toast's animate in.
         * If no changes, returns null.
         */
        default Animator getInAnimation() {
            return null;
        }

        /**
         * Retrieve the Toast's animate out.
         * If no changes, returns null.
         */
        default Animator getOutAnimation() {
            return null;
        }

        /**
         * Called on orientation changes.
         */
        default void onOrientationChange(int orientation) {  }
    }
}
