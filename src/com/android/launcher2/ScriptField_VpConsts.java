/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.renderscript.*;
import android.content.res.Resources;
import android.util.Log;

public class ScriptField_VpConsts extends android.renderscript.Script.FieldBase {
    static public class Item {
        public static final int sizeof = 48;

        Float4 Position;
        Float4 ScaleOffset;
        Float2 BendPos;
        Float2 ImgSize;

        Item() {
            Position = new Float4();
            ScaleOffset = new Float4();
            BendPos = new Float2();
            ImgSize = new Float2();
        }

    }

    private Item mItemArray[];
    private FieldPacker mIOBuffer;
    public  ScriptField_VpConsts(RenderScript rs, int count) {
        mItemArray = null;
        mIOBuffer = null;
        {
            Element.Builder eb = new Element.Builder(rs);
            eb.add(Element.createVector(rs, Element.DataType.FLOAT_32, 4), "Position");
            eb.add(Element.createVector(rs, Element.DataType.FLOAT_32, 4), "ScaleOffset");
            eb.add(Element.createVector(rs, Element.DataType.FLOAT_32, 2), "BendPos");
            eb.add(Element.createVector(rs, Element.DataType.FLOAT_32, 2), "ImgSize");
            mElement = eb.create();
        }

        init(rs, count);
    }

    private void copyToArray(Item i, int index) {
        if (mIOBuffer == null) mIOBuffer = new FieldPacker(Item.sizeof * mType.getX() /* count */);
        mIOBuffer.reset(index * Item.sizeof);
        mIOBuffer.addF32(i.Position);
        mIOBuffer.addF32(i.ScaleOffset);
        mIOBuffer.addF32(i.BendPos);
        mIOBuffer.addF32(i.ImgSize);
    }

    public void set(Item i, int index, boolean copyNow) {
        if (mItemArray == null) mItemArray = new Item[mType.getX() /* count */];
        mItemArray[index] = i;
        if (copyNow)  {
            copyToArray(i, index);
            mAllocation.subData1D(index * Item.sizeof, Item.sizeof, mIOBuffer.getData());
        }

    }

    public void copyAll() {
        for (int ct=0; ct < mItemArray.length; ct++) copyToArray(mItemArray[ct], ct);
        mAllocation.data(mIOBuffer.getData());
    }

}

