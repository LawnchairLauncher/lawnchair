/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gallery3d.exif;

import java.io.InputStream;
import java.nio.ByteBuffer;

class ByteBufferInputStream extends InputStream {

    private ByteBuffer mBuf;

    public ByteBufferInputStream(ByteBuffer buf) {
        mBuf = buf;
    }

    @Override
    public int read() {
        if (!mBuf.hasRemaining()) {
            return -1;
        }
        return mBuf.get() & 0xFF;
    }

    @Override
    public int read(byte[] bytes, int off, int len) {
        if (!mBuf.hasRemaining()) {
            return -1;
        }

        len = Math.min(len, mBuf.remaining());
        mBuf.get(bytes, off, len);
        return len;
    }
}
