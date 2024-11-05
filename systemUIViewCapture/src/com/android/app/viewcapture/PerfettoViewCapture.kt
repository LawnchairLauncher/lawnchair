/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.app.viewcapture

import android.content.Context
import android.internal.perfetto.protos.InternedDataOuterClass.InternedData
import android.internal.perfetto.protos.ProfileCommon.InternedString
import android.internal.perfetto.protos.TracePacketOuterClass.TracePacket
import android.internal.perfetto.protos.Viewcapture.ViewCapture as ViewCaptureMessage
import android.internal.perfetto.protos.WinscopeExtensionsImplOuterClass.WinscopeExtensionsImpl
import android.os.Trace
import android.tracing.perfetto.DataSourceParams
import android.tracing.perfetto.InitArguments
import android.tracing.perfetto.Producer
import android.util.proto.ProtoOutputStream
import androidx.annotation.WorkerThread
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

/**
 * ViewCapture that listens to Perfetto events (OnStart, OnStop, OnFlush) and continuously writes
 * captured frames to the Perfetto service (traced).
 */
internal class PerfettoViewCapture
internal constructor(private val context: Context, executor: Executor) :
    ViewCapture(RING_BUFFER_SIZE, DEFAULT_INIT_POOL_SIZE, executor) {

    private val mDataSource = ViewCaptureDataSource({ onStart() }, {}, { onStop() })

    private val mActiveSessions = AtomicInteger(0)

    private val mViewIdProvider = ViewIdProvider(context.getResources())

    private var mSerializationCurrentId: Int = 0
    private var mSerializationCurrentView: ViewPropertyRef? = null

    inner class NewInternedStrings {
        val packageNames = mutableListOf<String>()
        val windowNames = mutableListOf<String>()
        val viewIds = mutableListOf<String>()
        val classNames = mutableListOf<String>()
    }

    init {
        enableOrDisableWindowListeners(false)

        Producer.init(InitArguments.DEFAULTS)

        val dataSourceParams =
            DataSourceParams.Builder()
                .setBufferExhaustedPolicy(
                    DataSourceParams.PERFETTO_DS_BUFFER_EXHAUSTED_POLICY_STALL_AND_ABORT
                )
                .setNoFlush(true)
                .setWillNotifyOnStop(false)
                .build()
        mDataSource.register(dataSourceParams)
    }

    fun onStart() {
        if (mActiveSessions.incrementAndGet() == 1) {
            enableOrDisableWindowListeners(true)
        }
    }

    fun onStop() {
        if (mActiveSessions.decrementAndGet() == 0) {
            enableOrDisableWindowListeners(false)
        }
    }

    @WorkerThread
    override fun onCapturedViewPropertiesBg(
        elapsedRealtimeNanos: Long,
        windowName: String,
        startFlattenedTree: ViewPropertyRef
    ) {
        Trace.beginSection("vc#onCapturedViewPropertiesBg")

        mDataSource.trace { ctx ->
            val newInternedStrings = NewInternedStrings()
            val os = ctx.newTracePacket()
            os.write(TracePacket.TIMESTAMP, elapsedRealtimeNanos)
            serializeViews(
                os,
                windowName,
                startFlattenedTree,
                ctx.incrementalState,
                newInternedStrings
            )
            serializeIncrementalState(os, ctx.incrementalState, newInternedStrings)
        }

        Trace.endSection()
    }

    private fun serializeViews(
        os: ProtoOutputStream,
        windowName: String,
        startFlattenedTree: ViewPropertyRef,
        incrementalState: ViewCaptureDataSource.IncrementalState,
        newInternedStrings: NewInternedStrings
    ) {
        mSerializationCurrentView = startFlattenedTree
        mSerializationCurrentId = 0

        val tokenExtensions = os.start(TracePacket.WINSCOPE_EXTENSIONS)
        val tokenViewCapture = os.start(WinscopeExtensionsImpl.VIEWCAPTURE)
        os.write(
            ViewCaptureMessage.PACKAGE_NAME_IID,
            internPackageName(context.packageName, incrementalState, newInternedStrings)
        )
        os.write(
            ViewCaptureMessage.WINDOW_NAME_IID,
            internWindowName(windowName, incrementalState, newInternedStrings)
        )
        serializeViewsRec(os, -1, incrementalState, newInternedStrings)
        os.end(tokenViewCapture)
        os.end(tokenExtensions)
    }

    private fun serializeViewsRec(
        os: ProtoOutputStream,
        parentId: Int,
        incrementalState: ViewCaptureDataSource.IncrementalState,
        newInternedStrings: NewInternedStrings
    ) {
        if (mSerializationCurrentView == null) {
            return
        }

        val id = mSerializationCurrentId
        val childCount = mSerializationCurrentView!!.childCount

        serializeView(
            os,
            mSerializationCurrentView!!,
            mSerializationCurrentId,
            parentId,
            incrementalState,
            newInternedStrings
        )

        ++mSerializationCurrentId
        mSerializationCurrentView = mSerializationCurrentView!!.next

        for (i in 0..childCount - 1) {
            serializeViewsRec(os, id, incrementalState, newInternedStrings)
        }
    }

    private fun serializeView(
        os: ProtoOutputStream,
        view: ViewPropertyRef,
        id: Int,
        parentId: Int,
        incrementalState: ViewCaptureDataSource.IncrementalState,
        newInternedStrings: NewInternedStrings
    ) {
        val token = os.start(ViewCaptureMessage.VIEWS)

        os.write(ViewCaptureMessage.View.ID, id)
        os.write(ViewCaptureMessage.View.PARENT_ID, parentId)
        os.write(ViewCaptureMessage.View.HASHCODE, view.hashCode)
        os.write(
            ViewCaptureMessage.View.VIEW_ID_IID,
            internViewId(mViewIdProvider.getName(view.id), incrementalState, newInternedStrings)
        )
        os.write(
            ViewCaptureMessage.View.CLASS_NAME_IID,
            internClassName(view.clazz.name, incrementalState, newInternedStrings)
        )

        os.write(ViewCaptureMessage.View.LEFT, view.left)
        os.write(ViewCaptureMessage.View.TOP, view.top)
        os.write(ViewCaptureMessage.View.WIDTH, view.right - view.left)
        os.write(ViewCaptureMessage.View.HEIGHT, view.bottom - view.top)
        os.write(ViewCaptureMessage.View.SCROLL_X, view.scrollX)
        os.write(ViewCaptureMessage.View.SCROLL_Y, view.scrollY)

        os.write(ViewCaptureMessage.View.TRANSLATION_X, view.translateX)
        os.write(ViewCaptureMessage.View.TRANSLATION_Y, view.translateY)
        os.write(ViewCaptureMessage.View.SCALE_X, view.scaleX)
        os.write(ViewCaptureMessage.View.SCALE_Y, view.scaleY)
        os.write(ViewCaptureMessage.View.ALPHA, view.alpha)

        os.write(ViewCaptureMessage.View.WILL_NOT_DRAW, view.willNotDraw)
        os.write(ViewCaptureMessage.View.CLIP_CHILDREN, view.clipChildren)
        os.write(ViewCaptureMessage.View.VISIBILITY, view.visibility)

        os.write(ViewCaptureMessage.View.ELEVATION, view.elevation)

        os.end(token)
    }

    private fun internClassName(
        string: String,
        incrementalState: ViewCaptureDataSource.IncrementalState,
        newInternedStrings: NewInternedStrings
    ): Int {
        return internString(
            string,
            incrementalState.mInternMapClassName,
            newInternedStrings.classNames
        )
    }

    private fun internPackageName(
        string: String,
        incrementalState: ViewCaptureDataSource.IncrementalState,
        newInternedStrings: NewInternedStrings
    ): Int {
        return internString(
            string,
            incrementalState.mInternMapPackageName,
            newInternedStrings.packageNames
        )
    }

    private fun internViewId(
        string: String,
        incrementalState: ViewCaptureDataSource.IncrementalState,
        newInternedStrings: NewInternedStrings
    ): Int {
        return internString(string, incrementalState.mInternMapViewId, newInternedStrings.viewIds)
    }

    private fun internWindowName(
        string: String,
        incrementalState: ViewCaptureDataSource.IncrementalState,
        newInternedStrings: NewInternedStrings
    ): Int {
        return internString(
            string,
            incrementalState.mInternMapWindowName,
            newInternedStrings.windowNames
        )
    }

    private fun internString(
        string: String,
        internMap: MutableMap<String, Int>,
        newInternedStrings: MutableList<String>
    ): Int {
        if (internMap.containsKey(string)) {
            return internMap[string]!!
        }

        // +1 to avoid intern ID = 0, because javastream optimizes out zero values
        // and the perfetto trace processor would not de-intern that string.
        val internId = internMap.size + 1

        internMap.put(string, internId)
        newInternedStrings.add(string)
        return internId
    }

    private fun serializeIncrementalState(
        os: ProtoOutputStream,
        incrementalState: ViewCaptureDataSource.IncrementalState,
        newInternedStrings: NewInternedStrings
    ) {
        var flags = TracePacket.SEQ_NEEDS_INCREMENTAL_STATE
        if (!incrementalState.mHasNotifiedClearedState) {
            flags = flags or TracePacket.SEQ_INCREMENTAL_STATE_CLEARED
            incrementalState.mHasNotifiedClearedState = true
        }
        os.write(TracePacket.SEQUENCE_FLAGS, flags)

        val token = os.start(TracePacket.INTERNED_DATA)
        serializeInternMap(
            os,
            InternedData.VIEWCAPTURE_CLASS_NAME,
            incrementalState.mInternMapClassName,
            newInternedStrings.classNames
        )
        serializeInternMap(
            os,
            InternedData.VIEWCAPTURE_PACKAGE_NAME,
            incrementalState.mInternMapPackageName,
            newInternedStrings.packageNames
        )
        serializeInternMap(
            os,
            InternedData.VIEWCAPTURE_VIEW_ID,
            incrementalState.mInternMapViewId,
            newInternedStrings.viewIds
        )
        serializeInternMap(
            os,
            InternedData.VIEWCAPTURE_WINDOW_NAME,
            incrementalState.mInternMapWindowName,
            newInternedStrings.windowNames
        )
        os.end(token)
    }

    private fun serializeInternMap(
        os: ProtoOutputStream,
        fieldId: Long,
        map: Map<String, Int>,
        newInternedStrings: List<String>
    ) {
        if (newInternedStrings.isEmpty()) {
            return
        }

        var currentInternId = map.size - newInternedStrings.size + 1
        for (internedString in newInternedStrings) {
            val token = os.start(fieldId)
            os.write(InternedString.IID, currentInternId++)
            os.write(InternedString.STR, internedString.toByteArray())
            os.end(token)
        }
    }

    companion object {
        // Keep two frames in the base class' ring buffer.
        // This is the minimum required by the current implementation to work.
        private val RING_BUFFER_SIZE = 2
    }
}
