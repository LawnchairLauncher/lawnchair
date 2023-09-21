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

import static java.util.stream.Collectors.toList;

import android.content.Context;
import android.content.res.Resources;
import android.media.permission.SafeCloseable;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Trace;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import com.android.app.viewcapture.data.ExportedData;
import com.android.app.viewcapture.data.FrameData;
import com.android.app.viewcapture.data.ViewNode;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;
import java.util.zip.GZIPOutputStream;

/**
 * Utility class for capturing view data every frame
 */
public abstract class ViewCapture {

    private static final String TAG = "ViewCapture";

    // These flags are copies of two private flags in the View class.
    private static final int PFLAG_INVALIDATED = 0x80000000;
    private static final int PFLAG_DIRTY_MASK = 0x00200000;

    // Number of frames to keep in memory
    private final int mMemorySize;
    protected static final int DEFAULT_MEMORY_SIZE = 2000;
    // Initial size of the reference pool. This is at least be 5 * total number of views in
    // Launcher. This allows the first free frames avoid object allocation during view capture.
    protected static final int DEFAULT_INIT_POOL_SIZE = 300;

    public static final LooperExecutor MAIN_EXECUTOR = new LooperExecutor(Looper.getMainLooper());

    private final List<WindowListener> mListeners = new ArrayList<>();

    protected final Executor mBgExecutor;
    private final Choreographer mChoreographer;

    // Pool used for capturing view tree on the UI thread.
    private ViewRef mPool = new ViewRef();
    private boolean mIsEnabled = true;

    protected ViewCapture(int memorySize, int initPoolSize, Choreographer choreographer,
            Executor bgExecutor) {
        mMemorySize = memorySize;
        mChoreographer = choreographer;
        mBgExecutor = bgExecutor;
        mBgExecutor.execute(() -> initPool(initPoolSize));
    }

    public static LooperExecutor createAndStartNewLooperExecutor(String name, int priority) {
        HandlerThread thread = new HandlerThread(name, priority);
        thread.start();
        return new LooperExecutor(thread.getLooper());
    }

    @UiThread
    private void addToPool(ViewRef start, ViewRef end) {
        end.next = mPool;
        mPool = start;
    }

    @WorkerThread
    private void initPool(int initPoolSize) {
        ViewRef start = new ViewRef();
        ViewRef current = start;

        for (int i = 0; i < initPoolSize; i++) {
            current.next = new ViewRef();
            current = current.next;
        }

        ViewRef finalCurrent = current;
        MAIN_EXECUTOR.execute(() -> addToPool(start, finalCurrent));
    }

    /**
     * Attaches the ViewCapture to the provided window and returns a handle to detach the listener
     */
    public SafeCloseable startCapture(Window window) {
        String title = window.getAttributes().getTitle().toString();
        String name = TextUtils.isEmpty(title) ? window.toString() : title;
        return startCapture(window.getDecorView(), name);
    }

    /**
     * Attaches the ViewCapture to the provided window and returns a handle to detach the listener.
     * Verifies that ViewCapture is enabled before actually attaching an onDrawListener.
     */
    public SafeCloseable startCapture(View view, String name) {
        WindowListener listener = new WindowListener(view, name);
        if (mIsEnabled) MAIN_EXECUTOR.execute(listener::attachToRoot);
        mListeners.add(listener);
        return () -> {
            mListeners.remove(listener);
            listener.detachFromRoot();
        };
    }

    @UiThread
    protected void enableOrDisableWindowListeners(boolean isEnabled) {
        mIsEnabled = isEnabled;
        mListeners.forEach(WindowListener::detachFromRoot);
        if (mIsEnabled) mListeners.forEach(WindowListener::attachToRoot);
    }


    /**
     * Dumps all the active view captures
     */
    public void dump(PrintWriter writer, FileDescriptor out, Context context) {
        if (!mIsEnabled) {
            return;
        }
        ViewIdProvider idProvider = new ViewIdProvider(context.getResources());

        // Collect all the tasks first so that all the tasks are posted on the executor
        List<Pair<String, FutureTask<ExportedData>>> tasks = mListeners.stream()
                .map(l -> {
                    FutureTask<ExportedData> task =
                            new FutureTask<ExportedData>(() -> l.dumpToProto(idProvider));
                    mBgExecutor.execute(task);
                    return Pair.create(l.name, task);
                })
                .collect(toList());
        tasks.forEach(pair -> {
            writer.println();
            writer.println(" ContinuousViewCapture:");
            writer.println(" window " + pair.first + ":");
            writer.println("  pkg:" + context.getPackageName());
            writer.print("  data:");
            writer.flush();
            try (OutputStream os = new FileOutputStream(out)) {
                ExportedData data = pair.second.get();
                OutputStream encodedOS = new GZIPOutputStream(new Base64OutputStream(os,
                        Base64.NO_CLOSE | Base64.NO_PADDING | Base64.NO_WRAP));
                data.writeTo(encodedOS);
                encodedOS.close();
                os.flush();
            } catch (Exception e) {
                Log.e(TAG, "Error capturing proto", e);
            }
            writer.println();
            writer.println("--end--");
        });
    }

    public Optional<FutureTask<ExportedData>> getDumpTask(View view) {
        Context context = view.getContext().getApplicationContext();
        ViewIdProvider idProvider = new ViewIdProvider(context.getResources());

        return mListeners.stream()
                .filter(l -> l.mRoot.equals(view))
                .map(l -> {
                    FutureTask<ExportedData> task =
                            new FutureTask<ExportedData>(() -> l.dumpToProto(idProvider));
                    mBgExecutor.execute(task);
                    return task;
                })
                .findFirst();
    }

    /**
     * Once this window listener is attached to a window's root view, it traverses the entire
     * view tree on the main thread every time onDraw is called. It then saves the state of the view
     * tree traversed in a local list of nodes, so that this list of nodes can be processed on a
     * background thread, and prepared for being dumped into a bugreport.
     *
     * Since some of the work needs to be done on the main thread after every draw, this piece of
     * code needs to be hyper optimized. That is why we are recycling ViewRef and ViewPropertyRef
     * objects and storing the list of nodes as a flat LinkedList, rather than as a tree. This data
     * structure allows recycling to happen in O(1) time via pointer assignment. Without this
     * optimization, a lot of time is wasted creating ViewRef objects, or finding ViewRef objects to
     * recycle.
     *
     * Another optimization is to only traverse view nodes on the main thread that have potentially
     * changed since the last frame was drawn. This can be determined via a combination of private
     * flags inside the View class.
     *
     * Another optimization is to not store or manipulate any string objects on the main thread.
     * While this might seem trivial, using Strings in any form causes the ViewCapture to hog the
     * main thread for up to an additional 6-7ms. It must be avoided at all costs.
     *
     * Another optimization is to only store the class names of the Views in the view hierarchy one
     * time. They are then referenced via a classNameIndex value stored in each ViewPropertyRef.
     *
     * TODO: b/262585897: If further memory optimization is required, an effective one would be to
     * only store the changes between frames, rather than the entire node tree for each frame.
     * The go/web-hv UX already does this, and has reaped significant memory improves because of it.
     *
     * TODO: b/262585897: Another memory optimization could be to store all integer, float, and
     * boolean information via single integer values via the Chinese remainder theorem, or a similar
     * algorithm, which enables multiple numerical values to be stored inside 1 number. Doing this
     * would allow each ViewProperty / ViewRef to slim down its memory footprint significantly.
     *
     * One important thing to remember is that bugs related to recycling will usually only appear
     * after at least 2000 frames have been rendered. If that code is changed, the tester can
     * use hard-coded logs to verify that recycling is happening, and test view capturing at least
     * ~8000 frames or so to verify the recycling functionality is working properly.
     */
    private class WindowListener implements ViewTreeObserver.OnDrawListener {

        public final View mRoot;
        public final String name;

        private final ViewRef mViewRef = new ViewRef();

        private int mFrameIndexBg = -1;
        private boolean mIsFirstFrame = true;
        private final long[] mFrameTimesNanosBg = new long[mMemorySize];
        private final ViewPropertyRef[] mNodesBg = new ViewPropertyRef[mMemorySize];

        private boolean mIsActive = true;
        private final Consumer<ViewRef> mCaptureCallback = this::captureViewPropertiesBg;

        WindowListener(View view, String name) {
            mRoot = view;
            this.name = name;
        }

        /**
         * Every time onDraw is called, it does the minimal set of work required on the main thread,
         * i.e. capturing potentially dirty / invalidated views, and then immediately offloads the
         * rest of the processing work (extracting the captured view properties) to a background
         * thread via mExecutor.
         */
        @Override
        public void onDraw() {
            Trace.beginSection("view_capture");
            captureViewTree(mRoot, mViewRef);
            ViewRef captured = mViewRef.next;
            if (captured != null) {
                captured.callback = mCaptureCallback;
                captured.choreographerTimeNanos = mChoreographer.getFrameTimeNanos();
                mBgExecutor.execute(captured);
            }
            mIsFirstFrame = false;
            Trace.endSection();
        }

        /**
         * Captures the View property on the background thread, and transfer all the ViewRef objects
         * back to the pool
         */
        @WorkerThread
        private void captureViewPropertiesBg(ViewRef viewRefStart) {
            long choreographerTimeNanos = viewRefStart.choreographerTimeNanos;
            mFrameIndexBg++;
            if (mFrameIndexBg >= mMemorySize) {
                mFrameIndexBg = 0;
            }
            mFrameTimesNanosBg[mFrameIndexBg] = choreographerTimeNanos;

            ViewPropertyRef recycle = mNodesBg[mFrameIndexBg];

            ViewPropertyRef resultStart = null;
            ViewPropertyRef resultEnd = null;

            ViewRef viewRefEnd = viewRefStart;
            while (viewRefEnd != null) {
                ViewPropertyRef propertyRef = recycle;
                if (propertyRef == null) {
                    propertyRef = new ViewPropertyRef();
                } else {
                    recycle = recycle.next;
                    propertyRef.next = null;
                }

                ViewPropertyRef copy = null;
                if (viewRefEnd.childCount < 0) {
                    copy = findInLastFrame(viewRefEnd.view.hashCode());
                    viewRefEnd.childCount = (copy != null) ? copy.childCount : 0;
                }
                viewRefEnd.transferTo(propertyRef);

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

                if (viewRefEnd.next == null) {
                    // The compiler will complain about using a non-final variable from
                    // an outer class in a lambda if we pass in viewRefEnd directly.
                    final ViewRef finalViewRefEnd = viewRefEnd;
                    MAIN_EXECUTOR.execute(() -> addToPool(viewRefStart, finalViewRefEnd));
                    break;
                }
                viewRefEnd = viewRefEnd.next;
            }
            mNodesBg[mFrameIndexBg] = resultStart;
        }

        private @Nullable ViewPropertyRef findInLastFrame(int hashCode) {
            int lastFrameIndex = (mFrameIndexBg == 0) ? mMemorySize - 1 : mFrameIndexBg - 1;
            ViewPropertyRef viewPropertyRef = mNodesBg[lastFrameIndex];
            while (viewPropertyRef != null && viewPropertyRef.hashCode != hashCode) {
                viewPropertyRef = viewPropertyRef.next;
            }
            return viewPropertyRef;
        }

        void attachToRoot() {
            mIsActive = true;
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
        }

        void detachFromRoot() {
            mIsActive = false;
            mRoot.getViewTreeObserver().removeOnDrawListener(this);
        }

        private void safelyEnableOnDrawListener() {
            mRoot.getViewTreeObserver().removeOnDrawListener(this);
            mRoot.getViewTreeObserver().addOnDrawListener(this);
        }

        @WorkerThread
        private ExportedData dumpToProto(ViewIdProvider idProvider) {
            int size = (mNodesBg[mMemorySize - 1] == null) ? mFrameIndexBg + 1 : mMemorySize;
            ExportedData.Builder exportedDataBuilder = ExportedData.newBuilder();
            ArrayList<Class> classList = new ArrayList<>();

            for (int i = size - 1; i >= 0; i--) {
                int index = (mMemorySize + mFrameIndexBg - i) % mMemorySize;
                ViewNode.Builder nodeBuilder = ViewNode.newBuilder();
                mNodesBg[index].toProto(idProvider, classList, nodeBuilder);
                FrameData.Builder frameDataBuilder = FrameData.newBuilder()
                        .setNode(nodeBuilder)
                        .setTimestamp(mFrameTimesNanosBg[index]);
                exportedDataBuilder.addFrameData(frameDataBuilder);
            }
            return exportedDataBuilder
                    .addAllClassname(classList.stream().map(Class::getName).collect(toList()))
                    .build();
        }

        private ViewRef captureViewTree(View view, ViewRef start) {
            ViewRef ref;
            if (mPool != null) {
                ref = mPool;
                mPool = mPool.next;
                ref.next = null;
            } else {
                ref = new ViewRef();
            }
            ref.view = view;
            start.next = ref;
            if (view instanceof ViewGroup) {
                ViewGroup parent = (ViewGroup) view;
                // If a view has not changed since the last frame, we will copy
                // its children from the last processed frame's data.
                if ((view.mPrivateFlags & (PFLAG_INVALIDATED | PFLAG_DIRTY_MASK)) == 0
                        && !mIsFirstFrame) {
                    // A negative child count is the signal to copy this view from the last frame.
                    ref.childCount = -parent.getChildCount();
                    return ref;
                }
                ViewRef result = ref;
                int childCount = ref.childCount = parent.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    result = captureViewTree(parent.getChildAt(i), result);
                }
                return result;
            } else {
                ref.childCount = 0;
                return ref;
            }
        }
    }

    private static class ViewPropertyRef {
        // We store reference in memory to avoid generating and storing too many strings
        public Class clazz;
        public int hashCode;
        public int childCount = 0;

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

        public ViewPropertyRef next;

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
    }


    private static class ViewRef implements Runnable {
        public View view;
        public int childCount = 0;
        public ViewRef next;

        public Consumer<ViewRef> callback = null;
        public long choreographerTimeNanos = 0;

        public void transferTo(ViewPropertyRef out) {
            out.childCount = this.childCount;

            View view = this.view;
            this.view = null;

            out.clazz = view.getClass();
            out.hashCode = view.hashCode();
            out.id = view.getId();
            out.left = view.getLeft();
            out.top = view.getTop();
            out.right = view.getRight();
            out.bottom = view.getBottom();
            out.scrollX = view.getScrollX();
            out.scrollY = view.getScrollY();

            out.translateX = view.getTranslationX();
            out.translateY = view.getTranslationY();
            out.scaleX = view.getScaleX();
            out.scaleY = view.getScaleY();
            out.alpha = view.getAlpha();
            out.elevation = view.getElevation();

            out.visibility = view.getVisibility();
            out.willNotDraw = view.willNotDraw();
        }

        @Override
        public void run() {
            Consumer<ViewRef> oldCallback = callback;
            callback = null;
            if (oldCallback != null) {
                oldCallback.accept(this);
            }
        }
    }

    private static final class ViewIdProvider {

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
