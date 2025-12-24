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

import static android.window.DesktopModeFlags.ENABLE_TASKBAR_OVERFLOW;

import static com.android.launcher3.BubbleTextView.DISPLAY_TASKBAR;
import static com.android.launcher3.Flags.enableLauncherIconShapes;
import static com.android.launcher3.Flags.enableRecentsInTaskbar;
import static com.android.launcher3.Flags.enableTaskbarRecentsThemedIcons;
import static com.android.launcher3.Flags.refactorTaskbarUiState;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER;
import static com.android.launcher3.config.FeatureFlags.enableTaskbarPinning;
import static com.android.launcher3.icons.BitmapInfo.FLAG_THEMED;
import static com.android.launcher3.icons.IconNormalizer.ICON_VISIBLE_AREA_FACTOR;

import android.animation.LayoutTransition;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.ArraySet;
import android.util.AttributeSet;
import android.view.DisplayCutout;
import android.view.InputDevice;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Flags;
import com.android.launcher3.Insettable;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.apppairs.AppPairIcon;
import com.android.launcher3.celllayout.CellInfo;
import com.android.launcher3.deviceprofile.TaskbarProfile;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.folder.PreviewBackground;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.BitmapInfo.DrawableCreationFlags;
import com.android.launcher3.icons.IconShape;
import com.android.launcher3.model.data.AppPairInfo;
import com.android.launcher3.model.data.CollectionInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.taskbar.customization.TaskbarAllAppsButtonContainer;
import com.android.launcher3.taskbar.customization.TaskbarDividerContainer;
import com.android.launcher3.taskbar.customization.TaskbarIconsContainer;
import com.android.launcher3.taskbar.handoff.HandoffSuggestion;
import com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.PredictedAppIcon;
import com.android.quickstep.util.GroupTask;
import com.android.quickstep.util.SingleTask;
import com.android.quickstep.util.SplitTask;
import com.android.quickstep.views.TaskViewType;
import com.android.systemui.shared.recents.model.Task;
import com.android.wm.shell.shared.bubbles.BubbleBarLocation;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.patrykmichalik.opto.core.PreferenceExtensionsKt;
import app.lawnchair.hotseat.HotseatMode;
import app.lawnchair.preferences2.PreferenceManager2;
import app.lawnchair.theme.color.tokens.ColorTokens;

/**
 * Hosts the Taskbar content such as Hotseat and Recent Apps. Drawn on top of other apps.
 */
public class TaskbarView extends FrameLayout implements FolderIcon.FolderIconParent, Insettable,
        DeviceProfile.OnDeviceProfileChangeListener {
    private static final Rect sTmpRect = new Rect();
    private final Rect mIconLayoutBounds;
    private final int mIconTouchSize;
    private final int mItemMarginLeftRight;
    private final int mItemPadding;
    private final int mFolderLeaveBehindColor;
    private final int[] mFirstIconViewLocation = new int[2];
    private final int[] mLastIconViewLocation = new int[2];
    private final boolean mIsRtl;

    private final TaskbarUiState mTaskbarUiState;

    private final TaskbarActivityContext mActivityContext;
    @Nullable private BubbleBarLocation mBubbleBarLocation = null;

    // Initialized in init.
    private TaskbarViewCallbacks mControllerCallbacks;
    private View.OnClickListener mIconClickListener;
    private View.OnLongClickListener mIconLongClickListener;

    // Only non-null when the corresponding Folder is open.
    @Nullable private FolderIcon mLeaveBehindFolderIcon;

    // Only non-null when device supports having an All Apps button.
    private final TaskbarAllAppsButtonContainer mAllAppsButtonContainer;

    // Only non-null when device supports having a Divider button.
    @Nullable private TaskbarDividerContainer mTaskbarDividerContainer;

    // Only non-null when taskbar customization is enabled.
    @Nullable private TaskbarIconsContainer mHotseatIconsContainer;

    // Only non-null when device supports having a Taskbar Overflow button for pinned items.
    @Nullable private TaskbarOverflowView mTaskbarPinnedOverflowView;

    // Only non-null when device supports having a Taskbar Overflow button for recent tasks.
    @Nullable private TaskbarOverflowView mTaskbarRecentsOverflowView;

    private int mMaxNumIconsLimitForTest = -1;

    // Iterates within child views of TaskbarView
    private int mNextViewIndex = 0;
    // Iterates within child views of mHotseatIconsContainer (if non-null)
    private int mNextHotseatIndex = 0;

    public int getIgnoreTaskbarIconCount() {
        return mIgnoreTaskbarIconCount;
    }

    // TODO: clean it up in follow up cl with removal of taskbar icon alignment.
    // Only used for edge of 3 button navigation mode, where we need to hide icons which go
    // beyond the bounds.
    private int mIgnoreTaskbarIconCount = 0;
    /**
     * Whether the divider is between Hotseat icons and Recents,
     * instead of between All Apps button and Hotseat.
     */
    private boolean mAddedDividerForRecents;

    private final View mQsb;

    private final float mTransientTaskbarMinWidth;

    private boolean mShouldTryStartAlign;

    private int mMaxNumIcons = 0;
    private int mIdealNumIcons = 0;

    private final int mAllAppsButtonTranslationOffset;

    private int mNumStaticViews;

    private Set<GroupTask> mPrevRecentTasks = Collections.emptySet();
    private Set<GroupTask> mPrevOverflowTasks = Collections.emptySet();

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
        PreferenceManager2 preferenceManager2 = PreferenceManager2.getInstance(context);
        HotseatMode hotseatMode = PreferenceExtensionsKt.firstBlocking(preferenceManager2.getHotseatMode());
        mActivityContext = ActivityContext.lookupContext(context);
        mIconLayoutBounds = mActivityContext.getTransientTaskbarBounds();
        Resources resources = getResources();
        mIsRtl = Utilities.isRtl(resources);
        mTaskbarUiState = TaskbarUiStateMonitor.INSTANCE.get(context)
                .getTaskbarUiState(context.getDisplayId());
        if (refactorTaskbarUiState()) {
            mTaskbarUiState.setTaskbarViewIsShown(isShown());
        }
        mTransientTaskbarMinWidth = resources.getDimension(R.dimen.transient_taskbar_min_width);

        onDeviceProfileChanged(mActivityContext.getDeviceProfile());

        int actualMargin = resources.getDimensionPixelSize(R.dimen.taskbar_icon_spacing);
        int actualIconSize =
                mActivityContext.getDeviceProfile().getTaskbarProfile().getIconSize();
        if (enableTaskbarPinning() && canTransitionToTransientTaskbar()) {
            TaskbarProfile deviceProfile = mActivityContext.getTransientTaskbarProfile();
            actualIconSize = deviceProfile.getIconSize();
        }
        int visualIconSize = (int) (actualIconSize * ICON_VISIBLE_AREA_FACTOR);

        mIconTouchSize = Math.max(actualIconSize,
                resources.getDimensionPixelSize(R.dimen.taskbar_icon_min_touch_size));

        // We layout the icons to be of mIconTouchSize in width and height
        mItemMarginLeftRight = actualMargin - (mIconTouchSize - visualIconSize) / 2;

        if (Flags.enableTaskbarIconContainer()) {
            mHotseatIconsContainer =
                    TaskbarIconsContainer.create(context, mIconTouchSize, mItemMarginLeftRight);
        }

        // We always layout taskbar as a transient taskbar when we have taskbar pinning feature on,
        // then we scale and translate the icons to match persistent taskbar designs, so we use
        // taskbar icon size from current device profile to calculate correct item padding.
        mItemPadding = (mIconTouchSize - mActivityContext
                        .getDeviceProfile()
                        .getTaskbarProfile()
                        .getIconSize()) / 2;
        mFolderLeaveBehindColor = Themes.getAttrColor(mActivityContext,
                android.R.attr.textColorTertiary);

        // Needed to draw folder leave-behind when opening one.
        setWillNotDraw(false);

        mAllAppsButtonContainer = new TaskbarAllAppsButtonContainer(context);
        mAllAppsButtonTranslationOffset = (int) getResources().getDimension(
                mAllAppsButtonContainer.getAllAppsButtonTranslationXOffset(
                        mActivityContext.isTransientTaskbar()));

        if (enableTaskbarPinning() || enableRecentsInTaskbar()) {
            mTaskbarDividerContainer = new TaskbarDividerContainer(context);
        }

        if (ENABLE_TASKBAR_OVERFLOW.isTrue()) {
            mTaskbarRecentsOverflowView = TaskbarOverflowView.inflateIcon(
                    R.layout.taskbar_overflow_view, this, mIconTouchSize, mItemPadding);
            mTaskbarRecentsOverflowView.setId(R.id.taskbar_overflow_view);
        }

        if (TaskbarPopupController.canPinAppsOverflow()) {
            mTaskbarPinnedOverflowView = TaskbarOverflowView.inflateIcon(
                    R.layout.taskbar_overflow_view, this, mIconTouchSize, mItemPadding);
        }

        // TODO: Disable touch events on QSB otherwise it can crash.
        if (hotseatMode.isAvailable(context)) {
            mQsb = LayoutInflater.from(context).inflate(R.layout.search_container_hotseat, this, false);
        } else {
            mQsb = LayoutInflater.from(context).inflate(R.layout.empty_view, this, false);
        }
    }

    /**
     * @return the maximum number of 'icons' that can fit in the taskbar.
     */
    private int calculateMaxNumIcons() {
        DeviceProfile deviceProfile = mActivityContext.getDeviceProfile();
        int availableWidth = deviceProfile.getDeviceProperties().getWidthPx();
        int defaultEdgeMargin =
                (int) getResources().getDimension(deviceProfile.inv.inlineNavButtonsEndSpacing);
        int spaceForBubbleBar =
                Math.round(mControllerCallbacks.getBubbleBarMaxCollapsedWidthIfVisible());

        // Reserve space required for edge margins, or for navbar if shown. If task bar needs to be
        // center aligned with nav bar shown, reserve space on both sides.
        availableWidth -= Math.max(
                defaultEdgeMargin + spaceForBubbleBar,
                deviceProfile.getHotseatProfile().getBarEndOffset());
        availableWidth -= Math.max(
                defaultEdgeMargin + (mShouldTryStartAlign ? 0 : spaceForBubbleBar),
                mShouldTryStartAlign ? 0 : deviceProfile.getHotseatProfile().getBarEndOffset());

        // The space taken by an item icon used during layout.
        int iconSize = 2 * mItemMarginLeftRight + mIconTouchSize;

        int additionalIcons = 0;

        if (mTaskbarDividerContainer != null) {
            // Space for divider icon is reduced during layout compared to normal icon size, reserve
            // space for the divider separately.
            availableWidth -= iconSize - 4 * mItemMarginLeftRight;
            ++additionalIcons;
        }

        // All apps icon takes less space compared to normal icon size, reserve space for the icon
        // separately.
        boolean forceTransientTaskbarSize =
                enableTaskbarPinning() && canTransitionToTransientTaskbar();
        availableWidth -= iconSize - (int) getResources().getDimension(
                mAllAppsButtonContainer.getAllAppsButtonTranslationXOffset(
                        forceTransientTaskbarSize || mActivityContext.isTransientTaskbar()));
        ++additionalIcons;

        int maxIcons = Math.floorDiv(availableWidth, iconSize) + additionalIcons;
        return Math.min(maxIcons,
                mMaxNumIconsLimitForTest > 0 ? mMaxNumIconsLimitForTest : maxIcons);
    }

    /**
     * Whether the taskbar in the state context supports transition to a transient taskbar (e.g.
     * using a popup menu).
     */
    boolean canTransitionToTransientTaskbar() {
        return mActivityContext.getTaskbarFeatureEvaluator()
                .getSupportsTransitionToTransientTaskbar();
    }

    /**
     * Recalculates the max number of icons the taskbar view can show without entering overflow.
     * Returns whether the max number of icons changed and the change affects the number of icons
     * that should be shown in the taskbar.
     */
    boolean updateMaxNumIcons() {
        if (!ENABLE_TASKBAR_OVERFLOW.isTrue()) {
            return false;
        }
        int oldMaxNumIcons = mMaxNumIcons;
        mMaxNumIcons = calculateMaxNumIcons();
        return oldMaxNumIcons != mMaxNumIcons
                && (mIdealNumIcons > oldMaxNumIcons || mIdealNumIcons > mMaxNumIcons);
    }

    /**
     * Pre-adds views that are always children of this view for LayoutTransition support.
     * <p>
     * Normally these views are removed and re-added when updating hotseat and recents. This
     * approach does not behave well with LayoutTransition, so we instead need to add them
     * initially and avoid removing them during updates.
     */
    private int addStaticViews() {
        int numStaticViews = 1;
        addView(mAllAppsButtonContainer);

        if (mHotseatIconsContainer != null) {
            addView(mHotseatIconsContainer, mIsRtl ? 0 : numStaticViews);
            numStaticViews++;
        }

        if (mActivityContext.getDeviceProfile().isQsbInline) {
            addView(mQsb, mIsRtl ? numStaticViews : 0);
            mQsb.setVisibility(View.INVISIBLE);
            numStaticViews++;
        }
        return numStaticViews;
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
        mShouldTryStartAlign = mActivityContext.shouldStartAlignTaskbar();
    }

    /**
     * Returns the icon touch size.
     */
    public int getIconTouchSize() {
        return mIconTouchSize;
    }

    protected void init(TaskbarViewCallbacks callbacks) {
        if (Utilities.ATLEAST_P) {
            // set taskbar pane title so that accessibility service know it window and focuses.
            setAccessibilityPaneTitle(getContext().getString(R.string.taskbar_a11y_title));
        }
        mControllerCallbacks = callbacks;
        mIconClickListener = mControllerCallbacks.getIconOnClickListener();
        mIconLongClickListener = mControllerCallbacks.getIconOnLongClickListener();

        mAllAppsButtonContainer.setUpCallbacks(callbacks);
        if (mTaskbarRecentsOverflowView != null) {
            mTaskbarRecentsOverflowView.setOnClickListener(
                    mControllerCallbacks.getRecentsOverflowOnClickListener());
            mTaskbarRecentsOverflowView.setOnLongClickListener(
                    mControllerCallbacks.getRecentsOverflowOnLongClickListener());
            setHoverListenerForIcon(mTaskbarRecentsOverflowView);
        }

        if (mTaskbarPinnedOverflowView != null) {
            mTaskbarPinnedOverflowView.setOnClickListener(
                    mControllerCallbacks.getPinnedOverflowOnClickListener());
            mTaskbarPinnedOverflowView.setOnLongClickListener(
                    mControllerCallbacks.getPinnedOverflowOnLongClickListener());
            setHoverListenerForIcon(mTaskbarPinnedOverflowView);
        }

        if (ENABLE_TASKBAR_OVERFLOW.isTrue()) {
            mMaxNumIcons = calculateMaxNumIcons();
        }
    }

    void updatePinningPopupEventHandlers() {
        boolean supportsPinningPopup =
                mActivityContext.getTaskbarFeatureEvaluator().getSupportsPinningPopup();
        if (mTaskbarDividerContainer != null) {
            mTaskbarDividerContainer.setUpCallbacks(
                    supportsPinningPopup ? mControllerCallbacks : null);
        }

        if (Flags.showTaskbarPinningPopupFromAnywhere()) {
            setOnTouchListener(
                    supportsPinningPopup ? mControllerCallbacks.getTaskbarTouchListener() : null);
        }
    }

    private void removeAndRecycle(View view) {
        removeAndRecycle(this, view);
    }

    private void removeAndRecycle(ViewGroup parent, View view) {
        parent.removeView(view);
        view.setOnClickListener(null);
        view.setOnLongClickListener(null);
        if (!(view.getTag() instanceof CollectionInfo)) {
            mActivityContext.getViewCache().recycleView(view.getSourceLayoutResId(), view);
        }
        view.setTag(null);
    }

    /** Inflates/binds the hotseat items, recent tasks, and handoff suggestions to the view. */
    protected void updateItems(
        ItemInfo[] hotseatItemInfos,
        List<GroupTask> recentTasks,
        List<HandoffSuggestion> handoffSuggestions) {

        if (mActivityContext.isDestroyed()) return;
        // Filter out unsupported items.
        hotseatItemInfos = Arrays.stream(hotseatItemInfos)
                .filter(Objects::nonNull)
                .toArray(ItemInfo[]::new);
        recentTasks = recentTasks.stream()
                .filter(it -> it instanceof SingleTask || it instanceof SplitTask)
                .toList();

        if (mNumStaticViews == 0) {
            mNumStaticViews = addStaticViews();
        }

        // Skip static views and potential All Apps divider, if they are on the left.
        mNextViewIndex = mIsRtl ? 0 : mNumStaticViews;
        if (getChildAt(mNextViewIndex) == mTaskbarDividerContainer && !mAddedDividerForRecents) {
            mNextViewIndex++;
        }

        mIgnoreTaskbarIconCount = getIgnoreCountForTaskbarIcons(recentTasks.size(),
                hotseatItemInfos.length);

        // If pinned apps overflows, the maximum length of hotseat is still the same where the
        // last item is replaced by the overflow icon.
        final int hotseatItemLength = TaskbarPopupController.canPinAppsOverflow() ? Math.min(
                hotseatItemInfos.length,
                mActivityContext.getTaskbarSpecsEvaluator().getNumShownHotseatIcons())
                : hotseatItemInfos.length;

        // Update left section.
        if (mIsRtl) {
            updateHandoffSuggestions(handoffSuggestions);
            updateRecents(recentTasks.reversed(), hotseatItemLength);
        } else {
            updateHotseatItems(hotseatItemInfos);
        }

        // Now at theoretical position for recent apps divider.
        updateRecentsDivider(!recentTasks.isEmpty());
        if (getChildAt(mNextViewIndex) == mTaskbarDividerContainer) {
            mNextViewIndex++;
        }

        // Update right section.
        if (mIsRtl) {
            updateHotseatItems(hotseatItemInfos);
        } else {
            updateRecents(recentTasks, hotseatItemLength);
            updateHandoffSuggestions(handoffSuggestions);
        }

        // Recents divider takes priority.
        if (!mAddedDividerForRecents) {
            boolean allAppsDividerAllowed = !mActivityContext.isTaskbarShowingDesktopTasks();
            if (allAppsDividerAllowed) {
                updateAllAppsDivider();
            } else if (getChildAt(getExpectedAllAppsDividerIndex()) == mTaskbarDividerContainer) {
                removeView(mTaskbarDividerContainer);
            }
        }

        mAllAppsButtonContainer.updateTaskbarMinimalState(isTaskbarInMinimalState());
    }

    public boolean isTaskbarInMinimalState() {
        return getIconViews().length <= 1;
    }

    private void updateRecentsDivider(boolean hasRecents) {
        if (hasRecents && !mAddedDividerForRecents) {
            mAddedDividerForRecents = true;

            // Remove possible All Apps divider.
            if (getChildAt(mNumStaticViews) == mTaskbarDividerContainer) {
                mNextViewIndex--; // All Apps divider on the left. Need to account for removing it.
            }
            removeView(mTaskbarDividerContainer);

            addView(mTaskbarDividerContainer, mNextViewIndex);
        } else if (!hasRecents && mAddedDividerForRecents) {
            mAddedDividerForRecents = false;
            removeViewAt(mNextViewIndex);
        }
    }

    private void updateAllAppsDivider() {
        // Index where All Apps divider would be if it is already in Taskbar.
        final int expectedAllAppsDividerIndex = getExpectedAllAppsDividerIndex();
        boolean hasAtLeastOneIcon = mHotseatIconsContainer == null
                ? getChildCount() >= mNumStaticViews + 1
                : getChildCount() - mNumStaticViews == 0
                        && mHotseatIconsContainer.getChildCount() > 0;
        if (getChildAt(expectedAllAppsDividerIndex) == mTaskbarDividerContainer
                && getTotalNumberOfIcons() == mNumStaticViews) {
            // Only static views with divider so remove divider.
            removeView(mTaskbarDividerContainer);
        } else if (getChildAt(expectedAllAppsDividerIndex) != mTaskbarDividerContainer
                && hasAtLeastOneIcon) {
            // Static views with at least one app icon so add divider. For RTL, add it after the
            // icon that is at the expected index.
            addView(
                    mTaskbarDividerContainer,
                    mIsRtl ? expectedAllAppsDividerIndex + 1 : expectedAllAppsDividerIndex);
        }
    }

    private int getExpectedAllAppsDividerIndex() {
        if (mHotseatIconsContainer == null) {
            return mIsRtl
                    ? getChildCount() - mNumStaticViews - 1
                    : mNumStaticViews;
        } else {
            return mIsRtl
                    ? getChildCount() - mNumStaticViews
                    : mNumStaticViews - 1; // -1 to exclude mHotseatIconsContainer
        }
    }

    /**
     * Calculate how many icon we need to not show in Taskbar that are present in hotseat.
     */
    private int getIgnoreCountForTaskbarIcons(int recentsIcons, int hotseatIcons) {

        if (!mActivityContext.isThreeButtonNav()
                || mActivityContext.getTaskbarFeatureEvaluator().isRecentsEnabled()) {
            return 0;
        }

        DeviceProfile deviceProfile = mActivityContext.getDeviceProfile();

        // Add icon for all apps.
        int icons = 1;

        // Only include divider line in count if will be added to Taskbar view which is in
        // conditions below.
        if (mActivityContext.isInDesktopMode() && recentsIcons > 0) {
            icons += 1;
        } else if (recentsIcons + hotseatIcons != 0) {
            icons += 1;
        }
        int spaceNeeded = getIconLayoutWidth(icons + recentsIcons + hotseatIcons);

        boolean areBubblesVisible =
                mControllerCallbacks.isBubbleBarEnabled() && mBubbleBarLocation != null;
        int screenWidth = this.getResources().getDisplayMetrics().widthPixels;
        int navSpaceNeeded = deviceProfile.getHotseatProfile().getBarEndOffset();

        int ignoreCount = 0;
        //Screen Width - nav space
        int amountOfSpaceTaskbarIconsCanHave = screenWidth - navSpaceNeeded;
        if (areBubblesVisible) {
            // size of bubbles Icon and margin on the side.
            int bubbleBarMargin = getResources().getDimensionPixelSize(
                    R.dimen.transient_taskbar_bottom_margin);
            amountOfSpaceTaskbarIconsCanHave -= (mIconTouchSize + bubbleBarMargin);
        }
        int taskbarIconSpaceNeeded = spaceNeeded;
        while (amountOfSpaceTaskbarIconsCanHave < taskbarIconSpaceNeeded) {
            ignoreCount++;
            int iconSpace = mIconTouchSize + (2 * mItemMarginLeftRight);
            taskbarIconSpaceNeeded -= iconSpace;
        }
        return ignoreCount;
    }

    private void updateHotseatItems(ItemInfo[] hotseatItemInfos) {
        int numViewsAnimated = 0;
        final int numMaxIcons =
                mActivityContext.getTaskbarSpecsEvaluator().getNumShownHotseatIcons();
        final int hotseatLength = hotseatItemInfos.length;
        final boolean hasOverflow =
                mTaskbarPinnedOverflowView != null && hotseatLength > numMaxIcons;

        // The starting index of the pinned items on the taskbar.
        int onTaskbarStartIdx = 0;
        // The last index of the pinned items on the taskbar. This does not include the overflow
        // icon and the items inside the overflow icon if the pinned items overflow.
        int onTaskbarEndIdx = hotseatLength;

        boolean hasHotseatContainer = mHotseatIconsContainer != null;
        mNextHotseatIndex = hasHotseatContainer
                ? 0 // Start the count at 0 because the views are in a separate container
                : mNextViewIndex;

        if (hasOverflow) {
            final int itemsNotOverflown = numMaxIcons - 1;
            onTaskbarStartIdx = mIsRtl ? hotseatLength - itemsNotOverflown : 0;
            onTaskbarEndIdx = mIsRtl ? hotseatLength : itemsNotOverflown;

            final int overflownStartIndex = mIsRtl ? 0 : onTaskbarEndIdx;
            final int overflownEndIndex = mIsRtl ? onTaskbarStartIdx : hotseatLength;
            final List<ItemInfo> overflownItems = Arrays.asList(hotseatItemInfos).subList(
                    overflownStartIndex, overflownEndIndex);
            mTaskbarPinnedOverflowView.setItems(
                    overflownItems.stream().map(
                            iteminfo -> new ItemInfoWrapper(iteminfo, mActivityContext)).toList());
            if (mIsRtl) {
                maybeAddPinOverflowView();
            }
        } else if (isOverflowViewShowing()) {
            if (hasHotseatContainer) {
                mHotseatIconsContainer.removeView(mTaskbarPinnedOverflowView);
            } else {
                removeView(mTaskbarPinnedOverflowView);
            }
            mTaskbarPinnedOverflowView.clearItems();
        }

        // if there are ignore icons and make sure we are not removing more icons than we have.
        // mainly problem for tests.
        if (onTaskbarEndIdx - mIgnoreTaskbarIconCount >= 0) {
            onTaskbarEndIdx -= mIgnoreTaskbarIconCount;
        }

        for (ItemInfo hotseatItemInfo : Arrays.asList(hotseatItemInfos).subList(onTaskbarStartIdx,
                onTaskbarEndIdx)) {
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
            while ((hasHotseatContainer && isNextViewInHotseat(ItemInfo.class))
                    || (!hasHotseatContainer && isNextViewInSection(ItemInfo.class))) {
                hotseatView = hasHotseatContainer
                        ? mHotseatIconsContainer.getChildAt(mNextHotseatIndex)
                        : getChildAt(mNextViewIndex);

                // see if the view can be reused
                if ((hotseatView.getSourceLayoutResId() != expectedLayoutResId)
                        || (isCollection && (hotseatView.getTag() != hotseatItemInfo))) {
                    // Unlike for BubbleTextView, we can't reapply a new FolderInfo after inflation,
                    // so if the info changes we need to reinflate. This should only happen if a new
                    // folder is dragged to the position that another folder previously existed.
                    if (hasHotseatContainer) {
                        removeAndRecycle(mHotseatIconsContainer, hotseatView);
                    } else {
                        removeAndRecycle(hotseatView);
                    }
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
                LayoutParams lp = new TaskbarLayoutParams(mIconTouchSize, mIconTouchSize);
                hotseatView.setPadding(mItemPadding, mItemPadding, mItemPadding, mItemPadding);
                if (hasHotseatContainer) {
                    mHotseatIconsContainer.addView(hotseatView, mNextHotseatIndex, lp);
                } else {
                    addView(hotseatView, mNextViewIndex, lp);
                }
            } else if (hotseatView instanceof FolderIcon fi) {
                fi.onItemsChanged(false);
                fi.getFolder().reapplyItemInfo();
            }

            if (hotseatView.getLayoutParams() instanceof TaskbarLayoutParams tlp) {
                tlp.bindInfo = new CellInfo(hotseatView,
                        hotseatItemInfo.screenId, hotseatItemInfo.container,
                        hotseatItemInfo.cellX, hotseatItemInfo.cellY,
                        hotseatItemInfo.spanX, hotseatItemInfo.spanY);
            }

            // Apply the Hotseat ItemInfos, or hide the view if there is none for a given index.
            if (hotseatView instanceof BubbleTextView btv
                    && hotseatItemInfo instanceof WorkspaceItemInfo workspaceInfo) {
                if (btv instanceof PredictedAppIcon pai) {
                    if (pai.applyFromWorkspaceItemWithAnimation(workspaceInfo, numViewsAnimated)) {
                        numViewsAnimated++;
                    }
                } else {
                    btv.applyFromWorkspaceItem(workspaceInfo);
                }
            }
            setClickAndLongClickListenersForIcon(hotseatView);
            setHoverListenerForIcon(hotseatView);

            mNextHotseatIndex++;
            if (!hasHotseatContainer) {
                mNextViewIndex = mNextHotseatIndex;
            }
        }

        if (hasHotseatContainer) {
            while (isNextViewInHotseat(ItemInfo.class)) {
                removeAndRecycle(mHotseatIconsContainer,
                        mHotseatIconsContainer.getChildAt(mNextHotseatIndex));
            }
        } else {
            while (isNextViewInSection(ItemInfo.class)) {
                removeAndRecycle(getChildAt(mNextViewIndex));
            }
        }

        if (hasOverflow && !mIsRtl) {
            maybeAddPinOverflowView();
        }
    }

    private boolean isOverflowViewShowing() {
        if (mTaskbarPinnedOverflowView == null) return false;
        if (mHotseatIconsContainer != null) {
            return mHotseatIconsContainer.indexOfChild(mTaskbarPinnedOverflowView) != -1;
        }
        return indexOfChild(mTaskbarPinnedOverflowView) != -1;
    }

    private void maybeAddPinOverflowView() {
        if (!TaskbarPopupController.canPinAppsOverflow()) {
            return;
        }
        if (mHotseatIconsContainer != null) {
            if (!isOverflowViewShowing()) {
                mHotseatIconsContainer.addView(mTaskbarPinnedOverflowView, mNextHotseatIndex);
            }
            mNextHotseatIndex++;
        } else {
            if (!isOverflowViewShowing()) {
                addView(mTaskbarPinnedOverflowView, mNextViewIndex);
            }
            // [mNextViewIndex] follows the same index as [mNextHotseatIndex] so updates both
            // pointer here.
            mNextHotseatIndex++;
            mNextViewIndex++;
        }
    }

    private void updateRecents(List<GroupTask> recentTasks, int hotseatSize) {
        boolean supportsOverflow = ENABLE_TASKBAR_OVERFLOW.isTrue() && recentTasks.size() > 1;
        int overflowSize = 0;
        boolean hasOverflow = false;
        if (supportsOverflow && mTaskbarRecentsOverflowView != null) {
            // Need to account for All Apps and the divider. If we need to have an overflow, we will
            // have a divider for recents.
            final int nonTaskIconsToBeAdded = 2;
            mIdealNumIcons = hotseatSize + recentTasks.size() + nonTaskIconsToBeAdded;
            overflowSize = mIdealNumIcons - mMaxNumIcons;
            hasOverflow = overflowSize > 0;

            // RTL case is handled after we add the recent icons, because the button needs to
            // then be to the right of them.
            if (hasOverflow && !mIsRtl) {
                if (mPrevOverflowTasks.isEmpty()) {
                    addView(mTaskbarRecentsOverflowView, mNextViewIndex);
                }
                // NOTE: If overflow already existed, assume the overflow view is already
                // at the correct position.
                mNextViewIndex++;
            } else if (!hasOverflow && !mPrevOverflowTasks.isEmpty()) {
                removeView(mTaskbarRecentsOverflowView);
                mTaskbarRecentsOverflowView.clearItems();
            }
        } else if (mTaskbarRecentsOverflowView != null && !mPrevOverflowTasks.isEmpty()) {
            // Handle the case when closing all the windows together such as "clear all"
            // from overview.
            removeView(mTaskbarRecentsOverflowView);
            mTaskbarRecentsOverflowView.clearItems();
        }

        // An extra item needs to be added to overflow button to account for the space taken up by
        // the overflow button.
        final int itemsToAddToOverflow =
                hasOverflow ? Math.min(overflowSize + 1, recentTasks.size()) : 0;
        final Set<GroupTask> overflownRecentsSet;
        if (hasOverflow && mTaskbarRecentsOverflowView != null) {
            final int startIndex = mIsRtl ? recentTasks.size() - itemsToAddToOverflow : 0;
            final int endIndex = mIsRtl ? recentTasks.size() : itemsToAddToOverflow;
            final List<GroupTask> overflownRecents = recentTasks.subList(startIndex, endIndex);
            mTaskbarRecentsOverflowView.setItems(
                    overflownRecents.stream().map(
                            t -> new TaskWrapper(mActivityContext, ((SingleTask) t))).toList());
            overflownRecentsSet = new ArraySet<>(overflownRecents);
        } else {
            overflownRecentsSet = Collections.emptySet();
        }

        // Add Recent/Running icons.
        final Set<GroupTask> recentTasksSet = new ArraySet<>(recentTasks);
        final int startIndex = mIsRtl ? 0 : itemsToAddToOverflow;
        final int endIndex =
                mIsRtl ? recentTasks.size() - itemsToAddToOverflow : recentTasks.size();
        for (GroupTask task : recentTasks.subList(startIndex, endIndex)) {
            // Replace any Recent views with the appropriate type if it's not already that type.
            final int expectedLayoutResId;
            boolean isCollection = false;
            if (!(task instanceof SingleTask)) {
                if (task.taskViewType == TaskViewType.DESKTOP) {
                    expectedLayoutResId = -1;
                } else {
                    expectedLayoutResId = R.layout.app_pair_icon;
                }
                isCollection = true;
            } else {
                expectedLayoutResId = R.layout.taskbar_app_icon;
            }

            View recentIcon = null;
            // If a task is new, we should not reuse a view so that it animates in when it is added.
            final boolean canReuseView =
                    mPrevRecentTasks.contains(task) && !mPrevOverflowTasks.contains(task);
            while (canReuseView && isNextViewInSection(GroupTask.class)) {
                recentIcon = getChildAt(mNextViewIndex);
                GroupTask tag = (GroupTask) recentIcon.getTag();

                // see if the view can be reused
                if (recentIcon.getSourceLayoutResId() != expectedLayoutResId
                        || (isCollection && tag != task && !(tag instanceof SplitTask))
                        // Remove view corresponding to removed task so that it animates out.
                        || !recentTasksSet.contains(tag)
                        || overflownRecentsSet.contains(tag)) {
                    removeAndRecycle(recentIcon);
                    recentIcon = null;
                } else {
                    // View found
                    break;
                }
            }

            if (recentIcon == null) {
                if (task instanceof SingleTask) {
                    recentIcon = inflate(expectedLayoutResId);
                } else if (task instanceof SplitTask st) {
                    recentIcon = AppPairIcon.inflateIcon(expectedLayoutResId, mActivityContext,
                            this, st.toAppPairInfo(), DISPLAY_TASKBAR);
                    ((AppPairIcon) recentIcon).setTextVisible(false);
                    recentIcon.setTag(task);
                }
                LayoutParams lp = new TaskbarLayoutParams(mIconTouchSize, mIconTouchSize);
                recentIcon.setPadding(mItemPadding, mItemPadding, mItemPadding, mItemPadding);
                addView(recentIcon, mNextViewIndex, lp);
            } else if (recentIcon instanceof AppPairIcon api && task instanceof SplitTask st) {
                api.updateInfo(st.toAppPairInfo());
            }

            if (recentIcon instanceof BubbleTextView btv) {
                applyGroupTaskToBubbleTextView(btv, task);
            }
            setClickAndLongClickListenersForIcon(recentIcon);
            setHoverListenerForIcon(recentIcon);
            mNextViewIndex++;
        }

        while (isNextViewInSection(GroupTask.class)) {
            removeAndRecycle(getChildAt(mNextViewIndex));
        }

        if (mIsRtl && hasOverflow) {
            if (mPrevOverflowTasks.isEmpty()) {
                addView(mTaskbarRecentsOverflowView, mNextViewIndex);
            }
            mNextViewIndex++;
        }

        mPrevRecentTasks = recentTasksSet;
        mPrevOverflowTasks = overflownRecentsSet;
    }

    private void updateHandoffSuggestions(List<HandoffSuggestion> handoffSuggestions) {
        Set<HandoffSuggestion> tasksToAdd = new HashSet<>(handoffSuggestions);
        while (isNextViewInSection(HandoffSuggestion.class)) {
            View view = getChildAt(mNextViewIndex);
            if (tasksToAdd.contains(view.getTag())) {
                tasksToAdd.remove(view.getTag());
                mNextViewIndex++;
            } else {
                removeAndRecycle(getChildAt(mNextViewIndex));
            }
        }

        for (HandoffSuggestion handoffSuggestion : tasksToAdd) {
            View recentIcon = inflate(R.layout.taskbar_app_icon);
            LayoutParams lp = new TaskbarLayoutParams(mIconTouchSize, mIconTouchSize);
            recentIcon.setPadding(mItemPadding, mItemPadding, mItemPadding, mItemPadding);
            addView(recentIcon, mNextViewIndex++, lp);
            applyHandoffSuggestionToBubbleTextView((BubbleTextView) recentIcon, handoffSuggestion);
        }
    }

    public void applyHandoffSuggestionToBubbleTextView(
        BubbleTextView bubbleTextView,
        HandoffSuggestion handoffSuggestion) {

        HandoffSuggestion.Metadata metadata = handoffSuggestion.getMetadata();
        if (metadata != null) {
            bubbleTextView.applyIconAndLabel(
                metadata.getIcon(),
                metadata.getLabel(),
                metadata.getLabel());
        }

        bubbleTextView.setTag(handoffSuggestion);
    }

    private boolean isNextViewInSection(Class<?> tagClass) {
        return mNextViewIndex < getChildCount()
                && tagClass.isInstance(getChildAt(mNextViewIndex).getTag());
    }

    private boolean isNextViewInHotseat(Class<?> tagClass) {
        if (mHotseatIconsContainer == null) {
            return false;
        }
        final int nextIndex = mNextHotseatIndex;
        return nextIndex < mHotseatIconsContainer.getChildCount()
                && tagClass.isInstance(mHotseatIconsContainer.getChildAt(nextIndex).getTag());
    }

    protected View mapOverItems(ViewGroup parent, @NonNull ItemOperator op) {
        final int itemCount = parent.getChildCount();
        for (int itemIdx = 0; itemIdx < itemCount; itemIdx++) {
            View item = parent.getChildAt(itemIdx);
            if (item instanceof TaskbarIconsContainer tic) {
                mapOverItems(tic, op);
            }
            if (item.getTag() instanceof ItemInfo itemInfo && op.evaluate(itemInfo, item)) {
                return item;
            }
        }
        return null;
    }

    /** Binds the SingleTask to the BubbleTextView to be ready to present to the user. */
    public void applyGroupTaskToBubbleTextView(BubbleTextView btv, GroupTask groupTask) {
        if (!(groupTask instanceof SingleTask singleTask)) {
            return;
        }

        Task task = singleTask.getTask();
        // TODO(b/344038728): use FastBitmapDrawable instead of Drawable, to get disabled state
        //  while dragging.
        BitmapInfo bitmapInfo = groupTask.getBitmapInfos().get(0);
        final Drawable taskIcon;
        if (enableTaskbarRecentsThemedIcons()) {
            ThemeManager themeManager = ThemeManager.INSTANCE.get(mActivityContext);
            @DrawableCreationFlags int creationFlags =
                    themeManager.isIconThemeEnabled() ? FLAG_THEMED : 0;
            @Nullable IconShape iconShape =
                    enableLauncherIconShapes() ? themeManager.getIconShapeData().getValue() : null;
            taskIcon = Optional.ofNullable(bitmapInfo)
                    .map(bi -> bi.newIcon(mActivityContext, creationFlags, iconShape))
                    .orElse(null);
        } else {
            taskIcon = Optional.ofNullable(task.icon)
                    .map(Drawable::getConstantState)
                    .map(cs -> cs.newDrawable().mutate())
                    .orElse(null);
        }

        btv.applyIconAndLabel(taskIcon, task.title, task.titleDescription);
        btv.setTag(singleTask);
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

    /** Updates taskbar icons accordingly to the new bubble bar location. */
    public void onBubbleBarLocationUpdated(BubbleBarLocation location) {
        if (mBubbleBarLocation == location) return;
        mBubbleBarLocation = location;
        requestLayout();
    }

    /**
     * Returns translation X for the taskbar icons for provided {@link BubbleBarLocation}. If the
     * bubble bar is not enabled, or location of the bubble bar is the same, or taskbar is not start
     * aligned - returns 0.
     */
    public float getTranslationXForBubbleBarPosition(BubbleBarLocation location) {
        if (!mControllerCallbacks.isBubbleBarEnabled()
                || location == mBubbleBarLocation
                || !mActivityContext.shouldStartAlignTaskbar()
        ) {
            return 0;
        }
        Rect iconsBounds = getTransientTaskbarIconLayoutBoundsInParent();

        return getTaskBarIconsEndForBubbleBarLocation(location) - iconsBounds.right;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int spaceNeeded = getIconLayoutWidth();
        boolean layoutRtl = isLayoutRtl();
        DeviceProfile deviceProfile = mActivityContext.getDeviceProfile();
        int navSpaceNeeded = deviceProfile.getHotseatProfile().getBarEndOffset();
        int centerAlignIconEnd = (right + left + spaceNeeded) / 2;
        int iconEnd = centerAlignIconEnd;
        if (mShouldTryStartAlign) {
            int startSpacingPx =
                    deviceProfile.getHotseatProfile().getInlineNavButtonsEndSpacingPx();
            if (mControllerCallbacks.isBubbleBarEnabled()
                    && mBubbleBarLocation != null
                    && mActivityContext.shouldStartAlignTaskbar()) {
                iconEnd = (int) getTaskBarIconsEndForBubbleBarLocation(mBubbleBarLocation);
            } else {
                if (layoutRtl) {
                    iconEnd = right - startSpacingPx;
                } else {
                    iconEnd = startSpacingPx + spaceNeeded;
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
            }
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

        // With rtl layout, the all apps button will be translated by `allAppsButtonOffset` after
        // layout completion (by `TaskbarViewController`). Offset the icon end by the same amount
        // when laying out icons, so the taskbar content remains centered after all apps button
        // translation.
        if (layoutRtl) {
            iconEnd += mAllAppsButtonTranslationOffset;
        }

        mControllerCallbacks.onPreLayoutChildren();

        int count = getChildCount();
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
                int qsbTop = (bottom - top - deviceProfile.getHotseatProfile().getQsbHeight()) / 2;
                int qsbBottom = qsbTop + deviceProfile.getHotseatProfile().getQsbHeight();
                child.layout(qsbStart, qsbTop, qsbEnd, qsbBottom);
            } else if (child == mTaskbarDividerContainer) {
                iconEnd += mItemMarginLeftRight;
                int iconStart = iconEnd - mIconTouchSize;
                child.layout(iconStart, mIconLayoutBounds.top, iconEnd, mIconLayoutBounds.bottom);
                iconEnd = iconStart + mItemMarginLeftRight;
            } else if (child instanceof TaskbarIconsContainer tic) {
                iconEnd -= mItemMarginLeftRight;
                int numItems = tic.getChildCount();
                int iconStart = iconEnd - (mIconTouchSize * numItems)
                        - (2 * (mItemMarginLeftRight * (numItems - 1)));
                child.layout(iconStart, mIconLayoutBounds.top, iconEnd, mIconLayoutBounds.bottom);
                iconEnd = iconStart - mItemMarginLeftRight;
            } else {
                iconEnd -= mItemMarginLeftRight;
                int iconStart = iconEnd - mIconTouchSize;
                child.layout(iconStart, mIconLayoutBounds.top, iconEnd, mIconLayoutBounds.bottom);
                iconEnd = iconStart - mItemMarginLeftRight;
            }
        }

        mIconLayoutBounds.left = iconEnd;

        // Adjust the icon layout bounds by the amount by which all apps button will be translated
        // post layout to maintain margin between all apps button and the edge of the transient
        // taskbar background. Done for ltr layout only - for rtl layout, the offset needs to be
        // adjusted on the right, which is done by offsetting `iconEnd` after setting
        // `mIconLayoutBounds.right`.
        if (!layoutRtl) {
            mIconLayoutBounds.left += mAllAppsButtonTranslationOffset;
        }

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
     * Returns whether the given MotionEvent, *in screen coordinates*, is within any Taskbar item's
     * touch bounds.
     */
    public boolean isEventOverAnyItem(MotionEvent ev) {
        int xInOurCoordinates = (int) ev.getRawX();
        int yInOurCoordinates = (int) ev.getRawY();
        return isShown() && getTaskbarIconsActualBounds().contains(xInOurCoordinates,
                yInOurCoordinates);
    }

    /**
     * Returns the current visual taskbar icons bounds (unlike `mIconLayoutBounds` which contains
     * bounds for transient mode only).
     */
    Rect getTaskbarIconsActualBounds() {
        View[] iconViews = getIconViews();
        if (iconViews.length == 0) {
            return new Rect();
        }
        iconViews[0].getLocationOnScreen(mFirstIconViewLocation);
        iconViews[iconViews.length - 1].getLocationOnScreen(mLastIconViewLocation);

        return new Rect(
                mFirstIconViewLocation[0],
                mFirstIconViewLocation[1],
                mLastIconViewLocation[0] + mIconTouchSize,
                mLastIconViewLocation[1] + mIconTouchSize);
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (refactorTaskbarUiState()) {
            mTaskbarUiState.setTaskbarViewIsShown(isShown());
        }
    }

    /**
     * Gets visual bounds of the taskbar view. The visual bounds correspond to the taskbar touch
     * area, rather than layout placement in the parent view.
     */
    public Rect getTransientTaskbarIconLayoutBounds() {
        return new Rect(mIconLayoutBounds);
    }

    /** Gets taskbar layout bounds in parent view. */
    public Rect getTransientTaskbarIconLayoutBoundsInParent() {
        Rect actualBounds = new Rect(mIconLayoutBounds);
        actualBounds.top = getTop();
        actualBounds.bottom = getBottom();
        return actualBounds;
    }

    /** Returns the total number of icons in the taskbar. **/
    public int getTotalNumberOfIcons() {
        int numContainers = 0;
        int numIconsInContainers = 0;
        for (int i = getChildCount() - 1; i >= 0; --i) {
            if (getChildAt(i) instanceof TaskbarIconsContainer tic) {
                numContainers++;
                numIconsInContainers += tic.getChildCount();
            }
        }

        int count = getChildCount()
                - numContainers
                + numIconsInContainers;
        if (mActivityContext.getDeviceProfile().isQsbInline) {
            count--; // Exclude QSB
        }
        // count can be negative if views aren't added
        return Math.max(0, count);
    }
    /**
     * Returns the space used by the icons.
     */
    private int getIconLayoutWidth() {
        return getIconLayoutWidth(getTotalNumberOfIcons());
    }

    /**
     * Return the space needed based on the number of taskbar icons supplied vs existing children.
     */
    private int getIconLayoutWidth(int expectedNumberOfTaskbarIcons) {
        int iconLayoutBoundsWidth =
                expectedNumberOfTaskbarIcons * (mItemMarginLeftRight * 2 + mIconTouchSize);

        if (enableTaskbarPinning() && expectedNumberOfTaskbarIcons > 1) {
            // We are removing 4 * mItemMarginLeftRight as there should be no space between
            // All Apps icon, divider icon, and first app icon in taskbar
            iconLayoutBoundsWidth -= mItemMarginLeftRight * 4;
        }

        // The all apps button container gets offset horizontally, reducing the overall taskbar
        // view size.
        iconLayoutBoundsWidth -= mAllAppsButtonTranslationOffset;

        return iconLayoutBoundsWidth;
    }

    @Override
    public void setLayoutTransition(LayoutTransition transition) {
        super.setLayoutTransition(transition);
        if (mHotseatIconsContainer != null) {
            mHotseatIconsContainer.setLayoutTransition(transition);
        }
    }

    /**
     * Returns the app icons currently shown in the taskbar. The returned list does not include qsb,
     * but it includes all apps button and icon divider views.
     */
    public View[] getIconViews() {
        final int count = getChildCount();
        final int totalCount = getTotalNumberOfIcons();
        if (totalCount == 0) {
            return new View[0];
        }
        View[] icons = new View[totalCount];
        int insertionPoint = 0;
        for (int i = 0; i < count; i++) {
            if (getChildAt(i) == mQsb) continue;
            if (getChildAt(i) instanceof TaskbarIconsContainer tic) {
                int ticCount = tic.getChildCount();
                for (int j = 0; j < ticCount; j++) {
                    icons[insertionPoint++] = tic.getChildAt(j);
                }
                continue;
            }
            icons[insertionPoint++] = getChildAt(i);
        }
        return icons;
    }

    /**
     * The max number of icon views the taskbar can have when taskbar overflow is enabled.
     */
    int getMaxNumIconViews() {
        return mMaxNumIcons;
    }

    void limitMaxNumIconViewsForTest(int maxNumIconLimit) {
        mMaxNumIconsLimitForTest = maxNumIconLimit;
    }

    /**
     * Returns the all apps button in the taskbar.
     */
    public TaskbarAllAppsButtonContainer getAllAppsButtonContainer() {
        return mAllAppsButtonContainer;
    }

    /**
     * Returns the taskbar divider in the taskbar.
     */
    @Nullable
    public TaskbarDividerContainer getTaskbarDividerViewContainer() {
        return mTaskbarDividerContainer;
    }

    /**
     * Returns the taskbar recent tasks overflow view in the taskbar.
     */
    @Nullable
    public TaskbarOverflowView getTaskbarRecentsOverflowView() {
        return mTaskbarRecentsOverflowView;
    }

    /**
     * Returns the taskbar overflow view for pinned apps in the taskbar.
     */
    @Nullable
    public TaskbarOverflowView getTaskbarPinnedOverflowView() {
        return mTaskbarPinnedOverflowView;
    }

    /**
     * Returns whether the divider is between Hotseat icons and Recents,
     * instead of between All Apps button and Hotseat.
     */
    public boolean isDividerForRecents() {
        return mAddedDividerForRecents;
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

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (mActivityContext.isDestroyed()) return;
        super.dispatchDraw(canvas);
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
     * @return The all apps button horizontal offset used to calculate the taskbar contents width
     * during layout.
     */
    public int getAllAppsButtonTranslationXOffsetUsedForLayout() {
        return mAllAppsButtonTranslationOffset;
    }

    /**
     * This method only works for bubble bar enabled in persistent task bar and the taskbar is start
     * aligned.
     */
    private float getTaskBarIconsEndForBubbleBarLocation(BubbleBarLocation location) {
        DeviceProfile deviceProfile = mActivityContext.getDeviceProfile();
        boolean navbarOnRight = location.isOnLeft(isLayoutRtl());
        int navSpaceNeeded = deviceProfile.getHotseatProfile().getBarEndOffset();
        if (navbarOnRight) {
            return getWidth() - navSpaceNeeded;
        } else {
            return navSpaceNeeded + getIconLayoutWidth();
        }
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
        return new TaskbarLayoutParams(lp);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new TaskbarLayoutParams(getContext(), attrs);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof TaskbarLayoutParams;
    }

    public static class TaskbarLayoutParams extends FrameLayout.LayoutParams {

        @Nullable public CellInfo bindInfo;

        public TaskbarLayoutParams(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public TaskbarLayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }

        public TaskbarLayoutParams(int width, int height) {
            super(width, height);
        }
    }
}
