/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Paint;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuff;
import android.os.Vibrator;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

/**
 * A ViewGroup that coordinated dragging across its dscendants
 */
public class DragLayer extends FrameLayout implements DragController {
    private static final int SCROLL_DELAY = 600;
    private static final int SCROLL_ZONE = 20;
    private static final int VIBRATE_DURATION = 35;
    private static final int ANIMATION_SCALE_UP_DURATION = 110;

    private static final boolean PROFILE_DRAWING_DURING_DRAG = false;

    // Number of pixels to add to the dragged item for scaling
    private static final float DRAG_SCALE = 24.0f;

    private boolean mDragging = false;
    private boolean mShouldDrop;
    private float mLastMotionX;
    private float mLastMotionY;

    /**
     * The bitmap that is currently being dragged
     */
    private Bitmap mDragBitmap = null;
    private View mOriginator;

    private int mBitmapOffsetX;
    private int mBitmapOffsetY;

    /**
     * X offset from where we touched on the cell to its upper-left corner
     */
    private float mTouchOffsetX;

    /**
     * Y offset from where we touched on the cell to its upper-left corner
     */
    private float mTouchOffsetY;

    /**
     * Utility rectangle
     */
    private Rect mDragRect = new Rect();

    /**
     * Where the drag originated
     */
    private DragSource mDragSource;

    /**
     * The data associated with the object being dragged
     */
    private Object mDragInfo;

    private final Rect mRect = new Rect();
    private final int[] mDropCoordinates = new int[2];

    private final Vibrator mVibrator = new Vibrator();

    private DragListener mListener;

    private DragScroller mDragScroller;

    private static final int SCROLL_OUTSIDE_ZONE = 0;
    private static final int SCROLL_WAITING_IN_ZONE = 1;

    private static final int SCROLL_LEFT = 0;
    private static final int SCROLL_RIGHT = 1;

    private int mScrollState = SCROLL_OUTSIDE_ZONE;

    private ScrollRunnable mScrollRunnable = new ScrollRunnable();
    private View mIgnoredDropTarget;

    private RectF mDragRegion;
    private boolean mEnteredRegion;
    private DropTarget mLastDropTarget;

    private final Paint mTrashPaint = new Paint();
    private final Paint mEstimatedPaint = new Paint();
    private Paint mDragPaint;

    /**
     * If true, draw a "snag" showing where the object currently being dragged
     * would end up if dropped from current location.
     */
    private static final boolean DRAW_TARGET_SNAG = false;

    private Rect mEstimatedRect = new Rect();
    private float[] mDragCenter = new float[2];
    private float[] mEstimatedCenter = new float[2];
    private boolean mDrawEstimated = false;

    private int mTriggerWidth = -1;
    private int mTriggerHeight = -1;

    private static final int DISTANCE_DRAW_SNAG = 20;

    private static final int ANIMATION_STATE_STARTING = 1;
    private static final int ANIMATION_STATE_RUNNING = 2;
    private static final int ANIMATION_STATE_DONE = 3;

    private static final int ANIMATION_TYPE_SCALE = 1;

    private float mAnimationFrom;
    private float mAnimationTo;
    private int mAnimationDuration;
    private long mAnimationStartTime;
    private int mAnimationType;
    private int mAnimationState = ANIMATION_STATE_DONE;

    private InputMethodManager mInputMethodManager;

    /**
     * Used to create a new DragLayer from XML.
     *
     * @param context The application's context.
     * @param attrs The attribtues set containing the Workspace's customization values.
     */
    public DragLayer(Context context, AttributeSet attrs) {
        super(context, attrs);

        final int srcColor = context.getResources().getColor(R.color.delete_color_filter);
        mTrashPaint.setColorFilter(new PorterDuffColorFilter(srcColor, PorterDuff.Mode.SRC_ATOP));

        // Make estimated paint area in gray
        int snagColor = context.getResources().getColor(R.color.snag_callout_color);
        mEstimatedPaint.setColor(snagColor);
        mEstimatedPaint.setStrokeWidth(3);
        mEstimatedPaint.setAntiAlias(true);

    }

    public void startDrag(View v, DragSource source, Object dragInfo, int dragAction) {
        if (PROFILE_DRAWING_DURING_DRAG) {
            android.os.Debug.startMethodTracing("Launcher");
        }

        // Hide soft keyboard, if visible
        if (mInputMethodManager == null) {
            mInputMethodManager = (InputMethodManager)
                getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        }
        mInputMethodManager.hideSoftInputFromWindow(getWindowToken(), 0);

        if (mListener != null) {
            mListener.onDragStart(v, source, dragInfo, dragAction);
        }

        Rect r = mDragRect;
        r.set(v.getScrollX(), v.getScrollY(), 0, 0);

        offsetDescendantRectToMyCoords(v, r);
        mTouchOffsetX = mLastMotionX - r.left;
        mTouchOffsetY = mLastMotionY - r.top;

        v.clearFocus();
        v.setPressed(false);

        boolean willNotCache = v.willNotCacheDrawing();
        v.setWillNotCacheDrawing(false);

        // Reset the drawing cache background color to fully transparent
        // for the duration of this operation
        int color = v.getDrawingCacheBackgroundColor();
        v.setDrawingCacheBackgroundColor(0);

        if (color != 0) {
            v.destroyDrawingCache();
        }
        v.buildDrawingCache();
        Bitmap viewBitmap = v.getDrawingCache();
        int width = viewBitmap.getWidth();
        int height = viewBitmap.getHeight();

        mTriggerWidth = width * 2 / 3;
        mTriggerHeight = height * 2 / 3;

        Matrix scale = new Matrix();
        float scaleFactor = v.getWidth();
        scaleFactor = (scaleFactor + DRAG_SCALE) /scaleFactor;
        scale.setScale(scaleFactor, scaleFactor);

        mAnimationTo = 1.0f;
        mAnimationFrom = 1.0f / scaleFactor;
        mAnimationDuration = ANIMATION_SCALE_UP_DURATION;
        mAnimationState = ANIMATION_STATE_STARTING;
        mAnimationType = ANIMATION_TYPE_SCALE;

        mDragBitmap = Bitmap.createBitmap(viewBitmap, 0, 0, width, height, scale, true);
        v.destroyDrawingCache();
        v.setWillNotCacheDrawing(willNotCache);
        v.setDrawingCacheBackgroundColor(color);

        final Bitmap dragBitmap = mDragBitmap;
        mBitmapOffsetX = (dragBitmap.getWidth() - width) / 2;
        mBitmapOffsetY = (dragBitmap.getHeight() - height) / 2;

        if (dragAction == DRAG_ACTION_MOVE) {
            v.setVisibility(GONE);
        }

        mDragPaint = null;
        mDragging = true;
        mShouldDrop = true;
        mOriginator = v;
        mDragSource = source;
        mDragInfo = dragInfo;

        mVibrator.vibrate(VIBRATE_DURATION);

        mEnteredRegion = false;

        invalidate();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return mDragging || super.dispatchKeyEvent(event);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (mDragging && mDragBitmap != null) {
            if (mAnimationState == ANIMATION_STATE_STARTING) {
                mAnimationStartTime = SystemClock.uptimeMillis();
                mAnimationState = ANIMATION_STATE_RUNNING;
            }

            if (mAnimationState == ANIMATION_STATE_RUNNING) {
                float normalized = (float) (SystemClock.uptimeMillis() - mAnimationStartTime) /
                        mAnimationDuration;
                if (normalized >= 1.0f) {
                    mAnimationState = ANIMATION_STATE_DONE;
                }
                normalized = Math.min(normalized, 1.0f);
                final float value = mAnimationFrom  + (mAnimationTo - mAnimationFrom) * normalized;

                switch (mAnimationType) {
                    case ANIMATION_TYPE_SCALE:
                        final Bitmap dragBitmap = mDragBitmap;
                        canvas.save();
                        canvas.translate(mScrollX + mLastMotionX - mTouchOffsetX - mBitmapOffsetX,
                                mScrollY + mLastMotionY - mTouchOffsetY - mBitmapOffsetY);
                        canvas.translate((dragBitmap.getWidth() * (1.0f - value)) / 2,
                                (dragBitmap.getHeight() * (1.0f - value)) / 2);
                        canvas.scale(value, value);
                        canvas.drawBitmap(dragBitmap, 0.0f, 0.0f, mDragPaint);
                        canvas.restore();
                        break;
                }
            } else {
                // Only draw estimate drop "snag" when requested
                if (DRAW_TARGET_SNAG && mDrawEstimated) {
                    canvas.drawLine(mDragCenter[0], mDragCenter[1], mEstimatedCenter[0], mEstimatedCenter[1], mEstimatedPaint);
                    canvas.drawCircle(mEstimatedCenter[0], mEstimatedCenter[1], 8, mEstimatedPaint);
                }

                // Draw actual icon being dragged
                canvas.drawBitmap(mDragBitmap,
                        mScrollX + mLastMotionX - mTouchOffsetX - mBitmapOffsetX,
                        mScrollY + mLastMotionY - mTouchOffsetY - mBitmapOffsetY, mDragPaint);
            }
        }
    }

    private void endDrag() {
        if (mDragging) {
            mDragging = false;
            if (mDragBitmap != null) {
                mDragBitmap.recycle();
            }
            if (mOriginator != null) {
                mOriginator.setVisibility(VISIBLE);
            }
            if (mListener != null) {
                mListener.onDragEnd();
            }
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final int action = ev.getAction();

        final float x = ev.getX();
        final float y = ev.getY();

        switch (action) {
            case MotionEvent.ACTION_MOVE:
                break;

            case MotionEvent.ACTION_DOWN:
                // Remember location of down touch
                mLastMotionX = x;
                mLastMotionY = y;
                mLastDropTarget = null;
                break;

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (mShouldDrop && drop(x, y)) {
                    mShouldDrop = false;
                }
                endDrag();
                break;
        }

        return mDragging;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!mDragging) {
            return false;
        }

        final int action = ev.getAction();
        final float x = ev.getX();
        final float y = ev.getY();

        switch (action) {
        case MotionEvent.ACTION_DOWN:

            // Remember where the motion event started
            mLastMotionX = x;
            mLastMotionY = y;

            if ((x < SCROLL_ZONE) || (x > getWidth() - SCROLL_ZONE)) {
                mScrollState = SCROLL_WAITING_IN_ZONE;
                postDelayed(mScrollRunnable, SCROLL_DELAY);
            } else {
                mScrollState = SCROLL_OUTSIDE_ZONE;
            }

            break;
        case MotionEvent.ACTION_MOVE:
            final int scrollX = mScrollX;
            final int scrollY = mScrollY;

            final float touchX = mTouchOffsetX;
            final float touchY = mTouchOffsetY;

            final int offsetX = mBitmapOffsetX;
            final int offsetY = mBitmapOffsetY;

            int left = (int) (scrollX + mLastMotionX - touchX - offsetX);
            int top = (int) (scrollY + mLastMotionY - touchY - offsetY);

            final Bitmap dragBitmap = mDragBitmap;
            final int width = dragBitmap.getWidth();
            final int height = dragBitmap.getHeight();

            final Rect rect = mRect;
            rect.set(left - 1, top - 1, left + width + 1, top + height + 1);

            mLastMotionX = x;
            mLastMotionY = y;

            left = (int) (scrollX + x - touchX - offsetX);
            top = (int) (scrollY + y - touchY - offsetY);

            // Invalidate current icon position
            rect.union(left - 1, top - 1, left + width + 1, top + height + 1);

            mDragCenter[0] = rect.centerX();
            mDragCenter[1] = rect.centerY();

            // Invalidate any old estimated location
            if (DRAW_TARGET_SNAG && mDrawEstimated) {
                rect.union(mEstimatedRect);
            }

            final int[] coordinates = mDropCoordinates;
            DropTarget dropTarget = findDropTarget((int) x, (int) y, coordinates);
            if (dropTarget != null) {
                if (mLastDropTarget == dropTarget) {
                    dropTarget.onDragOver(mDragSource, coordinates[0], coordinates[1],
                        (int) mTouchOffsetX, (int) mTouchOffsetY, mDragInfo);
                } else {
                    if (mLastDropTarget != null) {
                        mLastDropTarget.onDragExit(mDragSource, coordinates[0], coordinates[1],
                            (int) mTouchOffsetX, (int) mTouchOffsetY, mDragInfo);
                    }
                    dropTarget.onDragEnter(mDragSource, coordinates[0], coordinates[1],
                        (int) mTouchOffsetX, (int) mTouchOffsetY, mDragInfo);
                }
            } else {
                if (mLastDropTarget != null) {
                    mLastDropTarget.onDragExit(mDragSource, coordinates[0], coordinates[1],
                        (int) mTouchOffsetX, (int) mTouchOffsetY, mDragInfo);
                }
            }

            // Render estimated drop "snag" only outside of width
            mDrawEstimated = false;
            if (DRAW_TARGET_SNAG && dropTarget != null) {
                Rect foundEstimate = dropTarget.estimateDropLocation(mDragSource,
                        (int) (scrollX + mLastMotionX), (int) (scrollY + mLastMotionY),
                        (int) mTouchOffsetX, (int) mTouchOffsetY, mDragInfo, mEstimatedRect);

                if (foundEstimate != null) {
                    mEstimatedCenter[0] = foundEstimate.centerX();
                    mEstimatedCenter[1] = foundEstimate.centerY();

                    int deltaX = (int) Math.abs(mEstimatedCenter[0] - mDragCenter[0]);
                    int deltaY = (int) Math.abs(mEstimatedCenter[1] - mDragCenter[1]);

                    if (deltaX > mTriggerWidth || deltaY > mTriggerHeight) {
                        mDrawEstimated = true;
                    }
                }
            }

            // Include new estimated area in invalidated rectangle
            if (DRAW_TARGET_SNAG && mDrawEstimated) {
                rect.union(mEstimatedRect);
            }
            invalidate(rect);

            mLastDropTarget = dropTarget;

            boolean inDragRegion = false;
            if (mDragRegion != null) {
                final RectF region = mDragRegion;
                final boolean inRegion = region.contains(ev.getRawX(), ev.getRawY());
                if (!mEnteredRegion && inRegion) {
                    mDragPaint = mTrashPaint;
                    mEnteredRegion = true;
                    inDragRegion = true;
                } else if (mEnteredRegion && !inRegion) {
                    mDragPaint = null;
                    mEnteredRegion = false;
                }
            }

            if (!inDragRegion && x < SCROLL_ZONE) {
                if (mScrollState == SCROLL_OUTSIDE_ZONE) {
                    mScrollState = SCROLL_WAITING_IN_ZONE;
                    mScrollRunnable.setDirection(SCROLL_LEFT);
                    postDelayed(mScrollRunnable, SCROLL_DELAY);
                }
            } else if (!inDragRegion && x > getWidth() - SCROLL_ZONE) {
                if (mScrollState == SCROLL_OUTSIDE_ZONE) {
                    mScrollState = SCROLL_WAITING_IN_ZONE;
                    mScrollRunnable.setDirection(SCROLL_RIGHT);
                    postDelayed(mScrollRunnable, SCROLL_DELAY);
                }
            } else {
                if (mScrollState == SCROLL_WAITING_IN_ZONE) {
                    mScrollState = SCROLL_OUTSIDE_ZONE;
                    mScrollRunnable.setDirection(SCROLL_RIGHT);
                    removeCallbacks(mScrollRunnable);
                }
            }

            break;
        case MotionEvent.ACTION_UP:
            removeCallbacks(mScrollRunnable);
            if (mShouldDrop) {
                drop(x, y);
                mShouldDrop = false;
            }
            endDrag();

            break;
        case MotionEvent.ACTION_CANCEL:
            endDrag();
        }

        return true;
    }

    private boolean drop(float x, float y) {
        invalidate();

        final int[] coordinates = mDropCoordinates;
        DropTarget dropTarget = findDropTarget((int) x, (int) y, coordinates);

        if (dropTarget != null) {
            dropTarget.onDragExit(mDragSource, coordinates[0], coordinates[1],
                    (int) mTouchOffsetX, (int) mTouchOffsetY, mDragInfo);
            if (dropTarget.acceptDrop(mDragSource, coordinates[0], coordinates[1],
                    (int) mTouchOffsetX, (int) mTouchOffsetY, mDragInfo)) {
                dropTarget.onDrop(mDragSource, coordinates[0], coordinates[1],
                        (int) mTouchOffsetX, (int) mTouchOffsetY, mDragInfo);
                mDragSource.onDropCompleted((View) dropTarget, true);
                return true;
            } else {
                mDragSource.onDropCompleted((View) dropTarget, false);
                return true;
            }
        }
        return false;
    }

    DropTarget findDropTarget(int x, int y, int[] dropCoordinates) {
        return findDropTarget(this, x, y, dropCoordinates);
    }

    private DropTarget findDropTarget(ViewGroup container, int x, int y, int[] dropCoordinates) {
        final Rect r = mDragRect;
        final int count = container.getChildCount();
        final int scrolledX = x + container.getScrollX();
        final int scrolledY = y + container.getScrollY();
        final View ignoredDropTarget = mIgnoredDropTarget;

        for (int i = count - 1; i >= 0; i--) {
            final View child = container.getChildAt(i);
            if (child.getVisibility() == VISIBLE && child != ignoredDropTarget) {
                child.getHitRect(r);
                if (r.contains(scrolledX, scrolledY)) {
                    DropTarget target = null;
                    if (child instanceof ViewGroup) {
                        x = scrolledX - child.getLeft();
                        y = scrolledY - child.getTop();
                        target = findDropTarget((ViewGroup) child, x, y, dropCoordinates);
                    }
                    if (target == null) {
                        if (child instanceof DropTarget) {
                            // Only consider this child if they will accept
                            DropTarget childTarget = (DropTarget) child;
                            if (childTarget.acceptDrop(mDragSource, x, y, 0, 0, mDragInfo)) {
                                dropCoordinates[0] = x;
                                dropCoordinates[1] = y;
                                return (DropTarget) child;
                            } else {
                                return null;
                            }
                        }
                    } else {
                        return target;
                    }
                }
            }
        }

        return null;
    }

    public void setDragScoller(DragScroller scroller) {
        mDragScroller = scroller;
    }

    public void setDragListener(DragListener l) {
        mListener = l;
    }

    public void removeDragListener(DragListener l) {
        mListener = null;
    }

    /**
     * Specifies the view that must be ignored when looking for a drop target.
     *
     * @param view The view that will not be taken into account while looking
     *        for a drop target.
     */
    void setIgnoredDropTarget(View view) {
        mIgnoredDropTarget = view;
    }

    /**
     * Specifies the delete region.
     *
     * @param region The rectangle in screen coordinates of the delete region.
     */
    void setDeleteRegion(RectF region) {
        mDragRegion = region;
    }

    private class ScrollRunnable implements Runnable {
        private int mDirection;

        ScrollRunnable() {
        }

        public void run() {
            if (mDragScroller != null) {
                mDrawEstimated = false;
                if (mDirection == SCROLL_LEFT) {
                    mDragScroller.scrollLeft();
                } else {
                    mDragScroller.scrollRight();
                }
                mScrollState = SCROLL_OUTSIDE_ZONE;
            }
        }

        void setDirection(int direction) {
            mDirection = direction;
        }
    }
}
