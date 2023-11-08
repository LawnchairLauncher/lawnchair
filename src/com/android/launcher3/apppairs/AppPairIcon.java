/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.launcher3.apppairs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.views.ActivityContext;

import java.util.Collections;
import java.util.Comparator;

/**
 * A {@link android.widget.FrameLayout} used to represent an app pair icon on the workspace.
 * <br>
 * The app pair icon is two parallel background rectangles with rounded corners. Icons of the two
 * member apps are set into these rectangles.
 */
public class AppPairIcon extends FrameLayout implements DraggableView {
    /**
     * Design specs -- the below ratios are in relation to the size of a standard app icon.
     */
    private static final float OUTER_PADDING_SCALE = 1 / 30f;
    private static final float INNER_PADDING_SCALE = 1 / 24f;
    private static final float MEMBER_ICON_SCALE = 11 / 30f;
    private static final float CENTER_CHANNEL_SCALE = 1 / 30f;
    private static final float BIG_RADIUS_SCALE = 1 / 5f;
    private static final float SMALL_RADIUS_SCALE = 1 / 15f;

    // App pair icons are slightly smaller than regular icons, so we pad the icon by this much on
    // each side.
    float mOuterPadding;
    // Inside of the icon, the two member apps are padded by this much.
    float mInnerPadding;
    // The two member apps have icons that are this big (in diameter).
    float mMemberIconSize;
    // The size of the center channel.
    float mCenterChannelSize;
    // The large outer radius of the background rectangles.
    float mBigRadius;
    // The small inner radius of the background rectangles.
    float mSmallRadius;
    // The app pairs icon appears differently in portrait and landscape.
    boolean mIsLandscape;

    private ActivityContext mActivity;
    // A view that holds the app pair's title.
    private BubbleTextView mAppPairName;
    // The underlying ItemInfo that stores info about the app pair members, etc.
    private FolderInfo mInfo;

    public AppPairIcon(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AppPairIcon(Context context) {
        super(context);
    }

    /**
     * Builds an AppPairIcon to be added to the Launcher.
     */
    public static AppPairIcon inflateIcon(int resId, ActivityContext activity,
            @Nullable ViewGroup group, FolderInfo appPairInfo) {
        DeviceProfile grid = activity.getDeviceProfile();
        LayoutInflater inflater = (group != null)
                ? LayoutInflater.from(group.getContext())
                : activity.getLayoutInflater();
        AppPairIcon icon = (AppPairIcon) inflater.inflate(resId, group, false);

        // Sort contents, so that left-hand app comes first
        Collections.sort(appPairInfo.contents, Comparator.comparingInt(a -> a.rank));

        icon.setClipToPadding(false);
        icon.setTag(appPairInfo);
        icon.setOnClickListener(activity.getItemOnClickListener());
        icon.mInfo = appPairInfo;
        icon.mActivity = activity;

        // Set up app pair title
        icon.mAppPairName = icon.findViewById(R.id.app_pair_icon_name);
        icon.mAppPairName.setCompoundDrawablePadding(0);
        FrameLayout.LayoutParams lp =
                (FrameLayout.LayoutParams) icon.mAppPairName.getLayoutParams();
        lp.topMargin = grid.iconSizePx + grid.iconDrawablePaddingPx;
        icon.mAppPairName.setText(appPairInfo.title);

        // Set up accessibility
        icon.setContentDescription(icon.getAccessibilityTitle(
                appPairInfo.contents.get(0).title, appPairInfo.contents.get(1).title));
        icon.setAccessibilityDelegate(activity.getAccessibilityDelegate());

        return icon;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        // Calculate device-specific measurements
        DeviceProfile grid = mActivity.getDeviceProfile();
        int defaultIconSize = grid.iconSizePx;
        mOuterPadding = OUTER_PADDING_SCALE * defaultIconSize;
        mInnerPadding = INNER_PADDING_SCALE * defaultIconSize;
        mMemberIconSize = MEMBER_ICON_SCALE * defaultIconSize;
        mCenterChannelSize = CENTER_CHANNEL_SCALE * defaultIconSize;
        mBigRadius = BIG_RADIUS_SCALE * defaultIconSize;
        mSmallRadius = SMALL_RADIUS_SCALE * defaultIconSize;
        mIsLandscape = grid.isLeftRightSplit;

        // Calculate drawable area position
        float leftBound = (canvas.getWidth() / 2f) - (defaultIconSize / 2f);
        float topBound = getPaddingTop();

        // Prepare to draw app pair icon background
        Drawable background = new AppPairIconBackground(getContext(), this);
        background.setBounds(0, 0, defaultIconSize, defaultIconSize);

        // Draw background
        canvas.save();
        canvas.translate(leftBound, topBound);
        background.draw(canvas);
        canvas.restore();

        // Prepare to draw icons
        WorkspaceItemInfo app1 = mInfo.contents.get(0);
        WorkspaceItemInfo app2 = mInfo.contents.get(1);
        Drawable app1Icon = app1.newIcon(getContext());
        Drawable app2Icon = app2.newIcon(getContext());
        app1Icon.setBounds(0, 0, defaultIconSize, defaultIconSize);
        app2Icon.setBounds(0, 0, defaultIconSize, defaultIconSize);

        // Draw first icon
        canvas.save();
        canvas.translate(leftBound, topBound);
        // The app icons are placed differently depending on device orientation.
        if (mIsLandscape) {
            canvas.translate(
                    (defaultIconSize / 2f) - (mCenterChannelSize / 2f) - mInnerPadding
                            - mMemberIconSize,
                    (defaultIconSize / 2f) - (mMemberIconSize / 2f)
            );
        } else {
            canvas.translate(
                    (defaultIconSize / 2f) - (mMemberIconSize / 2f),
                    (defaultIconSize / 2f) - (mCenterChannelSize / 2f) - mInnerPadding
                            - mMemberIconSize
            );

        }
        canvas.scale(MEMBER_ICON_SCALE, MEMBER_ICON_SCALE);
        app1Icon.draw(canvas);
        canvas.restore();

        // Draw second icon
        canvas.save();
        canvas.translate(leftBound, topBound);
        // The app icons are placed differently depending on device orientation.
        if (mIsLandscape) {
            canvas.translate(
                    (defaultIconSize / 2f) + (mCenterChannelSize / 2f) + mInnerPadding,
                    (defaultIconSize / 2f) - (mMemberIconSize / 2f)
            );
        } else {
            canvas.translate(
                    (defaultIconSize / 2f) - (mMemberIconSize / 2f),
                    (defaultIconSize / 2f) + (mCenterChannelSize / 2f) + mInnerPadding
            );
        }
        canvas.scale(MEMBER_ICON_SCALE, MEMBER_ICON_SCALE);
        app2Icon.draw(canvas);
        canvas.restore();
    }

    /**
     * Returns a formatted accessibility title for app pairs.
     */
    public String getAccessibilityTitle(CharSequence app1, CharSequence app2) {
        return getContext().getString(R.string.app_pair_name_format, app1, app2);
    }

    @Override
    public int getViewType() {
        return DRAGGABLE_ICON;
    }

    @Override
    public void getWorkspaceVisualDragBounds(Rect bounds) {
        mAppPairName.getIconBounds(bounds);
    }

    public FolderInfo getInfo() {
        return mInfo;
    }
}
