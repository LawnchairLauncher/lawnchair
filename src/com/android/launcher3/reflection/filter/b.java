package com.android.launcher3.reflection.filter;

import com.android.launcher3.compat.LauncherActivityInfoCompat;
import java.util.Iterator;
import java.util.List;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.content.Intent;

import com.android.launcher3.reflection.b_research;
import com.android.launcher3.reflection.m;
import android.content.ComponentName;
import java.util.ArrayList;
import java.util.HashMap;
import android.content.Context;
import android.content.pm.PackageManager;
import java.util.Map;
import com.android.launcher3.compat.UserHandleCompat;
import java.util.HashSet;

public class b
{
    public static final String[] i;
    private final c[] j;
    private final HashSet k;
    private final UserHandleCompat l;
    private final Map m;
    private final PackageManager mPackageManager;

    static {
        i = new String[] { "com.whatsapp", "com.facebook.katana", "com.facebook.orca", "com.google.android.youtube", "com.yodo1.crossyroad", "com.spotify.music", "com.android.chrome", "com.instagram.android", "com.google.android.gm", "com.skype.raider", "com.snapchat.android", "com.viber.voip", "com.twitter.android", "com.android.phone", "com.google.android.music", "com.google.android.calendar", "com.google.android.apps.genie.geniewidget", "com.netflix.mediaclient", "bbc.iplayer.android", "com.google.android.videos", "com.android.settings", "com.amazon.mShop.android.shopping", "com.microsoft.office.word", "com.google.android.apps.docs", "com.google.android.keep", "com.google.android.apps.plus", "com.google.android.talk" };
        //i = new String[] {};
    }

    public b(final Context context) {
        this.j = new c[b.i.length];
        this.m = new HashMap();
        this.k = new HashSet();
        this.mPackageManager = context.getPackageManager();
        this.l = UserHandleCompat.myUserHandle();
        for (int i = 0; i < b.i.length; ++i) {
            final c c = new c(b.i[i], "", i, -1);
            this.j[i] = c;
            this.m.put(b.i[i], c);
        }
    }

    private ArrayList m(final float n) {
        final ArrayList<b_research> list = new ArrayList<>(b.i.length);
        for (int i = 0; i < b.i.length; ++i) {
            if (this.j[i].state == -1) {
                this.n(i);
            }
            if (this.j[i].state == 1) {
                list.add(new b_research(com.android.launcher3.reflection.m.aK(new ComponentName(this.j[i].packageName, this.j[i].n)), n - list.size()));
            }
        }
        return list;
    }

    private void n(final int n) {
        final Intent launchIntentForPackage = this.mPackageManager.getLaunchIntentForPackage(b.i[n]);
        Label_0164: {
            if (launchIntentForPackage == null) {
                break Label_0164;
            }
            final ResolveInfo resolveActivity = this.mPackageManager.resolveActivity(launchIntentForPackage, 0);
            if (resolveActivity == null) {
                break Label_0164;
            }
            final ActivityInfo activityInfo = resolveActivity.activityInfo;
            if (activityInfo != null) {
                String n2 = activityInfo.name;
                if (n2.startsWith(".")) {
                    n2 = activityInfo.packageName + n2;
                }
                this.j[n].state = 1;
                this.j[n].n = n2;
            }
            else {
                this.j[n].state = 0;
                this.j[n].n = "";
            }
            return;
        }
    }

    public void c(final List list, final List list2) {
        final float n = 1.0f;
        float n2;
        if (list.size() > 0) {
            n2 = ((b_research) list.get(list.size() - 1)).Le - n;
        }
        else {
            n2 = n;
        }
        final ArrayList m = this.m(n2);
        this.k.clear();
        final Iterator<b_research> iterator = list.iterator();
        while (iterator.hasNext()) {
            this.k.add(iterator.next().Ld);
        }
        for (Object o : m) {
            final b_research b = (b_research) o;
            if (!this.k.contains(b.Ld)) {
                list.add(b);
                if (list2 == null) {
                    continue;
                }
                list2.add(b);
            }
        }
    }

    public void j(final int state, final LauncherActivityInfoCompat launcherActivityInfoCompat, final UserHandleCompat userHandleCompat) {
        if (this.l.equals(userHandleCompat)) {
            final c c = (c) this.m.get(launcherActivityInfoCompat.getComponentName().getPackageName());
            if (c != null) {
                c.state = state;
                c.n = launcherActivityInfoCompat.getComponentName().getClassName();
            }
        }
    }

    public void k(final int state, final String s, final UserHandleCompat userHandleCompat) {
        if (this.l.equals(userHandleCompat)) {
            final c c = (c) this.m.get(s);
            if (c != null) {
                c.state = state;
            }
        }
    }

    public void l(final int n, final String[] array, final UserHandleCompat userHandleCompat) {
        for (int i = 0; i < array.length; ++i) {
            this.k(n, array[i], userHandleCompat);
        }
    }
}