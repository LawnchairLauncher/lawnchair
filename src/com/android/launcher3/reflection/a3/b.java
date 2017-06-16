package com.android.launcher3.reflection.a3;

import java.io.IOException;
import java.util.List;
import java.io.DataOutputStream;
import java.io.DataInputStream;

public abstract class b
{
    a Mp;

    public static String Ta(final b b) {
        if (b instanceof d && !(b instanceof k)) {
            return "app_launch_extractor";
        }
        if (b instanceof k) {
            return "app_usage_extractor";
        }
        if (b instanceof e) {
            return "day_extractor";
        }
        if (b instanceof c) {
            return "feature_aggregator";
        }
        if (b instanceof g) {
            return "headset_extractor";
        }
        if (b instanceof f) {
            return "hour_extractor";
        }
        if (b instanceof i) {
            return "lat_lng_extractor";
        }
        if (!(b instanceof j)) {
            return null;
        }
        return "place_extractor";
    }

    public static b Tb(final String s) {
        if (s.equals("app_launch_extractor") || s.equals("com.android.launcher3.reflection.a3.d")) {
            return new d();
        }
        if (s.equals("app_usage_extractor") || s.equals("com.android.launcher3.reflection.a3.k")) {
            return new k();
        }
        if (s.equals("day_extractor") || s.equals("com.android.launcher3.reflection.a3.e")) {
            return new e();
        }
        if (s.equals("feature_aggregator") || s.equals("com.android.launcher3.reflection.a3.c")) {
            return new c();
        }
        if (s.equals("hour_extractor") || s.equals("com.android.launcher3.reflection.a3.f")) {
            return new f();
        }
        if (s.equals("headset_extractor") || s.equals("com.android.launcher3.reflection.a3.g")) {
            return new g();
        }
        if (s.equals("lat_lng_extractor") || s.equals("com.android.launcher3.reflection.a3.i")) {
            return new i();
        }
        if (!s.equals("place_extractor") && !s.equals("com.android.launcher3.reflection.a3.j")) {
            return null;
        }
        return new j();
    }

    public abstract com.android.launcher3.reflection.layers.b SV(final com.android.launcher3.reflection.common.b p0, final com.android.launcher3.reflection.common.nano.a p1);

    public abstract int SW();

    public void SX(final DataInputStream dataInputStream) throws IOException {
    }

    public void SY(final DataOutputStream dataOutputStream) throws IOException {
    }

    public void SZ(final List list) {
    }

    public void Tc(final a mp) {
        this.Mp = mp;
    }

    void Td(final Integer n) {
        if (this.Mp != null) {
            this.Mp.Sv(this, n);
        }
    }

    public abstract b clone();
}
