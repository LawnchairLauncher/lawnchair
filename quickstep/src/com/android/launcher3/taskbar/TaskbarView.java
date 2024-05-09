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

import static android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;

import static com.android.launcher3.BubbleTextView.DISPLAY_TASKBAR;
import static com.android.launcher3.Flags.enableCursorHoverStates;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER;
import static com.android.launcher3.config.FeatureFlags.ENABLE_ALL_APPS_SEARCH_IN_TASKBAR;
import static com.android.launcher3.config.FeatureFlags.enableTaskbarPinning;
import static com.android.launcher3.icons.IconNormalizer.ICON_VISIBLE_AREA_FACTOR;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.quickstep.RecentsAnimationDeviceState.QUICKSTEP_TOUCH_SLOP_RATIO_TWO_BUTTON;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.DisplayCutout;
import android.view.InputDevice;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import androidx.annotation.DimenRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.apppairs.AppPairIcon;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.folder.PreviewBackground;
import com.android.launcher3.model.data.AppPairInfo;
import com.android.launcher3.model.data.CollectionInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.LauncherBindableItemsContainer;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.IconButtonView;
import com.android.quickstep.DeviceConfigWrapper;
import com.android.quickstep.RecentsAnimationDeviceState;
import com.android.quickstep.util.AssistStateManager;

import java.util.function.Predicate;

/**
 * Hosts the Taskbar content such as Hotseat and Recent Apps. Drawn on top of other apps.
 */
public class TaskbarView extends FrameLayout implements FolderIcon.FolderIconParent, Insettable,
        DeviceProfile.OnDeviceProfileChangeListener {
    private static final String TAG = "TaskbarView";

    private static final Rect sTmpRect = new Rect();

    private final int[] mTempOutLocation = new int[2];
    private final Rect mIconLayoutBounds;
    private final int mIconTouchSize;
    private final int mItemMarginLeftRight;
    private final int mItemPadding;
    private final int mFolderLeaveBehindColor;
    private final boolean mIsRtl;
    private final boolean mIsTransientTaskbar;

    private final TaskbarActivityContext mActivityContext;
    private final RecentsAnimationDeviceState mDeviceState;

    // Initialized in init.
    private TaskbarViewCallbacks mControllerCallbacks;
    private View.OnClickListener mIconClickListener;
    private View.OnLongClickListener mIconLongClickListener;

    // Only non-null when the corresponding Folder is open.
    private @Nullable FolderIcon mLeaveBehindFolderIcon;

    // Only non-null when device supports having an All Apps button.
    private @Nullable IconButtonView mAllAppsButton;
    private Runnable mAllAppsTouchRunnable;
    private long mAllAppsButtonTouchDelayMs;
    private boolean mAllAppsTouchTriggered;
    private MotionEvent mCurrentDownEvent;
    private float mTouchSlopSquared;

    // Only non-null when device supports having an All Apps button.
    private @Nullable IconButtonView mTaskbarDivider;

    private final View mQsb;

    private final float mTransientTaskbarMinWidth;

    private boolean mShouldTryStartAlign;

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
        mIsTransientTaskbar = DisplayController.isTransientTaskbar(mActivityContext)
                && !mActivityContext.isPhoneMode();
        mIsRtl = Utilities.isRtl(resources);
        mTransientTaskbarMinWidth = resources.getDimension(R.dimen.transient_taskbar_min_width);

        onDeviceProfileChanged(mActivityContext.getDeviceProfile());

        int actualMargin = resources.getDimensionPixelSize(R.dimen.taskbar_icon_spacing);
        int actualIconSize = mActivityContext.getDeviceProfile().taskbarIconSize;
        if (enableTaskbarPinning() && !mActivityContext.isThreeButtonNav()) {
            DeviceProfile deviceProfile = mActivityContext.getTransientTaskbarDeviceProfile();
            actualIconSize = deviceProfile.taskbarIconSize;
        }
        int visualIconSize = (int) (actualIconSize * ICON_VISIBLE_AREA_FACTOR);

        mIconTouchSize = Math.max(actualIconSize,
                resources.getDimensionPixelSize(R.dimen.taskbar_icon_min_touch_size));

        // We layout the icons to be of mIconTouchSize in width and height
        mItemMarginLeftRight = actualMargin - (mIconTouchSize - visualIconSize) / 2;

        // We always layout taskbar as a transient taskbar when we have taskbar pinning feature on,
        // then we scale and translate the icons to match persistent taskbar designs, so we use
        // taskbar icon size from current device profile to calculate correct item padding.
        mItemPadding = (mIconTouchSize - mActivityContext.getDeviceProfile().taskbarIconSize) / 2;
        mFolderLeaveBehindColor = Themes.getAttrColor(mActivityContext,
                android.R.attr.textColorTertiary);

        // Needed to draw folder leave-behind when opening one.
        setWillNotDraw(false);

        mAllAppsButton = (IconButtonView) LayoutInflater.from(context)
                .inflate(R.layout.taskbar_all_apps_button, this, false);
        mAllAppsButton.setIconDrawable(resources.getDrawable(
                getAllAppsButton(mIsTransientTaskbar)));
        mAllAppsButton.setPadding(mItemPadding, mItemPadding, mItemPadding, mItemPadding);
        mAllAppsButton.setForegroundTint(
                mActivityContext.getColor(R.color.all_apps_button_color));

        if (enableTaskbarPinning()) {
            mTaskbarDivider = (IconButtonView) LayoutInflater.from(context).inflate(
                    R.layout.taskbar_divider,
                    this, false);
            mTaskbarDivider.setIconDrawable(
                    resources.getDrawable(R.drawable.taskbar_divider_button));
            mTaskbarDivider.setPadding(mItemPadding, mItemPadding, mItemPadding, mItemPadding);
        }

        // TODO: Disable touch events on QSB otherwise it can crash.
        mQsb = LayoutInflater.from(context).inflate(R.layout.search_container_hotseat, this, false);

        // Default long press (touch) delay = 400ms
        mAllAppsButtonTouchDelayMs = ViewConfiguration.getLongPressTimeout();
        // Default touch slop
        mDeviceState = new RecentsAnimationDeviceState(mContext);
        mTouchSlopSquared = mDeviceState.getSquaredTouchSlop(
                QUICKSTEP_TOUCH_SLOP_RATIO_TWO_BUTTON, 1);
    }

    @DrawableRes
    private int getAllAppsButton(boolean isTransientTaskbar) {
        boolean shouldSelectTransientIcon =
                (isTransientTaskbar || enableTaskbarPinning())
                && !mActivityContext.isThreeButtonNav();
        if (ENABLE_ALL_APPS_SEARCH_IN_TASKBAR.get()) {
            return shouldSelectTransientIcon
                    ? R.drawable.ic_transient_taskbar_all_apps_search_button
                    : R.drawable.ic_taskbar_all_apps_search_button;
        } else {
            return shouldSelectTransientIcon
                    ? R.drawable.ic_transient_taskbar_all_apps_button
                    : R.drawable.ic_taskbar_all_apps_button;
        }
    }

    @DimenRes
    public int getAllAppsButtonTranslationXOffset(boolean isTransientTaskbar) {
        if (isTransientTaskbar) {
            return R.dimen.transient_taskbar_all_apps_button_translation_x_offset;
        } else {
            return ENABLE_ALL_APPS_SEARCH_IN_TASKBAR.get()
                    ? R.dimen.taskbar_all_apps_search_button_translation_x_offset
                    : R.dimen.taskbar_all_apps_button_translation_x_offset;
        }
    }

    @Override
    public void setVisibility(int visibility) {
        boolean changed = getVisibility() != visibility;
        super.setVisibility(visibility);
        if (changed && mControllerCallbacks != null) {
            mControllerCallbacks.notifyVisibilityChanged();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mActivityContext.addOnDeviceProfileChangeListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mActivityContext.removeOnDeviceProfileChangeListener(this);
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile dp) {
        mShouldTryStartAlign = mActivityContext.isThreeButtonNav() && dp.startAlignTaskbar;
    }

    @Override
    public boolean performAccessibilityActionInternal(int action, Bundle arguments) {
        if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) {
            announceForAccessibility(mContext.getString(R.string.taskbar_a11y_shown_title));
        } else if (action == AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS) {
            announceForAccessibility(mContext.getString(R.string.taskbar_a11y_hidden_title));
        }
        return super.performAccessibilityActionInternal(action, arguments);

    }

    protected void announceAccessibilityChanges() {
        this.performAccessibilityAction(
                isVisibleToUser() ? AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS
                        : AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null);

        ActivityContext.lookupContext(getContext()).getDragLayer()
                .sendAccessibilityEvent(TYPE_WINDOW_CONTENT_CHANGED);
    }

    /**
     * Returns the icon touch size.
     */
    public int getIconTouchSize() {
        return mIconTouchSize;
    }

    protected void init(TaskbarViewCallbacks callbacks) {
        // set taskbar pane title so that accessibility service know it window and focuses.
        setAccessibilityPaneTitle(getContext().getString(R.string.taskbar_a11y_title));
        mControllerCallbacks = callbacks;
        mIconClickListener = mControllerCallbacks.getIconOnClickListener();
        mIconLongClickListener = mControllerCallbacks.getIconOnLongClickListener();

        if (mAllAppsButton != null) {
            mAllAppsButton.setOnClickListener(this::onAllAppsButtonClick);
            mAllAppsButton.setOnLongClickListener(this::onAllAppsButtonLongClick);
            mAllAppsButton.setOnTouchListener(this::onAllAppsButtonTouch);
            mAllAppsButton.setHapticFeedbackEnabled(
                    mControllerCallbacks.isAllAppsButtonHapticFeedbackEnabled());
            mAllAppsTouchRunnable = () -> {
                mControllerCallbacks.triggerAllAppsButtonLongClick();
                mAllAppsTouchTriggered = true;
            };
            AssistStateManager assistStateManager = AssistStateManager.INSTANCE.get(mContext);
            if (DeviceConfigWrapper.get().getCustomLpaaThresholds()
                    && assistStateManager.getLPNHDurationMillis().isPresent()) {
                mAllAppsButtonTouchDelayMs = assistStateManager.getLPNHDurationMillis().get();
            }
            if (DeviceConfigWrapper.get().getCustomLpaaThresholds()
                    && assistStateManager.getLPNHCustomSlopMultiplier().isPresent()) {
                mTouchSlopSquared = mDeviceState.getSquaredTouchSlop(
                        QUICKSTEP_TOUCH_SLOP_RATIO_TWO_BUTTON,
                        assistStateManager.getLPNHCustomSlopMultiplier().get());
            }
        }
        if (mTaskbarDivider != null && !mActivityContext.isThreeButtonNav()) {
            mTaskbarDivider.setOnLongClickListener(
                    mControllerCallbacks.getTaskbarDividerLongClickListener());
            mTaskbarDivider.setOnTouchListener(
                    mControllerCallbacks.getTaskbarDividerRightClickListener());
        }
    }

    private void removeAndRecycle(View view) {
        removeView(view);
        view.setOnClickListener(null);
        view.setOnLongClickListener(null);
        if (!(view.getTag() instanceof CollectionInfo)) {
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

            if (mTaskbarDivider != null) {
                removeView(mTaskbarDivider);
            }
        }
        removeView(mQsb);

        for (int i = 0; i < hotseatItemInfos.length; i++) {
            ItemInfo hotseatItemInfo = hotseatItemInfos[i];
            if (hotseatItemInfo == null) {
                continue;
            }

            // Replace any Hotseat views with the appropriate type if it's not already that type.
            final int expectedLayoutResId;
            boolean isCollection = false;
            if (hotseatItemInfo.isPredictedItem()) {
                expectedLayoutResId = R.layout.taskbar_predicted_app_icon;
            } else if (hotseatItemInfo instanceof CollectionInfo ci) {
                expectedLayoutResId = ci.itemType == ITEM_TYPE_APP_PAIR
                        ? R.layout.app_pair_icon
                        : R.layout.folder_icon;
                isCollection = true;
            } else {
                expectedLayoutResId = R.layout.taskbar_app_icon;
            }

            View hotseatView = null;
            while (nextViewIndex < getChildCount()) {
                hotseatView = getChildAt(nextViewIndex);

                // see if the view can be reused
                if ((hotseatView.getSourceLayoutResId() != expectedLayoutResId)
                        || (isCollection && (hotseatView.getTag() != hotseatItemInfo))) {
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
                if (isCollection) {
                    CollectionInfo collectionInfo = (CollectionInfo) hotseatItemInfo;
                    switch (hotseatItemInfo.itemType) {
                        case ITEM_TYPE_FOLDER:
                            hotseatView = FolderIcon.inflateFolderAndIcon(
                                    expectedLayoutResId, mActivityContext, this,
                                    (FolderInfo) collectionInfo);
                            ((FolderIcon) hotseatView).setTextVisible(false);
                            break;
                        case ITEM_TYPE_APP_PAIR:
                            hotseatView = AppPairIcon.inflateIcon(
                                    expectedLayoutResId, mActivityContext, this,
                                    (AppPairInfo) collectionInfo, DISPLAY_TASKBAR);
                            ((AppPairIcon) hotseatView).setTextVisible(false);
                            break;
                        default:
                            throw new IllegalStateException(
                                    "Unexpected item type: " + hotseatItemInfo.itemType);
                    }
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
            if (enableCursorHoverStates()) {
                setHoverListenerForIcon(hotseatView);
            }
            nextViewIndex++;
        }
        // Remove remaining views
        while (nextViewIndex < getChildCount()) {
            removeAndRecycle(getChildAt(nextViewIndex));
        }

        if (mAllAppsButton != null) {
            addView(mAllAppsButton, mIsRtl ? getChildCount() : 0);

            // if only all apps button present, don't include divider view.
            if (mTaskbarDivider != null && getChildCount() > 1) {
                addView(mTaskbarDivider, mIsRtl ? (getChildCount() - 1) : 1);
            }
        }
        if (mActivityContext.getDeviceProfile().isQsbInline) {
            addView(mQsb, mIsRtl ? getChildCount() : 0);
            // Always set QSB to invisible after re-adding.
            mQsb.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Sets OnClickListener and OnLongClickListener for the given view.
     */
    public void setClickAndLongClickListenersForIcon(View icon) {
        icon.setOnClickListener(mIconClickListener);
        icon.setOnLongClickListener(mIconLongClickListener);
        // Add right-click support to btv icons.
        icon.setOnTouchListener((v, event) -> {
            if (event.isFromSource(InputDevice.SOURCE_MOUSE)
                    && (event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0
                    && v instanceof BubbleTextView) {
                mActivityContext.showPopupMenuForIcon((BubbleTextView) v);
                return true;
            }
            return false;
        });
    }

    /**
     * Sets OnHoverListener for the given view.
     */
    private void setHoverListenerForIcon(View icon) {
        icon.setOnHoverListener(mControllerCallbacks.getIconOnHoverListener(icon));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int count = getChildCount();
        DeviceProfile deviceProfile = mActivityContext.getDeviceProfile();
        int spaceNeeded = getIconLayoutWidth();
        int navSpaceNeeded = deviceProfile.hotseatBarEndOffset;
        boolean layoutRtl = isLayoutRtl();
        int centerAlignIconEnd = right - (right - left - spaceNeeded) / 2;
        int iconEnd;

        if (mShouldTryStartAlign) {
            // Taskbar is aligned to the start
            int startSpacingPx = deviceProfile.inlineNavButtonsEndSpacingPx;

            if (layoutRtl) {
                iconEnd = right - startSpacingPx;
            } else {
                iconEnd = startSpacingPx + spaceNeeded;
            }
        } else {
            iconEnd = centerAlignIconEnd;
        }

        boolean needMoreSpaceForNav = layoutRtl
                ? navSpaceNeeded > (iconEnd - spaceNeeded)
                : iconEnd > (right - navSpaceNeeded);
        if (needMoreSpaceForNav) {
            // Add offset to account for nav bar when taskbar is centered
            int offset = layoutRtl
                    ? navSpaceNeeded - (centerAlignIconEnd - spaceNeeded)
                    : (right - navSpaceNeeded) - centerAlignIconEnd;

            iconEnd = centerAlignIconEnd + offset;
        }

        // Currently, we support only one device with display cutout and we only are concern about
        // it when the bottom rect is present and non empty
        DisplayCutout displayCutout = getDisplay().getCutout();
        if (displayCutout != null && !displayCutout.getBoundingRectBottom().isEmpty()) {
            Rect cutoutBottomRect = displayCutout.getBoundingRectBottom();
            // when cutout present at the bottom of screen align taskbar icons to cutout offset
            // if taskbar icon overlaps with cutout
            int taskbarIconLeftBound = iconEnd - spaceNeeded;
            int taskbarIconRightBound = iconEnd;

            boolean doesTaskbarIconsOverlapWithCutout =
                    taskbarIconLeftBound <= cutoutBottomRect.centerX()
                            && cutoutBottomRect.centerX() <= taskbarIconRightBound;

            if (doesTaskbarIconsOverlapWithCutout) {
                if (!layoutRtl) {
                    iconEnd = spaceNeeded + cutoutBottomRect.width();
                } else {
                    iconEnd = right - cutoutBottomRect.width();
                }
            }
        }

        sTmpRect.set(mIconLayoutBounds);

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
            } else if (child == mTaskbarDivider) {
                iconEnd += mItemMarginLeftRight;
                int iconStart = iconEnd - mIconTouchSize;
                child.layout(iconStart, mIconLayoutBounds.top, iconEnd, mIconLayoutBounds.bottom);
                iconEnd = iconStart + mItemMarginLeftRight;
            } else {
                iconEnd -= mItemMarginLeftRight;
                int iconStart = iconEnd - mIconTouchSize;
                child.layout(iconStart, mIconLayoutBounds.top, iconEnd, mIconLayoutBounds.bottom);
                iconEnd = iconStart - mItemMarginLeftRight;
            }
        }

        mIconLayoutBounds.left = iconEnd;

        if (mIconLayoutBounds.right - mIconLayoutBounds.left < mTransientTaskbarMinWidth) {
            int center = mIconLayoutBounds.centerX();
            int distanceFromCenter = (int) mTransientTaskbarMinWidth / 2;
            mIconLayoutBounds.right = center + distanceFromCenter;
            mIconLayoutBounds.left = center - distanceFromCenter;
        }

        if (!sTmpRect.equals(mIconLayoutBounds)) {
            mControllerCallbacks.notifyIconLayoutBoundsChanged();
        }
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
        int iconLayoutBoundsWidth =
                countExcludingQsb * (mItemMarginLeftRight * 2 + mIconTouchSize);

        if (enableTaskbarPinning() && countExcludingQsb > 1) {
            // We are removing 4 * mItemMarginLeftRight as there should be no space between
            // All Apps icon, divider icon, and first app icon in taskbar
            iconLayoutBoundsWidth -= mItemMarginLeftRight * 4;
        }
        return iconLayoutBoundsWidth;
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
     * Returns the taskbar divider in the taskbar.
     */
    @Nullable
    public View getTaskbarDividerView() {
        return mTaskbarDivider;
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
            canvas.translate(
                    mLeaveBehindFolderIcon.getLeft() + mLeaveBehindFolderIcon.getTranslationX(),
                    mLeaveBehindFolderIcon.getTop());
            PreviewBackground previewBackground = mLeaveBehindFolderIcon.getFolderBackground();
            previewBackground.drawLeaveBehind(canvas, mFolderLeaveBehindColor);
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
     *
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

    private boolean onAllAppsButtonTouch(View view, MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (mCurrentDownEvent != null) {
                    mCurrentDownEvent.recycle();
                }
                mCurrentDownEvent = MotionEvent.obtain(ev);
                mAllAppsTouchTriggered = false;
                MAIN_EXECUTOR.getHandler().postDelayed(
                        mAllAppsTouchRunnable, mAllAppsButtonTouchDelayMs);
                break;
            case MotionEvent.ACTION_MOVE:
                if (!MAIN_EXECUTOR.getHandler().hasCallbacks(mAllAppsTouchRunnable)
                        || mIsTransientTaskbar) {
                    break;
                }
                float dx = ev.getX() - mCurrentDownEvent.getX();
                float dy = ev.getY() - mCurrentDownEvent.getY();
                double distanceSquared = (dx * dx) + (dy * dy);
                if (distanceSquared > mTouchSlopSquared) {
                    Log.d(TAG, "Touch slop out");
                    cancelAllAppsButtonTouch();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cancelAllAppsButtonTouch();
        }
        return false;
    }

    private void cancelAllAppsButtonTouch() {
        MAIN_EXECUTOR.getHandler().removeCallbacks(mAllAppsTouchRunnable);
        // ACTION_UP is first triggered, then click listener / long-click listener is triggered on
        // the next frame, so we need to post twice and delay the reset.
        if (mAllAppsButton != null) {
            mAllAppsButton.post(() -> {
                mAllAppsButton.post(() -> {
                    mAllAppsTouchTriggered = false;
                });
            });
        }
    }

    private void onAllAppsButtonClick(View view) {
        if (!mAllAppsTouchTriggered) {
            mControllerCallbacks.triggerAllAppsButtonClick(view);
        }
    }

    // Handle long click from Switch Access and Voice Access
    private boolean onAllAppsButtonLongClick(View view) {
        if (!MAIN_EXECUTOR.getHandler().hasCallbacks(mAllAppsTouchRunnable)
                && !mAllAppsTouchTriggered) {
            mControllerCallbacks.triggerAllAppsButtonLongClick();
        }
        return true;
    }
}
