package com.android.server.am;


/** @hide */
public final class Flags {
    /** @hide */
    public static final String FLAG_APP_START_INFO_PROCESS_NAME_FIX = "com.android.server.am.app_start_info_process_name_fix";
    /** @hide */
    public static final String FLAG_AUTO_TRIGGER_OOMADJ_UPDATES = "com.android.server.am.auto_trigger_oomadj_updates";
    /** @hide */
    public static final String FLAG_BFGS_MANAGED_NETWORK_ACCESS = "com.android.server.am.bfgs_managed_network_access";
    /** @hide */
    public static final String FLAG_COLLECT_LOGCAT_ON_RUN_SYNCHRONOUSLY = "com.android.server.am.collect_logcat_on_run_synchronously";
    /** @hide */
    public static final String FLAG_CONSOLIDATE_COLLECT_REACHABLE = "com.android.server.am.consolidate_collect_reachable";
    /** @hide */
    public static final String FLAG_CPU_TIME_CAPABILITY_BASED_FREEZE_POLICY = "com.android.server.am.cpu_time_capability_based_freeze_policy";
    /** @hide */
    public static final String FLAG_DEFER_OUTGOING_BROADCASTS = "com.android.server.am.defer_outgoing_broadcasts";
    /** @hide */
    public static final String FLAG_ENABLE_ACTIVITY_MANAGER_STRUCTURED_SERVICE = "com.android.server.am.enable_activity_manager_structured_service";
    /** @hide */
    public static final String FLAG_ENABLE_DROPBOX_WATCHDOG_HEADERS = "com.android.server.am.enable_dropbox_watchdog_headers";
    /** @hide */
    public static final String FLAG_ENABLE_LONG_METHOD_TRACING_ON_ANR_TIMER = "com.android.server.am.enable_long_method_tracing_on_anr_timer";
    /** @hide */
    public static final String FLAG_EXPEDITE_ACTIVITY_LAUNCH_ON_COLD_START = "com.android.server.am.expedite_activity_launch_on_cold_start";
    /** @hide */
    public static final String FLAG_FGS_ABUSE_DETECTION = "com.android.server.am.fgs_abuse_detection";
    /** @hide */
    public static final String FLAG_FIX_APPLY_OOMADJ_ORDER = "com.android.server.am.fix_apply_oomadj_order";
    /** @hide */
    public static final String FLAG_LIMIT_LOGCAT_COLLECTION = "com.android.server.am.limit_logcat_collection";
    /** @hide */
    public static final String FLAG_LOG_BROADCAST_PROCESSED_EVENT = "com.android.server.am.log_broadcast_processed_event";
    /** @hide */
    public static final String FLAG_LOGCAT_LONGER_TIMEOUT = "com.android.server.am.logcat_longer_timeout";
    /** @hide */
    public static final String FLAG_LOWER_SMS_OOM_IMPORTANCE = "com.android.server.am.lower_sms_oom_importance";
    /** @hide */
    public static final String FLAG_NOT_SKIP_CONNECTION_RECOMPUTE_FOR_BIND_SCHEDULE_LIKE_TOP_APP = "com.android.server.am.not_skip_connection_recompute_for_bind_schedule_like_top_app";
    /** @hide */
    public static final String FLAG_OOMADJUSTER_CACHED_APP_TIERS = "com.android.server.am.oomadjuster_cached_app_tiers";
    /** @hide */
    public static final String FLAG_OOMADJUSTER_PREV_LADDERING = "com.android.server.am.oomadjuster_prev_laddering";
    /** @hide */
    public static final String FLAG_OOMADJUSTER_VIS_LADDERING = "com.android.server.am.oomadjuster_vis_laddering";
    /** @hide */
    public static final String FLAG_PERCEPTIBLE_TASKS = "com.android.server.am.perceptible_tasks";
    /** @hide */
    public static final String FLAG_PROTOTYPE_AGGRESSIVE_FREEZING = "com.android.server.am.prototype_aggressive_freezing";
    /** @hide */
    public static final String FLAG_PSC_BATCH_SERVICE_UPDATES = "com.android.server.am.psc_batch_service_updates";
    /** @hide */
    public static final String FLAG_PSC_BATCH_UPDATE = "com.android.server.am.psc_batch_update";
    /** @hide */
    public static final String FLAG_PUSH_ACTIVITY_STATE_TO_OOMADJUSTER = "com.android.server.am.push_activity_state_to_oomadjuster";
    /** @hide */
    public static final String FLAG_PUSH_BROADCAST_STATE_TO_OOMADJUSTER = "com.android.server.am.push_broadcast_state_to_oomadjuster";
    /** @hide */
    public static final String FLAG_PUSH_GLOBAL_STATE_TO_OOMADJUSTER = "com.android.server.am.push_global_state_to_oomadjuster";
    /** @hide */
    public static final String FLAG_REMOVE_LRU_SPAM_PREVENTION = "com.android.server.am.remove_lru_spam_prevention";
    /** @hide */
    public static final String FLAG_ROLLOUT_COMPUTER_CONTROL = "com.android.server.am.rollout_computer_control";
    /** @hide */
    public static final String FLAG_SKIP_UNIMPORTANT_CONNECTIONS = "com.android.server.am.skip_unimportant_connections";
    /** @hide */
    public static final String FLAG_SYNCMANAGER_OFF_MAIN_THREAD = "com.android.server.am.syncmanager_off_main_thread";
    /** @hide */
    public static final String FLAG_TRACE_UPDATE_APP_FREEZE_STATE_LSP = "com.android.server.am.trace_update_app_freeze_state_lsp";
    /** @hide */
    public static final String FLAG_USE_MEMCG_FOR_COMPACTION = "com.android.server.am.use_memcg_for_compaction";


    public static boolean appStartInfoProcessNameFix() {
        
        return FEATURE_FLAGS.appStartInfoProcessNameFix();
    }


    public static boolean autoTriggerOomadjUpdates() {
        
        return FEATURE_FLAGS.autoTriggerOomadjUpdates();
    }


    public static boolean bfgsManagedNetworkAccess() {
        
        return FEATURE_FLAGS.bfgsManagedNetworkAccess();
    }


    public static boolean collectLogcatOnRunSynchronously() {
        
        return FEATURE_FLAGS.collectLogcatOnRunSynchronously();
    }


    public static boolean consolidateCollectReachable() {
        
        return FEATURE_FLAGS.consolidateCollectReachable();
    }


    public static boolean cpuTimeCapabilityBasedFreezePolicy() {
        
        return FEATURE_FLAGS.cpuTimeCapabilityBasedFreezePolicy();
    }


    public static boolean deferOutgoingBroadcasts() {
        
        return FEATURE_FLAGS.deferOutgoingBroadcasts();
    }


    public static boolean enableActivityManagerStructuredService() {
        
        return FEATURE_FLAGS.enableActivityManagerStructuredService();
    }


    public static boolean enableDropboxWatchdogHeaders() {
        
        return FEATURE_FLAGS.enableDropboxWatchdogHeaders();
    }


    public static boolean enableLongMethodTracingOnAnrTimer() {
        
        return FEATURE_FLAGS.enableLongMethodTracingOnAnrTimer();
    }


    public static boolean expediteActivityLaunchOnColdStart() {
        
        return FEATURE_FLAGS.expediteActivityLaunchOnColdStart();
    }


    public static boolean fgsAbuseDetection() {
        
        return FEATURE_FLAGS.fgsAbuseDetection();
    }


    public static boolean fixApplyOomadjOrder() {
        
        return FEATURE_FLAGS.fixApplyOomadjOrder();
    }


    public static boolean limitLogcatCollection() {
        
        return FEATURE_FLAGS.limitLogcatCollection();
    }


    public static boolean logBroadcastProcessedEvent() {
        
        return FEATURE_FLAGS.logBroadcastProcessedEvent();
    }


    public static boolean logcatLongerTimeout() {
        
        return FEATURE_FLAGS.logcatLongerTimeout();
    }


    public static boolean lowerSmsOomImportance() {
        
        return FEATURE_FLAGS.lowerSmsOomImportance();
    }


    public static boolean notSkipConnectionRecomputeForBindScheduleLikeTopApp() {
        
        return FEATURE_FLAGS.notSkipConnectionRecomputeForBindScheduleLikeTopApp();
    }


    public static boolean oomadjusterCachedAppTiers() {
        
        return FEATURE_FLAGS.oomadjusterCachedAppTiers();
    }


    public static boolean oomadjusterPrevLaddering() {
        
        return FEATURE_FLAGS.oomadjusterPrevLaddering();
    }


    public static boolean oomadjusterVisLaddering() {
        
        return FEATURE_FLAGS.oomadjusterVisLaddering();
    }


    public static boolean perceptibleTasks() {
        
        return FEATURE_FLAGS.perceptibleTasks();
    }


    public static boolean prototypeAggressiveFreezing() {
        
        return FEATURE_FLAGS.prototypeAggressiveFreezing();
    }


    public static boolean pscBatchServiceUpdates() {
        
        return FEATURE_FLAGS.pscBatchServiceUpdates();
    }


    public static boolean pscBatchUpdate() {
        
        return FEATURE_FLAGS.pscBatchUpdate();
    }


    public static boolean pushActivityStateToOomadjuster() {
        
        return FEATURE_FLAGS.pushActivityStateToOomadjuster();
    }


    public static boolean pushBroadcastStateToOomadjuster() {
        
        return FEATURE_FLAGS.pushBroadcastStateToOomadjuster();
    }


    public static boolean pushGlobalStateToOomadjuster() {
        
        return FEATURE_FLAGS.pushGlobalStateToOomadjuster();
    }


    public static boolean removeLruSpamPrevention() {
        
        return FEATURE_FLAGS.removeLruSpamPrevention();
    }


    public static boolean rolloutComputerControl() {
        
        return FEATURE_FLAGS.rolloutComputerControl();
    }


    public static boolean skipUnimportantConnections() {
        
        return FEATURE_FLAGS.skipUnimportantConnections();
    }


    public static boolean syncmanagerOffMainThread() {
        
        return FEATURE_FLAGS.syncmanagerOffMainThread();
    }


    public static boolean traceUpdateAppFreezeStateLsp() {
        
        return FEATURE_FLAGS.traceUpdateAppFreezeStateLsp();
    }


    public static boolean useMemcgForCompaction() {
        
        return FEATURE_FLAGS.useMemcgForCompaction();
    }

    private static FeatureFlags FEATURE_FLAGS = new FeatureFlagsImpl();

}
