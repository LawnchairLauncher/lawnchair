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
package com.android.quickstep.util;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.Executors.createAndStartNewLooper;

import static java.util.stream.Collectors.toList;

import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.Trace;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnDrawListener;
import android.view.Window;

import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.LooperExecutor;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.view.ViewCaptureData.ExportedData;
import com.android.launcher3.view.ViewCaptureData.FrameData;
import com.android.launcher3.view.ViewCaptureData.ViewNode;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.zip.GZIPOutputStream;

/**
 * Utility class for capturing view data every frame
 */
public class ViewCapture {

    private static final String TAG = "ViewCapture";

    // These flags are copies of two private flags in the View class.
    private static final int PFLAG_INVALIDATED = 0x80000000;
    private static final int PFLAG_DIRTY_MASK = 0x00200000;

    // Number of frames to keep in memory
    private static final int MEMORY_SIZE = 2000;
    // Initial size of the reference pool. This is at least be 5 * total number of views in
    // Launcher. This allows the first free frames avoid object allocation during view capture.
    private static final int INIT_POOL_SIZE = 300;

    public static final MainThreadInitializedObject<ViewCapture> INSTANCE =
            new MainThreadInitializedObject<>(ViewCapture::new);

    private final List<WindowListener> mListeners = new ArrayList<>();

    private final Context mContext;
    private final LooperExecutor mExecutor;

    // Pool used for capturing view tree on the UI thread.
    private ViewRef mPool = new ViewRef();

    private ViewCapture(Context context) {
        mContext = context;
        if (FeatureFlags.CONTINUOUS_VIEW_TREE_CAPTURE.get()) {
            Looper looper = createAndStartNewLooper("ViewCapture",
                    Process.THREAD_PRIORITY_FOREGROUND);
            mExecutor = new LooperExecutor(looper);
            mExecutor.execute(this::initPool);
        } else {
            mExecutor = UI_HELPER_EXECUTOR;
        }
    }

    @UiThread
    private void addToPool(ViewRef start, ViewRef end) {
        end.next = mPool;
        mPool = start;
    }

    @WorkerThread
    private void initPool() {
        ViewRef start = new ViewRef();
        ViewRef current = start;

        for (int i = 0; i < INIT_POOL_SIZE; i++) {
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
     * Attaches the ViewCapture to the provided window and returns a handle to detach the listener
     */
    public SafeCloseable startCapture(View view, String name) {
        if (!FeatureFlags.CONTINUOUS_VIEW_TREE_CAPTURE.get()) {
            return () -> { };
        }

        WindowListener listener = new WindowListener(view, name);
        mExecutor.execute(() -> MAIN_EXECUTOR.execute(listener::attachToRoot));
        mListeners.add(listener);
        return () -> {
            mListeners.remove(listener);
            listener.destroy();
        };
    }

    /**
     * Dumps all the active view captures
     */
    public void dump(PrintWriter writer, FileDescriptor out) {
        if (!FeatureFlags.CONTINUOUS_VIEW_TREE_CAPTURE.get()) {
            return;
        }
        ViewIdProvider idProvider = new ViewIdProvider(mContext.getResources());

        // Collect all the tasks first so that all the tasks are posted on the executor
        List<Pair<String, Future<ExportedData>>> tasks = mListeners.stream()
                .map(l -> Pair.create(l.name, mExecutor.submit(() -> l.dumpToProto(idProvider))))
                .collect(toList());

        tasks.forEach(pair -> {
            writer.println();
            writer.println(" ContinuousViewCapture:");
            writer.println(" window " + pair.first + ":");
            writer.println("  pkg:" + mContext.getPackageName());
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

    private class WindowListener implements OnDrawListener {

        private final View mRoot;
        public final String name;

        private final Handler mHandler;
        private final ViewRef mViewRef = new ViewRef();

        private int mFrameIndexBg = -1;
        private boolean mIsFirstFrame = true;
        private final long[] mFrameTimesBg = new long[MEMORY_SIZE];
        private final ViewPropertyRef[] mNodesBg = new ViewPropertyRef[MEMORY_SIZE];

        private boolean mDestroyed = false;

        WindowListener(View view, String name) {
            mRoot = view;
            this.name = name;
            mHandler = new Handler(mExecutor.getLooper(), this::captureViewPropertiesBg);
        }

        @Override
        public void onDraw() {
            Trace.beginSection("view_capture");
            captureViewTree(mRoot, mViewRef);
            Message m = Message.obtain(mHandler);
            m.obj = mViewRef.next;
            mHandler.sendMessage(m);
            mIsFirstFrame = false;
            Trace.endSection();
        }

        /**
         * Captures the View property on the background thread, and transfer all the ViewRef objects
         * back to the pool
         */
        @WorkerThread
        private boolean captureViewPropertiesBg(Message msg) {
            ViewRef viewRefStart = (ViewRef) msg.obj;
            long time = msg.getWhen();
            if (viewRefStart == null) {
                return false;
            }
            mFrameIndexBg++;
            if (mFrameIndexBg >= MEMORY_SIZE) {
                mFrameIndexBg = 0;
            }
            mFrameTimesBg[mFrameIndexBg] = time;

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
            return true;
        }

        private ViewPropertyRef findInLastFrame(int hashCode) {
            int lastFrameIndex = (mFrameIndexBg == 0) ? MEMORY_SIZE - 1 : mFrameIndexBg - 1;
            ViewPropertyRef viewPropertyRef = mNodesBg[lastFrameIndex];
            while (viewPropertyRef != null && viewPropertyRef.hashCode != hashCode) {
                viewPropertyRef = viewPropertyRef.next;
            }
            return viewPropertyRef;
        }

        void attachToRoot() {
            if (mRoot.isAttachedToWindow()) {
                mRoot.getViewTreeObserver().addOnDrawListener(this);
            } else {
                mRoot.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View v) {
                        if (!mDestroyed) {
                            mRoot.getViewTreeObserver().addOnDrawListener(WindowListener.this);
                        }
                        mRoot.removeOnAttachStateChangeListener(this);
                    }

                    @Override
                    public void onViewDetachedFromWindow(View v) { }
                });
            }
        }

        void destroy() {
            mRoot.getViewTreeObserver().removeOnDrawListener(this);
            mDestroyed = true;
        }

        @WorkerThread
        private ExportedData dumpToProto(ViewIdProvider idProvider) {
            ExportedData.Builder dataBuilder = ExportedData.newBuilder();
            ArrayList<Class> classList = new ArrayList<>();

            int size = (mNodesBg[MEMORY_SIZE - 1] == null) ? mFrameIndexBg + 1 : MEMORY_SIZE;
            for (int i = size - 1; i >= 0; i--) {
                int index = (MEMORY_SIZE + mFrameIndexBg - i) % MEMORY_SIZE;
                ViewNode.Builder nodeBuilder = ViewNode.newBuilder();
                mNodesBg[index].toProto(idProvider, classList, nodeBuilder);
                dataBuilder.addFrameData(FrameData.newBuilder()
                        .setNode(nodeBuilder)
                        .setTimestamp(mFrameTimesBg[index]));
            }
            return dataBuilder
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
            out.next = this.next;
            out.elevation = this.elevation;
        }

        /**
         * Converts the data to the proto representation and returns the next property ref
         * at the end of the iteration.
         * @return
         */
        public ViewPropertyRef toProto(ViewIdProvider idProvider, ArrayList<Class> classList,
                ViewNode.Builder outBuilder) {
            int classnameIndex = classList.indexOf(clazz);
            if (classnameIndex < 0) {
                classnameIndex = classList.size();
                classList.add(clazz);
            }
            outBuilder
                    .setClassnameIndex(classnameIndex)
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
                ViewNode.Builder childBuilder = ViewNode.newBuilder();
                result = result.toProto(idProvider, classList, childBuilder);
                outBuilder.addChildren(childBuilder);
            }
            return result;
        }
    }

    private static class ViewRef {
        public View view;
        public int childCount = 0;
        public ViewRef next;

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
