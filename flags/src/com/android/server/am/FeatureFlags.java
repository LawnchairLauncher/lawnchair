package com.android.server.am;

/** @hide */
public interface FeatureFlags {




    boolean appStartInfoProcessNameFix();



    boolean autoTriggerOomadjUpdates();



    boolean bfgsManagedNetworkAccess();



    boolean collectLogcatOnRunSynchronously();



    boolean consolidateCollectReachable();



    boolean cpuTimeCapabilityBasedFreezePolicy();



    boolean deferOutgoingBroadcasts();



    boolean enableActivityManagerStructuredService();



    boolean enableDropboxWatchdogHeaders();



    boolean enableLongMethodTracingOnAnrTimer();



    boolean expediteActivityLaunchOnColdStart();



    boolean fgsAbuseDetection();



    boolean fixApplyOomadjOrder();



    boolean limitLogcatCollection();



    boolean logBroadcastProcessedEvent();



    boolean logcatLongerTimeout();



    boolean lowerSmsOomImportance();



    boolean notSkipConnectionRecomputeForBindScheduleLikeTopApp();



    boolean oomadjusterCachedAppTiers();



    boolean oomadjusterPrevLaddering();



    boolean oomadjusterVisLaddering();



    boolean perceptibleTasks();



    boolean prototypeAggressiveFreezing();



    boolean pscBatchServiceUpdates();



    boolean pscBatchUpdate();



    boolean pushActivityStateToOomadjuster();



    boolean pushBroadcastStateToOomadjuster();



    boolean pushGlobalStateToOomadjuster();



    boolean removeLruSpamPrevention();



    boolean rolloutComputerControl();



    boolean skipUnimportantConnections();



    boolean syncmanagerOffMainThread();



    boolean traceUpdateAppFreezeStateLsp();



    boolean useMemcgForCompaction();
}
