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

import java.util.HashMap;
import java.util.Map;

/**
 * This class stores all the tags in an IFD.
 *
 * @see ExifData
 * @see ExifTag
 */
class IfdData {

    private final int mIfdId;
    private final Map<Short, ExifTag> mExifTags = new HashMap<Short, ExifTag>();
    private int mOffsetToNextIfd = 0;
    private static final int[] sIfds = {
            IfdId.TYPE_IFD_0, IfdId.TYPE_IFD_1, IfdId.TYPE_IFD_EXIF,
            IfdId.TYPE_IFD_INTEROPERABILITY, IfdId.TYPE_IFD_GPS
    };
    /**
     * Creates an IfdData with given IFD ID.
     *
     * @see IfdId#TYPE_IFD_0
     * @see IfdId#TYPE_IFD_1
     * @see IfdId#TYPE_IFD_EXIF
     * @see IfdId#TYPE_IFD_GPS
     * @see IfdId#TYPE_IFD_INTEROPERABILITY
     */
    IfdData(int ifdId) {
        mIfdId = ifdId;
    }

    static protected int[] getIfds() {
        return sIfds;
    }

    /**
     * Get a array the contains all {@link ExifTag} in this IFD.
     */
    protected ExifTag[] getAllTags() {
        return mExifTags.values().toArray(new ExifTag[mExifTags.size()]);
    }

    /**
     * Gets the ID of this IFD.
     *
     * @see IfdId#TYPE_IFD_0
     * @see IfdId#TYPE_IFD_1
     * @see IfdId#TYPE_IFD_EXIF
     * @see IfdId#TYPE_IFD_GPS
     * @see IfdId#TYPE_IFD_INTEROPERABILITY
     */
    protected int getId() {
        return mIfdId;
    }

    /**
     * Gets the {@link ExifTag} with given tag id. Return null if there is no
     * such tag.
     */
    protected ExifTag getTag(short tagId) {
        return mExifTags.get(tagId);
    }

    /**
     * Adds or replaces a {@link ExifTag}.
     */
    protected ExifTag setTag(ExifTag tag) {
        tag.setIfd(mIfdId);
        return mExifTags.put(tag.getTagId(), tag);
    }

    protected boolean checkCollision(short tagId) {
        return mExifTags.get(tagId) != null;
    }

    /**
     * Removes the tag of the given ID
     */
    protected void removeTag(short tagId) {
        mExifTags.remove(tagId);
    }

    /**
     * Gets the tags count in the IFD.
     */
    protected int getTagCount() {
        return mExifTags.size();
    }

    /**
     * Sets the offset of next IFD.
     */
    protected void setOffsetToNextIfd(int offset) {
        mOffsetToNextIfd = offset;
    }

    /**
     * Gets the offset of next IFD.
     */
    protected int getOffsetToNextIfd() {
        return mOffsetToNextIfd;
    }

    /**
     * Returns true if all tags in this two IFDs are equal. Note that tags of
     * IFDs offset or thumbnail offset will be ignored.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (obj instanceof IfdData) {
            IfdData data = (IfdData) obj;
            if (data.getId() == mIfdId && data.getTagCount() == getTagCount()) {
                ExifTag[] tags = data.getAllTags();
                for (ExifTag tag : tags) {
                    if (ExifInterface.isOffsetTag(tag.getTagId())) {
                        continue;
                    }
                    ExifTag tag2 = mExifTags.get(tag.getTagId());
                    if (!tag.equals(tag2)) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }
}
