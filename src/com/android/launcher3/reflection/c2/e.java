package com.android.launcher3.reflection.c2;

import com.google.protobuf.nano.CodedOutputByteBufferNano;
import com.google.protobuf.nano.CodedInputByteBufferNano;
import com.google.protobuf.nano.WireFormatNano;
import com.google.protobuf.nano.MessageNano;

import java.io.IOException;

public final class e extends MessageNano
{
    public double[] aq;

    public e() {
        this.clear();
    }

    public e clear() {
        this.aq = WireFormatNano.EMPTY_DOUBLE_ARRAY;
        this.cachedSize = -1;
        return this;
    }

    protected int computeSerializedSize() {
        int computeSerializedSize = super.computeSerializedSize();
        if (this.aq != null && this.aq.length > 0) {
            computeSerializedSize = computeSerializedSize + this.aq.length * 8 + this.aq.length * 1;
        }
        return computeSerializedSize;
    }

    public e mergeFrom(final CodedInputByteBufferNano c) {
        try {
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
                    case 9: {
                        final int rg = WireFormatNano.getRepeatedFieldArrayLength(c, 9);
                        int i;
                        if (this.aq == null) {
                            i = 0;
                        } else {
                            i = this.aq.length;
                        }
                        final double[] aq = new double[rg + i];
                        if (i != 0) {
                            System.arraycopy(this.aq, 0, aq, 0, i);
                        }
                        while (i < aq.length - 1) {
                            aq[i] = c.readDouble();
                            c.readTag();
                            ++i;
                        }
                        aq[i] = c.readDouble();
                        this.aq = aq;
                        continue;
                    }
                    case 10: {
                        final int qm = c.readRawVarint32();
                        final int qn = c.pushLimit(qm);
                        final int n = qm / 8;
                        int j;
                        if (this.aq == null) {
                            j = 0;
                        } else {
                            j = this.aq.length;
                        }
                        final double[] aq2 = new double[n + j];
                        if (j != 0) {
                            System.arraycopy(this.aq, 0, aq2, 0, j);
                        }
                        while (j < aq2.length) {
                            aq2[j] = c.readDouble();
                            ++j;
                        }
                        this.aq = aq2;
                        c.popLimit(qn);
                        continue;
                    }
                }
            }
        }
        catch (IOException e) {
            return null;
        }
    }

    public void writeTo(final CodedOutputByteBufferNano b) {
        try {
            int i = 0;
            if (this.aq != null && this.aq.length > 0) {
                while (i < this.aq.length) {
                    b.writeDouble(1, this.aq[i]);
                    ++i;
                }
            }
            super.writeTo(b);
        }
        catch (IOException e) {
        }
    }
}
