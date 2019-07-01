/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.systemui.shared.recents.utilities;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.RectEvaluator;
import android.annotation.FloatRange;
import android.annotation.Nullable;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.os.Trace;
import android.util.ArraySet;
import android.util.IntProperty;
import android.util.Property;
import android.util.TypedValue;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewRootImpl;
import android.view.ViewStub;

import java.util.ArrayList;
import java.util.Collections;

/* Common code */
public class Utilities {

    public static final Property<Drawable, Integer> DRAWABLE_ALPHA =
            new IntProperty<Drawable>("drawableAlpha") {
                @Override
                public void setValue(Drawable object, int alpha) {
                    object.setAlpha(alpha);
                }

                @Override
                public Integer get(Drawable object) {
                    return object.getAlpha();
                }
            };

    public static final Property<Drawable, Rect> DRAWABLE_RECT =
            new Property<Drawable, Rect>(Rect.class, "drawableBounds") {
                @Override
                public void set(Drawable object, Rect bounds) {
                    object.setBounds(bounds);
                }

                @Override
                public Rect get(Drawable object) {
                    return object.getBounds();
                }
            };

    public static final RectFEvaluator RECTF_EVALUATOR = new RectFEvaluator();
    public static final RectEvaluator RECT_EVALUATOR = new RectEvaluator(new Rect());

    /**
     * @return the first parent walking up the view hierarchy that has the given class type.
     *
     * @param parentClass must be a class derived from {@link View}
     */
    public static <T extends View> T findParent(View v, Class<T> parentClass) {
        ViewParent parent = v.getParent();
        while (parent != null) {
            if (parentClass.isAssignableFrom(parent.getClass())) {
                return (T) parent;
            }
            parent = parent.getParent();
        }
        return null;
    }

    /**
     * Initializes the {@param setOut} with the given object.
     */
    public static <T> ArraySet<T> objectToSet(T obj, ArraySet<T> setOut) {
        setOut.clear();
        if (obj != null) {
            setOut.add(obj);
        }
        return setOut;
    }

    /**
     * Replaces the contents of {@param setOut} with the contents of the {@param array}.
     */
    public static <T> ArraySet<T> arrayToSet(T[] array, ArraySet<T> setOut) {
        setOut.clear();
        if (array != null) {
            Collections.addAll(setOut, array);
        }
        return setOut;
    }

    /**
     * @return the clamped {@param value} between the provided {@param min} and {@param max}.
     */
    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * @return the clamped {@param value} between the provided {@param min} and {@param max}.
     */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * @return the clamped {@param value} between 0 and 1.
     */
    public static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    /**
     * Scales the {@param value} to be proportionally between the {@param min} and
     * {@param max} values.
     *
     * @param value must be between 0 and 1
     */
    public static float mapRange(@FloatRange(from=0.0,to=1.0) float value, float min, float max) {
        return min + (value * (max - min));
    }

    /**
     * Scales the {@param value} proportionally from {@param min} and {@param max} to 0 and 1.
     *
     * @param value must be between {@param min} and {@param max}
     */
    public static float unmapRange(float value, float min, float max) {
        return (value - min) / (max - min);
    }

    /** Scales a rect about its centroid */
    public static void scaleRectAboutCenter(RectF r, float scale) {
        if (scale != 1.0f) {
            float cx = r.centerX();
            float cy = r.centerY();
            r.offset(-cx, -cy);
            r.left *= scale;
            r.top *= scale;
            r.right *= scale;
            r.bottom *= scale;
            r.offset(cx, cy);
        }
    }

    /** Calculates the constrast between two colors, using the algorithm provided by the WCAG v2. */
    public static float computeContrastBetweenColors(int bg, int fg) {
        float bgR = Color.red(bg) / 255f;
        float bgG = Color.green(bg) / 255f;
        float bgB = Color.blue(bg) / 255f;
        bgR = (bgR < 0.03928f) ? bgR / 12.92f : (float) Math.pow((bgR + 0.055f) / 1.055f, 2.4f);
        bgG = (bgG < 0.03928f) ? bgG / 12.92f : (float) Math.pow((bgG + 0.055f) / 1.055f, 2.4f);
        bgB = (bgB < 0.03928f) ? bgB / 12.92f : (float) Math.pow((bgB + 0.055f) / 1.055f, 2.4f);
        float bgL = 0.2126f * bgR + 0.7152f * bgG + 0.0722f * bgB;
        
        float fgR = Color.red(fg) / 255f;
        float fgG = Color.green(fg) / 255f;
        float fgB = Color.blue(fg) / 255f;
        fgR = (fgR < 0.03928f) ? fgR / 12.92f : (float) Math.pow((fgR + 0.055f) / 1.055f, 2.4f);
        fgG = (fgG < 0.03928f) ? fgG / 12.92f : (float) Math.pow((fgG + 0.055f) / 1.055f, 2.4f);
        fgB = (fgB < 0.03928f) ? fgB / 12.92f : (float) Math.pow((fgB + 0.055f) / 1.055f, 2.4f);
        float fgL = 0.2126f * fgR + 0.7152f * fgG + 0.0722f * fgB;

        return Math.abs((fgL + 0.05f) / (bgL + 0.05f));
    }

    /** Returns the base color overlaid with another overlay color with a specified alpha. */
    public static int getColorWithOverlay(int baseColor, int overlayColor, float overlayAlpha) {
        return Color.rgb(
            (int) (overlayAlpha * Color.red(baseColor) +
                    (1f - overlayAlpha) * Color.red(overlayColor)),
            (int) (overlayAlpha * Color.green(baseColor) +
                    (1f - overlayAlpha) * Color.green(overlayColor)),
            (int) (overlayAlpha * Color.blue(baseColor) +
                    (1f - overlayAlpha) * Color.blue(overlayColor)));
    }

    /**
     * Cancels an animation ensuring that if it has listeners, onCancel and onEnd
     * are not called.
     */
    public static void cancelAnimationWithoutCallbacks(Animator animator) {
        if (animator != null && animator.isStarted()) {
            removeAnimationListenersRecursive(animator);
            animator.cancel();
        }
    }

    /**
     * Recursively removes all the listeners of all children of this animator
     */
    public static void removeAnimationListenersRecursive(Animator animator) {
        if (animator instanceof AnimatorSet) {
            ArrayList<Animator> animators = ((AnimatorSet) animator).getChildAnimations();
            for (int i = animators.size() - 1; i >= 0; i--) {
                removeAnimationListenersRecursive(animators.get(i));
            }
        }
        animator.removeAllListeners();
    }

    /**
     * Sets the given {@link View}'s frame from its current translation.
     */
    public static void setViewFrameFromTranslation(View v) {
        RectF taskViewRect = new RectF(v.getLeft(), v.getTop(), v.getRight(), v.getBottom());
        taskViewRect.offset(v.getTranslationX(), v.getTranslationY());
        v.setTranslationX(0);
        v.setTranslationY(0);
        v.setLeftTopRightBottom((int) taskViewRect.left, (int) taskViewRect.top,
                (int) taskViewRect.right, (int) taskViewRect.bottom);
    }

    /**
     * Returns a view stub for the given view id.
     */
    public static ViewStub findViewStubById(View v, int stubId) {
        return (ViewStub) v.findViewById(stubId);
    }

    /**
     * Returns a view stub for the given view id.
     */
    public static ViewStub findViewStubById(Activity a, int stubId) {
        return (ViewStub) a.findViewById(stubId);
    }

    /**
     * Used for debugging, converts DP to PX.
     */
    public static float dpToPx(Resources res, float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, res.getDisplayMetrics());
    }

    /**
     * Adds a trace event for debugging.
     */
    public static void addTraceEvent(String event) {
        Trace.traceBegin(Trace.TRACE_TAG_VIEW, event);
        Trace.traceEnd(Trace.TRACE_TAG_VIEW);
    }

    /**
     * Returns whether this view, or one of its descendants have accessibility focus.
     */
    public static boolean isDescendentAccessibilityFocused(View v) {
        if (v.isAccessibilityFocused()) {
            return true;
        }

        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            int childCount = vg.getChildCount();
            for (int i = 0; i < childCount; i++) {
                if (isDescendentAccessibilityFocused(vg.getChildAt(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the application configuration, which is independent of the activity's current
     * configuration in multiwindow.
     */
    public static Configuration getAppConfiguration(Context context) {
        return context.getApplicationContext().getResources().getConfiguration();
    }

    /**
     * @return The next frame name for the specified surface or -1 if the surface is no longer
     *         valid.
     */
    public static long getNextFrameNumber(Surface s) {
        return s != null && s.isValid()
                ? s.getNextFrameNumber()
                : -1;

    }

    /**
     * @return The surface for the specified view.
     */
    public static @Nullable Surface getSurface(View v) {
        ViewRootImpl viewRoot = v.getViewRootImpl();
        if (viewRoot == null) {
            return null;
        }
        return viewRoot.mSurface;
    }

    /**
     * Returns a lightweight dump of a rect.
     */
    public static String dumpRect(Rect r) {
        if (r == null) {
            return "N:0,0-0,0";
        }
        return r.left + "," + r.top + "-" + r.right + "," + r.bottom;
    }

    /**
     * Posts a runnable on a handler at the front of the queue ignoring any sync barriers.
     */
    public static void postAtFrontOfQueueAsynchronously(Handler h, Runnable r) {
        Message msg = h.obtainMessage().setCallback(r);
        h.sendMessageAtFrontOfQueue(msg);
    }
}
