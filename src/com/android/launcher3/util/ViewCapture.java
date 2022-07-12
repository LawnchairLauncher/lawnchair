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

import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Trace;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnDrawListener;

import androidx.annotation.UiThread;

import com.android.launcher3.view.ViewCaptureData.ExportedData;
import com.android.launcher3.view.ViewCaptureData.FrameData;
import com.android.launcher3.view.ViewCaptureData.ViewNode;

import java.util.concurrent.FutureTask;

/**
 * Utility class for capturing view data every frame
 */
public class ViewCapture implements OnDrawListener {

    private static final String TAG = "ViewCapture";

    private static final int MEMORY_SIZE = 2000;

    private final View mRoot;
    private final long[] mFrameTimes = new long[MEMORY_SIZE];
    private final Node[] mNodes = new Node[MEMORY_SIZE];

    private int mFrameIndex = -1;

    /**
     * @param root the root view for the capture data
     */
    public ViewCapture(View root) {
        mRoot = root;
    }

    @Override
    public void onDraw() {
        Trace.beginSection("view_capture");
        long now = SystemClock.elapsedRealtimeNanos();

        mFrameIndex++;
        if (mFrameIndex >= MEMORY_SIZE) {
            mFrameIndex = 0;
        }
        mFrameTimes[mFrameIndex] = now;
        mNodes[mFrameIndex] = captureView(mRoot, mNodes[mFrameIndex]);
        Trace.endSection();
    }

    /**
     * Creates a proto of all the data captured so far.
     */
    public String dumpToString() {
        Handler handler = mRoot.getHandler();
        if (handler == null) {
            handler = Executors.MAIN_EXECUTOR.getHandler();
        }
        FutureTask<ExportedData> task = new FutureTask<>(this::dumpToProtoUI);
        if (Looper.myLooper() == handler.getLooper()) {
            task.run();
        } else {
            handler.post(task);
        }
        try {
            return Base64.encodeToString(task.get().toByteArray(),
                    Base64.NO_CLOSE | Base64.NO_PADDING | Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Error capturing proto", e);
            return "--error--";
        }
    }

    @UiThread
    private ExportedData dumpToProtoUI() {
        ExportedData.Builder dataBuilder = ExportedData.newBuilder();
        Resources res = mRoot.getResources();

        int size = (mNodes[MEMORY_SIZE - 1] == null) ? mFrameIndex + 1 : MEMORY_SIZE;
        for (int i = size - 1; i >= 0; i--) {
            int index = (MEMORY_SIZE + mFrameIndex - i) % MEMORY_SIZE;
            dataBuilder.addFrameData(FrameData.newBuilder()
                    .setNode(mNodes[index].toProto(res))
                    .setTimestamp(mFrameTimes[index]));
        }
        return dataBuilder.build();
    }

    private Node captureView(View view, Node recycle) {
        Node result = recycle == null ? new Node() : recycle;

        result.clazz = view.getClass();
        result.hashCode = view.hashCode();
        result.id = view.getId();
        result.left = view.getLeft();
        result.top = view.getTop();
        result.right = view.getRight();
        result.bottom = view.getBottom();
        result.scrollX = view.getScrollX();
        result.scrollY = view.getScrollY();

        result.translateX = view.getTranslationX();
        result.translateY = view.getTranslationY();
        result.scaleX = view.getScaleX();
        result.scaleY = view.getScaleY();
        result.alpha = view.getAlpha();

        result.visibility = view.getVisibility();
        result.willNotDraw = view.willNotDraw();

        if (view instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) view;
            result.clipChildren = parent.getClipChildren();
            int childCount = parent.getChildCount();
            if (childCount == 0) {
                result.children = null;
            } else {
                result.children = captureView(parent.getChildAt(0), result.children);
                Node lastChild = result.children;
                for (int i = 1; i < childCount; i++) {
                    lastChild.sibling = captureView(parent.getChildAt(i), lastChild.sibling);
                    lastChild = lastChild.sibling;
                }
                lastChild.sibling = null;
            }
        } else {
            result.clipChildren = false;
            result.children = null;
        }
        return result;
    }

    private static class Node {

        // We store reference in memory to avoid generating and storing too many strings
        public Class clazz;
        public int hashCode;

        public int id;
        public int left, top, right, bottom;
        public int scrollX, scrollY;

        public float translateX, translateY;
        public float scaleX, scaleY;
        public float alpha;

        public int visibility;
        public boolean willNotDraw;
        public boolean clipChildren;

        public Node sibling;
        public Node children;

        public ViewNode toProto(Resources res) {
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

            ViewNode.Builder result = ViewNode.newBuilder()
                    .setClassname(clazz.getName() + "@" + hashCode)
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
                    .setClipChildren(clipChildren);
            Node child = children;
            while (child != null) {
                result.addChildren(child.toProto(res));
                child = child.sibling;
            }
            return result.build();
        }

    }
}
