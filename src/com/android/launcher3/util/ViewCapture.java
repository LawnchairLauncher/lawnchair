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
package com.android.launcher3.util;

import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.content.res.Resources;
import android.os.Handler;
import android.os.Message;
import android.os.Trace;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnDrawListener;

import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import com.android.launcher3.view.ViewCaptureData.ExportedData;
import com.android.launcher3.view.ViewCaptureData.FrameData;
import com.android.launcher3.view.ViewCaptureData.ViewNode;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.concurrent.Future;

/**
 * Utility class for capturing view data every frame
 */
public class ViewCapture implements OnDrawListener {

    private static final String TAG = "ViewCapture";

    // Number of frames to keep in memory
    private static final int MEMORY_SIZE = 2000;
    // Initial size of the reference pool. This is at least be 5 * total number of views in
    // Launcher. This allows the first free frames avoid object allocation during view capture.
    private static final int INIT_POOL_SIZE = 300;

    private final View mRoot;
    private final Resources mResources;

    private final Handler mHandler;
    private final ViewRef mViewRef = new ViewRef();

    private int mFrameIndexBg = -1;
    private final long[] mFrameTimesBg = new long[MEMORY_SIZE];
    private final ViewPropertyRef[] mNodesBg = new ViewPropertyRef[MEMORY_SIZE];

    // Pool used for capturing view tree on the UI thread.
    private ViewRef mPool = new ViewRef();

    /**
     * @param root the root view for the capture data
     */
    public ViewCapture(View root) {
        mRoot = root;
        mResources = root.getResources();
        mHandler = new Handler(UI_HELPER_EXECUTOR.getLooper(), this::captureViewPropertiesBg);
    }

    /**
     * Attaches the ViewCapture to the root
     */
    public void attach() {
        mHandler.post(this::initPool);
    }

    @Override
    public void onDraw() {
        Trace.beginSection("view_capture");
        captureViewTree(mRoot, mViewRef);
        Message m = Message.obtain(mHandler);
        m.obj = mViewRef.next;
        mHandler.sendMessage(m);
        Trace.endSection();
    }

    /**
     * Captures the View property on the background thread, and transfer all the ViewRef objects
     * back to the pool
     */
    @WorkerThread
    private boolean captureViewPropertiesBg(Message msg) {
        ViewRef start = (ViewRef) msg.obj;
        long time = msg.getWhen();
        if (start == null) {
            return false;
        }
        mFrameIndexBg++;
        if (mFrameIndexBg >= MEMORY_SIZE) {
            mFrameIndexBg = 0;
        }
        mFrameTimesBg[mFrameIndexBg] = time;

        ViewPropertyRef recycle = mNodesBg[mFrameIndexBg];

        ViewPropertyRef result = null;
        ViewPropertyRef resultEnd = null;

        ViewRef current = start;
        ViewRef last = start;
        while (current != null) {
            ViewPropertyRef propertyRef = recycle;
            if (propertyRef == null) {
                propertyRef = new ViewPropertyRef();
            } else {
                recycle = recycle.next;
                propertyRef.next = null;
            }

            propertyRef.transfer(current);
            last = current;
            current = current.next;

            if (result == null) {
                result = propertyRef;
                resultEnd = result;
            } else {
                resultEnd.next = propertyRef;
                resultEnd = propertyRef;
            }
        }
        mNodesBg[mFrameIndexBg] = result;
        ViewRef end = last;
        Executors.MAIN_EXECUTOR.execute(() -> addToPool(start, end));
        return true;
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

        ViewRef end = current;
        Executors.MAIN_EXECUTOR.execute(() ->  {
            addToPool(start, end);
            if (mRoot.isAttachedToWindow()) {
                mRoot.getViewTreeObserver().addOnDrawListener(this);
            }
        });
    }

    /**
     * Creates a proto of all the data captured so far.
     */
    public void dump(FileDescriptor out) {
        Future<ExportedData> task = UI_HELPER_EXECUTOR.submit(this::dumpToProto);
        try (OutputStream os = new FileOutputStream(out)) {
            ExportedData data = task.get();
            Base64OutputStream encodedOS = new Base64OutputStream(os,
                    Base64.NO_CLOSE | Base64.NO_PADDING | Base64.NO_WRAP);
            data.writeTo(encodedOS);
            encodedOS.close();
            os.flush();
        } catch (Exception e) {
            Log.e(TAG, "Error capturing proto", e);
        }
    }

    @WorkerThread
    private ExportedData dumpToProto() {
        ExportedData.Builder dataBuilder = ExportedData.newBuilder();
        Resources res = mResources;

        int size = (mNodesBg[MEMORY_SIZE - 1] == null) ? mFrameIndexBg + 1 : MEMORY_SIZE;
        for (int i = size - 1; i >= 0; i--) {
            int index = (MEMORY_SIZE + mFrameIndexBg - i) % MEMORY_SIZE;
            ViewNode.Builder nodeBuilder = ViewNode.newBuilder();
            mNodesBg[index].toProto(res, nodeBuilder);
            dataBuilder.addFrameData(FrameData.newBuilder()
                    .setNode(nodeBuilder)
                    .setTimestamp(mFrameTimesBg[index]));
        }
        return dataBuilder.build();
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
            ViewRef result = ref;
            ViewGroup parent = (ViewGroup) view;
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

        public void transfer(ViewRef viewRef) {
            childCount = viewRef.childCount;

            View view = viewRef.view;
            viewRef.view = null;

            clazz = view.getClass();
            hashCode = view.hashCode();
            id = view.getId();
            left = view.getLeft();
            top = view.getTop();
            right = view.getRight();
            bottom = view.getBottom();
            scrollX = view.getScrollX();
            scrollY = view.getScrollY();

            translateX = view.getTranslationX();
            translateY = view.getTranslationY();
            scaleX = view.getScaleX();
            scaleY = view.getScaleY();
            alpha = view.getAlpha();

            visibility = view.getVisibility();
            willNotDraw = view.willNotDraw();
            elevation = view.getElevation();
        }

        /**
         * Converts the data to the proto representation and returns the next property ref
         * at the end of the iteration.
         * @param res
         * @return
         */
        public ViewPropertyRef toProto(Resources res, ViewNode.Builder outBuilder) {
            String resolvedId;
            if (id >= 0) {
                try {
                    resolvedId = res.getResourceTypeName(id) + '/' + res.getResourceEntryName(id);
                } catch (Resources.NotFoundException e) {
                    resolvedId = "id/" + "0x" + Integer.toHexString(id).toUpperCase();
                }
            } else {
                resolvedId = "NO_ID";
            }
            outBuilder.setClassname(clazz.getName() + "@" + hashCode)
                    .setId(resolvedId)
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
                result = result.toProto(res, childBuilder);
                outBuilder.addChildren(childBuilder);
            }
            return result;
        }
    }

    private static class ViewRef {
        public View view;
        public int childCount = 0;
        public ViewRef next;
    }
}
