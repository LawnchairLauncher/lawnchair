package com.android.launcher3;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class StartupReceiver extends BroadcastReceiver {

    static final String SYESTEM_READY = "com.android.launcher3.SYESTEM_READY";

    @Override
    public void onReceive(Context context, Intent intent) {
        context.sendStickyBroadcast(new Intent(SYESTEM_READY));
    }
}
