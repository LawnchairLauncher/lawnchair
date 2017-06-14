package com.android.launcher3.reflection.nano;

import com.google.protobuf.nano.CodedOutputByteBufferNano;
import com.google.protobuf.nano.WireFormatNano; //f
import com.google.protobuf.nano.CodedInputByteBufferNano; //c
import com.google.protobuf.nano.InvalidProtocolBufferNanoException;

import java.io.IOException;

public final class a extends com.google.protobuf.nano.MessageNano
{
    public String LA;
    public String LB;
    public long LC;
    public long LD;
    public long LE;
    public String LF;
    public long LG;
    public long LH;
    public b[] LI;
    public String Ly;
    public String Lz;

    public a() {
        this.clear();
    }

    public static a Sx(final CodedInputByteBufferNano c) {
        return new a().mergeFrom(c);
    }

    public static a parseFrom(final byte[] array) {
        try {
            return com.google.protobuf.nano.MessageNano.mergeFrom(new a(), array);
        }
        catch (InvalidProtocolBufferNanoException e) {
            return null;
        }
    }

    public a clear() {
        final long lh = 0L;
        this.Ly = "";
        this.Lz = "";
        this.LA = "";
        this.LB = "";
        this.LC = lh;
        this.LD = lh;
        this.LE = lh;
        this.LF = "";
        this.LG = lh;
        this.LH = lh;
        this.LI = b.emptyArray();
        this.cachedSize = -1;
        return this;
    }

    protected int computeSerializedSize() {
        final long n = 0L;
        int computeSerializedSize = super.computeSerializedSize();
        if (!this.Ly.equals("")) {
            computeSerializedSize += CodedOutputByteBufferNano.computeStringSize(1, this.Ly);
        }
        if (!this.Lz.equals("")) {
            computeSerializedSize += CodedOutputByteBufferNano.computeStringSize(2, this.Lz);
        }
        if (!this.LA.equals("")) {
            computeSerializedSize += CodedOutputByteBufferNano.computeStringSize(3, this.LA);
        }
        if (!this.LB.equals("")) {
            computeSerializedSize += CodedOutputByteBufferNano.computeStringSize(4, this.LB);
        }
        if (this.LC != n) {
            computeSerializedSize += CodedOutputByteBufferNano.computeInt64Size(5, this.LC);
        }
        if (this.LD != n) {
            computeSerializedSize += CodedOutputByteBufferNano.computeInt64Size(6, this.LD);
        }
        if (this.LE != n) {
            computeSerializedSize += CodedOutputByteBufferNano.computeInt64Size(7, this.LE);
        }
        if (!this.LF.equals("")) {
            computeSerializedSize += CodedOutputByteBufferNano.computeStringSize(8, this.LF);
        }
        if (this.LG != n) {
            computeSerializedSize += CodedOutputByteBufferNano.computeInt64Size(9, this.LG);
        }
        if (this.LH != n) {
            computeSerializedSize += CodedOutputByteBufferNano.computeInt64Size(10, this.LH);
        }
        if (this.LI != null && this.LI.length > 0) {
            int n2 = computeSerializedSize;
            for (int i = 0; i < this.LI.length; ++i) {
                final b b = this.LI[i];
                if (b != null) {
                    n2 += CodedOutputByteBufferNano.computeMessageSize(11, b);
                }
            }
            computeSerializedSize = n2;
        }
        return computeSerializedSize;
    }

    public a mergeFrom(final CodedInputByteBufferNano c) {
        while (true) {
            try {
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
                        this.Ly = c.readString();
                        continue;
                    }
                    case 18: {
                        this.Lz = c.readString();
                        continue;
                    }
                    case 26: {
                        this.LA = c.readString();
                        continue;
                    }
                    case 34: {
                        this.LB = c.readString();
                        continue;
                    }
                    case 40: {
                        this.LC = c.readInt64();
                        continue;
                    }
                    case 48: {
                        this.LD = c.readInt64();
                        continue;
                    }
                    case 56: {
                        this.LE = c.readInt64();
                        continue;
                    }
                    case 66: {
                        this.LF = c.readString();
                        continue;
                    }
                    case 72: {
                        this.LG = c.readInt64();
                        continue;
                    }
                    case 80: {
                        this.LH = c.readInt64();
                        continue;
                    }
                    case 90: {
                        final int rg = WireFormatNano.getRepeatedFieldArrayLength(c, 90);
                        int i;
                        if (this.LI != null) {
                            i = this.LI.length;
                        }
                        else {
                            i = 0;
                        }
                        final b[] li = new b[rg + i];
                        if (i != 0) {
                            System.arraycopy(this.LI, 0, li, 0, i);
                        }
                        while (i < li.length - 1) {
                            c.readMessage(li[i] = new b());
                            c.readTag();
                            ++i;
                        }
                        c.readMessage(li[i] = new b());
                        this.LI = li;
                        continue;
                    }
                }

            }
            catch (IOException e) {

            }
        }
    }

    @Override
    public void writeTo(final CodedOutputByteBufferNano b) throws IOException {
        final long n = 0L;
        int i = 0;
        if (!this.Ly.equals("")) {
            b.writeString(1, this.Ly);
        }
        if (!this.Lz.equals("")) {
            b.writeString(2, this.Lz);
        }
        if (!this.LA.equals("")) {
            b.writeString(3, this.LA);
        }
        if (!this.LB.equals("")) {
            b.writeString(4, this.LB);
        }
        if (this.LC != n) {
            b.writeInt64(5, this.LC);
        }
        if (this.LD != n) {
            b.writeInt64(6, this.LD);
        }
        if (this.LE != n) {
            b.writeInt64(7, this.LE);
        }
        if (!this.LF.equals("")) {
            b.writeString(8, this.LF);
        }
        if (this.LG != n) {
            b.writeInt64(9, this.LG);
        }
        if (this.LH != n) {
            b.writeInt64(10, this.LH);
        }
        if (this.LI != null && this.LI.length > 0) {
            while (i < this.LI.length) {
                final b b2 = this.LI[i];
                if (b2 != null) {
                    b.writeGroup(11, b2);
                }
                ++i;
            }
        }
        super.writeTo(b);
    }
}