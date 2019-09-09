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
package com.android.systemui.shared.recents.view;

import android.app.ActivityOptions;
import android.app.ActivityOptions.OnAnimationStartedListener;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.GraphicBuffer;
import android.graphics.Picture;
import android.os.Bundle;
import android.os.Handler;
import android.os.IRemoteCallback;
import android.os.RemoteException;
import android.view.View;

import java.util.function.Consumer;

/**
 * A helper class to create transitions to/from an App to Recents.
 */
public class RecentsTransition {

    /**
     * Creates a new transition aspect scaled transition activity options.
     */
    public static ActivityOptions createAspectScaleAnimation(Context context, Handler handler,
            boolean scaleUp, AppTransitionAnimationSpecsFuture animationSpecsFuture,
            final Runnable animationStartCallback) {
        final OnAnimationStartedListener animStartedListener = new OnAnimationStartedListener() {
            private boolean mHandled;

            @Override
            public void onAnimationStarted() {
                // OnAnimationStartedListener can be called numerous times, so debounce here to
                // prevent multiple callbacks
                if (mHandled) {
                    return;
                }
                mHandled = true;

                if (animationStartCallback != null) {
                    animationStartCallback.run();
                }
            }
        };
        final ActivityOptions opts = ActivityOptions.makeMultiThumbFutureAspectScaleAnimation(
                context, handler,
                animationSpecsFuture != null ? animationSpecsFuture.getFuture() : null,
                animStartedListener, scaleUp);
        return opts;
    }

    /**
     * Wraps a animation-start callback in a binder that can be called from window manager.
     */
    public static IRemoteCallback wrapStartedListener(final Handler handler,
            final Runnable animationStartCallback) {
        if (animationStartCallback == null) {
            return null;
        }
        return new IRemoteCallback.Stub() {
            @Override
            public void sendResult(Bundle data) throws RemoteException {
                handler.post(animationStartCallback);
            }
        };
    }

    /**
     * @return a {@link GraphicBuffer} with the {@param view} drawn into it. Result can be null if
     *         we were unable to allocate a hardware bitmap.
     */
    public static Bitmap drawViewIntoHardwareBitmap(int width, int height, final View view,
            final float scale, final int eraseColor) {
        return createHardwareBitmap(width, height, new Consumer<Canvas>() {
            @Override
            public void accept(Canvas c) {
                c.scale(scale, scale);
                if (eraseColor != 0) {
                    c.drawColor(eraseColor);
                }
                if (view != null) {
                    view.draw(c);
                }
            }
        });
    }

    /**
     * @return a hardware {@link Bitmap} after being drawn with the {@param consumer}. Result can be
     *         null if we were unable to allocate a hardware bitmap.
     */
    public static Bitmap createHardwareBitmap(int width, int height, Consumer<Canvas> consumer) {
        final Picture picture = new Picture();
        final Canvas canvas = picture.beginRecording(width, height);
        consumer.accept(canvas);
        picture.endRecording();
        return Bitmap.createBitmap(picture);
    }
}
