package com.android.launcher3.reflection.nano;

import com.google.protobuf.nano.CodedInputByteBufferNano;
import com.google.protobuf.nano.CodedOutputByteBufferNano;
import com.google.protobuf.nano.MessageNano;
import com.google.protobuf.nano.WireFormatNano;

import java.io.IOException;

public final class f extends MessageNano
{
    private static volatile f[] Mg;
    public int Mh;
    public String name;

    public f() {
        this.clear();
    }

    public static f[] emptyArray() {
        if (f.Mg == null) {
            while (true) {
                while (true) {
                    synchronized (b.KD) {
                        if (f.Mg != null) {
                            break;
                        }
                    }
                    f.Mg = new f[0];
                    continue;
                }
            }
        }
        return f.Mg;
    }

    public f clear() {
        this.Mh = 0;
        this.name = "";
        this.cachedSize = -1;
        return this;
    }

    protected int computeSerializedSize() {
        int computeSerializedSize = super.computeSerializedSize();
        if (this.Mh != 0) {
            computeSerializedSize += CodedOutputByteBufferNano.computeInt32Size(1, this.Mh);
        }
        if (!this.name.equals("")) {
            computeSerializedSize += CodedOutputByteBufferNano.computeStringSize(2, this.name);
        }
        return computeSerializedSize;
    }

    public f mergeFrom(final CodedInputByteBufferNano c) throws IOException {
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
                    this.Mh = c.readInt32();
                    continue;
                }
                case 18: {
                    this.name = c.readString();
                    continue;
                }
            }
        }
    }

    public void writeTo(final CodedOutputByteBufferNano b) throws IOException {
        if (this.Mh != 0) {
            b.writeInt32(1, this.Mh);
        }
        if (!this.name.equals("")) {
            b.writeString(2, this.name);
        }
        super.writeTo(b);
    }
}