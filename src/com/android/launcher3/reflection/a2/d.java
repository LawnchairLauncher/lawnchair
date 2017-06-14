package com.android.launcher3.reflection.a2;

import android.util.Log;

public class d implements c
{
    private final a J;
    private final Object K;

    public d(final a j, final Object k) {
        this.J = j;
        this.K = k;
    }

    public void x(final com.android.launcher3.reflection.nano.a a) {
        /*try {
            final com.google.android.gms.location.a k = this.K;
            try {
                final Location eq = k.Eq(this.J);
                if (eq == null) {
                    return;
                }
                try {
                    final b b = new b();
                    b.LL = "lat_long";
                    b.LM = eq.getTime();
                    b.LO = new double[2];
                    final double[] lo = b.LO;
                    try {
                        lo[0] = eq.getLatitude();
                        final double[] lo2 = b.LO;
                        try {
                            lo2[1] = eq.getLongitude();
                            try {
                                com.google.research.reflection.common.a.Sy(a, b);
                            }
                            catch (SecurityException ex) {
                                Log.d("Reflection.LocReader", "cannot read location due to lack of permission", (Throwable)ex);
                            }
                        }
                        catch (SecurityException ex2) {}
                    }
                    catch (SecurityException ex3) {}
                }
                catch (SecurityException ex4) {}
            }
            catch (SecurityException ex5) {}
        }
        catch (SecurityException ex6) {}*/
    }
}
