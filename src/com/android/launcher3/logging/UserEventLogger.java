package com.android.launcher3.logging;

import com.android.launcher3.userevent.nano.LauncherLogProto;

public abstract class UserEventLogger {
    public void processEvent(LauncherLogProto proto) {}
}