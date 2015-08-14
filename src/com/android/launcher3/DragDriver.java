/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.launcher3;

import android.content.ClipData;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Point;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;

/**
 * Base class for driving a drag/drop operation.
 */
public abstract class DragDriver {
    protected final EventListener mEventListener;

    public interface EventListener {
        void onDriverDragMove(float x, float y);
        void onDriverDragExitWindow();
        void onDriverDragEnd(float x, float y, DropTarget dropTargetOverride);
        void onDriverDragCancel();
    }

    public DragDriver(EventListener eventListener) {
        mEventListener = eventListener;
    }

    /**
     * Handles ending of the DragView animation.
     */
    public abstract void onDragViewAnimationEnd();

    public boolean onTouchEvent(MotionEvent ev) {
        final int action = ev.getAction();

        switch (action) {
            case MotionEvent.ACTION_MOVE:
                mEventListener.onDriverDragMove(ev.getX(), ev.getY());
                break;
            case MotionEvent.ACTION_UP:
                mEventListener.onDriverDragMove(ev.getX(), ev.getY());
                mEventListener.onDriverDragEnd(ev.getX(), ev.getY(), null);
                break;
            case MotionEvent.ACTION_CANCEL:
                mEventListener.onDriverDragCancel();
                break;
        }

        return true;
    }

    public abstract boolean onDragEvent (DragEvent event);


    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final int action = ev.getAction();

        switch (action) {
            case MotionEvent.ACTION_UP:
                mEventListener.onDriverDragEnd(ev.getX(), ev.getY(), null);
                break;
            case MotionEvent.ACTION_CANCEL:
                mEventListener.onDriverDragCancel();
                break;
        }

        return true;
    }

    public static DragDriver create(
            DragController dragController, ItemInfo dragInfo, DragView dragView) {
        if (Utilities.isNycOrAbove()) {
            return new SystemDragDriver(dragController, dragInfo.getIntent(), dragView);
        } else {
            return new InternalDragDriver(dragController);
        }
    }

};

/**
 * Class for driving a system (i.e. framework) drag/drop operation.
 */
class SystemDragDriver extends DragDriver {
    /** Intent associated with the drag operation, or null is there no associated intent.  */
    private final Intent mDragIntent;

    private final DragView mDragView;
    boolean mIsFrameworkDragActive = false;
    boolean mReceivedDropEvent = false;
    float mLastX = 0;
    float mLastY = 0;

    public SystemDragDriver(DragController dragController, Intent dragIntent, DragView dragView) {
        super(dragController);
        mDragIntent = dragIntent;
        mDragView = dragView;
    }

    private static class ShadowBuilder extends View.DragShadowBuilder {
        final DragView mDragView;

        public ShadowBuilder(DragView dragView) {
            mDragView = dragView;
        }

        @Override
        public void onProvideShadowMetrics (Point size, Point touch) {
            mDragView.provideDragShadowMetrics(size, touch);
        }

        @Override
        public void onDrawShadow(Canvas canvas) {
            mDragView.drawDragShadow(canvas);
        }
    };

    @Override
    public void onDragViewAnimationEnd() {
        // Clip data for the drag operation. If there is an intent, create an intent-based ClipData,
        // which will be passed to a global DND.
        // If there is no intent, craft a fake ClipData and start a local DND operation; this
        // ClipData will be ignored.
        final ClipData dragData = mDragIntent != null ?
                ClipData.newIntent("", mDragIntent) :
                ClipData.newPlainText("", "");

        View.DragShadowBuilder shadowBuilder = new ShadowBuilder(mDragView);
        // TODO: DND flags are in flux, once settled, use the appropriate constant.
        final int flagGlobal = 1 << 0;
        final int flagOpaque = 1 << 9;
        final int flags = (mDragIntent != null ? flagGlobal : 0) | flagOpaque;

        mIsFrameworkDragActive = true;

        if (!mDragView.startDrag(dragData, shadowBuilder, null, flags)) {
            mIsFrameworkDragActive = false;
            mEventListener.onDriverDragCancel();
            return;
        }

        // Starting from this point, the driver takes over showing the drag shadow, so hiding the
        // drag view.
        mDragView.setVisibility(View.INVISIBLE);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return !mIsFrameworkDragActive && super.onTouchEvent(ev);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return !mIsFrameworkDragActive && super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onDragEvent (DragEvent event) {
        if (!mIsFrameworkDragActive) {
            // We are interested only in drag events started by this driver.
            return false;
        }

        final int action = event.getAction();

        switch (action) {
            case DragEvent.ACTION_DRAG_STARTED:
                mLastX = event.getX();
                mLastY = event.getY();
                return true;

            case DragEvent.ACTION_DRAG_ENTERED:
                mLastX = event.getX();
                mLastY = event.getY();
                return true;

            case DragEvent.ACTION_DRAG_LOCATION:
                mLastX = event.getX();
                mLastY = event.getY();
                mEventListener.onDriverDragMove(event.getX(), event.getY());
                return true;

            case DragEvent.ACTION_DROP:
                mLastX = event.getX();
                mLastY = event.getY();
                mReceivedDropEvent = true;
                return true;

            case DragEvent.ACTION_DRAG_EXITED:
                mLastX = event.getX();
                mLastY = event.getY();
                mEventListener.onDriverDragExitWindow();
                return true;

            case DragEvent.ACTION_DRAG_ENDED:
                final boolean dragAccepted = event.getResult();
                final boolean acceptedByAnotherWindow = dragAccepted && !mReceivedDropEvent;

                // When the system drag ends, its drag shadow disappears. Resume showing the drag
                // view for the possible final animation.
                mDragView.setVisibility(View.VISIBLE);

                final DropTarget dropTargetOverride = acceptedByAnotherWindow ?
                        new AnotherWindowDropTarget(mDragView.getContext()) : null;

                mEventListener.onDriverDragEnd(mLastX, mLastY, dropTargetOverride);
                mIsFrameworkDragActive = false;
                return true;

            default:
                return false;
        }
    }
};

/**
 * Class for driving an internal (i.e. not using framework) drag/drop operation.
 */
class InternalDragDriver extends DragDriver {
    public InternalDragDriver(DragController dragController) {
        super(dragController);
    }

    @Override
    public void onDragViewAnimationEnd() {}

    @Override
    public boolean onDragEvent (DragEvent event) { return false; }
};
