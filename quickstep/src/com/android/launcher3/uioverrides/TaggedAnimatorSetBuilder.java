/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.uioverrides;

import android.animation.Animator;
import android.util.SparseArray;

import com.android.launcher3.anim.AnimatorSetBuilder;

import java.util.Collections;
import java.util.List;

public class TaggedAnimatorSetBuilder extends AnimatorSetBuilder {

    /**
     * Map of the index in {@link #mAnims} to tag. All the animations in {@link #mAnims} starting
     * from this index correspond to the tag (until a new tag is specified for an index)
     */
    private final SparseArray<Object> mTags = new SparseArray<>();

    @Override
    public void startTag(Object obj) {
        mTags.put(mAnims.size(), obj);
    }

    public List<Animator> getAnimationsForTag(Object tag) {
        int startIndex = mTags.indexOfValue(tag);
        if (startIndex < 0) {
            return Collections.emptyList();
        }
        int startPos = mTags.keyAt(startIndex);

        int endIndex = startIndex + 1;
        int endPos = endIndex >= mTags.size() ? mAnims.size() : mTags.keyAt(endIndex);

        return mAnims.subList(startPos, endPos);
    }
}
