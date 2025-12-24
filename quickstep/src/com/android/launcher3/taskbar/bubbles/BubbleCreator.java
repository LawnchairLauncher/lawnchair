/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.taskbar.bubbles;

import static android.content.pm.LauncherApps.ShortcutQuery.FLAG_GET_PERSONS_DATA;
import static android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_CACHED;
import static android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC;
import static android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED_BY_ANY_LAUNCHER;

import static com.android.launcher3.icons.BaseIconFactory.MODE_WITH_SHADOW;
import static com.android.launcher3.icons.FastBitmapDrawable.WHITE_SCRIM_ALPHA;
import static com.android.wm.shell.shared.bubbles.FlyoutDrawableLoader.loadFlyoutDrawable;

import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.VisibleForTesting;

import com.android.internal.graphics.ColorUtils;
import com.android.launcher3.R;
import com.android.launcher3.icons.BaseIconFactory.IconOptions;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.BubbleIconFactory;
import com.android.launcher3.shortcuts.ShortcutRequest;
import com.android.launcher3.taskbar.bubbles.flyout.BubbleBarFlyoutMessage;
import com.android.wm.shell.shared.bubbles.BubbleInfo;
import com.android.wm.shell.shared.bubbles.ParcelableFlyoutMessage;

/**
 * Loads the necessary info to populate / present a bubble (name, icon, shortcut).
 */
public class BubbleCreator {

    private static final String TAG = BubbleCreator.class.getSimpleName();

    private final Context mContext;
    private final LauncherApps mLauncherApps;
    private final BubbleIconFactory mIconFactory;

    public BubbleCreator(Context context) {
        mContext = context;
        mLauncherApps = mContext.getSystemService(LauncherApps.class);
        mIconFactory = new BubbleIconFactory(context,
                context.getResources().getDimensionPixelSize(R.dimen.bubblebar_icon_size),
                context.getResources().getDimensionPixelSize(R.dimen.bubblebar_badge_size),
                context.getResources().getColor(R.color.important_conversation),
                context.getResources().getDimensionPixelSize(
                        com.android.internal.R.dimen.importance_ring_stroke_width));
    }

    /**
     * Creates a BubbleBarBubble object, including the view if needed, and populates it with
     * the info needed for presentation.
     *
     * @param context the context to use for inflation.
     * @param info the info to use to populate the bubble.
     * @param barView the parent view for the bubble (bubble is not added to the view).
     * @param existingBubble if a bubble exists already, this object gets updated with the new
     *                       info & returned (& any existing views are reused instead of inflating
     *                       new ones.
     */
    @Nullable
    public BubbleBarBubble populateBubble(Context context, BubbleInfo info, ViewGroup barView,
            @Nullable BubbleBarBubble existingBubble) {
        ApplicationInfo appInfo;
        try {
            appInfo = mLauncherApps.getApplicationInfo(
                    info.getPackageName(),
                    0,
                    new UserHandle(info.getUserId()));
        } catch (PackageManager.NameNotFoundException e) {
            // If we can't find package... don't think we should show the bubble.
            Log.w(TAG, "Unable to find packageName: " + info.getPackageName());
            return null;
        }
        if (appInfo == null) {
            Log.w(TAG, "Unable to find appInfo: " + info.getPackageName());
            return null;
        }
        // TODO: Can use BubbleInfo to get icon and label
        PackageManager pm = context.getPackageManager();
        String appName = info.getAppName();
        Drawable appIcon = appInfo.loadUnbadgedIcon(pm);

        return populateBubble(context, info, appIcon, appName, barView, existingBubble);
    }

    /**
     * @see #populateBubble(Context, BubbleInfo, ViewGroup, BubbleBarBubble)
     */
    @Nullable
    @VisibleForTesting
    public BubbleBarBubble populateBubble(Context context, BubbleInfo info,
            Drawable appIcon, String appName,
            ViewGroup barView,
            @Nullable BubbleBarBubble existingBubble) {
        boolean isImportantConvo = info.isImportantConversation();
        ShortcutRequest.QueryResult result = new ShortcutRequest(context,
                new UserHandle(info.getUserId()))
                .forPackage(info.getPackageName(), info.getShortcutId())
                .query(FLAG_MATCH_DYNAMIC
                        | FLAG_MATCH_PINNED_BY_ANY_LAUNCHER
                        | FLAG_MATCH_CACHED
                        | FLAG_GET_PERSONS_DATA);
        ShortcutInfo shortcutInfo = result.size() > 0 ? result.get(0) : null;
        if (shortcutInfo == null) {
            Log.w(TAG, "No shortcutInfo found for bubble: " + info.getKey()
                    + " with shortcutId: " + info.getShortcutId());
        }

        // Badged bubble image
        Drawable bubbleDrawable = mIconFactory.getBubbleDrawable(
                context, shortcutInfo, info.getIcon());
        if (bubbleDrawable == null) {
            // Default to app icon
            bubbleDrawable = appIcon;
        }
        BitmapInfo badgeBitmapInfo = mIconFactory.getBadgeBitmap(
                appIcon, new UserHandle(info.getUserId()), isImportantConvo);
        Bitmap bubbleBitmap = mIconFactory.getBubbleBitmap(bubbleDrawable);

        int dotColor = ColorUtils.blendARGB(badgeBitmapInfo.color,
                Color.WHITE, WHITE_SCRIM_ALPHA / 255f);

        final BubbleBarFlyoutMessage flyoutMessage =
                getFlyoutMessage(info.getParcelableFlyoutMessage());

        if (existingBubble == null) {
            LayoutInflater inflater = LayoutInflater.from(context);
            BubbleView bubbleView = (BubbleView) inflater.inflate(
                    R.layout.bubblebar_item_view, barView, false /* attachToRoot */);

            BubbleBarBubble bubble = new BubbleBarBubble(info, bubbleView,
                    badgeBitmapInfo, bubbleBitmap, dotColor, appName, flyoutMessage);
            bubbleView.setBubble(bubble);
            return bubble;
        } else {
            // If we already have a bubble (so it already has an inflated view), update it.
            existingBubble.setInfo(info);
            existingBubble.setBadge(badgeBitmapInfo);
            existingBubble.setIcon(bubbleBitmap);
            existingBubble.setDotColor(dotColor);
            existingBubble.setAppName(appName);
            existingBubble.setFlyoutMessage(flyoutMessage);
            return existingBubble;
        }
    }

    @Nullable
    private BubbleBarFlyoutMessage getFlyoutMessage(
            @Nullable ParcelableFlyoutMessage parcelableFlyoutMessage) {
        if (parcelableFlyoutMessage == null) {
            return null;
        }
        String title = parcelableFlyoutMessage.getTitle();
        String message = parcelableFlyoutMessage.getMessage();
        return new BubbleBarFlyoutMessage(
                loadFlyoutDrawable(parcelableFlyoutMessage.getIcon(), mContext),
                title == null ? "" : title,
                message == null ? "" : message);
    }

    /**
     * Creates the overflow view shown in the bubble bar.
     *
     * @param barView the parent view for the bubble (bubble is not added to the view).
     */
    public BubbleBarOverflow createOverflow(ViewGroup barView) {
        Bitmap bitmap = createOverflowBitmap();
        LayoutInflater inflater = LayoutInflater.from(mContext);
        BubbleView bubbleView = (BubbleView) inflater.inflate(
                R.layout.bubble_bar_overflow_button, barView, false /* attachToRoot */);
        BubbleBarOverflow overflow = new BubbleBarOverflow(bubbleView);
        bubbleView.setOverflow(overflow, bitmap);
        return overflow;
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private Bitmap createOverflowBitmap() {
        Drawable iconDrawable = mContext.getDrawable(R.drawable.bubble_ic_overflow_button);
        return mIconFactory.createBadgedIconBitmap(iconDrawable,
                new IconOptions()
                        .setBitmapGenerationMode(MODE_WITH_SHADOW)
                        .setExtractedColor(Color.TRANSPARENT)).icon;
    }

}
