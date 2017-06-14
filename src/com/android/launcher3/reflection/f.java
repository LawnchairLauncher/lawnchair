package com.android.launcher3.reflection;

import android.content.SharedPreferences;
import com.android.launcher3.compat.UserHandleCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.reflection.b2.a;
import java.io.File;
import com.android.launcher3.reflection.b2.d;
import com.android.launcher3.Utilities;
import com.android.launcher3.reflection.a2.b;
import java.util.ArrayList;
import android.content.Context;

public class f
{
    public static j ah(final Context context) {
        final ArrayList list = new ArrayList();
        final c c = new c(context);
        final SharedPreferences prefs = Utilities.getPrefs(context);
        new n();
        final d d = new d(new com.android.launcher3.reflection.b2.b(context, "reflection.events"));
        a a = null;
        final File file = new File(context.getCacheDir(), "client_actions");
        if (file.exists()) {
            file.delete();
        }
        final b b = new b(context);
        list.add(b);
        final SharedPreferences aj = m.aJ(context);
        final com.android.launcher3.reflection.filter.a a2 = new com.android.launcher3.reflection.filter.a(context.getContentResolver(), aj, a, b);
        final com.android.launcher3.reflection.filter.f f = new com.android.launcher3.reflection.filter.f(context);
        final com.android.launcher3.reflection.filter.b b2 = new com.android.launcher3.reflection.filter.b(context);
        final com.android.launcher3.reflection.filter.d d2 = new com.android.launcher3.reflection.filter.d(b);
        final e e = new e(a2, d, aj, "foreground_evt_buf.properties", null);
        final File file2 = new File(context.getFilesDir(), "reflection.engine");
        final g g = new g(d, aj, new File(context.getFilesDir(), "reflection.engine.background"), e, a2);
        new i().aw(aj, file2, e, d, g);
        e.ag(file2);
        final com.android.launcher3.reflection.a a3 = new com.android.launcher3.reflection.a(aj);
        final ArrayList<String> list2 = new ArrayList<>();
        for (Object o : m.bh) {
            String s = (String)o;
            if (s.startsWith("/")) {
                list2.add(context.getDir(s.substring(1), 0).getAbsolutePath());
            }
            else {
                list2.add(s);
            }
        }
        final j j = new j(e, g, b, a2, f, b2, d2, a3, new com.android.launcher3.reflection.b2.c(aj, new File(context.getApplicationInfo().dataDir), list2), a, c);
        final com.android.launcher3.reflection.d d3 = new com.android.launcher3.reflection.d(context, j, b2, f, d2);
        list.add(d3);
        j.ay(list);
        final LauncherAppsCompat instance = LauncherAppsCompat.getInstance(context);
        for (final UserHandleCompat userHandleCompat : UserManagerCompat.getInstance(context).getUserProfiles()) {
            d3.processUserApps(instance.getActivityList(null, userHandleCompat), userHandleCompat);
        }
        return j;
    }
}