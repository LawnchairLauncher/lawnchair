package com.android.launcher3.reflection.a2;

import android.app.usage.UsageEvents;
import com.android.launcher3.reflection.m;
import android.content.ComponentName;
import android.app.usage.UsageEvents;
import com.android.launcher3.reflection.nano.b;
import java.util.ArrayList;
import java.util.List;
import android.app.usage.UsageStatsManager;

public class a implements c
{
    private final UsageStatsManager C;

    public a(final UsageStatsManager c) {
        this.C = c;
    }

    public List w(final long n) {
        final ArrayList<b> list = new ArrayList<>();
        final long currentTimeMillis = System.currentTimeMillis();
        final UsageEvents queryEvents = this.C.queryEvents(currentTimeMillis - n, currentTimeMillis);
        final UsageEvents.Event usageEvents$Event = new UsageEvents.Event();
        while (queryEvents.hasNextEvent()) {
            queryEvents.getNextEvent(usageEvents$Event);
            if (usageEvents$Event.getEventType() == 1) {
                final b b = new b();
                b.LL = "app_usage";
                b.LM = usageEvents$Event.getTimeStamp();
                b.LK = m.aK(new ComponentName(usageEvents$Event.getPackageName(), usageEvents$Event.getClassName()));
                list.add(b);
            }
        }
        return list;
    }

    public void x(final com.android.launcher3.reflection.nano.a a) {
        //com.google.research.reflection.common.a.Sz(a, "app_usage", this.w(600000L));
    }
}