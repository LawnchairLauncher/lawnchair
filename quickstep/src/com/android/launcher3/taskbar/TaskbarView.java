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

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.icons.ThemedIconDrawable;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.LauncherBindableItemsContainer;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.DoubleShadowBubbleTextView;

import java.util.function.Predicate;

/**
 * Hosts the Taskbar content such as Hotseat and Recent Apps. Drawn on top of other apps.
 */
public class TaskbarView extends FrameLayout implements FolderIcon.FolderIconParent, Insettable {
    private static final String TAG = TaskbarView.class.getSimpleName();

    private static final float TASKBAR_BACKGROUND_LUMINANCE = 0.30f;
    public int mThemeIconsBackground;

    private final int[] mTempOutLocation = new int[2];
    private final Rect mIconLayoutBounds;
    private final int mIconTouchSize;
    private final int mItemMarginLeftRight;
    private final int mItemPadding;
    private final boolean mIsRtl;

    private final TaskbarActivityContext mActivityContext;

    // Initialized in init.
    private TaskbarViewController.TaskbarViewCallbacks mControllerCallbacks;
    private View.OnClickListener mIconClickListener;
    private View.OnLongClickListener mIconLongClickListener;

    // Prevents dispatching touches to children if true
    private boolean mTouchEnabled = true;

    // Only non-null when the corresponding Folder is open.
    private @Nullable FolderIcon mLeaveBehindFolderIcon;

    // Only non-null when device supports having an All Apps button.
    private @Nullable View mAllAppsButton;

    private View mQsb;

    public TaskbarView(@NonNull Context context) {
        this(context, null);
    }

    public TaskbarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskbarView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public TaskbarView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mActivityContext = ActivityContext.lookupContext(context);
        mIconLayoutBounds = mActivityContext.getTransientTaskbarBounds();
        Resources resources = getResources();
        mIsRtl = Utilities.isRtl(resources);

        int actualMargin = resources.getDimensionPixelSize(R.dimen.taskbar_icon_spacing);
        int actualIconSize = mActivityContext.getDeviceProfile().iconSizePx;

        mIconTouchSize = Math.max(actualIconSize,
                resources.getDimensionPixelSize(R.dimen.taskbar_icon_min_touch_size));

        // We layout the icons to be of mIconTouchSize in width and height
        mItemMarginLeftRight = actualMargin - (mIconTouchSize - actualIconSize) / 2;
        mItemPadding = (mIconTouchSize - actualIconSize) / 2;

        // Needed to draw folder leave-behind when opening one.
        setWillNotDraw(false);

        mThemeIconsBackground = calculateThemeIconsBackground();

        if (FeatureFlags.ENABLE_ALL_APPS_IN_TASKBAR.get()) {
            mAllAppsButton = LayoutInflater.from(context)
                    .inflate(R.layout.taskbar_all_apps_button, this, false);
            mAllAppsButton.setPadding(mItemPadding, mItemPadding, mItemPadding, mItemPadding);
            mAllAppsButton.setScaleX(mIsRtl ? -1 : 1);
            if (mActivityContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_PC)) {
                mAllAppsButton.setVisibility(GONE);
            }
        }

        // TODO: Disable touch events on QSB otherwise it can crash.
        mQsb = LayoutInflater.from(context).inflate(R.layout.search_container_hotseat, this, false);

    }

    private int getColorWithGivenLuminance(int color, float luminance) {
        float[] colorHSL = new float[3];
        ColorUtils.colorToHSL(color, colorHSL);
        colorHSL[2] = luminance;
        return ColorUtils.HSLToColor(colorHSL);
    }

    private int calculateThemeIconsBackground() {
        int color = ThemedIconDrawable.getColors(mContext)[0];
        if (Utilities.isDarkTheme(mContext)) {
            return getColorWithGivenLuminance(color, TASKBAR_BACKGROUND_LUMINANCE);
        }
        return color;
    }

    protected void init(TaskbarViewController.TaskbarViewCallbacks callbacks) {
        mControllerCallbacks = callbacks;
        mIconClickListener = mControllerCallbacks.getIconOnClickListener();
        mIconLongClickListener = mControllerCallbacks.getIconOnLongClickListener();

        setOnLongClickListener(mControllerCallbacks.getBackgroundOnLongClickListener());

        if (mAllAppsButton != null) {
            mAllAppsButton.setOnClickListener(mControllerCallbacks.getAllAppsButtonClickListener());
        }
    }

    private void removeAndRecycle(View view) {
        removeView(view);
        view.setOnClickListener(null);
        view.setOnLongClickListener(null);
        if (!(view.getTag() instanceof FolderInfo)) {
            mActivityContext.getViewCache().recycleView(view.getSourceLayoutResId(), view);
        }
        view.setTag(null);
    }

    /**
     * Inflates/binds the Hotseat views to show in the Taskbar given their ItemInfos.
     */
    protected void updateHotseatItems(ItemInfo[] hotseatItemInfos) {
        int nextViewIndex = 0;
        int numViewsAnimated = 0;

        if (mAllAppsButton != null) {
            removeView(mAllAppsButton);
        }
        removeView(mQsb);


        for (int i = 0; i < hotseatItemInfos.length; i++) {
            ItemInfo hotseatItemInfo = hotseatItemInfos[i];
            if (hotseatItemInfo == null) {
                continue;
            }

            // Replace any Hotseat views with the appropriate type if it's not already that type.
            final int expectedLayoutResId;
            boolean isFolder = false;
            if (hotseatItemInfo.isPredictedItem()) {
                expectedLayoutResId = R.layout.taskbar_predicted_app_icon;
            } else if (hotseatItemInfo instanceof FolderInfo) {
                expectedLayoutResId = R.layout.folder_icon;
                isFolder = true;
            } else {
                expectedLayoutResId = R.layout.taskbar_app_icon;
            }

            View hotseatView = null;
            while (nextViewIndex < getChildCount()) {
                hotseatView = getChildAt(nextViewIndex);

                // see if the view can be reused
                if ((hotseatView.getSourceLayoutResId() != expectedLayoutResId)
                        || (isFolder && (hotseatView.getTag() != hotseatItemInfo))) {
                    // Unlike for BubbleTextView, we can't reapply a new FolderInfo after inflation,
                    // so if the info changes we need to reinflate. This should only happen if a new
                    // folder is dragged to the position that another folder previously existed.
                    removeAndRecycle(hotseatView);
                    hotseatView = null;
                } else {
                    // View found
                    break;
                }
            }

            if (hotseatView == null) {
                if (isFolder) {
                    FolderInfo folderInfo = (FolderInfo) hotseatItemInfo;
                    FolderIcon folderIcon = FolderIcon.inflateFolderAndIcon(expectedLayoutResId,
                            mActivityContext, this, folderInfo);
                    folderIcon.setTextVisible(false);
                    hotseatView = folderIcon;
                } else {
                    hotseatView = inflate(expectedLayoutResId);
                }
                LayoutParams lp = new LayoutParams(mIconTouchSize, mIconTouchSize);
                hotseatView.setPadding(mItemPadding, mItemPadding, mItemPadding, mItemPadding);
                addView(hotseatView, nextViewIndex, lp);
            }

            // Apply the Hotseat ItemInfos, or hide the view if there is none for a given index.
            if (hotseatView instanceof BubbleTextView
                    && hotseatItemInfo instanceof WorkspaceItemInfo) {
                BubbleTextView btv = (BubbleTextView) hotseatView;
                WorkspaceItemInfo workspaceInfo = (WorkspaceItemInfo) hotseatItemInfo;

                boolean animate = btv.shouldAnimateIconChange((WorkspaceItemInfo) hotseatItemInfo);
                btv.applyFromWorkspaceItem(workspaceInfo, animate, numViewsAnimated);
                if (animate) {
                    numViewsAnimated++;
                }
            }
            setClickAndLongClickListenersForIcon(hotseatView);
            nextViewIndex++;
        }
        // Remove remaining views
        while (nextViewIndex < getChildCount()) {
            removeAndRecycle(getChildAt(nextViewIndex));
        }

        if (mAllAppsButton != null) {
            int index = mIsRtl ? getChildCount() : 0;
            addView(mAllAppsButton, index);
        }
        if (mActivityContext.getDeviceProfile().isQsbInline) {
            addView(mQsb, mIsRtl ? getChildCount() : 0);
            // Always set QSB to invisible after re-adding.
            mQsb.setVisibility(View.INVISIBLE);
        }

        mThemeIconsBackground = calculateThemeIconsBackground();
        setThemedIconsBackgroundColor(mThemeIconsBackground);
    }

    /**
     * Traverse all the child views and change the background of themeIcons
     **/
    public void setThemedIconsBackgroundColor(int color) {
        for (View icon : getIconViews()) {
            if (icon instanceof DoubleShadowBubbleTextView) {
                DoubleShadowBubbleTextView textView = ((DoubleShadowBubbleTextView) icon);
                if (textView.getIcon() != null
                        && textView.getIcon() instanceof ThemedIconDrawable) {
                    ((ThemedIconDrawable) textView.getIcon()).changeBackgroundColor(color);
                }
            }
        }
    }

    /**
     * Sets OnClickListener and OnLongClickListener for the given view.
     */
    public void setClickAndLongClickListenersForIcon(View icon) {
        icon.setOnClickListener(mIconClickListener);
        icon.setOnLongClickListener(mIconLongClickListener);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int count = getChildCount();
        DeviceProfile deviceProfile = mActivityContext.getDeviceProfile();
        int spaceNeeded = getIconLayoutWidth();
        int navSpaceNeeded = deviceProfile.hotseatBarEndOffset;
        boolean layoutRtl = isLayoutRtl();
        int iconEnd = right - (right - left - spaceNeeded) / 2;
        boolean needMoreSpaceForNav = layoutRtl ?
                navSpaceNeeded > (iconEnd - spaceNeeded) :
                iconEnd > (right - navSpaceNeeded);
        if (needMoreSpaceForNav) {
            int offset = layoutRtl ?
                    navSpaceNeeded - (iconEnd - spaceNeeded) :
                    (right - navSpaceNeeded) - iconEnd;
            iconEnd += offset;
        }
        // Layout the children
        mIconLayoutBounds.right = iconEnd;
        mIconLayoutBounds.top = (bottom - top - mIconTouchSize) / 2;
        mIconLayoutBounds.bottom = mIconLayoutBounds.top + mIconTouchSize;
        for (int i = count; i > 0; i--) {
            View child = getChildAt(i - 1);
            if (child == mQsb) {
                int qsbStart;
                int qsbEnd;
                if (layoutRtl) {
                    qsbStart = iconEnd + mItemMarginLeftRight;
                    qsbEnd = qsbStart + deviceProfile.hotseatQsbWidth;
                } else {
                    qsbEnd = iconEnd - mItemMarginLeftRight;
                    qsbStart = qsbEnd - deviceProfile.hotseatQsbWidth;
                }
                int qsbTop = (bottom - top - deviceProfile.hotseatQsbHeight) / 2;
                int qsbBottom = qsbTop + deviceProfile.hotseatQsbHeight;
                child.layout(qsbStart, qsbTop, qsbEnd, qsbBottom);
            } else {
                iconEnd -= mItemMarginLeftRight;
                int iconStart = iconEnd - mIconTouchSize;
                child.layout(iconStart, mIconLayoutBounds.top, iconEnd, mIconLayoutBounds.bottom);
                iconEnd = iconStart - mItemMarginLeftRight;
            }
        }
        mIconLayoutBounds.left = iconEnd;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!mTouchEnabled) {
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        mControllerCallbacks.onInterceptTouchEvent(ev);
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mTouchEnabled) {
            return true;
        }
        if (mIconLayoutBounds.left <= event.getX()
                && event.getX() <= mIconLayoutBounds.right
                && !DisplayController.isTransientTaskbar(mActivityContext)) {
            // Don't allow long pressing between icons, or above/below them
            // unless its transient taskbar.
            mControllerCallbacks.clearTouchInProgress();
            return true;
        }
        if (mControllerCallbacks.onTouchEvent(event)) {
            int oldAction = event.getAction();
            try {
                event.setAction(MotionEvent.ACTION_CANCEL);
                return super.onTouchEvent(event);
            } finally {
                event.setAction(oldAction);
            }
        }
        return super.onTouchEvent(event);
    }

    public void setTouchesEnabled(boolean touchEnabled) {
        this.mTouchEnabled = touchEnabled;
        mControllerCallbacks.clearTouchInProgress();
    }

    /**
     * Returns whether the given MotionEvent, *in screen coorindates*, is within any Taskbar item's
     * touch bounds.
     */
    public boolean isEventOverAnyItem(MotionEvent ev) {
        getLocationOnScreen(mTempOutLocation);
        int xInOurCoordinates = (int) ev.getX() - mTempOutLocation[0];
        int yInOurCoorindates = (int) ev.getY() - mTempOutLocation[1];
        return isShown() && mIconLayoutBounds.contains(xInOurCoordinates, yInOurCoorindates);
    }

    public Rect getIconLayoutBounds() {
        return mIconLayoutBounds;
    }

    /**
     * Returns the space used by the icons
     */
    public int getIconLayoutWidth() {
        int countExcludingQsb = getChildCount();
        DeviceProfile deviceProfile = mActivityContext.getDeviceProfile();
        if (deviceProfile.isQsbInline) {
            countExcludingQsb--;
        }
        return countExcludingQsb * (mItemMarginLeftRight * 2 + mIconTouchSize);
    }

    /**
     * Returns the app icons currently shown in the taskbar.
     */
    public View[] getIconViews() {
        final int count = getChildCount();
        View[] icons = new View[count];
        for (int i = 0; i < count; i++) {
            icons[i] = getChildAt(i);
        }
        return icons;
    }

    /**
     * Returns the all apps button in the taskbar.
     */
    @Nullable
    public View getAllAppsButtonView() {
        return mAllAppsButton;
    }

    /**
     * Returns the QSB in the taskbar.
     */
    public View getQsb() {
        return mQsb;
    }

    // FolderIconParent implemented methods.

    @Override
    public void drawFolderLeaveBehindForIcon(FolderIcon child) {
        mLeaveBehindFolderIcon = child;
        invalidate();
    }

    @Override
    public void clearFolderLeaveBehind(FolderIcon child) {
        mLeaveBehindFolderIcon = null;
        invalidate();
    }

    // End FolderIconParent implemented methods.

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mLeaveBehindFolderIcon != null) {
            canvas.save();
            canvas.translate(mLeaveBehindFolderIcon.getLeft(), mLeaveBehindFolderIcon.getTop());
            mLeaveBehindFolderIcon.getFolderBackground().drawLeaveBehind(canvas);
            canvas.restore();
        }
    }

    private View inflate(@LayoutRes int layoutResId) {
        return mActivityContext.getViewCache().getView(layoutResId, mActivityContext, this);
    }

    @Override
    public void setInsets(Rect insets) {
        // Ignore, we just implement Insettable to draw behind system insets.
    }

    public boolean areIconsVisible() {
        // Consider the overall visibility
        return getVisibility() == VISIBLE;
    }

    /**
     * Maps {@code op} over all the child views.
     */
    public void mapOverItems(LauncherBindableItemsContainer.ItemOperator op) {
        // map over all the shortcuts on the taskbar
        for (int i = 0; i < getChildCount(); i++) {
            View item = getChildAt(i);
            if (op.evaluate((ItemInfo) item.getTag(), item)) {
                return;
            }
        }
    }

    /**
     * Finds the first icon to match one of the given matchers, from highest to lowest priority.
     * @return The first match, or All Apps button if no match was found.
     */
    public View getFirstMatch(Predicate<ItemInfo>... matchers) {
        for (Predicate<ItemInfo> matcher : matchers) {
            for (int i = 0; i < getChildCount(); i++) {
                View item = getChildAt(i);
                if (!(item.getTag() instanceof ItemInfo)) {
                    // Should only happen for All Apps button.
                    continue;
                }
                ItemInfo info = (ItemInfo) item.getTag();
                if (matcher.test(info)) {
                    return item;
                }
            }
        }
        return mAllAppsButton;
    }
}
