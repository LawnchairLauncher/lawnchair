package com.android.launcher3.reflection.layers;

import java.util.Random;
import java.util.Arrays;

public class l
{
    public static void TU(final double[] array) {
        Arrays.fill(array, 0.0);
    }

    static void TV(final b b, final int n, final b b2) {
        final int td = b.TD(false);
        double n2 = -1.7976931348623157E308;
        int i = b.Tw(false, n, 0);
        final int tw = b.Tw(false, n, td);
        for (int j = i; j < tw; ++j) {
            if (b.MC[j] > n2) {
                n2 = b.MC[j];
            }
        }
        double n3 = 0.0;
        while (i < tw) {
            b2.MC[i] = Math.exp(b.MC[i] - n2);
            n3 += b2.MC[i];
            ++i;
        }
        if (n3 == 0.0) {
            throw new RuntimeException("softmax sum = 0");
        }
        for (int k = 0; k < td; ++k) {
            final double[] mc = b2.MC;
            mc[k] /= n3;
        }
    }

    static double TW(final double n) {
        final double n2 = 1.0;
        final double exp = Math.exp(-n);
        if (!Double.isInfinite(exp)) {
            return n2 / (exp + n2);
        }
        return 0.0;
    }

    static double TX(final double n) {
        final double n2 = 0.0;
        if (n > n2) {
            return n;
        }
        return n2;
    }

    public static void TY(final b b, final int n, final boolean b2) {
        final double n2 = 0.1;
        final int td = b.TD(false);
        if (!b2) {
            final Random random = new Random();
            for (int i = 0; i < td; ++i) {
                b.Tz(false, n, i, random.nextGaussian() * n2);
            }
        }
        else {
            for (int j = 0; j < td; ++j) {
                b.Tz(false, n, j, Math.random() * n2);
            }
        }
    }

    public static void TZ(final b b, final boolean b2) {
        for (int i = 0; i < b.TC(false); ++i) {
            TY(b, i, b2);
        }
    }
}