/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.systemui.shared.recents.utilities;

import static android.app.StatusBarManager.NAVBAR_BACK_DISMISS_IME;
import static android.app.StatusBarManager.NAVBAR_IME_SWITCHER_BUTTON_VISIBLE;
import static android.app.StatusBarManager.NAVBAR_IME_VISIBLE;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;

import android.annotation.TargetApi;
import android.app.StatusBarManager.NavbarFlags;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.InputMethodService.BackDispositionMode;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.view.WindowManager;

import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.utils.windowmanager.WindowManagerUtils;

/* Common code */
public class Utilities {

    private static final float TABLET_MIN_DPS = 600;

    /**
     * Posts a runnable on a handler at the front of the queue ignoring any sync barriers.
     */
    public static void postAtFrontOfQueueAsynchronously(Handler h, Runnable r) {
        Message msg = h.obtainMessage().setCallback(r);
        h.sendMessageAtFrontOfQueue(msg);
    }

    public static boolean isRotationAnimationCCW(int from, int to) {
        // All 180deg WM rotation animations are CCW, match that
        if (from == Surface.ROTATION_0 && to == Surface.ROTATION_90) return false;
        if (from == Surface.ROTATION_0 && to == Surface.ROTATION_180) return true; //180d so CCW
        if (from == Surface.ROTATION_0 && to == Surface.ROTATION_270) return true;
        if (from == Surface.ROTATION_90 && to == Surface.ROTATION_0) return true;
        if (from == Surface.ROTATION_90 && to == Surface.ROTATION_180) return false;
        if (from == Surface.ROTATION_90 && to == Surface.ROTATION_270) return true; //180d so CCW
        if (from == Surface.ROTATION_180 && to == Surface.ROTATION_0) return true; //180d so CCW
        if (from == Surface.ROTATION_180 && to == Surface.ROTATION_90) return true;
        if (from == Surface.ROTATION_180 && to == Surface.ROTATION_270) return false;
        if (from == Surface.ROTATION_270 && to == Surface.ROTATION_0) return false;
        if (from == Surface.ROTATION_270 && to == Surface.ROTATION_90) return true; //180d so CCW
        if (from == Surface.ROTATION_270 && to == Surface.ROTATION_180) return true;
        return false; // Default
    }

    /**
     * Compares the ratio of two quantities and returns whether that ratio is greater than the
     * provided bound. Order of quantities does not matter. Bound should be a decimal representation
     * of a percentage.
     */
    public static boolean isRelativePercentDifferenceGreaterThan(float first, float second,
            float bound) {
        return (Math.abs(first - second) / Math.abs((first + second) / 2.0f)) > bound;
    }

    /** Calculates the constrast between two colors, using the algorithm provided by the WCAG v2. */
    public static float computeContrastBetweenColors(int bg, int fg) {
        float bgR = Color.red(bg) / 255f;
        float bgG = Color.green(bg) / 255f;
        float bgB = Color.blue(bg) / 255f;
        bgR = (bgR < 0.03928f) ? bgR / 12.92f : (float) Math.pow((bgR + 0.055f) / 1.055f, 2.4f);
        bgG = (bgG < 0.03928f) ? bgG / 12.92f : (float) Math.pow((bgG + 0.055f) / 1.055f, 2.4f);
        bgB = (bgB < 0.03928f) ? bgB / 12.92f : (float) Math.pow((bgB + 0.055f) / 1.055f, 2.4f);
        float bgL = 0.2126f * bgR + 0.7152f * bgG + 0.0722f * bgB;

        float fgR = Color.red(fg) / 255f;
        float fgG = Color.green(fg) / 255f;
        float fgB = Color.blue(fg) / 255f;
        fgR = (fgR < 0.03928f) ? fgR / 12.92f : (float) Math.pow((fgR + 0.055f) / 1.055f, 2.4f);
        fgG = (fgG < 0.03928f) ? fgG / 12.92f : (float) Math.pow((fgG + 0.055f) / 1.055f, 2.4f);
        fgB = (fgB < 0.03928f) ? fgB / 12.92f : (float) Math.pow((fgB + 0.055f) / 1.055f, 2.4f);
        float fgL = 0.2126f * fgR + 0.7152f * fgG + 0.0722f * fgB;

        return Math.abs((fgL + 0.05f) / (bgL + 0.05f));
    }

    /**
     * @return the clamped {@param value} between the provided {@param min} and {@param max}.
     */
    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Updates the navigation bar state flags with the given IME state.
     *
     * @param oldFlags        current navigation bar state flags.
     * @param backDisposition the IME back disposition mode. Only takes effect if
     *                        {@code isImeVisible} is {@code true}.
     * @param isImeVisible    whether the IME is currently visible.
     * @param showImeSwitcher whether the IME Switcher button should be shown. Only takes effect if
     *                        {@code isImeVisible} is {@code true}.
     */
    @NavbarFlags
    public static int updateNavbarFlagsFromIme(@NavbarFlags int oldFlags,
            @BackDispositionMode int backDisposition, boolean isImeVisible,
            boolean showImeSwitcher) {
        int flags = oldFlags;
        switch (backDisposition) {
            case InputMethodService.BACK_DISPOSITION_DEFAULT:
            case InputMethodService.BACK_DISPOSITION_WILL_NOT_DISMISS:
            case InputMethodService.BACK_DISPOSITION_WILL_DISMISS:
                if (isImeVisible) {
                    flags |= NAVBAR_BACK_DISMISS_IME;
                } else {
                    flags &= ~NAVBAR_BACK_DISMISS_IME;
                }
                break;
            case InputMethodService.BACK_DISPOSITION_ADJUST_NOTHING:
                flags &= ~NAVBAR_BACK_DISMISS_IME;
                break;
        }
        if (isImeVisible) {
            flags |= NAVBAR_IME_VISIBLE;
        } else {
            flags &= ~NAVBAR_IME_VISIBLE;
        }
        if (showImeSwitcher && isImeVisible) {
            flags |= NAVBAR_IME_SWITCHER_BUTTON_VISIBLE;
        } else {
            flags &= ~NAVBAR_IME_SWITCHER_BUTTON_VISIBLE;
        }

        return flags;
    }

    /** @return whether or not {@param context} represents that of a large screen device or not */
    @TargetApi(Build.VERSION_CODES.R)
    public static boolean isLargeScreen(Context context) {
        return isLargeScreen(WindowManagerUtils.getWindowManager(context), context.getResources());
    }

    /** @return whether or not {@param context} represents that of a large screen device or not */
    public static boolean isLargeScreen(WindowManager windowManager, Resources resources) {
        final Rect bounds = windowManager.getCurrentWindowMetrics().getBounds();

        float smallestWidth = dpiFromPx(Math.min(bounds.width(), bounds.height()),
                resources.getConfiguration().densityDpi);
        return smallestWidth >= TABLET_MIN_DPS;
    }

    public static float dpiFromPx(float size, int densityDpi) {
        float densityRatio = (float) densityDpi / DisplayMetrics.DENSITY_DEFAULT;
        return (size / densityRatio);
    }

    /** Whether a task is in freeform mode. */
    public static boolean isFreeformTask(Task task) {
        return task != null && task.getKey() != null
                && task.getKey().windowingMode == WINDOWING_MODE_FREEFORM;
    }
}
