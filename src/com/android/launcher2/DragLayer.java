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

package com.android.launcher2;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;

import com.android.launcher.R;

import java.util.ArrayList;

/**
 * A ViewGroup that coordinates dragging across its descendants
 */
public class DragLayer extends FrameLayout {
    private DragController mDragController;
    private int[] mTmpXY = new int[2];

    private int mXDown, mYDown;
    private Launcher mLauncher;

    // Variables relating to resizing widgets
    private final ArrayList<AppWidgetResizeFrame> mResizeFrames =
            new ArrayList<AppWidgetResizeFrame>();
    private AppWidgetResizeFrame mCurrentResizeFrame;

    // Variables relating to animation of views after drop
    private ValueAnimator mDropAnim = null;
    private ValueAnimator mFadeOutAnim = null;
    private TimeInterpolator mCubicEaseOutInterpolator = new DecelerateInterpolator(1.5f);
    private View mDropView = null;

    private int[] mDropViewPos = new int[2];
    private float mDropViewScale;
    private float mDropViewAlpha;

    /**
     * Used to create a new DragLayer from XML.
     *
     * @param context The application's context.
     * @param attrs The attributes set containing the Workspace's customization values.
     */
    public DragLayer(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Disable multitouch across the workspace/all apps/customize tray
        setMotionEventSplittingEnabled(false);
    }

    public void setup(Launcher launcher, DragController controller) {
        mLauncher = launcher;
        mDragController = controller;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return mDragController.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    private boolean handleTouchDown(MotionEvent ev, boolean intercept) {
        Rect hitRect = new Rect();
        int x = (int) ev.getX();
        int y = (int) ev.getY();

        for (AppWidgetResizeFrame child: mResizeFrames) {
            child.getHitRect(hitRect);
            if (hitRect.contains(x, y)) {
                if (child.beginResizeIfPointInRegion(x - child.getLeft(), y - child.getTop())) {
                    mCurrentResizeFrame = child;
                    mXDown = x;
                    mYDown = y;
                    requestDisallowInterceptTouchEvent(true);
                    return true;
                }
            }
        }

        Folder currentFolder = mLauncher.getWorkspace().getOpenFolder();
        if (currentFolder != null && intercept) {
            if (currentFolder.isEditingName()) {
                getDescendantRectRelativeToSelf(currentFolder.getEditTextRegion(), hitRect);
                if (!hitRect.contains(x, y)) {
                    currentFolder.dismissEditingName();
                    return true;
                }
            }

            getDescendantRectRelativeToSelf(currentFolder, hitRect);
            if (!hitRect.contains(x, y)) {
                mLauncher.closeFolder();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            if (handleTouchDown(ev, true)) {
                return true;
            }
        }
        clearAllResizeFrames();
        return mDragController.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean handled = false;
        int action = ev.getAction();

        int x = (int) ev.getX();
        int y = (int) ev.getY();

        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                if (handleTouchDown(ev, false)) {
                    return true;
                }
            }
        }

        if (mCurrentResizeFrame != null) {
            handled = true;
            switch (action) {
                case MotionEvent.ACTION_MOVE:
                    mCurrentResizeFrame.visualizeResizeForDelta(x - mXDown, y - mYDown);
                    break;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    mCurrentResizeFrame.commitResizeForDelta(x - mXDown, y - mYDown);
                    mCurrentResizeFrame = null;
            }
        }
        if (handled) return true;
        return mDragController.onTouchEvent(ev);
    }

    public void getDescendantRectRelativeToSelf(View descendant, Rect r) {
        mTmpXY[0] = 0;
        mTmpXY[1] = 0;
        getDescendantCoordRelativeToSelf(descendant, mTmpXY);
        r.set(mTmpXY[0], mTmpXY[1],
                mTmpXY[0] + descendant.getWidth(), mTmpXY[1] + descendant.getHeight());
    }

    private void getDescendantCoordRelativeToSelf(View descendant, int[] coord) {
        coord[0] += descendant.getLeft();
        coord[1] += descendant.getTop();
        ViewParent viewParent = descendant.getParent();
        while (viewParent instanceof View && viewParent != this) {
            final View view = (View)viewParent;
            coord[0] += view.getLeft() + (int) (view.getTranslationX() + 0.5f) - view.getScrollX();
            coord[1] += view.getTop() + (int) (view.getTranslationY() + 0.5f) - view.getScrollY();
            viewParent = view.getParent();
        }
    }

    public void getLocationInDragLayer(View child, int[] loc) {
        loc[0] = 0;
        loc[1] = 0;
        getDescendantCoordRelativeToSelf(child, loc);
    }

    public void getViewRectRelativeToSelf(View v, Rect r) {
        int[] loc = new int[2];
        getLocationInWindow(loc);
        int x = loc[0];
        int y = loc[1];

        v.getLocationInWindow(loc);
        int vX = loc[0];
        int vY = loc[1];

        int left = vX - x;
        int top = vY - y;
        r.set(left, top, left + v.getMeasuredWidth(), top + v.getMeasuredHeight());
    }

    @Override
    public boolean dispatchUnhandledMove(View focused, int direction) {
        return mDragController.dispatchUnhandledMove(focused, direction);
    }

    public static class LayoutParams extends FrameLayout.LayoutParams {
        public int x, y;
        public boolean customPosition = false;

        /**
         * {@inheritDoc}
         */
        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getWidth() {
            return width;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        public int getHeight() {
            return height;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getX() {
            return x;
        }

        public void setY(int y) {
            this.y = y;
        }

        public int getY() {
            return y;
        }
    }

    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            final FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) child.getLayoutParams();
            if (flp instanceof LayoutParams) {
                final LayoutParams lp = (LayoutParams) flp;
                if (lp.customPosition) {
                    child.layout(lp.x, lp.y, lp.x + lp.width, lp.y + lp.height);
                }
            }
        }
    }

    public void clearAllResizeFrames() {
        if (mResizeFrames.size() > 0) {
            for (AppWidgetResizeFrame frame: mResizeFrames) {
                removeView(frame);
            }
            mResizeFrames.clear();
        }
    }

    public boolean hasResizeFrames() {
        return mResizeFrames.size() > 0;
    }

    public boolean isWidgetBeingResized() {
        return mCurrentResizeFrame != null;
    }

    public void addResizeFrame(ItemInfo itemInfo, LauncherAppWidgetHostView widget,
            CellLayout cellLayout) {
        AppWidgetResizeFrame resizeFrame = new AppWidgetResizeFrame(getContext(),
                itemInfo, widget, cellLayout, this);

        LayoutParams lp = new LayoutParams(-1, -1);
        lp.customPosition = true;

        addView(resizeFrame, lp);
        mResizeFrames.add(resizeFrame);

        resizeFrame.snapToWidget(false);
    }

    public void animateViewIntoPosition(DragView dragView, final View child) {
        ((CellLayoutChildren) child.getParent()).measureChild(child);
        CellLayout.LayoutParams lp =  (CellLayout.LayoutParams) child.getLayoutParams();

        Rect r = new Rect();
        getViewRectRelativeToSelf(dragView, r);

        int coord[] = new int[2];
        coord[0] = lp.x;
        coord[1] = lp.y;
        // Since the child hasn't necessarily been laid out, we force the lp to be updated with
        // the correct coordinates and use these to determine the final location
        getDescendantCoordRelativeToSelf((View) child.getParent(), coord);
        int toX = coord[0] - (dragView.getWidth() - child.getMeasuredWidth()) / 2;
        int toY = coord[1] - (dragView.getHeight() - child.getMeasuredHeight()) / 2;

        final int fromX = r.left + (dragView.getWidth() - child.getMeasuredWidth())  / 2;
        final int fromY = r.top + (dragView.getHeight() - child.getMeasuredHeight())  / 2;
        child.setVisibility(INVISIBLE);
        child.setAlpha(0);
        Runnable onCompleteRunnable = new Runnable() {
            public void run() {
                child.setVisibility(VISIBLE);
                ObjectAnimator oa = ObjectAnimator.ofFloat(child, "alpha", 0f, 1f);
                oa.setDuration(60);
                oa.start();
            }
        };
        animateViewIntoPosition(dragView, fromX, fromY, toX, toY, onCompleteRunnable, true);
    }

    private void animateViewIntoPosition(final View view, final int fromX, final int fromY,
            final int toX, final int toY, Runnable onCompleteRunnable, boolean fadeOut) {
        Rect from = new Rect(fromX, fromY, fromX +
                view.getMeasuredWidth(), fromY + view.getMeasuredHeight());
        Rect to = new Rect(toX, toY, toX + view.getMeasuredWidth(), toY + view.getMeasuredHeight());
        animateView(view, from, to, 1f, 1.0f, -1, null, null, onCompleteRunnable, true);

    }

    public void animateView(final View view, final Rect from, final Rect to, final float finalAlpha,
            final float finalScale, int duration, final Interpolator motionInterpolator,
            final Interpolator alphaInterpolator, final Runnable onCompleteRunnable,
            final boolean fadeOut) {
        // Calculate the duration of the animation based on the object's distance
        final float dist = (float) Math.sqrt(Math.pow(to.left - from.left, 2) +
                Math.pow(to.top - from.top, 2));
        final Resources res = getResources();
        final float maxDist = (float) res.getInteger(R.integer.config_dropAnimMaxDist);

        // If duration < 0, this is a cue to compute the duration based on the distance
        if (duration < 0) {
            duration = res.getInteger(R.integer.config_dropAnimMaxDuration);
            if (dist < maxDist) {
                duration *= mCubicEaseOutInterpolator.getInterpolation(dist / maxDist);
            }
        }

        if (mDropAnim != null) {
            mDropAnim.cancel();
        }

        mDropView = view;
        final float initialAlpha = view.getAlpha();
        mDropAnim = new ValueAnimator();
        if (alphaInterpolator == null || motionInterpolator == null) {
            mDropAnim.setInterpolator(mCubicEaseOutInterpolator);
        }

        mDropAnim.setDuration(duration);
        mDropAnim.setFloatValues(0.0f, 1.0f);
        mDropAnim.removeAllUpdateListeners();
        mDropAnim.addUpdateListener(new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                final float percent = (Float) animation.getAnimatedValue();
                // Invalidate the old position
                int width = view.getMeasuredWidth();
                int height = view.getMeasuredHeight();
                invalidate(mDropViewPos[0], mDropViewPos[1],
                        mDropViewPos[0] + width, mDropViewPos[1] + height);

                float alphaPercent = alphaInterpolator == null ? percent :
                        alphaInterpolator.getInterpolation(percent);
                float motionPercent = motionInterpolator == null ? percent :
                        motionInterpolator.getInterpolation(percent);

                mDropViewPos[0] = from.left + (int) ((to.left - from.left) * motionPercent);
                mDropViewPos[1] = from.top + (int) ((to.top - from.top) * motionPercent);
                mDropViewScale = percent * finalScale + (1 - percent);
                mDropViewAlpha = alphaPercent * finalAlpha + (1 - alphaPercent) * initialAlpha;
                invalidate(mDropViewPos[0], mDropViewPos[1],
                        mDropViewPos[0] + width, mDropViewPos[1] + height);
            }
        });
        mDropAnim.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator animation) {
                if (onCompleteRunnable != null) {
                    onCompleteRunnable.run();
                }
                if (fadeOut) {
                    fadeOutDragView();
                } else {
                    mDropView = null;
                }
            }
        });
        mDropAnim.start();
    }

    private void fadeOutDragView() {
        mFadeOutAnim = new ValueAnimator();
        mFadeOutAnim.setDuration(150);
        mFadeOutAnim.setFloatValues(0f, 1f);
        mFadeOutAnim.removeAllUpdateListeners();
        mFadeOutAnim.addUpdateListener(new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                final float percent = (Float) animation.getAnimatedValue();
                mDropViewAlpha = 1 - percent;
                int width = mDropView.getMeasuredWidth();
                int height = mDropView.getMeasuredHeight();
                invalidate(mDropViewPos[0], mDropViewPos[1],
                        mDropViewPos[0] + width, mDropViewPos[1] + height);
            }
        });
        mFadeOutAnim.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator animation) {
                mDropView = null;
            }
        });
        mFadeOutAnim.start();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mDropView != null) {
            // We are animating an item that was just dropped on the home screen.
            // Render its View in the current animation position.
            canvas.save(Canvas.MATRIX_SAVE_FLAG);
            final int xPos = mDropViewPos[0] - mDropView.getScrollX();
            final int yPos = mDropViewPos[1] - mDropView.getScrollY();
            int width = mDropView.getMeasuredWidth();
            int height = mDropView.getMeasuredHeight();
            canvas.translate(xPos, yPos);
            canvas.translate((1 - mDropViewScale) * width / 2, (1 - mDropViewScale) * height / 2);
            canvas.scale(mDropViewScale, mDropViewScale);
            mDropView.setAlpha(mDropViewAlpha);
            mDropView.draw(canvas);
            canvas.restore();
        }
    }
}
