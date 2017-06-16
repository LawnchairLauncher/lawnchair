package com.android.launcher3.reflection.common.nano;

import com.google.protobuf.nano.CodedInputByteBufferNano; //c
import com.google.protobuf.nano.CodedOutputByteBufferNano;
import com.google.protobuf.nano.WireFormatNano; //f
import com.google.protobuf.nano.InvalidProtocolBufferNanoException;
import com.google.protobuf.nano.MessageNano; //a

import java.io.IOException;

public final class b extends MessageNano
{
    private static volatile b[] LJ;
    public static final Object KD = new Object();
    public String LK;
    public String LL;
    public long LM;
    public String[] LN;
    public double[] LO;
    public long[] LP;

    public b() {
        this.clear();
    }

    public static b[] emptyArray() {
        if (b.LJ == null) {
            while (true) {
                synchronized (KD) {
                    if (b.LJ != null) {
                        break;
                    }
                }
                b.LJ = new b[0];
                continue;
            }
        }
        return b.LJ;
    }

    public static b parseFrom(final byte[] array) {
        try {
            return a.mergeFrom(new b(), array);
        }
        catch (InvalidProtocolBufferNanoException e) {
            return null;
        }
    }

    public b clear() {
        this.LK = "";
        this.LL = "";
        this.LM = 0L;
        this.LN = WireFormatNano.EMPTY_STRING_ARRAY;
        this.LO = WireFormatNano.EMPTY_DOUBLE_ARRAY;
        this.LP = WireFormatNano.EMPTY_LONG_ARRAY;
        this.cachedSize = -1;
        return this;
    }

    protected int computeSerializedSize() {
        final int n = 1;
        int i = 0;
        int computeSerializedSize = super.computeSerializedSize();
        if (!this.LK.equals("")) {
            computeSerializedSize += CodedOutputByteBufferNano.computeStringSize(n, this.LK);
        }
        if (!this.LL.equals("")) {
            computeSerializedSize += CodedOutputByteBufferNano.computeStringSize(2, this.LL);
        }
        if (this.LM != 0L) {
            computeSerializedSize += CodedOutputByteBufferNano.computeInt64Size(3, this.LM);
        }
        if (this.LN != null && this.LN.length > 0) {
            int j = 0;
            int n2 = 0;
            int n3 = 0;
            while (j < this.LN.length) {
                final String s = this.LN[j];
                if (s != null) {
                    ++n3;
                    n2 += CodedOutputByteBufferNano.computeStringSizeNoTag(s);
                }
                ++j;
            }
            computeSerializedSize = computeSerializedSize + n2 + n3 * 1;
        }
        if (this.LO != null && this.LO.length > 0) {
            computeSerializedSize = computeSerializedSize + this.LO.length * 8 + this.LO.length * 1;
        }
        if (this.LP != null && this.LP.length > 0) {
            int n4 = 0;
            while (i < this.LP.length) {
                n4 += CodedOutputByteBufferNano.computeInt64SizeNoTag(this.LP[i]);
                ++i;
            }
            computeSerializedSize = computeSerializedSize + n4 + this.LP.length * 1;
        }
        return computeSerializedSize;
    }

    public b mergeFrom(final CodedInputByteBufferNano c) throws IOException {
        while (true) {
            final int qs = c.readTag();
            switch (qs) {
                default: {
                    if (!WireFormatNano.parseUnknownField(c, qs)) {
                        return this;
                    }
                    continue;
                }
                case 0: {
                    return this;
                }
                case 10: {
                    this.LK = c.readString();
                    continue;
                }
                case 18: {
                    this.LL = c.readString();
                    continue;
                }
                case 24: {
                    this.LM = c.readInt64();
                    continue;
                }
                case 34: {
                    final int rg = WireFormatNano.getRepeatedFieldArrayLength(c, 34);
                    int i;
                    if (this.LN != null) {
                        i = this.LN.length;
                    }
                    else {
                        i = 0;
                    }
                    final String[] ln = new String[rg + i];
                    if (i != 0) {
                        System.arraycopy(this.LN, 0, ln, 0, i);
                    }
                    while (i < ln.length - 1) {
                        ln[i] = c.readString();
                        c.readTag();
                        ++i;
                    }
                    ln[i] = c.readString();
                    this.LN = ln;
                    continue;
                }
                case 41: {
                    final int rg2 = WireFormatNano.getRepeatedFieldArrayLength(c, 41);
                    int j;
                    if (this.LO != null) {
                        j = this.LO.length;
                    }
                    else {
                        j = 0;
                    }
                    final double[] lo = new double[rg2 + j];
                    if (j != 0) {
                        System.arraycopy(this.LO, 0, lo, 0, j);
                    }
                    while (j < lo.length - 1) {
                        lo[j] = c.readDouble();
                        c.readTag();
                        ++j;
                    }
                    lo[j] = c.readDouble();
                    this.LO = lo;
                    continue;
                }
                case 42: {
                    final int qm = c.readRawVarint32();
                    final int qn = c.pushLimit(qm);
                    final int n = qm / 8;
                    int k;
                    if (this.LO != null) {
                        k = this.LO.length;
                    }
                    else {
                        k = 0;
                    }
                    final double[] lo2 = new double[n + k];
                    if (k != 0) {
                        System.arraycopy(this.LO, 0, lo2, 0, k);
                    }
                    while (k < lo2.length) {
                        lo2[k] = c.readDouble();
                        ++k;
                    }
                    this.LO = lo2;
                    c.popLimit(qn);
                    continue;
                }
                case 48: {
                    final int rg3 = WireFormatNano.getRepeatedFieldArrayLength(c, 48);
                    int l;
                    if (this.LP != null) {
                        l = this.LP.length;
                    }
                    else {
                        l = 0;
                    }
                    final long[] lp = new long[rg3 + l];
                    if (l != 0) {
                        System.arraycopy(this.LP, 0, lp, 0, l);
                    }
                    while (l < lp.length - 1) {
                        lp[l] = c.readInt64();
                        c.readTag();
                        ++l;
                    }
                    lp[l] = c.readInt64();
                    this.LP = lp;
                    continue;
                }
                case 50: {
                    final int qn2 = c.pushLimit(c.readRawVarint32());
                    final int position = c.getPosition();
                    int n2 = 0;
                    while (c.getBytesUntilLimit() > 0) {
                        c.readInt64();
                        ++n2;
                    }
                    c.rewindToPosition(position);
                    int length;
                    if (this.LP != null) {
                        length = this.LP.length;
                    }
                    else {
                        length = 0;
                    }
                    final long[] lp2 = new long[n2 + length];
                    if (length != 0) {
                        System.arraycopy(this.LP, 0, lp2, 0, length);
                    }
                    while (length < lp2.length) {
                        lp2[length] = c.readInt64();
                        ++length;
                    }
                    this.LP = lp2;
                    c.popLimit(qn2);
                    continue;
                }
            }
        }
    }

    public void writeTo(final CodedOutputByteBufferNano b) throws IOException {
        int i = 0;
        if (!this.LK.equals("")) {
            b.writeString(1, this.LK);
        }
        if (!this.LL.equals("")) {
            b.writeString(2, this.LL);
        }
        if (this.LM != 0L) {
            b.writeInt64(3, this.LM);
        }
        if (this.LN != null && this.LN.length > 0) {
            for (int j = 0; j < this.LN.length; ++j) {
                final String s = this.LN[j];
                if (s != null) {
                    b.writeString(4, s);
                }
            }
        }
        if (this.LO != null && this.LO.length > 0) {
            for (int k = 0; k < this.LO.length; ++k) {
                b.writeDouble(5, this.LO[k]);
            }
        }
        if (this.LP != null && this.LP.length > 0) {
            while (i < this.LP.length) {
                b.writeInt64(6, this.LP[i]);
                ++i;
            }
        }
        super.writeTo(b);
    }
}