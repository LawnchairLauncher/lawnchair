package com.android.launcher3.reflection.common.nano;

import com.google.protobuf.nano.WireFormatNano;
import com.google.protobuf.nano.CodedInputByteBufferNano;
import com.google.protobuf.nano.CodedOutputByteBufferNano;
import com.google.protobuf.nano.MessageNano;

import java.io.IOException;

public final class e extends MessageNano
{
    private static volatile e[] Me;
    public long Mf;
    public int key;

    public e() {
        this.clear();
    }

    public static e[] emptyArray() {
        if (e.Me == null) {
            while (true) {
                synchronized (b.KD) {
                    if (e.Me != null) {
                        break;
                    }
                }
                e.Me = new e[0];
                continue;
            }
        }
        return e.Me;
    }

    public e clear() {
        this.key = 0;
        this.Mf = 0L;
        this.cachedSize = -1;
        return this;
    }

    protected int computeSerializedSize() {
        int computeSerializedSize = super.computeSerializedSize();
        if (this.key != 0) {
            computeSerializedSize += CodedOutputByteBufferNano.computeInt32Size(1, this.key);
        }
        if (this.Mf != 0L) {
            computeSerializedSize += CodedOutputByteBufferNano.computeInt64Size(2, this.Mf);
        }
        return computeSerializedSize;
    }

    public e mergeFrom(final CodedInputByteBufferNano c) throws IOException {
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
                    this.key = c.readInt32();
                    continue;
                }
                case 16: {
                    this.Mf = c.readInt64();
                    continue;
                }
            }
        }
    }

    public void writeTo(final CodedOutputByteBufferNano b) throws IOException {
        if (this.key != 0) {
            b.writeInt32(1, this.key);
        }
        if (this.Mf != 0L) {
            b.writeInt64(2, this.Mf);
        }
        super.writeTo(b);
    }
}
