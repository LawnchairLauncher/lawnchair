package com.android.launcher3.reflectionevents;

import java.util.Iterator;

import com.android.launcher3.userevent.nano.LauncherLogProto;
import android.content.Intent;
import com.android.launcher3.logging.LoggerUtils;
import java.util.ArrayList;
import java.util.List;
import com.android.launcher3.logging.UserEventDispatcher;

public class a extends UserEventDispatcher
{
    private List cw;

    public a() {
        this.cw = new ArrayList();
    }

    public void bW(final b b) {
        this.cw.add(b);
    }

    public void bX(final int spanX) {
        final LauncherLogProto.LauncherEvent initLauncherEvent = LoggerUtils.initLauncherEvent(0, 1);
        initLauncherEvent.action.touch = 0;
        initLauncherEvent.srcTarget[0].itemType = 6;
        initLauncherEvent.srcTarget[0].spanX = spanX;
        this.dispatchUserEvent(initLauncherEvent, null);
    }

    public void dispatchUserEvent(final LauncherLogProto.LauncherEvent launcherEvent, final Intent intent) {
        super.dispatchUserEvent(launcherEvent, intent);
        final Iterator iterator = this.cw.iterator();
        while (iterator.hasNext()) {
            ((b)iterator.next()).aH(launcherEvent, intent);
        }
    }
}