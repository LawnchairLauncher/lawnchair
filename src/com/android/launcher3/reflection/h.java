package com.android.launcher3.reflection;

import android.content.SharedPreferences;
import android.util.Log;

import com.android.launcher3.logging.FileLog;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.reflection.common.nano.a;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

class h implements Runnable
{
    private e aN;
    final /* synthetic */ g aO;

    public h(final g ao) {
        this.aO = ao;
    }

    private void au(long x) {
        final int n = 1;
        Preconditions.assertNonUiThread();
        final long currentTimeMillis = System.currentTimeMillis();
        FileLog.d("Reflection.StBatchTrain", "Start loading events from logs...");
        while (true) {
            Object o = this.aO;
            Object o2;
            synchronized (o) {
                if (this.aO.aI != this) {
                    return;
                }
                o2 = this.aO.aJ.R(x, 1000);

                if (o2 == null || ((com.android.launcher3.reflection.b2.e)o2).W.isEmpty()) {
                    o2 = System.currentTimeMillis() - currentTimeMillis;
                    o = String.format("Retrain finished, total time including loading: %dms", new Object[] { o2 });
                    FileLog.d("Reflection.StBatchTrain", (String)o);
                    return;
                }
            }
            final List w = ((com.android.launcher3.reflection.b2.e)o2).W;
            final Object[] array = { w.size(), null };
            array[n] = System.currentTimeMillis() - currentTimeMillis;
            FileLog.d("Reflection.StBatchTrain", String.format("Num events loaded: %d, time taken so far: %dms", array));
            for (Object o3 : w) {
                final a a = (a) o3;
                synchronized (this.aO) {
                    if (this.aO.aI == this) {
                        this.aN.aa(a.LA, a);
                        if (a.Ly.startsWith("/deleted_app/")) {
                            continue;
                        }
                        final com.android.launcher3.reflection.predictor.e z = this.aN.Z();
                        if (z != null) {
                            try {
                                z.Sk(a);
                            }
                            catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }
                    }
                }
            }
            x = ((com.android.launcher3.reflection.b2.e)o2).X;
            final Locale us = Locale.US;
            final Object[] array2 = new Object[n];
            array2[0] = x;
            final String format = String.format(us, "InProgress:%d", array2);
            final Object[] array3 = new Object[n];
            array3[0] = format;
            o2 = String.format("Progress: %s", array3);
            FileLog.d("Reflection.StBatchTrain", (String)o2);
            synchronized (this.aO) {
                o2 = this.aO;
                o2 = ((g)o2).aI;
                if (o2 != this) {
                    return;
                }
                o2 = this.aO;
                o2 = ((g)o2).aM;
                o2 = ((SharedPreferences)o2).edit();
                ((SharedPreferences.Editor)o2).putString("staged_batch_training_progress", format).apply();
                this.aN.af();
            }
        }
    }

    private e av() {
        Preconditions.assertNonUiThread();

        String status = aO.aM.getString("staged_batch_training_progress", "Success");
        if (!status.equals("Success")) {
            aN = new e(aO.aK, aO.aJ, aO.aM, "foreground_evt_buf.properties", this);
            aN.ag(aO.aH);
            aO.aK.f();
            if (!status.equals("New")) {
                Matcher matcher = g.aG.matcher(status);
                if (!matcher.find()) {
                    Long result = Long.parseLong(matcher.group());
                    aN.ab();
                    au(result);
                    if (aO.aI == null) {
                        aO.aM.edit().putString("staged_batch_training_progress", "Success").apply();
                    }
                } else {
                    Log.e("Reflection.StBatchTrain", "Invalid progress string.");
                }
            }
        }
        return aN;
    }

    public void run() {
        try {
            this.aO.al(this.av(), this);
        }
        finally {
            this.aO.ak(null, this);
        }
    }
}