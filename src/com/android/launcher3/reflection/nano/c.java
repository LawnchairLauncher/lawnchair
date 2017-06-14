package com.android.launcher3.reflection.nano;

import java.io.IOException;
import java.util.Arrays;
import com.google.protobuf.nano.CodedOutputByteBufferNano;
import com.google.protobuf.nano.MessageNano;
import com.google.protobuf.nano.WireFormatNano;

public final class c extends MessageNano
{
    private static volatile c[] LQ;
    public String LR;
    public String LS;
    public byte[] LT;
    public int LU;
    public int LV;
    public f[] LW;
    public e[] LX;
    public e[] LY;
    public e[] LZ;

    public c() {
        this.clear();
    }

    public static c[] emptyArray() {
        if (c.LQ == null) {
            while (true) {
                while (true) {
                    synchronized (b.KD) {
                        if (c.LQ != null) {
                            break;
                        }
                    }
                    c.LQ = new c[0];
                    continue;
                }
            }
        }
        return c.LQ;
    }

    public c clear() {
        this.LR = "";
        this.LS = "";
        this.LT = WireFormatNano.EMPTY_BYTES;
        this.LU = 0;
        this.LV = 0;
        this.LW = f.emptyArray();
        this.LX = e.emptyArray();
        this.LY = e.emptyArray();
        this.LZ = e.emptyArray();
        this.cachedSize = -1;
        return this;
    }

    protected int computeSerializedSize() {
        int i = 0;
        int computeSerializedSize = super.computeSerializedSize();
        if (!this.LR.equals("")) {
            computeSerializedSize += CodedOutputByteBufferNano.computeStringSize(1, this.LR);
        }
        if (!this.LS.equals("")) {
            computeSerializedSize += CodedOutputByteBufferNano.computeStringSize(2, this.LS);
        }
        if (!Arrays.equals(this.LT, WireFormatNano.EMPTY_BYTES)) {
            computeSerializedSize += CodedOutputByteBufferNano.computeBytesSize(3, this.LT);
        }
        if (this.LV != 0) {
            computeSerializedSize += CodedOutputByteBufferNano.computeInt32Size(4, this.LV);
        }
        if (this.LW != null && this.LW.length > 0) {
            int n = computeSerializedSize;
            for (int j = 0; j < this.LW.length; ++j) {
                final f f = this.LW[j];
                if (f != null) {
                    n += CodedOutputByteBufferNano.computeGroupSize(5, f);
                }
            }
            computeSerializedSize = n;
        }
        if (this.LX != null && this.LX.length > 0) {
            int n2 = computeSerializedSize;
            for (int k = 0; k < this.LX.length; ++k) {
                final e e = this.LX[k];
                if (e != null) {
                    n2 += CodedOutputByteBufferNano.computeGroupSize(6, e);
                }
            }
            computeSerializedSize = n2;
        }
        if (this.LY != null && this.LY.length > 0) {
            int n3 = computeSerializedSize;
            for (int l = 0; l < this.LY.length; ++l) {
                final e e2 = this.LY[l];
                if (e2 != null) {
                    n3 += CodedOutputByteBufferNano.computeGroupSize(7, e2);
                }
            }
            computeSerializedSize = n3;
        }
        if (this.LZ != null && this.LZ.length > 0) {
            while (i < this.LZ.length) {
                final e e3 = this.LZ[i];
                if (e3 != null) {
                    computeSerializedSize += CodedOutputByteBufferNano.computeGroupSize(8, e3);
                }
                ++i;
            }
        }
        if (this.LU != 0) {
            computeSerializedSize += CodedOutputByteBufferNano.computeInt32Size(9, this.LU);
        }
        return computeSerializedSize;
    }

    public c mergeFrom(final com.google.protobuf.nano.CodedInputByteBufferNano c) {
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
                    case 10: {
                        this.LR = c.readString();
                        continue;
                    }
                    case 18: {
                        this.LS = c.readString();
                        continue;
                    }
                    case 26: {
                        this.LT = c.readBytes();
                        continue;
                    }
                    case 32: {
                        this.LV = c.readInt32();
                        continue;
                    }
                    case 42: {
                        final int rg = WireFormatNano.getRepeatedFieldArrayLength(c, 42);
                        int i;
                        if (this.LW != null) {
                            i = this.LW.length;
                        } else {
                            i = 0;
                        }
                        final f[] lw = new f[rg + i];
                        if (i != 0) {
                            System.arraycopy(this.LW, 0, lw, 0, i);
                        }
                        while (i < lw.length - 1) {
                            c.readMessage(lw[i] = new f());
                            c.readTag();
                            ++i;
                        }
                        c.readMessage(lw[i] = new f());
                        this.LW = lw;
                        continue;
                    }
                    case 50: {
                        final int rg2 = WireFormatNano.getRepeatedFieldArrayLength(c, 50);
                        int j;
                        if (this.LX != null) {
                            j = this.LX.length;
                        } else {
                            j = 0;
                        }
                        final e[] lx = new e[rg2 + j];
                        if (j != 0) {
                            System.arraycopy(this.LX, 0, lx, 0, j);
                        }
                        while (j < lx.length - 1) {
                            c.readMessage(lx[j] = new e());
                            c.readTag();
                            ++j;
                        }
                        c.readMessage(lx[j] = new e());
                        this.LX = lx;
                        continue;
                    }
                    case 58: {
                        final int rg3 = WireFormatNano.getRepeatedFieldArrayLength(c, 58);
                        int k;
                        if (this.LY != null) {
                            k = this.LY.length;
                        } else {
                            k = 0;
                        }
                        final e[] ly = new e[rg3 + k];
                        if (k != 0) {
                            System.arraycopy(this.LY, 0, ly, 0, k);
                        }
                        while (k < ly.length - 1) {
                            c.readMessage(ly[k] = new e());
                            c.readTag();
                            ++k;
                        }
                        c.readMessage(ly[k] = new e());
                        this.LY = ly;
                        continue;
                    }
                    case 66: {
                        final int rg4 = WireFormatNano.getRepeatedFieldArrayLength(c, 66);
                        int l;
                        if (this.LZ != null) {
                            l = this.LZ.length;
                        } else {
                            l = 0;
                        }
                        final e[] lz = new e[rg4 + l];
                        if (l != 0) {
                            System.arraycopy(this.LZ, 0, lz, 0, l);
                        }
                        while (l < lz.length - 1) {
                            c.readMessage(lz[l] = new e());
                            c.readTag();
                            ++l;
                        }
                        c.readMessage(lz[l] = new e());
                        this.LZ = lz;
                        continue;
                    }
                    case 72: {
                        this.LU = c.readInt32();
                        continue;
                    }
                }
            }
        }
        catch (IOException e) {
            return null;
        }
    }

    public void writeTo(final CodedOutputByteBufferNano b) throws IOException {
        int i = 0;
        if (!this.LR.equals("")) {
            b.writeString(1, this.LR);
        }
        if (!this.LS.equals("")) {
            b.writeString(2, this.LS);
        }
        if (!Arrays.equals(this.LT, WireFormatNano.EMPTY_BYTES)) {
            b.writeBytes(3, this.LT);
        }
        if (this.LV != 0) {
            b.writeInt32(4, this.LV);
        }
        if (this.LW != null && this.LW.length > 0) {
            for (int j = 0; j < this.LW.length; ++j) {
                final f f = this.LW[j];
                if (f != null) {
                    b.writeGroup(5, f);
                }
            }
        }
        if (this.LX != null && this.LX.length > 0) {
            for (int k = 0; k < this.LX.length; ++k) {
                final e e = this.LX[k];
                if (e != null) {
                    b.writeGroup(6, e);
                }
            }
        }
        if (this.LY != null && this.LY.length > 0) {
            for (int l = 0; l < this.LY.length; ++l) {
                final e e2 = this.LY[l];
                if (e2 != null) {
                    b.writeGroup(7, e2);
                }
            }
        }
        if (this.LZ != null && this.LZ.length > 0) {
            while (i < this.LZ.length) {
                final e e3 = this.LZ[i];
                if (e3 != null) {
                    b.writeGroup(8, e3);
                }
                ++i;
            }
        }
        if (this.LU != 0) {
            b.writeInt32(9, this.LU);
        }
        super.writeTo(b);
    }
}