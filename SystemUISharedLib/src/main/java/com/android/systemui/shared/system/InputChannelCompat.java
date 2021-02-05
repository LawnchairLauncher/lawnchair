/**
 * Copyright (C) 2019 The Android Open Source Project
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

import android.os.Bundle;
import android.os.Looper;
import android.view.BatchedInputEventReceiver;
import android.view.Choreographer;
import android.view.InputChannel;
import android.view.InputEvent;
import android.view.InputEventSender;
import android.view.MotionEvent;

/**
 * @see android.view.InputChannel
 */
public class InputChannelCompat {

    /**
     * Callback for receiving event callbacks
     */
    public interface InputEventListener {
        /**
         * @param ev event to be handled
         */
        void onInputEvent(InputEvent ev);
    }

    /**
     * Creates a dispatcher from the extras received as part on onInitialize
     */
    public static InputEventReceiver fromBundle(Bundle params, String key,
            Looper looper, Choreographer choreographer, InputEventListener listener) {

        InputChannel channel = params.getParcelable(key);
        return new InputEventReceiver(channel, looper, choreographer, listener);
    }

    /**
     * Version of addBatch method which preserves time accuracy in nanoseconds instead of
     * converting the time to milliseconds.
     * @param src old MotionEvent where the target should be appended
     * @param target new MotionEvent which should be added to the src
     * @return true if the merge was successful
     *
     * @see MotionEvent#addBatch(MotionEvent)
     */
    public static boolean mergeMotionEvent(MotionEvent src, MotionEvent target) {
        return target.addBatch(src);
    }

    /**
     * @see BatchedInputEventReceiver
     */
    public static class InputEventReceiver {

        private final BatchedInputEventReceiver mReceiver;
        private final InputChannel mInputChannel;

        public InputEventReceiver(InputChannel inputChannel, Looper looper,
                Choreographer choreographer, final InputEventListener listener) {
            mInputChannel = inputChannel;
            mReceiver = new BatchedInputEventReceiver(inputChannel, looper, choreographer) {

                @Override
                public void onInputEvent(InputEvent event) {
                    listener.onInputEvent(event);
                    finishInputEvent(event, true /* handled */);
                }
            };
        }

        /**
         * @see BatchedInputEventReceiver#dispose()
         */
        public void dispose() {
            mReceiver.dispose();
            mInputChannel.dispose();
        }
    }

    /**
     * @see InputEventSender
     */
    public static class InputEventDispatcher {

        private final InputChannel mInputChannel;
        private final InputEventSender mSender;

        public InputEventDispatcher(InputChannel inputChannel, Looper looper) {
            mInputChannel = inputChannel;
            mSender = new InputEventSender(inputChannel, looper) { };
        }

        /**
         * @see InputEventSender#sendInputEvent(int, InputEvent)
         */
        public void dispatch(InputEvent ev) {
            mSender.sendInputEvent(ev.getSequenceNumber(), ev);
        }

        /**
         * @see InputEventSender#dispose()
         */
        public void dispose() {
            mSender.dispose();
            mInputChannel.dispose();
        }
    }
}
