package com.android.launcher3.reflection.filter;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.reflection.c2.d;
import com.android.launcher3.reflection.c2.c;
import android.content.Intent;
import android.database.Cursor;
import android.util.Log;
import android.net.Uri;

import java.util.Locale;
import com.android.launcher3.util.Preconditions;
import java.util.Iterator;
import java.util.List;
import java.util.HashSet;
import com.android.launcher3.reflection.a2.b;
import android.content.ContentResolver;
import java.util.Set;
import android.content.SharedPreferences;

public class a
{
    private final SharedPreferences c;
    private final com.android.launcher3.reflection.b2.a d;
    private Set e;
    private final FirstPageComponentsFilterIntentParser f;
    private final ContentResolver g;
    private final b h;

    public a(final ContentResolver g, final SharedPreferences c, final com.android.launcher3.reflection.b2.a d, final b h) {
        this.g = g;
        this.c = c;
        this.d = d;
        this.h = h;
        this.f = new FirstPageComponentsFilterIntentParser();
        this.e = new HashSet();
        this.d();
    }

    private void d() {
        if (this.c != null) {
            final Set stringSet = this.c.getStringSet("model_subtracted_events", (Set)null);
            if (stringSet != null) {
                this.e = stringSet;
            }
        }
    }

    private void g() {
        if (this.c != null) {
            this.c.edit().putStringSet("model_subtracted_events", this.e).apply();
        }
    }

    public void c(final List list, final List list2) {
        final Iterator<com.android.launcher3.reflection.predictor.b> iterator = list.iterator();
        while (iterator.hasNext()) {
            final com.android.launcher3.reflection.predictor.b b = iterator.next();
            if (this.e.contains(b.Ld)) {
                if (list2 != null) {
                    list2.add(b);
                }
                iterator.remove();
            }
        }
    }

    public void e() {
        String string;
        long long1;
        synchronized (this) {
            Preconditions.assertNonUiThread();
            this.e = new HashSet();
            final Locale english = Locale.ENGLISH;
            Object o = "(SELECT %s from %s ORDER BY %s ASC LIMIT 1)";
            final String format = String.format(english, (String)o, "_id", "workspaceScreens", "screenRank");
            o = Locale.ENGLISH;
            final String format2 = String.format((Locale)o, "%s = %d AND (%s = %d OR (%s = %d AND %s = %s))", "itemType", 0, "container", -101, "container", -100, "screen", format);
            final ContentResolver g = this.g;
            o = LauncherSettings.Favorites.CONTENT_URI;
            o = g.query((Uri)o, new String[] { "intent", "profileId" }, format2, null, null);
            final Cursor cursor = (Cursor)o;

            if (!cursor.moveToFirst()) {
                return;
            }

            while (true) {
                try {
                    string = cursor.getString(0);
                    long1 = ((Cursor)o).getLong(1);
                    if (string != null) {
                        final Intent i = this.f.i(string);
                        if (i == null || i.getComponent() == null) {
                            Log.e("Reflection.1stPFilter", "No component retrieved from intent.");
                        } else {
                            this.e.add(this.h.y(i.getComponent(), long1));
                        }
                    }
                    if (!((Cursor)o).moveToNext()) {
                        ((Cursor)o).close();
                        return;
                    }
                }
                catch (Exception ex) {
                    Log.e("Reflection.1stPFilter", "Error in reading intent from cursor", (Throwable)ex);
                }
            }
        }
    }

    public boolean f() {
        synchronized (this) {
            Preconditions.assertNonUiThread();
            final Set e = new HashSet(this.e);
            this.e();
            final boolean b = !this.e.equals(e);
            if (b) {
                this.g();
                if (this.d != null) {
                    final com.android.launcher3.reflection.c2.a a = new com.android.launcher3.reflection.c2.a();
                    a.Y = System.currentTimeMillis();
                    a.Z = "subtraction_changed";
                    final c[] aj = new c[this.e.size()];
                    final Iterator iterator = this.e.iterator();
                    int n = 0;
                    while (iterator.hasNext()) {
                        final String ag = (String) iterator.next();
                        final c c = new c();
                        c.ag = ag;
                        final int n2 = n + 1;
                        aj[n] = c;
                        n = n2;
                    }
                    final d aa = new d();
                    aa.aj = aj;
                    a.aa = aa;
                    this.d.J(a);
                }
            }
            return b;
        }
    }

    public boolean h(final String s) {
        return !this.e.contains(s);
    }
}