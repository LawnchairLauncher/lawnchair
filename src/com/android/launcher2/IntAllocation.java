/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.launcher2;

import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.util.Log;

import java.lang.reflect.Field;

public class IntAllocation {
    private RenderScript mRS;
    private int[] mBuffer;
    private Allocation mAlloc;

    public IntAllocation(RenderScript rs) {
        mRS = rs;
    }

    public void save() {
        Field[] fields = this.getClass().getFields();
        if (mBuffer == null) {
            int maxIndex = 0;
            for (Field f: fields) {
                AllocationIndex index = f.getAnnotation(AllocationIndex.class);
                if (index != null) {
                    int value = index.value();
                    if (value > maxIndex) {
                        maxIndex = value;
                    }
                }
            }
            mBuffer = new int[maxIndex+1];
            if (true) {
                // helpful debugging check
                for (Field f: fields) {
                    AllocationIndex index = f.getAnnotation(AllocationIndex.class);
                    if (index != null) {
                        int i = index.value();
                        if (mBuffer[i] != 0) {
                            throw new RuntimeException("@AllocationIndex on field in class "
                                    + this.getClass().getName() + " with duplicate value "
                                    + i + " for field " + f.getName() + ". The other field is "
                                    + fields[mBuffer[i]-1].getName() + '.');
                        }
                        mBuffer[i] = i+1;
                    }
                }
                for (int i=0; i<mBuffer.length; i++) {
                    mBuffer[i] = 0;
                }
            }
            mAlloc = Allocation.createSized(mRS, Element.USER_I32, mBuffer.length);
        }
        int[] buf = mBuffer;
        for (Field f: fields) {
            AllocationIndex index = f.getAnnotation(AllocationIndex.class);
            if (index != null) {
                try {
                    buf[index.value()] = f.getInt(this);
                } catch (IllegalAccessException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
        mAlloc.data(buf);
    }

    public void read() {
        int[] buf = mBuffer;
        if (buf != null) {
            mAlloc.readData(buf);
            Field[] fields = this.getClass().getFields();
            for (Field f: fields) {
                AllocationIndex index = f.getAnnotation(AllocationIndex.class);
                if (index != null) {
                    try {
                        f.setInt(this, buf[index.value()]);
                    } catch (IllegalAccessException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }
    }

    Allocation getAllocation() {
        return mAlloc;
    }
}
