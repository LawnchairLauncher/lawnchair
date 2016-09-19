/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.shortcuts;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.TimeInterpolator;
import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.ShapeDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.IconCache;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.LauncherViewPropertyAnimator;
import com.android.launcher3.LogAccelerateInterpolator;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.accessibility.ShortcutMenuAccessibilityDelegate;
import com.android.launcher3.compat.UserHandleCompat;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.graphics.TriangleShape;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;

import java.util.Collections;
import java.util.List;

/**
 * A container for shortcuts to deep links within apps.
 */
@TargetApi(Build.VERSION_CODES.N)
public class DeepShortcutsContainer extends LinearLayout implements View.OnLongClickListener,
        View.OnTouchListener, DragSource, DragController.DragListener {
    private static final String TAG = "ShortcutsContainer";

    private final Point mIconShift = new Point();

    private final Launcher mLauncher;
    private final DeepShortcutManager mDeepShortcutsManager;
    private final int mStartDragThreshold;
    private final ShortcutMenuAccessibilityDelegate mAccessibilityDelegate;
    private final boolean mIsRtl;

    private BubbleTextView mDeferredDragIcon;
    private final Rect mTempRect = new Rect();
    private Point mIconLastTouchPos = new Point();
    private boolean mIsLeftAligned;
    private boolean mIsAboveIcon;
    private View mArrow;

    private Animator mOpenCloseAnimator;
    private boolean mDeferContainerRemoval;
    private boolean mIsOpen;

    public DeepShortcutsContainer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mLauncher = Launcher.getLauncher(context);
        mDeepShortcutsManager = LauncherAppState.getInstance().getShortcutManager();

        mStartDragThreshold = getResources().getDimensionPixelSize(
                R.dimen.deep_shortcuts_start_drag_threshold);
        mAccessibilityDelegate = new ShortcutMenuAccessibilityDelegate(mLauncher);
        mIsRtl = Utilities.isRtl(getResources());
    }

    public DeepShortcutsContainer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DeepShortcutsContainer(Context context) {
        this(context, null, 0);
    }

    public void populateAndShow(final BubbleTextView originalIcon, final List<String> ids) {
        final Resources resources = getResources();
        final int arrowWidth = resources.getDimensionPixelSize(R.dimen.deep_shortcuts_arrow_width);
        final int arrowHeight = resources.getDimensionPixelSize(R.dimen.deep_shortcuts_arrow_height);
        final int arrowHorizontalOffset = resources.getDimensionPixelSize(
                R.dimen.deep_shortcuts_arrow_horizontal_offset);
        final int arrowVerticalOffset = resources.getDimensionPixelSize(
                R.dimen.deep_shortcuts_arrow_vertical_offset);

        // Add dummy views first, and populate with real shortcut info when ready.
        final int spacing = getResources().getDimensionPixelSize(R.dimen.deep_shortcuts_spacing);
        final LayoutInflater inflater = mLauncher.getLayoutInflater();
        int numShortcuts = Math.min(ids.size(), ShortcutFilter.MAX_SHORTCUTS);
        for (int i = 0; i < numShortcuts; i++) {
            final DeepShortcutView shortcut =
                    (DeepShortcutView) inflater.inflate(R.layout.deep_shortcut, this, false);
            if (i < numShortcuts - 1) {
                ((LayoutParams) shortcut.getLayoutParams()).bottomMargin = spacing;
            }
            shortcut.getBubbleText().setAccessibilityDelegate(mAccessibilityDelegate);
            addView(shortcut);
        }
        setContentDescription(getContext().getString(R.string.shortcuts_menu_description,
                numShortcuts, originalIcon.getContentDescription().toString()));

        measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        orientAboutIcon(originalIcon, arrowHeight + arrowVerticalOffset);

        // Add the arrow.
        mArrow = addArrowView(arrowHorizontalOffset, arrowVerticalOffset, arrowWidth, arrowHeight);
        mArrow.setPivotX(arrowWidth / 2);
        mArrow.setPivotY(mIsAboveIcon ? 0 : arrowHeight);

        animateOpen();

        deferDrag(originalIcon);

        // Load the shortcuts on a background thread and update the container as it animates.
        final Looper workerLooper = LauncherModel.getWorkerLooper();
        final Handler uiHandler = new Handler(Looper.getMainLooper());
        final ItemInfo originalInfo = (ItemInfo) originalIcon.getTag();
        final UserHandleCompat user = originalInfo.user;
        final ComponentName activity = originalInfo.getTargetComponent();
        new Handler(workerLooper).postAtFrontOfQueue(new Runnable() {
            @Override
            public void run() {
                final List<ShortcutInfoCompat> shortcuts = ShortcutFilter.sortAndFilterShortcuts(
                        mDeepShortcutsManager.queryForShortcutsContainer(activity, ids, user));
                // We want the lowest rank to be closest to the user's finger.
                if (mIsAboveIcon) {
                    Collections.reverse(shortcuts);
                }
                for (int i = 0; i < shortcuts.size(); i++) {
                    final ShortcutInfoCompat shortcut = shortcuts.get(i);
                    uiHandler.post(new UpdateShortcutChild(
                            i, new UnbadgedShortcutInfo(shortcut, mLauncher)));
                }
            }
        });
    }

    /** Updates the child of this container at the given index based on the given shortcut info. */
    private class UpdateShortcutChild implements Runnable {
        private int mShortcutChildIndex;
        private UnbadgedShortcutInfo mShortcutChildInfo;

        public UpdateShortcutChild(int shortcutChildIndex, UnbadgedShortcutInfo shortcutChildInfo) {
            mShortcutChildIndex = shortcutChildIndex;
            mShortcutChildInfo = shortcutChildInfo;
        }

        @Override
        public void run() {
            getShortcutAt(mShortcutChildIndex)
                    .applyShortcutInfo(mShortcutChildInfo, DeepShortcutsContainer.this);
        }
    }

    private DeepShortcutView getShortcutAt(int index) {
        if (!mIsAboveIcon) {
            // Opening down, so arrow is the first view.
            index++;
        }
        return (DeepShortcutView) getChildAt(index);
    }

    private int getShortcutCount() {
        // All children except the arrow are shortcuts.
        return getChildCount() - 1;
    }

    private void animateOpen() {
        setVisibility(View.VISIBLE);
        mIsOpen = true;

        final AnimatorSet shortcutAnims = LauncherAnimUtils.createAnimatorSet();
        final int shortcutCount = getShortcutCount();

        final long duration = getResources().getInteger(
                R.integer.config_deepShortcutOpenDuration);
        final long arrowScaleDuration = getResources().getInteger(
                R.integer.config_deepShortcutArrowOpenDuration);
        final long arrowScaleDelay = duration - arrowScaleDuration;
        final long stagger = getResources().getInteger(
                R.integer.config_deepShortcutOpenStagger);
        final TimeInterpolator fadeInterpolator = new LogAccelerateInterpolator(100, 0);

        // Animate shortcuts
        DecelerateInterpolator interpolator = new DecelerateInterpolator();
        for (int i = 0; i < shortcutCount; i++) {
            final DeepShortcutView deepShortcutView = getShortcutAt(i);
            deepShortcutView.setVisibility(INVISIBLE);
            deepShortcutView.setAlpha(0);

            Animator anim = deepShortcutView.createOpenAnimation(mIsAboveIcon, mIsLeftAligned);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    deepShortcutView.setVisibility(VISIBLE);
                }
            });
            anim.setDuration(duration);
            int animationIndex = mIsAboveIcon ? shortcutCount - i - 1 : i;
            anim.setStartDelay(stagger * animationIndex);
            anim.setInterpolator(interpolator);
            shortcutAnims.play(anim);

            Animator fadeAnim = new LauncherViewPropertyAnimator(deepShortcutView).alpha(1);
            fadeAnim.setInterpolator(fadeInterpolator);
            // We want the shortcut to be fully opaque before the arrow starts animating.
            fadeAnim.setDuration(arrowScaleDelay);
            shortcutAnims.play(fadeAnim);
        }
        shortcutAnims.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mOpenCloseAnimator = null;
                Utilities.sendCustomAccessibilityEvent(
                        DeepShortcutsContainer.this,
                        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                        getContext().getString(R.string.action_deep_shortcut));
            }
        });

        // Animate the arrow
        mArrow.setScaleX(0);
        mArrow.setScaleY(0);
        Animator arrowScale = new LauncherViewPropertyAnimator(mArrow).scaleX(1).scaleY(1);
        arrowScale.setStartDelay(arrowScaleDelay);
        arrowScale.setDuration(arrowScaleDuration);
        shortcutAnims.play(arrowScale);

        mOpenCloseAnimator = shortcutAnims;
        shortcutAnims.start();
    }

    /**
     * Orients this container above or below the given icon, aligning with the left or right.
     *
     * These are the preferred orientations, in order (RTL prefers right-aligned over left):
     * - Above and left-aligned
     * - Above and right-aligned
     * - Below and left-aligned
     * - Below and right-aligned
     *
     * So we always align left if there is enough horizontal space
     * and align above if there is enough vertical space.
     */
    private void orientAboutIcon(BubbleTextView icon, int arrowHeight) {
        int width = getMeasuredWidth();
        int height = getMeasuredHeight() + arrowHeight;

        DragLayer dragLayer = mLauncher.getDragLayer();
        dragLayer.getDescendantRectRelativeToSelf(icon, mTempRect);
        Rect insets = dragLayer.getInsets();

        // Align left (right in RTL) if there is room.
        int leftAlignedX = mTempRect.left + icon.getPaddingLeft();
        int rightAlignedX = mTempRect.right - width - icon.getPaddingRight();
        int x = leftAlignedX;
        boolean canBeLeftAligned = leftAlignedX + width < dragLayer.getRight() - insets.right;
        boolean canBeRightAligned = rightAlignedX > dragLayer.getLeft() + insets.left;
        if (!canBeLeftAligned || (mIsRtl && canBeRightAligned)) {
            x = rightAlignedX;
        }
        mIsLeftAligned = x == leftAlignedX;
        if (mIsRtl) {
            x -= dragLayer.getWidth() - width;
        }

        // Offset x so that the arrow and shortcut icons are center-aligned with the original icon.
        int iconWidth = icon.getWidth() - icon.getTotalPaddingLeft() - icon.getTotalPaddingRight();
        iconWidth *= icon.getScaleX();
        Resources resources = getResources();
        int xOffset;
        if (isAlignedWithStart()) {
            // Aligning with the shortcut icon.
            int shortcutIconWidth = resources.getDimensionPixelSize(R.dimen.deep_shortcut_icon_size);
            int shortcutPaddingStart = resources.getDimensionPixelSize(
                    R.dimen.deep_shortcut_padding_start);
            xOffset = iconWidth / 2 - shortcutIconWidth / 2 - shortcutPaddingStart;
        } else {
            // Aligning with the drag handle.
            int shortcutDragHandleWidth = resources.getDimensionPixelSize(
                    R.dimen.deep_shortcut_drag_handle_size);
            int shortcutPaddingEnd = resources.getDimensionPixelSize(
                    R.dimen.deep_shortcut_padding_end);
            xOffset = iconWidth / 2 - shortcutDragHandleWidth / 2 - shortcutPaddingEnd;
        }
        x += mIsLeftAligned ? xOffset : -xOffset;

        // Open above icon if there is room.
        int iconHeight = icon.getIcon().getBounds().height();
        int y = mTempRect.top + icon.getPaddingTop() - height;
        mIsAboveIcon = y > dragLayer.getTop() + insets.top;
        if (!mIsAboveIcon) {
            y = mTempRect.top + icon.getPaddingTop() + iconHeight;
        }

        // Insets are added later, so subtract them now.
        y -= insets.top;

        setX(x);
        setY(y);
    }

    private boolean isAlignedWithStart() {
        return mIsLeftAligned && !mIsRtl || !mIsLeftAligned && mIsRtl;
    }

    /**
     * Adds an arrow view pointing at the original icon.
     * @param horizontalOffset the horizontal offset of the arrow, so that it
     *                              points at the center of the original icon
     */
    private View addArrowView(int horizontalOffset, int verticalOffset, int width, int height) {
        LinearLayout.LayoutParams layoutParams = new LayoutParams(width, height);
        if (mIsLeftAligned) {
            layoutParams.gravity = Gravity.LEFT;
            layoutParams.leftMargin = horizontalOffset;
        } else {
            layoutParams.gravity = Gravity.RIGHT;
            layoutParams.rightMargin = horizontalOffset;
        }
        if (mIsAboveIcon) {
            layoutParams.topMargin = verticalOffset;
        } else {
            layoutParams.bottomMargin = verticalOffset;
        }

        View arrowView = new View(getContext());
        ShapeDrawable arrowDrawable = new ShapeDrawable(TriangleShape.create(
                width, height, !mIsAboveIcon));
        arrowDrawable.getPaint().setColor(Color.WHITE);
        arrowView.setBackground(arrowDrawable);
        arrowView.setElevation(getElevation());
        addView(arrowView, mIsAboveIcon ? getChildCount() : 0, layoutParams);
        return arrowView;
    }

    private void deferDrag(BubbleTextView originalIcon) {
        mDeferredDragIcon = originalIcon;
        mLauncher.getDragController().addDragListener(this);
    }

    public BubbleTextView getDeferredDragIcon() {
        return mDeferredDragIcon;
    }

    /**
     * Determines when the deferred drag should be started.
     *
     * Current behavior:
     * - Start the drag if the touch passes a certain distance from the original touch down.
     */
    public DragOptions.DeferDragCondition createDeferDragCondition(final Runnable onDragStart) {
        return new DragOptions.DeferDragCondition() {
            @Override
            public boolean shouldStartDeferredDrag(double distanceDragged) {
                return distanceDragged > mStartDragThreshold;
            }

            @Override
            public void onDeferredDragStart() {
                mDeferredDragIcon.setVisibility(INVISIBLE);
            }

            @Override
            public void onDropBeforeDeferredDrag() {
                mLauncher.getUserEventDispatcher().logDeepShortcutsOpen(mDeferredDragIcon);
                if (!mIsAboveIcon) {
                    mDeferredDragIcon.setTextVisibility(false);
                }
            }

            @Override
            public void onDragStart() {
                if (onDragStart != null) {
                    onDragStart.run();
                }
            }
        };
    }

    @Override
    public boolean onTouch(View v, MotionEvent ev) {
        // Touched a shortcut, update where it was touched so we can drag from there on long click.
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                mIconLastTouchPos.set((int) ev.getX(), (int) ev.getY());
                break;
        }
        return false;
    }

    public boolean onLongClick(View v) {
        // Return early if this is not initiated from a touch or not the correct view
        if (!v.isInTouchMode() || !(v.getParent() instanceof DeepShortcutView)) return false;
        // Return if global dragging is not enabled
        if (!mLauncher.isDraggingEnabled()) return false;

        // Long clicked on a shortcut.
        mDeferContainerRemoval = true;
        DeepShortcutView sv = (DeepShortcutView) v.getParent();
        sv.setWillDrawIcon(false);

        // Move the icon to align with the center-top of the touch point
        mIconShift.x = mIconLastTouchPos.x - sv.getIconCenter().x;
        mIconShift.y = mIconLastTouchPos.y - mLauncher.getDeviceProfile().iconSizePx;

        DragView dv = mLauncher.getWorkspace().beginDragShared(
                sv.getBubbleText(), this, sv.getFinalInfo(),
                new ShortcutDragPreviewProvider(sv.getIconView(), mIconShift), new DragOptions());
        dv.animateShift(-mIconShift.x, -mIconShift.y);

        // TODO: support dragging from within folder without having to close it
        mLauncher.closeFolder();
        return false;
    }

    @Override
    public boolean supportsFlingToDelete() {
        return true;
    }

    @Override
    public boolean supportsAppInfoDropTarget() {
        return true;
    }

    @Override
    public boolean supportsDeleteDropTarget() {
        return false;
    }

    @Override
    public float getIntrinsicIconScaleFactor() {
        return 1f;
    }

    @Override
    public void onFlingToDeleteCompleted() {
        // Don't care; ignore.
    }

    @Override
    public void onDropCompleted(View target, DropTarget.DragObject d, boolean isFlingToDelete,
            boolean success) {
        if (!success) {
            d.dragView.remove();
            mLauncher.showWorkspace(true);
            mLauncher.getDropTargetBar().onDragEnd();
        }
    }

    @Override
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) {
        // Either the original icon or one of the shortcuts was dragged.
        // Hide the container, but don't remove it yet because that interferes with touch events.
        animateClose();
    }

    @Override
    public void onDragEnd() {
        if (!mIsOpen) {
            if (mOpenCloseAnimator != null) {
                // Close animation is running.
                mDeferContainerRemoval = false;
            } else {
                // Close animation is not running.
                if (mDeferContainerRemoval) {
                    close();
                }
            }
        }
        mDeferredDragIcon.setVisibility(VISIBLE);
    }

    @Override
    public void fillInLaunchSourceData(View v, ItemInfo info, Target target, Target targetParent) {
        target.itemType = LauncherLogProto.DEEPSHORTCUT;
        // TODO: add target.rank
        targetParent.containerType = LauncherLogProto.DEEPSHORTCUTS;
    }

    public void animateClose() {
        if (!mIsOpen) {
            return;
        }
        if (mOpenCloseAnimator != null) {
            mOpenCloseAnimator.cancel();
        }
        mIsOpen = false;

        final AnimatorSet shortcutAnims = LauncherAnimUtils.createAnimatorSet();
        final int shortcutCount = getShortcutCount();
        int numOpenShortcuts = 0;
        for (int i = 0; i < shortcutCount; i++) {
            if (getShortcutAt(i).isOpenOrOpening()) {
                numOpenShortcuts++;
            }
        }
        final long duration = getResources().getInteger(
                R.integer.config_deepShortcutCloseDuration);
        final long arrowScaleDuration = getResources().getInteger(
                R.integer.config_deepShortcutArrowOpenDuration);
        final long stagger = getResources().getInteger(
                R.integer.config_deepShortcutCloseStagger);
        final TimeInterpolator fadeInterpolator = new LogAccelerateInterpolator(100, 0);

        int firstOpenShortcutIndex = mIsAboveIcon ? shortcutCount - numOpenShortcuts : 0;
        for (int i = firstOpenShortcutIndex; i < firstOpenShortcutIndex + numOpenShortcuts; i++) {
            final DeepShortcutView view = getShortcutAt(i);
            Animator anim;
            if (view.willDrawIcon()) {
                anim = view.createCloseAnimation(mIsAboveIcon, mIsLeftAligned, duration);
                int animationIndex = mIsAboveIcon ? i - firstOpenShortcutIndex
                        : numOpenShortcuts - i - 1;
                anim.setStartDelay(stagger * animationIndex);

                Animator fadeAnim = new LauncherViewPropertyAnimator(view).alpha(0);
                // Don't start fading until the arrow is gone.
                fadeAnim.setStartDelay(stagger * animationIndex + arrowScaleDuration);
                fadeAnim.setDuration(duration - arrowScaleDuration);
                fadeAnim.setInterpolator(fadeInterpolator);
                shortcutAnims.play(fadeAnim);
            } else {
                // The view is being dragged. Animate it such that it collapses with the drag view
                anim = view.collapseToIcon();
                anim.setDuration(DragView.VIEW_ZOOM_DURATION);

                // Scale and translate the view to follow the drag view.
                Point iconCenter = view.getIconCenter();
                view.setPivotX(iconCenter.x);
                view.setPivotY(iconCenter.y);

                float scale = ((float) mLauncher.getDeviceProfile().iconSizePx) / view.getHeight();
                LauncherViewPropertyAnimator anim2 = new LauncherViewPropertyAnimator(view)
                        .scaleX(scale)
                        .scaleY(scale)
                        .translationX(mIconShift.x)
                        .translationY(mIconShift.y);
                anim2.setDuration(DragView.VIEW_ZOOM_DURATION);
                shortcutAnims.play(anim2);
            }
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    view.setVisibility(INVISIBLE);
                }
            });
            shortcutAnims.play(anim);
        }
        Animator arrowAnim = new LauncherViewPropertyAnimator(mArrow)
                .scaleX(0).scaleY(0).setDuration(arrowScaleDuration);
        arrowAnim.setStartDelay(0);
        shortcutAnims.play(arrowAnim);

        shortcutAnims.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mOpenCloseAnimator = null;
                if (mDeferContainerRemoval) {
                    setVisibility(INVISIBLE);
                } else {
                    close();
                }
            }
        });
        mOpenCloseAnimator = shortcutAnims;
        shortcutAnims.start();
    }

    /**
     * Closes the folder without animation.
     */
    public void close() {
        if (mOpenCloseAnimator != null) {
            mOpenCloseAnimator.cancel();
            mOpenCloseAnimator = null;
        }
        mIsOpen = false;
        mDeferContainerRemoval = false;
        boolean isInHotseat = ((ItemInfo) mDeferredDragIcon.getTag()).container
                == LauncherSettings.Favorites.CONTAINER_HOTSEAT;
        mDeferredDragIcon.setTextVisibility(!isInHotseat);
        mLauncher.getDragController().removeDragListener(this);
        mLauncher.getDragLayer().removeView(this);
    }

    public boolean isOpen() {
        return mIsOpen;
    }

    /**
     * Shows the shortcuts container for {@param icon}
     * @return the container if shown or null.
     */
    public static DeepShortcutsContainer showForIcon(BubbleTextView icon) {
        Launcher launcher = Launcher.getLauncher(icon.getContext());
        if (launcher.getOpenShortcutsContainer() != null) {
            // There is already a shortcuts container open, so don't open this one.
            icon.clearFocus();
            return null;
        }
        List<String> ids = launcher.getShortcutIdsForItem((ItemInfo) icon.getTag());
        if (!ids.isEmpty()) {
            // There are shortcuts associated with the app, so defer its drag.
            final DeepShortcutsContainer container =
                    (DeepShortcutsContainer) launcher.getLayoutInflater().inflate(
                            R.layout.deep_shortcuts_container, launcher.getDragLayer(), false);
            container.setVisibility(View.INVISIBLE);
            launcher.getDragLayer().addView(container);
            container.populateAndShow(icon, ids);
            return container;
        }
        return null;
    }

    /**
     * Extension of {@link ShortcutInfo} which does not badge the icons.
     */
    static class UnbadgedShortcutInfo extends ShortcutInfo {
        public final ShortcutInfoCompat mDetail;

        public UnbadgedShortcutInfo(ShortcutInfoCompat shortcutInfo, Context context) {
            super(shortcutInfo, context);
            mDetail = shortcutInfo;
        }

        @Override
        protected Bitmap getBadgedIcon(Bitmap unbadgedBitmap, ShortcutInfoCompat shortcutInfo,
                IconCache cache, Context context) {
            return unbadgedBitmap;
        }
    }
}
