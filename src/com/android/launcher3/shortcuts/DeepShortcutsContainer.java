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

import android.animation.AnimatorSet;
import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.accessibility.ShortcutMenuAccessibilityDelegate;
import com.android.launcher3.compat.UserHandleCompat;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.graphics.ScaledPreviewProvider;
import com.android.launcher3.logging.UserEventDispatcher;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A container for shortcuts to deep links within apps.
 */
@TargetApi(Build.VERSION_CODES.N)
public class DeepShortcutsContainer extends LinearLayout implements View.OnLongClickListener,
        View.OnTouchListener, DragSource, DragController.DragListener,
        UserEventDispatcher.LaunchSourceProvider {
    private static final String TAG = "ShortcutsContainer";

    private final Launcher mLauncher;
    private final DeepShortcutManager mDeepShortcutsManager;
    private final int mDragDeadzone;
    private final int mStartDragThreshold;
    private final ShortcutMenuAccessibilityDelegate mAccessibilityDelegate;

    private BubbleTextView mDeferredDragIcon;
    private int mActivePointerId;
    private int[] mTouchDown = null;
    private DragView mDragView;
    private float mLastX, mLastY;
    private float mDistanceDragged = 0;
    private final Rect mTempRect = new Rect();
    private final int[] mTempXY = new int[2];
    private Point mIconLastTouchPos = new Point();
    private boolean mIsLeftAligned;
    private boolean mIsAboveIcon;

    private boolean mSrcIconDragStarted;

    /**
     * Sorts shortcuts in rank order, with manifest shortcuts coming before dynamic shortcuts.
     */
    private static final Comparator<ShortcutInfoCompat> sShortcutsComparator
            = new Comparator<ShortcutInfoCompat>() {
        @Override
        public int compare(ShortcutInfoCompat a, ShortcutInfoCompat b) {
            if (a.isDeclaredInManifest() && !b.isDeclaredInManifest()) {
                return -1;
            }
            if (!a.isDeclaredInManifest() && b.isDeclaredInManifest()) {
                return 1;
            }
            return Integer.compare(a.getRank(), b.getRank());
        }
    };

    private static final Comparator<ShortcutInfoCompat> sShortcutsComparatorReversed
            = Collections.reverseOrder(sShortcutsComparator);

    public DeepShortcutsContainer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mLauncher = (Launcher) context;
        mDeepShortcutsManager = LauncherAppState.getInstance().getShortcutManager();

        mDragDeadzone = ViewConfiguration.get(context).getScaledTouchSlop();
        mStartDragThreshold = getResources().getDimensionPixelSize(
                R.dimen.deep_shortcuts_start_drag_threshold);
        mAccessibilityDelegate = new ShortcutMenuAccessibilityDelegate(mLauncher);
    }

    public DeepShortcutsContainer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DeepShortcutsContainer(Context context) {
        this(context, null, 0);
    }

    public void populateAndShow(final BubbleTextView originalIcon, final List<String> ids) {
        // Add dummy views first, and populate with real shortcut info when ready.
        final int spacing = getResources().getDimensionPixelSize(R.dimen.deep_shortcuts_spacing);
        final LayoutInflater inflater = mLauncher.getLayoutInflater();
        for (int i = 0; i < ids.size(); i++) {
            final DeepShortcutView shortcut =
                    (DeepShortcutView) inflater.inflate(R.layout.deep_shortcut, this, false);
            if (i < ids.size() - 1) {
                ((LayoutParams) shortcut.getLayoutParams()).bottomMargin = spacing;
            }
            shortcut.getBubbleText().setAccessibilityDelegate(mAccessibilityDelegate);
            addView(shortcut);
        }

        measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        animateOpen(originalIcon);

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
                final List<ShortcutInfoCompat> shortcuts = mDeepShortcutsManager
                        .queryForShortcutsContainer(activity, ids, user);
                // We want the lowest rank to be closest to the user's finger.
                final Comparator<ShortcutInfoCompat> shortcutsComparator = mIsAboveIcon ?
                        sShortcutsComparatorReversed : sShortcutsComparator;
                Collections.sort(shortcuts, shortcutsComparator);
                for (int i = 0; i < shortcuts.size(); i++) {
                    final ShortcutInfoCompat shortcut = shortcuts.get(i);
                    final ShortcutInfo launcherShortcutInfo =
                            new UnbadgedShortcutInfo(shortcut, mLauncher);
                    CharSequence shortLabel = shortcut.getShortLabel();
                    CharSequence longLabel = shortcut.getLongLabel();
                    uiHandler.post(new UpdateShortcutChild(i, launcherShortcutInfo,
                            shortLabel, longLabel));
                }
            }
        });
    }

    /** Updates the child of this container at the given index based on the given shortcut info. */
    private class UpdateShortcutChild implements Runnable {
        private int mShortcutChildIndex;
        private ShortcutInfo mShortcutChildInfo;
        private CharSequence mShortLabel;
        private CharSequence mLongLabel;

        public UpdateShortcutChild(int shortcutChildIndex, ShortcutInfo shortcutChildInfo,
                CharSequence shortLabel, CharSequence longLabel) {
            mShortcutChildIndex = shortcutChildIndex;
            mShortcutChildInfo = shortcutChildInfo;
            mShortLabel = shortLabel;
            mLongLabel = longLabel;
        }

        @Override
        public void run() {
            BubbleTextView shortcutView = getShortcutAt(mShortcutChildIndex).getBubbleText();
            shortcutView.applyFromShortcutInfo(mShortcutChildInfo,
                    LauncherAppState.getInstance().getIconCache());
            // Use the long label as long as it exists and fits.
            int availableWidth = shortcutView.getWidth() - shortcutView.getTotalPaddingLeft()
                    - shortcutView.getTotalPaddingRight();
            boolean usingLongLabel = !TextUtils.isEmpty(mLongLabel)
                    && shortcutView.getPaint().measureText(mLongLabel.toString()) <= availableWidth;
            shortcutView.setText(usingLongLabel ? mLongLabel : mShortLabel);
            shortcutView.setOnClickListener(mLauncher);
            shortcutView.setOnLongClickListener(DeepShortcutsContainer.this);
            shortcutView.setOnTouchListener(DeepShortcutsContainer.this);
        }
    }

    private DeepShortcutView getShortcutAt(int index) {
        return (DeepShortcutView) getChildAt(index);
    }

    private void animateOpen(BubbleTextView originalIcon) {
        orientAboutIcon(originalIcon);

        setVisibility(View.VISIBLE);

        final AnimatorSet shortcutAnims = LauncherAnimUtils.createAnimatorSet();
        final int numShortcuts = getChildCount();
        final int arrowOffset = getResources().getDimensionPixelSize(
                R.dimen.deep_shortcuts_arrow_horizontal_offset);
        final int pivotX = mIsLeftAligned ? arrowOffset : getMeasuredWidth() - arrowOffset;
        final int pivotY = getShortcutAt(0).getMeasuredHeight() / 2;
        for (int i = 0; i < numShortcuts; i++) {
            DeepShortcutView deepShortcutView = getShortcutAt(i);
            deepShortcutView.setPivotX(pivotX);
            deepShortcutView.setPivotY(pivotY);
            int animationIndex = mIsAboveIcon ? numShortcuts - i - 1 : i;
            shortcutAnims.play(deepShortcutView.createOpenAnimation(animationIndex, mIsAboveIcon));
        }
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
     *
     * TODO: draw arrow based on orientation.
     */
    private void orientAboutIcon(BubbleTextView icon) {
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();

        DragLayer dragLayer = mLauncher.getDragLayer();
        dragLayer.getDescendantRectRelativeToSelf(icon, mTempRect);
        Rect insets = dragLayer.getInsets();

        // Align left (right in RTL) if there is room.
        boolean isRtl = Utilities.isRtl(getResources());
        int leftAlignedX = mTempRect.left + icon.getPaddingLeft();
        int rightAlignedX = mTempRect.right - width - icon.getPaddingRight();
        int x = leftAlignedX;
        boolean canBeLeftAligned = leftAlignedX + width < dragLayer.getRight() - insets.right;
        boolean canBeRightAligned = rightAlignedX > dragLayer.getLeft() + insets.left;
        if (!canBeLeftAligned || (isRtl && canBeRightAligned)) {
            x = rightAlignedX;
        }
        mIsLeftAligned = x == leftAlignedX;
        if (isRtl) {
            x -= dragLayer.getWidth() - width;
        }

        // Open above icon if there is room.
        int y = mTempRect.top - height;
        mIsAboveIcon = mTempRect.top - height > dragLayer.getTop() + insets.top;
        if (!mIsAboveIcon) {
            y = mTempRect.bottom;
        }

        // Insets are added later, so subtract them now.
        y -= insets.top;

        setX(x);
        setY(y);
    }

    private void deferDrag(BubbleTextView originalIcon) {
        mDeferredDragIcon = originalIcon;
        showDragView(originalIcon);
        mLauncher.getDragController().addDragListener(this);
    }

    public BubbleTextView getDeferredDragIcon() {
        return mDeferredDragIcon;
    }

    private void showDragView(BubbleTextView originalIcon) {
        // TODO: implement support for Drawable DragViews so we don't have to create a bitmap here.
        Bitmap b = Utilities.createIconBitmap(originalIcon.getIcon(), mLauncher);
        float scale = mLauncher.getDragLayer().getLocationInDragLayer(originalIcon, mTempXY);
        int dragLayerX = Math.round(mTempXY[0] - (b.getWidth() - scale * originalIcon.getWidth()) / 2);
        int dragLayerY = Math.round(mTempXY[1] - (b.getHeight() - scale * b.getHeight()) / 2
                - Workspace.DRAG_BITMAP_PADDING / 2) + originalIcon.getPaddingTop();
        int motionDownX = mLauncher.getDragController().getMotionDown().x;
        int motionDownY = mLauncher.getDragController().getMotionDown().y;
        final int registrationX = motionDownX - dragLayerX;
        final int registrationY = motionDownY - dragLayerY;

        float scaleDps = getResources().getDimensionPixelSize(R.dimen.deep_shortcuts_drag_view_scale);
        mDragView = new DragView(mLauncher, b, registrationX, registrationY, 1f, scaleDps);
        mLastX = mLastY = mDistanceDragged = 0;
        mDragView.show(motionDownX, motionDownY);
    }

    public boolean onForwardedEvent(MotionEvent ev, int activePointerId, int[] touchDown) {
        mActivePointerId = activePointerId;
        mTouchDown = touchDown;
        return dispatchTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mDeferredDragIcon == null) {
            return false;
        }

        final int activePointerIndex = ev.findPointerIndex(mActivePointerId);
        if (activePointerIndex < 0) {
            return false;
        }
        final float x = ev.getX(activePointerIndex);
        final float y = ev.getY(activePointerIndex);


        int action = ev.getAction();
        // The event was in this container's coordinate system before this,
        // but will be in DragLayer's coordinate system from now on.
        Utilities.translateEventCoordinates(this, mLauncher.getDragLayer(), ev);
        final int dragLayerX = (int) ev.getX();
        final int dragLayerY = (int) ev.getY();
        if (action == MotionEvent.ACTION_MOVE) {
            if (mLastX != 0 || mLastY != 0) {
                mDistanceDragged += Math.hypot(mLastX - x, mLastY - y);
            }
            mLastX = x;
            mLastY = y;

            if (shouldStartDeferredDrag((int) x, (int) y)) {
                mSrcIconDragStarted = true;
                cleanupDeferredDrag(true);
                mDeferredDragIcon.getParent().requestDisallowInterceptTouchEvent(false);
                mDeferredDragIcon.getOnLongClickListener().onLongClick(mDeferredDragIcon);
                mLauncher.getDragController().onTouchEvent(ev);
                return true;
            } else if (mDistanceDragged > mDragDeadzone) {
                // After dragging further than a small deadzone,
                // have the drag view follow the user's finger.
                mDragView.setVisibility(VISIBLE);
                mDragView.move(dragLayerX, dragLayerY);
                mDeferredDragIcon.setVisibility(INVISIBLE);
            }
        } else if (action == MotionEvent.ACTION_UP) {
            cleanupDeferredDrag(true);
        } else if (action == MotionEvent.ACTION_CANCEL) {
            // Do not change the source icon visibility if we are already dragging the source icon.
            cleanupDeferredDrag(!mSrcIconDragStarted);
        }
        return true;
    }

    /**
     * Determines whether the deferred drag should be started based on touch coordinates
     * relative to the original icon and the shortcuts container.
     *
     * Current behavior:
     * - Start the drag if the touch passes a certain distance from the original touch down.
     *
     * @param x the x touch coordinate relative to this container
     * @param y the y touch coordinate relative to this container
     */
    private boolean shouldStartDeferredDrag(int x, int y) {
        double distFromTouchDown = Math.hypot(x - mTouchDown[0], y - mTouchDown[1]);
        return distFromTouchDown > mStartDragThreshold;
    }

    public void cleanupDeferredDrag(boolean updateSrcVisibility) {
        if (mDragView != null) {
            mDragView.remove();
        }
        if (updateSrcVisibility) {
            mDeferredDragIcon.setVisibility(VISIBLE);
        }
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
        // Return early if this is not initiated from a touch
        if (!v.isInTouchMode()) return false;
        // Return if global dragging is not enabled
        if (!mLauncher.isDraggingEnabled()) return false;

        UnbadgedShortcutInfo unbadgedInfo = (UnbadgedShortcutInfo) v.getTag();
        ShortcutInfo badged = new ShortcutInfo(unbadgedInfo);
        // Queue an update task on the worker thread. This ensures that the badged
        // shortcut eventually gets its icon updated.
        mLauncher.getModel().updateShortcutInfo(unbadgedInfo.mDetail, badged);

        // Long clicked on a shortcut.
        mLauncher.getWorkspace().beginDragShared(v, mIconLastTouchPos, this, false, badged,
                new ScaledPreviewProvider(v));
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
        return true;
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
    public void onDragStart(DragSource source, ItemInfo info, int dragAction) {
        // Either the original icon or one of the shortcuts was dragged.
        // Hide the container, but don't remove it yet because that interferes with touch events.
        setVisibility(INVISIBLE);
    }

    @Override
    public void onDragEnd() {
        // Now remove the container.
        mLauncher.closeShortcutsContainer();
    }

    @Override
    public void fillInLaunchSourceData(View v, ItemInfo info, Target target, Target targetParent) {
        target.itemType = LauncherLogProto.DEEPSHORTCUT;
        // TODO: add target.rank
        targetParent.containerType = LauncherLogProto.DEEPSHORTCUTS;
    }

    /**
     * Shows the shortcuts container for {@param icon}
     * @return the container if shown or null.
     */
    public static DeepShortcutsContainer showForIcon(BubbleTextView icon) {
        Launcher launcher = Launcher.getLauncher(icon.getContext());
        List<String> ids = launcher.getShortcutIdsForItem((ItemInfo) icon.getTag());
        if (!ids.isEmpty()) {
            // There are shortcuts associated with the app, so defer its drag.
            final DeepShortcutsContainer container =
                    (DeepShortcutsContainer) launcher.getLayoutInflater().inflate(
                            R.layout.deep_shortcuts_container, launcher.getDragLayer(), false);
            container.setVisibility(View.INVISIBLE);
            launcher.getDragLayer().addView(container);
            container.populateAndShow(icon, ids);
            icon.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
            return container;
        }
        return null;
    }

    /**
     * Extension of {@link ShortcutInfo} which does not badge the icons.
     */
    private static class UnbadgedShortcutInfo extends ShortcutInfo {
        private final ShortcutInfoCompat mDetail;

        public UnbadgedShortcutInfo(ShortcutInfoCompat shortcutInfo, Context context) {
            super(shortcutInfo, context);
            mDetail = shortcutInfo;
        }

        @Override
        protected Bitmap getBadgedIcon(Drawable unbadgedIcon, Context context) {
            return Utilities.createScaledBitmapWithoutShadow(unbadgedIcon, context);
        }
    }
}
