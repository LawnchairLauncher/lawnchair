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

import android.util.Log;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

class ExifModifier {
    public static final String TAG = "ExifModifier";
    public static final boolean DEBUG = false;
    private final ByteBuffer mByteBuffer;
    private final ExifData mTagToModified;
    private final List<TagOffset> mTagOffsets = new ArrayList<TagOffset>();
    private final ExifInterface mInterface;
    private int mOffsetBase;

    private static class TagOffset {
        final int mOffset;
        final ExifTag mTag;

        TagOffset(ExifTag tag, int offset) {
            mTag = tag;
            mOffset = offset;
        }
    }

    protected ExifModifier(ByteBuffer byteBuffer, ExifInterface iRef) throws IOException,
            ExifInvalidFormatException {
        mByteBuffer = byteBuffer;
        mOffsetBase = byteBuffer.position();
        mInterface = iRef;
        InputStream is = null;
        try {
            is = new ByteBufferInputStream(byteBuffer);
            // Do not require any IFD;
            ExifParser parser = ExifParser.parse(is, mInterface);
            mTagToModified = new ExifData(parser.getByteOrder());
            mOffsetBase += parser.getTiffStartPosition();
            mByteBuffer.position(0);
        } finally {
            ExifInterface.closeSilently(is);
        }
    }

    protected ByteOrder getByteOrder() {
        return mTagToModified.getByteOrder();
    }

    protected boolean commit() throws IOException, ExifInvalidFormatException {
        InputStream is = null;
        try {
            is = new ByteBufferInputStream(mByteBuffer);
            int flag = 0;
            IfdData[] ifdDatas = new IfdData[] {
                    mTagToModified.getIfdData(IfdId.TYPE_IFD_0),
                    mTagToModified.getIfdData(IfdId.TYPE_IFD_1),
                    mTagToModified.getIfdData(IfdId.TYPE_IFD_EXIF),
                    mTagToModified.getIfdData(IfdId.TYPE_IFD_INTEROPERABILITY),
                    mTagToModified.getIfdData(IfdId.TYPE_IFD_GPS)
            };

            if (ifdDatas[IfdId.TYPE_IFD_0] != null) {
                flag |= ExifParser.OPTION_IFD_0;
            }
            if (ifdDatas[IfdId.TYPE_IFD_1] != null) {
                flag |= ExifParser.OPTION_IFD_1;
            }
            if (ifdDatas[IfdId.TYPE_IFD_EXIF] != null) {
                flag |= ExifParser.OPTION_IFD_EXIF;
            }
            if (ifdDatas[IfdId.TYPE_IFD_GPS] != null) {
                flag |= ExifParser.OPTION_IFD_GPS;
            }
            if (ifdDatas[IfdId.TYPE_IFD_INTEROPERABILITY] != null) {
                flag |= ExifParser.OPTION_IFD_INTEROPERABILITY;
            }

            ExifParser parser = ExifParser.parse(is, flag, mInterface);
            int event = parser.next();
            IfdData currIfd = null;
            while (event != ExifParser.EVENT_END) {
                switch (event) {
                    case ExifParser.EVENT_START_OF_IFD:
                        currIfd = ifdDatas[parser.getCurrentIfd()];
                        if (currIfd == null) {
                            parser.skipRemainingTagsInCurrentIfd();
                        }
                        break;
                    case ExifParser.EVENT_NEW_TAG:
                        ExifTag oldTag = parser.getTag();
                        ExifTag newTag = currIfd.getTag(oldTag.getTagId());
                        if (newTag != null) {
                            if (newTag.getComponentCount() != oldTag.getComponentCount()
                                    || newTag.getDataType() != oldTag.getDataType()) {
                                return false;
                            } else {
                                mTagOffsets.add(new TagOffset(newTag, oldTag.getOffset()));
                                currIfd.removeTag(oldTag.getTagId());
                                if (currIfd.getTagCount() == 0) {
                                    parser.skipRemainingTagsInCurrentIfd();
                                }
                            }
                        }
                        break;
                }
                event = parser.next();
            }
            for (IfdData ifd : ifdDatas) {
                if (ifd != null && ifd.getTagCount() > 0) {
                    return false;
                }
            }
            modify();
        } finally {
            ExifInterface.closeSilently(is);
        }
        return true;
    }

    private void modify() {
        mByteBuffer.order(getByteOrder());
        for (TagOffset tagOffset : mTagOffsets) {
            writeTagValue(tagOffset.mTag, tagOffset.mOffset);
        }
    }

    private void writeTagValue(ExifTag tag, int offset) {
        if (DEBUG) {
            Log.v(TAG, "modifying tag to: \n" + tag.toString());
            Log.v(TAG, "at offset: " + offset);
        }
        mByteBuffer.position(offset + mOffsetBase);
        switch (tag.getDataType()) {
            case ExifTag.TYPE_ASCII:
                byte buf[] = tag.getStringByte();
                if (buf.length == tag.getComponentCount()) {
                    buf[buf.length - 1] = 0;
                    mByteBuffer.put(buf);
                } else {
                    mByteBuffer.put(buf);
                    mByteBuffer.put((byte) 0);
                }
                break;
            case ExifTag.TYPE_LONG:
            case ExifTag.TYPE_UNSIGNED_LONG:
                for (int i = 0, n = tag.getComponentCount(); i < n; i++) {
                    mByteBuffer.putInt((int) tag.getValueAt(i));
                }
                break;
            case ExifTag.TYPE_RATIONAL:
            case ExifTag.TYPE_UNSIGNED_RATIONAL:
                for (int i = 0, n = tag.getComponentCount(); i < n; i++) {
                    Rational v = tag.getRational(i);
                    mByteBuffer.putInt((int) v.getNumerator());
                    mByteBuffer.putInt((int) v.getDenominator());
                }
                break;
            case ExifTag.TYPE_UNDEFINED:
            case ExifTag.TYPE_UNSIGNED_BYTE:
                buf = new byte[tag.getComponentCount()];
                tag.getBytes(buf);
                mByteBuffer.put(buf);
                break;
            case ExifTag.TYPE_UNSIGNED_SHORT:
                for (int i = 0, n = tag.getComponentCount(); i < n; i++) {
                    mByteBuffer.putShort((short) tag.getValueAt(i));
                }
                break;
        }
    }

    public void modifyTag(ExifTag tag) {
        mTagToModified.addTag(tag);
    }
}
