package com.android.launcher3.reflection;

import java.util.ArrayList;
import java.io.Closeable;
import com.android.launcher3.Utilities;
import java.io.IOException;
import android.util.Log;
//import com.android.launcher3.util.b;
import com.android.launcher3.util.Preconditions;
//import com.google.research.reflection.predictor.g;
//import com.google.research.reflection.predictor.c;
import java.util.Map;
import java.io.File;
import android.content.SharedPreferences;
import java.util.HashMap;
import com.android.launcher3.reflection.filter.a;
import com.android.launcher3.reflection.b2.d;

public class e
{
    //private com.google.research.reflection.predictor.e aA;
    private final d aB;
    private final a aC;
    private final HashMap aD;
    private final SharedPreferences aE;
    private final Runnable aF;
    private final String ay;
    private File az;

    public e(final a ac, final d ab, final SharedPreferences ae, final String ay, final Runnable af) {
        this.aD = new HashMap();
        this.az = null;
        this.aC = ac;
        this.aB = ab;
        this.aE = ae;
        this.ay = ay;
        this.aF = af;
        //this.aA = new com.google.research.reflection.predictor.e();
    }

    private String ac() {
        final int n = 1;
        final StringBuffer sb = new StringBuffer();
        final Object[] array = new Object[n];
        array[0] = (n != 0);
        sb.append(String.format("LatLong=%b ", array));
        final Object[] array2 = new Object[n];
        array2[0] = false;
        sb.append(String.format("Semantic=%b ", array2));
        final Object[] array3 = new Object[n];
        array3[0] = (n != 0);
        sb.append(String.format("Install=%b ", array3));
        final Object[] array4 = new Object[n];
        array4[0] = (n != 0);
        sb.append(String.format("Headset=%b ", array4));
        return sb.toString();
    }

    public void X(final e e) {
        synchronized (this) {
            synchronized (e) {
                this.aD.clear();
                this.aD.putAll(e.aD);
            }
        }
    }

    public void Y(final String s, final String s2) {
        synchronized (this) {
            final String string = "/deleted_app/" + System.currentTimeMillis() + "/";
            final HashMap hashMap = new HashMap();
            /*final c c = this.aD.get(s);
            if (c != null) {
                c.RR(s2, string, hashMap);
                this.af();
            }*/
            this.aB.Q(s2, string, hashMap);
        }
    }

    /*com.google.research.reflection.predictor.e Z() {
        return this.aA;
    }*/

    void aa(final String s, final com.android.launcher3.reflection.nano.a a) {
        synchronized (this) {
            if (!a.Ly.startsWith("/deleted_app/") && this.aC.h(a.Ly)) {
                c c = (c) this.aD.get(s);
                if (c == null) {
                    //c = new c(new g());
                    //c.RY(this.aA);
                    //this.aD.put(s, c);
                }
                //c.RN(a);
            }
        }
    }

    boolean ab() {
        /*synchronized (this) {
            Preconditions.assertNonUiThread();
            if (this.aE != null) {
                //this.aA = com.google.research.reflection.predictor.e.Sr(this.aE.getString(this.ay, (String)null));
            }
            if (this.az == null) {
                return false;
            }
            this.aD.clear();
            try {
                final File az = this.az;
                try {
                    final byte[] ao = b.aO(az);
                    try {
                        final com.android.launcher3.reflection.nano.d from = com.android.launcher3.reflection.nano.d.parseFrom(ao);
                        try {
                            final HashMap ad = this.aD;
                            try {
                                c.RV(from, ad, this.aA);
                                return true;
                            }
                            catch (IOException ex) {
                                Log.e("Reflection.Engine", "Failed to load models, starting a fresh model.", (Throwable)ex);
                                return false;
                            }
                        }
                        catch (IOException ex2) {}
                    }
                    catch (IOException ex3) {}
                }
                catch (IOException ex4) {}
            }
            catch (IOException ex5) {}
            finally {
                Utilities.closeSilently(null);
            }
        }*/ return false;
    }

    /*public com.google.research.reflection.predictor.a ad(final String s, final com.android.launcher3.reflection.nano.a a) {
        synchronized (this) {
            final c c = this.aD.get(s);
            if (c == null) {
                final com.google.research.reflection.predictor.a a2 = new com.google.research.reflection.predictor.a();
                a2.RJ(new ArrayList());
                return a2;
            }
            if (c.RM().isEmpty()) {
                final com.google.research.reflection.predictor.a a3 = new com.google.research.reflection.predictor.a();
                a3.RJ(new ArrayList());
                return a3;
            }
            final com.google.research.reflection.predictor.a rp = c.RP(a);
            rp.RI();
            return rp;
        }
    }*/

    public void ae(final String s, final com.android.launcher3.reflection.nano.a a) {
        final String s2 = "system";
        try {
            if (!s.equals(s2)) {
                this.aa(s, a);
            }
            //if (!a.Ly.startsWith("/deleted_app/") && this.aA != null) {
                //this.aA.Sk(a);
            //}
            this.aB.O(a.LC);
            this.aB.P(a);
        }
        finally {
        }
    }

    public boolean af() {
        return false;
    }

    public void ag(final File az) {
        synchronized (this) {
            this.az = az;
        }
    }

    HashMap getPredictors() {
        return this.aD;
    }

    void reset() {
        synchronized (this) {
            Preconditions.assertNonUiThread();
            this.aD.clear();
        }
    }
}