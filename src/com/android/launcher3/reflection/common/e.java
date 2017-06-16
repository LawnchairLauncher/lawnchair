package com.android.launcher3.reflection.common;

import java.util.TimeZone;
import java.util.Calendar;
import com.android.launcher3.reflection.common.nano.a;

public class e
{
    private static Calendar SQ(final a a) {
        if (a.LF == null) {
            final Calendar instance = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            instance.setTimeInMillis(a.LC + a.LG);
            return instance;
        }
        final Calendar instance2 = Calendar.getInstance(TimeZone.getTimeZone(a.LF));
        instance2.setTimeInMillis(a.LC);
        return instance2;
    }

    public static long SR(final a a, final a a2) throws Exception {
        final long n = 0L;
        int n2 = 1;
        final long n3 = a2.LC - a.LC - a.LH;
        int n4;
        if (a.LD <= n) {
            n4 = n2;
        }
        else {
            n4 = 0;
        }
        if (n4 == 0) {
            int n5;
            if (a2.LD <= n) {
                n5 = n2;
            }
            else {
                n5 = 0;
            }
            if (n5 == 0) {
                if (a.LD == a2.LD) {
                    return a2.LE - a.LE - a.LH;
                }
                final long n6 = a2.LD + a2.LE - (a.LD + a.LE) - a.LH;
                if (n3 == n6) {
                    return n6;
                }
                throw new Exception(n6 + "");
            }
        }
        if (n3 < n) {
            n2 = 0;
        }
        if (n2 == 0) {
            throw new Exception(Long.MAX_VALUE + "");
        }
        return n3;
    }

    public static int SS(final a a) {
        return SQ(a).get(Calendar.DAY_OF_WEEK);
    }

    public static int ST(final a a) {
        return SQ(a).get(Calendar.HOUR_OF_DAY);
    }

    public static int SU(final a a) {
        return SQ(a).get(Calendar.MINUTE);
    }
}
