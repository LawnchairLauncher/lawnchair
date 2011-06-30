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
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

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
    private TimeInterpolator mQuintEaseOutInterpolator = new DecelerateInterpolator(2.5f);
    private int[] mDropViewPos = new int[] { -1, -1 };
    private View mDropView = null;

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
        descendant.getHitRect(r);
        mTmpXY[0] = 0;
        mTmpXY[1] = 0;
        getDescendantCoordRelativeToSelf(descendant, mTmpXY);
        r.offset(mTmpXY[0], mTmpXY[1]);
    }

    public void getDescendantCoordRelativeToSelf(View descendant, int[] coord) {
        ViewParent viewParent = descendant.getParent();
        while (viewParent instanceof View && viewParent != this) {
            final View view = (View)viewParent;
            coord[0] += view.getLeft() + (int) (view.getTranslationX() + 0.5f) - view.getScrollX();
            coord[1] += view.getTop() + (int) (view.getTranslationY() + 0.5f) - view.getScrollY();
            viewParent = view.getParent();
        }
    }

    public void getViewLocationRelativeToSelf(View v, int[] location) {
        getLocationOnScreen(location);
        int x = location[0];
        int y = location[1];

        v.getLocationOnScreen(location);
        int vX = location[0];
        int vY = location[1];

        location[0] = vX - x;
        location[1] = vY - y;
    }

    @Override
    public boolean dispatchUnhandledMove(View focused, int direction) {
        return mDragController.dispatchUnhandledMove(focused, direction);
    }

    public View createDragView(Bitmap b, int xPos, int yPos) {
        ImageView imageView = new ImageView(mContext);
        imageView.setImageBitmap(b);
        imageView.setX(xPos);
        imageView.setY(yPos);
        addView(imageView, b.getWidth(), b.getHeight());

        return imageView;
    }

    public View createDragView(View v) {
        v.getLocationOnScreen(mTmpXY);
        return createDragView(mDragController.getViewBitmap(v), mTmpXY[0], mTmpXY[1]);
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

        int[] loc = new int[2];
        getViewLocationRelativeToSelf(dragView, loc);

        int coord[] = new int[2];
        coord[0] = lp.x;
        coord[1] = lp.y;
        getDescendantCoordRelativeToSelf(child, coord);

        final int fromX = loc[0] + (dragView.getWidth() - child.getMeasuredWidth())  / 2;
        final int fromY = loc[1] + (dragView.getHeight() - child.getMeasuredHeight())  / 2;
        final int dx = coord[0] - fromX;
        final int dy = coord[1] - fromY;

        child.setVisibility(INVISIBLE);
        animateViewIntoPosition(child, fromX, fromY, dx, dy);
    }

    private void animateViewIntoPosition(final View view, final int fromX, final int fromY,
            final int dX, final int dY) {

        // Calculate the duration of the animation based on the object's distance
        final float dist = (float) Math.sqrt(dX*dX + dY*dY);
        final Resources res = getResources();
        final float maxDist = (float) res.getInteger(R.integer.config_dropAnimMaxDist);
        int duration = res.getInteger(R.integer.config_dropAnimMaxDuration);
        if (dist < maxDist) {
            duration *= mQuintEaseOutInterpolator.getInterpolation(dist / maxDist);
        }

        if (mDropAnim != null) {
            mDropAnim.end();
        }
        mDropAnim = new ValueAnimator();
        mDropAnim.setInterpolator(mQuintEaseOutInterpolator);

        // The view is invisible during the animation; we render it manually.
        mDropAnim.addListener(new AnimatorListenerAdapter() {
            public void onAnimationStart(Animator animation) {
                // Set this here so that we don't render it until the animation begins
                mDropView = view;
            }

            public void onAnimationEnd(Animator animation) {
                if (mDropView != null) {
                    mDropView.setVisibility(View.VISIBLE);
                    mDropView = null;
                }
            }
        });

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

                mDropViewPos[0] = fromX + (int) (percent * dX + 0.5f);
                mDropViewPos[1] = fromY + (int) (percent * dY + 0.5f);
                invalidate(mDropViewPos[0], mDropViewPos[1],
                        mDropViewPos[0] + width, mDropViewPos[1] + height);
            }
        });
        mDropAnim.start();
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
            canvas.translate(xPos, yPos);
            mDropView.draw(canvas);
            canvas.restore();
        }
    }
}
