package com.android.launcher2;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.launcher.R;

public class AppWidgetResizeFrame extends FrameLayout {

    private ItemInfo mItemInfo;
    private LauncherAppWidgetHostView mWidgetView;
    private CellLayout mCellLayout;
    private ImageView mLeftHandle;
    private ImageView mRightHandle;
    private ImageView mTopHandle; 
    private ImageView mBottomHandle;

    private boolean mLeftBorderActive;
    private boolean mRightBorderActive;
    private boolean mTopBorderActive;
    private boolean mBottomBorderActive;

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

    private int mExpandability[] = new int[4];

    final int BORDER_WIDTH = 50;
    final int FRAME_MARGIN = 15;
    final int SNAP_DURATION = 150;

    public AppWidgetResizeFrame(Context context, ItemInfo itemInfo, 
            LauncherAppWidgetHostView widgetView, CellLayout cellLayout) {

        super(context);
        mContext = context;
        mItemInfo = itemInfo;
        mCellLayout = cellLayout;
        mWidgetView = widgetView;
        mResizeMode = widgetView.getAppWidgetInfo().resizableMode;
        
        final AppWidgetProviderInfo info = widgetView.getAppWidgetInfo();
        int[] result = mCellLayout.rectToCell(info.minWidth, info.minHeight, null);
        mMinHSpan = result[0];
        mMinVSpan = result[1];

        setBackgroundResource(R.drawable.resize_frame);
        setPadding(0, 0, 0, 0);

        LayoutParams lp;
        mLeftHandle = new ImageView(context);
        mLeftHandle.setImageResource(R.drawable.h_handle);
        lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 
                Gravity.LEFT | Gravity.CENTER_VERTICAL);
        addView(mLeftHandle, lp);

        mRightHandle = new ImageView(context);
        mRightHandle.setImageResource(R.drawable.h_handle);
        lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 
                Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        addView(mRightHandle, lp);

        mTopHandle = new ImageView(context);
        mTopHandle.setImageResource(R.drawable.v_handle);
        lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 
                Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        addView(mTopHandle, lp);

        mBottomHandle = new ImageView(context);
        mBottomHandle.setImageResource(R.drawable.v_handle);
        lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 
                Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        addView(mBottomHandle, lp);

        if (mResizeMode == AppWidgetProviderInfo.RESIZE_HORIZONTAL) {
            mTopHandle.setVisibility(GONE);
            mBottomHandle.setVisibility(GONE);
        } else if (mResizeMode == AppWidgetProviderInfo.RESIZE_VERTICAL) {
            mLeftHandle.setVisibility(GONE);
            mRightHandle.setVisibility(GONE);
        }        
    }

    public boolean beginResizeIfPointInRegion(int x, int y) {
        boolean horizontalActive = (mResizeMode & AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0;
        boolean verticalActive = (mResizeMode & AppWidgetProviderInfo.RESIZE_VERTICAL) != 0;
        mLeftBorderActive = (x < BORDER_WIDTH) && horizontalActive;
        mRightBorderActive = (x > getWidth() - BORDER_WIDTH) && horizontalActive;
        mTopBorderActive = (y < BORDER_WIDTH) && verticalActive;
        mBottomBorderActive = (y > getHeight() - BORDER_WIDTH) && verticalActive;

        boolean anyBordersActive = mLeftBorderActive || mRightBorderActive
                || mTopBorderActive || mBottomBorderActive;

        mBaselineWidth = getMeasuredWidth();
        mBaselineHeight = getMeasuredHeight();
        mBaselineX = getLeft();
        mBaselineY = getTop();
        mRunningHInc = 0;
        mRunningVInc = 0;

        if (anyBordersActive) {
            mLeftHandle.setAlpha(mLeftBorderActive ? 1.0f : 0.5f);
            mRightHandle.setAlpha(mRightBorderActive ? 1.0f : 0.5f);
            mTopHandle.setAlpha(mTopBorderActive ? 1.0f : 0.5f);
            mBottomHandle.setAlpha(mBottomBorderActive ? 1.0f : 0.5f);
        }
        mCellLayout.getExpandabilityArrayForView(mWidgetView, mExpandability);
        return anyBordersActive;
    }

    public void updateDeltas(int deltaX, int deltaY) {
        if (mLeftBorderActive) {
            mDeltaX = Math.max(-mBaselineX, deltaX); 
            mDeltaX = Math.min(mBaselineWidth - 2*BORDER_WIDTH, mDeltaX);
        } else if (mRightBorderActive) {
            mDeltaX = Math.min(mCellLayout.getWidth() - (mBaselineX + mBaselineWidth), deltaX);
            mDeltaX = Math.max(-mBaselineWidth + 2*BORDER_WIDTH, mDeltaX);
        }

        if (mTopBorderActive) {
            mDeltaY = Math.max(-mBaselineY, deltaY);
            mDeltaY = Math.min(mBaselineHeight - 2*BORDER_WIDTH, mDeltaY);
        } else if (mBottomBorderActive) {
            mDeltaY = Math.min(mCellLayout.getHeight() - (mBaselineY + mBaselineHeight), deltaY);
            mDeltaY = Math.max(-mBaselineHeight + 2*BORDER_WIDTH, mDeltaY);
        }
    }

    public void visualizeResizeForDelta(int deltaX, int deltaY) {
        updateDeltas(deltaX, deltaY);
        CellLayout.LayoutParams lp = (CellLayout.LayoutParams) getLayoutParams();
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

    private void resizeWidgetIfNeeded() {
        // TODO: these computations probably aren't quite right... think about them

        //System.out.println("runningIncX before: " + mRunningHInc);
        //System.out.println("runningIncY before: " + mRunningVInc);
        
        int xThreshold = mCellLayout.getCellWidth() + mCellLayout.getWidthGap();
        int yThreshold = mCellLayout.getCellHeight() + mCellLayout.getHeightGap();

        int hSpanInc = (int) Math.round(1.0f * mDeltaX / xThreshold) - mRunningHInc;
        int vSpanInc = (int) Math.round(1.0f * mDeltaY / yThreshold) - mRunningVInc;
        int cellXInc = 0;
        int cellYInc = 0;

        if (hSpanInc == 0 && vSpanInc == 0) return;

        // Before we change the widget, we clear the occupied cells associated with it.
        // The new set of occupied cells is marked below, once the layout params are updated.
        mCellLayout.markCellsAsUnoccupiedForView(mWidgetView);

        CellLayout.LayoutParams lp = (CellLayout.LayoutParams) mWidgetView.getLayoutParams();
        if (mLeftBorderActive) {
            cellXInc = Math.max(-mExpandability[0], hSpanInc);
            cellXInc = Math.min(lp.cellHSpan - mMinHSpan, cellXInc);
            hSpanInc *= -1;
            hSpanInc = Math.min(mExpandability[0], hSpanInc);
            hSpanInc = Math.max(-(lp.cellHSpan - mMinHSpan), hSpanInc);
            mRunningHInc -= hSpanInc;
        } else if (mRightBorderActive) {
            hSpanInc = Math.min(mExpandability[2], hSpanInc);
            hSpanInc = Math.max(-(lp.cellHSpan - mMinHSpan), hSpanInc);
            mRunningHInc += hSpanInc;
        }

        if (mTopBorderActive) {
            cellYInc = Math.max(-mExpandability[1], vSpanInc);
            cellYInc = Math.min(lp.cellVSpan - mMinVSpan, cellYInc);
            vSpanInc *= -1;
            vSpanInc = Math.min(mExpandability[1], vSpanInc);
            vSpanInc = Math.max(-(lp.cellVSpan - mMinVSpan), vSpanInc);
            mRunningVInc -= vSpanInc;
        } else if (mBottomBorderActive) {
            vSpanInc = Math.min(mExpandability[3], vSpanInc);
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

        try {
            mCellLayout.getExpandabilityArrayForView(mWidgetView, mExpandability);
        } catch (Exception e) {
            System.out.println("Problem!");
        }

        // Update the cells occupied by this widget
        mCellLayout.markCellsAsOccupiedForView(mWidgetView);
    }

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
        final CellLayout.LayoutParams lp = (CellLayout.LayoutParams) getLayoutParams();

        final int newWidth = mWidgetView.getWidth() + 2 * FRAME_MARGIN;
        final int newHeight = mWidgetView.getHeight() + 2 * FRAME_MARGIN;
        final int newX = mWidgetView.getLeft() - FRAME_MARGIN;
        final int newY = mWidgetView.getTop() - FRAME_MARGIN;
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
            PropertyValuesHolder height = PropertyValuesHolder.ofInt("height", lp.height, newHeight);
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
