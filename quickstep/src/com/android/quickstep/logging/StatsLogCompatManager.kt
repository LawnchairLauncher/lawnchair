/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.quickstep.logging

import android.content.Context
import android.util.Log
import android.util.StatsEvent
import android.view.Surface
import android.view.View
import androidx.annotation.WorkerThread
import androidx.slice.SliceItem
import com.android.internal.jank.Cuj
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.Utilities
import com.android.launcher3.logger.LauncherAtom
import com.android.launcher3.logger.LauncherAtom.ContainerInfo
import com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.ALL_APPS_CONTAINER
import com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.EXTENDED_CONTAINERS
import com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.FOLDER
import com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.HOTSEAT
import com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.PREDICTED_HOTSEAT_CONTAINER
import com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.SEARCH_RESULT_CONTAINER
import com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.TASK_BAR_CONTAINER
import com.android.launcher3.logger.LauncherAtom.FolderContainer.ParentContainerCase
import com.android.launcher3.logger.LauncherAtom.FromState
import com.android.launcher3.logger.LauncherAtom.ItemInfo.ItemCase.APPLICATION
import com.android.launcher3.logger.LauncherAtom.ItemInfo.ItemCase.FOLDER_ICON
import com.android.launcher3.logger.LauncherAtom.ItemInfo.ItemCase.SEARCH_ACTION_ITEM
import com.android.launcher3.logger.LauncherAtom.ItemInfo.ItemCase.SHORTCUT
import com.android.launcher3.logger.LauncherAtom.ItemInfo.ItemCase.SLICE
import com.android.launcher3.logger.LauncherAtom.ItemInfo.ItemCase.TASK
import com.android.launcher3.logger.LauncherAtom.ItemInfo.ItemCase.TASK_VIEW
import com.android.launcher3.logger.LauncherAtom.ItemInfo.ItemCase.WIDGET
import com.android.launcher3.logger.LauncherAtom.LauncherAttributes
import com.android.launcher3.logger.LauncherAtom.Slice
import com.android.launcher3.logger.LauncherAtom.TaskSwitcherContainer.OrientationHandler.LANDSCAPE
import com.android.launcher3.logger.LauncherAtom.TaskSwitcherContainer.OrientationHandler.PORTRAIT
import com.android.launcher3.logger.LauncherAtom.TaskSwitcherContainer.OrientationHandler.SEASCAPE
import com.android.launcher3.logger.LauncherAtom.ToState
import com.android.launcher3.logger.LauncherAtomExtensions.DeviceSearchResultContainer.SearchAttributes
import com.android.launcher3.logger.LauncherAtomExtensions.DeviceSearchResultContainer.SearchAttributes.EntryState.ALL_APPS
import com.android.launcher3.logger.LauncherAtomExtensions.DeviceSearchResultContainer.SearchAttributes.EntryState.OVERVIEW
import com.android.launcher3.logger.LauncherAtomExtensions.DeviceSearchResultContainer.SearchAttributes.EntryState.QSB
import com.android.launcher3.logger.LauncherAtomExtensions.DeviceSearchResultContainer.SearchAttributes.EntryState.TASKBAR
import com.android.launcher3.logger.LauncherAtomExtensions.ExtendedContainers.ContainerCase.DEVICE_SEARCH_RESULT_CONTAINER
import com.android.launcher3.logging.InstanceId
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_VERTICAL_SWIPE_BEGIN
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_VERTICAL_SWIPE_END
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_PRIVATE_SPACE_LOCK_ANIMATION_BEGIN
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_PRIVATE_SPACE_LOCK_ANIMATION_END
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_PRIVATE_SPACE_UNLOCK_ANIMATION_BEGIN
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_PRIVATE_SPACE_UNLOCK_ANIMATION_END
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_WORKSPACE_SNAPSHOT
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_WORK_UTILITY_VIEW_EXPAND_ANIMATION_BEGIN
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_WORK_UTILITY_VIEW_EXPAND_ANIMATION_END
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_WORK_UTILITY_VIEW_SHRINK_ANIMATION_BEGIN
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_WORK_UTILITY_VIEW_SHRINK_ANIMATION_END
import com.android.launcher3.logging.StatsLogManager.StatsImpressionLogger.State
import com.android.launcher3.logging.StatsLogManager.StatsLatencyLogger.LatencyType
import com.android.launcher3.logging.StatsLogManager.StatsLatencyLogger.LatencyType.UNKNOWN
import com.android.launcher3.model.data.CollectionInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.DisplayController
import com.android.launcher3.util.Executors
import com.android.launcher3.util.LogConfig
import com.android.launcher3.views.ActivityContext
import com.android.systemui.shared.system.InteractionJankMonitorWrapper
import com.android.systemui.shared.system.SysUiStatsLog
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject

/**
 * This class calls StatsLog compile time generated methods.
 *
 * To see if the logs are properly sent to statsd, execute following command.
 *
 * $ wwdebug (to turn on the logcat printout) $ wwlogcat (see logcat with grep filter on) $
 * statsd_testdrive (see how ww is writing the proto to statsd buffer)
 */
class StatsLogCompatManager private constructor(context: Context) : StatsLogManager(context) {
    /**
     * This class is purely used to support dagger bindings to be overridden in launcher variants.
     * Very similar to [dagger.assisted.AssistedFactory]. But [dagger.assisted.AssistedFactory]
     * cannot be overridden and this makes dagger binding difficult.
     */
    class StatsLogCompatManagerFactory @Inject internal constructor() : StatsLogManagerFactory() {
        override fun create(context: Context): StatsLogManager {
            return StatsLogCompatManager(context)
        }
    }

    override fun createLogger(): StatsLogger {
        return StatsCompatLogger(mContext, mActivityContext)
    }

    override fun createLatencyLogger(): StatsLatencyLogger {
        return StatsCompatLatencyLogger()
    }

    override fun createImpressionLogger(): StatsImpressionLogger {
        return StatsCompatImpressionLogger()
    }

    /** Helps to construct and write statsd compatible log message. */
    private class StatsCompatLogger(
        private val context: Context,
        private val activityContext: ActivityContext?,
    ) : StatsLogger {

        private var mItemInfo: ItemInfo? = DEFAULT_ITEM_INFO
        private var mInstanceId: InstanceId? = DEFAULT_INSTANCE_ID
        private var mRank: Int? = null
        private var mContainerInfo: ContainerInfo? = null
        private var mSrcState = LAUNCHER_STATE_UNSPECIFIED
        private var mDstState = LAUNCHER_STATE_UNSPECIFIED
        private var mFromState: FromState? = null
        private var mToState: ToState? = null
        private var mEditText: String? = null
        private var mSliceItem: SliceItem? = null
        private var mSlice: Slice? = null
        private var mCardinality: Int? = null
        private var mInputType = SysUiStatsLog.LAUNCHER_UICHANGED__INPUT_TYPE__UNKNOWN
        private var mFeatures: Int? = null
        private var mPackageName: String? = null

        /** Indicates the current rotation of the display. Uses [values.][android.view.Surface] */
        private val mDisplayRotation = DisplayController.INSTANCE[context].info.rotation

        override fun withItemInfo(itemInfo: ItemInfo?) = apply {
            require(mContainerInfo == null) {
                "ItemInfo and ContainerInfo are mutual exclusive; cannot log both."
            }
            mItemInfo = itemInfo
        }

        override fun withInstanceId(instanceId: InstanceId?) = apply { mInstanceId = instanceId }

        override fun withRank(rank: Int) = apply { mRank = rank }

        override fun withSrcState(srcState: Int) = apply { mSrcState = srcState }

        override fun withDstState(dstState: Int) = apply { mDstState = dstState }

        override fun withContainerInfo(containerInfo: ContainerInfo?) = apply {
            require(mItemInfo === DEFAULT_ITEM_INFO) {
                "ItemInfo and ContainerInfo are mutual exclusive; cannot log both."
            }
            this.mContainerInfo = containerInfo
        }

        override fun withFromState(fromState: FromState?) = apply { this.mFromState = fromState }

        override fun withToState(toState: ToState?) = apply { this.mToState = toState }

        override fun withEditText(editText: String?) = apply { this.mEditText = editText }

        override fun withSliceItem(sliceItem: SliceItem) = apply {
            require(mItemInfo === DEFAULT_ITEM_INFO && mSlice == null) {
                "ItemInfo, Slice and SliceItem are mutual exclusive; cannot set more than one of them."
            }
            this.mSliceItem = sliceItem
        }

        override fun withSlice(slice: Slice) = apply {
            require(mItemInfo === DEFAULT_ITEM_INFO && mSliceItem == null) {
                "ItemInfo, Slice and SliceItem are mutual exclusive; cannot set more than one of them."
            }
            this.mSlice = slice
        }

        override fun withCardinality(cardinality: Int) = apply { this.mCardinality = cardinality }

        override fun withInputType(inputType: Int) = apply { this.mInputType = inputType }

        override fun withFeatures(feature: Int) = apply { this.mFeatures = feature }

        override fun withPackageName(packageName: String?) = apply { mPackageName = packageName }

        override fun log(event: EventEnum) {
            if (DEBUG) {
                val name = if (event is Enum<*>) event.name else event.id.toString() + ""
                Log.d(TAG, name)
            }

            if (mSlice == null && mSliceItem != null)
                mSlice = Slice.newBuilder().setUri(mSliceItem!!.slice!!.uri.toString()).build()

            if (mSlice != null) {
                Executors.MODEL_EXECUTOR.execute {
                    val itemInfoBuilder = LauncherAtom.ItemInfo.newBuilder().setSlice(mSlice)
                    mContainerInfo?.let { itemInfoBuilder.setContainerInfo(it) }
                    write(event, applyOverwrites(itemInfoBuilder.build()))
                }
                return
            }

            val info = mItemInfo ?: return

            // If the item is inside a collection, fetch collection info in a BG thread
            // and then write to StatsLog.
            LauncherAppState.INSTANCE[context].model.enqueueModelUpdateTask { _, dataModel, _ ->
                write(
                    event,
                    applyOverwrites(
                        info.buildProto(
                            dataModel.itemsIdMap[info.container] as CollectionInfo?,
                            context,
                        )
                    ),
                )
            }
        }

        override fun sendToInteractionJankMonitor(event: EventEnum?, view: View?) {
            if (event !is LauncherEvent) return
            when (event) {
                LAUNCHER_ALLAPPS_VERTICAL_SWIPE_BEGIN ->
                    InteractionJankMonitorWrapper.begin(view, Cuj.CUJ_LAUNCHER_ALL_APPS_SCROLL)

                LAUNCHER_ALLAPPS_VERTICAL_SWIPE_END ->
                    InteractionJankMonitorWrapper.end(Cuj.CUJ_LAUNCHER_ALL_APPS_SCROLL)
                LAUNCHER_PRIVATE_SPACE_LOCK_ANIMATION_BEGIN ->
                    InteractionJankMonitorWrapper.begin(view, Cuj.CUJ_LAUNCHER_PRIVATE_SPACE_LOCK)

                LAUNCHER_PRIVATE_SPACE_LOCK_ANIMATION_END ->
                    InteractionJankMonitorWrapper.end(Cuj.CUJ_LAUNCHER_PRIVATE_SPACE_LOCK)
                LAUNCHER_PRIVATE_SPACE_UNLOCK_ANIMATION_BEGIN ->
                    InteractionJankMonitorWrapper.begin(view, Cuj.CUJ_LAUNCHER_PRIVATE_SPACE_UNLOCK)

                LAUNCHER_PRIVATE_SPACE_UNLOCK_ANIMATION_END ->
                    InteractionJankMonitorWrapper.end(Cuj.CUJ_LAUNCHER_PRIVATE_SPACE_UNLOCK)
                LAUNCHER_WORK_UTILITY_VIEW_EXPAND_ANIMATION_BEGIN ->
                    InteractionJankMonitorWrapper.begin(
                        view,
                        Cuj.CUJ_LAUNCHER_WORK_UTILITY_VIEW_EXPAND,
                    )

                LAUNCHER_WORK_UTILITY_VIEW_EXPAND_ANIMATION_END ->
                    InteractionJankMonitorWrapper.end(Cuj.CUJ_LAUNCHER_WORK_UTILITY_VIEW_EXPAND)

                LAUNCHER_WORK_UTILITY_VIEW_SHRINK_ANIMATION_BEGIN ->
                    InteractionJankMonitorWrapper.begin(
                        view,
                        Cuj.CUJ_LAUNCHER_WORK_UTILITY_VIEW_SHRINK,
                    )

                LAUNCHER_WORK_UTILITY_VIEW_SHRINK_ANIMATION_END ->
                    InteractionJankMonitorWrapper.end(Cuj.CUJ_LAUNCHER_WORK_UTILITY_VIEW_SHRINK)

                else -> {}
            }
        }

        fun applyOverwrites(atomInfo: LauncherAtom.ItemInfo): LauncherAtom.ItemInfo =
            atomInfo
                .toBuilder()
                .apply {
                    mRank?.let { setRank(it) }
                    mContainerInfo?.let { setContainerInfo(it) }
                    activityContext?.applyOverwritesToLogItem(this)

                    if (mFromState != null || mToState != null || mEditText != null) {
                        val folderIconBuilder = folderIcon.toBuilder()
                        mFromState?.let { folderIconBuilder.setFromLabelState(it) }
                        mToState?.let { folderIconBuilder.setToLabelState(it) }
                        mEditText?.let { folderIconBuilder.setLabelInfo(it) }

                        setFolderIcon(folderIconBuilder)
                    }
                }
                .build()

        @WorkerThread
        fun write(event: EventEnum, atomInfo: LauncherAtom.ItemInfo) {
            val instanceId = mInstanceId
            val srcState = mSrcState
            val dstState = mDstState
            val inputType = mInputType
            val packageName = mPackageName ?: getPackageName(atomInfo)

            if (IS_VERBOSE) {
                val name =
                    if (event is Enum<*>) (event as Enum<*>).name else event.id.toString() + ""
                val logStringBuilder = StringBuilder("\n")
                if (instanceId !== DEFAULT_INSTANCE_ID)
                    logStringBuilder.append("InstanceId:$instanceId")

                logStringBuilder.append(name)

                if (
                    srcState != LAUNCHER_STATE_UNSPECIFIED || dstState != LAUNCHER_STATE_UNSPECIFIED
                ) {
                    logStringBuilder.append(
                        "(State:${getStateString(srcState)}->${getStateString(dstState)})"
                    )
                }

                if (atomInfo.hasContainerInfo()) logStringBuilder.append("\n$atomInfo")
                packageName?.let { logStringBuilder.append("\nPackage name: $it") }

                Log.d(TAG, logStringBuilder.toString())
            }

            for (consumer in LOGS_CONSUMER) {
                consumer.consume(event, atomInfo)
            }

            // TODO: remove this when b/231648228 is fixed.
            if (Utilities.isRunningInTestHarness()) {
                return
            }
            val cardinality = mCardinality ?: getCardinality(atomInfo)

            val features = mFeatures ?: getFeatures(atomInfo)

            if (!Utilities.ATLEAST_R) return

            SysUiStatsLog.write(
                SysUiStatsLog.LAUNCHER_EVENT,
                SysUiStatsLog.LAUNCHER_UICHANGED__ACTION__DEFAULT_ACTION, /* deprecated */
                srcState,
                dstState,
                null, /* launcher extensions, deprecated */
                false, /* quickstep_enabled, deprecated */
                event.id, /* event_id */
                atomInfo.itemCase.number, /* target_id */
                instanceId!!.id, /* instance_id TODO */
                0, /* uid TODO */
                packageName, /* package_name */
                getComponentName(atomInfo), /* component_name */
                getGridX(atomInfo, false), /* grid_x */
                getGridY(atomInfo, false), /* grid_y */
                getPageId(atomInfo), /* page_id */
                getGridX(atomInfo, true), /* grid_x_parent */
                getGridY(atomInfo, true), /* grid_y_parent */
                getParentPageId(atomInfo), /* page_id_parent */
                getHierarchy(atomInfo), /* hierarchy */
                false, /* is_work_profile, deprecated */
                atomInfo.rank, /* rank */
                atomInfo.folderIcon.fromLabelState.number, /* fromState */
                atomInfo.folderIcon.toLabelState.number, /* toState */
                atomInfo.folderIcon.labelInfo, /* edittext */
                cardinality, /* cardinality */
                features, /* features */
                getSearchAttributes(atomInfo), /* searchAttributes */
                getAttributes(atomInfo), /* attributes */
                inputType, /* input_type */
                atomInfo.userType, /* user_type */
                displayRotation, /* display_rotation */
                getRecentsOrientationHandler(atomInfo), /* recents_orientation_handler */
            )
        }

        val displayRotation: Int
            get() =
                when (mDisplayRotation) {
                    Surface.ROTATION_90 ->
                        SysUiStatsLog.LAUNCHER_UICHANGED__DISPLAY_ROTATION__ROTATION_90
                    Surface.ROTATION_180 ->
                        SysUiStatsLog.LAUNCHER_UICHANGED__DISPLAY_ROTATION__ROTATION_180
                    Surface.ROTATION_270 ->
                        SysUiStatsLog.LAUNCHER_UICHANGED__DISPLAY_ROTATION__ROTATION_270
                    else -> SysUiStatsLog.LAUNCHER_UICHANGED__DISPLAY_ROTATION__ROTATION_0
                }

        fun getRecentsOrientationHandler(itemInfo: LauncherAtom.ItemInfo): Int {
            val orientationHandler = itemInfo.containerInfo.taskSwitcherContainer.orientationHandler
            return when (orientationHandler) {
                PORTRAIT -> SysUiStatsLog.LAUNCHER_UICHANGED__RECENTS_ORIENTATION_HANDLER__PORTRAIT
                LANDSCAPE ->
                    SysUiStatsLog.LAUNCHER_UICHANGED__RECENTS_ORIENTATION_HANDLER__LANDSCAPE
                SEASCAPE -> SysUiStatsLog.LAUNCHER_UICHANGED__RECENTS_ORIENTATION_HANDLER__SEASCAPE
            }
        }

        companion object {
            private val DEFAULT_ITEM_INFO = ItemInfo()

            init {
                DEFAULT_ITEM_INFO.itemType = Favorites.ITEM_TYPE_NON_ACTIONABLE
            }
        }
    }

    /** Helps to construct and log statsd compatible latency events. */
    private class StatsCompatLatencyLogger : StatsLatencyLogger {
        private var mInstanceId: InstanceId? = DEFAULT_INSTANCE_ID
        private var mType: LatencyType? = UNKNOWN
        private var mPackageId = 0
        private var mLatencyInMillis: Long = 0
        private var mQueryLength = -1
        private var mSubEventType = 0
        private var mCardinality = -1

        override fun withInstanceId(instanceId: InstanceId?) = apply {
            this.mInstanceId = instanceId
        }

        override fun withType(type: LatencyType?) = apply { this.mType = type }

        override fun withPackageId(packageId: Int) = apply { this.mPackageId = packageId }

        override fun withLatency(latencyInMillis: Long) = apply {
            this.mLatencyInMillis = latencyInMillis
        }

        override fun withQueryLength(queryLength: Int) = apply { this.mQueryLength = queryLength }

        override fun withSubEventType(type: Int) = apply { this.mSubEventType = type }

        override fun withCardinality(cardinality: Int) = apply { this.mCardinality = cardinality }

        override fun log(event: EventEnum) {
            if (IS_VERBOSE) {
                val name =
                    if (event is Enum<*>) (event as Enum<*>).name else event.id.toString() + ""
                Log.d(LATENCY_TAG, "InstanceId=$mInstanceId $name=${mLatencyInMillis}ms")
            }

            if (!Utilities.ATLEAST_R) return

            SysUiStatsLog.write(
                SysUiStatsLog.LAUNCHER_LATENCY,
                event.id, // event_id
                mInstanceId!!.id, // instance_id
                mPackageId, // package_id
                mLatencyInMillis, // latency_in_millis
                mType!!.id, // type
                mQueryLength, // query_length
                mSubEventType, // sub_event_type
                mCardinality, // cardinality
            )
        }
    }

    /** Helps to construct and log statsd compatible impression events. */
    private class StatsCompatImpressionLogger : StatsImpressionLogger {
        private var mInstanceId: InstanceId? = DEFAULT_INSTANCE_ID
        private var mLauncherState: State? = State.UNKNOWN
        private var mQueryLength = -1

        // Fields used for Impression Logging V2.
        private var mResultType = 0
        private var mAboveKeyboard = false
        private var mUid = 0
        private var mResultSource = 0

        override fun withInstanceId(instanceId: InstanceId?) = apply {
            this.mInstanceId = instanceId
        }

        override fun withState(state: State?) = apply { this.mLauncherState = state }

        override fun withQueryLength(queryLength: Int) = apply { this.mQueryLength = queryLength }

        override fun withResultType(resultType: Int) = apply { mResultType = resultType }

        override fun withAboveKeyboard(aboveKeyboard: Boolean) = apply {
            mAboveKeyboard = aboveKeyboard
        }

        override fun withUid(uid: Int) = apply { mUid = uid }

        override fun withResultSource(resultSource: Int) = apply { mResultSource = resultSource }

        override fun log(event: EventEnum) {
            if (IS_VERBOSE) {
                val name =
                    if (event is Enum<*>) (event as Enum<*>).name else event.id.toString() + ""
                Log.d(
                    IMPRESSION_TAG,
                    """"
                     |InstanceId:$mInstanceId
                     |ImpressionEvent:$name
                     |  LauncherState = $mLauncherState
                     |  QueryLength = $mQueryLength
                     |  ResultType=$mResultType is_above_keyboard=$mAboveKeyboard mUid=$mUid
                     |  result_source=$mResultSource
                     |"""
                        .trimMargin(),
                )
            }

            if (!Utilities.ATLEAST_R) return

            SysUiStatsLog.write(
                SysUiStatsLog.LAUNCHER_IMPRESSION_EVENT_V2,
                event.id, // event_id
                mInstanceId!!.id, // instance_id
                mLauncherState!!.launcherState, // state
                mQueryLength, // query_length
                mResultType, // result type
                mAboveKeyboard, // above keyboard
                mUid, // uid
                mResultSource, // result source
            )
        }
    }

    /** Interface to get stats log while it is dispatched to the system */
    interface StatsLogConsumer {
        @WorkerThread fun consume(event: EventEnum?, atomInfo: LauncherAtom.ItemInfo?)
    }

    companion object {
        private const val TAG = "StatsLog"
        private const val LATENCY_TAG = "StatsLatencyLog"
        private const val IMPRESSION_TAG = "StatsImpressionLog"
        private val IS_VERBOSE = Utilities.isPropertyEnabled(LogConfig.STATSLOG)
        private val DEBUG = !Utilities.isRunningInTestHarness()
        private val DEFAULT_INSTANCE_ID: InstanceId = InstanceId.fakeInstanceId(0)

        // LauncherAtom.ItemInfo.getDefaultInstance() should be used but until launcher proto
        // migrates
        // from nano to lite, bake constant to prevent robo test failure.
        private const val DEFAULT_PAGE_INDEX = -2
        private const val FOLDER_HIERARCHY_OFFSET = 100
        private const val SEARCH_RESULT_HIERARCHY_OFFSET = 200
        private const val EXTENDED_CONTAINERS_HIERARCHY_OFFSET = 300
        private const val ALL_APPS_HIERARCHY_OFFSET = 400

        /** Flags for converting SearchAttribute to integer value. */
        private const val SEARCH_ATTRIBUTES_CORRECTED_QUERY = 1 shl 0
        private const val SEARCH_ATTRIBUTES_DIRECT_MATCH = 1 shl 1
        private const val SEARCH_ATTRIBUTES_ENTRY_STATE_ALL_APPS = 1 shl 2
        private const val SEARCH_ATTRIBUTES_ENTRY_STATE_QSB = 1 shl 3
        private const val SEARCH_ATTRIBUTES_ENTRY_STATE_OVERVIEW = 1 shl 4
        private const val SEARCH_ATTRIBUTES_ENTRY_STATE_TASKBAR = 1 shl 5

        @JvmField val LOGS_CONSUMER: CopyOnWriteArrayList<StatsLogConsumer> = CopyOnWriteArrayList()

        /** Synchronously writes an itemInfo to stats log */
        @WorkerThread
        @JvmStatic
        fun writeSnapshot(info: LauncherAtom.ItemInfo, instanceId: InstanceId) {
            if (IS_VERBOSE) {
                Log.d(TAG, String.format("\nwriteSnapshot(%d):\n%s", instanceId.id, info))
            }
            if (Utilities.isRunningInTestHarness()) {
                return
            }

            if (!Utilities.ATLEAST_R) return

            SysUiStatsLog.write(
                SysUiStatsLog.LAUNCHER_SNAPSHOT,
                LAUNCHER_WORKSPACE_SNAPSHOT.id, /* event_id */
                info.itemCase.number, /* target_id */
                instanceId.id, /* instance_id */
                0, /* uid */
                getPackageName(info), /* package_name */
                getComponentName(info), /* component_name */
                getGridX(info, false), /* grid_x */
                getGridY(info, false), /* grid_y */
                getPageId(info), /* page_id */
                getGridX(info, true), /* grid_x_parent */
                getGridY(info, true), /* grid_y_parent */
                getParentPageId(info), /* page_id_parent */
                getHierarchy(info), /* hierarchy */
                info.isWork, /* is_work_profile */
                0, /* origin */
                getCardinality(info), /* cardinality */
                info.widget.spanX,
                info.widget.spanY,
                getFeatures(info),
                getAttributes(info), /* attributes */
            )
        }

        private fun getAttributes(itemInfo: LauncherAtom.ItemInfo): ByteArray =
            LauncherAttributes.newBuilder()
                .apply { itemInfo.itemAttributesList.forEach { addItemAttributes(it.number) } }
                .build()
                .toByteArray()

        /**
         * Builds [StatsEvent] from [LauncherAtom.ItemInfo]. Used for pulled atom callback
         * implementation.
         */
        @JvmStatic
        fun buildStatsEvent(info: LauncherAtom.ItemInfo, instanceId: InstanceId?): StatsEvent {
            return SysUiStatsLog.buildStatsEvent(
                SysUiStatsLog.LAUNCHER_LAYOUT_SNAPSHOT, // atom ID,
                LAUNCHER_WORKSPACE_SNAPSHOT.id, // event_id = 1;
                info.itemCase.number, // item_id = 2;
                instanceId?.id ?: 0, // instance_id = 3;
                0, // uid = 4 [(is_uid) = true];
                getPackageName(info), // package_name = 5;
                getComponentName(info), // component_name = 6;
                getGridX(info, false), // grid_x = 7 [default = -1];
                getGridY(info, false), // grid_y = 8 [default = -1];
                getPageId(info), // page_id = 9 [default = -2];
                getGridX(info, true), // grid_x_parent = 10 [default = -1];
                getGridY(info, true), // grid_y_parent = 11 [default = -1];
                getParentPageId(info), // page_id_parent = 12 [default = -2];
                getHierarchy(info), // container_id = 13;
                info.isWork, // is_work_profile = 14;
                0, // attribute_id = 15;
                getCardinality(info), // cardinality = 16;
                info.widget.spanX, // span_x = 17 [default = 1];
                info.widget.spanY, // span_y = 18 [default = 1];
                getAttributes(info), /* attributes = 19 [(log_mode) = MODE_BYTES] */
                info.isKidsMode, /* is_kids_mode = 20 */
            )
        }

        private fun getCardinality(info: LauncherAtom.ItemInfo): Int {
            if (Utilities.isRunningInTestHarness()) return 0

            when (info.containerInfo.containerCase) {
                PREDICTED_HOTSEAT_CONTAINER ->
                    return info.containerInfo.predictedHotseatContainer.cardinality
                TASK_BAR_CONTAINER -> return info.containerInfo.taskBarContainer.cardinality
                SEARCH_RESULT_CONTAINER ->
                    return info.containerInfo.searchResultContainer.queryLength
                EXTENDED_CONTAINERS -> {
                    val extendedCont = info.containerInfo.extendedContainers
                    if (extendedCont.containerCase == DEVICE_SEARCH_RESULT_CONTAINER) {
                        val deviceSearchResultCont = extendedCont.deviceSearchResultContainer
                        return if (deviceSearchResultCont.hasQueryLength())
                            deviceSearchResultCont.queryLength
                        else -1
                    }
                    return when (info.itemCase) {
                        FOLDER_ICON -> info.folderIcon.cardinality
                        TASK_VIEW -> info.taskView.cardinality
                        else -> 0
                    }
                }

                else ->
                    return when (info.itemCase) {
                        FOLDER_ICON -> info.folderIcon.cardinality
                        TASK_VIEW -> info.taskView.cardinality
                        else -> 0
                    }
            }
        }

        private fun getPackageName(info: LauncherAtom.ItemInfo): String? =
            when (info.itemCase) {
                APPLICATION -> info.application.packageName
                SHORTCUT -> info.shortcut.shortcutName
                WIDGET -> info.widget.packageName
                TASK -> info.task.packageName
                SEARCH_ACTION_ITEM -> info.searchActionItem.packageName
                else -> null
            }

        private fun getComponentName(info: LauncherAtom.ItemInfo): String? =
            when (info.itemCase) {
                APPLICATION -> info.application.componentName
                SHORTCUT -> info.shortcut.shortcutName
                WIDGET -> info.widget.componentName
                TASK -> info.task.componentName
                TASK_VIEW -> info.taskView.componentName
                SEARCH_ACTION_ITEM -> info.searchActionItem.title
                SLICE -> info.slice.uri
                else -> null
            }

        private fun getGridX(info: LauncherAtom.ItemInfo, parent: Boolean): Int {
            val containerInfo = info.containerInfo
            return if (containerInfo.containerCase == FOLDER) {
                if (parent) {
                    containerInfo.folder.workspace.gridX
                } else {
                    containerInfo.folder.gridX
                }
            } else if (containerInfo.containerCase == EXTENDED_CONTAINERS) {
                containerInfo.extendedContainers.deviceSearchResultContainer.gridX
            } else {
                containerInfo.workspace.gridX
            }
        }

        private fun getGridY(info: LauncherAtom.ItemInfo, parent: Boolean): Int =
            if (info.containerInfo.containerCase == FOLDER) {
                if (parent) {
                    info.containerInfo.folder.workspace.gridY
                } else {
                    info.containerInfo.folder.gridY
                }
            } else {
                info.containerInfo.workspace.gridY
            }

        private fun getPageId(info: LauncherAtom.ItemInfo): Int =
            when (info.itemCase) {
                TASK -> info.task.index
                TASK_VIEW -> info.taskView.index
                else -> getPageIdFromContainerInfo(info.containerInfo)
            }

        private fun getPageIdFromContainerInfo(containerInfo: ContainerInfo): Int =
            when (containerInfo.containerCase) {
                FOLDER -> containerInfo.folder.pageIndex
                HOTSEAT -> containerInfo.hotseat.index
                PREDICTED_HOTSEAT_CONTAINER -> containerInfo.predictedHotseatContainer.index
                TASK_BAR_CONTAINER -> containerInfo.taskBarContainer.index
                else -> containerInfo.workspace.pageIndex
            }

        private fun getParentPageId(info: LauncherAtom.ItemInfo): Int =
            info.containerInfo.run {
                when {
                    containerCase == FOLDER &&
                        folder.parentContainerCase == ParentContainerCase.HOTSEAT ->
                        folder.hotseat.index

                    containerCase == FOLDER -> folder.workspace.pageIndex
                    containerCase == SEARCH_RESULT_CONTAINER ->
                        searchResultContainer.workspace.pageIndex

                    else -> workspace.pageIndex
                }
            }

        private fun getHierarchy(info: LauncherAtom.ItemInfo): Int {
            if (Utilities.isRunningInTestHarness()) return 0

            return info.containerInfo.run {
                when (containerCase) {
                    FOLDER -> folder.parentContainerCase.number + FOLDER_HIERARCHY_OFFSET
                    SEARCH_RESULT_CONTAINER ->
                        searchResultContainer.parentContainerCase.number +
                            SEARCH_RESULT_HIERARCHY_OFFSET
                    EXTENDED_CONTAINERS ->
                        extendedContainers.containerCase.number +
                            EXTENDED_CONTAINERS_HIERARCHY_OFFSET
                    ALL_APPS_CONTAINER ->
                        allAppsContainer.parentContainerCase.number + ALL_APPS_HIERARCHY_OFFSET
                    else -> containerCase.number
                }
            }
        }

        private fun getStateString(state: Int): String =
            when (state) {
                SysUiStatsLog.LAUNCHER_UICHANGED__DST_STATE__BACKGROUND -> "BACKGROUND"
                SysUiStatsLog.LAUNCHER_UICHANGED__DST_STATE__HOME -> "HOME"
                SysUiStatsLog.LAUNCHER_UICHANGED__DST_STATE__OVERVIEW -> "OVERVIEW"
                SysUiStatsLog.LAUNCHER_UICHANGED__DST_STATE__ALLAPPS -> "ALLAPPS"
                else -> "INVALID"
            }

        private fun getFeatures(info: LauncherAtom.ItemInfo): Int =
            when (info.itemCase) {
                WIDGET -> info.widget.widgetFeatures
                TASK_VIEW -> info.taskView.type
                else -> 0
            }

        private fun getSearchAttributes(info: LauncherAtom.ItemInfo): Int {
            if (Utilities.isRunningInTestHarness()) return 0

            val containerInfo = info.containerInfo
            if (
                containerInfo.containerCase == EXTENDED_CONTAINERS &&
                    (containerInfo.extendedContainers.containerCase ==
                        DEVICE_SEARCH_RESULT_CONTAINER) &&
                    containerInfo.extendedContainers.deviceSearchResultContainer
                        .hasSearchAttributes()
            ) {
                return searchAttributesToInt(
                    containerInfo.extendedContainers.deviceSearchResultContainer.searchAttributes
                )
            }
            return 0
        }

        private fun searchAttributesToInt(searchAttributes: SearchAttributes): Int {
            var response = 0
            if (searchAttributes.correctedQuery)
                response = response or SEARCH_ATTRIBUTES_CORRECTED_QUERY

            if (searchAttributes.directMatch) response = response or SEARCH_ATTRIBUTES_DIRECT_MATCH

            response =
                response or
                    when (searchAttributes.entryState) {
                        ALL_APPS -> SEARCH_ATTRIBUTES_ENTRY_STATE_ALL_APPS
                        QSB -> SEARCH_ATTRIBUTES_ENTRY_STATE_QSB
                        OVERVIEW -> SEARCH_ATTRIBUTES_ENTRY_STATE_OVERVIEW
                        TASKBAR -> SEARCH_ATTRIBUTES_ENTRY_STATE_TASKBAR
                        else -> 0
                    }

            return response
        }
    }
}
