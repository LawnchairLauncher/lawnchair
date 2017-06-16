package com.android.launcher3.reflection.common.nano;

import com.google.protobuf.nano.InvalidProtocolBufferNanoException;
import com.google.protobuf.nano.WireFormatNano;
import com.google.protobuf.nano.CodedOutputByteBufferNano;
import com.google.protobuf.nano.MessageNano;

import java.io.IOException;

public final class d extends MessageNano
{
    public long Ma;
    public int Mb;
    public String Mc;
    public c[] Md;

    public d() {
        this.clear();
    }

    public static d parseFrom(final byte[] array) throws InvalidProtocolBufferNanoException {
        return (d)a.mergeFrom(new d(), array);
    }

    public d clear() {
        this.Ma = 0L;
        this.Mb = 0;
        this.Mc = "";
        this.Md = c.emptyArray();
        this.cachedSize = -1;
        return this;
    }

    protected int computeSerializedSize() {
        int computeSerializedSize = super.computeSerializedSize();
        if (this.Ma != 0L) {
            computeSerializedSize += CodedOutputByteBufferNano.computeInt64Size(1, this.Ma);
        }
        if (this.Mb != 0) {
            computeSerializedSize += CodedOutputByteBufferNano.computeInt32Size(2, this.Mb);
        }
        if (!this.Mc.equals("")) {
            computeSerializedSize += CodedOutputByteBufferNano.computeStringSize(3, this.Mc);
        }
        if (this.Md != null && this.Md.length > 0) {
            int n = computeSerializedSize;
            for (int i = 0; i < this.Md.length; ++i) {
                final c c = this.Md[i];
                if (c != null) {
                    n += CodedOutputByteBufferNano.computeGroupSize(4, c);
                }
            }
            computeSerializedSize = n;
        }
        return computeSerializedSize;
    }

    public d mergeFrom(final com.google.protobuf.nano.CodedInputByteBufferNano c) throws IOException {
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
                    this.Ma = c.readInt64();
                    continue;
                }
                case 16: {
                    this.Mb = c.readInt32();
                    continue;
                }
                case 26: {
                    this.Mc = c.readString();
                    continue;
                }
                case 34: {
                    final int rg = WireFormatNano.getRepeatedFieldArrayLength(c, 34);
                    int i;
                    if (this.Md != null) {
                        i = this.Md.length;
                    }
                    else {
                        i = 0;
                    }
                    final c[] md = new c[rg + i];
                    if (i != 0) {
                        System.arraycopy(this.Md, 0, md, 0, i);
                    }
                    while (i < md.length - 1) {
                        c.readMessage(md[i] = new c());
                        c.readTag();
                        ++i;
                    }
                    c.readMessage(md[i] = new c());
                    this.Md = md;
                    continue;
                }
            }
        }
    }

    public void writeTo(final CodedOutputByteBufferNano b) {
        try {
            int i = 0;
            if (this.Ma != 0L) {
                b.writeInt64(1, this.Ma);
            }
            if (this.Mb != 0) {
                b.writeInt32(2, this.Mb);
            }
            if (!this.Mc.equals("")) {
                b.writeString(3, this.Mc);
            }
            if (this.Md != null && this.Md.length > 0) {
                while (i < this.Md.length) {
                    final c c = this.Md[i];
                    if (c != null) {
                        b.writeGroup(4, c);
                    }
                    ++i;
                }
            }
            super.writeTo(b);
        }
        catch (IOException ex) {
        }
    }
}