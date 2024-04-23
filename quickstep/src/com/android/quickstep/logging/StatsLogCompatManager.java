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

package com.android.quickstep.logging;

import static androidx.core.util.Preconditions.checkNotNull;
import static androidx.core.util.Preconditions.checkState;

import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_NON_ACTIONABLE;
import static com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.ALL_APPS_CONTAINER;
import static com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.EXTENDED_CONTAINERS;
import static com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.FOLDER;
import static com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.SEARCH_RESULT_CONTAINER;
import static com.android.launcher3.logger.LauncherAtomExtensions.ExtendedContainers.ContainerCase.DEVICE_SEARCH_RESULT_CONTAINER;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_WORKSPACE_SNAPSHOT;
import static com.android.systemui.shared.system.SysUiStatsLog.LAUNCHER_UICHANGED__DST_STATE__ALLAPPS;
import static com.android.systemui.shared.system.SysUiStatsLog.LAUNCHER_UICHANGED__DST_STATE__BACKGROUND;
import static com.android.systemui.shared.system.SysUiStatsLog.LAUNCHER_UICHANGED__DST_STATE__HOME;
import static com.android.systemui.shared.system.SysUiStatsLog.LAUNCHER_UICHANGED__DST_STATE__OVERVIEW;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.util.StatsEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.slice.SliceItem;

import com.android.internal.jank.Cuj;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.Utilities;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.logger.LauncherAtom.Attribute;
import com.android.launcher3.logger.LauncherAtom.ContainerInfo;
import com.android.launcher3.logger.LauncherAtom.FolderContainer.ParentContainerCase;
import com.android.launcher3.logger.LauncherAtom.FolderIcon;
import com.android.launcher3.logger.LauncherAtom.FromState;
import com.android.launcher3.logger.LauncherAtom.LauncherAttributes;
import com.android.launcher3.logger.LauncherAtom.ToState;
import com.android.launcher3.logger.LauncherAtomExtensions.DeviceSearchResultContainer;
import com.android.launcher3.logger.LauncherAtomExtensions.DeviceSearchResultContainer.SearchAttributes;
import com.android.launcher3.logger.LauncherAtomExtensions.ExtendedContainers;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.AllAppsList;
import com.android.launcher3.model.BaseModelUpdateTask;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.data.CollectionInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.LogConfig;
import com.android.launcher3.views.ActivityContext;
import com.android.systemui.shared.system.InteractionJankMonitorWrapper;
import com.android.systemui.shared.system.SysUiStatsLog;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * This class calls StatsLog compile time generated methods.
 *
 * To see if the logs are properly sent to statsd, execute following command.
 * <ul>
 * $ wwdebug (to turn on the logcat printout)
 * $ wwlogcat (see logcat with grep filter on)
 * $ statsd_testdrive (see how ww is writing the proto to statsd buffer)
 * </ul>
 */
public class StatsLogCompatManager extends StatsLogManager {

    private static final String TAG = "StatsLog";
    private static final String LATENCY_TAG = "StatsLatencyLog";
    private static final String IMPRESSION_TAG = "StatsImpressionLog";
    private static final boolean IS_VERBOSE = Utilities.isPropertyEnabled(LogConfig.STATSLOG);
    private static final boolean DEBUG = !Utilities.isRunningInTestHarness();
    private static final InstanceId DEFAULT_INSTANCE_ID = InstanceId.fakeInstanceId(0);
    // LauncherAtom.ItemInfo.getDefaultInstance() should be used but until launcher proto migrates
    // from nano to lite, bake constant to prevent robo test failure.
    private static final int DEFAULT_PAGE_INDEX = -2;
    private static final int FOLDER_HIERARCHY_OFFSET = 100;
    private static final int SEARCH_RESULT_HIERARCHY_OFFSET = 200;
    private static final int EXTENDED_CONTAINERS_HIERARCHY_OFFSET = 300;
    private static final int ALL_APPS_HIERARCHY_OFFSET = 400;

    /**
     * Flags for converting SearchAttribute to integer value.
     */
    private static final int SEARCH_ATTRIBUTES_CORRECTED_QUERY = 1 << 0;
    private static final int SEARCH_ATTRIBUTES_DIRECT_MATCH = 1 << 1;
    private static final int SEARCH_ATTRIBUTES_ENTRY_STATE_ALL_APPS = 1 << 2;
    private static final int SEARCH_ATTRIBUTES_ENTRY_STATE_QSB = 1 << 3;
    private static final int SEARCH_ATTRIBUTES_ENTRY_STATE_OVERVIEW = 1 << 4;
    private static final int SEARCH_ATTRIBUTES_ENTRY_STATE_TASKBAR = 1 << 5;

    public static final CopyOnWriteArrayList<StatsLogConsumer> LOGS_CONSUMER =
            new CopyOnWriteArrayList<>();

    public StatsLogCompatManager(Context context) {
        super(context);
    }

    @Override
    protected StatsLogger createLogger() {
        return new StatsCompatLogger(mContext, mActivityContext);
    }

    @Override
    protected StatsLatencyLogger createLatencyLogger() {
        return new StatsCompatLatencyLogger();
    }

    @Override
    protected StatsImpressionLogger createImpressionLogger() {
        return new StatsCompatImpressionLogger();
    }

    /**
     * Synchronously writes an itemInfo to stats log
     */
    @WorkerThread
    public static void writeSnapshot(LauncherAtom.ItemInfo info, InstanceId instanceId) {
        if (IS_VERBOSE) {
            Log.d(TAG, String.format("\nwriteSnapshot(%d):\n%s", instanceId.getId(), info));
        }
        if (Utilities.isRunningInTestHarness()) {
            return;
        }
        SysUiStatsLog.write(SysUiStatsLog.LAUNCHER_SNAPSHOT,
                LAUNCHER_WORKSPACE_SNAPSHOT.getId() /* event_id */,
                info.getItemCase().getNumber()  /* target_id */,
                instanceId.getId() /* instance_id */,
                0 /* uid */,
                getPackageName(info) /* package_name */,
                getComponentName(info) /* component_name */,
                getGridX(info, false) /* grid_x */,
                getGridY(info, false) /* grid_y */,
                getPageId(info) /* page_id */,
                getGridX(info, true) /* grid_x_parent */,
                getGridY(info, true) /* grid_y_parent */,
                getParentPageId(info) /* page_id_parent */,
                getHierarchy(info) /* hierarchy */,
                info.getIsWork() /* is_work_profile */,
                0 /* origin */,
                getCardinality(info) /* cardinality */,
                info.getWidget().getSpanX(),
                info.getWidget().getSpanY(),
                getFeatures(info),
                getAttributes(info) /* attributes */
        );
    }

    private static byte[] getAttributes(LauncherAtom.ItemInfo itemInfo) {
        LauncherAttributes.Builder responseBuilder = LauncherAttributes.newBuilder();
        itemInfo.getItemAttributesList().stream().map(Attribute::getNumber).forEach(
                responseBuilder::addItemAttributes);
        return responseBuilder.build().toByteArray();
    }

    /**
     * Builds {@link StatsEvent} from {@link LauncherAtom.ItemInfo}. Used for pulled atom callback
     * implementation.
     */
    public static StatsEvent buildStatsEvent(LauncherAtom.ItemInfo info,
            @Nullable InstanceId instanceId) {
        return SysUiStatsLog.buildStatsEvent(
                SysUiStatsLog.LAUNCHER_LAYOUT_SNAPSHOT, // atom ID,
                LAUNCHER_WORKSPACE_SNAPSHOT.getId(), // event_id = 1;
                info.getItemCase().getNumber(), // item_id = 2;
                instanceId == null ? 0 : instanceId.getId(), //instance_id = 3;
                0, //uid = 4 [(is_uid) = true];
                getPackageName(info), // package_name = 5;
                getComponentName(info), // component_name = 6;
                getGridX(info, false), //grid_x = 7 [default = -1];
                getGridY(info, false), //grid_y = 8 [default = -1];
                getPageId(info), // page_id = 9 [default = -2];
                getGridX(info, true), //grid_x_parent = 10 [default = -1];
                getGridY(info, true), //grid_y_parent = 11 [default = -1];
                getParentPageId(info), //page_id_parent = 12 [default = -2];
                getHierarchy(info), // container_id = 13;
                info.getIsWork(), // is_work_profile = 14;
                0, // attribute_id = 15;
                getCardinality(info), // cardinality = 16;
                info.getWidget().getSpanX(), // span_x = 17 [default = 1];
                info.getWidget().getSpanY(), // span_y = 18 [default = 1];
                getAttributes(info) /* attributes = 19 [(log_mode) = MODE_BYTES] */,
                info.getIsKidsMode() /* is_kids_mode = 20 */
        );
    }

    /**
     * Helps to construct and write statsd compatible log message.
     */
    private static class StatsCompatLogger implements StatsLogger {

        private static final ItemInfo DEFAULT_ITEM_INFO = new ItemInfo();
        static {
            DEFAULT_ITEM_INFO.itemType = ITEM_TYPE_NON_ACTIONABLE;
        }
        private final Context mContext;
        private final Optional<ActivityContext> mActivityContext;
        private ItemInfo mItemInfo = DEFAULT_ITEM_INFO;
        private InstanceId mInstanceId = DEFAULT_INSTANCE_ID;
        private OptionalInt mRank = OptionalInt.empty();
        private Optional<ContainerInfo> mContainerInfo = Optional.empty();
        private int mSrcState = LAUNCHER_STATE_UNSPECIFIED;
        private int mDstState = LAUNCHER_STATE_UNSPECIFIED;
        private Optional<FromState> mFromState = Optional.empty();
        private Optional<ToState> mToState = Optional.empty();
        private Optional<String> mEditText = Optional.empty();
        private SliceItem mSliceItem;
        private LauncherAtom.Slice mSlice;
        private Optional<Integer> mCardinality = Optional.empty();
        private int mInputType = SysUiStatsLog.LAUNCHER_UICHANGED__INPUT_TYPE__UNKNOWN;
        private Optional<Integer> mFeatures = Optional.empty();
        private Optional<String> mPackageName = Optional.empty();

        StatsCompatLogger(Context context, ActivityContext activityContext) {
            mContext = context;
            mActivityContext = Optional.ofNullable(activityContext);
        }

        @Override
        public StatsLogger withItemInfo(ItemInfo itemInfo) {
            if (mContainerInfo.isPresent()) {
                throw new IllegalArgumentException(
                        "ItemInfo and ContainerInfo are mutual exclusive; cannot log both.");
            }
            this.mItemInfo = itemInfo;
            return this;
        }

        @Override
        public StatsLogger withInstanceId(InstanceId instanceId) {
            this.mInstanceId = instanceId;
            return this;
        }

        @Override
        public StatsLogger withRank(int rank) {
            this.mRank = OptionalInt.of(rank);
            return this;
        }

        @Override
        public StatsLogger withSrcState(int srcState) {
            this.mSrcState = srcState;
            return this;
        }

        @Override
        public StatsLogger withDstState(int dstState) {
            this.mDstState = dstState;
            return this;
        }

        @Override
        public StatsLogger withContainerInfo(ContainerInfo containerInfo) {
            checkState(mItemInfo == DEFAULT_ITEM_INFO,
                    "ItemInfo and ContainerInfo are mutual exclusive; cannot log both.");
            this.mContainerInfo = Optional.of(containerInfo);
            return this;
        }

        @Override
        public StatsLogger withFromState(FromState fromState) {
            this.mFromState = Optional.of(fromState);
            return this;
        }

        @Override
        public StatsLogger withToState(ToState toState) {
            this.mToState = Optional.of(toState);
            return this;
        }

        @Override
        public StatsLogger withEditText(String editText) {
            this.mEditText = Optional.of(editText);
            return this;
        }

        @Override
        public StatsLogger withSliceItem(@NonNull SliceItem sliceItem) {
            checkState(mItemInfo == DEFAULT_ITEM_INFO && mSlice == null,
                    "ItemInfo, Slice and SliceItem are mutual exclusive; cannot set more than one"
                            + " of them.");
            this.mSliceItem = checkNotNull(sliceItem, "expected valid sliceItem but received null");
            return this;
        }

        @Override
        public StatsLogger withSlice(LauncherAtom.Slice slice) {
            checkState(mItemInfo == DEFAULT_ITEM_INFO && mSliceItem == null,
                    "ItemInfo, Slice and SliceItem are mutual exclusive; cannot set more than one"
                            + " of them.");
            checkNotNull(slice, "expected valid slice but received null");
            checkNotNull(slice.getUri(), "expected valid slice uri but received null");
            this.mSlice = slice;
            return this;
        }

        @Override
        public StatsLogger withCardinality(int cardinality) {
            this.mCardinality = Optional.of(cardinality);
            return this;
        }

        @Override
        public StatsLogger withInputType(int inputType) {
            this.mInputType = inputType;
            return this;
        }

        @Override
        public StatsLogger withFeatures(int feature) {
            this.mFeatures = Optional.of(feature);
            return this;
        }

        @Override
        public StatsLogger withPackageName(@Nullable String packageName) {
            mPackageName = Optional.ofNullable(packageName);
            return this;
        }

        @Override
        public void log(EventEnum event) {
            if (DEBUG) {
                String name = (event instanceof Enum) ? ((Enum) event).name() :
                        event.getId() + "";
                Log.d(TAG, name);
            }

            if (mSlice == null && mSliceItem != null) {
                mSlice = LauncherAtom.Slice.newBuilder().setUri(
                        mSliceItem.getSlice().getUri().toString()).build();
            }

            if (mSlice != null) {
                Executors.MODEL_EXECUTOR.execute(
                        () -> {
                            LauncherAtom.ItemInfo.Builder itemInfoBuilder =
                                    LauncherAtom.ItemInfo.newBuilder().setSlice(mSlice);
                            mContainerInfo.ifPresent(itemInfoBuilder::setContainerInfo);
                            write(event, applyOverwrites(itemInfoBuilder.build()));
                        });
                return;
            }

            if (mItemInfo == null) {
                return;
            }

            if (mItemInfo.container < 0 || !LauncherAppState.INSTANCE.executeIfCreated(app -> {
                // Item is inside a collection, fetch collection info in a BG thread
                // and then write to StatsLog.
                app.getModel().enqueueModelUpdateTask(
                        new BaseModelUpdateTask() {
                            @Override
                            public void execute(@NonNull final LauncherAppState app,
                                    @NonNull final BgDataModel dataModel,
                                    @NonNull final AllAppsList apps) {
                                CollectionInfo collectionInfo =
                                        dataModel.collections.get(mItemInfo.container);
                                write(event, applyOverwrites(mItemInfo.buildProto(collectionInfo)));
                            }
                        });
            })) {
                // Write log on the model thread so that logs do not go out of order
                // (for eg: drop comes after drag)
                Executors.MODEL_EXECUTOR.execute(
                        () -> write(event, applyOverwrites(mItemInfo.buildProto())));
            }
        }

        @Override
        public void sendToInteractionJankMonitor(EventEnum event, View view) {
            if (!(event instanceof LauncherEvent)) {
                return;
            }
            switch ((LauncherEvent) event) {
                case LAUNCHER_ALLAPPS_VERTICAL_SWIPE_BEGIN:
                    InteractionJankMonitorWrapper.begin(
                            view,
                            Cuj.CUJ_LAUNCHER_ALL_APPS_SCROLL);
                    break;
                case LAUNCHER_ALLAPPS_VERTICAL_SWIPE_END:
                    InteractionJankMonitorWrapper.end(Cuj.CUJ_LAUNCHER_ALL_APPS_SCROLL);
                    break;
                default:
                    break;
            }
        }

        private LauncherAtom.ItemInfo applyOverwrites(LauncherAtom.ItemInfo atomInfo) {
            LauncherAtom.ItemInfo.Builder itemInfoBuilder = atomInfo.toBuilder();

            mRank.ifPresent(itemInfoBuilder::setRank);
            mContainerInfo.ifPresent(itemInfoBuilder::setContainerInfo);

            mActivityContext.ifPresent(activityContext ->
                    activityContext.applyOverwritesToLogItem(itemInfoBuilder));

            if (mFromState.isPresent() || mToState.isPresent() || mEditText.isPresent()) {
                FolderIcon.Builder folderIconBuilder = itemInfoBuilder
                        .getFolderIcon()
                        .toBuilder();
                mFromState.ifPresent(folderIconBuilder::setFromLabelState);
                mToState.ifPresent(folderIconBuilder::setToLabelState);
                mEditText.ifPresent(folderIconBuilder::setLabelInfo);
                itemInfoBuilder.setFolderIcon(folderIconBuilder);
            }
            return itemInfoBuilder.build();
        }

        @WorkerThread
        private void write(EventEnum event, LauncherAtom.ItemInfo atomInfo) {
            InstanceId instanceId = mInstanceId;
            int srcState = mSrcState;
            int dstState = mDstState;
            int inputType = mInputType;
            String packageName = mPackageName.orElseGet(() -> getPackageName(atomInfo));
            if (IS_VERBOSE) {
                String name = (event instanceof Enum) ? ((Enum) event).name() :
                        event.getId() + "";
                StringBuilder logStringBuilder = new StringBuilder("\n");
                if (instanceId != DEFAULT_INSTANCE_ID) {
                    logStringBuilder.append(String.format("InstanceId:%s ", instanceId));
                }
                logStringBuilder.append(name);
                if (srcState != LAUNCHER_STATE_UNSPECIFIED
                        || dstState != LAUNCHER_STATE_UNSPECIFIED) {
                    logStringBuilder.append(
                            String.format("(State:%s->%s)", getStateString(srcState),
                                    getStateString(dstState)));
                }
                if (atomInfo.hasContainerInfo()) {
                    logStringBuilder.append("\n").append(atomInfo);
                }
                if (!TextUtils.isEmpty(packageName)) {
                    logStringBuilder.append(String.format("\nPackage name: %s", packageName));
                }
                Log.d(TAG, logStringBuilder.toString());
            }

            for (StatsLogConsumer consumer : LOGS_CONSUMER) {
                consumer.consume(event, atomInfo);
            }

            // TODO: remove this when b/231648228 is fixed.
            if (Utilities.isRunningInTestHarness()) {
                return;
            }
            int cardinality = mCardinality.orElseGet(() -> getCardinality(atomInfo));
            int features = mFeatures.orElseGet(() -> getFeatures(atomInfo));
            SysUiStatsLog.write(
                    SysUiStatsLog.LAUNCHER_EVENT,
                    SysUiStatsLog.LAUNCHER_UICHANGED__ACTION__DEFAULT_ACTION /* deprecated */,
                    srcState,
                    dstState,
                    null /* launcher extensions, deprecated */,
                    false /* quickstep_enabled, deprecated */,
                    event.getId() /* event_id */,
                    atomInfo.getItemCase().getNumber() /* target_id */,
                    instanceId.getId() /* instance_id TODO */,
                    0 /* uid TODO */,
                    packageName /* package_name */,
                    getComponentName(atomInfo) /* component_name */,
                    getGridX(atomInfo, false) /* grid_x */,
                    getGridY(atomInfo, false) /* grid_y */,
                    getPageId(atomInfo) /* page_id */,
                    getGridX(atomInfo, true) /* grid_x_parent */,
                    getGridY(atomInfo, true) /* grid_y_parent */,
                    getParentPageId(atomInfo) /* page_id_parent */,
                    getHierarchy(atomInfo) /* hierarchy */,
                    false /* is_work_profile, deprecated */,
                    atomInfo.getRank() /* rank */,
                    atomInfo.getFolderIcon().getFromLabelState().getNumber() /* fromState */,
                    atomInfo.getFolderIcon().getToLabelState().getNumber() /* toState */,
                    atomInfo.getFolderIcon().getLabelInfo() /* edittext */,
                    cardinality /* cardinality */,
                    features /* features */,
                    getSearchAttributes(atomInfo) /* searchAttributes */,
                    getAttributes(atomInfo) /* attributes */,
                    inputType /* input_type */,
                    atomInfo.getUserType() /* user_type */);
        }
    }

    /**
     * Helps to construct and log statsd compatible latency events.
     */
    private static class StatsCompatLatencyLogger implements StatsLatencyLogger {
        private InstanceId mInstanceId = DEFAULT_INSTANCE_ID;
        private LatencyType mType = LatencyType.UNKNOWN;
        private int mPackageId = 0;
        private long mLatencyInMillis;
        private int mQueryLength = -1;
        private int mSubEventType = 0;
        private int mCardinality = -1;

        @Override
        public StatsLatencyLogger withInstanceId(InstanceId instanceId) {
            this.mInstanceId = instanceId;
            return this;
        }

        @Override
        public StatsLatencyLogger withType(LatencyType type) {
            this.mType = type;
            return this;
        }

        @Override
        public StatsLatencyLogger withPackageId(int packageId) {
            this.mPackageId = packageId;
            return this;
        }

        @Override
        public StatsLatencyLogger withLatency(long latencyInMillis) {
            this.mLatencyInMillis = latencyInMillis;
            return this;
        }

        @Override
        public StatsLatencyLogger withQueryLength(int queryLength) {
            this.mQueryLength = queryLength;
            return this;
        }

        @Override
        public StatsLatencyLogger withSubEventType(int type) {
            this.mSubEventType = type;
            return this;
        }

        @Override
        public StatsLatencyLogger withCardinality(int cardinality) {
            this.mCardinality = cardinality;
            return this;
        }

        @Override
        public void log(EventEnum event) {
            if (IS_VERBOSE) {
                String name = (event instanceof Enum) ? ((Enum) event).name() :
                        event.getId() + "";
                StringBuilder logStringBuilder = new StringBuilder("\n");
                logStringBuilder.append(String.format("InstanceId:%s ", mInstanceId));
                logStringBuilder.append(String.format("%s=%sms", name, mLatencyInMillis));
                Log.d(LATENCY_TAG, logStringBuilder.toString());
            }

            SysUiStatsLog.write(SysUiStatsLog.LAUNCHER_LATENCY,
                    event.getId(), // event_id
                    mInstanceId.getId(), // instance_id
                    mPackageId, // package_id
                    mLatencyInMillis, // latency_in_millis
                    mType.getId(), //type
                    mQueryLength, // query_length
                    mSubEventType, // sub_event_type
                    mCardinality // cardinality
            );
        }
    }

    /**
     * Helps to construct and log statsd compatible impression events.
     */
    private static class StatsCompatImpressionLogger implements StatsImpressionLogger {
        private InstanceId mInstanceId = DEFAULT_INSTANCE_ID;
        private State mLauncherState = State.UNKNOWN;
        private int mQueryLength = -1;

        // Fields used for Impression Logging V2.
        private int mResultType;
        private boolean mAboveKeyboard = false;
        private int mUid;
        private int mResultSource;

        @Override
        public StatsImpressionLogger withInstanceId(InstanceId instanceId) {
            this.mInstanceId = instanceId;
            return this;
        }

        @Override
        public StatsImpressionLogger withState(State state) {
            this.mLauncherState = state;
            return this;
        }

        @Override
        public StatsImpressionLogger withQueryLength(int queryLength) {
            this.mQueryLength = queryLength;
            return this;
        }

        @Override
        public StatsImpressionLogger withResultType(int resultType) {
            mResultType = resultType;
            return this;
        }


        @Override
        public StatsImpressionLogger withAboveKeyboard(boolean aboveKeyboard) {
            mAboveKeyboard = aboveKeyboard;
            return this;
        }

        @Override
        public StatsImpressionLogger withUid(int uid) {
            mUid = uid;
            return this;
        }

        @Override
        public StatsImpressionLogger withResultSource(int resultSource) {
            mResultSource = resultSource;
            return this;
        }

        @Override
        public void log(EventEnum event) {
            if (IS_VERBOSE) {
                String name = (event instanceof Enum) ? ((Enum) event).name() :
                        event.getId() + "";
                StringBuilder logStringBuilder = new StringBuilder("\n");
                logStringBuilder.append(String.format("InstanceId:%s ", mInstanceId));
                logStringBuilder.append(String.format("ImpressionEvent:%s ", name));
                logStringBuilder.append(String.format("\n\tLauncherState = %s ", mLauncherState));
                logStringBuilder.append(String.format("\tQueryLength = %s ", mQueryLength));
                logStringBuilder.append(String.format(
                        "\n\t ResultType = %s is_above_keyboard = %s"
                                + " uid = %s result_source = %s",
                        mResultType,
                        mAboveKeyboard, mUid, mResultSource));

                Log.d(IMPRESSION_TAG, logStringBuilder.toString());
            }


            SysUiStatsLog.write(SysUiStatsLog.LAUNCHER_IMPRESSION_EVENT_V2,
                    event.getId(), // event_id
                    mInstanceId.getId(), // instance_id
                    mLauncherState.getLauncherState(), // state
                    mQueryLength, // query_length
                    mResultType, //result type
                    mAboveKeyboard, // above keyboard
                    mUid, // uid
                    mResultSource // result source

            );
        }
    }

    private static int getCardinality(LauncherAtom.ItemInfo info) {
        if (Utilities.isRunningInTestHarness()) {
            return 0;
        }
        switch (info.getContainerInfo().getContainerCase()) {
            case PREDICTED_HOTSEAT_CONTAINER:
                return info.getContainerInfo().getPredictedHotseatContainer().getCardinality();
            case TASK_BAR_CONTAINER:
                return info.getContainerInfo().getTaskBarContainer().getCardinality();
            case SEARCH_RESULT_CONTAINER:
                return info.getContainerInfo().getSearchResultContainer().getQueryLength();
            case EXTENDED_CONTAINERS:
                ExtendedContainers extendedCont = info.getContainerInfo().getExtendedContainers();
                if (extendedCont.getContainerCase() == DEVICE_SEARCH_RESULT_CONTAINER) {
                    DeviceSearchResultContainer deviceSearchResultCont = extendedCont
                            .getDeviceSearchResultContainer();
                    return deviceSearchResultCont.hasQueryLength() ? deviceSearchResultCont
                            .getQueryLength() : -1;
                }
            default:
                return info.getFolderIcon().getCardinality();
        }
    }

    private static String getPackageName(LauncherAtom.ItemInfo info) {
        switch (info.getItemCase()) {
            case APPLICATION:
                return info.getApplication().getPackageName();
            case SHORTCUT:
                return info.getShortcut().getShortcutName();
            case WIDGET:
                return info.getWidget().getPackageName();
            case TASK:
                return info.getTask().getPackageName();
            case SEARCH_ACTION_ITEM:
                return info.getSearchActionItem().getPackageName();
            default:
                return null;
        }
    }

    private static String getComponentName(LauncherAtom.ItemInfo info) {
        switch (info.getItemCase()) {
            case APPLICATION:
                return info.getApplication().getComponentName();
            case SHORTCUT:
                return info.getShortcut().getShortcutName();
            case WIDGET:
                return info.getWidget().getComponentName();
            case TASK:
                return info.getTask().getComponentName();
            case SEARCH_ACTION_ITEM:
                return info.getSearchActionItem().getTitle();
            case SLICE:
                return info.getSlice().getUri();
            default:
                return null;
        }
    }

    private static int getGridX(LauncherAtom.ItemInfo info, boolean parent) {
        LauncherAtom.ContainerInfo containerInfo = info.getContainerInfo();
        if (containerInfo.getContainerCase() == FOLDER) {
            if (parent) {
                return containerInfo.getFolder().getWorkspace().getGridX();
            } else {
                return containerInfo.getFolder().getGridX();
            }
        } else if (containerInfo.getContainerCase() == EXTENDED_CONTAINERS) {
            return containerInfo.getExtendedContainers()
                    .getDeviceSearchResultContainer().getGridX();
        } else {
            return containerInfo.getWorkspace().getGridX();
        }
    }

    private static int getGridY(LauncherAtom.ItemInfo info, boolean parent) {
        if (info.getContainerInfo().getContainerCase() == FOLDER) {
            if (parent) {
                return info.getContainerInfo().getFolder().getWorkspace().getGridY();
            } else {
                return info.getContainerInfo().getFolder().getGridY();
            }
        } else {
            return info.getContainerInfo().getWorkspace().getGridY();
        }
    }

    private static int getPageId(LauncherAtom.ItemInfo info) {
        if (info.hasTask()) {
            return info.getTask().getIndex();
        }
        switch (info.getContainerInfo().getContainerCase()) {
            case FOLDER:
                return info.getContainerInfo().getFolder().getPageIndex();
            case HOTSEAT:
                return info.getContainerInfo().getHotseat().getIndex();
            case PREDICTED_HOTSEAT_CONTAINER:
                return info.getContainerInfo().getPredictedHotseatContainer().getIndex();
            case TASK_BAR_CONTAINER:
                return info.getContainerInfo().getTaskBarContainer().getIndex();
            default:
                return info.getContainerInfo().getWorkspace().getPageIndex();
        }
    }

    private static int getParentPageId(LauncherAtom.ItemInfo info) {
        switch (info.getContainerInfo().getContainerCase()) {
            case FOLDER:
                if (info.getContainerInfo().getFolder().getParentContainerCase()
                        == ParentContainerCase.HOTSEAT) {
                    return info.getContainerInfo().getFolder().getHotseat().getIndex();
                }
                return info.getContainerInfo().getFolder().getWorkspace().getPageIndex();
            case SEARCH_RESULT_CONTAINER:
                return info.getContainerInfo().getSearchResultContainer().getWorkspace()
                        .getPageIndex();
            default:
                return info.getContainerInfo().getWorkspace().getPageIndex();
        }
    }

    private static int getHierarchy(LauncherAtom.ItemInfo info) {
        if (Utilities.isRunningInTestHarness()) {
            return 0;
        }
        if (info.getContainerInfo().getContainerCase() == FOLDER) {
            return info.getContainerInfo().getFolder().getParentContainerCase().getNumber()
                    + FOLDER_HIERARCHY_OFFSET;
        } else if (info.getContainerInfo().getContainerCase() == SEARCH_RESULT_CONTAINER) {
            return info.getContainerInfo().getSearchResultContainer().getParentContainerCase()
                    .getNumber() + SEARCH_RESULT_HIERARCHY_OFFSET;
        } else if (info.getContainerInfo().getContainerCase() == EXTENDED_CONTAINERS) {
            return info.getContainerInfo().getExtendedContainers().getContainerCase().getNumber()
                    + EXTENDED_CONTAINERS_HIERARCHY_OFFSET;
        } else if (info.getContainerInfo().getContainerCase() == ALL_APPS_CONTAINER) {
            return info.getContainerInfo().getAllAppsContainer().getParentContainerCase()
                    .getNumber() + ALL_APPS_HIERARCHY_OFFSET;
        } else {
            return info.getContainerInfo().getContainerCase().getNumber();
        }
    }

    private static String getStateString(int state) {
        switch (state) {
            case LAUNCHER_UICHANGED__DST_STATE__BACKGROUND:
                return "BACKGROUND";
            case LAUNCHER_UICHANGED__DST_STATE__HOME:
                return "HOME";
            case LAUNCHER_UICHANGED__DST_STATE__OVERVIEW:
                return "OVERVIEW";
            case LAUNCHER_UICHANGED__DST_STATE__ALLAPPS:
                return "ALLAPPS";
            default:
                return "INVALID";
        }
    }

    private static int getFeatures(LauncherAtom.ItemInfo info) {
        if (info.getItemCase().equals(LauncherAtom.ItemInfo.ItemCase.WIDGET)) {
            return info.getWidget().getWidgetFeatures();
        }
        return 0;
    }

    private static int getSearchAttributes(LauncherAtom.ItemInfo info) {
        if (Utilities.isRunningInTestHarness()) {
            return 0;
        }
        ContainerInfo containerInfo = info.getContainerInfo();
        if (containerInfo.getContainerCase() == EXTENDED_CONTAINERS
                && containerInfo.getExtendedContainers().getContainerCase()
                == DEVICE_SEARCH_RESULT_CONTAINER
                && containerInfo.getExtendedContainers()
                .getDeviceSearchResultContainer().hasSearchAttributes()
        ) {
            return searchAttributesToInt(containerInfo.getExtendedContainers()
                    .getDeviceSearchResultContainer().getSearchAttributes());
        }
        return 0;
    }

    private static int searchAttributesToInt(SearchAttributes searchAttributes) {
        int response = 0;
        if (searchAttributes.getCorrectedQuery()) {
            response = response | SEARCH_ATTRIBUTES_CORRECTED_QUERY;
        }
        if (searchAttributes.getDirectMatch()) {
            response = response | SEARCH_ATTRIBUTES_DIRECT_MATCH;
        }
        if (searchAttributes.getEntryState() == SearchAttributes.EntryState.ALL_APPS) {
            response = response | SEARCH_ATTRIBUTES_ENTRY_STATE_ALL_APPS;
        } else if (searchAttributes.getEntryState() == SearchAttributes.EntryState.QSB) {
            response = response | SEARCH_ATTRIBUTES_ENTRY_STATE_QSB;
        } else if (searchAttributes.getEntryState() == SearchAttributes.EntryState.OVERVIEW) {
            response = response | SEARCH_ATTRIBUTES_ENTRY_STATE_OVERVIEW;
        } else if (searchAttributes.getEntryState() == SearchAttributes.EntryState.TASKBAR) {
            response = response | SEARCH_ATTRIBUTES_ENTRY_STATE_TASKBAR;
        }

        return response;
    }

    /**
     * Interface to get stats log while it is dispatched to the system
     */
    public interface StatsLogConsumer {

        @WorkerThread
        void consume(EventEnum event, LauncherAtom.ItemInfo atomInfo);
    }
}
