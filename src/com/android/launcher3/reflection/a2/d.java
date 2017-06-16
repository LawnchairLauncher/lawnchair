package com.android.launcher3.reflection.a2;

import android.location.Location;
import android.util.Log;
import com.android.launcher3.reflection.common.nano.b;
import com.google.android.gms.common.api.GoogleApiClient;

public class d implements c
{
    private final GoogleApiClient J;
    private final Object K;

    public d(final GoogleApiClient j, final Object k) {
        this.J = j;
        this.K = k;
    }

    public void x(final com.android.launcher3.reflection.common.nano.a a) {
        try {
            final com.google.android.gms.location.FusedLocationProviderApi k = (com.google.android.gms.location.FusedLocationProviderApi) this.K;
            final Location eq = k.getLastLocation(this.J);
            if (eq == null) {
                return;
            }
            final b b = new b();
            b.LL = "lat_long";
            b.LM = eq.getTime();
            b.LO = new double[2];
            final double[] lo = b.LO;
            lo[0] = eq.getLatitude();
            final double[] lo2 = b.LO;
            try {
                lo2[1] = eq.getLongitude();
                try {
                    com.android.launcher3.reflection.common.a.Sy(a, b);
                }
                catch (SecurityException ex) {
                    Log.d("Reflection.LocReader", "cannot read location due to lack of permission", ex);
                }
            }
            catch (SecurityException ex2) {}
        }
        catch (SecurityException ex6) {}
    }
}
