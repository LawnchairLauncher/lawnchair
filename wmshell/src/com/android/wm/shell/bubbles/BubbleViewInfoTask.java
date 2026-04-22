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

package com.android.wm.shell.bubbles;

import static com.android.wm.shell.bubbles.BadgedImageView.DEFAULT_PATH_SIZE;
import static com.android.wm.shell.bubbles.BadgedImageView.WHITE_SCRIM_ALPHA;
import static com.android.wm.shell.bubbles.BubbleDebugConfig.TAG_BUBBLES;
import static com.android.wm.shell.bubbles.BubbleDebugConfig.TAG_WITH_CLASS_NAME;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BUBBLES;
import static com.android.wm.shell.shared.bubbles.FlyoutDrawableLoader.loadFlyoutDrawable;

import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.ShortcutInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.PathParser;
import android.view.LayoutInflater;
import android.view.View;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.graphics.ColorUtils;
import com.android.internal.protolog.ProtoLog;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.BubbleIconFactory;
import com.android.wm.shell.R;
import com.android.wm.shell.bubbles.appinfo.BubbleAppInfo;
import com.android.wm.shell.bubbles.appinfo.BubbleAppInfoProvider;
import com.android.wm.shell.bubbles.bar.BubbleBarExpandedView;
import com.android.wm.shell.bubbles.bar.BubbleBarLayerView;
import com.android.wm.shell.shared.handles.RegionSamplingHelper;

import java.lang.ref.WeakReference;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple task to inflate views & load necessary info to display a bubble.
 */
public class BubbleViewInfoTask {
    private static final String TAG = TAG_WITH_CLASS_NAME ? "BubbleViewInfoTask" : TAG_BUBBLES;

    /**
     * Callback to find out when the bubble has been inflated & necessary data loaded.
     */
    public interface Callback {
        /**
         * Called when data has been loaded for the bubble.
         */
        void onBubbleViewsReady(Bubble bubble);
    }

    private final Bubble mBubble;
    private final WeakReference<Context> mContext;
    private final WeakReference<BubbleExpandedViewManager> mExpandedViewManager;
    private final WeakReference<BubbleTaskViewFactory> mTaskViewFactory;
    private final WeakReference<BubblePositioner> mPositioner;
    private final WeakReference<BubbleStackView> mStackView;
    private final WeakReference<BubbleBarLayerView> mLayerView;
    private final BubbleIconFactory mIconFactory;
    private final boolean mSkipInflation;
    private final Callback mCallback;
    private final Executor mMainExecutor;
    private final Executor mBgExecutor;
    private final BubbleAppInfoProvider mAppInfoProvider;

    private final AtomicBoolean mStarted = new AtomicBoolean();
    private final AtomicBoolean mCancelled = new AtomicBoolean();
    private final AtomicBoolean mFinished = new AtomicBoolean();

    /**
     * Creates a task to load information for the provided {@link Bubble}. Once all info
     * is loaded, {@link Callback} is notified.
     */
    BubbleViewInfoTask(Bubble b,
            Context context,
            BubbleExpandedViewManager expandedViewManager,
            BubbleTaskViewFactory taskViewFactory,
            BubblePositioner positioner,
            @Nullable BubbleStackView stackView,
            @Nullable BubbleBarLayerView layerView,
            BubbleIconFactory factory,
            BubbleAppInfoProvider appInfoProvider,
            boolean skipInflation,
            Callback c,
            Executor mainExecutor,
            Executor bgExecutor) {
        mBubble = b;
        mContext = new WeakReference<>(context);
        mExpandedViewManager = new WeakReference<>(expandedViewManager);
        mTaskViewFactory = new WeakReference<>(taskViewFactory);
        mPositioner = new WeakReference<>(positioner);
        mStackView = new WeakReference<>(stackView);
        mLayerView = new WeakReference<>(layerView);
        mIconFactory = factory;
        mAppInfoProvider = appInfoProvider;
        mSkipInflation = skipInflation;
        mCallback = c;
        mMainExecutor = mainExecutor;
        mBgExecutor = bgExecutor;
    }

    /**
     * Load bubble view info in background using {@code bgExecutor} specified in constructor.
     * <br>
     * Use {@link #cancel()} to stop the task.
     *
     * @throws IllegalStateException if the task is already started
     */
    public void start() {
        verifyCanStart();
        if (mCancelled.get()) {
            // We got cancelled even before start was called. Exit early
            mFinished.set(true);
            return;
        }
        mBgExecutor.execute(() -> {
            if (mCancelled.get()) {
                // We got cancelled while background executor was busy and this was waiting
                mFinished.set(true);
                return;
            }
            BubbleViewInfo viewInfo = loadViewInfo();
            if (mCancelled.get()) {
                // Do not schedule anything on main executor if we got cancelled.
                // Loading view info involves inflating views and it is possible we get cancelled
                // during it.
                mFinished.set(true);
                return;
            }
            mMainExecutor.execute(() -> {
                // Before updating view info check that we did not get cancelled while waiting
                // main executor to pick up the work
                if (!mCancelled.get()) {
                    updateViewInfo(viewInfo);
                }
                mFinished.set(true);
            });
        });
    }

    private void verifyCanStart() {
        if (mStarted.getAndSet(true)) {
            throw new IllegalStateException("Task already started");
        }
    }

    /**
     * Load bubble view info synchronously.
     *
     * @throws IllegalStateException if the task is already started
     */
    public void startSync() {
        verifyCanStart();
        if (mCancelled.get()) {
            mFinished.set(true);
            return;
        }
        updateViewInfo(loadViewInfo());
        mFinished.set(true);
    }

    /**
     * Cancel the task. Stops the task from running if called before {@link #start()} or
     * {@link #startSync()}
     */
    public void cancel() {
        mCancelled.set(true);
    }

    /**
     * Return {@code true} when the task has completed loading the view info.
     */
    public boolean isFinished() {
        return mFinished.get();
    }

    @Nullable
    private BubbleViewInfo loadViewInfo() {
        if (!verifyState()) {
            // If we're in an inconsistent state, then switched modes and should just bail now.
            return null;
        }
        ProtoLog.v(WM_SHELL_BUBBLES, "Task loading bubble view info key=%s", mBubble.getKey());
        if (mLayerView.get() != null) {
            return BubbleViewInfo.populateForBubbleBar(mContext.get(), mTaskViewFactory.get(),
                    mLayerView.get(), mIconFactory, mBubble, mAppInfoProvider, mSkipInflation);
        } else {
            return BubbleViewInfo.populate(mContext.get(), mTaskViewFactory.get(),
                    mPositioner.get(), mStackView.get(), mIconFactory, mBubble, mAppInfoProvider,
                    mSkipInflation);
        }
    }

    private void updateViewInfo(@Nullable BubbleViewInfo viewInfo) {
        if (viewInfo == null || !verifyState()) {
            return;
        }
        ProtoLog.v(WM_SHELL_BUBBLES, "Task updating bubble view info key=%s", mBubble.getKey());
        if (!mBubble.isInflated()) {
            if (viewInfo.expandedView != null) {
                ProtoLog.v(WM_SHELL_BUBBLES, "Task initializing expanded view key=%s",
                        mBubble.getKey());
                viewInfo.expandedView.initialize(mExpandedViewManager.get(), mStackView.get(),
                        mPositioner.get(), false /* isOverflow */, viewInfo.taskView);
            } else if (viewInfo.bubbleBarExpandedView != null) {
                ProtoLog.v(WM_SHELL_BUBBLES, "Task initializing bubble bar expanded view key=%s",
                        mBubble.getKey());
                viewInfo.bubbleBarExpandedView.initialize(mExpandedViewManager.get(),
                        mPositioner.get(), false /* isOverflow */, mBubble,
                        viewInfo.taskView, mMainExecutor, mBgExecutor,
                        new RegionSamplingProvider() {
                            @Override
                            public RegionSamplingHelper createHelper(View sampledView,
                                    RegionSamplingHelper.SamplingCallback callback,
                                    Executor backgroundExecutor, Executor mainExecutor) {
                                return RegionSamplingProvider.super.createHelper(sampledView,
                                        callback, backgroundExecutor, mainExecutor);
                            }
                        });
            }
        }

        mBubble.setViewInfo(viewInfo);
        if (mCallback != null) {
            mCallback.onBubbleViewsReady(mBubble);
        }
    }

    private boolean verifyState() {
        if (mExpandedViewManager.get().isShowingAsBubbleBar()) {
            return mLayerView.get() != null;
        } else {
            return mStackView.get() != null;
        }
    }

    /**
     * Info necessary to render a bubble.
     */
    @VisibleForTesting
    public static class BubbleViewInfo {
        // TODO(b/273312602): for foldables it might make sense to populate all of the views

        // Only set if views where inflated as part of the task
        @Nullable BubbleTaskView taskView;

        // Always populated
        ShortcutInfo shortcutInfo;
        String appName;
        Bitmap rawBadgeBitmap;

        // Only populated when showing in taskbar
        @Nullable BubbleBarExpandedView bubbleBarExpandedView;

        // These are only populated when not showing in taskbar
        @Nullable BadgedImageView imageView;
        @Nullable BubbleExpandedView expandedView;
        int dotColor;
        Path dotPath;
        Bubble.FlyoutMessage flyoutMessage;
        Bitmap bubbleBitmap;
        Bitmap badgeBitmap;

        @Nullable
        public static BubbleViewInfo populateForBubbleBar(Context c,
                BubbleTaskViewFactory taskViewFactory,
                BubbleBarLayerView layerView,
                BubbleIconFactory iconFactory,
                Bubble b,
                BubbleAppInfoProvider appInfoProvider,
                boolean skipInflation) {
            BubbleViewInfo info = new BubbleViewInfo();

            if (!skipInflation && !b.isInflated()) {
                ProtoLog.v(WM_SHELL_BUBBLES, "Task inflating bubble bar views key=%s", b.getKey());
                info.taskView = b.getOrCreateBubbleTaskView(taskViewFactory);
                LayoutInflater inflater = LayoutInflater.from(c);
                info.bubbleBarExpandedView = (BubbleBarExpandedView) inflater.inflate(
                        R.layout.bubble_bar_expanded_view, layerView, false /* attachToRoot */);
            }

            if (!populateCommonInfo(info, c, b, iconFactory, appInfoProvider)) {
                // if we failed to update common fields return null
                return null;
            }

            // set the flyout message but don't load the avatar because we can't pass it on the
            // binder to launcher
            info.flyoutMessage = b.getFlyoutMessage();

            return info;
        }

        @VisibleForTesting
        @Nullable
        public static BubbleViewInfo populate(Context c,
                BubbleTaskViewFactory taskViewFactory,
                BubblePositioner positioner,
                BubbleStackView stackView,
                BubbleIconFactory iconFactory,
                Bubble b,
                BubbleAppInfoProvider appInfoProvider,
                boolean skipInflation) {
            BubbleViewInfo info = new BubbleViewInfo();

            // View inflation: only should do this once per bubble
            if (!skipInflation && !b.isInflated()) {
                ProtoLog.v(WM_SHELL_BUBBLES, "Task inflating bubble views key=%s", b.getKey());
                LayoutInflater inflater = LayoutInflater.from(c);
                info.imageView = (BadgedImageView) inflater.inflate(
                        R.layout.bubble_view, stackView, false /* attachToRoot */);
                info.imageView.initialize(positioner);

                info.taskView = b.getOrCreateBubbleTaskView(taskViewFactory);
                info.expandedView = (BubbleExpandedView) inflater.inflate(
                        R.layout.bubble_expanded_view, stackView, false /* attachToRoot */);
            }

            if (!populateCommonInfo(info, c, b, iconFactory, appInfoProvider)) {
                // if we failed to update common fields return null
                return null;
            }

            // Flyout
            info.flyoutMessage = b.getFlyoutMessage();
            if (info.flyoutMessage != null) {
                info.flyoutMessage.senderAvatar =
                        loadFlyoutDrawable(info.flyoutMessage.senderIcon, c);
            }
            return info;
        }
    }

    /**
     * Modifies the given {@code info} object and populates common fields in it.
     *
     * <p>This method returns {@code true} if the update was successful and {@code false} otherwise.
     * Callers should assume that the info object is unusable if the update was unsuccessful.
     */
    private static boolean populateCommonInfo(
            BubbleViewInfo info, Context c, Bubble b, BubbleIconFactory iconFactory,
            BubbleAppInfoProvider appInfoProvider) {
        if (b.getShortcutInfo() != null) {
            info.shortcutInfo = b.getShortcutInfo();
        }

        BubbleAppInfo appInfo = appInfoProvider.resolveAppInfo(c, b);
        if (appInfo == null) {
            return false;
        }

        Drawable badgedIcon = appInfo.getBadgedIcon();
        Drawable appIcon = appInfo.getAppIcon();
        if (appInfo.getAppName() != null) {
            info.appName = appInfo.getAppName();
        }

        Drawable bubbleDrawable = null;
        try {
            // Badged bubble image
            bubbleDrawable = iconFactory.getBubbleDrawable(c, info.shortcutInfo,
                    b.getIcon());
        } catch (Exception e) {
            // If we can't create the icon we'll default to the app icon
            Log.w(TAG, "Exception creating icon for the bubble: " + b.getKey());
        }

        if (bubbleDrawable == null) {
            // Default to app icon
            bubbleDrawable = appIcon;
        }

        BitmapInfo badgeBitmapInfo = iconFactory.getBadgeBitmap(badgedIcon,
                b.isImportantConversation());
        info.badgeBitmap = badgeBitmapInfo.icon;
        // Raw badge bitmap never includes the important conversation ring
        info.rawBadgeBitmap = b.isImportantConversation()
                ? iconFactory.getBadgeBitmap(badgedIcon, false).icon
                : badgeBitmapInfo.icon;

        float[] bubbleBitmapScale = new float[1];
        info.bubbleBitmap = iconFactory.getBubbleBitmap(bubbleDrawable, bubbleBitmapScale);

        // Dot color & placement
        Path iconPath = PathParser.createPathFromPathData(
                c.getResources().getString(com.android.internal.R.string.config_icon_mask));
        Matrix matrix = new Matrix();
        float scale = bubbleBitmapScale[0];
        float radius = DEFAULT_PATH_SIZE / 2f;
        matrix.setScale(scale /* x scale */, scale /* y scale */, radius /* pivot x */,
                radius /* pivot y */);
        iconPath.transform(matrix);
        info.dotPath = iconPath;
        info.dotColor = ColorUtils.blendARGB(badgeBitmapInfo.color,
                Color.WHITE, WHITE_SCRIM_ALPHA);
        return true;
    }
}
