package com.android.launcher3.reflection.c2;

import com.google.protobuf.nano.CodedOutputByteBufferNano;
import com.google.protobuf.nano.WireFormatNano;
import com.google.protobuf.nano.CodedInputByteBufferNano;
import com.google.protobuf.nano.MessageNano;

import java.io.IOException;

public final class b extends MessageNano
{
    public String ac;
    public String ad;
    public String ae;
    public String af;

    public b() {
        this.clear();
    }

    public b clear() {
        this.ac = "";
        this.ad = "";
        this.ae = "";
        this.af = "";
        this.cachedSize = -1;
        return this;
    }

    protected int computeSerializedSize() {
        int computeSerializedSize = super.computeSerializedSize();
        if (!this.ac.equals("")) {
            computeSerializedSize += CodedOutputByteBufferNano.computeStringSize(1, this.ac);
        }
        if (!this.ad.equals("")) {
            computeSerializedSize += CodedOutputByteBufferNano.computeStringSize(2, this.ad);
        }
        if (!this.ae.equals("")) {
            computeSerializedSize += CodedOutputByteBufferNano.computeStringSize(3, this.ae);
        }
        if (!this.af.equals("")) {
            computeSerializedSize += CodedOutputByteBufferNano.computeStringSize(4, this.af);
        }
        return computeSerializedSize;
    }

    public b mergeFrom(final CodedInputByteBufferNano c) {
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
                        this.ac = c.readString();
                        continue;
                    }
                    case 18: {
                        this.ad = c.readString();
                        continue;
                    }
                    case 26: {
                        this.ae = c.readString();
                        continue;
                    }
                    case 34: {
                        this.af = c.readString();
                        continue;
                    }
                }
            }
        }
        catch (IOException e) {
            return null;
        }
    }

    public void writeTo(final com.google.protobuf.nano.CodedOutputByteBufferNano b) {
        try {
            if (!this.ac.equals("")) {
                b.writeString(1, this.ac);
            }
            if (!this.ad.equals("")) {
                b.writeString(2, this.ad);
            }
            if (!this.ae.equals("")) {
                b.writeString(3, this.ae);
            }
            if (!this.af.equals("")) {
                b.writeString(4, this.af);
            }
            super.writeTo(b);
        }
        catch (IOException e) {

        }
    }
}
