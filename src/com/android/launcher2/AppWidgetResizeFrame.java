package com.android.launcher2;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.res.Resources;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.launcher.R;

public class AppWidgetResizeFrame extends FrameLayout {

    private ItemInfo mItemInfo;
    private LauncherAppWidgetHostView mWidgetView;
    private CellLayout mCellLayout;
    private DragLayer mDragLayer;
    private Workspace mWorkspace;
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
    private int mResizeMode;

    private int mRunningHInc;
    private int mRunningVInc;
    private int mMinHSpan;
    private int mMinVSpan;
    private int mDeltaX;
    private int mDeltaY;

    private int mBackgroundPadding;
    private int mTouchTargetWidth;

    private int mExpandability[] = new int[4];

    final int SNAP_DURATION = 150;
    final int BACKGROUND_PADDING = 24;
    final float DIMMED_HANDLE_ALPHA = 0f;
    final float RESIZE_THRESHOLD = 0.66f;

    public static final int LEFT = 0;
    public static final int TOP = 1;
    public static final int RIGHT = 2;
    public static final int BOTTOM = 3;

    private Launcher mLauncher;

    public AppWidgetResizeFrame(Context context, ItemInfo itemInfo, 
            LauncherAppWidgetHostView widgetView, CellLayout cellLayout, DragLayer dragLayer) {

        super(context);
        mLauncher = (Launcher) context;
        mItemInfo = itemInfo;
        mCellLayout = cellLayout;
        mWidgetView = widgetView;
        mResizeMode = widgetView.getAppWidgetInfo().resizeMode;
        mDragLayer = dragLayer;
        mWorkspace = (Workspace) dragLayer.findViewById(R.id.workspace);

        final AppWidgetProviderInfo info = widgetView.getAppWidgetInfo();
        int[] result = mLauncher.getMinResizeSpanForWidget(info, null);
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

        Launcher.Padding p = mLauncher.getPaddingForWidget(widgetView.getAppWidgetInfo().provider);
        mWidgetPaddingLeft = p.left;
        mWidgetPaddingTop = p.top;
        mWidgetPaddingRight = p.right;
        mWidgetPaddingBottom = p.bottom;

        if (mResizeMode == AppWidgetProviderInfo.RESIZE_HORIZONTAL) {
            mTopHandle.setVisibility(GONE);
            mBottomHandle.setVisibility(GONE);
        } else if (mResizeMode == AppWidgetProviderInfo.RESIZE_VERTICAL) {
            mLeftHandle.setVisibility(GONE);
            mRightHandle.setVisibility(GONE);
        }

        final float density = mLauncher.getResources().getDisplayMetrics().density;
        mBackgroundPadding = (int) Math.ceil(density * BACKGROUND_PADDING);
        mTouchTargetWidth = 2 * mBackgroundPadding;
    }

    public boolean beginResizeIfPointInRegion(int x, int y) {
        boolean horizontalActive = (mResizeMode & AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0;
        boolean verticalActive = (mResizeMode & AppWidgetProviderInfo.RESIZE_VERTICAL) != 0;
        mLeftBorderActive = (x < mTouchTargetWidth) && horizontalActive;
        mRightBorderActive = (x > getWidth() - mTouchTargetWidth) && horizontalActive;
        mTopBorderActive = (y < mTouchTargetWidth) && verticalActive;
        mBottomBorderActive = (y > getHeight() - mTouchTargetWidth) && verticalActive;

        boolean anyBordersActive = mLeftBorderActive || mRightBorderActive
                || mTopBorderActive || mBottomBorderActive;

        mBaselineWidth = getMeasuredWidth();
        mBaselineHeight = getMeasuredHeight();
        mBaselineX = getLeft();
        mBaselineY = getTop();
        mRunningHInc = 0;
        mRunningVInc = 0;

        if (anyBordersActive) {
            mLeftHandle.setAlpha(mLeftBorderActive ? 1.0f : DIMMED_HANDLE_ALPHA);
            mRightHandle.setAlpha(mRightBorderActive ? 1.0f :DIMMED_HANDLE_ALPHA);
            mTopHandle.setAlpha(mTopBorderActive ? 1.0f : DIMMED_HANDLE_ALPHA);
            mBottomHandle.setAlpha(mBottomBorderActive ? 1.0f : DIMMED_HANDLE_ALPHA);
        }
        mCellLayout.getExpandabilityArrayForView(mWidgetView, mExpandability);

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

    /**
     *  Based on the deltas, we resize the frame, and, if needed, we resize the widget.
     */
    public void visualizeResizeForDelta(int deltaX, int deltaY) {
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

        resizeWidgetIfNeeded();
        requestLayout();
    }

    /**
     *  Based on the current deltas, we determine if and how to resize the widget.
     */
    private void resizeWidgetIfNeeded() {
        int xThreshold = mCellLayout.getCellWidth() + mCellLayout.getWidthGap();
        int yThreshold = mCellLayout.getCellHeight() + mCellLayout.getHeightGap();

        float hSpanIncF = 1.0f * mDeltaX / xThreshold - mRunningHInc;
        float vSpanIncF = 1.0f * mDeltaY / yThreshold - mRunningVInc;

        int hSpanInc = 0;
        int vSpanInc = 0;
        int cellXInc = 0;
        int cellYInc = 0;

        if (Math.abs(hSpanIncF) > RESIZE_THRESHOLD) {
            hSpanInc = Math.round(hSpanIncF);
        }
        if (Math.abs(vSpanIncF) > RESIZE_THRESHOLD) {
            vSpanInc = Math.round(vSpanIncF);
        }

        if (hSpanInc == 0 && vSpanInc == 0) return;

        // Before we change the widget, we clear the occupied cells associated with it.
        // The new set of occupied cells is marked below, once the layout params are updated.
        mCellLayout.markCellsAsUnoccupiedForView(mWidgetView);

        CellLayout.LayoutParams lp = (CellLayout.LayoutParams) mWidgetView.getLayoutParams();

        // For each border, we bound the resizing based on the minimum width, and the maximum
        // expandability.
        if (mLeftBorderActive) {
            cellXInc = Math.max(-mExpandability[LEFT], hSpanInc);
            cellXInc = Math.min(lp.cellHSpan - mMinHSpan, cellXInc);
            hSpanInc *= -1;
            hSpanInc = Math.min(mExpandability[LEFT], hSpanInc);
            hSpanInc = Math.max(-(lp.cellHSpan - mMinHSpan), hSpanInc);
            mRunningHInc -= hSpanInc;
        } else if (mRightBorderActive) {
            hSpanInc = Math.min(mExpandability[RIGHT], hSpanInc);
            hSpanInc = Math.max(-(lp.cellHSpan - mMinHSpan), hSpanInc);
            mRunningHInc += hSpanInc;
        }

        if (mTopBorderActive) {
            cellYInc = Math.max(-mExpandability[TOP], vSpanInc);
            cellYInc = Math.min(lp.cellVSpan - mMinVSpan, cellYInc);
            vSpanInc *= -1;
            vSpanInc = Math.min(mExpandability[TOP], vSpanInc);
            vSpanInc = Math.max(-(lp.cellVSpan - mMinVSpan), vSpanInc);
            mRunningVInc -= vSpanInc;
        } else if (mBottomBorderActive) {
            vSpanInc = Math.min(mExpandability[BOTTOM], vSpanInc);
            vSpanInc = Math.max(-(lp.cellVSpan - mMinVSpan), vSpanInc);
            mRunningVInc += vSpanInc;
        }

        // Update the widget's dimensions and position according to the deltas computed above
        if (mLeftBorderActive || mRightBorderActive) {
            lp.cellHSpan += hSpanInc;
            lp.cellX += cellXInc;
        }

        if (mTopBorderActive || mBottomBorderActive) {
            lp.cellVSpan += vSpanInc;
            lp.cellY += cellYInc;
        }

        // Update the expandability array, as we have changed the widget's size.
        mCellLayout.getExpandabilityArrayForView(mWidgetView, mExpandability);

        // Update the cells occupied by this widget
        mCellLayout.markCellsAsOccupiedForView(mWidgetView);
        mWidgetView.requestLayout();
    }

    /**
     * This is the final step of the resize. Here we save the new widget size and position
     * to LauncherModel and animate the resize frame.
     */
    public void commitResizeForDelta(int deltaX, int deltaY) {
        visualizeResizeForDelta(deltaX, deltaY);

        CellLayout.LayoutParams lp = (CellLayout.LayoutParams) mWidgetView.getLayoutParams();
        LauncherModel.resizeItemInDatabase(getContext(), mItemInfo, lp.cellX, lp.cellY,
                lp.cellHSpan, lp.cellVSpan);
        mWidgetView.requestLayout();

        // Once our widget resizes (hence the post), we want to snap the resize frame to it
        post(new Runnable() {
            public void run() {
                snapToWidget(true);
            }
        });
    }

    public void snapToWidget(boolean animate) {
        final DragLayer.LayoutParams lp = (DragLayer.LayoutParams) getLayoutParams();
        int xOffset = mCellLayout.getLeft() + mCellLayout.getPaddingLeft() - mWorkspace.getScrollX();
        int yOffset = mCellLayout.getTop() + mCellLayout.getPaddingTop() - mWorkspace.getScrollY();

        int newWidth = mWidgetView.getWidth() + 2 * mBackgroundPadding - mWidgetPaddingLeft -
                mWidgetPaddingRight;
        int newHeight = mWidgetView.getHeight() + 2 * mBackgroundPadding - mWidgetPaddingTop -
                mWidgetPaddingBottom;

        int newX = mWidgetView.getLeft() - mBackgroundPadding + xOffset + mWidgetPaddingLeft;
        int newY = mWidgetView.getTop() - mBackgroundPadding + yOffset + mWidgetPaddingTop;

        // We need to make sure the frame stays within the bounds of the CellLayout
        if (newY < 0) {
            newHeight -= -newY;
            newY = 0;
        }
        if (newY + newHeight > mDragLayer.getHeight()) {
            newHeight -= newY + newHeight - mDragLayer.getHeight();
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
            ObjectAnimator oa = ObjectAnimator.ofPropertyValuesHolder(lp, width, height, x, y);
            ObjectAnimator leftOa = ObjectAnimator.ofFloat(mLeftHandle, "alpha", 1.0f);
            ObjectAnimator rightOa = ObjectAnimator.ofFloat(mRightHandle, "alpha", 1.0f);
            ObjectAnimator topOa = ObjectAnimator.ofFloat(mTopHandle, "alpha", 1.0f);
            ObjectAnimator bottomOa = ObjectAnimator.ofFloat(mBottomHandle, "alpha", 1.0f);
            oa.addUpdateListener(new AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator animation) {
                    requestLayout();
                }
            });
            AnimatorSet set = new AnimatorSet();
            if (mResizeMode == AppWidgetProviderInfo.RESIZE_VERTICAL) {
                set.playTogether(oa, topOa, bottomOa);
            } else if (mResizeMode == AppWidgetProviderInfo.RESIZE_HORIZONTAL) {
                set.playTogether(oa, leftOa, rightOa);
            } else {
                set.playTogether(oa, leftOa, rightOa, topOa, bottomOa);
            }

            set.setDuration(SNAP_DURATION);
            set.start();
        }
    }
}
