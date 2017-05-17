package com.android.launcher3;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.android.launcher3.accessibility.DragViewStateAnnouncer;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.util.FocusLogic;
import com.android.launcher3.util.TouchController;

public class AppWidgetResizeFrame extends FrameLayout
        implements View.OnKeyListener, TouchController {
    private static final int SNAP_DURATION = 150;
    private static final float DIMMED_HANDLE_ALPHA = 0f;
    private static final float RESIZE_THRESHOLD = 0.66f;

    private static final Rect sTmpRect = new Rect();

    // Represents the cell size on the grid in the two orientations.
    private static Point[] sCellSize;

    private static final int HANDLE_COUNT = 4;
    private static final int INDEX_LEFT = 0;
    private static final int INDEX_TOP = 1;
    private static final int INDEX_RIGHT = 2;
    private static final int INDEX_BOTTOM = 3;

    private final Launcher mLauncher;
    private final DragViewStateAnnouncer mStateAnnouncer;

    private final View[] mDragHandles = new View[HANDLE_COUNT];

    private LauncherAppWidgetHostView mWidgetView;
    private CellLayout mCellLayout;
    private DragLayer mDragLayer;

    private Rect mWidgetPadding;

    private final int mBackgroundPadding;
    private final int mTouchTargetWidth;

    private final int[] mDirectionVector = new int[2];
    private final int[] mLastDirectionVector = new int[2];
    private final int[] mTmpPt = new int[2];

    private final IntRange mTempRange1 = new IntRange();
    private final IntRange mTempRange2 = new IntRange();

    private final IntRange mDeltaXRange = new IntRange();
    private final IntRange mBaselineX = new IntRange();

    private final IntRange mDeltaYRange = new IntRange();
    private final IntRange mBaselineY = new IntRange();

    private boolean mLeftBorderActive;
    private boolean mRightBorderActive;
    private boolean mTopBorderActive;
    private boolean mBottomBorderActive;

    private int mResizeMode;

    private int mRunningHInc;
    private int mRunningVInc;
    private int mMinHSpan;
    private int mMinVSpan;
    private int mDeltaX;
    private int mDeltaY;
    private int mDeltaXAddOn;
    private int mDeltaYAddOn;

    private int mTopTouchRegionAdjustment = 0;
    private int mBottomTouchRegionAdjustment = 0;

    private int mXDown, mYDown;

    public AppWidgetResizeFrame(Context context) {
        this(context, null);
    }

    public AppWidgetResizeFrame(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppWidgetResizeFrame(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mLauncher = Launcher.getLauncher(context);
        mStateAnnouncer = DragViewStateAnnouncer.createFor(this);

        mBackgroundPadding = getResources()
                .getDimensionPixelSize(R.dimen.resize_frame_background_padding);
        mTouchTargetWidth = 2 * mBackgroundPadding;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        for (int i = 0; i < HANDLE_COUNT; i ++) {
            mDragHandles[i] = getChildAt(i);
        }
    }

    public void setupForWidget(LauncherAppWidgetHostView widgetView, CellLayout cellLayout,
            DragLayer dragLayer) {
        mCellLayout = cellLayout;
        mWidgetView = widgetView;
        LauncherAppWidgetProviderInfo info = (LauncherAppWidgetProviderInfo)
                widgetView.getAppWidgetInfo();
        mResizeMode = info.resizeMode;
        mDragLayer = dragLayer;

        mMinHSpan = info.minSpanX;
        mMinVSpan = info.minSpanY;

        if (!info.isCustomWidget) {
            mWidgetPadding = AppWidgetHostView.getDefaultPaddingForWidget(getContext(),
                    widgetView.getAppWidgetInfo().provider, null);
        } else {
            Resources r = getContext().getResources();
            int padding = r.getDimensionPixelSize(R.dimen.default_widget_padding);
            mWidgetPadding = new Rect(padding, padding, padding, padding);
        }

        if (mResizeMode == AppWidgetProviderInfo.RESIZE_HORIZONTAL) {
            mDragHandles[INDEX_TOP].setVisibility(GONE);
            mDragHandles[INDEX_BOTTOM].setVisibility(GONE);
        } else if (mResizeMode == AppWidgetProviderInfo.RESIZE_VERTICAL) {
            mDragHandles[INDEX_LEFT].setVisibility(GONE);
            mDragHandles[INDEX_RIGHT].setVisibility(GONE);
        }

        // When we create the resize frame, we first mark all cells as unoccupied. The appropriate
        // cells (same if not resized, or different) will be marked as occupied when the resize
        // frame is dismissed.
        mCellLayout.markCellsAsUnoccupiedForView(mWidgetView);

        setOnKeyListener(this);
    }

    public boolean beginResizeIfPointInRegion(int x, int y) {
        boolean horizontalActive = (mResizeMode & AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0;
        boolean verticalActive = (mResizeMode & AppWidgetProviderInfo.RESIZE_VERTICAL) != 0;

        mLeftBorderActive = (x < mTouchTargetWidth) && horizontalActive;
        mRightBorderActive = (x > getWidth() - mTouchTargetWidth) && horizontalActive;
        mTopBorderActive = (y < mTouchTargetWidth + mTopTouchRegionAdjustment) && verticalActive;
        mBottomBorderActive = (y > getHeight() - mTouchTargetWidth + mBottomTouchRegionAdjustment)
                && verticalActive;

        boolean anyBordersActive = mLeftBorderActive || mRightBorderActive
                || mTopBorderActive || mBottomBorderActive;

        if (anyBordersActive) {
            mDragHandles[INDEX_LEFT].setAlpha(mLeftBorderActive ? 1.0f : DIMMED_HANDLE_ALPHA);
            mDragHandles[INDEX_RIGHT].setAlpha(mRightBorderActive ? 1.0f :DIMMED_HANDLE_ALPHA);
            mDragHandles[INDEX_TOP].setAlpha(mTopBorderActive ? 1.0f : DIMMED_HANDLE_ALPHA);
            mDragHandles[INDEX_BOTTOM].setAlpha(mBottomBorderActive ? 1.0f : DIMMED_HANDLE_ALPHA);
        }

        if (mLeftBorderActive) {
            mDeltaXRange.set(-getLeft(), getWidth() - 2 * mTouchTargetWidth);
        } else if (mRightBorderActive) {
            mDeltaXRange.set(2 * mTouchTargetWidth - getWidth(), mDragLayer.getWidth() - getRight());
        } else {
            mDeltaXRange.set(0, 0);
        }
        mBaselineX.set(getLeft(), getRight());

        if (mTopBorderActive) {
            mDeltaYRange.set(-getTop(), getHeight() - 2 * mTouchTargetWidth);
        } else if (mBottomBorderActive) {
            mDeltaYRange.set(2 * mTouchTargetWidth - getHeight(), mDragLayer.getHeight() - getBottom());
        } else {
            mDeltaYRange.set(0, 0);
        }
        mBaselineY.set(getTop(), getBottom());

        return anyBordersActive;
    }

    /**
     *  Based on the deltas, we resize the frame.
     */
    public void visualizeResizeForDelta(int deltaX, int deltaY) {
        mDeltaX = mDeltaXRange.clamp(deltaX);
        mDeltaY = mDeltaYRange.clamp(deltaY);

        DragLayer.LayoutParams lp = (DragLayer.LayoutParams) getLayoutParams();
        mDeltaX = mDeltaXRange.clamp(deltaX);
        mBaselineX.applyDelta(mLeftBorderActive, mRightBorderActive, mDeltaX, mTempRange1);
        lp.x = mTempRange1.start;
        lp.width = mTempRange1.size();

        mDeltaY = mDeltaYRange.clamp(deltaY);
        mBaselineY.applyDelta(mTopBorderActive, mBottomBorderActive, mDeltaY, mTempRange1);
        lp.y = mTempRange1.start;
        lp.height = mTempRange1.size();

        resizeWidgetIfNeeded(false);

        // When the widget resizes in multi-window mode, the translation value changes to maintain
        // a center fit. These overrides ensure the resize frame always aligns with the widget view.
        getSnappedRectRelativeToDragLayer(sTmpRect);
        if (mLeftBorderActive) {
            lp.width = sTmpRect.width() + sTmpRect.left - lp.x;
        }
        if (mTopBorderActive) {
            lp.height = sTmpRect.height() + sTmpRect.top - lp.y;
        }
        if (mRightBorderActive) {
            lp.x = sTmpRect.left;
        }
        if (mBottomBorderActive) {
            lp.y = sTmpRect.top;
        }

        requestLayout();
    }

    private static int getSpanIncrement(float deltaFrac) {
        return Math.abs(deltaFrac) > RESIZE_THRESHOLD ? Math.round(deltaFrac) : 0;
    }

    /**
     *  Based on the current deltas, we determine if and how to resize the widget.
     */
    private void resizeWidgetIfNeeded(boolean onDismiss) {
        float xThreshold = mCellLayout.getCellWidth();
        float yThreshold = mCellLayout.getCellHeight();

        int hSpanInc = getSpanIncrement((mDeltaX + mDeltaXAddOn) / xThreshold - mRunningHInc);
        int vSpanInc = getSpanIncrement((mDeltaY + mDeltaYAddOn) / yThreshold - mRunningVInc);

        if (!onDismiss && (hSpanInc == 0 && vSpanInc == 0)) return;

        mDirectionVector[0] = 0;
        mDirectionVector[1] = 0;

        CellLayout.LayoutParams lp = (CellLayout.LayoutParams) mWidgetView.getLayoutParams();

        int spanX = lp.cellHSpan;
        int spanY = lp.cellVSpan;
        int cellX = lp.useTmpCoords ? lp.tmpCellX : lp.cellX;
        int cellY = lp.useTmpCoords ? lp.tmpCellY : lp.cellY;

        // For each border, we bound the resizing based on the minimum width, and the maximum
        // expandability.
        mTempRange1.set(cellX, spanX + cellX);
        int hSpanDelta = mTempRange1.applyDeltaAndBound(mLeftBorderActive, mRightBorderActive,
                hSpanInc, mMinHSpan, mCellLayout.getCountX(), mTempRange2);
        cellX = mTempRange2.start;
        spanX = mTempRange2.size();
        if (hSpanDelta != 0) {
            mDirectionVector[0] = mLeftBorderActive ? -1 : 1;
        }

        mTempRange1.set(cellY, spanY + cellY);
        int vSpanDelta = mTempRange1.applyDeltaAndBound(mTopBorderActive, mBottomBorderActive,
                vSpanInc, mMinVSpan, mCellLayout.getCountY(), mTempRange2);
        cellY = mTempRange2.start;
        spanY = mTempRange2.size();
        if (vSpanDelta != 0) {
            mDirectionVector[1] = mTopBorderActive ? -1 : 1;
        }

        if (!onDismiss && vSpanDelta == 0 && hSpanDelta == 0) return;

        // We always want the final commit to match the feedback, so we make sure to use the
        // last used direction vector when committing the resize / reorder.
        if (onDismiss) {
            mDirectionVector[0] = mLastDirectionVector[0];
            mDirectionVector[1] = mLastDirectionVector[1];
        } else {
            mLastDirectionVector[0] = mDirectionVector[0];
            mLastDirectionVector[1] = mDirectionVector[1];
        }

        if (mCellLayout.createAreaForResize(cellX, cellY, spanX, spanY, mWidgetView,
                mDirectionVector, onDismiss)) {
            if (mStateAnnouncer != null && (lp.cellHSpan != spanX || lp.cellVSpan != spanY) ) {
                mStateAnnouncer.announce(
                        mLauncher.getString(R.string.widget_resized, spanX, spanY));
            }

            lp.tmpCellX = cellX;
            lp.tmpCellY = cellY;
            lp.cellHSpan = spanX;
            lp.cellVSpan = spanY;
            mRunningVInc += vSpanDelta;
            mRunningHInc += hSpanDelta;

            if (!onDismiss) {
                updateWidgetSizeRanges(mWidgetView, mLauncher, spanX, spanY);
            }
        }
        mWidgetView.requestLayout();
    }

    static void updateWidgetSizeRanges(AppWidgetHostView widgetView, Launcher launcher,
            int spanX, int spanY) {
        getWidgetSizeRanges(launcher, spanX, spanY, sTmpRect);
        widgetView.updateAppWidgetSize(null, sTmpRect.left, sTmpRect.top,
                sTmpRect.right, sTmpRect.bottom);
    }

    public static Rect getWidgetSizeRanges(Context context, int spanX, int spanY, Rect rect) {
        if (sCellSize == null) {
            InvariantDeviceProfile inv = LauncherAppState.getIDP(context);

            // Initiate cell sizes.
            sCellSize = new Point[2];
            sCellSize[0] = inv.landscapeProfile.getCellSize();
            sCellSize[1] = inv.portraitProfile.getCellSize();
        }

        if (rect == null) {
            rect = new Rect();
        }
        final float density = context.getResources().getDisplayMetrics().density;

        // Compute landscape size
        int landWidth = (int) ((spanX * sCellSize[0].x) / density);
        int landHeight = (int) ((spanY * sCellSize[0].y) / density);

        // Compute portrait size
        int portWidth = (int) ((spanX * sCellSize[1].x) / density);
        int portHeight = (int) ((spanY * sCellSize[1].y) / density);
        rect.set(portWidth, landHeight, landWidth, portHeight);
        return rect;
    }

    /**
     * This is the final step of the resize. Here we save the new widget size and position
     * to LauncherModel and animate the resize frame.
     */
    public void commitResize() {
        resizeWidgetIfNeeded(true);
        requestLayout();
    }

    private void onTouchUp() {
        int xThreshold = mCellLayout.getCellWidth();
        int yThreshold = mCellLayout.getCellHeight();

        mDeltaXAddOn = mRunningHInc * xThreshold;
        mDeltaYAddOn = mRunningVInc * yThreshold;
        mDeltaX = 0;
        mDeltaY = 0;

        post(new Runnable() {
            @Override
            public void run() {
                snapToWidget(true);
            }
        });
    }

    /**
     * Returns the rect of this view when the frame is snapped around the widget, with the bounds
     * relative to the {@link DragLayer}.
     */
    private void getSnappedRectRelativeToDragLayer(Rect out) {
        float scale = mWidgetView.getScaleToFit();

        mDragLayer.getViewRectRelativeToSelf(mWidgetView, out);

        int width = 2 * mBackgroundPadding
                + (int) (scale * (out.width() - mWidgetPadding.left - mWidgetPadding.right));
        int height = 2 * mBackgroundPadding
                + (int) (scale * (out.height() - mWidgetPadding.top - mWidgetPadding.bottom));

        int x = (int) (out.left - mBackgroundPadding + scale * mWidgetPadding.left);
        int y = (int) (out.top - mBackgroundPadding + scale * mWidgetPadding.top);

        out.left = x;
        out.top = y;
        out.right = out.left + width;
        out.bottom = out.top + height;
    }

    public void snapToWidget(boolean animate) {
        getSnappedRectRelativeToDragLayer(sTmpRect);
        int newWidth = sTmpRect.width();
        int newHeight = sTmpRect.height();
        int newX = sTmpRect.left;
        int newY = sTmpRect.top;

        // We need to make sure the frame's touchable regions lie fully within the bounds of the
        // DragLayer. We allow the actual handles to be clipped, but we shift the touch regions
        // down accordingly to provide a proper touch target.
        if (newY < 0) {
            // In this case we shift the touch region down to start at the top of the DragLayer
            mTopTouchRegionAdjustment = -newY;
        } else {
            mTopTouchRegionAdjustment = 0;
        }
        if (newY + newHeight > mDragLayer.getHeight()) {
            // In this case we shift the touch region up to end at the bottom of the DragLayer
            mBottomTouchRegionAdjustment = -(newY + newHeight - mDragLayer.getHeight());
        } else {
            mBottomTouchRegionAdjustment = 0;
        }

        final DragLayer.LayoutParams lp = (DragLayer.LayoutParams) getLayoutParams();
        if (!animate) {
            lp.width = newWidth;
            lp.height = newHeight;
            lp.x = newX;
            lp.y = newY;
            for (int i = 0; i < HANDLE_COUNT; i++) {
                mDragHandles[i].setAlpha(1.0f);
            }
            requestLayout();
        } else {
            PropertyValuesHolder width = PropertyValuesHolder.ofInt("width", lp.width, newWidth);
            PropertyValuesHolder height = PropertyValuesHolder.ofInt("height", lp.height,
                    newHeight);
            PropertyValuesHolder x = PropertyValuesHolder.ofInt("x", lp.x, newX);
            PropertyValuesHolder y = PropertyValuesHolder.ofInt("y", lp.y, newY);
            ObjectAnimator oa =
                    LauncherAnimUtils.ofPropertyValuesHolder(lp, this, width, height, x, y);
            oa.addUpdateListener(new AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator animation) {
                    requestLayout();
                }
            });
            AnimatorSet set = LauncherAnimUtils.createAnimatorSet();
            set.play(oa);
            for (int i = 0; i < HANDLE_COUNT; i++) {
                set.play(LauncherAnimUtils.ofFloat(mDragHandles[i], ALPHA, 1.0f));
            }

            set.setDuration(SNAP_DURATION);
            set.start();
        }

        setFocusableInTouchMode(true);
        requestFocus();
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        // Clear the frame and give focus to the widget host view when a directional key is pressed.
        if (FocusLogic.shouldConsume(keyCode)) {
            mDragLayer.clearResizeFrame();
            mWidgetView.requestFocus();
            return true;
        }
        return false;
    }

    private boolean handleTouchDown(MotionEvent ev) {
        Rect hitRect = new Rect();
        int x = (int) ev.getX();
        int y = (int) ev.getY();

        getHitRect(hitRect);
        if (hitRect.contains(x, y)) {
            if (beginResizeIfPointInRegion(x - getLeft(), y - getTop())) {
                mXDown = x;
                mYDown = y;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        int action = ev.getAction();
        int x = (int) ev.getX();
        int y = (int) ev.getY();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                return handleTouchDown(ev);
            case MotionEvent.ACTION_MOVE:
                visualizeResizeForDelta(x - mXDown, y - mYDown);
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                visualizeResizeForDelta(x - mXDown, y - mYDown);
                onTouchUp();
                mXDown = mYDown = 0;
                break;
        }
        return true;
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN && handleTouchDown(ev)) {
            return true;
        }
        return false;
    }

    /**
     * A mutable class for describing the range of two int values.
     */
    private static class IntRange {

        public int start, end;

        public int clamp(int value) {
            return Utilities.boundToRange(value, start, end);
        }

        public void set(int s, int e) {
            start = s;
            end = e;
        }

        public int size() {
            return end - start;
        }

        /**
         * Moves either the start or end edge (but never both) by {@param delta} and  sets the
         * result in {@param out}
         */
        public void applyDelta(boolean moveStart, boolean moveEnd, int delta, IntRange out) {
            out.start = moveStart ? start + delta : start;
            out.end = moveEnd ? end + delta : end;
        }

        /**
         * Applies delta similar to {@link #applyDelta(boolean, boolean, int, IntRange)},
         * with extra conditions.
         * @param minSize minimum size after with the moving edge should not be shifted any further.
         *                For eg, if delta = -3 when moving the endEdge brings the size to less than
         *                minSize, only delta = -2 will applied
         * @param maxEnd The maximum value to the end edge (start edge is always restricted to 0)
         * @return the amount of increase when endEdge was moves and the amount of decrease when
         * the start edge was moved.
         */
        public int applyDeltaAndBound(boolean moveStart, boolean moveEnd, int delta,
                int minSize, int maxEnd, IntRange out) {
            applyDelta(moveStart, moveEnd, delta, out);
            if (out.start < 0) {
                out.start = 0;
            }
            if (out.end > maxEnd) {
                out.end = maxEnd;
            }
            if (out.size() < minSize) {
                if (moveStart) {
                    out.start = out.end - minSize;
                } else if (moveEnd) {
                    out.end = out.start + minSize;
                }
            }
            return moveEnd ? out.size() - size() : size() - out.size();
        }
    }
}
