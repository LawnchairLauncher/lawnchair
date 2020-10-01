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

package com.android.launcher3.dragndrop;

import android.os.SystemClock;
import android.view.DragEvent;
import android.view.MotionEvent;

import java.util.function.Consumer;

/**
 * Base class for driving a drag/drop operation.
 */
public abstract class DragDriver {

    protected final EventListener mEventListener;
    protected final Consumer<MotionEvent> mSecondaryEventConsumer;

    public interface EventListener {
        void onDriverDragMove(float x, float y);
        void onDriverDragExitWindow();
        void onDriverDragEnd(float x, float y);
        void onDriverDragCancel();
    }

    public DragDriver(EventListener eventListener, Consumer<MotionEvent> sec) {
        mEventListener = eventListener;
        mSecondaryEventConsumer = sec;
    }

    /**
     * Called to handle system touch event
     */
    public boolean onTouchEvent(MotionEvent ev) {
        return false;
    }

    /**
     * Called to handle system touch intercept event
     */
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return false;
    }

    /**
     * Called to handle system drag event
     */
    public boolean onDragEvent(DragEvent event) {
        return false;
    }

    /**
     * Created a driver for handing the actual events
     */
    public static DragDriver create(DragController dragController, DragOptions options,
            Consumer<MotionEvent> sec) {
        if (options.simulatedDndStartPoint != null) {
            if  (options.isAccessibleDrag) {
                return null;
            }
            return new SystemDragDriver(dragController, sec);
        } else {
            return new InternalDragDriver(dragController, sec);
        }
    }

    /**
     * Class for driving a system (i.e. framework) drag/drop operation.
     */
    static class SystemDragDriver extends DragDriver {

        private final long mDragStartTime;
        float mLastX = 0;
        float mLastY = 0;

        SystemDragDriver(DragController dragController, Consumer<MotionEvent> sec) {
            super(dragController, sec);
            mDragStartTime = SystemClock.uptimeMillis();
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            return false;
        }

        /**
         * It creates a temporary {@link MotionEvent} object for secondary consumer
         */
        private void simulateSecondaryMotionEvent(DragEvent event) {
            final int motionAction;
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    motionAction = MotionEvent.ACTION_DOWN;
                    break;
                case DragEvent.ACTION_DRAG_LOCATION:
                    motionAction = MotionEvent.ACTION_MOVE;
                    break;
                case DragEvent.ACTION_DRAG_ENDED:
                    motionAction = MotionEvent.ACTION_UP;
                    break;
                default:
                    return;
            }
            MotionEvent emulatedEvent = MotionEvent.obtain(mDragStartTime,
                    SystemClock.uptimeMillis(), motionAction, event.getX(), event.getY(), 0);
            mSecondaryEventConsumer.accept(emulatedEvent);
            emulatedEvent.recycle();
        }

        @Override
        public boolean onDragEvent(DragEvent event) {
            simulateSecondaryMotionEvent(event);
            final int action = event.getAction();

            switch (action) {
                case DragEvent.ACTION_DRAG_STARTED:
                    mLastX = event.getX();
                    mLastY = event.getY();
                    return true;

                case DragEvent.ACTION_DRAG_ENTERED:
                    return true;

                case DragEvent.ACTION_DRAG_LOCATION:
                    mLastX = event.getX();
                    mLastY = event.getY();
                    mEventListener.onDriverDragMove(event.getX(), event.getY());
                    return true;

                case DragEvent.ACTION_DROP:
                    mLastX = event.getX();
                    mLastY = event.getY();
                    mEventListener.onDriverDragMove(event.getX(), event.getY());
                    mEventListener.onDriverDragEnd(mLastX, mLastY);
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    mEventListener.onDriverDragExitWindow();
                    return true;

                case DragEvent.ACTION_DRAG_ENDED:
                    mEventListener.onDriverDragCancel();
                    return true;

                default:
                    return false;
            }
        }
    }

    /**
     * Class for driving an internal (i.e. not using framework) drag/drop operation.
     */
    static class InternalDragDriver extends DragDriver {
        InternalDragDriver(DragController dragController, Consumer<MotionEvent> sec) {
            super(dragController, sec);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            mSecondaryEventConsumer.accept(ev);
            final int action = ev.getAction();

            switch (action) {
                case MotionEvent.ACTION_MOVE:
                    mEventListener.onDriverDragMove(ev.getX(), ev.getY());
                    break;
                case MotionEvent.ACTION_UP:
                    mEventListener.onDriverDragMove(ev.getX(), ev.getY());
                    mEventListener.onDriverDragEnd(ev.getX(), ev.getY());
                    break;
                case MotionEvent.ACTION_CANCEL:
                    mEventListener.onDriverDragCancel();
                    break;
            }

            return true;
        }


        public boolean onInterceptTouchEvent(MotionEvent ev) {
            mSecondaryEventConsumer.accept(ev);
            final int action = ev.getAction();

            switch (action) {
                case MotionEvent.ACTION_UP:
                    mEventListener.onDriverDragEnd(ev.getX(), ev.getY());
                    break;
                case MotionEvent.ACTION_CANCEL:
                    mEventListener.onDriverDragCancel();
                    break;
            }
            return true;
        }
    }
}


