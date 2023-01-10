/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3;

import static com.android.launcher3.anim.Interpolators.ACCEL_2;
import static com.android.launcher3.anim.Interpolators.DEACCEL_2;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_HOME;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_OVERVIEW;
import static com.android.launcher3.testing.shared.TestProtocol.ALL_APPS_STATE_ORDINAL;
import static com.android.launcher3.testing.shared.TestProtocol.BACKGROUND_APP_STATE_ORDINAL;
import static com.android.launcher3.testing.shared.TestProtocol.HINT_STATE_ORDINAL;
import static com.android.launcher3.testing.shared.TestProtocol.HINT_STATE_TWO_BUTTON_ORDINAL;
import static com.android.launcher3.testing.shared.TestProtocol.NORMAL_STATE_ORDINAL;
import static com.android.launcher3.testing.shared.TestProtocol.OVERVIEW_MODAL_TASK_STATE_ORDINAL;
import static com.android.launcher3.testing.shared.TestProtocol.OVERVIEW_SPLIT_SELECT_ORDINAL;
import static com.android.launcher3.testing.shared.TestProtocol.OVERVIEW_STATE_ORDINAL;
import static com.android.launcher3.testing.shared.TestProtocol.QUICK_SWITCH_STATE_ORDINAL;
import static com.android.launcher3.testing.shared.TestProtocol.SPRING_LOADED_STATE_ORDINAL;

import android.content.Context;
import android.graphics.Color;
import android.view.animation.Interpolator;

import com.android.launcher3.statemanager.BaseState;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.states.HintState;
import com.android.launcher3.states.SpringLoadedState;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.uioverrides.states.AllAppsState;
import com.android.launcher3.uioverrides.states.OverviewState;
import com.android.launcher3.views.ActivityContext;

import java.util.Arrays;

/**
 * Base state for various states used for the Launcher
 */
public abstract class LauncherState implements BaseState<LauncherState> {

    /**
     * Set of elements indicating various workspace elements which change visibility across states
     * Note that workspace is not included here as in that case, we animate individual pages
     */
    public static final int NONE = 0;
    public static final int HOTSEAT_ICONS = 1 << 0;
    public static final int ALL_APPS_CONTENT = 1 << 1;
    public static final int VERTICAL_SWIPE_INDICATOR = 1 << 2;
    public static final int OVERVIEW_ACTIONS = 1 << 3;
    public static final int CLEAR_ALL_BUTTON = 1 << 4;
    public static final int WORKSPACE_PAGE_INDICATOR = 1 << 5;
    public static final int SPLIT_PLACHOLDER_VIEW = 1 << 6;

    // Flag indicating workspace has multiple pages visible.
    public static final int FLAG_MULTI_PAGE = BaseState.getFlag(0);
    // Flag indicating that workspace and its contents are not accessible
    public static final int FLAG_WORKSPACE_INACCESSIBLE = BaseState.getFlag(1);

    // Flag indicating the state allows workspace icons to be dragged.
    public static final int FLAG_WORKSPACE_ICONS_CAN_BE_DRAGGED = BaseState.getFlag(2);
    // Flag to indicate that workspace should draw page background
    public static final int FLAG_WORKSPACE_HAS_BACKGROUNDS = BaseState.getFlag(3);
    // Flag to indicate if the state would have scrim over sysui region: statu sbar and nav bar
    public static final int FLAG_HAS_SYS_UI_SCRIM = BaseState.getFlag(4);
    // Flag to inticate that all popups should be closed when this state is enabled.
    public static final int FLAG_CLOSE_POPUPS = BaseState.getFlag(5);
    public static final int FLAG_OVERVIEW_UI = BaseState.getFlag(6);

    // Flag indicating that hotseat and its contents are not accessible.
    public static final int FLAG_HOTSEAT_INACCESSIBLE = BaseState.getFlag(7);


    public static final float NO_OFFSET = 0;
    public static final float NO_SCALE = 1;

    protected static final PageAlphaProvider DEFAULT_ALPHA_PROVIDER =
            new PageAlphaProvider(ACCEL_2) {
                @Override
                public float getPageAlpha(int pageIndex) {
                    return 1;
                }
            };

    protected static final PageTranslationProvider DEFAULT_PAGE_TRANSLATION_PROVIDER =
            new PageTranslationProvider(DEACCEL_2) {
                @Override
                public float getPageTranslation(int pageIndex) {
                    return 0;
                }
            };

    private static final LauncherState[] sAllStates = new LauncherState[10];

    /**
     * TODO: Create a separate class for NORMAL state.
     */
    public static final LauncherState NORMAL = new LauncherState(NORMAL_STATE_ORDINAL,
            LAUNCHER_STATE_HOME,
            FLAG_DISABLE_RESTORE | FLAG_WORKSPACE_ICONS_CAN_BE_DRAGGED | FLAG_HAS_SYS_UI_SCRIM) {
        @Override
        public int getTransitionDuration(Context context, boolean isToState) {
            // Arbitrary duration, when going to NORMAL we use the state we're coming from instead.
            return 0;
        }
    };

    /**
     * Various Launcher states arranged in the increasing order of UI layers
     */
    public static final LauncherState SPRING_LOADED = new SpringLoadedState(
            SPRING_LOADED_STATE_ORDINAL);
    public static final LauncherState ALL_APPS = new AllAppsState(ALL_APPS_STATE_ORDINAL);
    public static final LauncherState HINT_STATE = new HintState(HINT_STATE_ORDINAL);
    public static final LauncherState HINT_STATE_TWO_BUTTON = new HintState(
            HINT_STATE_TWO_BUTTON_ORDINAL, LAUNCHER_STATE_OVERVIEW);

    public static final LauncherState OVERVIEW = new OverviewState(OVERVIEW_STATE_ORDINAL);
    public static final LauncherState OVERVIEW_MODAL_TASK = OverviewState.newModalTaskState(
            OVERVIEW_MODAL_TASK_STATE_ORDINAL);
    public static final LauncherState QUICK_SWITCH =
            OverviewState.newSwitchState(QUICK_SWITCH_STATE_ORDINAL);
    public static final LauncherState BACKGROUND_APP =
            OverviewState.newBackgroundState(BACKGROUND_APP_STATE_ORDINAL);
    public static final LauncherState OVERVIEW_SPLIT_SELECT =
            OverviewState.newSplitSelectState(OVERVIEW_SPLIT_SELECT_ORDINAL);

    public final int ordinal;

    /**
     * Used for {@link com.android.launcher3.logging.StatsLogManager}
     */
    public final int statsLogOrdinal;

    /**
     * True if the state has overview panel visible.
     */
    public final boolean overviewUi;

    private final int mFlags;

    public LauncherState(int id, int statsLogOrdinal, int flags) {
        this.statsLogOrdinal = statsLogOrdinal;
        this.mFlags = flags;
        this.overviewUi = (flags & FLAG_OVERVIEW_UI) != 0;
        this.ordinal = id;
        sAllStates[id] = this;
    }

    /**
     * Returns if the state has the provided flag
     */
    @Override
    public final boolean hasFlag(int mask) {
        return (mFlags & mask) != 0;
    }

    public static LauncherState[] values() {
        return Arrays.copyOf(sAllStates, sAllStates.length);
    }

    public ScaleAndTranslation getWorkspaceScaleAndTranslation(Launcher launcher) {
        return new ScaleAndTranslation(NO_SCALE, NO_OFFSET, NO_OFFSET);
    }

    public ScaleAndTranslation getHotseatScaleAndTranslation(Launcher launcher) {
        // For most states, treat the hotseat as if it were part of the workspace.
        return getWorkspaceScaleAndTranslation(launcher);
    }

    /**
     * Returns an array of two elements.
     * The first specifies the scale for the overview
     * The second is the factor ([0, 1], 0 => center-screen; 1 => offscreen) by which overview
     * should be shifted horizontally.
     */
    public float[] getOverviewScaleAndOffset(Launcher launcher) {
        return launcher.getNormalOverviewScaleAndOffset();
    }

    public float getOverviewFullscreenProgress() {
        return 0;
    }

    public int getVisibleElements(Launcher launcher) {
        return HOTSEAT_ICONS | WORKSPACE_PAGE_INDICATOR | VERTICAL_SWIPE_INDICATOR;
    }

    /**
     * A shorthand for checking getVisibleElements() & elements == elements.
     * @return Whether all of the given elements are visible.
     */
    public boolean areElementsVisible(Launcher launcher, int elements) {
        return (getVisibleElements(launcher) & elements) == elements;
    }

    /**
     * Returns whether taskbar is stashed and thus should either:
     * 1) replace hotseat or taskbar icons with a handle in gesture navigation mode or
     * 2) fade out the hotseat or taskbar icons in 3-button navigation mode.
     */
    public boolean isTaskbarStashed(Launcher launcher) {
        return false;
    }

    /** Returns whether taskbar is aligned with the hotseat vs position inside apps */
    public boolean isTaskbarAlignedWithHotseat(Launcher launcher) {
        return true;
    }

    /**
     * Fraction shift in the vertical translation UI and related properties
     *
     * @see com.android.launcher3.allapps.AllAppsTransitionController
     */
    public float getVerticalProgress(Launcher launcher) {
        return 1f;
    }

    public float getWorkspaceBackgroundAlpha(Launcher launcher) {
        return 0;
    }

    /**
     * What color should the workspace scrim be in when at rest in this state.
     * Return {@link Color#TRANSPARENT} for no scrim.
     */
    public int getWorkspaceScrimColor(Launcher launcher) {
        return Color.TRANSPARENT;
    }

    /**
     * For this state, how modal should over view been shown. 0 modalness means all tasks drawn,
     * 1 modalness means the current task is show on its own.
     */
    public float getOverviewModalness() {
        return 0;
    }

    /**
     * For this state, how much additional translation there should be for each of the
     * child TaskViews. Note that the translation can be its primary or secondary dimension.
     */
    public float getSplitSelectTranslation(Launcher launcher) {
        return 0;
    }

    /**
     * The amount of blur and wallpaper zoom to apply to the background of either the app
     * or Launcher surface in this state. Should be a number between 0 and 1, inclusive.
     *
     * 0 means completely zoomed in, without blurs. 1 is zoomed out, with blurs.
     */
    public final  <DEVICE_PROFILE_CONTEXT extends Context & ActivityContext>
            float getDepth(DEVICE_PROFILE_CONTEXT context) {
        return getDepth(context,
                BaseDraggingActivity.fromContext(context).getDeviceProfile().isMultiWindowMode);
    }

    /**
     * Returns the amount of blur and wallpaper zoom for this state with {@param isMultiWindowMode}.
     *
     * @see #getDepth(Context).
     */
    public final <DEVICE_PROFILE_CONTEXT extends Context & ActivityContext>
            float getDepth(DEVICE_PROFILE_CONTEXT context, boolean isMultiWindowMode) {
        if (isMultiWindowMode) {
            return 0;
        }
        return getDepthUnchecked(context);
    }

    protected <DEVICE_PROFILE_CONTEXT extends Context & ActivityContext>
            float getDepthUnchecked(DEVICE_PROFILE_CONTEXT context) {
        return 0f;
    }

    public String getDescription(Launcher launcher) {
        return launcher.getWorkspace().getCurrentPageDescription();
    }

    public PageAlphaProvider getWorkspacePageAlphaProvider(Launcher launcher) {
        if ((this != NORMAL && this != HINT_STATE)
                || !launcher.getDeviceProfile().shouldFadeAdjacentWorkspaceScreens()) {
            return DEFAULT_ALPHA_PROVIDER;
        }
        final int centerPage = launcher.getWorkspace().getNextPage();
        return new PageAlphaProvider(ACCEL_2) {
            @Override
            public float getPageAlpha(int pageIndex) {
                return pageIndex != centerPage ? 0 : 1f;
            }
        };
    }

    /**
     * Gets the translation provider for workspace pages.
     */
    public PageTranslationProvider getWorkspacePageTranslationProvider(Launcher launcher) {
        if (this != SPRING_LOADED || !launcher.getDeviceProfile().isTwoPanels) {
            return DEFAULT_PAGE_TRANSLATION_PROVIDER;
        }
        final float quarterPageSpacing = launcher.getWorkspace().getPageSpacing() / 4f;
        return new PageTranslationProvider(DEACCEL_2) {
            @Override
            public float getPageTranslation(int pageIndex) {
                boolean isRtl = launcher.getWorkspace().mIsRtl;
                boolean isFirstPage = pageIndex % 2 == 0;
                return ((isFirstPage && !isRtl) || (!isFirstPage && isRtl)) ? -quarterPageSpacing
                        : quarterPageSpacing;
            }
        };
    }

    @Override
    public LauncherState getHistoryForState(LauncherState previousState) {
        // No history is supported
        return NORMAL;
    }

    @Override
    public String toString() {
        return TestProtocol.stateOrdinalToString(ordinal);
    }

    public void onBackPressed(Launcher launcher) {
        if (this != NORMAL) {
            StateManager<LauncherState> lsm = launcher.getStateManager();
            LauncherState lastState = lsm.getLastState();
            lsm.goToState(lastState);
        }
    }

    public static abstract class PageAlphaProvider {

        public final Interpolator interpolator;

        public PageAlphaProvider(Interpolator interpolator) {
            this.interpolator = interpolator;
        }

        public abstract float getPageAlpha(int pageIndex);
    }

    /**
     * Provider for the translation and animation interpolation of workspace pages.
     */
    public abstract static class PageTranslationProvider {

        public final Interpolator interpolator;

        public PageTranslationProvider(Interpolator interpolator) {
            this.interpolator = interpolator;
        }

        /**
         * Gets the translation of the workspace page at the provided page index.
         */
        public abstract float getPageTranslation(int pageIndex);
    }

    public static class ScaleAndTranslation {
        public float scale;
        public float translationX;
        public float translationY;

        public ScaleAndTranslation(float scale, float translationX, float translationY) {
            this.scale = scale;
            this.translationX = translationX;
            this.translationY = translationY;
        }
    }
}
