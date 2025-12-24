package com.android.server.am;


import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
/** @hide */
public class CustomFeatureFlags implements FeatureFlags {

    private BiPredicate<String, Predicate<FeatureFlags>> mGetValueImpl;

    public CustomFeatureFlags(BiPredicate<String, Predicate<FeatureFlags>> getValueImpl) {
        mGetValueImpl = getValueImpl;
    }
    @Override

    public boolean appStartInfoProcessNameFix() {
        return getValue(Flags.FLAG_APP_START_INFO_PROCESS_NAME_FIX,
            FeatureFlags::appStartInfoProcessNameFix);
    }

    @Override

    public boolean autoTriggerOomadjUpdates() {
        return getValue(Flags.FLAG_AUTO_TRIGGER_OOMADJ_UPDATES,
            FeatureFlags::autoTriggerOomadjUpdates);
    }

    @Override

    public boolean bfgsManagedNetworkAccess() {
        return getValue(Flags.FLAG_BFGS_MANAGED_NETWORK_ACCESS,
            FeatureFlags::bfgsManagedNetworkAccess);
    }

    @Override

    public boolean collectLogcatOnRunSynchronously() {
        return getValue(Flags.FLAG_COLLECT_LOGCAT_ON_RUN_SYNCHRONOUSLY,
            FeatureFlags::collectLogcatOnRunSynchronously);
    }

    @Override

    public boolean consolidateCollectReachable() {
        return getValue(Flags.FLAG_CONSOLIDATE_COLLECT_REACHABLE,
            FeatureFlags::consolidateCollectReachable);
    }

    @Override

    public boolean cpuTimeCapabilityBasedFreezePolicy() {
        return getValue(Flags.FLAG_CPU_TIME_CAPABILITY_BASED_FREEZE_POLICY,
            FeatureFlags::cpuTimeCapabilityBasedFreezePolicy);
    }

    @Override

    public boolean deferOutgoingBroadcasts() {
        return getValue(Flags.FLAG_DEFER_OUTGOING_BROADCASTS,
            FeatureFlags::deferOutgoingBroadcasts);
    }

    @Override

    public boolean enableActivityManagerStructuredService() {
        return getValue(Flags.FLAG_ENABLE_ACTIVITY_MANAGER_STRUCTURED_SERVICE,
            FeatureFlags::enableActivityManagerStructuredService);
    }

    @Override

    public boolean enableDropboxWatchdogHeaders() {
        return getValue(Flags.FLAG_ENABLE_DROPBOX_WATCHDOG_HEADERS,
            FeatureFlags::enableDropboxWatchdogHeaders);
    }

    @Override

    public boolean enableLongMethodTracingOnAnrTimer() {
        return getValue(Flags.FLAG_ENABLE_LONG_METHOD_TRACING_ON_ANR_TIMER,
            FeatureFlags::enableLongMethodTracingOnAnrTimer);
    }

    @Override

    public boolean expediteActivityLaunchOnColdStart() {
        return getValue(Flags.FLAG_EXPEDITE_ACTIVITY_LAUNCH_ON_COLD_START,
            FeatureFlags::expediteActivityLaunchOnColdStart);
    }

    @Override

    public boolean fgsAbuseDetection() {
        return getValue(Flags.FLAG_FGS_ABUSE_DETECTION,
            FeatureFlags::fgsAbuseDetection);
    }

    @Override

    public boolean fixApplyOomadjOrder() {
        return getValue(Flags.FLAG_FIX_APPLY_OOMADJ_ORDER,
            FeatureFlags::fixApplyOomadjOrder);
    }

    @Override

    public boolean limitLogcatCollection() {
        return getValue(Flags.FLAG_LIMIT_LOGCAT_COLLECTION,
            FeatureFlags::limitLogcatCollection);
    }

    @Override

    public boolean logBroadcastProcessedEvent() {
        return getValue(Flags.FLAG_LOG_BROADCAST_PROCESSED_EVENT,
            FeatureFlags::logBroadcastProcessedEvent);
    }

    @Override

    public boolean logcatLongerTimeout() {
        return getValue(Flags.FLAG_LOGCAT_LONGER_TIMEOUT,
            FeatureFlags::logcatLongerTimeout);
    }

    @Override

    public boolean lowerSmsOomImportance() {
        return getValue(Flags.FLAG_LOWER_SMS_OOM_IMPORTANCE,
            FeatureFlags::lowerSmsOomImportance);
    }

    @Override

    public boolean notSkipConnectionRecomputeForBindScheduleLikeTopApp() {
        return getValue(Flags.FLAG_NOT_SKIP_CONNECTION_RECOMPUTE_FOR_BIND_SCHEDULE_LIKE_TOP_APP,
            FeatureFlags::notSkipConnectionRecomputeForBindScheduleLikeTopApp);
    }

    @Override

    public boolean oomadjusterCachedAppTiers() {
        return getValue(Flags.FLAG_OOMADJUSTER_CACHED_APP_TIERS,
            FeatureFlags::oomadjusterCachedAppTiers);
    }

    @Override

    public boolean oomadjusterPrevLaddering() {
        return getValue(Flags.FLAG_OOMADJUSTER_PREV_LADDERING,
            FeatureFlags::oomadjusterPrevLaddering);
    }

    @Override

    public boolean oomadjusterVisLaddering() {
        return getValue(Flags.FLAG_OOMADJUSTER_VIS_LADDERING,
            FeatureFlags::oomadjusterVisLaddering);
    }

    @Override

    public boolean perceptibleTasks() {
        return getValue(Flags.FLAG_PERCEPTIBLE_TASKS,
            FeatureFlags::perceptibleTasks);
    }

    @Override

    public boolean prototypeAggressiveFreezing() {
        return getValue(Flags.FLAG_PROTOTYPE_AGGRESSIVE_FREEZING,
            FeatureFlags::prototypeAggressiveFreezing);
    }

    @Override

    public boolean pscBatchServiceUpdates() {
        return getValue(Flags.FLAG_PSC_BATCH_SERVICE_UPDATES,
            FeatureFlags::pscBatchServiceUpdates);
    }

    @Override

    public boolean pscBatchUpdate() {
        return getValue(Flags.FLAG_PSC_BATCH_UPDATE,
            FeatureFlags::pscBatchUpdate);
    }

    @Override

    public boolean pushActivityStateToOomadjuster() {
        return getValue(Flags.FLAG_PUSH_ACTIVITY_STATE_TO_OOMADJUSTER,
            FeatureFlags::pushActivityStateToOomadjuster);
    }

    @Override

    public boolean pushBroadcastStateToOomadjuster() {
        return getValue(Flags.FLAG_PUSH_BROADCAST_STATE_TO_OOMADJUSTER,
            FeatureFlags::pushBroadcastStateToOomadjuster);
    }

    @Override

    public boolean pushGlobalStateToOomadjuster() {
        return getValue(Flags.FLAG_PUSH_GLOBAL_STATE_TO_OOMADJUSTER,
            FeatureFlags::pushGlobalStateToOomadjuster);
    }

    @Override

    public boolean removeLruSpamPrevention() {
        return getValue(Flags.FLAG_REMOVE_LRU_SPAM_PREVENTION,
            FeatureFlags::removeLruSpamPrevention);
    }

    @Override

    public boolean rolloutComputerControl() {
        return getValue(Flags.FLAG_ROLLOUT_COMPUTER_CONTROL,
            FeatureFlags::rolloutComputerControl);
    }

    @Override

    public boolean skipUnimportantConnections() {
        return getValue(Flags.FLAG_SKIP_UNIMPORTANT_CONNECTIONS,
            FeatureFlags::skipUnimportantConnections);
    }

    @Override

    public boolean syncmanagerOffMainThread() {
        return getValue(Flags.FLAG_SYNCMANAGER_OFF_MAIN_THREAD,
            FeatureFlags::syncmanagerOffMainThread);
    }

    @Override

    public boolean traceUpdateAppFreezeStateLsp() {
        return getValue(Flags.FLAG_TRACE_UPDATE_APP_FREEZE_STATE_LSP,
            FeatureFlags::traceUpdateAppFreezeStateLsp);
    }

    @Override

    public boolean useMemcgForCompaction() {
        return getValue(Flags.FLAG_USE_MEMCG_FOR_COMPACTION,
            FeatureFlags::useMemcgForCompaction);
    }

    public boolean isFlagReadOnlyOptimized(String flagName) {
        if (mReadOnlyFlagsSet.contains(flagName) &&
            isOptimizationEnabled()) {
                return true;
        }
        return false;
    }


    private boolean isOptimizationEnabled() {
        return false;
    }

    protected boolean getValue(String flagName, Predicate<FeatureFlags> getter) {
        return mGetValueImpl.test(flagName, getter);
    }

    public List<String> getFlagNames() {
        return Arrays.asList(
            Flags.FLAG_APP_START_INFO_PROCESS_NAME_FIX,
            Flags.FLAG_AUTO_TRIGGER_OOMADJ_UPDATES,
            Flags.FLAG_BFGS_MANAGED_NETWORK_ACCESS,
            Flags.FLAG_COLLECT_LOGCAT_ON_RUN_SYNCHRONOUSLY,
            Flags.FLAG_CONSOLIDATE_COLLECT_REACHABLE,
            Flags.FLAG_CPU_TIME_CAPABILITY_BASED_FREEZE_POLICY,
            Flags.FLAG_DEFER_OUTGOING_BROADCASTS,
            Flags.FLAG_ENABLE_ACTIVITY_MANAGER_STRUCTURED_SERVICE,
            Flags.FLAG_ENABLE_DROPBOX_WATCHDOG_HEADERS,
            Flags.FLAG_ENABLE_LONG_METHOD_TRACING_ON_ANR_TIMER,
            Flags.FLAG_EXPEDITE_ACTIVITY_LAUNCH_ON_COLD_START,
            Flags.FLAG_FGS_ABUSE_DETECTION,
            Flags.FLAG_FIX_APPLY_OOMADJ_ORDER,
            Flags.FLAG_LIMIT_LOGCAT_COLLECTION,
            Flags.FLAG_LOG_BROADCAST_PROCESSED_EVENT,
            Flags.FLAG_LOGCAT_LONGER_TIMEOUT,
            Flags.FLAG_LOWER_SMS_OOM_IMPORTANCE,
            Flags.FLAG_NOT_SKIP_CONNECTION_RECOMPUTE_FOR_BIND_SCHEDULE_LIKE_TOP_APP,
            Flags.FLAG_OOMADJUSTER_CACHED_APP_TIERS,
            Flags.FLAG_OOMADJUSTER_PREV_LADDERING,
            Flags.FLAG_OOMADJUSTER_VIS_LADDERING,
            Flags.FLAG_PERCEPTIBLE_TASKS,
            Flags.FLAG_PROTOTYPE_AGGRESSIVE_FREEZING,
            Flags.FLAG_PSC_BATCH_SERVICE_UPDATES,
            Flags.FLAG_PSC_BATCH_UPDATE,
            Flags.FLAG_PUSH_ACTIVITY_STATE_TO_OOMADJUSTER,
            Flags.FLAG_PUSH_BROADCAST_STATE_TO_OOMADJUSTER,
            Flags.FLAG_PUSH_GLOBAL_STATE_TO_OOMADJUSTER,
            Flags.FLAG_REMOVE_LRU_SPAM_PREVENTION,
            Flags.FLAG_ROLLOUT_COMPUTER_CONTROL,
            Flags.FLAG_SKIP_UNIMPORTANT_CONNECTIONS,
            Flags.FLAG_SYNCMANAGER_OFF_MAIN_THREAD,
            Flags.FLAG_TRACE_UPDATE_APP_FREEZE_STATE_LSP,
            Flags.FLAG_USE_MEMCG_FOR_COMPACTION
        );
    }

    private Set<String> mReadOnlyFlagsSet = new HashSet<>(
        Arrays.asList(
            Flags.FLAG_APP_START_INFO_PROCESS_NAME_FIX,
            Flags.FLAG_AUTO_TRIGGER_OOMADJ_UPDATES,
            Flags.FLAG_BFGS_MANAGED_NETWORK_ACCESS,
            Flags.FLAG_COLLECT_LOGCAT_ON_RUN_SYNCHRONOUSLY,
            Flags.FLAG_CONSOLIDATE_COLLECT_REACHABLE,
            Flags.FLAG_CPU_TIME_CAPABILITY_BASED_FREEZE_POLICY,
            Flags.FLAG_DEFER_OUTGOING_BROADCASTS,
            Flags.FLAG_ENABLE_ACTIVITY_MANAGER_STRUCTURED_SERVICE,
            Flags.FLAG_ENABLE_DROPBOX_WATCHDOG_HEADERS,
            Flags.FLAG_ENABLE_LONG_METHOD_TRACING_ON_ANR_TIMER,
            Flags.FLAG_EXPEDITE_ACTIVITY_LAUNCH_ON_COLD_START,
            Flags.FLAG_FGS_ABUSE_DETECTION,
            Flags.FLAG_FIX_APPLY_OOMADJ_ORDER,
            Flags.FLAG_LIMIT_LOGCAT_COLLECTION,
            Flags.FLAG_LOG_BROADCAST_PROCESSED_EVENT,
            Flags.FLAG_LOGCAT_LONGER_TIMEOUT,
            Flags.FLAG_LOWER_SMS_OOM_IMPORTANCE,
            Flags.FLAG_NOT_SKIP_CONNECTION_RECOMPUTE_FOR_BIND_SCHEDULE_LIKE_TOP_APP,
            Flags.FLAG_OOMADJUSTER_CACHED_APP_TIERS,
            Flags.FLAG_OOMADJUSTER_PREV_LADDERING,
            Flags.FLAG_OOMADJUSTER_VIS_LADDERING,
            Flags.FLAG_PERCEPTIBLE_TASKS,
            Flags.FLAG_PROTOTYPE_AGGRESSIVE_FREEZING,
            Flags.FLAG_PSC_BATCH_SERVICE_UPDATES,
            Flags.FLAG_PSC_BATCH_UPDATE,
            Flags.FLAG_PUSH_ACTIVITY_STATE_TO_OOMADJUSTER,
            Flags.FLAG_PUSH_BROADCAST_STATE_TO_OOMADJUSTER,
            Flags.FLAG_PUSH_GLOBAL_STATE_TO_OOMADJUSTER,
            Flags.FLAG_REMOVE_LRU_SPAM_PREVENTION,
            Flags.FLAG_ROLLOUT_COMPUTER_CONTROL,
            Flags.FLAG_SKIP_UNIMPORTANT_CONNECTIONS,
            Flags.FLAG_SYNCMANAGER_OFF_MAIN_THREAD,
            Flags.FLAG_TRACE_UPDATE_APP_FREEZE_STATE_LSP,
            Flags.FLAG_USE_MEMCG_FOR_COMPACTION,
            ""
        )
    );
}
