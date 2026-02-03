/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.transition;

import static android.app.ActivityOptions.ANIM_FROM_STYLE;
import static android.app.ActivityOptions.ANIM_NONE;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_CLOSE_PREPARE_BACK_NAVIGATION;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_PREPARE_BACK_NAVIGATION;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;
import static android.window.TransitionInfo.FLAGS_IS_NON_APP_WINDOW;
import static android.window.TransitionInfo.FLAG_IS_DISPLAY;
import static android.window.TransitionInfo.FLAG_TRANSLUCENT;

import static com.android.internal.policy.TransitionAnimation.WALLPAPER_TRANSITION_CLOSE;
import static com.android.internal.policy.TransitionAnimation.WALLPAPER_TRANSITION_INTRA_CLOSE;
import static com.android.internal.policy.TransitionAnimation.WALLPAPER_TRANSITION_INTRA_OPEN;
import static com.android.internal.policy.TransitionAnimation.WALLPAPER_TRANSITION_OPEN;
import static com.android.wm.shell.transition.Transitions.transitTypeToString;

import android.annotation.ColorInt;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.WindowConfiguration;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Rect;
import android.util.SparseArray;
import android.view.InsetsSource;
import android.view.InsetsState;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.window.TransitionInfo;

import com.android.internal.R;
import com.android.internal.policy.TransitionAnimation;
import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.TransitionUtil;

/** The helper class that provides methods for adding styles to transition animations. */
public class TransitionAnimationHelper {

    /** Loads the animation that is defined through attribute id for the given transition. */
    @Nullable
    public static Animation loadAttributeAnimation(@WindowManager.TransitionType int type,
            @NonNull TransitionInfo info,
            @NonNull TransitionInfo.Change change, int wallpaperTransit,
            @NonNull TransitionAnimation transitionAnimation, boolean isDreamTransition) {
        final int changeMode = change.getMode();
        final int changeFlags = change.getFlags();
        final boolean enter = TransitionUtil.isOpeningType(changeMode);
        final boolean isTask = change.getTaskInfo() != null;
        final boolean isFreeform = isTask && change.getTaskInfo().isFreeform();
        final boolean isCoveredByOpaqueFullscreenChange =
                isCoveredByOpaqueFullscreenChange(info, change);
        final TransitionInfo.AnimationOptions options = change.getAnimationOptions();
        final int overrideType = options != null ? options.getType() : ANIM_NONE;
        int animAttr = 0;
        boolean translucent = false;
        if (isDreamTransition) {
            if (type == TRANSIT_OPEN) {
                animAttr = enter
                        ? R.styleable.WindowAnimation_dreamActivityOpenEnterAnimation
                        : R.styleable.WindowAnimation_dreamActivityOpenExitAnimation;
            } else if (type == TRANSIT_CLOSE) {
                animAttr = enter
                        ? 0
                        : R.styleable.WindowAnimation_dreamActivityCloseExitAnimation;
            }
        } else if (wallpaperTransit == WALLPAPER_TRANSITION_INTRA_OPEN) {
            animAttr = enter
                    ? R.styleable.WindowAnimation_wallpaperIntraOpenEnterAnimation
                    : R.styleable.WindowAnimation_wallpaperIntraOpenExitAnimation;
        } else if (wallpaperTransit == WALLPAPER_TRANSITION_INTRA_CLOSE) {
            animAttr = enter
                    ? R.styleable.WindowAnimation_wallpaperIntraCloseEnterAnimation
                    : R.styleable.WindowAnimation_wallpaperIntraCloseExitAnimation;
        } else if (wallpaperTransit == WALLPAPER_TRANSITION_OPEN) {
            animAttr = enter
                    ? R.styleable.WindowAnimation_wallpaperOpenEnterAnimation
                    : R.styleable.WindowAnimation_wallpaperOpenExitAnimation;
        } else if (wallpaperTransit == WALLPAPER_TRANSITION_CLOSE) {
            animAttr = enter
                    ? R.styleable.WindowAnimation_wallpaperCloseEnterAnimation
                    : R.styleable.WindowAnimation_wallpaperCloseExitAnimation;
        } else if (!isCoveredByOpaqueFullscreenChange
                && isFreeform
                && TransitionUtil.isOpeningMode(type)
                && change.getMode() == TRANSIT_TO_BACK) {
            // Set translucent here so TransitionAnimation loads the appropriate animations for
            // translucent activities and tasks later
            translucent = (changeFlags & FLAG_TRANSLUCENT) != 0;
            // The main Task is launching or being brought to front, this Task is being minimized
            animAttr = R.styleable.WindowAnimation_activityCloseExitAnimation;
        } else if (!isCoveredByOpaqueFullscreenChange
                && isFreeform
                && type == TRANSIT_TO_FRONT
                && change.getMode() == TRANSIT_TO_FRONT) {
            // Set translucent here so TransitionAnimation loads the appropriate animations for
            // translucent activities and tasks later
            translucent = (changeFlags & FLAG_TRANSLUCENT) != 0;
            // Bring the minimized Task back to front
            animAttr = R.styleable.WindowAnimation_activityOpenEnterAnimation;
        } else if (type == TRANSIT_OPEN) {
            // We will translucent open animation for translucent activities and tasks. Choose
            // WindowAnimation_activityOpenEnterAnimation and set translucent here, then
            // TransitionAnimation loads appropriate animation later.
            translucent = (changeFlags & FLAG_TRANSLUCENT) != 0;
            if (isTask && translucent && !enter) {
                // For closing translucent tasks, use the activity close animation
                animAttr = R.styleable.WindowAnimation_activityCloseExitAnimation;
            } else if (isTask && !translucent) {
                animAttr = enter
                        ? R.styleable.WindowAnimation_taskOpenEnterAnimation
                        : R.styleable.WindowAnimation_taskOpenExitAnimation;
            } else {
                animAttr = enter
                        ? R.styleable.WindowAnimation_activityOpenEnterAnimation
                        : R.styleable.WindowAnimation_activityOpenExitAnimation;
            }
        } else if (type == TRANSIT_TO_FRONT) {
            animAttr = enter
                    ? R.styleable.WindowAnimation_taskToFrontEnterAnimation
                    : R.styleable.WindowAnimation_taskToFrontExitAnimation;
        } else if (type == TRANSIT_CLOSE) {
            if ((changeFlags & FLAG_TRANSLUCENT) != 0 && !enter) {
                translucent = true;
            }
            if (isTask && !translucent) {
                animAttr = enter
                        ? R.styleable.WindowAnimation_taskCloseEnterAnimation
                        : R.styleable.WindowAnimation_taskCloseExitAnimation;
            } else {
                animAttr = enter
                        ? R.styleable.WindowAnimation_activityCloseEnterAnimation
                        : R.styleable.WindowAnimation_activityCloseExitAnimation;
            }
        } else if (type == TRANSIT_TO_BACK) {
            animAttr = enter
                    ? R.styleable.WindowAnimation_taskToBackEnterAnimation
                    : R.styleable.WindowAnimation_taskToBackExitAnimation;
        }

        Animation a = null;
        if (animAttr != 0) {
            if (overrideType == ANIM_FROM_STYLE && !isTask) {
                final TransitionInfo.AnimationOptions.CustomActivityTransition customTransition =
                        getCustomActivityTransition(animAttr, options);
                if (customTransition != null) {
                    a = loadCustomActivityTransition(
                            customTransition, options, enter, transitionAnimation);
                } else {
                    a = transitionAnimation
                            .loadAnimationAttr(options.getPackageName(), options.getAnimations(),
                                    animAttr, translucent);
                }
            } else if (translucent && !isTask && ((changeFlags & FLAGS_IS_NON_APP_WINDOW) == 0)) {
                // Un-styled translucent activities technically have undefined animations; however,
                // as is always the case, some apps now rely on this being no-animation, so skip
                // loading animations here.
            } else {
                a = transitionAnimation.loadDefaultAnimationAttr(animAttr, translucent);
            }
        }

        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                "loadAnimation: anim=%s animAttr=0x%x type=%s isEntrance=%b", a, animAttr,
                transitTypeToString(type),
                enter);
        return a;
    }

    static TransitionInfo.AnimationOptions.CustomActivityTransition getCustomActivityTransition(
            int animAttr, TransitionInfo.AnimationOptions options) {
        boolean isOpen = false;
        switch (animAttr) {
            case R.styleable.WindowAnimation_activityOpenEnterAnimation:
            case R.styleable.WindowAnimation_activityOpenExitAnimation:
                isOpen = true;
                break;
            case R.styleable.WindowAnimation_activityCloseEnterAnimation:
            case R.styleable.WindowAnimation_activityCloseExitAnimation:
                break;
            default:
                return null;
        }

        return options.getCustomActivityTransition(isOpen);
    }

    /**
     * Gets the final transition type from {@link TransitionInfo} for determining the animation.
     */
    public static int getTransitionTypeFromInfo(@NonNull TransitionInfo info) {
        final int type = info.getType();
        // This back navigation is canceled, check whether the transition should be open or close
        if (type == TRANSIT_PREPARE_BACK_NAVIGATION
                || type == TRANSIT_CLOSE_PREPARE_BACK_NAVIGATION) {
            if (!info.getChanges().isEmpty()) {
                final TransitionInfo.Change change = info.getChanges().get(0);
                return TransitionUtil.isOpeningMode(change.getMode())
                        ? TRANSIT_OPEN : TRANSIT_CLOSE;
            }
        }
        // If the info transition type is opening transition, iterate its changes to see if it
        // has any opening change, if none, returns TRANSIT_CLOSE type for closing animation.
        if (type == TRANSIT_OPEN) {
            boolean hasOpenTransit = false;
            for (TransitionInfo.Change change : info.getChanges()) {
                if ((change.getTaskInfo() != null || change.hasFlags(FLAG_IS_DISPLAY))
                        && !TransitionUtil.isOrderOnly(change)) {
                    // This isn't an activity-level transition.
                    return type;
                }
                if (change.getTaskInfo() != null
                        && change.hasFlags(FLAG_IS_DISPLAY | FLAGS_IS_NON_APP_WINDOW)) {
                    // Ignore non-activity containers.
                    continue;
                }
                if (change.getMode() == TRANSIT_OPEN) {
                    hasOpenTransit = true;
                    break;
                }
            }
            if (!hasOpenTransit) {
                return TRANSIT_CLOSE;
            }
        }
        return type;
    }

    static Animation loadCustomActivityTransition(
            @NonNull TransitionInfo.AnimationOptions.CustomActivityTransition transitionAnim,
            TransitionInfo.AnimationOptions options, boolean enter,
            TransitionAnimation transitionAnimation) {
        final Animation a = transitionAnimation.loadAppTransitionAnimation(options.getPackageName(),
                enter ? transitionAnim.getCustomEnterResId()
                        : transitionAnim.getCustomExitResId());
        if (a != null && transitionAnim.getCustomBackgroundColor() != 0) {
            a.setBackdropColor(transitionAnim.getCustomBackgroundColor());
        }
        return a;
    }

    /**
     * Gets the background {@link ColorInt} for the given transition animation if it is set.
     *
     * @param defaultColor  {@link ColorInt} to return if there is no background color specified by
     *                      the given transition animation.
     */
    @ColorInt
    public static int getTransitionBackgroundColorIfSet(@NonNull TransitionInfo.Change change,
            @NonNull Animation a, @ColorInt int defaultColor) {
        if (!a.getShowBackdrop()) {
            return defaultColor;
        } else if (a.getBackdropColor() != 0) {
            // Otherwise fallback on the background color provided through the animation
            // definition.
            return a.getBackdropColor();
        } else if (change.getBackgroundColor() != 0) {
            // Otherwise default to the window's background color if provided through
            // the theme as the background color for the animation - the top most window
            // with a valid background color and showBackground set takes precedence.
            return change.getBackgroundColor();
        }
        return defaultColor;
    }

    /**
     * Adds the given {@code backgroundColor} as the background color to the transition animation.
     */
    public static void addBackgroundToTransition(@NonNull SurfaceControl rootLeash,
            @ColorInt int backgroundColor, @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction) {
        if (backgroundColor == 0) {
            // No background color.
            return;
        }
        final Color bgColor = Color.valueOf(backgroundColor);
        final float[] colorArray = new float[] { bgColor.red(), bgColor.green(), bgColor.blue() };
        final SurfaceControl animationBackgroundSurface = new SurfaceControl.Builder()
                .setName("Animation Background")
                .setParent(rootLeash)
                .setColorLayer()
                .setOpaque(true)
                .setCallsite("TransitionAnimationHelper.addBackgroundToTransition")
                .build();
        startTransaction
                .setLayer(animationBackgroundSurface, Integer.MIN_VALUE)
                .setColor(animationBackgroundSurface, colorArray)
                .show(animationBackgroundSurface);
        finishTransaction.remove(animationBackgroundSurface);
    }

    /**
     * Returns whether there is an opaque fullscreen Change positioned in front of the given Change
     * in the given TransitionInfo.
     */
    static boolean isCoveredByOpaqueFullscreenChange(
            TransitionInfo info, TransitionInfo.Change change) {
        // TransitionInfo#getChanges() are ordered from front to back
        for (TransitionInfo.Change coveringChange : info.getChanges()) {
            if (coveringChange == change) {
                return false;
            }
            if ((coveringChange.getFlags() & FLAG_TRANSLUCENT) == 0
                    && coveringChange.getTaskInfo() != null
                    && coveringChange.getTaskInfo().getWindowingMode()
                    == WindowConfiguration.WINDOWING_MODE_FULLSCREEN) {
                return true;
            }
        }
        return false;
    }

    /**
     * In some situations (eg. TaskBar) the content area of a display appears to be rounded. For
     * these situations, we may want the animation to also express the same rounded corners (even
     * though in steady-state, the app internally manages the insets). This class Keeps track of,
     * and provides, the bounds of rounded-corner display content.
     *
     * This is used to enable already-running animations to adapt to changes in taskbar/navbar
     * position live.
     */
    public static class RoundedContentPerDisplay implements
            DisplayInsetsController.OnInsetsChangedListener {

        /** The current bounds of the display content (post-inset). */
        final Rect mBounds = new Rect();

        @Override
        public void insetsChanged(InsetsState insetsState) {
            Insets insets = Insets.NONE;
            for (int i = insetsState.sourceSize() - 1; i >= 0; i--) {
                final InsetsSource source = insetsState.sourceAt(i);
                if (!source.hasFlags(InsetsSource.FLAG_INSETS_ROUNDED_CORNER)) {
                    continue;
                }
                Rect displayFrame = insetsState.getDisplayFrame();
                insets = Insets.max(source.calculateInsets(displayFrame, displayFrame, false),
                        insets);
            }
            mBounds.set(insetsState.getDisplayFrame());
            mBounds.inset(insets);
        }
    }

    /**
     * Keeps track of the bounds of rounded-corner display content (post-inset).
     *
     * @see RoundedContentPerDisplay
     */
    public static class RoundedContentTracker implements
            DisplayController.OnDisplaysChangedListener {
        final DisplayController mDisplayController;
        final DisplayInsetsController mDisplayInsetsController;
        final SparseArray<RoundedContentPerDisplay> mPerDisplay = new SparseArray<>();

        RoundedContentTracker(DisplayController dc, DisplayInsetsController dic) {
            mDisplayController = dc;
            mDisplayInsetsController = dic;
        }

        void init() {
            mDisplayController.addDisplayWindowListener(this);
        }

        RoundedContentPerDisplay forDisplay(int displayId) {
            return mPerDisplay.get(displayId);
        }

        @Override
        public void onDisplayAdded(int displayId) {
            final RoundedContentPerDisplay perDisplay = new RoundedContentPerDisplay();
            mDisplayInsetsController.addInsetsChangedListener(displayId, perDisplay);
            mPerDisplay.put(displayId, perDisplay);
            final DisplayLayout dl = mDisplayController.getDisplayLayout(displayId);
            perDisplay.mBounds.set(0, 0, dl.width(), dl.height());
        }

        @Override
        public void onDisplayRemoved(int displayId) {
            final RoundedContentPerDisplay listener = mPerDisplay.removeReturnOld(displayId);
            if (listener != null) {
                mDisplayInsetsController.removeInsetsChangedListener(displayId, listener);
            }
        }
    }
}
