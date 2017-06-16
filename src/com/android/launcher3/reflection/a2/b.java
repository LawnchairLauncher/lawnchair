package com.android.launcher3.reflection.a2;

import com.android.launcher3.reflection.m;
import android.content.ComponentName;

import java.util.Iterator;
import java.util.Calendar;
import android.app.usage.UsageStatsManager;
import android.os.Process;
import android.app.AppOpsManager;

import com.android.launcher3.compat.UserHandleCompat;
import com.android.launcher3.compat.UserManagerCompat;
import android.content.Context;
import java.util.List;
import java.util.ArrayList;
import com.android.launcher3.reflection.k;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

public class b implements k
{
    private final ArrayList D;
    private final GoogleApiClient E;
    private final com.android.launcher3.reflection.common.nano.a F;
    private final long G;
    private final List H;
    private final List I;

    public b(final Context context) {
        final int n = 4;
        this.D = new ArrayList();
        this.F = new com.android.launcher3.reflection.common.nano.a();
        this.H = new ArrayList(n);
        this.I = new ArrayList(n);
        this.E = this.A(context);
        this.G = UserManagerCompat.getInstance(context).getSerialNumberForUser(UserHandleCompat.myUserHandle());
        this.H.add(new d(this.E, LocationServices.FusedLocationApi));
        final e e = new e(this.F, context);
        this.I.add(e);
        this.D.add(e);
        final com.android.launcher3.reflection.a2.a d = this.D(context);
        if (d != null) {
            this.H.add(d);
        }
        this.E.connect(); //eo
        this.E();
    }

    b(final List h, final List i, final long g) {
        this.D = new ArrayList();
        this.F = new com.android.launcher3.reflection.common.nano.a();
        this.H = h;
        this.I = i;
        this.E = null;
        this.G = g;
        this.E();
    }

    private GoogleApiClient A(final Context context) {
        final com.google.android.gms.common.api.GoogleApiClient.Builder b = new com.google.android.gms.common.api.GoogleApiClient.Builder(context);
        b.addApi(LocationServices.API);
        b.useDefaultAccount();
        return b.build();
    }

    private com.android.launcher3.reflection.common.nano.a C() {
        final com.android.launcher3.reflection.common.nano.a f = this.F;
        final byte[] byteArray = com.google.protobuf.nano.MessageNano.toByteArray(f);
        return com.android.launcher3.reflection.common.nano.a.parseFrom(byteArray);

    }

    private com.android.launcher3.reflection.a2.a D(final Context context) {
        com.android.launcher3.reflection.a2.a a;
        if (((AppOpsManager)context.getSystemService(Context.APP_OPS_SERVICE)).checkOpNoThrow("android:get_usage_stats", Process.myUid(), context.getPackageName()) == 0) {
            a = new com.android.launcher3.reflection.a2.a((UsageStatsManager)context.getSystemService(Context.USAGE_STATS_SERVICE));
        }
        else {
            a = null;
        }
        return a;
    }

    public com.android.launcher3.reflection.common.nano.a B(final String la, final String lz, final Calendar calendar, final long ld, final long le, final String ly) {
        final com.android.launcher3.reflection.common.nano.a c = this.C();
        c.LC = calendar.getTimeInMillis();
        c.LF = calendar.getTimeZone().getID();
        c.LG = calendar.getTimeZone().getOffset(c.LC);
        c.LD = ld;
        c.LE = le;
        c.Ly = ly;
        c.LA = la;
        if (lz == null || lz.length() == 0) {
            c.Lz = "app_launch";
        }
        else {
            c.Lz = lz;
        }
        return c;
    }

    public void E() {
        final Iterator iterator = this.H.iterator();
        while (iterator.hasNext()) {
            ((d)iterator.next()).x(this.F);
        }
    }

    public void F() {
        this.E.disconnect(); // .ep();
        final Iterator iterator = this.D.iterator();
        while (iterator.hasNext()) {
            ((e)iterator.next()).F();
        }
    }

    public String y(final ComponentName componentName, final long n) {
        String s = m.aK(componentName);
        if (n != this.G) {
            s = String.format("%s#%d", s, n);
        }
        return s;
    }

    public com.android.launcher3.reflection.common.nano.a z(final String s, final String s2, final Calendar calendar, final long n, final long n2, final String s3) {
        this.E();
        return this.B(s, s2, calendar, n, n2, s3);
    }
}