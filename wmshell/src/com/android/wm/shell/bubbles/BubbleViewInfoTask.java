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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.AsyncTask;
import android.util.Log;
import android.util.PathParser;
import android.view.LayoutInflater;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.graphics.ColorUtils;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.BubbleIconFactory;
import com.android.wm.shell.R;
import com.android.wm.shell.bubbles.bar.BubbleBarExpandedView;
import com.android.wm.shell.bubbles.bar.BubbleBarLayerView;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Simple task to inflate views & load necessary info to display a bubble.
 */
public class BubbleViewInfoTask extends AsyncTask<Void, Void, BubbleViewInfoTask.BubbleViewInfo> {
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

    private Bubble mBubble;
    private WeakReference<Context> mContext;
    private WeakReference<BubbleExpandedViewManager> mExpandedViewManager;
    private WeakReference<BubbleTaskViewFactory> mTaskViewFactory;
    private WeakReference<BubblePositioner> mPositioner;
    private WeakReference<BubbleStackView> mStackView;
    private WeakReference<BubbleBarLayerView> mLayerView;
    private BubbleIconFactory mIconFactory;
    private boolean mSkipInflation;
    private Callback mCallback;
    private Executor mMainExecutor;

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
            boolean skipInflation,
            Callback c,
            Executor mainExecutor) {
        mBubble = b;
        mContext = new WeakReference<>(context);
        mExpandedViewManager = new WeakReference<>(expandedViewManager);
        mTaskViewFactory = new WeakReference<>(taskViewFactory);
        mPositioner = new WeakReference<>(positioner);
        mStackView = new WeakReference<>(stackView);
        mLayerView = new WeakReference<>(layerView);
        mIconFactory = factory;
        mSkipInflation = skipInflation;
        mCallback = c;
        mMainExecutor = mainExecutor;
    }

    @Override
    protected BubbleViewInfo doInBackground(Void... voids) {
        if (!verifyState()) {
            // If we're in an inconsistent state, then switched modes and should just bail now.
            return null;
        }
        if (mLayerView.get() != null) {
            return BubbleViewInfo.populateForBubbleBar(mContext.get(), mExpandedViewManager.get(),
                    mTaskViewFactory.get(), mPositioner.get(), mLayerView.get(), mIconFactory,
                    mBubble, mSkipInflation);
        } else {
            return BubbleViewInfo.populate(mContext.get(), mExpandedViewManager.get(),
                    mTaskViewFactory.get(), mPositioner.get(), mStackView.get(), mIconFactory,
                    mBubble, mSkipInflation);
        }
    }

    @Override
    protected void onPostExecute(BubbleViewInfo viewInfo) {
        if (isCancelled() || viewInfo == null) {
            return;
        }

        mMainExecutor.execute(() -> {
            if (!verifyState()) {
                return;
            }
            mBubble.setViewInfo(viewInfo);
            if (mCallback != null) {
                mCallback.onBubbleViewsReady(mBubble);
            }
        });
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
        @Nullable Bubble.FlyoutMessage flyoutMessage;
        Bitmap bubbleBitmap;
        Bitmap badgeBitmap;

        @Nullable
        public static BubbleViewInfo populateForBubbleBar(Context c,
                BubbleExpandedViewManager expandedViewManager,
                BubbleTaskViewFactory taskViewFactory,
                BubblePositioner positioner,
                BubbleBarLayerView layerView,
                BubbleIconFactory iconFactory,
                Bubble b,
                boolean skipInflation) {
            BubbleViewInfo info = new BubbleViewInfo();

            if (!skipInflation && !b.isInflated()) {
                BubbleTaskView bubbleTaskView = b.getOrCreateBubbleTaskView(taskViewFactory);
                LayoutInflater inflater = LayoutInflater.from(c);
                info.bubbleBarExpandedView = (BubbleBarExpandedView) inflater.inflate(
                        R.layout.bubble_bar_expanded_view, layerView, false /* attachToRoot */);
                info.bubbleBarExpandedView.initialize(
                        expandedViewManager, positioner, false /* isOverflow */, bubbleTaskView);
            }

            if (!populateCommonInfo(info, c, b, iconFactory)) {
                // if we failed to update common fields return null
                return null;
            }

            return info;
        }

        @VisibleForTesting
        @Nullable
        public static BubbleViewInfo populate(Context c,
                BubbleExpandedViewManager expandedViewManager,
                BubbleTaskViewFactory taskViewFactory,
                BubblePositioner positioner,
                BubbleStackView stackView,
                BubbleIconFactory iconFactory,
                Bubble b,
                boolean skipInflation) {
            BubbleViewInfo info = new BubbleViewInfo();

            // View inflation: only should do this once per bubble
            if (!skipInflation && !b.isInflated()) {
                LayoutInflater inflater = LayoutInflater.from(c);
                info.imageView = (BadgedImageView) inflater.inflate(
                        R.layout.bubble_view, stackView, false /* attachToRoot */);
                info.imageView.initialize(positioner);

                BubbleTaskView bubbleTaskView = b.getOrCreateBubbleTaskView(taskViewFactory);
                info.expandedView = (BubbleExpandedView) inflater.inflate(
                        R.layout.bubble_expanded_view, stackView, false /* attachToRoot */);
                info.expandedView.initialize(
                        expandedViewManager, stackView, positioner, false /* isOverflow */,
                        bubbleTaskView);
            }

            if (!populateCommonInfo(info, c, b, iconFactory)) {
                // if we failed to update common fields return null
                return null;
            }

            // Flyout
            info.flyoutMessage = b.getFlyoutMessage();
            if (info.flyoutMessage != null) {
                info.flyoutMessage.senderAvatar =
                        loadSenderAvatar(c, info.flyoutMessage.senderIcon);
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
            BubbleViewInfo info, Context c, Bubble b, BubbleIconFactory iconFactory) {
        if (b.getShortcutInfo() != null) {
            info.shortcutInfo = b.getShortcutInfo();
        }

        // App name & app icon
        PackageManager pm = BubbleController.getPackageManagerForUser(c,
                b.getUser().getIdentifier());
        ApplicationInfo appInfo;
        Drawable badgedIcon;
        Drawable appIcon;
        try {
            appInfo = pm.getApplicationInfo(
                    b.getPackageName(),
                    PackageManager.MATCH_UNINSTALLED_PACKAGES
                            | PackageManager.MATCH_DISABLED_COMPONENTS
                            | PackageManager.MATCH_DIRECT_BOOT_UNAWARE
                            | PackageManager.MATCH_DIRECT_BOOT_AWARE);
            if (appInfo != null) {
                info.appName = String.valueOf(pm.getApplicationLabel(appInfo));
            }
            appIcon = pm.getApplicationIcon(b.getPackageName());
            badgedIcon = pm.getUserBadgedIcon(appIcon, b.getUser());
        } catch (PackageManager.NameNotFoundException exception) {
            // If we can't find package... don't think we should show the bubble.
            Log.w(TAG, "Unable to find package: " + b.getPackageName());
            return false;
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

    @Nullable
    static Drawable loadSenderAvatar(@NonNull final Context context, @Nullable final Icon icon) {
        Objects.requireNonNull(context);
        if (icon == null) return null;
        try {
            if (icon.getType() == Icon.TYPE_URI
                    || icon.getType() == Icon.TYPE_URI_ADAPTIVE_BITMAP) {
                context.grantUriPermission(context.getPackageName(),
                        icon.getUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            return icon.loadDrawable(context);
        } catch (Exception e) {
            Log.w(TAG, "loadSenderAvatar failed: " + e.getMessage());
            return null;
        }
    }
}
