package com.android.launcher3.reflection.predictor;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.android.launcher3.reflection.common.nano.a;

public abstract class d
{
    private c Lp;

    public static String Sa(final d d) {
        if (!(d instanceof g)) {
            return null;
        }
        return "neural_predictor";
    }

    public static d Sb(final String s) {
        if (!s.equals("neural_predictor") && !s.equals("com.google.research.reflection.predictor.g")) {
            return null;
        }
        return new g();
    }

    public void Sc(final c lp) {
        this.Lp = lp;
    }

    public final c Sd() {
        return this.Lp;
    }

    public abstract com.android.launcher3.reflection.predictor.a Se(final float[] p0, final a p1);

    public abstract com.android.launcher3.reflection.predictor.a Sf(final a p0);

    public abstract void Sg(final DataOutputStream p0) throws IOException;

    public abstract void Sh(final DataInputStream p0) throws IOException;

    public abstract void Si(final Integer p0, final Integer p1, final String p2);

    public void Sj(final String s, final String s2) {
    }
}