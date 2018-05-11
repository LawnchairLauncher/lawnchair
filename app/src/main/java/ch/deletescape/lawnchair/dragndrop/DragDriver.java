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

package ch.deletescape.lawnchair.dragndrop;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.view.DragEvent;
import android.view.MotionEvent;

import java.util.ArrayList;

import ch.deletescape.lawnchair.DropTarget;
import ch.deletescape.lawnchair.DropTarget.DragObject;
import ch.deletescape.lawnchair.InstallShortcutReceiver;
import ch.deletescape.lawnchair.ShortcutInfo;
import ch.deletescape.lawnchair.Utilities;

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

    public abstract boolean onDragEvent(DragEvent event);


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

    public static DragDriver create(Context context, DragController dragController,
                                    DragObject dragObject, DragOptions options) {
        if (Utilities.ATLEAST_NOUGAT && options.systemDndStartPoint != null) {
            return new SystemDragDriver(dragController, context, dragObject);
        } else {
            return new InternalDragDriver(dragController);
        }
    }
}

/**
 * Class for driving a system (i.e. framework) drag/drop operation.
 */
class SystemDragDriver extends DragDriver {

    private final DragObject mDragObject;
    private final Context mContext;

    boolean mReceivedDropEvent;
    float mLastX;
    float mLastY;

    public SystemDragDriver(DragController dragController, Context context, DragObject dragObject) {
        super(dragController);
        mDragObject = dragObject;
        mContext = context;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return false;
    }

    @Override
    public boolean onDragEvent(DragEvent event) {
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
                mReceivedDropEvent =
                        updateInfoFromClipData(event.getClipData(), event.getClipDescription());
                return mReceivedDropEvent;

            case DragEvent.ACTION_DRAG_EXITED:
                mEventListener.onDriverDragExitWindow();
                return true;

            case DragEvent.ACTION_DRAG_ENDED:
                if (mReceivedDropEvent) {
                    mEventListener.onDriverDragEnd(mLastX, mLastY, null);
                } else {
                    mEventListener.onDriverDragCancel();
                }
                return true;

            default:
                return false;
        }
    }

    private boolean updateInfoFromClipData(ClipData data, ClipDescription desc) {
        if (data == null) {
            return false;
        }
        ArrayList<Intent> intents = new ArrayList<>();
        int itemCount = data.getItemCount();
        for (int i = 0; i < itemCount; i++) {
            Intent intent = data.getItemAt(i).getIntent();
            if (intent == null) {
                continue;
            }

            // Give preference to shortcut intents.
            if (!Intent.ACTION_CREATE_SHORTCUT.equals(intent.getAction())) {
                intents.add(intent);
                continue;
            }
            ShortcutInfo info = InstallShortcutReceiver.fromShortcutIntent(mContext, intent);
            if (info != null) {
                mDragObject.dragInfo = info;
                return true;
            }
            return true;
        }

        // Try creating shortcuts just using the intent and label
        Intent fullIntent = new Intent().putExtra(Intent.EXTRA_SHORTCUT_NAME, desc.getLabel());
        for (Intent intent : intents) {
            fullIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, intent);
            ShortcutInfo info = InstallShortcutReceiver.fromShortcutIntent(mContext, fullIntent);
            if (info != null) {
                mDragObject.dragInfo = info;
                return true;
            }
        }

        return false;
    }
}

/**
 * Class for driving an internal (i.e. not using framework) drag/drop operation.
 */
class InternalDragDriver extends DragDriver {
    public InternalDragDriver(DragController dragController) {
        super(dragController);
    }

    @Override
    public boolean onDragEvent(DragEvent event) {
        return false;
    }
}
