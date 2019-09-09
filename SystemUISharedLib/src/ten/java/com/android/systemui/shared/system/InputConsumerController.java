/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.systemui.shared.system;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.INPUT_CONSUMER_PIP;
import static android.view.WindowManager.INPUT_CONSUMER_RECENTS_ANIMATION;

import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.view.BatchedInputEventReceiver;
import android.view.Choreographer;
import android.view.IWindowManager;
import android.view.InputChannel;
import android.view.InputEvent;
import android.view.WindowManagerGlobal;

import java.io.PrintWriter;

/**
 * Manages the input consumer that allows the SystemUI to directly receive input.
 */
public class InputConsumerController {

    private static final String TAG = InputConsumerController.class.getSimpleName();

    /**
     * Listener interface for callers to subscribe to input events.
     */
    public interface InputListener {
        /** Handles any input event. */
        boolean onInputEvent(InputEvent ev);
    }

    /**
     * Listener interface for callers to learn when this class is registered or unregistered with
     * window manager
     */
    public interface RegistrationListener {
        void onRegistrationChanged(boolean isRegistered);
    }

    /**
     * Input handler used for the input consumer. Input events are batched and consumed with the
     * SurfaceFlinger vsync.
     */
    private final class InputEventReceiver extends BatchedInputEventReceiver {

        public InputEventReceiver(InputChannel inputChannel, Looper looper) {
            super(inputChannel, looper, Choreographer.getSfInstance());
        }

        @Override
        public void onInputEvent(InputEvent event) {
            boolean handled = true;
            try {
                if (mListener != null) {
                    handled = mListener.onInputEvent(event);
                }
            } finally {
                finishInputEvent(event, handled);
            }
        }
    }

    private final IWindowManager mWindowManager;
    private final IBinder mToken;
    private final String mName;

    private InputEventReceiver mInputEventReceiver;
    private InputListener mListener;
    private RegistrationListener mRegistrationListener;

    /**
     * @param name the name corresponding to the input consumer that is defined in the system.
     */
    public InputConsumerController(IWindowManager windowManager, String name) {
        mWindowManager = windowManager;
        mToken = new Binder();
        mName = name;
    }

    /**
     * @return A controller for the pip input consumer.
     */
    public static InputConsumerController getPipInputConsumer() {
        return new InputConsumerController(WindowManagerGlobal.getWindowManagerService(),
                INPUT_CONSUMER_PIP);
    }

    /**
     * @return A controller for the recents animation input consumer.
     */
    public static InputConsumerController getRecentsAnimationInputConsumer() {
        return new InputConsumerController(WindowManagerGlobal.getWindowManagerService(),
                INPUT_CONSUMER_RECENTS_ANIMATION);
    }

    /**
     * Sets the input listener.
     */
    public void setInputListener(InputListener listener) {
        mListener = listener;
    }

    /**
     * Sets the registration listener.
     */
    public void setRegistrationListener(RegistrationListener listener) {
        mRegistrationListener = listener;
        if (mRegistrationListener != null) {
            mRegistrationListener.onRegistrationChanged(mInputEventReceiver != null);
        }
    }

    /**
     * Check if the InputConsumer is currently registered with WindowManager
     *
     * @return {@code true} if registered, {@code false} if not.
     */
    public boolean isRegistered() {
        return mInputEventReceiver != null;
    }

    /**
     * Registers the input consumer.
     */
    public void registerInputConsumer() {
        if (mInputEventReceiver == null) {
            final InputChannel inputChannel = new InputChannel();
            try {
                // TODO(b/113087003): Support Picture-in-picture in multi-display.
                mWindowManager.destroyInputConsumer(mName, DEFAULT_DISPLAY);
                mWindowManager.createInputConsumer(mToken, mName, DEFAULT_DISPLAY, inputChannel);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to create input consumer", e);
            }
            mInputEventReceiver = new InputEventReceiver(inputChannel, Looper.myLooper());
            if (mRegistrationListener != null) {
                mRegistrationListener.onRegistrationChanged(true /* isRegistered */);
            }
        }
    }

    /**
     * Unregisters the input consumer.
     */
    public void unregisterInputConsumer() {
        if (mInputEventReceiver != null) {
            try {
                // TODO(b/113087003): Support Picture-in-picture in multi-display.
                mWindowManager.destroyInputConsumer(mName, DEFAULT_DISPLAY);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to destroy input consumer", e);
            }
            mInputEventReceiver.dispose();
            mInputEventReceiver = null;
            if (mRegistrationListener != null) {
                mRegistrationListener.onRegistrationChanged(false /* isRegistered */);
            }
        }
    }

    public void dump(PrintWriter pw, String prefix) {
        final String innerPrefix = prefix + "  ";
        pw.println(prefix + TAG);
        pw.println(innerPrefix + "registered=" + (mInputEventReceiver != null));
    }
}
