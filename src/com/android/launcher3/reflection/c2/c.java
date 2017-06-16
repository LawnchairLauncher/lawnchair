package com.android.launcher3.reflection.c2;

import com.google.protobuf.nano.CodedOutputByteBufferNano;
import com.google.protobuf.nano.WireFormatNano;
import com.google.protobuf.nano.MessageNano;

import java.io.IOException;

public final class c extends MessageNano
{
    private static volatile c[] ai;
    public String ag;
    public float ah;

    public c() {
        this.clear();
    }

    public static c[] emptyArray() {
        Label_0035: {
            if (c.ai != null) {
                break Label_0035;
            }
            synchronized (com.android.launcher3.reflection.common.nano.b.KD) {
                if (c.ai == null) {
                    c.ai = new c[0];
                }
                return c.ai;
            }
        }

        return c.ai;
    }

    public c clear() {
        this.ag = "";
        this.ah = 0.0f;
        this.cachedSize = -1;
        return this;
    }

    protected int computeSerializedSize() {
        int computeSerializedSize = super.computeSerializedSize();
        if (!this.ag.equals("")) {
            computeSerializedSize += CodedOutputByteBufferNano.computeStringSize(1, this.ag);
        }
        if (Float.floatToIntBits(this.ah) != Float.floatToIntBits(0.0f)) {
            computeSerializedSize += CodedOutputByteBufferNano.computeFloatSize(2, this.ah);
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
                        this.ag = c.readString();
                        continue;
                    }
                    case 21: {
                        this.ah = c.readFloat();
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
            if (!this.ag.equals("")) {
                b.writeString(1, this.ag);
            }
            if (Float.floatToIntBits(this.ah) != Float.floatToIntBits(0.0f)) {
                b.writeFloat(2, this.ah);
            }
            super.writeTo(b);
        }
        catch (IOException e) {
        }
    }
}
