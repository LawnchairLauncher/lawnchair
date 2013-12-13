package com.android.launcher3;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.graphics.Rect;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

public class AppWidgetResizeFrame extends FrameLayout {
    private LauncherAppWidgetHostView mWidgetView;
    private CellLayout mCellLayout;
    private DragLayer mDragLayer;
    private ImageView mLeftHandle;
    private ImageView mRightHandle;
    private ImageView mTopHandle;
    private ImageView mBottomHandle;

    private boolean mLeftBorderActive;
    private boolean mRightBorderActive;
    private boolean mTopBorderActive;
    private boolean mBottomBorderActive;

    private int mWidgetPaddingLeft;
    private int mWidgetPaddingRight;
    private int mWidgetPaddingTop;
    private int mWidgetPaddingBottom;

    private int mBaselineWidth;
    private int mBaselineHeight;
    private int mBaselineX;
    private int mBaselineY;

    private int mRunningHInc;
    private int mRunningVInc;
    private int mMinHSpan;
    private int mMinVSpan;
    private int mDeltaX;
    private int mDeltaY;
    private int mDeltaXAddOn;
    private int mDeltaYAddOn;

    private int mBackgroundPadding;
    private int mTouchTargetWidth;

    private int mTopTouchRegionAdjustment = 0;
    private int mBottomTouchRegionAdjustment = 0;

    int[] mDirectionVector = new int[2];
    int[] mLastDirectionVector = new int[2];
    int[] mTmpPt = new int[2];

    final int SNAP_DURATION = 150;
    final int BACKGROUND_PADDING = 24;
    final float DIMMED_HANDLE_ALPHA = 0f;
    final float RESIZE_THRESHOLD = 0.66f;

    private static Rect mTmpRect = new Rect();

    public static final int LEFT = 0;
    public static final int TOP = 1;
    public static final int RIGHT = 2;
    public static final int BOTTOM = 3;

    private Launcher mLauncher;

    public AppWidgetResizeFrame(Context context,
            LauncherAppWidgetHostView widgetView, CellLayout cellLayout, DragLayer dragLayer) {

        super(context);
        mLauncher = (Launcher) context;
        mCellLayout = cellLayout;
        mWidgetView = widgetView;
        mDragLayer = dragLayer;

        final AppWidgetProviderInfo info = widgetView.getAppWidgetInfo();
        int[] result = Launcher.getMinSpanForWidget(mLauncher, info);
        mMinHSpan = result[0];
        mMinVSpan = result[1];

        setBackgroundResource(R.drawable.widget_resize_frame_holo);
        setPadding(0, 0, 0, 0);

        LayoutParams lp;
        mLeftHandle = new ImageView(context);
        mLeftHandle.setImageResource(R.drawable.widget_resize_handle_left);
        lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 
                Gravity.LEFT | Gravity.CENTER_VERTICAL);
        addView(mLeftHandle, lp);

        mRightHandle = new ImageView(context);
        mRightHandle.setImageResource(R.drawable.widget_resize_handle_right);
        lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 
                Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        addView(mRightHandle, lp);

        mTopHandle = new ImageView(context);
        mTopHandle.setImageResource(R.drawable.widget_resize_handle_top);
        lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 
                Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        addView(mTopHandle, lp);

        mBottomHandle = new ImageView(context);
        mBottomHandle.setImageResource(R.drawable.widget_resize_handle_bottom);
        lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 
                Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        addView(mBottomHandle, lp);

        Rect p = AppWidgetHostView.getDefaultPaddingForWidget(context,
                widgetView.getAppWidgetInfo().provider, null);
        mWidgetPaddingLeft = p.left;
        mWidgetPaddingTop = p.top;
        mWidgetPaddingRight = p.right;
        mWidgetPaddingBottom = p.bottom;

        final float density = mLauncher.getResources().getDisplayMetrics().density;
        mBackgroundPadding = (int) Math.ceil(density * BACKGROUND_PADDING);
        mTouchTargetWidth = 2 * mBackgroundPadding;

        // When we create the resize frame, we first mark all cells as unoccupied. The appropriate
        // cells (same if not resized, or different) will be marked as occupied when the resize
        // frame is dismissed.
        mCellLayout.markCellsAsUnoccupiedForView(mWidgetView);
    }

    public boolean beginResizeIfPointInRegion(int x, int y) {

        mLeftBorderActive = x < mTouchTargetWidth;
        mRightBorderActive = x > getWidth() - mTouchTargetWidth;
        mTopBorderActive = y < mTouchTargetWidth + mTopTouchRegionAdjustment;
        mBottomBorderActive = y > getHeight() - mTouchTargetWidth + mBottomTouchRegionAdjustment;

        boolean anyBordersActive = mLeftBorderActive || mRightBorderActive
                || mTopBorderActive || mBottomBorderActive;

        mBaselineWidth = getMeasuredWidth();
        mBaselineHeight = getMeasuredHeight();
        mBaselineX = getLeft();
        mBaselineY = getTop();

        if (anyBordersActive) {
            mLeftHandle.setAlpha(mLeftBorderActive ? 1.0f : DIMMED_HANDLE_ALPHA);
            mRightHandle.setAlpha(mRightBorderActive ? 1.0f :DIMMED_HANDLE_ALPHA);
            mTopHandle.setAlpha(mTopBorderActive ? 1.0f : DIMMED_HANDLE_ALPHA);
            mBottomHandle.setAlpha(mBottomBorderActive ? 1.0f : DIMMED_HANDLE_ALPHA);
        }
        return anyBordersActive;
    }

    /**
     *  Here we bound the deltas such that the frame cannot be stretched beyond the extents
     *  of the CellLayout, and such that the frame's borders can't cross.
     */
    public void updateDeltas(int deltaX, int deltaY) {
        if (mLeftBorderActive) {
            mDeltaX = Math.max(-mBaselineX, deltaX); 
            mDeltaX = Math.min(mBaselineWidth - 2 * mTouchTargetWidth, mDeltaX);
        } else if (mRightBorderActive) {
            mDeltaX = Math.min(mDragLayer.getWidth() - (mBaselineX + mBaselineWidth), deltaX);
            mDeltaX = Math.max(-mBaselineWidth + 2 * mTouchTargetWidth, mDeltaX);
        }

        if (mTopBorderActive) {
            mDeltaY = Math.max(-mBaselineY, deltaY);
            mDeltaY = Math.min(mBaselineHeight - 2 * mTouchTargetWidth, mDeltaY);
        } else if (mBottomBorderActive) {
            mDeltaY = Math.min(mDragLayer.getHeight() - (mBaselineY + mBaselineHeight), deltaY);
            mDeltaY = Math.max(-mBaselineHeight + 2 * mTouchTargetWidth, mDeltaY);
        }
    }

    public void visualizeResizeForDelta(int deltaX, int deltaY) {
        visualizeResizeForDelta(deltaX, deltaY, false);
    }

    /**
     *  Based on the deltas, we resize the frame, and, if needed, we resize the widget.
     */
    private void visualizeResizeForDelta(int deltaX, int deltaY, boolean onDismiss) {
        updateDeltas(deltaX, deltaY);
        DragLayer.LayoutParams lp = (DragLayer.LayoutParams) getLayoutParams();

        if (mLeftBorderActive) {
            lp.x = mBaselineX + mDeltaX;
            lp.width = mBaselineWidth - mDeltaX;
        } else if (mRightBorderActive) {
            lp.width = mBaselineWidth + mDeltaX;
        }

        if (mTopBorderActive) {
            lp.y = mBaselineY + mDeltaY;
            lp.height = mBaselineHeight - mDeltaY;
        } else if (mBottomBorderActive) {
            lp.height = mBaselineHeight + mDeltaY;
        }

        resizeWidgetIfNeeded(onDismiss);
        requestLayout();
    }

    /**
     *  Based on the current deltas, we determine if and how to resize the widget.
     */
    private void resizeWidgetIfNeeded(boolean onDismiss) {
        int xThreshold = mCellLayout.getCellWidth() + mCellLayout.getWidthGap();
        int yThreshold = mCellLayout.getCellHeight() + mCellLayout.getHeightGap();

        int deltaX = mDeltaX + mDeltaXAddOn;
        int deltaY = mDeltaY + mDeltaYAddOn;

        float hSpanIncF = 1.0f * deltaX / xThreshold - mRunningHInc;
        float vSpanIncF = 1.0f * deltaY / yThreshold - mRunningVInc;

        int hSpanInc = 0;
        int vSpanInc = 0;
        int cellXInc = 0;
        int cellYInc = 0;

        int countX = mCellLayout.getCountX();
        int countY = mCellLayout.getCountY();

        if (Math.abs(hSpanIncF) > RESIZE_THRESHOLD) {
            hSpanInc = Math.round(hSpanIncF);
        }
        if (Math.abs(vSpanIncF) > RESIZE_THRESHOLD) {
            vSpanInc = Math.round(vSpanIncF);
        }

        if (!onDismiss && (hSpanInc == 0 && vSpanInc == 0)) return;


        CellLayout.LayoutParams lp = (CellLayout.LayoutParams) mWidgetView.getLayoutParams();

        int spanX = lp.cellHSpan;
        int spanY = lp.cellVSpan;
        int cellX = lp.useTmpCoords ? lp.tmpCellX : lp.cellX;
        int cellY = lp.useTmpCoords ? lp.tmpCellY : lp.cellY;

        int hSpanDelta = 0;
        int vSpanDelta = 0;

        // For each border, we bound the resizing based on the minimum width, and the maximum
        // expandability.
        if (mLeftBorderActive) {
            cellXInc = Math.max(-cellX, hSpanInc);
            cellXInc = Math.min(lp.cellHSpan - mMinHSpan, cellXInc);
            hSpanInc *= -1;
            hSpanInc = Math.min(cellX, hSpanInc);
            hSpanInc = Math.max(-(lp.cellHSpan - mMinHSpan), hSpanInc);
            hSpanDelta = -hSpanInc;

        } else if (mRightBorderActive) {
            hSpanInc = Math.min(countX - (cellX + spanX), hSpanInc);
            hSpanInc = Math.max(-(lp.cellHSpan - mMinHSpan), hSpanInc);
            hSpanDelta = hSpanInc;
        }

        if (mTopBorderActive) {
            cellYInc = Math.max(-cellY, vSpanInc);
            cellYInc = Math.min(lp.cellVSpan - mMinVSpan, cellYInc);
            vSpanInc *= -1;
            vSpanInc = Math.min(cellY, vSpanInc);
            vSpanInc = Math.max(-(lp.cellVSpan - mMinVSpan), vSpanInc);
            vSpanDelta = -vSpanInc;
        } else if (mBottomBorderActive) {
            vSpanInc = Math.min(countY - (cellY + spanY), vSpanInc);
            vSpanInc = Math.max(-(lp.cellVSpan - mMinVSpan), vSpanInc);
            vSpanDelta = vSpanInc;
        }

        mDirectionVector[0] = 0;
        mDirectionVector[1] = 0;
        // Update the widget's dimensions and position according to the deltas computed above
        if (mLeftBorderActive || mRightBorderActive) {
            spanX += hSpanInc;
            cellX += cellXInc;
            if (hSpanDelta != 0) {
                mDirectionVector[0] = mLeftBorderActive ? -1 : 1;
            }
        }

        if (mTopBorderActive || mBottomBorderActive) {
            spanY += vSpanInc;
            cellY += cellYInc;
            if (vSpanDelta != 0) {
                mDirectionVector[1] = mTopBorderActive ? -1 : 1;
            }
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

        getWidgetSizeRanges(launcher, spanX, spanY, mTmpRect);
        widgetView.updateAppWidgetSize(null, mTmpRect.left, mTmpRect.top,
                mTmpRect.right, mTmpRect.bottom);
    }

    static Rect getWidgetSizeRanges(Launcher launcher, int spanX, int spanY, Rect rect) {
        if (rect == null) {
            rect = new Rect();
        }
        Rect landMetrics = Workspace.getCellLayoutMetrics(launcher, CellLayout.LANDSCAPE);
        Rect portMetrics = Workspace.getCellLayoutMetrics(launcher, CellLayout.PORTRAIT);
        final float density = launcher.getResources().getDisplayMetrics().density;

        // Compute landscape size
        int cellWidth = landMetrics.left;
        int cellHeight = landMetrics.top;
        int widthGap = landMetrics.right;
        int heightGap = landMetrics.bottom;
        int landWidth = (int) ((spanX * cellWidth + (spanX - 1) * widthGap) / density);
        int landHeight = (int) ((spanY * cellHeight + (spanY - 1) * heightGap) / density);

        // Compute portrait size
        cellWidth = portMetrics.left;
        cellHeight = portMetrics.top;
        widthGap = portMetrics.right;
        heightGap = portMetrics.bottom;
        int portWidth = (int) ((spanX * cellWidth + (spanX - 1) * widthGap) / density);
        int portHeight = (int) ((spanY * cellHeight + (spanY - 1) * heightGap) / density);
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

    public void onTouchUp() {
        int xThreshold = mCellLayout.getCellWidth() + mCellLayout.getWidthGap();
        int yThreshold = mCellLayout.getCellHeight() + mCellLayout.getHeightGap();

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

    public void snapToWidget(boolean animate) {
        final DragLayer.LayoutParams lp = (DragLayer.LayoutParams) getLayoutParams();
        int newWidth = mWidgetView.getWidth() + 2 * mBackgroundPadding - mWidgetPaddingLeft -
                mWidgetPaddingRight;
        int newHeight = mWidgetView.getHeight() + 2 * mBackgroundPadding - mWidgetPaddingTop -
                mWidgetPaddingBottom;

        mTmpPt[0] = mWidgetView.getLeft();
        mTmpPt[1] = mWidgetView.getTop();
        mDragLayer.getDescendantCoordRelativeToSelf(mCellLayout.getShortcutsAndWidgets(), mTmpPt);

        int newX = mTmpPt[0] - mBackgroundPadding + mWidgetPaddingLeft;
        int newY = mTmpPt[1] - mBackgroundPadding + mWidgetPaddingTop;

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

        if (!animate) {
            lp.width = newWidth;
            lp.height = newHeight;
            lp.x = newX;
            lp.y = newY;
            mLeftHandle.setAlpha(1.0f);
            mRightHandle.setAlpha(1.0f);
            mTopHandle.setAlpha(1.0f);
            mBottomHandle.setAlpha(1.0f);
            requestLayout();
        } else {
            PropertyValuesHolder width = PropertyValuesHolder.ofInt("width", lp.width, newWidth);
            PropertyValuesHolder height = PropertyValuesHolder.ofInt("height", lp.height,
                    newHeight);
            PropertyValuesHolder x = PropertyValuesHolder.ofInt("x", lp.x, newX);
            PropertyValuesHolder y = PropertyValuesHolder.ofInt("y", lp.y, newY);
            ObjectAnimator oa =
                    LauncherAnimUtils.ofPropertyValuesHolder(lp, this, width, height, x, y);
            ObjectAnimator leftOa = LauncherAnimUtils.ofFloat(mLeftHandle, "alpha", 1.0f);
            ObjectAnimator rightOa = LauncherAnimUtils.ofFloat(mRightHandle, "alpha", 1.0f);
            ObjectAnimator topOa = LauncherAnimUtils.ofFloat(mTopHandle, "alpha", 1.0f);
            ObjectAnimator bottomOa = LauncherAnimUtils.ofFloat(mBottomHandle, "alpha", 1.0f);
            oa.addUpdateListener(new AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator animation) {
                    requestLayout();
                }
            });
            AnimatorSet set = LauncherAnimUtils.createAnimatorSet();
            set.playTogether(oa, leftOa, rightOa, topOa, bottomOa);

            set.setDuration(SNAP_DURATION);
            set.start();
        }
    }
}
