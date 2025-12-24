package com.android.server.am;

/** @hide */
public final class FeatureFlagsImpl implements FeatureFlags {
    @Override


    public boolean appStartInfoProcessNameFix() {
        return true;
    }

    @Override


    public boolean autoTriggerOomadjUpdates() {
        return false;
    }

    @Override


    public boolean bfgsManagedNetworkAccess() {
        return false;
    }

    @Override


    public boolean collectLogcatOnRunSynchronously() {
        return true;
    }

    @Override


    public boolean consolidateCollectReachable() {
        return true;
    }

    @Override


    public boolean cpuTimeCapabilityBasedFreezePolicy() {
        return true;
    }

    @Override


    public boolean deferOutgoingBroadcasts() {
        return false;
    }

    @Override


    public boolean enableActivityManagerStructuredService() {
        return false;
    }

    @Override


    public boolean enableDropboxWatchdogHeaders() {
        return true;
    }

    @Override


    public boolean enableLongMethodTracingOnAnrTimer() {
        return false;
    }

    @Override


    public boolean expediteActivityLaunchOnColdStart() {
        return false;
    }

    @Override


    public boolean fgsAbuseDetection() {
        return false;
    }

    @Override


    public boolean fixApplyOomadjOrder() {
        return false;
    }

    @Override


    public boolean limitLogcatCollection() {
        return true;
    }

    @Override


    public boolean logBroadcastProcessedEvent() {
        return true;
    }

    @Override


    public boolean logcatLongerTimeout() {
        return true;
    }

    @Override


    public boolean lowerSmsOomImportance() {
        return true;
    }

    @Override


    public boolean notSkipConnectionRecomputeForBindScheduleLikeTopApp() {
        return true;
    }

    @Override


    public boolean oomadjusterCachedAppTiers() {
        return false;
    }

    @Override


    public boolean oomadjusterPrevLaddering() {
        return false;
    }

    @Override


    public boolean oomadjusterVisLaddering() {
        return false;
    }

    @Override


    public boolean perceptibleTasks() {
        return false;
    }

    @Override


    public boolean prototypeAggressiveFreezing() {
        return false;
    }

    @Override


    public boolean pscBatchServiceUpdates() {
        return false;
    }

    @Override


    public boolean pscBatchUpdate() {
        return false;
    }

    @Override


    public boolean pushActivityStateToOomadjuster() {
        return true;
    }

    @Override


    public boolean pushBroadcastStateToOomadjuster() {
        return true;
    }

    @Override


    public boolean pushGlobalStateToOomadjuster() {
        return true;
    }

    @Override


    public boolean removeLruSpamPrevention() {
        return false;
    }

    @Override


    public boolean rolloutComputerControl() {
        return false;
    }

    @Override


    public boolean skipUnimportantConnections() {
        return false;
    }

    @Override


    public boolean syncmanagerOffMainThread() {
        return false;
    }

    @Override


    public boolean traceUpdateAppFreezeStateLsp() {
        return true;
    }

    @Override


    public boolean useMemcgForCompaction() {
        return false;
    }

}
