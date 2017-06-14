package com.android.launcher3.reflection.c2;

import com.google.protobuf.nano.WireFormatNano;
import com.google.protobuf.nano.CodedInputByteBufferNano;

import java.io.IOException;

public final class a extends com.google.protobuf.nano.MessageNano
{
    public long Y;
    public String Z;
    public d aa;
    public b ab;
    public String packageName;

    public a() {
        this.clear();
    }

    public static a S(final CodedInputByteBufferNano c) {
        return new a().mergeFrom(c);
    }

    public a clear() {
        this.Z = "";
        this.Y = 0L;
        this.ab = null;
        this.aa = null;
        this.packageName = "";
        this.cachedSize = -1;
        return this;
    }

    protected int computeSerializedSize() {
        int computeSerializedSize = super.computeSerializedSize();
        if (!this.Z.equals("")) {
            computeSerializedSize += com.google.protobuf.nano.CodedOutputByteBufferNano.computeStringSize(1, this.Z);
        }
        if (this.Y != 0L) {
            computeSerializedSize += com.google.protobuf.nano.CodedOutputByteBufferNano.computeInt64Size(2, this.Y);
        }
        if (this.ab != null) {
            computeSerializedSize += com.google.protobuf.nano.CodedOutputByteBufferNano.computeGroupSize(3, this.ab);
        }
        if (this.aa != null) {
            computeSerializedSize += com.google.protobuf.nano.CodedOutputByteBufferNano.computeGroupSize(4, this.aa);
        }
        if (!this.packageName.equals("")) {
            computeSerializedSize += com.google.protobuf.nano.CodedOutputByteBufferNano.computeStringSize(5, this.packageName);
        }
        return computeSerializedSize;
    }

    public a mergeFrom(final CodedInputByteBufferNano c) {
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
                        this.Z = c.readString();
                        continue;
                    }
                    case 16: {
                        this.Y = c.readInt64();
                        continue;
                    }
                    case 26: {
                        if (this.ab == null) {
                            this.ab = new b();
                        }
                        c.readMessage(this.ab);
                        continue;
                    }
                    case 34: {
                        if (this.aa == null) {
                            this.aa = new d();
                        }
                        c.readMessage(this.aa);
                        continue;
                    }
                    case 42: {
                        this.packageName = c.readString();
                        continue;
                    }
                }
            }
        }
        catch (IOException e) {
            return null;
        }
    }

    public void writeTo(final com.google.protobuf.nano.CodedOutputByteBufferNano b) throws IOException {
        if (!this.Z.equals("")) {
            b.writeString(1, this.Z);
        }
        if (this.Y != 0L) {
            b.writeInt64(2, this.Y);
        }
        if (this.ab != null) {
            b.writeGroup(3, this.ab);
        }
        if (this.aa != null) {
            b.writeGroup(4, this.aa);
        }
        if (!this.packageName.equals("")) {
            b.writeString(5, this.packageName);
        }
        super.writeTo(b);
    }
}
