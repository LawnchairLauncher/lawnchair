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

import java.io.UnsupportedEncodingException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This class stores the EXIF header in IFDs according to the JPEG
 * specification. It is the result produced by {@link ExifReader}.
 *
 * @see ExifReader
 * @see IfdData
 */
class ExifData {
    private static final String TAG = "ExifData";
    private static final byte[] USER_COMMENT_ASCII = {
            0x41, 0x53, 0x43, 0x49, 0x49, 0x00, 0x00, 0x00
    };
    private static final byte[] USER_COMMENT_JIS = {
            0x4A, 0x49, 0x53, 0x00, 0x00, 0x00, 0x00, 0x00
    };
    private static final byte[] USER_COMMENT_UNICODE = {
            0x55, 0x4E, 0x49, 0x43, 0x4F, 0x44, 0x45, 0x00
    };

    private final IfdData[] mIfdDatas = new IfdData[IfdId.TYPE_IFD_COUNT];
    private byte[] mThumbnail;
    private ArrayList<byte[]> mStripBytes = new ArrayList<byte[]>();
    private final ByteOrder mByteOrder;

    ExifData(ByteOrder order) {
        mByteOrder = order;
    }

    /**
     * Gets the compressed thumbnail. Returns null if there is no compressed
     * thumbnail.
     *
     * @see #hasCompressedThumbnail()
     */
    protected byte[] getCompressedThumbnail() {
        return mThumbnail;
    }

    /**
     * Sets the compressed thumbnail.
     */
    protected void setCompressedThumbnail(byte[] thumbnail) {
        mThumbnail = thumbnail;
    }

    /**
     * Returns true it this header contains a compressed thumbnail.
     */
    protected boolean hasCompressedThumbnail() {
        return mThumbnail != null;
    }

    /**
     * Adds an uncompressed strip.
     */
    protected void setStripBytes(int index, byte[] strip) {
        if (index < mStripBytes.size()) {
            mStripBytes.set(index, strip);
        } else {
            for (int i = mStripBytes.size(); i < index; i++) {
                mStripBytes.add(null);
            }
            mStripBytes.add(strip);
        }
    }

    /**
     * Gets the strip count.
     */
    protected int getStripCount() {
        return mStripBytes.size();
    }

    /**
     * Gets the strip at the specified index.
     *
     * @exceptions #IndexOutOfBoundException
     */
    protected byte[] getStrip(int index) {
        return mStripBytes.get(index);
    }

    /**
     * Returns true if this header contains uncompressed strip.
     */
    protected boolean hasUncompressedStrip() {
        return mStripBytes.size() != 0;
    }

    /**
     * Gets the byte order.
     */
    protected ByteOrder getByteOrder() {
        return mByteOrder;
    }

    /**
     * Returns the {@link IfdData} object corresponding to a given IFD if it
     * exists or null.
     */
    protected IfdData getIfdData(int ifdId) {
        if (ExifTag.isValidIfd(ifdId)) {
            return mIfdDatas[ifdId];
        }
        return null;
    }

    /**
     * Adds IFD data. If IFD data of the same type already exists, it will be
     * replaced by the new data.
     */
    protected void addIfdData(IfdData data) {
        mIfdDatas[data.getId()] = data;
    }

    /**
     * Returns the {@link IfdData} object corresponding to a given IFD or
     * generates one if none exist.
     */
    protected IfdData getOrCreateIfdData(int ifdId) {
        IfdData ifdData = mIfdDatas[ifdId];
        if (ifdData == null) {
            ifdData = new IfdData(ifdId);
            mIfdDatas[ifdId] = ifdData;
        }
        return ifdData;
    }

    /**
     * Returns the tag with a given TID in the given IFD if the tag exists.
     * Otherwise returns null.
     */
    protected ExifTag getTag(short tag, int ifd) {
        IfdData ifdData = mIfdDatas[ifd];
        return (ifdData == null) ? null : ifdData.getTag(tag);
    }

    /**
     * Adds the given ExifTag to its default IFD and returns an existing ExifTag
     * with the same TID or null if none exist.
     */
    protected ExifTag addTag(ExifTag tag) {
        if (tag != null) {
            int ifd = tag.getIfd();
            return addTag(tag, ifd);
        }
        return null;
    }

    /**
     * Adds the given ExifTag to the given IFD and returns an existing ExifTag
     * with the same TID or null if none exist.
     */
    protected ExifTag addTag(ExifTag tag, int ifdId) {
        if (tag != null && ExifTag.isValidIfd(ifdId)) {
            IfdData ifdData = getOrCreateIfdData(ifdId);
            return ifdData.setTag(tag);
        }
        return null;
    }

    protected void clearThumbnailAndStrips() {
        mThumbnail = null;
        mStripBytes.clear();
    }

    /**
     * Removes the thumbnail and its related tags. IFD1 will be removed.
     */
    protected void removeThumbnailData() {
        clearThumbnailAndStrips();
        mIfdDatas[IfdId.TYPE_IFD_1] = null;
    }

    /**
     * Removes the tag with a given TID and IFD.
     */
    protected void removeTag(short tagId, int ifdId) {
        IfdData ifdData = mIfdDatas[ifdId];
        if (ifdData == null) {
            return;
        }
        ifdData.removeTag(tagId);
    }

    /**
     * Decodes the user comment tag into string as specified in the EXIF
     * standard. Returns null if decoding failed.
     */
    protected String getUserComment() {
        IfdData ifdData = mIfdDatas[IfdId.TYPE_IFD_0];
        if (ifdData == null) {
            return null;
        }
        ExifTag tag = ifdData.getTag(ExifInterface.getTrueTagKey(ExifInterface.TAG_USER_COMMENT));
        if (tag == null) {
            return null;
        }
        if (tag.getComponentCount() < 8) {
            return null;
        }

        byte[] buf = new byte[tag.getComponentCount()];
        tag.getBytes(buf);

        byte[] code = new byte[8];
        System.arraycopy(buf, 0, code, 0, 8);

        try {
            if (Arrays.equals(code, USER_COMMENT_ASCII)) {
                return new String(buf, 8, buf.length - 8, "US-ASCII");
            } else if (Arrays.equals(code, USER_COMMENT_JIS)) {
                return new String(buf, 8, buf.length - 8, "EUC-JP");
            } else if (Arrays.equals(code, USER_COMMENT_UNICODE)) {
                return new String(buf, 8, buf.length - 8, "UTF-16");
            } else {
                return null;
            }
        } catch (UnsupportedEncodingException e) {
            Log.w(TAG, "Failed to decode the user comment");
            return null;
        }
    }

    /**
     * Returns a list of all {@link ExifTag}s in the ExifData or null if there
     * are none.
     */
    protected List<ExifTag> getAllTags() {
        ArrayList<ExifTag> ret = new ArrayList<ExifTag>();
        for (IfdData d : mIfdDatas) {
            if (d != null) {
                ExifTag[] tags = d.getAllTags();
                if (tags != null) {
                    for (ExifTag t : tags) {
                        ret.add(t);
                    }
                }
            }
        }
        if (ret.size() == 0) {
            return null;
        }
        return ret;
    }

    /**
     * Returns a list of all {@link ExifTag}s in a given IFD or null if there
     * are none.
     */
    protected List<ExifTag> getAllTagsForIfd(int ifd) {
        IfdData d = mIfdDatas[ifd];
        if (d == null) {
            return null;
        }
        ExifTag[] tags = d.getAllTags();
        if (tags == null) {
            return null;
        }
        ArrayList<ExifTag> ret = new ArrayList<ExifTag>(tags.length);
        for (ExifTag t : tags) {
            ret.add(t);
        }
        if (ret.size() == 0) {
            return null;
        }
        return ret;
    }

    /**
     * Returns a list of all {@link ExifTag}s with a given TID or null if there
     * are none.
     */
    protected List<ExifTag> getAllTagsForTagId(short tag) {
        ArrayList<ExifTag> ret = new ArrayList<ExifTag>();
        for (IfdData d : mIfdDatas) {
            if (d != null) {
                ExifTag t = d.getTag(tag);
                if (t != null) {
                    ret.add(t);
                }
            }
        }
        if (ret.size() == 0) {
            return null;
        }
        return ret;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (obj instanceof ExifData) {
            ExifData data = (ExifData) obj;
            if (data.mByteOrder != mByteOrder ||
                    data.mStripBytes.size() != mStripBytes.size() ||
                    !Arrays.equals(data.mThumbnail, mThumbnail)) {
                return false;
            }
            for (int i = 0; i < mStripBytes.size(); i++) {
                if (!Arrays.equals(data.mStripBytes.get(i), mStripBytes.get(i))) {
                    return false;
                }
            }
            for (int i = 0; i < IfdId.TYPE_IFD_COUNT; i++) {
                IfdData ifd1 = data.getIfdData(i);
                IfdData ifd2 = getIfdData(i);
                if (ifd1 != ifd2 && ifd1 != null && !ifd1.equals(ifd2)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

}
