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

package com.android.systemui.shared.system;

import static android.view.WindowManagerPolicyConstants.NAV_BAR_MODE_2BUTTON;
import static android.view.WindowManagerPolicyConstants.NAV_BAR_MODE_3BUTTON;
import static android.view.WindowManagerPolicyConstants.NAV_BAR_MODE_GESTURAL;

import static com.android.systemui.shared.Flags.shadeAllowBackGesture;

import android.annotation.LongDef;
import android.content.Context;
import android.content.res.Resources;
import android.view.ViewConfiguration;
import android.view.WindowManagerPolicyConstants;

import com.android.internal.policy.ScreenDecorationsUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.StringJoiner;

/**
 * Various shared constants between Launcher and SysUI as part of quickstep
 */
public class QuickStepContract {

    public static final String KEY_EXTRA_SYSUI_PROXY = "extra_sysui_proxy";
    public static final String KEY_EXTRA_UNFOLD_ANIMATION_FORWARDER = "extra_unfold_animation";
    // See ISysuiUnlockAnimationController.aidl
    public static final String KEY_EXTRA_UNLOCK_ANIMATION_CONTROLLER = "unlock_animation";

    public static final String NAV_BAR_MODE_3BUTTON_OVERLAY =
            WindowManagerPolicyConstants.NAV_BAR_MODE_3BUTTON_OVERLAY;
    public static final String NAV_BAR_MODE_GESTURAL_OVERLAY =
            WindowManagerPolicyConstants.NAV_BAR_MODE_GESTURAL_OVERLAY;

    // Overview is disabled, either because the device is in lock task mode, or because the device
    // policy has disabled the feature
    public static final long SYSUI_STATE_SCREEN_PINNING = 1L << 0;
    // The navigation bar is hidden due to immersive mode
    public static final long SYSUI_STATE_NAV_BAR_HIDDEN = 1L << 1;
    // The notification panel is expanded and interactive (either locked or unlocked), and the
    // quick settings is not expanded
    public static final long SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED = 1L << 2;
    // The keyguard bouncer is showing
    public static final long SYSUI_STATE_BOUNCER_SHOWING = 1L << 3;
    // The navigation bar a11y button should be shown
    public static final long SYSUI_STATE_A11Y_BUTTON_CLICKABLE = 1L << 4;
    // The navigation bar a11y button shortcut is available
    public static final long SYSUI_STATE_A11Y_BUTTON_LONG_CLICKABLE = 1L << 5;
    // The keyguard is showing and not occluded
    public static final long SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING = 1L << 6;
    // The recents feature is disabled (either by SUW/SysUI/device policy)
    public static final long SYSUI_STATE_OVERVIEW_DISABLED = 1L << 7;
    // The home feature is disabled (either by SUW/SysUI/device policy)
    public static final long SYSUI_STATE_HOME_DISABLED = 1L << 8;
    // The keyguard is showing, but occluded
    public static final long SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED = 1L << 9;
    // The search feature is disabled (either by SUW/SysUI/device policy)
    public static final long SYSUI_STATE_SEARCH_DISABLED = 1L << 10;
    // The notification panel is expanded and interactive (either locked or unlocked), and quick
    // settings is expanded.
    public static final long SYSUI_STATE_QUICK_SETTINGS_EXPANDED = 1L << 11;
    // Winscope tracing is enabled
    public static final long SYSUI_STATE_DISABLE_GESTURE_SPLIT_INVOCATION = 1L << 12;
    // The Assistant gesture should be constrained. It is up to the launcher implementation to
    // decide how to constrain it
    public static final long SYSUI_STATE_ASSIST_GESTURE_CONSTRAINED = 1L << 13;
    // The bubble stack is expanded. This means that the home gesture should be ignored, since a
    // swipe up is an attempt to close the bubble stack, but that the back gesture should remain
    // enabled (since it's used to navigate back within the bubbled app, or to collapse the bubble
    // stack.
    public static final long SYSUI_STATE_BUBBLES_EXPANDED = 1L << 14;
    // A SysUI dialog is showing.
    public static final long SYSUI_STATE_DIALOG_SHOWING = 1L << 15;
    // The one-handed mode is active
    public static final long SYSUI_STATE_ONE_HANDED_ACTIVE = 1L << 16;
    // Allow system gesture no matter the system bar(s) is visible or not
    public static final long SYSUI_STATE_ALLOW_GESTURE_IGNORING_BAR_VISIBILITY = 1L << 17;
    // The IME is showing
    public static final long SYSUI_STATE_IME_SHOWING = 1L << 18;
    // The window magnification is overlapped with system gesture insets at the bottom.
    public static final long SYSUI_STATE_MAGNIFICATION_OVERLAP = 1L << 19;
    // ImeSwitcher is showing
    public static final long SYSUI_STATE_IME_SWITCHER_SHOWING = 1L << 20;
    // Device dozing/AOD state
    public static final long SYSUI_STATE_DEVICE_DOZING = 1L << 21;
    // The home feature is disabled (either by SUW/SysUI/device policy)
    public static final long SYSUI_STATE_BACK_DISABLED = 1L << 22;
    // The bubble stack is expanded AND the mange menu for bubbles is expanded on top of it.
    public static final long SYSUI_STATE_BUBBLES_MANAGE_MENU_EXPANDED = 1L << 23;
    // The voice interaction session window is showing
    public static final long SYSUI_STATE_VOICE_INTERACTION_WINDOW_SHOWING = 1L << 25;
    // Freeform windows are showing in desktop mode
    public static final long SYSUI_STATE_FREEFORM_ACTIVE_IN_DESKTOP_MODE = 1L << 26;
    // Device dreaming state
    public static final long SYSUI_STATE_DEVICE_DREAMING = 1L << 27;
    // Whether the device is currently awake (as opposed to asleep, see WakefulnessLifecycle).
    // Note that the device is awake on while waking up on, but not while going to sleep.
    public static final long SYSUI_STATE_AWAKE = 1L << 28;
    // Whether the device is currently transitioning between awake/asleep indicated by
    // SYSUI_STATE_AWAKE.
    public static final long SYSUI_STATE_WAKEFULNESS_TRANSITION = 1L << 29;
    // The notification panel expansion fraction is > 0
    public static final long SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE = 1L << 30;
    // When keyguard will be dismissed but didn't start animation yet
    public static final long SYSUI_STATE_STATUS_BAR_KEYGUARD_GOING_AWAY = 1L << 31;
    // Physical keyboard shortcuts helper is showing
    public static final long SYSUI_STATE_SHORTCUT_HELPER_SHOWING = 1L << 32;
    // Touchpad gestures are disabled
    public static final long SYSUI_STATE_TOUCHPAD_GESTURES_DISABLED = 1L << 33;

    // Mask for SystemUiStateFlags to isolate SYSUI_STATE_AWAKE and
    // SYSUI_STATE_WAKEFULNESS_TRANSITION, to match WAKEFULNESS_* constants
    public static final long SYSUI_STATE_WAKEFULNESS_MASK =
            SYSUI_STATE_AWAKE | SYSUI_STATE_WAKEFULNESS_TRANSITION;
    // Mirroring the WakefulnessLifecycle#Wakefulness states
    public static final long WAKEFULNESS_ASLEEP = 0;
    public static final long WAKEFULNESS_AWAKE = SYSUI_STATE_AWAKE;
    public static final long WAKEFULNESS_GOING_TO_SLEEP = SYSUI_STATE_WAKEFULNESS_TRANSITION;
    public static final long WAKEFULNESS_WAKING =
            SYSUI_STATE_WAKEFULNESS_TRANSITION | SYSUI_STATE_AWAKE;

    // Whether the back gesture is allowed (or ignored) by the Shade
    public static final boolean ALLOW_BACK_GESTURE_IN_SHADE = shadeAllowBackGesture();

    @Retention(RetentionPolicy.SOURCE)
    @LongDef({SYSUI_STATE_SCREEN_PINNING,
            SYSUI_STATE_NAV_BAR_HIDDEN,
            SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED,
            SYSUI_STATE_QUICK_SETTINGS_EXPANDED,
            SYSUI_STATE_BOUNCER_SHOWING,
            SYSUI_STATE_A11Y_BUTTON_CLICKABLE,
            SYSUI_STATE_A11Y_BUTTON_LONG_CLICKABLE,
            SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING,
            SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED,
            SYSUI_STATE_OVERVIEW_DISABLED,
            SYSUI_STATE_HOME_DISABLED,
            SYSUI_STATE_SEARCH_DISABLED,
            SYSUI_STATE_DISABLE_GESTURE_SPLIT_INVOCATION,
            SYSUI_STATE_ASSIST_GESTURE_CONSTRAINED,
            SYSUI_STATE_BUBBLES_EXPANDED,
            SYSUI_STATE_DIALOG_SHOWING,
            SYSUI_STATE_ONE_HANDED_ACTIVE,
            SYSUI_STATE_ALLOW_GESTURE_IGNORING_BAR_VISIBILITY,
            SYSUI_STATE_IME_SHOWING,
            SYSUI_STATE_MAGNIFICATION_OVERLAP,
            SYSUI_STATE_IME_SWITCHER_SHOWING,
            SYSUI_STATE_DEVICE_DOZING,
            SYSUI_STATE_BACK_DISABLED,
            SYSUI_STATE_BUBBLES_MANAGE_MENU_EXPANDED,
            SYSUI_STATE_VOICE_INTERACTION_WINDOW_SHOWING,
            SYSUI_STATE_FREEFORM_ACTIVE_IN_DESKTOP_MODE,
            SYSUI_STATE_DEVICE_DREAMING,
            SYSUI_STATE_AWAKE,
            SYSUI_STATE_WAKEFULNESS_TRANSITION,
            SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE,
            SYSUI_STATE_STATUS_BAR_KEYGUARD_GOING_AWAY,
            SYSUI_STATE_SHORTCUT_HELPER_SHOWING,
            SYSUI_STATE_TOUCHPAD_GESTURES_DISABLED,
    })
    public @interface SystemUiStateFlags {}

    public static String getSystemUiStateString(long flags) {
        StringJoiner str = new StringJoiner("|");
        if ((flags & SYSUI_STATE_SCREEN_PINNING) != 0) {
            str.add("screen_pinned");
        }
        if ((flags & SYSUI_STATE_OVERVIEW_DISABLED) != 0) {
            str.add("overview_disabled");
        }
        if ((flags & SYSUI_STATE_HOME_DISABLED) != 0) {
            str.add("home_disabled");
        }
        if ((flags & SYSUI_STATE_SEARCH_DISABLED) != 0) {
            str.add("search_disabled");
        }
        if ((flags & SYSUI_STATE_NAV_BAR_HIDDEN) != 0) {
            str.add("navbar_hidden");
        }
        if ((flags & SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED) != 0) {
            str.add("notif_expanded");
        }
        if ((flags & SYSUI_STATE_QUICK_SETTINGS_EXPANDED) != 0) {
            str.add("qs_visible");
        }
        if ((flags & SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING) != 0) {
            str.add("keygrd_visible");
        }
        if ((flags & SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED) != 0) {
            str.add("keygrd_occluded");
        }
        if ((flags & SYSUI_STATE_BOUNCER_SHOWING) != 0) {
            str.add("bouncer_visible");
        }
        if ((flags & SYSUI_STATE_DIALOG_SHOWING) != 0) {
            str.add("dialog_showing");
        }
        if ((flags & SYSUI_STATE_A11Y_BUTTON_CLICKABLE) != 0) {
            str.add("a11y_click");
        }
        if ((flags & SYSUI_STATE_A11Y_BUTTON_LONG_CLICKABLE) != 0) {
            str.add("a11y_long_click");
        }
        if ((flags & SYSUI_STATE_DISABLE_GESTURE_SPLIT_INVOCATION) != 0) {
            str.add("disable_gesture_split_invocation");
        }
        if ((flags & SYSUI_STATE_ASSIST_GESTURE_CONSTRAINED) != 0) {
            str.add("asst_gesture_constrain");
        }
        if ((flags & SYSUI_STATE_BUBBLES_EXPANDED) != 0) {
            str.add("bubbles_expanded");
        }
        if ((flags & SYSUI_STATE_ONE_HANDED_ACTIVE) != 0) {
            str.add("one_handed_active");
        }
        if ((flags & SYSUI_STATE_ALLOW_GESTURE_IGNORING_BAR_VISIBILITY) != 0) {
            str.add("allow_gesture");
        }
        if ((flags & SYSUI_STATE_IME_SHOWING) != 0) {
            str.add("ime_visible");
        }
        if ((flags & SYSUI_STATE_MAGNIFICATION_OVERLAP) != 0) {
            str.add("magnification_overlap");
        }
        if ((flags & SYSUI_STATE_IME_SWITCHER_SHOWING) != 0) {
            str.add("ime_switcher_showing");
        }
        if ((flags & SYSUI_STATE_DEVICE_DOZING) != 0) {
            str.add("device_dozing");
        }
        if ((flags & SYSUI_STATE_BACK_DISABLED) != 0) {
            str.add("back_disabled");
        }
        if ((flags & SYSUI_STATE_BUBBLES_MANAGE_MENU_EXPANDED) != 0) {
            str.add("bubbles_mange_menu_expanded");
        }
        if ((flags & SYSUI_STATE_VOICE_INTERACTION_WINDOW_SHOWING) != 0) {
            str.add("vis_win_showing");
        }
        if ((flags & SYSUI_STATE_FREEFORM_ACTIVE_IN_DESKTOP_MODE) != 0) {
            str.add("freeform_active_in_desktop_mode");
        }
        if ((flags & SYSUI_STATE_DEVICE_DREAMING) != 0) {
            str.add("device_dreaming");
        }
        if ((flags & SYSUI_STATE_WAKEFULNESS_TRANSITION) != 0) {
            str.add("wakefulness_transition");
        }
        if ((flags & SYSUI_STATE_AWAKE) != 0) {
            str.add("awake");
        }
        if ((flags & SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE) != 0) {
            str.add("notif_visible");
        }
        if ((flags & SYSUI_STATE_STATUS_BAR_KEYGUARD_GOING_AWAY) != 0) {
            str.add("keygrd_going_away");
        }
        if ((flags & SYSUI_STATE_SHORTCUT_HELPER_SHOWING) != 0) {
            str.add("shortcut_helper_showing");
        }
        if ((flags & SYSUI_STATE_TOUCHPAD_GESTURES_DISABLED) != 0) {
            str.add("touchpad_gestures_disabled");
        }

        return str.toString();
    }

    /**
     * Ratio of quickstep touch slop (when system takes over the touch) to view touch slop
     */
    public static final float QUICKSTEP_TOUCH_SLOP_RATIO = 3;

    /**
     * Touch slop for quickstep gesture
     */
    public static final float getQuickStepTouchSlopPx(Context context) {
        return QUICKSTEP_TOUCH_SLOP_RATIO * ViewConfiguration.get(context).getScaledTouchSlop();
    }

    /**
     * Returns whether the specified sysui state is such that the assistant gesture should be
     * disabled.
     */
    public static boolean isAssistantGestureDisabled(long sysuiStateFlags) {
        if ((sysuiStateFlags & SYSUI_STATE_ALLOW_GESTURE_IGNORING_BAR_VISIBILITY) != 0) {
            sysuiStateFlags &= ~SYSUI_STATE_NAV_BAR_HIDDEN;
        }
        // Disable when in quick settings, screen pinning, immersive, the bouncer is showing, 
        // or search is disabled
        long disableFlags = SYSUI_STATE_SCREEN_PINNING
                | SYSUI_STATE_NAV_BAR_HIDDEN
                | SYSUI_STATE_BOUNCER_SHOWING
                | SYSUI_STATE_SEARCH_DISABLED
                | SYSUI_STATE_QUICK_SETTINGS_EXPANDED;
        if ((sysuiStateFlags & disableFlags) != 0) {
            return true;
        }

        // Disable when notifications are showing (only if unlocked)
        if ((sysuiStateFlags & SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED) != 0
                && (sysuiStateFlags & SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING) == 0) {
            return true;
        }

        return false;
    }

    /**
     * Returns whether the specified sysui state is such that the back gesture should be
     * disabled.
     */
    public static boolean isBackGestureDisabled(long sysuiStateFlags, boolean forTrackpad) {
        // Always allow when the bouncer/global actions/voice session is showing (even on top of
        // the keyguard)
        if ((sysuiStateFlags & SYSUI_STATE_BOUNCER_SHOWING) != 0
                || (sysuiStateFlags & SYSUI_STATE_DIALOG_SHOWING) != 0
                || (sysuiStateFlags & SYSUI_STATE_VOICE_INTERACTION_WINDOW_SHOWING) != 0) {
            return false;
        }
        if ((sysuiStateFlags & SYSUI_STATE_ALLOW_GESTURE_IGNORING_BAR_VISIBILITY) != 0) {
            sysuiStateFlags &= ~SYSUI_STATE_NAV_BAR_HIDDEN;
        }

        return (sysuiStateFlags & getBackGestureDisabledMask(forTrackpad)) != 0;
    }

    private static long getBackGestureDisabledMask(boolean forTrackpad) {
        // Disable when in immersive, or the notifications are interactive
        long disableFlags = SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING;
        if (!forTrackpad) {
            disableFlags |= SYSUI_STATE_NAV_BAR_HIDDEN;
        }

        // EdgeBackGestureHandler ignores Back gesture when SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED.
        // To allow Shade to respond to Back, we're bypassing this check (behind a flag).
        if (!ALLOW_BACK_GESTURE_IN_SHADE) {
            disableFlags |= SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED;
        }
        return disableFlags;
    }

    /**
     * @return whether this nav bar mode is edge to edge
     */
    public static boolean isGesturalMode(int mode) {
        return mode == NAV_BAR_MODE_GESTURAL;
    }

    /**
     * @return whether this nav bar mode is swipe up
     */
    public static boolean isSwipeUpMode(int mode) {
        return mode == NAV_BAR_MODE_2BUTTON;
    }

    /**
     * @return whether this nav bar mode is 3 button
     */
    public static boolean isLegacyMode(int mode) {
        return mode == NAV_BAR_MODE_3BUTTON;
    }

    /**
     * Corner radius that should be used on windows in order to cover the display.
     * These values are expressed in pixels because they should not respect display or font
     * scaling. The corner radius may change when folding/unfolding the device.
     */
    public static float getWindowCornerRadius(Context context) {
        return ScreenDecorationsUtils.getWindowCornerRadius(context);
    }

    /**
     * If live rounded corners are supported on windows.
     */
    public static boolean supportsRoundedCornersOnWindows(Resources resources) {
        return ScreenDecorationsUtils.supportsRoundedCornersOnWindows(resources);
    }
}
