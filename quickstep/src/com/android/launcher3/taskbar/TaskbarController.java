/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.taskbar;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import static com.android.systemui.shared.system.WindowManagerWrapper.ITYPE_BOTTOM_TAPPABLE_ELEMENT;
import static com.android.systemui.shared.system.WindowManagerWrapper.ITYPE_EXTRA_NAVIGATION_BAR;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.ActivityOptions;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.inputmethodservice.InputMethodService;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherState;
import com.android.launcher3.QuickstepTransitionManager;
import com.android.launcher3.R;
import com.android.launcher3.anim.AlphaUpdateListener;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.taskbar.TaskbarNavButtonController.TaskbarButton;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.views.ActivityContext;
import com.android.quickstep.AnimatedFloat;
import com.android.quickstep.SysUINavigationMode;
import com.android.quickstep.TouchInteractionService.TaskbarOverviewProxyDelegate;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.WindowManagerWrapper;

/**
 * Interfaces with Launcher/WindowManager/SystemUI to determine what to show in TaskbarView.
 */
public class TaskbarController implements TaskbarOverviewProxyDelegate {

    private static final String WINDOW_TITLE = "Taskbar";

    private final TaskbarContainerView mTaskbarContainerView;
    private final TaskbarView mTaskbarViewInApp;
    private final TaskbarView mTaskbarViewOnHome;
    private final ImeBarView mImeBarView;

    private final BaseQuickstepLauncher mLauncher;
    private final WindowManager mWindowManager;
    // Layout width and height of the Taskbar in the default state.
    private final Point mTaskbarSize;
    private final TaskbarStateHandler mTaskbarStateHandler;
    private final TaskbarAnimationController mTaskbarAnimationController;
    private final TaskbarHotseatController mHotseatController;
    private final TaskbarDragController mDragController;
    private final TaskbarNavButtonController mNavButtonController;

    // Initialized in init().
    private WindowManager.LayoutParams mWindowLayoutParams;
    private SysUINavigationMode.Mode mNavMode = SysUINavigationMode.Mode.NO_BUTTON;
    private final SysUINavigationMode.NavigationModeChangeListener mNavigationModeChangeListener =
            this::onNavModeChanged;

    private @Nullable Animator mAnimator;
    private boolean mIsAnimatingToLauncher;

    public TaskbarController(BaseQuickstepLauncher launcher,
            TaskbarContainerView taskbarContainerView, TaskbarView taskbarViewOnHome) {
        mLauncher = launcher;
        mTaskbarContainerView = taskbarContainerView;
        mTaskbarContainerView.construct(createTaskbarContainerViewCallbacks());
        ButtonProvider buttonProvider = new ButtonProvider(launcher);
        mTaskbarViewInApp = mTaskbarContainerView.findViewById(R.id.taskbar_view);
        mTaskbarViewInApp.construct(createTaskbarViewCallbacks(), buttonProvider);
        mTaskbarViewOnHome = taskbarViewOnHome;
        mTaskbarViewOnHome.construct(createTaskbarViewCallbacks(), buttonProvider);
        mImeBarView = mTaskbarContainerView.findViewById(R.id.ime_bar_view);
        mImeBarView.construct(buttonProvider);
        mNavButtonController = new TaskbarNavButtonController(launcher);
        mWindowManager = mLauncher.getWindowManager();
        mTaskbarSize = new Point(MATCH_PARENT, mLauncher.getDeviceProfile().taskbarSize);
        mTaskbarStateHandler = mLauncher.getTaskbarStateHandler();
        mTaskbarAnimationController = new TaskbarAnimationController(mLauncher,
                createTaskbarAnimationControllerCallbacks());
        mHotseatController = new TaskbarHotseatController(mLauncher,
                createTaskbarHotseatControllerCallbacks());
        mDragController = new TaskbarDragController(mLauncher);
    }

    private TaskbarAnimationControllerCallbacks createTaskbarAnimationControllerCallbacks() {
        return new TaskbarAnimationControllerCallbacks() {
            @Override
            public void updateTaskbarBackgroundAlpha(float alpha) {
                mTaskbarContainerView.setTaskbarBackgroundAlpha(alpha);
            }

            @Override
            public void updateTaskbarVisibilityAlpha(float alpha) {
                mTaskbarViewInApp.setAlpha(alpha);
                mTaskbarViewOnHome.setAlpha(alpha);
            }

            @Override
            public void updateImeBarVisibilityAlpha(float alpha) {
                if (mNavMode != SysUINavigationMode.Mode.THREE_BUTTONS) {
                    // TODO Remove sysui IME bar for gesture nav as well
                    return;
                }
                mImeBarView.setAlpha(alpha);
                mImeBarView.setVisibility(alpha == 0 ? GONE : VISIBLE);
            }

            @Override
            public void updateTaskbarScale(float scale) {
                mTaskbarViewInApp.setScaleX(scale);
                mTaskbarViewInApp.setScaleY(scale);
            }

            @Override
            public void updateTaskbarTranslationY(float translationY) {
                if (translationY < 0) {
                    // Resize to accommodate the max translation we'll reach.
                    setTaskbarWindowHeight(mTaskbarSize.y
                            + mLauncher.getHotseat().getTaskbarOffsetY());
                } else {
                    setTaskbarWindowHeight(mTaskbarSize.y);
                }
                mTaskbarViewInApp.setTranslationY(translationY);
            }
        };
    }

    private TaskbarContainerViewCallbacks createTaskbarContainerViewCallbacks() {
        return new TaskbarContainerViewCallbacks() {
            @Override
            public void onViewRemoved() {
                // Ensure no other children present (like Folders, etc)
                for (int i = 0; i < mTaskbarContainerView.getChildCount(); i++) {
                    View v = mTaskbarContainerView.getChildAt(i);
                    if (!((v instanceof TaskbarView) || (v instanceof ImeBarView))){
                        return;
                    }
                }
                setTaskbarWindowFullscreen(false);
            }

            @Override
            public boolean isTaskbarTouchable() {
                return mTaskbarContainerView.getAlpha() > AlphaUpdateListener.ALPHA_CUTOFF_THRESHOLD
                        && (mTaskbarViewInApp.getVisibility() == VISIBLE
                            || mImeBarView.getVisibility() == VISIBLE)
                        && !mIsAnimatingToLauncher;
            }
        };
    }

    private TaskbarViewCallbacks createTaskbarViewCallbacks() {
        return new TaskbarViewCallbacks() {
            @Override
            public View.OnClickListener getItemOnClickListener() {
                return view -> {
                    Object tag = view.getTag();
                    if (tag instanceof Task) {
                        Task task = (Task) tag;
                        ActivityManagerWrapper.getInstance().startActivityFromRecents(task.key,
                                ActivityOptions.makeBasic());
                    } else if (tag instanceof FolderInfo) {
                        FolderIcon folderIcon = (FolderIcon) view;
                        Folder folder = folderIcon.getFolder();

                        setTaskbarWindowFullscreen(true);

                        mTaskbarContainerView.post(() -> {
                            folder.animateOpen();

                            folder.iterateOverItems((itemInfo, itemView) -> {
                                itemView.setOnClickListener(getItemOnClickListener());
                                itemView.setOnLongClickListener(getItemOnLongClickListener());
                                // To play haptic when dragging, like other Taskbar items do.
                                itemView.setHapticFeedbackEnabled(true);
                                return false;
                            });
                        });
                    } else {
                        ItemClickHandler.INSTANCE.onClick(view);
                    }

                    AbstractFloatingView.closeAllOpenViews(
                            mTaskbarContainerView.getTaskbarActivityContext());
                };
            }

            @Override
            public View.OnLongClickListener getItemOnLongClickListener() {
                return mDragController::startSystemDragOnLongClick;
            }

            @Override
            public int getEmptyHotseatViewVisibility(TaskbarView taskbarView) {
                // When on the home screen, we want the empty hotseat views to take up their full
                // space so that the others line up with the home screen hotseat.
                boolean isOnHomeScreen = taskbarView == mTaskbarViewOnHome
                        || mLauncher.hasBeenResumed() || mIsAnimatingToLauncher;
                return isOnHomeScreen ? INVISIBLE : GONE;
            }

            @Override
            public float getNonIconScale(TaskbarView taskbarView) {
                return taskbarView == mTaskbarViewOnHome ? getTaskbarScaleOnHome() : 1f;
            }

            @Override
            public void onItemPositionsChanged(TaskbarView taskbarView) {
                if (taskbarView == mTaskbarViewOnHome) {
                    alignRealHotseatWithTaskbar();
                }
            }

            @Override
            public void onNavigationButtonClick(@TaskbarButton int buttonType) {
                mNavButtonController.onButtonClick(buttonType);
            }
        };
    }

    private TaskbarHotseatControllerCallbacks createTaskbarHotseatControllerCallbacks() {
        return new TaskbarHotseatControllerCallbacks() {
            @Override
            public void updateHotseatItems(ItemInfo[] hotseatItemInfos) {
                mTaskbarViewInApp.updateHotseatItems(hotseatItemInfos);
            }
        };
    }

    /**
     * Initializes the Taskbar, including adding it to the screen.
     */
    public void init() {
        mNavMode = SysUINavigationMode.INSTANCE.get(mLauncher)
                .addModeChangeListener(mNavigationModeChangeListener);
        mTaskbarViewInApp.init(mHotseatController.getNumHotseatIcons(), mNavMode);
        mTaskbarViewOnHome.init(mHotseatController.getNumHotseatIcons(), mNavMode);
        mTaskbarContainerView.init(mTaskbarViewInApp);
        mImeBarView.init(createTaskbarViewCallbacks());
        addToWindowManager();
        mTaskbarStateHandler.setTaskbarCallbacks(createTaskbarStateHandlerCallbacks());
        mTaskbarAnimationController.init();
        mHotseatController.init();

        setWhichTaskbarViewIsVisible(mLauncher.hasBeenResumed()
                ? mTaskbarViewOnHome
                : mTaskbarViewInApp);
    }

    private TaskbarStateHandlerCallbacks createTaskbarStateHandlerCallbacks() {
        return new TaskbarStateHandlerCallbacks() {
            @Override
            public AnimatedFloat getAlphaTarget() {
                return mTaskbarAnimationController.getTaskbarVisibilityForLauncherState();
            }

            @Override
            public AnimatedFloat getScaleTarget() {
                return mTaskbarAnimationController.getTaskbarScaleForLauncherState();
            }

            @Override
            public AnimatedFloat getTranslationYTarget() {
                return mTaskbarAnimationController.getTaskbarTranslationYForLauncherState();
            }
        };
    }

    /**
     * Removes the Taskbar from the screen, and removes any obsolete listeners etc.
     */
    public void cleanup() {
        if (mAnimator != null) {
            // End this first, in case it relies on properties that are about to be cleaned up.
            mAnimator.end();
        }

        mTaskbarViewInApp.cleanup();
        mTaskbarViewOnHome.cleanup();
        mTaskbarContainerView.cleanup();
        mImeBarView.cleanup();
        removeFromWindowManager();
        mTaskbarStateHandler.setTaskbarCallbacks(null);
        mTaskbarAnimationController.cleanup();
        mHotseatController.cleanup();

        setWhichTaskbarViewIsVisible(null);
        SysUINavigationMode.INSTANCE.get(mLauncher)
                .removeModeChangeListener(mNavigationModeChangeListener);
    }

    private void removeFromWindowManager() {
        mWindowManager.removeViewImmediate(mTaskbarContainerView);
    }

    private void addToWindowManager() {
        final int gravity = Gravity.BOTTOM;

        mWindowLayoutParams = new WindowManager.LayoutParams(
                mTaskbarSize.x,
                mTaskbarSize.y,
                TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        mWindowLayoutParams.setTitle(WINDOW_TITLE);
        mWindowLayoutParams.packageName = mLauncher.getPackageName();
        mWindowLayoutParams.gravity = gravity;
        mWindowLayoutParams.setFitInsetsTypes(0);
        mWindowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        mWindowLayoutParams.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        mWindowLayoutParams.setSystemApplicationOverlay(true);

        WindowManagerWrapper wmWrapper = WindowManagerWrapper.getInstance();
        wmWrapper.setProvidesInsetsTypes(
                mWindowLayoutParams,
                new int[] { ITYPE_EXTRA_NAVIGATION_BAR, ITYPE_BOTTOM_TAPPABLE_ELEMENT }
        );

        TaskbarContainerView.LayoutParams taskbarLayoutParams =
                new TaskbarContainerView.LayoutParams(mTaskbarSize.x, mTaskbarSize.y);
        taskbarLayoutParams.gravity = gravity;
        mTaskbarViewInApp.setLayoutParams(taskbarLayoutParams);

        mWindowManager.addView(mTaskbarContainerView, mWindowLayoutParams);
    }

    private void onNavModeChanged(SysUINavigationMode.Mode newMode) {
        mNavMode = newMode;
        cleanup();
        init();
    }

    /**
     * Should be called from onResume() and onPause(), and animates the Taskbar accordingly.
     */
    public void onLauncherResumedOrPaused(boolean isResumed) {
        long duration = QuickstepTransitionManager.CONTENT_ALPHA_DURATION;
        if (mAnimator != null) {
            mAnimator.cancel();
        }
        if (isResumed) {
            mAnimator = createAnimToLauncher(null, duration);
        } else {
            mAnimator = createAnimToApp(duration);
        }
        mAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mAnimator = null;
            }
        });
        mAnimator.start();
    }

    /**
     * Create Taskbar animation when going from an app to Launcher.
     * @param toState If known, the state we will end up in when reaching Launcher.
     */
    public Animator createAnimToLauncher(@Nullable LauncherState toState, long duration) {
        PendingAnimation anim = new PendingAnimation(duration);
        anim.add(mTaskbarAnimationController.createAnimToBackgroundAlpha(0, duration));
        if (toState != null) {
            mTaskbarStateHandler.setStateWithAnimation(toState, new StateAnimationConfig(), anim);
        }

        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mIsAnimatingToLauncher = true;
                mTaskbarViewInApp.updateHotseatItemsVisibility();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mIsAnimatingToLauncher = false;
                setWhichTaskbarViewIsVisible(mTaskbarViewOnHome);
            }
        });

        return anim.buildAnim();
    }

    private Animator createAnimToApp(long duration) {
        PendingAnimation anim = new PendingAnimation(duration);
        anim.add(mTaskbarAnimationController.createAnimToBackgroundAlpha(1, duration));
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mTaskbarViewInApp.updateHotseatItemsVisibility();
                setWhichTaskbarViewIsVisible(mTaskbarViewInApp);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
            }
        });
        return anim.buildAnim();
    }

    /**
     * Should be called when the IME visibility changes, so we can hide/show Taskbar accordingly.
     */
    public void setIsImeVisible(boolean isImeVisible) {
        mTaskbarAnimationController.animateToVisibilityForIme(isImeVisible ? 0 : 1);
        blockTaskbarTouchesForIme(isImeVisible);
    }

    /**
     * When in 3 button nav, the above doesn't get called since we prevent sysui nav bar from
     * instantiating at all, which is what's responsible for sending sysui state flags over.
     *
     * @param vis IME visibility flag
     * @param backDisposition Used to determine back button behavior for software keyboard
     *                        See BACK_DISPOSITION_* constants in {@link InputMethodService}
     */
    public void updateImeStatus(int displayId, int vis, int backDisposition,
            boolean showImeSwitcher) {
        if (displayId != mTaskbarContainerView.getContext().getDisplayId() ||
                mNavMode != SysUINavigationMode.Mode.THREE_BUTTONS) {
            return;
        }

        boolean imeVisible = (vis & InputMethodService.IME_VISIBLE) != 0;
        mTaskbarAnimationController.animateToVisibilityForIme(imeVisible ? 0 : 1);
        mImeBarView.setImeSwitcherVisibility(showImeSwitcher);
        blockTaskbarTouchesForIme(imeVisible);
    }

    /**
     * Should be called when one or more items in the Hotseat have changed.
     */
    public void onHotseatUpdated() {
        mHotseatController.onHotseatUpdated();
    }

    /**
     * @param ev MotionEvent in screen coordinates.
     * @return Whether any Taskbar item could handle the given MotionEvent if given the chance.
     */
    public boolean isEventOverAnyTaskbarItem(MotionEvent ev) {
        return mTaskbarViewInApp.isEventOverAnyItem(ev);
    }

    public boolean isDraggingItem() {
        return mTaskbarViewInApp.isDraggingItem() || mTaskbarViewOnHome.isDraggingItem();
    }

    /**
     * @return Whether the given View is in the same window as Taskbar.
     */
    public boolean isViewInTaskbar(View v) {
        return mTaskbarContainerView.isAttachedToWindow()
                && mTaskbarContainerView.getWindowId().equals(v.getWindowId());
    }

    /**
     * Pads the Hotseat to line up exactly with Taskbar's copy of the Hotseat.
     */
    public void alignRealHotseatWithTaskbar() {
        Rect hotseatBounds = new Rect();
        DeviceProfile grid = mLauncher.getDeviceProfile();
        int hotseatHeight = grid.workspacePadding.bottom + grid.taskbarSize;
        int taskbarOffset = mLauncher.getHotseat().getTaskbarOffsetY();
        int hotseatTopDiff = hotseatHeight - grid.taskbarSize - taskbarOffset;
        int hotseatBottomDiff = taskbarOffset;

        mTaskbarViewOnHome.getHotseatBounds().roundOut(hotseatBounds);
        mLauncher.getHotseat().setPadding(hotseatBounds.left,
                hotseatBounds.top + hotseatTopDiff,
                mTaskbarViewOnHome.getWidth() - hotseatBounds.right,
                mTaskbarViewOnHome.getHeight() - hotseatBounds.bottom + hotseatBottomDiff);
    }

    private void setWhichTaskbarViewIsVisible(@Nullable TaskbarView visibleTaskbar) {
        mTaskbarViewInApp.setVisibility(visibleTaskbar == mTaskbarViewInApp
                ? VISIBLE : INVISIBLE);
        mTaskbarViewOnHome.setVisibility(visibleTaskbar == mTaskbarViewOnHome
                ? VISIBLE : INVISIBLE);
        mLauncher.getHotseat().setIconsAlpha(visibleTaskbar != mTaskbarViewInApp ? 1f : 0f);
    }

    private void blockTaskbarTouchesForIme(boolean block) {
        mTaskbarViewOnHome.setTouchesEnabled(!block);
        mTaskbarViewInApp.setTouchesEnabled(!block);
    }

    /**
     * Returns the ratio of the taskbar icon size on home vs in an app.
     */
    public float getTaskbarScaleOnHome() {
        DeviceProfile inAppDp = mTaskbarContainerView.getTaskbarActivityContext()
                .getDeviceProfile();
        DeviceProfile onHomeDp = ActivityContext.lookupContext(mTaskbarViewOnHome.getContext())
                .getDeviceProfile();
        return (float) onHomeDp.cellWidthPx / inAppDp.cellWidthPx;
    }

    /**
     * Updates the TaskbarContainer to MATCH_PARENT vs original Taskbar size.
     */
    private void setTaskbarWindowFullscreen(boolean fullscreen) {
        setTaskbarWindowHeight(fullscreen ? MATCH_PARENT : mTaskbarSize.y);
    }

    /**
     * Updates the TaskbarContainer height (pass mTaskbarSize.y to reset).
     */
    private void setTaskbarWindowHeight(int height) {
        mWindowLayoutParams.width = mTaskbarSize.x;
        mWindowLayoutParams.height = height;
        mWindowManager.updateViewLayout(mTaskbarContainerView, mWindowLayoutParams);
    }

    /**
     * Contains methods that TaskbarStateHandler can call to interface with TaskbarController.
     */
    protected interface TaskbarStateHandlerCallbacks {
        AnimatedFloat getAlphaTarget();
        AnimatedFloat getScaleTarget();
        AnimatedFloat getTranslationYTarget();
    }

    /**
     * Contains methods that TaskbarAnimationController can call to interface with
     * TaskbarController.
     */
    protected interface TaskbarAnimationControllerCallbacks {
        void updateTaskbarBackgroundAlpha(float alpha);
        void updateTaskbarVisibilityAlpha(float alpha);
        void updateImeBarVisibilityAlpha(float alpha);
        void updateTaskbarScale(float scale);
        void updateTaskbarTranslationY(float translationY);
    }

    /**
     * Contains methods that TaskbarContainerView can call to interface with TaskbarController.
     */
    protected interface TaskbarContainerViewCallbacks {
        void onViewRemoved();
        boolean isTaskbarTouchable();
    }

    /**
     * Contains methods that TaskbarView can call to interface with TaskbarController.
     */
    protected interface TaskbarViewCallbacks {
        View.OnClickListener getItemOnClickListener();
        View.OnLongClickListener getItemOnLongClickListener();
        int getEmptyHotseatViewVisibility(TaskbarView taskbarView);
        /** Returns how much to scale non-icon elements such as spacing and dividers. */
        float getNonIconScale(TaskbarView taskbarView);
        void onItemPositionsChanged(TaskbarView taskbarView);
        void onNavigationButtonClick(@TaskbarButton int buttonType);
    }

    /**
     * Contains methods that TaskbarHotseatController can call to interface with TaskbarController.
     */
    protected interface TaskbarHotseatControllerCallbacks {
        void updateHotseatItems(ItemInfo[] hotseatItemInfos);
    }
}
