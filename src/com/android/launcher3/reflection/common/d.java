package com.android.launcher3.reflection.common;

import android.support.annotation.NonNull;

public class d implements Comparable
{
    public int Mn;
    public float Mo;

    public d(final int mn) {
        this.Mn = mn;
        this.Mo = 1.0f;
    }

    public d(final int mn, final float mo) {
        this.Mn = mn;
        this.Mo = mo;
    }

    public d clone() {
        return new d(this.Mn, this.Mo);
    }

    public boolean equals(final Object o) {
        return o instanceof d && (this.Mn == ((d)o).Mn && this.Mo == ((d)o).Mo);
    }

    public int hashCode() {
        return this.Mn * 31 + 17 + Float.floatToIntBits(this.Mo);
    }

    public String toString() {
        return new StringBuilder(27).append(this.Mn).append("=").append(this.Mo).toString();
    }

    @Override
    public int compareTo(@NonNull Object d) {
        if (d instanceof d) {
            return Float.compare(this.Mo, ((d)d).Mo);
        }
        return 0;
    }
}