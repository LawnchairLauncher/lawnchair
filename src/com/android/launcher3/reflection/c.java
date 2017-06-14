package com.android.launcher3.reflection;

import android.app.AlarmManager;
import java.util.concurrent.TimeUnit;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.CountDownLatch;
import com.android.launcher3.util.Preconditions;
import android.util.MutableLong;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.SystemClock;
import java.util.Calendar;
import android.content.Context;

import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;

public class c implements b
{
    private final long ar;

    public c(final Context context) {
        this.ar = this.initRecordedTime(context, 1);
    }

    public long V() {
        return this.ar;
    }

    protected long getAbsoluteBootTime() {
        return Calendar.getInstance().getTimeInMillis() - SystemClock.elapsedRealtime();
    }

    protected long initRecordedTime(final Context context, final int n) {
        final int n2 = 1;
        final Intent intent = new Intent("com.google.android.apps.nexuslauncher.reflection.ACTION_BOOT_CYCLE");
        final PendingIntent broadcast = PendingIntent.getBroadcast(context, n, intent, FLAG_UPDATE_CURRENT);
        final MutableLong mutableLong = new MutableLong(this.getAbsoluteBootTime());
        if (broadcast != null) {
            Preconditions.assertNonUiThread();
            final CountDownLatch countDownLatch = new CountDownLatch(1);
            final o o = new o(this, mutableLong, countDownLatch);
            try {
                broadcast.send(n, o, new Handler(Looper.getMainLooper()));
                countDownLatch.await(1L, TimeUnit.SECONDS);
                return mutableLong.value;
            }
            catch (PendingIntent.CanceledException ex) {}
            catch (InterruptedException ex2) {}
        }
        intent.putExtra("time", mutableLong.value);
        ((AlarmManager)context.getSystemService(Context.ALARM_SERVICE)).set(n2, Long.MAX_VALUE, PendingIntent.getBroadcast(context, n, intent, FLAG_UPDATE_CURRENT));
        return mutableLong.value;
    }
}