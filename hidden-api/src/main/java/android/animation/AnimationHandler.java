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

package android.animation;

import android.os.SystemClock;
//import android.os.SystemProperties;
import android.util.ArrayMap;
import android.util.Log;
import android.view.Choreographer;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * This custom, static handler handles the timing pulse that is shared by all active
 * ValueAnimators. This approach ensures that the setting of animation values will happen on the
 * same thread that animations start on, and that all animations will share the same times for
 * calculating their values, which makes synchronizing animations possible.
 *
 * The handler uses the Choreographer by default for doing periodic callbacks. A custom
 * AnimationFrameCallbackProvider can be set on the handler to provide timing pulse that
 * may be independent of UI frame update. This could be useful in testing.
 *
 *
 */
public class AnimationHandler {

    private static final String TAG = "AnimationHandler";
    private static final boolean LOCAL_LOGV = false;

    /**
     * Internal per-thread collections used to avoid set collisions as animations start and end
     * while being processed.
     */
    private final ArrayMap<AnimationFrameCallback, Long> mDelayedCallbackStartTime =
        new ArrayMap<>();
    private final ArrayList<AnimationFrameCallback> mAnimationCallbacks =
        new ArrayList<>();
    private final ArrayList<AnimationFrameCallback> mCommitCallbacks =
        new ArrayList<>();
    private AnimationFrameCallbackProvider mProvider;

    // Static flag which allows the pausing behavior to be globally disabled/enabled.
    private static boolean sAnimatorPausingEnabled = isPauseBgAnimationsEnabledInSystemProperties();

    // Static flag which prevents the system property from overriding sAnimatorPausingEnabled field.
    private static boolean sOverrideAnimatorPausingSystemProperty = false;

    /**
     * This paused list is used to store animators forcibly paused when the activity
     * went into the background (to avoid unnecessary background processing work).
     * These animators should be resume()'d when the activity returns to the foreground.
     */
    private final ArrayList<Animator> mPausedAnimators = new ArrayList<>();

    /**
     * This structure is used to store the currently active objects (ViewRootImpls or
     * WallpaperService.Engines) in the process. Each of these objects sends a request to
     * AnimationHandler when it goes into the background (request to pause) or foreground
     * (request to resume). Because all animators are managed by AnimationHandler on the same
     * thread, it should only ever pause animators when *all* requestors are in the background.
     * This list tracks the background/foreground state of all requestors and only ever
     * pauses animators when all items are in the background (false). To simplify, we only ever
     * store visible (foreground) requestors; if the set size reaches zero, there are no
     * objects in the foreground and it is time to pause animators.
     */
    private final ArrayList<WeakReference<Object>> mAnimatorRequestors = new ArrayList<>();

    private final Choreographer.FrameCallback mFrameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            doAnimationFrame(getProvider().getFrameTime());
            if (mAnimationCallbacks.size() > 0) {
                getProvider().postFrameCallback(this);
            }
        }
    };

    public final static ThreadLocal<AnimationHandler> sAnimatorHandler = new ThreadLocal<>();
    private boolean mListDirty = false;

    public static AnimationHandler getInstance() {
        if (sAnimatorHandler.get() == null) {
            sAnimatorHandler.set(new AnimationHandler());
        }
        return sAnimatorHandler.get();
    }

    /**
     * System property that controls the behavior of pausing infinite animators when an app
     * is moved to the background.
     *
     * @return the value of 'framework.pause_bg_animations.enabled' system property
     */
    private static boolean isPauseBgAnimationsEnabledInSystemProperties() {
        return false;
    }

    /**
     * Disable the default behavior of pausing infinite animators when
     * apps go into the background.
     *
     * @param enable Enable (default behavior) or disable background pausing behavior.
     */
    public static void setAnimatorPausingEnabled(boolean enable) {
        sAnimatorPausingEnabled = enable;
    }

    /**
     * Prevents the setAnimatorPausingEnabled behavior from being overridden
     * by the 'framework.pause_bg_animations.enabled' system property value.
     *
     * This is for testing purposes only.
     *
     * @param enable Enable or disable (default behavior) overriding the system
     *               property.
     */
    public static void setOverrideAnimatorPausingSystemProperty(boolean enable) {
        sOverrideAnimatorPausingSystemProperty = enable;
    }

    /**
     * This is called when a window goes away. We should remove
     * it from the requestors list to ensure that we are counting requests correctly and not
     * tracking obsolete+enabled requestors.
     */
    public static void removeRequestor(Object requestor) {
        if (LOCAL_LOGV) {
            Log.v(TAG, "removeRequestor for " + requestor);
        }
    }

    /**
     * This method is called from ViewRootImpl or WallpaperService when either a window is no
     * longer visible (enable == false) or when a window becomes visible (enable == true).
     * If animators are not properly disabled when activities are backgrounded, it can lead to
     * unnecessary processing, particularly for infinite animators, as the system will continue
     * to pulse timing events even though the results are not visible. As a workaround, we
     * pause all un-paused infinite animators, and resume them when any window in the process
     * becomes visible.
     */
    public static void requestAnimatorsEnabled(boolean enable, Object requestor) {
    }

    private void requestAnimatorsEnabledImpl(boolean enable, Object requestor) {
        boolean wasEmpty = mAnimatorRequestors.isEmpty();
        setAnimatorPausingEnabled(isPauseBgAnimationsEnabledInSystemProperties());
        synchronized (mAnimatorRequestors) {
            // Only store WeakRef objects to avoid leaks
            if (enable) {
                // First, check whether such a reference is already on the list
                WeakReference<Object> weakRef = null;
                for (int i = mAnimatorRequestors.size() - 1; i >= 0; --i) {
                    WeakReference<Object> ref = mAnimatorRequestors.get(i);
                    Object referent = ref.get();
                    if (referent == requestor) {
                        weakRef = ref;
                    } else if (referent == null) {
                        // Remove any reference that has been cleared
                        mAnimatorRequestors.remove(i);
                    }
                }
                if (weakRef == null) {
                    weakRef = new WeakReference<>(requestor);
                    mAnimatorRequestors.add(weakRef);
                }
            } else {
                for (int i = mAnimatorRequestors.size() - 1; i >= 0; --i) {
                    WeakReference<Object> ref = mAnimatorRequestors.get(i);
                    Object referent = ref.get();
                    if (referent == requestor || referent == null) {
                        // remove requested item or item that has been cleared
                        mAnimatorRequestors.remove(i);
                    }
                }
                // If a reference to the requestor wasn't in the list, nothing to remove
            }
        }
        if (!sAnimatorPausingEnabled) {
            // Resume any animators that have been paused in the meantime, otherwise noop
            // Leave logic above so that if pausing gets re-enabled, the state of the requestors
            // list is valid
            resumeAnimators();
            return;
        }
        boolean isEmpty = mAnimatorRequestors.isEmpty();
        if (wasEmpty != isEmpty) {
            // only paused/resume animators if there was a visibility change
            if (!isEmpty) {
                // If any requestors are enabled, resume currently paused animators
                resumeAnimators();
            } else {
                // Wait before pausing to avoid thrashing animator state for temporary backgrounding

            }
        }
        if (LOCAL_LOGV) {

            for (int i = 0; i < mAnimatorRequestors.size(); ++i) {
                Log.v(TAG, "animatorRequestors " + i + " = "
                    + mAnimatorRequestors.get(i) + " with referent "
                    + mAnimatorRequestors.get(i).get());
            }
        }
    }

    private void resumeAnimators() {
        Choreographer.getInstance().removeFrameCallback(mPauser);
        for (int i = mPausedAnimators.size() - 1; i >= 0; --i) {
            mPausedAnimators.get(i).resume();
        }
        mPausedAnimators.clear();
    }

    private Choreographer.FrameCallback mPauser = frameTimeNanos -> {
        if (mAnimatorRequestors.size() > 0) {
            // something enabled animators since this callback was scheduled - bail
            return;
        }
        for (int i = 0; i < mAnimationCallbacks.size(); ++i) {
            AnimationFrameCallback callback = mAnimationCallbacks.get(i);
            if (callback instanceof Animator) {
                Animator animator = ((Animator) callback);
                if (animator.getTotalDuration() == Animator.DURATION_INFINITE
                    && !animator.isPaused()) {
                    mPausedAnimators.add(animator);
                    animator.pause();
                }
            }
        }
    };

    /**
     * By default, the Choreographer is used to provide timing for frame callbacks. A custom
     * provider can be used here to provide different timing pulse.
     */
    public void setProvider(AnimationFrameCallbackProvider provider) {
        if (provider == null) {
            mProvider = new MyFrameCallbackProvider();
        } else {
            mProvider = provider;
        }
    }

    private AnimationFrameCallbackProvider getProvider() {
        if (mProvider == null) {
            mProvider = new MyFrameCallbackProvider();
        }
        return mProvider;
    }

    /**
     * Register to get a callback on the next frame after the delay.
     */
    public void addAnimationFrameCallback(final AnimationFrameCallback callback, long delay) {
        if (mAnimationCallbacks.size() == 0) {
            getProvider().postFrameCallback(mFrameCallback);
        }
        if (!mAnimationCallbacks.contains(callback)) {
            mAnimationCallbacks.add(callback);
        }

        if (delay > 0) {
            mDelayedCallbackStartTime.put(callback, (SystemClock.uptimeMillis() + delay));
        }
    }

    /**
     * Register to get a one shot callback for frame commit timing. Frame commit timing is the
     * time *after* traversals are done, as opposed to the animation frame timing, which is
     * before any traversals. This timing can be used to adjust the start time of an animation
     * when expensive traversals create big delta between the animation frame timing and the time
     * that animation is first shown on screen.
     *
     * Note this should only be called when the animation has already registered to receive
     * animation frame callbacks. This callback will be guaranteed to happen *after* the next
     * animation frame callback.
     */
    public void addOneShotCommitCallback(final AnimationFrameCallback callback) {
        if (!mCommitCallbacks.contains(callback)) {
            mCommitCallbacks.add(callback);
        }
    }

    /**
     * Removes the given callback from the list, so it will no longer be called for frame related
     * timing.
     */
    public void removeCallback(AnimationFrameCallback callback) {
        mCommitCallbacks.remove(callback);
        mDelayedCallbackStartTime.remove(callback);
        int id = mAnimationCallbacks.indexOf(callback);
        if (id >= 0) {
            mAnimationCallbacks.set(id, null);
            mListDirty = true;
        }
    }

    private void doAnimationFrame(long frameTime) {
        long currentTime = SystemClock.uptimeMillis();
        final int size = mAnimationCallbacks.size();
        for (int i = 0; i < size; i++) {
            final AnimationFrameCallback callback = mAnimationCallbacks.get(i);
            if (callback == null) {
                continue;
            }
            if (isCallbackDue(callback, currentTime)) {
                callback.doAnimationFrame(frameTime);
                if (mCommitCallbacks.contains(callback)) {
                    getProvider().postCommitCallback(new Runnable() {
                        @Override
                        public void run() {
                            commitAnimationFrame(callback, getProvider().getFrameTime());
                        }
                    });
                }
            }
        }
        cleanUpList();
    }

    private void commitAnimationFrame(AnimationFrameCallback callback, long frameTime) {
        if (!mDelayedCallbackStartTime.containsKey(callback) &&
            mCommitCallbacks.contains(callback)) {
            callback.commitAnimationFrame(frameTime);
            mCommitCallbacks.remove(callback);
        }
    }

    /**
     * Remove the callbacks from mDelayedCallbackStartTime once they have passed the initial delay
     * so that they can start getting frame callbacks.
     *
     * @return true if they have passed the initial delay or have no delay, false otherwise.
     */
    private boolean isCallbackDue(AnimationFrameCallback callback, long currentTime) {
        Long startTime = mDelayedCallbackStartTime.get(callback);
        if (startTime == null) {
            return true;
        }
        if (startTime < currentTime) {
            mDelayedCallbackStartTime.remove(callback);
            return true;
        }
        return false;
    }



    private void cleanUpList() {
        if (mListDirty) {
            for (int i = mAnimationCallbacks.size() - 1; i >= 0; i--) {
                if (mAnimationCallbacks.get(i) == null) {
                    mAnimationCallbacks.remove(i);
                }
            }
            mListDirty = false;
        }
    }

    private int getCallbackSize() {
        int count = 0;
        int size = mAnimationCallbacks.size();
        for (int i = size - 1; i >= 0; i--) {
            if (mAnimationCallbacks.get(i) != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * Default provider of timing pulse that uses Choreographer for frame callbacks.
     */
    public class MyFrameCallbackProvider implements AnimationFrameCallbackProvider {

        final Choreographer mChoreographer = Choreographer.getInstance();

        @Override
        public void postFrameCallback(Choreographer.FrameCallback callback) {
            mChoreographer.postFrameCallback(callback);
        }

        @Override
        public void postCommitCallback(Runnable runnable) {
        }

        @Override
        public long getFrameTime() {
            return 1L;
        }

        @Override
        public long getFrameDelay() {
            return 1L;
        }

        @Override
        public void setFrameDelay(long delay) {
        }
    }

    /**
     * Callbacks that receives notifications for animation timing and frame commit timing.
     * @hide
     */
    public interface AnimationFrameCallback {
        /**
         * Run animation based on the frame time.
         * @param frameTime The frame start time, in the {@link SystemClock#uptimeMillis()} time
         *                  base.
         * @return if the animation has finished.
         */
        boolean doAnimationFrame(long frameTime);

        /**
         * This notifies the callback of frame commit time. Frame commit time is the time after
         * traversals happen, as opposed to the normal animation frame time that is before
         * traversals. This is used to compensate expensive traversals that happen as the
         * animation starts. When traversals take a long time to complete, the rendering of the
         * initial frame will be delayed (by a long time). But since the startTime of the
         * animation is set before the traversal, by the time of next frame, a lot of time would
         * have passed since startTime was set, the animation will consequently skip a few frames
         * to respect the new frameTime. By having the commit time, we can adjust the start time to
         * when the first frame was drawn (after any expensive traversals) so that no frames
         * will be skipped.
         *
         * @param frameTime The frame time after traversals happen, if any, in the
         *                  {@link SystemClock#uptimeMillis()} time base.
         */
        void commitAnimationFrame(long frameTime);
    }

    /**
     * The intention for having this interface is to increase the testability of ValueAnimator.
     * Specifically, we can have a custom implementation of the interface below and provide
     * timing pulse without using Choreographer. That way we could use any arbitrary interval for
     * our timing pulse in the tests.
     *
     * @hide
     */
    public interface AnimationFrameCallbackProvider {
        void postFrameCallback(Choreographer.FrameCallback callback);
        void postCommitCallback(Runnable runnable);
        long getFrameTime();
        long getFrameDelay();
        void setFrameDelay(long delay);
    }
}
