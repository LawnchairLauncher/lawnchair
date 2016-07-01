package com.android.launcher3.shortcuts;

import android.animation.Animator;
import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LogDecelerateInterpolator;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.compat.UserHandleCompat;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.logging.UserEventDispatcher;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;
import com.android.launcher3.util.UiThreadCircularReveal;

import java.util.ArrayList;
import java.util.List;

/**
 * A container for shortcuts to deep links within apps.
 */
@TargetApi(Build.VERSION_CODES.N)
public class DeepShortcutsContainer extends LinearLayout implements View.OnLongClickListener,
        View.OnTouchListener, DragSource, DragController.DragListener,
        UserEventDispatcher.LaunchSourceProvider {
    private static final String TAG = "ShortcutsContainer";

    private Launcher mLauncher;
    private DeepShortcutManager mDeepShortcutsManager;
    private final int mDragDeadzone;
    private final int mStartDragThreshold;
    private BubbleTextView mDeferredDragIcon;
    private int mActivePointerId;
    private Point mTouchDown = null;
    private DragView mDragView;
    private float mLastX, mLastY;
    private float mDistanceDragged = 0;
    private final Rect mTempRect = new Rect();
    private final int[] mTempXY = new int[2];
    private Point mIconLastTouchPos = new Point();
    private boolean mIsLeftAligned;
    private boolean mIsAboveIcon;

    public DeepShortcutsContainer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mLauncher = (Launcher) context;
        mDeepShortcutsManager = LauncherAppState.getInstance().getShortcutManager();

        mDragDeadzone = ViewConfiguration.get(context).getScaledTouchSlop();
        mStartDragThreshold = getResources().getDimensionPixelSize(
                R.dimen.deep_shortcuts_start_drag_threshold);
    }

    public DeepShortcutsContainer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DeepShortcutsContainer(Context context) {
        this(context, null, 0);
    }

    public void populateAndShow(final BubbleTextView originalIcon, final List<String> ids) {
        // Add dummy views first, and populate with real shortcut info when ready.
        for (int i = 0; i < ids.size(); i++) {
            final DeepShortcutView shortcut = (DeepShortcutView)
                    mLauncher.getLayoutInflater().inflate(R.layout.deep_shortcut, this, false);
            if (i < ids.size() - 1) {
                ((LayoutParams) shortcut.getLayoutParams()).bottomMargin = shortcut.getSpacing();
            }
            addView(shortcut);
        }

        measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        animateOpen(originalIcon);

        deferDrag(originalIcon);

        // Load the shortcuts on a background thread and update the container as it animates.
        final ItemInfo originalInfo = (ItemInfo) originalIcon.getTag();
        final UserHandleCompat user = originalInfo.user;
        final ComponentName activity = originalInfo.getTargetComponent();
        new AsyncTask<Void, Void, List<ShortcutInfo>>() {
            public List<ShortcutInfo> doInBackground(Void ... args) {
                List<ShortcutInfoCompat> shortcuts = mDeepShortcutsManager
                        .queryForAllAppShortcuts(activity, ids, user);
                List<ShortcutInfo> shortcutInfos = new ArrayList<>(shortcuts.size());
                for (ShortcutInfoCompat shortcut : shortcuts) {
                    shortcutInfos.add(ShortcutInfo.fromDeepShortcutInfo(shortcut, mLauncher));
                }
                return shortcutInfos;
            }

            // TODO: implement onProgressUpdate() to load shortcuts one at a time.

            @Override
            protected void onPostExecute(List<ShortcutInfo> shortcuts) {
                for (int i = 0; i < shortcuts.size(); i++) {
                    DeepShortcutView iconAndText = (DeepShortcutView) getChildAt(i);
                    ShortcutInfo launcherShortcutInfo = shortcuts.get(i);
                    iconAndText.applyFromShortcutInfo(launcherShortcutInfo,
                            LauncherAppState.getInstance().getIconCache());
                    iconAndText.setOnClickListener(mLauncher);
                    iconAndText.setOnLongClickListener(DeepShortcutsContainer.this);
                    iconAndText.setOnTouchListener(DeepShortcutsContainer.this);
                    int viewId = mLauncher.getViewIdForItem(originalInfo);
                    iconAndText.setId(viewId);
                }
            }
        }.execute();
    }

    // TODO: update this animation
    private void animateOpen(BubbleTextView originalIcon) {
        orientAboutIcon(originalIcon);

        setVisibility(View.VISIBLE);
        int rx = (int) Math.max(Math.max(getMeasuredWidth() - getPivotX(), 0), getPivotX());
        int ry = (int) Math.max(Math.max(getMeasuredHeight() - getPivotY(), 0), getPivotY());
        float radius = (float) Math.hypot(rx, ry);
        Animator reveal = UiThreadCircularReveal.createCircularReveal(this, (int) getPivotX(),
                (int) getPivotY(), 0, radius);
        reveal.setDuration(getResources().getInteger(R.integer.config_materialFolderExpandDuration));
        reveal.setInterpolator(new LogDecelerateInterpolator(100, 0));
        reveal.start();
    }

    /**
     * Orients this container above or below the given icon, aligning with the left or right.
     *
     * These are the preferred orientations, in order:
     * - Above and left-aligned
     * - Above and right-aligned
     * - Below and left-aligned
     * - Below and right-aligned
     *
     * So we always align left if there is enough horizontal space
     * and align above if there is enough vertical space.
     *
     * TODO: draw pointer based on orientation.
     */
    private void orientAboutIcon(BubbleTextView icon) {
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();

        DragLayer dragLayer = mLauncher.getDragLayer();
        dragLayer.getDescendantRectRelativeToSelf(icon, mTempRect);
        // Align left and above by default.
        int x = mTempRect.left + icon.getPaddingLeft();
        int y = mTempRect.top - height;
        Rect insets = dragLayer.getInsets();

        mIsLeftAligned = x + width < dragLayer.getRight() - insets.right;
        if (!mIsLeftAligned) {
            x = mTempRect.right - width - icon.getPaddingRight();
        }

        mIsAboveIcon = mTempRect.top - height > dragLayer.getTop() + insets.top;
        if (!mIsAboveIcon) {
            y = mTempRect.bottom;
        }

        setPivotX(width / 2);
        setPivotY(height / 2);

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
        mDragView = new DragView(mLauncher, b, registrationX, registrationY,
                0, 0, b.getWidth(), b.getHeight(), 1f, scaleDps);
        mLastX = mLastY = mDistanceDragged = 0;
        mDragView.show(motionDownX, motionDownY);
    }

    public boolean onForwardedEvent(MotionEvent ev, int activePointerId, MotionEvent touchDownEvent) {
        mTouchDown = new Point((int) touchDownEvent.getX(), (int) touchDownEvent.getY());
        mActivePointerId = activePointerId;
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
        int childCount = getChildCount();
        if (action == MotionEvent.ACTION_MOVE) {
            if (mLastX != 0 || mLastY != 0) {
                mDistanceDragged += Math.hypot(mLastX - x, mLastY - y);
            }
            mLastX = x;
            mLastY = y;

            boolean containerContainsTouch = x >= 0 && y >= 0 && x < getWidth() && y < getHeight();
            if (shouldStartDeferredDrag((int) x, (int) y, containerContainsTouch)) {
                cleanupDeferredDrag();
                mDeferredDragIcon.getParent().requestDisallowInterceptTouchEvent(false);
                mDeferredDragIcon.getOnLongClickListener().onLongClick(mDeferredDragIcon);
                mLauncher.getDragController().onTouchEvent(ev);
                return true;
            } else {
                // Determine whether touch is over a shortcut.
                boolean hoveringOverShortcut = false;
                for (int i = 0; i < childCount; i++) {
                    DeepShortcutView shortcut = (DeepShortcutView) getChildAt(i);
                    if (shortcut.updateHoverState(containerContainsTouch, hoveringOverShortcut, y)) {
                        hoveringOverShortcut = true;
                    }
                }

                if (!hoveringOverShortcut && mDistanceDragged > mDragDeadzone) {
                    // After dragging further than a small deadzone,
                    // have the drag view follow the user's finger.
                    mDragView.setVisibility(VISIBLE);
                    mDragView.move(dragLayerX, dragLayerY);
                    mDeferredDragIcon.setVisibility(INVISIBLE);
                } else if (hoveringOverShortcut) {
                    // Jump drag view back to original place on grid,
                    // so user doesn't think they are still dragging.
                    // TODO: can we improve this interaction? maybe with a ghost icon or similar?
                    mDragView.setVisibility(INVISIBLE);
                    mDeferredDragIcon.setVisibility(VISIBLE);
                }
            }
        } else if (action == MotionEvent.ACTION_UP) {
            cleanupDeferredDrag();
            // Launch a shortcut if user was hovering over it.
            for (int i = 0; i < childCount; i++) {
                DeepShortcutView shortcut = (DeepShortcutView) getChildAt(i);
                if (shortcut.isHoveringOver()) {
                    shortcut.performClick();
                    break;
                }
            }
        }
        return true;
    }

    /**
     * Determines whether the deferred drag should be started based on touch coordinates
     * relative to the original icon and the shortcuts container.
     *
     * Current behavior:
     * - Compute distance from original touch down to closest container edge.
     * - Compute distance from latest touch (given x and y) and compare to original distance;
     *   if the new distance is larger than a threshold, the deferred drag should start.
     * - Never defer the drag if this container contains the touch.
     *
     * @param x the x touch coordinate relative to this container
     * @param y the y touch coordinate relative to this container
     */
    private boolean shouldStartDeferredDrag(int x, int y, boolean containerContainsTouch) {
        Point closestEdge = new Point(mTouchDown.x, mIsAboveIcon ? getMeasuredHeight() : 0);
        double distToEdge = Math.hypot(mTouchDown.x - closestEdge.x, mTouchDown.y - closestEdge.y);
        double newDistToEdge = Math.hypot(x - closestEdge.x, y - closestEdge.y);
        return  !containerContainsTouch && (newDistToEdge - distToEdge > mStartDragThreshold);
    }

    public void cleanupDeferredDrag() {
        if (mDragView != null) {
            mDragView.remove();
        }
        mDeferredDragIcon.setVisibility(VISIBLE);
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

        // Long clicked on a shortcut.
        mLauncher.getWorkspace().beginDragShared(v, mIconLastTouchPos, this, false);
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
        return (float) getResources().getDimensionPixelSize(R.dimen.deep_shortcut_icon_size)
                / mLauncher.getDeviceProfile().iconSizePx;
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
        target.itemType = LauncherLogProto.SHORTCUT; // TODO: change to DYNAMIC_SHORTCUT
        target.gridX = info.cellX;
        target.gridY = info.cellY;
        target.pageIndex = 0;
        targetParent.containerType = LauncherLogProto.FOLDER; // TODO: change to DYNAMIC_SHORTCUTS
    }
}
