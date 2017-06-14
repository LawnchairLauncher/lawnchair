package com.android.launcher3.reflection;

import android.util.Log;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.config.ProviderConfig;
import android.os.SystemClock;
import java.util.Calendar;
import android.content.ComponentName;
import java.util.Iterator;
import com.android.launcher3.util.Preconditions;
import java.util.List;
import java.util.ArrayList;
import com.android.launcher3.reflection.filter.d;
import com.android.launcher3.reflection.filter.f;
import com.android.launcher3.reflection.b2.c;
import com.android.launcher3.reflection.b2.a;

public class j
{
    private final b aP;
    private final a aQ;
    private final c aR;
    private final e aS;
    private final com.android.launcher3.reflection.filter.a aT;
    private final f aU;
    private final d aV;
    private final com.android.launcher3.reflection.filter.b aW;
    private final com.android.launcher3.reflection.a aX;
    private final ArrayList aY;
    private final com.android.launcher3.reflection.a2.b aZ;
    private final g ba;

    public j(final e as, final g ba, final com.android.launcher3.reflection.a2.b az, final com.android.launcher3.reflection.filter.a at, final f au, final com.android.launcher3.reflection.filter.b aw, final d av, final com.android.launcher3.reflection.a ax, final c ar, final a aq, final b ap) {
        this.aY = new ArrayList();
        this.aS = as;
        this.ba = ba;
        this.aZ = az;
        this.aT = at;
        this.aU = au;
        this.aW = aw;
        this.aV = av;
        this.aX = ax;
        this.aR = ar;
        this.aQ = aq;
        this.aP = ap;
    }

    private ArrayList aB(final String s, final com.android.launcher3.reflection.nano.a a, final com.android.launcher3.reflection.c2.d d) {
        final int n = 12;
        List list = null;
        this.aT.f();
        //final com.google.research.reflection.predictor.a ad = this.aS.ad(s, a);
        //final double[] rg = ad.RG();
        //final ArrayList ri = ad.RI();
        final ArrayList ri = new ArrayList();
        List list2;
        List list3;
        List list4;
        List list5;
        if (d != null) {
            list2 = new ArrayList();
            list3 = new ArrayList(/*ri*/);
            list4 = new ArrayList();
            list5 = new ArrayList();
            list = new ArrayList();
        }
        else {
            list5 = null;
            list4 = null;
            list2 = null;
            list3 = null;
        }
        this.aV.c(ri, list2);
        this.aW.c(ri, list4);
        this.aT.c(ri, list);
        this.aU.c(ri, list5);
        if (d != null) {
            /*if (rg != null) {
                d.ak = new com.android.launcher3.reflection.c.e();
                d.ak.aq = rg;
            }*/
            d.al = this.az(ri);
            d.am = this.az(list3);
            d.an = this.az(list4);
            d.ao = this.az(list5);
            d.aj = this.az(list);
        }
        if (ri.size() > n) {
            return new ArrayList(ri.subList(0, n));
        }
        return ri;
    }

    private com.android.launcher3.reflection.c2.c[] az(final List list) {
        final com.android.launcher3.reflection.c2.c[] array = new com.android.launcher3.reflection.c2.c[list.size()];
        for (int i = 0; i < list.size(); ++i) {
            final com.android.launcher3.reflection.c2.c c = new com.android.launcher3.reflection.c2.c();
            c.ag = ((b_research) list.get(i)).Ld;
            c.ah = ((b_research) list.get(i)).Le;
            array[i] = c;
        }
        return array;
    }

    public void aA(final boolean b) {
        synchronized (this) {
            Preconditions.assertNonUiThread();
            if (b) {
                this.aR.M();
                this.aS.reset();
            }
            final Iterator iterator = this.aY.iterator();
            while (iterator.hasNext()) {
                //iterator.next().F();
            }
        }
    }
    // monitorexit(this)

    String aC(final ComponentName componentName, final long n) {
        return this.aZ.y(componentName, n);
    }

    public void aD(final String s) {
        com.android.launcher3.reflection.c2.a a = null;
        Preconditions.assertNonUiThread();
        final Calendar instance = Calendar.getInstance();
        final com.android.launcher3.reflection.nano.a z = this.aZ.z(s, "predict", instance, this.aP.V(), SystemClock.elapsedRealtime(), "unknown");
        com.android.launcher3.reflection.c2.d aa;
        if (this.aQ != null) {
            a = new com.android.launcher3.reflection.c2.a();
            a.Z = "prediction_update";
            a.Y = instance.getTimeInMillis();
            aa = new com.android.launcher3.reflection.c2.d();
            a.aa = aa;
        }
        else {
            aa = null;
        }
        final ArrayList ab = this.aB(s, z, aa);
        if (ProviderConfig.IS_DOGFOOD_BUILD && ab.size() == 0) {
            FileLog.d("Reflection.SvcHandler", "predictions.size() == 0. Are every applications on the firstpage/hotseat?");
        }
        Log.d("j", "doing predictions");
        this.aX.U(ab);
        if (a != null) {
            this.aQ.J(a);
        }
    }

    public void aE(final String s, final String packageName, final String z, final LauncherLogProto.LauncherEvent launcherEvent) {
        Preconditions.assertNonUiThread();
        if (packageName == null) {
            Log.e("Reflection.SvcHandler", "Empty event string");
            return;
        }
        final Calendar instance = Calendar.getInstance();
        this.aS.ae(s, this.aZ.z(s, z, instance, this.aP.V(), SystemClock.elapsedRealtime(), packageName));
        this.aS.af();
        this.aV.q();
        if (this.aQ != null) {
            final com.android.launcher3.reflection.c2.a a = new com.android.launcher3.reflection.c2.a();
            a.Z = z;
            a.Y = instance.getTimeInMillis();
            a.packageName = packageName;
            if (launcherEvent != null) {
                final com.android.launcher3.reflection.c2.b ab = new com.android.launcher3.reflection.c2.b();
                if (launcherEvent.srcTarget.length >= 2 && launcherEvent.srcTarget[1].containerType != 0) {
                    ab.ac = Integer.toString(launcherEvent.srcTarget[1].containerType);
                    ab.ad = Integer.toString(launcherEvent.srcTarget[0].pageIndex);
                }
                a.ab = ab;
            }
            this.aQ.J(a);
        }
    }

    public void ax(final String s, final long n) {
        Preconditions.assertNonUiThread();
        this.aS.Y("system", String.format("%s/", s));
    }

    public void ay(final List list) {
        this.aY.addAll(list);
    }
}