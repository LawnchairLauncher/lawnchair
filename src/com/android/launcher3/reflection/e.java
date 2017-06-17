package com.android.launcher3.reflection;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import com.android.launcher3.Utilities;
import com.android.launcher3.reflection.common.*;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.reflection.predictor.g;
import com.android.launcher3.reflection.predictor.c;

import java.io.File;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Calendar;
import java.util.HashMap;
import com.android.launcher3.reflection.filter.a;
import com.android.launcher3.reflection.b2.d;

public class e
{
    public com.android.launcher3.reflection.predictor.e aA;
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
        this.aA = new com.android.launcher3.reflection.predictor.e();
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
            final c c = (c) this.aD.get(s);
            if (c != null) {
                c.RR(s2, string, hashMap);
                this.af();
            }
            this.aB.Q(s2, string, hashMap);
        }
    }

    com.android.launcher3.reflection.predictor.e Z() {
        return this.aA;
    }

    void aa(final String s, final com.android.launcher3.reflection.common.nano.a a) {
        synchronized (this) {
            if (!a.Ly.startsWith("/deleted_app/") && this.aC.h(a.Ly)) {
                c c = (c) this.aD.get(s);
                if (c == null) {
                    c = new c(new g());
                    c.RY(this.aA);
                    this.aD.put(s, c);
                }
                c.RN(a);
            }
        }
    }

    boolean ab() {
        synchronized (this) {
            Preconditions.assertNonUiThread();
            if (this.aE != null) {
                this.aA = com.android.launcher3.reflection.predictor.e.Sr(this.aE.getString(this.ay, (String)null));
            }
            if (this.az == null) {
                return false;
            }
            this.aD.clear();
            try {
                final File az = this.az;
                final byte[] ao = com.android.launcher3.reflection.util.b.aO(az);
                final com.android.launcher3.reflection.common.nano.d from = com.android.launcher3.reflection.common.nano.d.parseFrom(ao);
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
            catch (IOException ex5) {}
            finally {
                Utilities.closeSilently(null);
            }
        } return false;
    }

    public com.android.launcher3.reflection.predictor.a ad(final String s, final com.android.launcher3.reflection.common.nano.a a) {
        synchronized (this) {
            c c = (c) this.aD.get(s);
            if (c == null) {
                //c = new c(new g());
                //c.RY(this.aA);

                final com.android.launcher3.reflection.predictor.a a2 = new com.android.launcher3.reflection.predictor.a();
                a2.RJ(new ArrayList());
                return a2;
            }
            if (c.RM().isEmpty()) {
                final com.android.launcher3.reflection.predictor.a a3 = new com.android.launcher3.reflection.predictor.a();
                a3.RJ(new ArrayList());
                return a3;
            }
            final com.android.launcher3.reflection.predictor.a rp = c.RP(a);
            rp.RI();
            return rp;
        }
    }

    public void ae(final String s, final com.android.launcher3.reflection.common.nano.a a) {
        final String s2 = "system";
        try {
            if (!s.equals(s2)) {
                this.aa(s, a);
            }
            if (!a.Ly.startsWith("/deleted_app/") && this.aA != null) {
                this.aA.Sk(a);
            }
            this.aB.O(a.LC);
            this.aB.P(a);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        finally {
        }
    }

    public boolean af() {
        Preconditions.assertNonUiThread();
        String str = com.android.launcher3.reflection.predictor.e.Ss(aA);
        SharedPreferences.Editor edit = aE.edit();
        edit.putString(ay, str);
        edit.apply();

        try {
            com.android.launcher3.reflection.common.nano.d d = com.android.launcher3.reflection.predictor.c.RW(aD);
            Calendar calendar = Calendar.getInstance();
            d.Ma = calendar.getTimeInMillis();
            d.Mb = 0;
            d.Mc = ac(); //app info

            FileOutputStream fileOutputStream = new FileOutputStream(az);
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
            DataOutputStream dataOutputStream = new DataOutputStream(bufferedOutputStream);
            dataOutputStream.write(com.google.protobuf.nano.MessageNano.toByteArray(d));
            if (aF != null) {
                aF.run();
            }

            Utilities.closeSilently(dataOutputStream);
            Utilities.closeSilently(bufferedOutputStream);
            Utilities.closeSilently(fileOutputStream);

            return true;
        }
        catch (IOException ex) {
            Log.d("Reflection.Engine", "Failed to save models");
        }
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