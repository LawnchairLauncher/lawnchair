package com.android.launcher3.reflection.c2;

import com.google.protobuf.nano.CodedOutputByteBufferNano;
import com.google.protobuf.nano.WireFormatNano;
import com.google.protobuf.nano.CodedInputByteBufferNano;
import com.google.protobuf.nano.MessageNano;

import java.io.IOException;

public final class d extends MessageNano
{
    public c[] aj;
    public e ak;
    public c[] al;
    public c[] am;
    public c[] an;
    public c[] ao;
    public int ap;

    public d() {
        this.clear();
    }

    public d clear() {
        this.ap = 0;
        this.al = c.emptyArray();
        this.am = c.emptyArray();
        this.ao = c.emptyArray();
        this.aj = c.emptyArray();
        this.an = c.emptyArray();
        this.ak = null;
        this.cachedSize = -1;
        return this;
    }

    protected int computeSerializedSize() {
        int i = 0;
        int computeSerializedSize = super.computeSerializedSize();
        if (this.ap != 0) {
            computeSerializedSize += CodedOutputByteBufferNano.computeInt32Size(1, this.ap);
        }
        if (this.al != null && this.al.length > 0) {
            int n = computeSerializedSize;
            for (int j = 0; j < this.al.length; ++j) {
                final c c = this.al[j];
                if (c != null) {
                    n += CodedOutputByteBufferNano.computeGroupSize(2, c);
                }
            }
            computeSerializedSize = n;
        }
        if (this.am != null && this.am.length > 0) {
            int n2 = computeSerializedSize;
            for (int k = 0; k < this.am.length; ++k) {
                final c c2 = this.am[k];
                if (c2 != null) {
                    n2 += CodedOutputByteBufferNano.computeGroupSize(3, c2);
                }
            }
            computeSerializedSize = n2;
        }
        if (this.ao != null && this.ao.length > 0) {
            int n3 = computeSerializedSize;
            for (int l = 0; l < this.ao.length; ++l) {
                final c c3 = this.ao[l];
                if (c3 != null) {
                    n3 += CodedOutputByteBufferNano.computeGroupSize(4, c3);
                }
            }
            computeSerializedSize = n3;
        }
        if (this.aj != null && this.aj.length > 0) {
            int n4 = computeSerializedSize;
            for (int n5 = 0; n5 < this.aj.length; ++n5) {
                final c c4 = this.aj[n5];
                if (c4 != null) {
                    n4 += CodedOutputByteBufferNano.computeGroupSize(5, c4);
                }
            }
            computeSerializedSize = n4;
        }
        if (this.an != null && this.an.length > 0) {
            while (i < this.an.length) {
                final c c5 = this.an[i];
                if (c5 != null) {
                    computeSerializedSize += CodedOutputByteBufferNano.computeGroupSize(6, c5);
                }
                ++i;
            }
        }
        if (this.ak != null) {
            computeSerializedSize += CodedOutputByteBufferNano.computeGroupSize(7, this.ak);
        }
        return computeSerializedSize;
    }

    public d mergeFrom(final com.google.protobuf.nano.CodedInputByteBufferNano c) {
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
                    case 8: {
                        this.ap = c.readInt32();
                        continue;
                    }
                    case 18: {
                        final int rg = WireFormatNano.getRepeatedFieldArrayLength(c, 18);
                        int i;
                        if (this.al == null) {
                            i = 0;
                        } else {
                            i = this.al.length;
                        }
                        final c[] al = new c[rg + i];
                        if (i != 0) {
                            System.arraycopy(this.al, 0, al, 0, i);
                        }
                        while (i < al.length - 1) {
                            c.readMessage(al[i] = new c());
                            c.readTag();
                            ++i;
                        }
                        c.readMessage(al[i] = new c());
                        this.al = al;
                        continue;
                    }
                    case 26: {
                        final int rg2 = WireFormatNano.getRepeatedFieldArrayLength(c, 26);
                        int j;
                        if (this.am == null) {
                            j = 0;
                        } else {
                            j = this.am.length;
                        }
                        final c[] am = new c[rg2 + j];
                        if (j != 0) {
                            System.arraycopy(this.am, 0, am, 0, j);
                        }
                        while (j < am.length - 1) {
                            c.readMessage(am[j] = new c());
                            c.readTag();
                            ++j;
                        }
                        c.readMessage(am[j] = new c());
                        this.am = am;
                        continue;
                    }
                    case 34: {
                        final int rg3 = WireFormatNano.getRepeatedFieldArrayLength(c, 34);
                        int k;
                        if (this.ao == null) {
                            k = 0;
                        } else {
                            k = this.ao.length;
                        }
                        final c[] ao = new c[rg3 + k];
                        if (k != 0) {
                            System.arraycopy(this.ao, 0, ao, 0, k);
                        }
                        while (k < ao.length - 1) {
                            c.readMessage(ao[k] = new c());
                            c.readTag();
                            ++k;
                        }
                        c.readMessage(ao[k] = new c());
                        this.ao = ao;
                        continue;
                    }
                    case 42: {
                        final int rg4 = WireFormatNano.getRepeatedFieldArrayLength(c, 42);
                        int l;
                        if (this.aj == null) {
                            l = 0;
                        } else {
                            l = this.aj.length;
                        }
                        final c[] aj = new c[rg4 + l];
                        if (l != 0) {
                            System.arraycopy(this.aj, 0, aj, 0, l);
                        }
                        while (l < aj.length - 1) {
                            c.readMessage(aj[l] = new c());
                            c.readTag();
                            ++l;
                        }
                        c.readMessage(aj[l] = new c());
                        this.aj = aj;
                        continue;
                    }
                    case 50: {
                        final int rg5 = WireFormatNano.getRepeatedFieldArrayLength(c, 50);
                        int length;
                        if (this.an == null) {
                            length = 0;
                        } else {
                            length = this.an.length;
                        }
                        final c[] an = new c[rg5 + length];
                        if (length != 0) {
                            System.arraycopy(this.an, 0, an, 0, length);
                        }
                        while (length < an.length - 1) {
                            c.readMessage(an[length] = new c());
                            c.readTag();
                            ++length;
                        }
                        c.readMessage(an[length] = new c());
                        this.an = an;
                        continue;
                    }
                    case 58: {
                        if (this.ak == null) {
                            this.ak = new e();
                        }
                        c.readMessage(this.ak);
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
            if (this.ap != 0) {
                b.writeInt32(1, this.ap);
            }
            if (this.al != null && this.al.length > 0) {
                for (int j = 0; j < this.al.length; ++j) {
                    final c c = this.al[j];
                    if (c != null) {
                        b.writeGroup(2, c);
                    }
                }
            }
            if (this.am != null && this.am.length > 0) {
                for (int k = 0; k < this.am.length; ++k) {
                    final c c2 = this.am[k];
                    if (c2 != null) {
                        b.writeGroup(3, c2);
                    }
                }
            }
            if (this.ao != null && this.ao.length > 0) {
                for (int l = 0; l < this.ao.length; ++l) {
                    final c c3 = this.ao[l];
                    if (c3 != null) {
                        b.writeGroup(4, c3);
                    }
                }
            }
            if (this.aj != null && this.aj.length > 0) {
                for (int n = 0; n < this.aj.length; ++n) {
                    final c c4 = this.aj[n];
                    if (c4 != null) {
                        b.writeGroup(5, c4);
                    }
                }
            }
            if (this.an != null && this.an.length > 0) {
                while (i < this.an.length) {
                    final c c5 = this.an[i];
                    if (c5 != null) {
                        b.writeGroup(6, c5);
                    }
                    ++i;
                }
            }
            if (this.ak != null) {
                b.writeGroup(7, this.ak);
            }
            super.writeTo(b);
        }
        catch (IOException e) {
        }
    }
}
