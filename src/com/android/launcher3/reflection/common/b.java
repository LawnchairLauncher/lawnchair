package com.android.launcher3.reflection.common;

import java.util.LinkedList;

public class b
{
    private int Mi;
    private int Mj;
    private Object[] Mk;
    private LinkedList Ml;
    private int Mm;

    public b(final int n, final boolean b) {
        final int n2 = -1;
        this.Mi = n2;
        this.Mj = 0;
        this.Mm = n2;
        if (n > 0) {
            this.Mk = new Object[n];
            if (b) {
                this.Ml = new LinkedList();
            }
            return;
        }
        throw new RuntimeException();
    }

    private boolean SE() {
        return this.Ml != null && this.Ml.size() < this.Mk.length;
    }

    public int SC() {
        return this.Mm;
    }

    public Object SD() {
        if (this.Ml != null && !this.Ml.isEmpty()) {
            return this.Ml.removeLast();
        }
        return null;
    }

    public int SF() {
        return this.Mj;
    }

    public Object SG(final int n) {
        if (n >= 0 && n < this.Mj) {
            int n2 = this.Mi - (this.Mj - n - 1);
            if (n2 < 0) {
                n2 += this.Mk.length;
            }
            return this.Mk[n2];
        }
        return null;
    }

    public int SH() {
        return this.Mk.length;
    }

    public Object SI() {
        if (this.Mj != 0) {
            return this.Mk[this.Mi];
        }
        return null;
    }

    public void SJ() {
        if (this.Mj != 0) {
            int n = this.Mi - (this.Mj - 1);
            if (n < 0) {
                n += this.Mk.length;
            }
            if (this.SE()) {
                this.Ml.add(this.Mk[n]);
            }
            this.Mk[n] = null;
            --this.Mj;
            --this.Mm;
        }
    }

    public Object add(final Object o) {
        ++this.Mi;
        if (this.Mi == this.Mk.length) {
            this.Mi = 0;
        }
        if (this.Mk[this.Mi] != null && this.SE()) {
            this.Ml.add(this.Mk[this.Mi]);
        }
        this.Mk[this.Mi] = o;
        if (this.Mj < this.Mk.length) {
            ++this.Mj;
        }
        ++this.Mm;
        return o;
    }

    public void clear() {
        final int n = -1;
        this.Mi = n;
        this.Mm = n;
        this.Mj = 0;
    }
}