/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.app.viewcapture;

import static com.android.app.viewcapture.data.ExportedData.MagicNumber.MAGIC_NUMBER_H;
import static com.android.app.viewcapture.data.ExportedData.MagicNumber.MAGIC_NUMBER_L;

import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.media.permission.SafeCloseable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Trace;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.android.app.viewcapture.data.ExportedData;
import com.android.app.viewcapture.data.FrameData;
import com.android.app.viewcapture.data.MotionWindowData;
import com.android.app.viewcapture.data.ViewNode;
import com.android.app.viewcapture.data.WindowData;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Utility class for capturing view data every frame
 */
public abstract class ViewCapture {

    private static final String TAG = "ViewCapture";

    // These flags are copies of two private flags in the View class.
    private static final int PFLAG_INVALIDATED = 0x80000000;
    private static final int PFLAG_DIRTY_MASK = 0x00200000;

    private static final long MAGIC_NUMBER_FOR_WINSCOPE =
            ((long) MAGIC_NUMBER_H.getNumber() << 32) | MAGIC_NUMBER_L.getNumber();

    // Number of frames to keep in memory
    private final int mMemorySize;

    // Number of ViewPropertyRef to preallocate per window
    private final int mInitPoolSize;

    protected static final int DEFAULT_MEMORY_SIZE = 2000;
    // Initial size of the reference pool. This is at least be 5 * total number of views in
    // Launcher. This allows the first free frames avoid object allocation during view capture.
    protected static final int DEFAULT_INIT_POOL_SIZE = 300;

    public static final LooperExecutor MAIN_EXECUTOR = new LooperExecutor(Looper.getMainLooper());

    private final List<WindowListener> mListeners = Collections.synchronizedList(new ArrayList<>());

    protected final Executor mBgExecutor;

    private boolean mIsEnabled = true;

    @VisibleForTesting
    public boolean mIsStarted = false;

    protected ViewCapture(int memorySize, int initPoolSize, Executor bgExecutor) {
        mMemorySize = memorySize;
        mBgExecutor = bgExecutor;
        mInitPoolSize = initPoolSize;
    }

    public static LooperExecutor createAndStartNewLooperExecutor(String name, int priority) {
        HandlerThread thread = new HandlerThread(name, priority);
        thread.start();
        return new LooperExecutor(thread.getLooper());
    }

    /**
     * Attaches the ViewCapture to the provided window and returns a handle to detach the listener
     */
    @AnyThread
    @NonNull
    public SafeCloseable startCapture(@NonNull Window window) {
        String title = window.getAttributes().getTitle().toString();
        String name = TextUtils.isEmpty(title) ? window.toString() : title;
        return startCapture(window.getDecorView(), name);
    }

    /**
     * Attaches the ViewCapture to the provided window and returns a handle to detach the listener.
     * Verifies that ViewCapture is enabled before actually attaching an onDrawListener.
     */
    @AnyThread
    @NonNull
    public SafeCloseable startCapture(@NonNull View view, @NonNull String name) {
        mIsStarted = true;
        WindowListener listener = new WindowListener(view, name);

        if (mIsEnabled) {
            listener.attachToRoot();
        }

        mListeners.add(listener);

        view.getContext().registerComponentCallbacks(listener);

        return () -> {
            if (listener.mRoot != null && listener.mRoot.getContext() != null) {
                listener.mRoot.getContext().unregisterComponentCallbacks(listener);
            }
            mListeners.remove(listener);

            listener.detachFromRoot();
        };
    }

    /**
     * Launcher checks for leaks in many spots during its instrumented tests. The WindowListeners
     * appear to have leaks because they store mRoot views. In reality, attached views close their
     * respective window listeners when they are destroyed.
     * <p>
     * This method deletes detaches and deletes mRoot views from windowListeners. This makes the
     * WindowListeners unusable for anything except dumping previously captured information. They
     * are still technically enabled to allow for dumping.
     */
    @VisibleForTesting
    @AnyThread
    public void stopCapture(@NonNull View rootView) {
        mIsStarted = false;
        mListeners.forEach(it -> {
            if (rootView == it.mRoot) {
                runOnUiThread(() -> {
                    if (it.mRoot != null) {
                        it.mRoot.getViewTreeObserver().removeOnDrawListener(it);
                        it.mRoot = null;
                    }
                }, it.mRoot);
            }
        });
    }

    @AnyThread
    protected void enableOrDisableWindowListeners(boolean isEnabled) {
        mIsEnabled = isEnabled;
        mListeners.forEach(WindowListener::detachFromRoot);
        if (mIsEnabled) mListeners.forEach(WindowListener::attachToRoot);
    }

    @AnyThread
    protected void dumpTo(OutputStream os, Context context)
            throws InterruptedException, ExecutionException, IOException {
        if (mIsEnabled) {
            DataOutputStream dataOutputStream = new DataOutputStream(os);
            ExportedData ex = getExportedData(context);
            dataOutputStream.writeInt(ex.getSerializedSize());
            ex.writeTo(dataOutputStream);
        }
    }

    @VisibleForTesting
    public ExportedData getExportedData(Context context)
            throws InterruptedException, ExecutionException {
        ArrayList<Class> classList = new ArrayList<>();
        return ExportedData.newBuilder()
                .setMagicNumber(MAGIC_NUMBER_FOR_WINSCOPE)
                .setPackage(context.getPackageName())
                .addAllWindowData(getWindowData(context, classList, l -> l.mIsActive).get())
                .addAllClassname(toStringList(classList))
                .setRealToElapsedTimeOffsetNanos(TimeUnit.MILLISECONDS
                        .toNanos(System.currentTimeMillis()) - SystemClock.elapsedRealtimeNanos())
                .build();
    }

    private static List<String> toStringList(List<Class> classList) {
        return classList.stream().map(Class::getName).collect(Collectors.toList());
    }

    public CompletableFuture<Optional<MotionWindowData>> getDumpTask(View view) {
        ArrayList<Class> classList = new ArrayList<>();
        return getWindowData(view.getContext().getApplicationContext(), classList,
                l -> l.mRoot.equals(view)).thenApply(list -> list.stream().findFirst().map(w ->
                MotionWindowData.newBuilder()
                        .addAllFrameData(w.getFrameDataList())
                        .addAllClassname(toStringList(classList))
                        .build()));
    }

    @AnyThread
    private CompletableFuture<List<WindowData>> getWindowData(Context context,
            ArrayList<Class> outClassList, Predicate<WindowListener> filter) {
        ViewIdProvider idProvider = new ViewIdProvider(context.getResources());
        return CompletableFuture.supplyAsync(
                () -> mListeners.stream()
                        .filter(filter)
                        .collect(Collectors.toList()),
                MAIN_EXECUTOR).thenApplyAsync(
                        it -> it.stream()
                                .map(l -> l.dumpToProto(idProvider, outClassList))
                                .collect(Collectors.toList()),
                        mBgExecutor);
    }

    @WorkerThread
    protected void onCapturedViewPropertiesBg(long elapsedRealtimeNanos, String windowName,
            ViewPropertyRef startFlattenedViewTree) {
    }

    @AnyThread
    void runOnUiThread(Runnable action, View view) {
        if (view == null) {
            // Corner case. E.g.: the capture is stopped (root view set to null),
            // but the bg thread is still processing work.
            Log.i(TAG, "Skipping run on UI thread. Provided view == null.");
            return;
        }

        Handler handlerUi = view.getHandler();
        if (handlerUi != null && handlerUi.getLooper().getThread() == Thread.currentThread()) {
            action.run();
            return;
        }

        view.post(action);
    }

    /**
     * Once this window listener is attached to a window's root view, it traverses the entire
     * view tree on the main thread every time onDraw is called. It then saves the state of the view
     * tree traversed in a local list of nodes, so that this list of nodes can be processed on a
     * background thread, and prepared for being dumped into a bugreport.
     * <p>
     * Since some of the work needs to be done on the main thread after every draw, this piece of
     * code needs to be hyper optimized. That is why we are recycling ViewPropertyRef objects
     * and storing the list of nodes as a flat LinkedList, rather than as a tree. This data
     * structure allows recycling to happen in O(1) time via pointer assignment. Without this
     * optimization, a lot of time is wasted creating ViewPropertyRef objects, or finding
     * ViewPropertyRef objects to recycle.
     * <p>
     * Another optimization is to only traverse view nodes on the main thread that have potentially
     * changed since the last frame was drawn. This can be determined via a combination of private
     * flags inside the View class.
     * <p>
     * Another optimization is to not store or manipulate any string objects on the main thread.
     * While this might seem trivial, using Strings in any form causes the ViewCapture to hog the
     * main thread for up to an additional 6-7ms. It must be avoided at all costs.
     * <p>
     * Another optimization is to only store the class names of the Views in the view hierarchy one
     * time. They are then referenced via a classNameIndex value stored in each ViewPropertyRef.
     * <p>
     * TODO: b/262585897: If further memory optimization is required, an effective one would be to
     * only store the changes between frames, rather than the entire node tree for each frame.
     * The go/web-hv UX already does this, and has reaped significant memory improves because of it.
     * <p>
     * TODO: b/262585897: Another memory optimization could be to store all integer, float, and
     * boolean information via single integer values via the Chinese remainder theorem, or a similar
     * algorithm, which enables multiple numerical values to be stored inside 1 number. Doing this
     * would allow each ViewPropertyRef to slim down its memory footprint significantly.
     * <p>
     * One important thing to remember is that bugs related to recycling will usually only appear
     * after at least 2000 frames have been rendered. If that code is changed, the tester can
     * use hard-coded logs to verify that recycling is happening, and test view capturing at least
     * ~8000 frames or so to verify the recycling functionality is working properly.
     * <p>
     * Each WindowListener is memory aware and will both stop collecting view capture information,
     * as well as delete their current stash of information upon a signal from the system that
     * memory resources are scarce. The user will need to restart the app process before
     * more ViewCapture information is captured.
     */
    private class WindowListener implements ViewTreeObserver.OnDrawListener, ComponentCallbacks2 {

        @Nullable
        public View mRoot;
        public final String name;

        // Pool used for capturing view tree on the UI thread.
        private ViewPropertyRef mPool = new ViewPropertyRef();
        private final ViewPropertyRef mViewPropertyRef = new ViewPropertyRef();

        private int mFrameIndexBg = -1;
        private boolean mIsFirstFrame = true;
        private long[] mFrameTimesNanosBg = new long[mMemorySize];
        private ViewPropertyRef[] mNodesBg = new ViewPropertyRef[mMemorySize];

        private boolean mIsActive = true;
        private final Consumer<ViewPropertyRef> mCaptureCallback =
                this::copyCleanViewsFromLastFrameBg;

        WindowListener(View view, String name) {
            mRoot = view;
            this.name = name;
            initPool(mInitPoolSize);
        }

        /**
         * Every time onDraw is called, it does the minimal set of work required on the main thread,
         * i.e. capturing potentially dirty / invalidated views, and then immediately offloads the
         * rest of the processing work (extracting the captured view properties) to a background
         * thread via mExecutor.
         */
        @Override
        @UiThread
        public void onDraw() {
            Trace.beginSection("vc#onDraw");
            try {
                View root = mRoot;
                if (root == null) {
                    // Handle the corner case where another (non-UI) thread
                    // concurrently stopped the capture and set mRoot = null
                    return;
                }
                captureViewTree(root, mViewPropertyRef);
                ViewPropertyRef captured = mViewPropertyRef.next;
                if (captured != null) {
                    captured.callback = mCaptureCallback;
                    captured.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos();
                    mBgExecutor.execute(captured);
                }
                mIsFirstFrame = false;
            } finally {
                Trace.endSection();
            }
        }

        /**
         * Copy clean views from the last frame on the background thread. Clean views are
         * the remaining part of the view hierarchy that was not already copied by the UI thread.
         * Then transfer the received ViewPropertyRef objects back to the UI thread's pool.
         */
        @WorkerThread
        private void copyCleanViewsFromLastFrameBg(ViewPropertyRef start) {
            Trace.beginSection("vc#copyCleanViewsFromLastFrameBg");

            long elapsedRealtimeNanos = start.elapsedRealtimeNanos;
            mFrameIndexBg++;
            if (mFrameIndexBg >= mMemorySize) {
                mFrameIndexBg = 0;
            }
            mFrameTimesNanosBg[mFrameIndexBg] = elapsedRealtimeNanos;

            ViewPropertyRef recycle = mNodesBg[mFrameIndexBg];

            ViewPropertyRef resultStart = null;
            ViewPropertyRef resultEnd = null;

            ViewPropertyRef end = start;

            while (end != null) {
                end.completeTransferFromViewBg();

                ViewPropertyRef propertyRef = recycle;
                if (propertyRef == null) {
                    propertyRef = new ViewPropertyRef();
                } else {
                    recycle = recycle.next;
                    propertyRef.next = null;
                }

                ViewPropertyRef copy = null;
                if (end.childCount < 0) {
                    copy = findInLastFrame(end.hashCode);
                    if (copy != null) {
                        copy.transferTo(end);
                    } else {
                        end.childCount = 0;
                    }
                }
                end.transferTo(propertyRef);

                if (resultStart == null) {
                    resultStart = propertyRef;
                    resultEnd = resultStart;
                } else {
                    resultEnd.next = propertyRef;
                    resultEnd = resultEnd.next;
                }

                if (copy != null) {
                    int pending = copy.childCount;
                    while (pending > 0) {
                        copy = copy.next;
                        pending = pending - 1 + copy.childCount;

                        propertyRef = recycle;
                        if (propertyRef == null) {
                            propertyRef = new ViewPropertyRef();
                        } else {
                            recycle = recycle.next;
                            propertyRef.next = null;
                        }

                        copy.transferTo(propertyRef);

                        resultEnd.next = propertyRef;
                        resultEnd = resultEnd.next;
                    }
                }

                if (end.next == null) {
                    // The compiler will complain about using a non-final variable from
                    // an outer class in a lambda if we pass in 'end' directly.
                    final ViewPropertyRef finalEnd = end;
                    runOnUiThread(() -> addToPool(start, finalEnd), mRoot);
                    break;
                }
                end = end.next;
            }
            mNodesBg[mFrameIndexBg] = resultStart;

            onCapturedViewPropertiesBg(elapsedRealtimeNanos, name, resultStart);

            Trace.endSection();
        }

        @WorkerThread
        private @Nullable ViewPropertyRef findInLastFrame(int hashCode) {
            int lastFrameIndex = (mFrameIndexBg == 0) ? mMemorySize - 1 : mFrameIndexBg - 1;
            ViewPropertyRef viewPropertyRef = mNodesBg[lastFrameIndex];
            while (viewPropertyRef != null && viewPropertyRef.hashCode != hashCode) {
                viewPropertyRef = viewPropertyRef.next;
            }
            return viewPropertyRef;
        }

        private void initPool(int initPoolSize) {
            ViewPropertyRef start = new ViewPropertyRef();
            ViewPropertyRef current = start;

            for (int i = 0; i < initPoolSize; i++) {
                current.next = new ViewPropertyRef();
                current = current.next;
            }

            ViewPropertyRef finalCurrent = current;
            addToPool(start, finalCurrent);
        }

        private void addToPool(ViewPropertyRef start, ViewPropertyRef end) {
            end.next = mPool;
            mPool = start;
        }

        @UiThread
        private ViewPropertyRef getFromPool() {
            ViewPropertyRef ref = mPool;
            if (ref != null) {
                mPool = ref.next;
                ref.next = null;
            } else {
                ref = new ViewPropertyRef();
            }
            return ref;
        }

        @AnyThread
        void attachToRoot() {
            if (mRoot == null) return;
            mIsActive = true;
            runOnUiThread(() -> {
                if (mRoot.isAttachedToWindow()) {
                    safelyEnableOnDrawListener();
                } else {
                    mRoot.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                        @Override
                        public void onViewAttachedToWindow(View v) {
                            if (mIsActive) {
                                safelyEnableOnDrawListener();
                            }
                            mRoot.removeOnAttachStateChangeListener(this);
                        }

                        @Override
                        public void onViewDetachedFromWindow(View v) {
                        }
                    });
                }
            }, mRoot);
        }

        @AnyThread
        void detachFromRoot() {
            mIsActive = false;
            runOnUiThread(() -> {
                if (mRoot != null) {
                    mRoot.getViewTreeObserver().removeOnDrawListener(this);
                }
            }, mRoot);
        }

        @UiThread
        private void safelyEnableOnDrawListener() {
            if (mRoot != null) {
                mRoot.getViewTreeObserver().removeOnDrawListener(this);
                mRoot.getViewTreeObserver().addOnDrawListener(this);
            }
        }

        @WorkerThread
        private WindowData dumpToProto(ViewIdProvider idProvider, ArrayList<Class> classList) {
            WindowData.Builder builder = WindowData.newBuilder().setTitle(name);
            int size = (mNodesBg[mMemorySize - 1] == null) ? mFrameIndexBg + 1 : mMemorySize;
            for (int i = size - 1; i >= 0; i--) {
                int index = (mMemorySize + mFrameIndexBg - i) % mMemorySize;
                ViewNode.Builder nodeBuilder = ViewNode.newBuilder();
                mNodesBg[index].toProto(idProvider, classList, nodeBuilder);
                FrameData.Builder frameDataBuilder = FrameData.newBuilder()
                        .setNode(nodeBuilder)
                        .setTimestamp(mFrameTimesNanosBg[index]);
                builder.addFrameData(frameDataBuilder);
            }
            return builder.build();
        }

        @UiThread
        private ViewPropertyRef captureViewTree(View view, ViewPropertyRef start) {
            ViewPropertyRef ref = getFromPool();
            start.next = ref;
            if (view instanceof ViewGroup) {
                ViewGroup parent = (ViewGroup) view;
                // If a view has not changed since the last frame, we will copy
                // its children from the last processed frame's data.
                if ((view.mPrivateFlags & (PFLAG_INVALIDATED | PFLAG_DIRTY_MASK)) == 0
                        && !mIsFirstFrame) {
                    // A negative child count is the signal to copy this view from the last frame.
                    ref.childCount = -1;
                    ref.view = view;
                    return ref;
                }
                ViewPropertyRef result = ref;
                int childCount = ref.childCount = parent.getChildCount();
                ref.transferFrom(view);
                for (int i = 0; i < childCount; i++) {
                    result = captureViewTree(parent.getChildAt(i), result);
                }
                return result;
            } else {
                ref.childCount = 0;
                ref.transferFrom(view);
                return ref;
            }
        }

        @Override
        public void onTrimMemory(int level) {
            if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
                mNodesBg = new ViewPropertyRef[0];
                mFrameTimesNanosBg = new long[0];
                if (mRoot != null && mRoot.getContext() != null) {
                    mRoot.getContext().unregisterComponentCallbacks(this);
                }
                detachFromRoot();
                mRoot = null;
            }
        }

        @Override
        public void onConfigurationChanged(Configuration configuration) {
            // No Operation
        }

        @Override
        public void onLowMemory() {
            onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND);
        }
    }

    protected static class ViewPropertyRef implements Runnable {
        public View view;

        // We store reference in memory to avoid generating and storing too many strings
        public Class clazz;
        public int hashCode;

        public int id;
        public int left, top, right, bottom;
        public int scrollX, scrollY;

        public float translateX, translateY;
        public float scaleX, scaleY;
        public float alpha;
        public float elevation;

        public int visibility;
        public boolean willNotDraw;
        public boolean clipChildren;
        public int childCount = 0;

        public ViewPropertyRef next;

        public Consumer<ViewPropertyRef> callback = null;
        public long elapsedRealtimeNanos = 0;


        public void transferFrom(View in) {
            view = in;

            left = in.getLeft();
            top = in.getTop();
            right = in.getRight();
            bottom = in.getBottom();
            scrollX = in.getScrollX();
            scrollY = in.getScrollY();

            translateX = in.getTranslationX();
            translateY = in.getTranslationY();
            scaleX = in.getScaleX();
            scaleY = in.getScaleY();
            alpha = in.getAlpha();
            elevation = in.getElevation();

            visibility = in.getVisibility();
            willNotDraw = in.willNotDraw();
        }

        /**
         * Transfer in backgroup thread view properties that remain unchanged between frames.
         */
        public void completeTransferFromViewBg() {
            clazz = view.getClass();
            hashCode = view.hashCode();
            id = view.getId();
            view = null;
        }

        public void transferTo(ViewPropertyRef out) {
            out.clazz = this.clazz;
            out.hashCode = this.hashCode;
            out.childCount = this.childCount;
            out.id = this.id;
            out.left = this.left;
            out.top = this.top;
            out.right = this.right;
            out.bottom = this.bottom;
            out.scrollX = this.scrollX;
            out.scrollY = this.scrollY;
            out.scaleX = this.scaleX;
            out.scaleY = this.scaleY;
            out.translateX = this.translateX;
            out.translateY = this.translateY;
            out.alpha = this.alpha;
            out.visibility = this.visibility;
            out.willNotDraw = this.willNotDraw;
            out.clipChildren = this.clipChildren;
            out.elevation = this.elevation;
        }

        /**
         * Converts the data to the proto representation and returns the next property ref
         * at the end of the iteration.
         */
        public ViewPropertyRef toProto(ViewIdProvider idProvider, ArrayList<Class> classList,
                ViewNode.Builder viewNode) {
            int classnameIndex = classList.indexOf(clazz);
            if (classnameIndex < 0) {
                classnameIndex = classList.size();
                classList.add(clazz);
            }

            viewNode.setClassnameIndex(classnameIndex)
                    .setHashcode(hashCode)
                    .setId(idProvider.getName(id))
                    .setLeft(left)
                    .setTop(top)
                    .setWidth(right - left)
                    .setHeight(bottom - top)
                    .setTranslationX(translateX)
                    .setTranslationY(translateY)
                    .setScrollX(scrollX)
                    .setScrollY(scrollY)
                    .setScaleX(scaleX)
                    .setScaleY(scaleY)
                    .setAlpha(alpha)
                    .setVisibility(visibility)
                    .setWillNotDraw(willNotDraw)
                    .setElevation(elevation)
                    .setClipChildren(clipChildren);

            ViewPropertyRef result = next;
            for (int i = 0; (i < childCount) && (result != null); i++) {
                ViewNode.Builder childViewNode = ViewNode.newBuilder();
                result = result.toProto(idProvider, classList, childViewNode);
                viewNode.addChildren(childViewNode);
            }
            return result;
        }

        @Override
        public void run() {
            Consumer<ViewPropertyRef> oldCallback = callback;
            callback = null;
            if (oldCallback != null) {
                oldCallback.accept(this);
            }
        }
    }

    protected static final class ViewIdProvider {

        private final SparseArray<String> mNames = new SparseArray<>();
        private final Resources mRes;

        ViewIdProvider(Resources res) {
            mRes = res;
        }

        String getName(int id) {
            String name = mNames.get(id);
            if (name == null) {
                if (id >= 0) {
                    try {
                        name = mRes.getResourceTypeName(id) + '/' + mRes.getResourceEntryName(id);
                    } catch (Resources.NotFoundException e) {
                        name = "id/" + "0x" + Integer.toHexString(id).toUpperCase();
                    }
                } else {
                    name = "NO_ID";
                }
                mNames.put(id, name);
            }
            return name;
        }
    }
}
