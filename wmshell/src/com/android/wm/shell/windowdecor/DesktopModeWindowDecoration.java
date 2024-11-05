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

package com.android.wm.shell.windowdecor;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.windowingModeToString;
import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_UP;

import static com.android.launcher3.icons.BaseIconFactory.MODE_DEFAULT;
import static com.android.wm.shell.windowdecor.DragResizeWindowGeometry.getFineResizeCornerSize;
import static com.android.wm.shell.windowdecor.DragResizeWindowGeometry.getLargeResizeCornerSize;
import static com.android.wm.shell.windowdecor.DragResizeWindowGeometry.getResizeEdgeHandleSize;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.WindowConfiguration.WindowingMode;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Trace;
import android.util.Log;
import android.util.Size;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.window.WindowContainerTransaction;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.policy.ScreenDecorationsUtils;
import com.android.launcher3.icons.BaseIconFactory;
import com.android.launcher3.icons.IconProvider;
import com.android.window.flags.Flags;
import com.android.wm.shell.R;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.shared.DesktopModeStatus;
import com.android.wm.shell.splitscreen.SplitScreenController;
import com.android.wm.shell.windowdecor.extension.TaskInfoKt;
import com.android.wm.shell.windowdecor.viewholder.AppHandleViewHolder;
import com.android.wm.shell.windowdecor.viewholder.AppHeaderViewHolder;
import com.android.wm.shell.windowdecor.viewholder.WindowDecorationViewHolder;

import kotlin.Unit;

import java.util.function.Supplier;

/**
 * Defines visuals and behaviors of a window decoration of a caption bar and shadows. It works with
 * {@link DesktopModeWindowDecorViewModel}.
 *
 * The shadow's thickness is 20dp when the window is in focus and 5dp when the window isn't.
 */
public class DesktopModeWindowDecoration extends WindowDecoration<WindowDecorLinearLayout> {
    private static final String TAG = "DesktopModeWindowDecoration";

    private final Handler mHandler;
    private final Choreographer mChoreographer;
    private final SyncTransactionQueue mSyncQueue;

    private WindowDecorationViewHolder mWindowDecorViewHolder;
    private View.OnClickListener mOnCaptionButtonClickListener;
    private View.OnTouchListener mOnCaptionTouchListener;
    private View.OnLongClickListener mOnCaptionLongClickListener;
    private View.OnGenericMotionListener mOnCaptionGenericMotionListener;
    private DragPositioningCallback mDragPositioningCallback;
    private DragResizeInputListener mDragResizeListener;
    private DragDetector mDragDetector;
    private Runnable mCurrentViewHostRunnable = null;
    private RelayoutParams mRelayoutParams = new RelayoutParams();
    private final WindowDecoration.RelayoutResult<WindowDecorLinearLayout> mResult =
            new WindowDecoration.RelayoutResult<>();
    private final Runnable mViewHostRunnable =
            () -> updateViewHost(mRelayoutParams, null /* onDrawTransaction */, mResult);

    private final Point mPositionInParent = new Point();
    private HandleMenu mHandleMenu;

    private MaximizeMenu mMaximizeMenu;

    private ResizeVeil mResizeVeil;
    private Bitmap mAppIconBitmap;
    private Bitmap mResizeVeilBitmap;

    private CharSequence mAppName;

    private ExclusionRegionListener mExclusionRegionListener;

    private final RootTaskDisplayAreaOrganizer mRootTaskDisplayAreaOrganizer;

    DesktopModeWindowDecoration(
            Context context,
            DisplayController displayController,
            ShellTaskOrganizer taskOrganizer,
            ActivityManager.RunningTaskInfo taskInfo,
            SurfaceControl taskSurface,
            Handler handler,
            Choreographer choreographer,
            SyncTransactionQueue syncQueue,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer) {
        this (context, displayController, taskOrganizer, taskInfo, taskSurface,
                handler, choreographer, syncQueue, rootTaskDisplayAreaOrganizer,
                SurfaceControl.Builder::new, SurfaceControl.Transaction::new,
                WindowContainerTransaction::new, SurfaceControl::new,
                new SurfaceControlViewHostFactory() {});
    }

    DesktopModeWindowDecoration(
            Context context,
            DisplayController displayController,
            ShellTaskOrganizer taskOrganizer,
            ActivityManager.RunningTaskInfo taskInfo,
            SurfaceControl taskSurface,
            Handler handler,
            Choreographer choreographer,
            SyncTransactionQueue syncQueue,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            Supplier<SurfaceControl.Builder> surfaceControlBuilderSupplier,
            Supplier<SurfaceControl.Transaction> surfaceControlTransactionSupplier,
            Supplier<WindowContainerTransaction> windowContainerTransactionSupplier,
            Supplier<SurfaceControl> surfaceControlSupplier,
            SurfaceControlViewHostFactory surfaceControlViewHostFactory) {
        super(context, displayController, taskOrganizer, taskInfo, taskSurface,
                surfaceControlBuilderSupplier, surfaceControlTransactionSupplier,
                windowContainerTransactionSupplier, surfaceControlSupplier,
                surfaceControlViewHostFactory);
        mHandler = handler;
        mChoreographer = choreographer;
        mSyncQueue = syncQueue;
        mRootTaskDisplayAreaOrganizer = rootTaskDisplayAreaOrganizer;
    }

    void setCaptionListeners(
            View.OnClickListener onCaptionButtonClickListener,
            View.OnTouchListener onCaptionTouchListener,
            View.OnLongClickListener onLongClickListener,
            View.OnGenericMotionListener onGenericMotionListener) {
        mOnCaptionButtonClickListener = onCaptionButtonClickListener;
        mOnCaptionTouchListener = onCaptionTouchListener;
        mOnCaptionLongClickListener = onLongClickListener;
        mOnCaptionGenericMotionListener = onGenericMotionListener;
    }

    void setExclusionRegionListener(ExclusionRegionListener exclusionRegionListener) {
        mExclusionRegionListener = exclusionRegionListener;
    }

    void setDragPositioningCallback(DragPositioningCallback dragPositioningCallback) {
        mDragPositioningCallback = dragPositioningCallback;
    }

    void setDragDetector(DragDetector dragDetector) {
        mDragDetector = dragDetector;
        mDragDetector.setTouchSlop(ViewConfiguration.get(mContext).getScaledTouchSlop());
    }

    @Override
    void relayout(ActivityManager.RunningTaskInfo taskInfo) {
        final SurfaceControl.Transaction t = mSurfaceControlTransactionSupplier.get();
        // The crop and position of the task should only be set when a task is fluid resizing. In
        // all other cases, it is expected that the transition handler positions and crops the task
        // in order to allow the handler time to animate before the task before the final
        // position and crop are set.
        final boolean shouldSetTaskPositionAndCrop = !DesktopModeStatus.isVeiledResizeEnabled()
                && mTaskDragResizer.isResizingOrAnimating();
        // For headers only (i.e. in freeform): use |applyStartTransactionOnDraw| so that the
        // transaction (that applies task crop) is synced with the buffer transaction (that draws
        // the View). Both will be shown on screen at the same, whereas applying them independently
        // causes flickering. See b/270202228.
        final boolean applyTransactionOnDraw =
                taskInfo.getWindowingMode() == WINDOWING_MODE_FREEFORM;
        relayout(taskInfo, t, t, applyTransactionOnDraw, shouldSetTaskPositionAndCrop);
        if (!applyTransactionOnDraw) {
            t.apply();
        }
    }

    void relayout(ActivityManager.RunningTaskInfo taskInfo,
            SurfaceControl.Transaction startT, SurfaceControl.Transaction finishT,
            boolean applyStartTransactionOnDraw, boolean shouldSetTaskPositionAndCrop) {
        Trace.beginSection("DesktopModeWindowDecoration#relayout");
        if (taskInfo.getWindowingMode() == WINDOWING_MODE_FREEFORM) {
            // The Task is in Freeform mode -> show its header in sync since it's an integral part
            // of the window itself - a delayed header might cause bad UX.
            relayoutInSync(taskInfo, startT, finishT, applyStartTransactionOnDraw,
                    shouldSetTaskPositionAndCrop);
        } else {
            // The Task is outside Freeform mode -> allow the handle view to be delayed since the
            // handle is just a small addition to the window.
            relayoutWithDelayedViewHost(taskInfo, startT, finishT, applyStartTransactionOnDraw,
                    shouldSetTaskPositionAndCrop);
        }
        Trace.endSection();
    }

    /** Run the whole relayout phase immediately without delay. */
    private void relayoutInSync(ActivityManager.RunningTaskInfo taskInfo,
            SurfaceControl.Transaction startT, SurfaceControl.Transaction finishT,
            boolean applyStartTransactionOnDraw, boolean shouldSetTaskPositionAndCrop) {
        // Clear the current ViewHost runnable as we will update the ViewHost here
        clearCurrentViewHostRunnable();
        updateRelayoutParamsAndSurfaces(taskInfo, startT, finishT, applyStartTransactionOnDraw,
                shouldSetTaskPositionAndCrop);
        if (mResult.mRootView != null) {
            updateViewHost(mRelayoutParams, startT, mResult);
        }
    }

    /**
     * Clear the current ViewHost runnable - to ensure it doesn't run once relayout params have been
     * updated.
     */
    private void clearCurrentViewHostRunnable() {
        if (mCurrentViewHostRunnable != null) {
            mHandler.removeCallbacks(mCurrentViewHostRunnable);
            mCurrentViewHostRunnable = null;
        }
    }

    /**
     * Relayout the window decoration but repost some of the work, to unblock the current callstack.
     */
    private void relayoutWithDelayedViewHost(ActivityManager.RunningTaskInfo taskInfo,
            SurfaceControl.Transaction startT, SurfaceControl.Transaction finishT,
            boolean applyStartTransactionOnDraw, boolean shouldSetTaskPositionAndCrop) {
        if (applyStartTransactionOnDraw) {
            throw new IllegalArgumentException(
                    "We cannot both sync viewhost ondraw and delay viewhost creation.");
        }
        // Clear the current ViewHost runnable as we will update the ViewHost here
        clearCurrentViewHostRunnable();
        updateRelayoutParamsAndSurfaces(taskInfo, startT, finishT,
                false /* applyStartTransactionOnDraw */, shouldSetTaskPositionAndCrop);
        if (mResult.mRootView == null) {
            // This means something blocks the window decor from showing, e.g. the task is hidden.
            // Nothing is set up in this case including the decoration surface.
            return;
        }
        // Store the current runnable so it can be removed if we start a new relayout.
        mCurrentViewHostRunnable = mViewHostRunnable;
        mHandler.post(mCurrentViewHostRunnable);
    }

    private void updateRelayoutParamsAndSurfaces(ActivityManager.RunningTaskInfo taskInfo,
            SurfaceControl.Transaction startT, SurfaceControl.Transaction finishT,
            boolean applyStartTransactionOnDraw, boolean shouldSetTaskPositionAndCrop) {
        Trace.beginSection("DesktopModeWindowDecoration#updateRelayoutParamsAndSurfaces");
        if (isHandleMenuActive()) {
            mHandleMenu.relayout(startT);
        }

        updateRelayoutParams(mRelayoutParams, mContext, taskInfo, applyStartTransactionOnDraw,
                shouldSetTaskPositionAndCrop);

        final WindowDecorLinearLayout oldRootView = mResult.mRootView;
        final SurfaceControl oldDecorationSurface = mDecorationContainerSurface;
        final WindowContainerTransaction wct = new WindowContainerTransaction();

        Trace.beginSection("DesktopModeWindowDecoration#relayout-updateViewsAndSurfaces");
        updateViewsAndSurfaces(mRelayoutParams, startT, finishT, wct, oldRootView, mResult);
        Trace.endSection();
        // After this line, mTaskInfo is up-to-date and should be used instead of taskInfo

        Trace.beginSection("DesktopModeWindowDecoration#relayout-applyWCT");
        mTaskOrganizer.applyTransaction(wct);
        Trace.endSection();

        if (mResult.mRootView == null) {
            // This means something blocks the window decor from showing, e.g. the task is hidden.
            // Nothing is set up in this case including the decoration surface.
            Trace.endSection(); // DesktopModeWindowDecoration#updateRelayoutParamsAndSurfaces
            return;
        }

        if (oldRootView != mResult.mRootView) {
            mWindowDecorViewHolder = createViewHolder();
        }
        Trace.beginSection("DesktopModeWindowDecoration#relayout-binding");
        mWindowDecorViewHolder.bindData(mTaskInfo);
        Trace.endSection();

        if (!mTaskInfo.isFocused) {
            closeHandleMenu();
            closeMaximizeMenu();
        }

        updateDragResizeListener(oldDecorationSurface);
        updateMaximizeMenu(startT);
        Trace.endSection(); // DesktopModeWindowDecoration#updateRelayoutParamsAndSurfaces
    }

    private void updateDragResizeListener(SurfaceControl oldDecorationSurface) {
        if (!isDragResizable(mTaskInfo)) {
            if (!mTaskInfo.positionInParent.equals(mPositionInParent)) {
                // We still want to track caption bar's exclusion region on a non-resizeable task.
                updateExclusionRegion();
            }
            closeDragResizeListener();
            return;
        }

        if (oldDecorationSurface != mDecorationContainerSurface || mDragResizeListener == null) {
            closeDragResizeListener();
            Trace.beginSection("DesktopModeWindowDecoration#relayout-DragResizeInputListener");
            mDragResizeListener = new DragResizeInputListener(
                    mContext,
                    mHandler,
                    mChoreographer,
                    mDisplay.getDisplayId(),
                    mDecorationContainerSurface,
                    mDragPositioningCallback,
                    mSurfaceControlBuilderSupplier,
                    mSurfaceControlTransactionSupplier,
                    mDisplayController);
            Trace.endSection();
        }

        final int touchSlop = ViewConfiguration.get(mResult.mRootView.getContext())
                .getScaledTouchSlop();
        mDragDetector.setTouchSlop(touchSlop);

        // If either task geometry or position have changed, update this task's
        // exclusion region listener
        final Resources res = mResult.mRootView.getResources();
        if (mDragResizeListener.setGeometry(
                new DragResizeWindowGeometry(mRelayoutParams.mCornerRadius,
                        new Size(mResult.mWidth, mResult.mHeight), getResizeEdgeHandleSize(res),
                        getFineResizeCornerSize(res), getLargeResizeCornerSize(res)), touchSlop)
                || !mTaskInfo.positionInParent.equals(mPositionInParent)) {
            updateExclusionRegion();
        }
    }

    private static boolean isDragResizable(ActivityManager.RunningTaskInfo taskInfo) {
        final boolean isFreeform =
                taskInfo.getWindowingMode() == WINDOWING_MODE_FREEFORM;
        return isFreeform && taskInfo.isResizeable;
    }

    private void updateMaximizeMenu(SurfaceControl.Transaction startT) {
        if (!isDragResizable(mTaskInfo) || !isMaximizeMenuActive()) {
            return;
        }
        if (!mTaskInfo.isVisible()) {
            closeMaximizeMenu();
        } else {
            mMaximizeMenu.positionMenu(calculateMaximizeMenuPosition(), startT);
        }
    }

    private WindowDecorationViewHolder createViewHolder() {
        if (mRelayoutParams.mLayoutResId == R.layout.desktop_mode_app_handle) {
            return new AppHandleViewHolder(
                    mResult.mRootView,
                    mOnCaptionTouchListener,
                    mOnCaptionButtonClickListener
            );
        } else if (mRelayoutParams.mLayoutResId
                == R.layout.desktop_mode_app_header) {
            loadAppInfoIfNeeded();
            return new AppHeaderViewHolder(
                    mResult.mRootView,
                    mOnCaptionTouchListener,
                    mOnCaptionButtonClickListener,
                    mOnCaptionLongClickListener,
                    mOnCaptionGenericMotionListener,
                    mAppName,
                    mAppIconBitmap,
                    () -> {
                        if (!isMaximizeMenuActive()) {
                            createMaximizeMenu();
                        }
                        return Unit.INSTANCE;
                    });
        }
        throw new IllegalArgumentException("Unexpected layout resource id");
    }

    @VisibleForTesting
    static void updateRelayoutParams(
            RelayoutParams relayoutParams,
            Context context,
            ActivityManager.RunningTaskInfo taskInfo,
            boolean applyStartTransactionOnDraw,
            boolean shouldSetTaskPositionAndCrop) {
        final int captionLayoutId = getDesktopModeWindowDecorLayoutId(taskInfo.getWindowingMode());
        final boolean isAppHeader =
                captionLayoutId == R.layout.desktop_mode_app_header;
        final boolean isAppHandle = captionLayoutId == R.layout.desktop_mode_app_handle;
        relayoutParams.reset();
        relayoutParams.mRunningTaskInfo = taskInfo;
        relayoutParams.mLayoutResId = captionLayoutId;
        relayoutParams.mCaptionHeightId = getCaptionHeightIdStatic(taskInfo.getWindowingMode());
        relayoutParams.mCaptionWidthId = getCaptionWidthId(relayoutParams.mLayoutResId);

        if (isAppHeader) {
            if (TaskInfoKt.isTransparentCaptionBarAppearance(taskInfo)) {
                // If the app is requesting to customize the caption bar, allow input to fall
                // through to the windows below so that the app can respond to input events on
                // their custom content.
                relayoutParams.mInputFeatures |= WindowManager.LayoutParams.INPUT_FEATURE_SPY;
            }
            // Report occluding elements as bounding rects to the insets system so that apps can
            // draw in the empty space in the center:
            //   First, the "app chip" section of the caption bar (+ some extra margins).
            final RelayoutParams.OccludingCaptionElement appChipElement =
                    new RelayoutParams.OccludingCaptionElement();
            appChipElement.mWidthResId = R.dimen.desktop_mode_customizable_caption_margin_start;
            appChipElement.mAlignment = RelayoutParams.OccludingCaptionElement.Alignment.START;
            relayoutParams.mOccludingCaptionElements.add(appChipElement);
            //   Then, the right-aligned section (drag space, maximize and close buttons).
            final RelayoutParams.OccludingCaptionElement controlsElement =
                    new RelayoutParams.OccludingCaptionElement();
            controlsElement.mWidthResId = R.dimen.desktop_mode_customizable_caption_margin_end;
            controlsElement.mAlignment = RelayoutParams.OccludingCaptionElement.Alignment.END;
            relayoutParams.mOccludingCaptionElements.add(controlsElement);
        } else if (isAppHandle) {
            // The focused decor (fullscreen/split) does not need to handle input because input in
            // the App Handle is handled by the InputMonitor in DesktopModeWindowDecorViewModel.
            relayoutParams.mInputFeatures
                    |= WindowManager.LayoutParams.INPUT_FEATURE_NO_INPUT_CHANNEL;
        }
        if (DesktopModeStatus.useWindowShadow(/* isFocusedWindow= */ taskInfo.isFocused)) {
            relayoutParams.mShadowRadiusId = taskInfo.isFocused
                    ? R.dimen.freeform_decor_shadow_focused_thickness
                    : R.dimen.freeform_decor_shadow_unfocused_thickness;
        }
        relayoutParams.mApplyStartTransactionOnDraw = applyStartTransactionOnDraw;
        relayoutParams.mSetTaskPositionAndCrop = shouldSetTaskPositionAndCrop;

        // The configuration used to layout the window decoration. A copy is made instead of using
        // the original reference so that the configuration isn't mutated on config changes and
        // diff checks can be made in WindowDecoration#relayout using the pre/post-relayout
        // configuration. See b/301119301.
        // TODO(b/301119301): consider moving the config data needed for diffs to relayout params
        // instead of using a whole Configuration as a parameter.
        final Configuration windowDecorConfig = new Configuration();
        if (Flags.enableAppHeaderWithTaskDensity() && isAppHeader) {
            // Should match the density of the task. The task may have had its density overridden
            // to be different that SysUI's.
            windowDecorConfig.setTo(taskInfo.configuration);
        } else if (DesktopModeStatus.useDesktopOverrideDensity()) {
            // The task has had its density overridden, but keep using the system's density to
            // layout the header.
            windowDecorConfig.setTo(context.getResources().getConfiguration());
        } else {
            windowDecorConfig.setTo(taskInfo.configuration);
        }
        relayoutParams.mWindowDecorConfig = windowDecorConfig;

        if (DesktopModeStatus.useRoundedCorners()) {
            relayoutParams.mCornerRadius =
                    (int) ScreenDecorationsUtils.getWindowCornerRadius(context);
        }
    }

    /**
     * If task has focused window decor, return the caption id of the fullscreen caption size
     * resource. Otherwise, return ID_NULL and caption width be set to task width.
     */
    private static int getCaptionWidthId(int layoutResId) {
        if (layoutResId == R.layout.desktop_mode_app_handle) {
            return R.dimen.desktop_mode_fullscreen_decor_caption_width;
        }
        return Resources.ID_NULL;
    }


    private PointF calculateMaximizeMenuPosition() {
        final PointF position = new PointF();
        final Resources resources = mContext.getResources();
        final DisplayLayout displayLayout =
                mDisplayController.getDisplayLayout(mTaskInfo.displayId);
        if (displayLayout == null) return position;

        final int displayWidth = displayLayout.width();
        final int displayHeight = displayLayout.height();
        final int captionHeight = getCaptionHeight(mTaskInfo.getWindowingMode());

        final ImageButton maximizeWindowButton =
                mResult.mRootView.findViewById(R.id.maximize_window);
        final int[] maximizeButtonLocation = new int[2];
        maximizeWindowButton.getLocationInWindow(maximizeButtonLocation);

        final int menuWidth = loadDimensionPixelSize(
                resources, R.dimen.desktop_mode_maximize_menu_width);
        final int menuHeight = loadDimensionPixelSize(
                resources, R.dimen.desktop_mode_maximize_menu_height);

        float menuLeft = (mPositionInParent.x + maximizeButtonLocation[0] - ((float) (menuWidth
                - maximizeWindowButton.getWidth()) / 2));
        float menuTop = (mPositionInParent.y + captionHeight);
        final float menuRight = menuLeft + menuWidth;
        final float menuBottom = menuTop + menuHeight;

        // If the menu is out of screen bounds, shift it up/left as needed
        if (menuRight > displayWidth) {
            menuLeft = (displayWidth - menuWidth);
        }
        if (menuBottom > displayHeight) {
            menuTop = (displayHeight - menuHeight);
        }

        return new PointF(menuLeft, menuTop);
    }

    boolean isHandleMenuActive() {
        return mHandleMenu != null;
    }

    boolean shouldResizeListenerHandleEvent(@NonNull MotionEvent e, @NonNull Point offset) {
        return mDragResizeListener != null && mDragResizeListener.shouldHandleEvent(e, offset);
    }

    boolean isHandlingDragResize() {
        return mDragResizeListener != null && mDragResizeListener.isHandlingDragResize();
    }

    private void loadAppInfoIfNeeded() {
        // TODO(b/337370277): move this to another thread.
        try {
            Trace.beginSection("DesktopModeWindowDecoration#loadAppInfoIfNeeded");
            if (mAppIconBitmap != null && mAppName != null) {
                return;
            }
            final ActivityInfo activityInfo = mTaskInfo.topActivityInfo;
            if (activityInfo == null) {
                Log.e(TAG, "Top activity info not found in task");
                return;
            }
            PackageManager pm = mContext.getApplicationContext().getPackageManager();
            final IconProvider provider = new IconProvider(mContext);
            final Drawable appIconDrawable = provider.getIcon(activityInfo);
            final BaseIconFactory headerIconFactory = createIconFactory(mContext,
                    R.dimen.desktop_mode_caption_icon_radius);
            mAppIconBitmap = headerIconFactory.createScaledBitmap(appIconDrawable, MODE_DEFAULT);

            final BaseIconFactory resizeVeilIconFactory = createIconFactory(mContext,
                    R.dimen.desktop_mode_resize_veil_icon_size);
            mResizeVeilBitmap = resizeVeilIconFactory
                    .createScaledBitmap(appIconDrawable, MODE_DEFAULT);

            final ApplicationInfo applicationInfo = activityInfo.applicationInfo;
            mAppName = pm.getApplicationLabel(applicationInfo);
        } finally {
            Trace.endSection();
        }
    }

    private BaseIconFactory createIconFactory(Context context, int dimensions) {
        final Resources resources = context.getResources();
        final int densityDpi = resources.getDisplayMetrics().densityDpi;
        final int iconSize = resources.getDimensionPixelSize(dimensions);
        return new BaseIconFactory(context, densityDpi, iconSize);
    }

    private void closeDragResizeListener() {
        if (mDragResizeListener == null) {
            return;
        }
        mDragResizeListener.close();
        mDragResizeListener = null;
    }

    /**
     * Create the resize veil for this task. Note the veil's visibility is View.GONE by default
     * until a resize event calls showResizeVeil below.
     */
    private void createResizeVeilIfNeeded() {
        if (mResizeVeil != null) return;
        loadAppInfoIfNeeded();
        mResizeVeil = new ResizeVeil(mContext, mDisplayController, mResizeVeilBitmap,
                mTaskSurface, mSurfaceControlTransactionSupplier, mTaskInfo);
    }

    /**
     * Show the resize veil.
     */
    public void showResizeVeil(Rect taskBounds) {
        createResizeVeilIfNeeded();
        mResizeVeil.showVeil(mTaskSurface, taskBounds, mTaskInfo);
    }

    /**
     * Show the resize veil.
     */
    public void showResizeVeil(SurfaceControl.Transaction tx, Rect taskBounds) {
        createResizeVeilIfNeeded();
        mResizeVeil.showVeil(tx, mTaskSurface, taskBounds, mTaskInfo, false /* fadeIn */);
    }

    /**
     * Set new bounds for the resize veil
     */
    public void updateResizeVeil(Rect newBounds) {
        mResizeVeil.updateResizeVeil(newBounds);
    }

    /**
     * Set new bounds for the resize veil
     */
    public void updateResizeVeil(SurfaceControl.Transaction tx, Rect newBounds) {
        mResizeVeil.updateResizeVeil(tx, newBounds);
    }

    /**
     * Fade the resize veil out.
     */
    public void hideResizeVeil() {
        mResizeVeil.hideVeil();
    }

    private void disposeResizeVeil() {
        if (mResizeVeil == null) return;
        mResizeVeil.dispose();
        mResizeVeil = null;
    }

    /**
     * Determine valid drag area for this task based on elements in the app chip.
     */
    @Override
    @NonNull
    Rect calculateValidDragArea() {
        final int appTextWidth = ((AppHeaderViewHolder)
                mWindowDecorViewHolder).getAppNameTextWidth();
        final int leftButtonsWidth = loadDimensionPixelSize(mContext.getResources(),
                R.dimen.desktop_mode_app_details_width_minus_text) + appTextWidth;
        final int requiredEmptySpace = loadDimensionPixelSize(mContext.getResources(),
                R.dimen.freeform_required_visible_empty_space_in_header);
        final int rightButtonsWidth = loadDimensionPixelSize(mContext.getResources(),
                R.dimen.desktop_mode_right_edge_buttons_width);
        final int taskWidth = mTaskInfo.configuration.windowConfiguration.getBounds().width();
        final DisplayLayout layout = mDisplayController.getDisplayLayout(mTaskInfo.displayId);
        final int displayWidth = layout.width();
        final Rect stableBounds = new Rect();
        layout.getStableBounds(stableBounds);
        return new Rect(
                determineMinX(leftButtonsWidth, rightButtonsWidth, requiredEmptySpace,
                        taskWidth),
                stableBounds.top,
                determineMaxX(leftButtonsWidth, rightButtonsWidth, requiredEmptySpace,
                        taskWidth, displayWidth),
                determineMaxY(requiredEmptySpace, stableBounds));
    }


    /**
     * Determine the lowest x coordinate of a freeform task. Used for restricting drag inputs.
     */
    private int determineMinX(int leftButtonsWidth, int rightButtonsWidth, int requiredEmptySpace,
            int taskWidth) {
        // Do not let apps with < 48dp empty header space go off the left edge at all.
        if (leftButtonsWidth + rightButtonsWidth + requiredEmptySpace > taskWidth) {
            return 0;
        }
        return -taskWidth + requiredEmptySpace + rightButtonsWidth;
    }

    /**
     * Determine the highest x coordinate of a freeform task. Used for restricting drag inputs.
     */
    private int determineMaxX(int leftButtonsWidth, int rightButtonsWidth, int requiredEmptySpace,
            int taskWidth, int displayWidth) {
        // Do not let apps with < 48dp empty header space go off the right edge at all.
        if (leftButtonsWidth + rightButtonsWidth + requiredEmptySpace > taskWidth) {
            return displayWidth - taskWidth;
        }
        return displayWidth - requiredEmptySpace - leftButtonsWidth;
    }

    /**
     * Determine the highest y coordinate of a freeform task. Used for restricting drag inputs.
     */
    private int determineMaxY(int requiredEmptySpace, Rect stableBounds) {
        return stableBounds.bottom - requiredEmptySpace;
    }


    /**
     * Create and display maximize menu window
     */
    void createMaximizeMenu() {
        mMaximizeMenu = new MaximizeMenu(mSyncQueue, mRootTaskDisplayAreaOrganizer,
                mDisplayController, mTaskInfo, mOnCaptionButtonClickListener,
                mOnCaptionGenericMotionListener, mOnCaptionTouchListener, mContext,
                calculateMaximizeMenuPosition(), mSurfaceControlTransactionSupplier);
        mMaximizeMenu.show();
    }

    /**
     * Close the maximize menu window
     */
    void closeMaximizeMenu() {
        if (!isMaximizeMenuActive()) return;
        mMaximizeMenu.close();
        mMaximizeMenu = null;
    }

    boolean isMaximizeMenuActive() {
        return mMaximizeMenu != null;
    }

    /**
     * Create and display handle menu window.
     */
    void createHandleMenu(SplitScreenController splitScreenController) {
        loadAppInfoIfNeeded();
        mHandleMenu = new HandleMenu.Builder(this)
                .setAppIcon(mAppIconBitmap)
                .setAppName(mAppName)
                .setOnClickListener(mOnCaptionButtonClickListener)
                .setOnTouchListener(mOnCaptionTouchListener)
                .setLayoutId(mRelayoutParams.mLayoutResId)
                .setWindowingButtonsVisible(DesktopModeStatus.canEnterDesktopMode(mContext))
                .setCaptionHeight(mResult.mCaptionHeight)
                .setDisplayController(mDisplayController)
                .setSplitScreenController(splitScreenController)
                .build();
        mWindowDecorViewHolder.onHandleMenuOpened();
        mHandleMenu.show();
    }

    /**
     * Close the handle menu window.
     */
    void closeHandleMenu() {
        if (!isHandleMenuActive()) return;
        mWindowDecorViewHolder.onHandleMenuClosed();
        mHandleMenu.close();
        mHandleMenu = null;
    }

    @Override
    void releaseViews(WindowContainerTransaction wct) {
        closeHandleMenu();
        closeMaximizeMenu();
        super.releaseViews(wct);
    }

    /**
     * Close an open handle menu if input is outside of menu coordinates
     *
     * @param ev the tapped point to compare against
     */
    void closeHandleMenuIfNeeded(MotionEvent ev) {
        if (!isHandleMenuActive()) return;

        PointF inputPoint = offsetCaptionLocation(ev);

        // If this is called before open_menu_button's onClick, we don't want to close
        // the menu since it will just reopen in onClick.
        final boolean pointInOpenMenuButton = pointInView(
                mResult.mRootView.findViewById(R.id.open_menu_button),
                inputPoint.x,
                inputPoint.y);

        if (!mHandleMenu.isValidMenuInput(inputPoint) && !pointInOpenMenuButton) {
            closeHandleMenu();
        }
    }

    /**
     * Close an open maximize menu if input is outside of menu coordinates
     *
     * @param ev the tapped point to compare against
     */
    void closeMaximizeMenuIfNeeded(MotionEvent ev) {
        if (!isMaximizeMenuActive()) return;

        if (!mMaximizeMenu.isValidMenuInput(ev)) {
            closeMaximizeMenu();
        }
    }

    boolean isFocused() {
        return mTaskInfo.isFocused;
    }

    /**
     * Offset the coordinates of a {@link MotionEvent} to be in the same coordinate space as caption
     *
     * @param ev the {@link MotionEvent} to offset
     * @return the point of the input in local space
     */
    private PointF offsetCaptionLocation(MotionEvent ev) {
        final PointF result = new PointF(ev.getX(), ev.getY());
        final ActivityManager.RunningTaskInfo taskInfo =
                mTaskOrganizer.getRunningTaskInfo(mTaskInfo.taskId);
        if (taskInfo == null) return result;
        final Point positionInParent = taskInfo.positionInParent;
        result.offset(-positionInParent.x, -positionInParent.y);
        return result;
    }

    /**
     * Checks if motion event occurs in the caption handle area of a focused caption (the caption on
     * a task in fullscreen or in multi-windowing mode). This should be used in cases where
     * onTouchListener will not work (i.e. when caption is in status bar area).
     *
     * @param ev       the {@link MotionEvent} to check
     * @return {@code true} if event is inside caption handle view, {@code false} if not
     */
    boolean checkTouchEventInFocusedCaptionHandle(MotionEvent ev) {
        if (isHandleMenuActive() || !(mWindowDecorViewHolder
                instanceof AppHandleViewHolder)) {
            return false;
        }

        return checkTouchEventInCaption(ev);
    }

    /**
     * Checks if touch event occurs in caption.
     *
     * @param ev       the {@link MotionEvent} to check
     * @return {@code true} if event is inside caption view, {@code false} if not
     */
    boolean checkTouchEventInCaption(MotionEvent ev) {
        final PointF inputPoint = offsetCaptionLocation(ev);
        return inputPoint.x >= mResult.mCaptionX
                && inputPoint.x <= mResult.mCaptionX + mResult.mCaptionWidth
                && inputPoint.y >= 0
                && inputPoint.y <= mResult.mCaptionHeight;
    }

    /**
     * Checks whether the touch event falls inside the customizable caption region.
     */
    boolean checkTouchEventInCustomizableRegion(MotionEvent ev) {
        return mResult.mCustomizableCaptionRegion.contains((int) ev.getRawX(), (int) ev.getRawY());
    }

    /**
     * Check a passed MotionEvent if it has occurred on any button related to this decor.
     * Note this should only be called when a regular onClick is not possible
     * (i.e. the button was clicked through status bar layer)
     *
     * @param ev the MotionEvent to compare
     */
    void checkTouchEvent(MotionEvent ev) {
        if (mResult.mRootView == null) return;
        final View caption = mResult.mRootView.findViewById(R.id.desktop_mode_caption);
        final View handle = caption.findViewById(R.id.caption_handle);
        final boolean inHandle = !isHandleMenuActive()
                && checkTouchEventInFocusedCaptionHandle(ev);
        final int action = ev.getActionMasked();
        if (action == ACTION_UP && inHandle) {
            handle.performClick();
        }
        if (isHandleMenuActive()) {
            mHandleMenu.checkMotionEvent(ev);
            closeHandleMenuIfNeeded(ev);
        }
    }

    /**
     * Updates hover and pressed status of views in this decoration. Should only be called
     * when status cannot be updated normally (i.e. the button is hovered through status
     * bar layer).
     * @param ev the MotionEvent to compare against.
     */
    void updateHoverAndPressStatus(MotionEvent ev) {
        if (mResult.mRootView == null) return;
        final View handle = mResult.mRootView.findViewById(R.id.caption_handle);
        final boolean inHandle = !isHandleMenuActive()
                && checkTouchEventInFocusedCaptionHandle(ev);
        final int action = ev.getActionMasked();
        // The comparison against ACTION_UP is needed for the cancel drag to desktop case.
        handle.setHovered(inHandle && action != ACTION_UP);
        // We want handle to remain pressed if the pointer moves outside of it during a drag.
        handle.setPressed((inHandle && action == ACTION_DOWN)
                || (handle.isPressed() && action != ACTION_UP && action != ACTION_CANCEL));
        if (isHandleMenuActive() && !isHandleMenuAboveStatusBar()) {
            mHandleMenu.checkMotionEvent(ev);
        }
    }

    private boolean isHandleMenuAboveStatusBar() {
        return Flags.enableAdditionalWindowsAboveStatusBar() && !mTaskInfo.isFreeform();
    }

    private boolean pointInView(View v, float x, float y) {
        return v != null && v.getLeft() <= x && v.getRight() >= x
                && v.getTop() <= y && v.getBottom() >= y;
    }

    @Override
    public void close() {
        closeDragResizeListener();
        closeHandleMenu();
        mExclusionRegionListener.onExclusionRegionDismissed(mTaskInfo.taskId);
        disposeResizeVeil();
        clearCurrentViewHostRunnable();
        super.close();
    }

    private static int getDesktopModeWindowDecorLayoutId(@WindowingMode int windowingMode) {
        return windowingMode == WINDOWING_MODE_FREEFORM
                ? R.layout.desktop_mode_app_header
                : R.layout.desktop_mode_app_handle;
    }

    private void updatePositionInParent() {
        mPositionInParent.set(mTaskInfo.positionInParent);
    }

    private void updateExclusionRegion() {
        // An outdated position in parent is one reason for this to be called; update it here.
        updatePositionInParent();
        mExclusionRegionListener
                .onExclusionRegionChanged(mTaskInfo.taskId, getGlobalExclusionRegion());
    }

    /**
     * Create a new exclusion region from the corner rects (if resizeable) and caption bounds
     * of this task.
     */
    private Region getGlobalExclusionRegion() {
        Region exclusionRegion;
        if (mDragResizeListener != null && mTaskInfo.isResizeable) {
            exclusionRegion = mDragResizeListener.getCornersRegion();
        } else {
            exclusionRegion = new Region();
        }
        exclusionRegion.union(new Rect(0, 0, mResult.mWidth,
                getCaptionHeight(mTaskInfo.getWindowingMode())));
        exclusionRegion.translate(mPositionInParent.x, mPositionInParent.y);
        return exclusionRegion;
    }

    int getCaptionX() {
        return mResult.mCaptionX;
    }

    @Override
    int getCaptionHeightId(@WindowingMode int windowingMode) {
        return getCaptionHeightIdStatic(windowingMode);
    }

    private static int getCaptionHeightIdStatic(@WindowingMode int windowingMode) {
        return windowingMode == WINDOWING_MODE_FULLSCREEN
                ? R.dimen.desktop_mode_fullscreen_decor_caption_height
                : R.dimen.desktop_mode_freeform_decor_caption_height;
    }

    private int getCaptionHeight(@WindowingMode int windowingMode) {
        return loadDimensionPixelSize(mContext.getResources(), getCaptionHeightId(windowingMode));
    }

    @Override
    int getCaptionViewId() {
        return R.id.desktop_mode_caption;
    }

    void setAnimatingTaskResize(boolean animatingTaskResize) {
        if (mRelayoutParams.mLayoutResId == R.layout.desktop_mode_app_handle) return;
        ((AppHeaderViewHolder) mWindowDecorViewHolder)
                .setAnimatingTaskResize(animatingTaskResize);
    }

    /** Called when there is a {@Link ACTION_HOVER_EXIT} on the maximize window button. */
    void onMaximizeWindowHoverExit() {
        ((AppHeaderViewHolder) mWindowDecorViewHolder)
                .onMaximizeWindowHoverExit();
    }

    /** Called when there is a {@Link ACTION_HOVER_ENTER} on the maximize window button. */
    void onMaximizeWindowHoverEnter() {
        ((AppHeaderViewHolder) mWindowDecorViewHolder)
                .onMaximizeWindowHoverEnter();
    }

    /** Called when there is a {@Link ACTION_HOVER_ENTER} on a view in the maximize menu. */
    void onMaximizeMenuHoverEnter(int id, MotionEvent ev) {
        mMaximizeMenu.onMaximizeMenuHoverEnter(id, ev);
    }

    /** Called when there is a {@Link ACTION_HOVER_MOVE} on a view in the maximize menu. */
    void onMaximizeMenuHoverMove(int id, MotionEvent ev) {
        mMaximizeMenu.onMaximizeMenuHoverMove(id, ev);
    }

    /** Called when there is a {@Link ACTION_HOVER_EXIT} on a view in the maximize menu. */
    void onMaximizeMenuHoverExit(int id, MotionEvent ev) {
        mMaximizeMenu.onMaximizeMenuHoverExit(id, ev);
    }


    @Override
    public String toString() {
        return "{"
                + "mPositionInParent=" + mPositionInParent + ", "
                + "taskId=" + mTaskInfo.taskId + ", "
                + "windowingMode=" + windowingModeToString(mTaskInfo.getWindowingMode()) + ", "
                + "isFocused=" + isFocused()
                + "}";
    }

    static class Factory {

        DesktopModeWindowDecoration create(
                Context context,
                DisplayController displayController,
                ShellTaskOrganizer taskOrganizer,
                ActivityManager.RunningTaskInfo taskInfo,
                SurfaceControl taskSurface,
                Handler handler,
                Choreographer choreographer,
                SyncTransactionQueue syncQueue,
                RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer) {
            return new DesktopModeWindowDecoration(
                    context,
                    displayController,
                    taskOrganizer,
                    taskInfo,
                    taskSurface,
                    handler,
                    choreographer,
                    syncQueue,
                    rootTaskDisplayAreaOrganizer);
        }
    }

    interface ExclusionRegionListener {
        /** Inform the implementing class of this task's change in region resize handles */
        void onExclusionRegionChanged(int taskId, Region region);

        /**
         * Inform the implementing class that this task no longer needs an exclusion region,
         * likely due to it closing.
         */
        void onExclusionRegionDismissed(int taskId);
    }
}
