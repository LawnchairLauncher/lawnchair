
package com.android.launcher2;

import android.content.res.Resources;
import android.renderscript.*;
import android.util.Log;

public class ScriptField_VpConsts
    extends android.renderscript.Script.FieldBase
{

    static public class Item {
        Item() {
            Position = new Float4();
            ScaleOffset = new Float4();
            BendPos = new Float2();
            ImgSize = new Float2();
        }

        public static final int sizeof = (12*4);

        Float4 Position;
        Float4 ScaleOffset;
        Float2 BendPos;
        Float2 ImgSize;
    }
    private Item mItemArray[];


    public ScriptField_VpConsts(RenderScript rs, int count) {
        // Allocate a pack/unpack buffer
        mIOBuffer = new FieldPacker(Item.sizeof * count);
        mItemArray = new Item[count];

        Element.Builder eb = new Element.Builder(rs);
        eb.add(Element.F32_4(rs), "Position");
        eb.add(Element.F32_4(rs), "ScaleOffset");
        eb.add(Element.F32_2(rs), "BendPos");
        eb.add(Element.F32_2(rs), "ImgSize");
        mElement = eb.create();

        init(rs, count);
    }

    private void copyToArray(Item i, int index) {
        mIOBuffer.reset(index * Item.sizeof);
        mIOBuffer.addF32(i.Position);
        mIOBuffer.addF32(i.ScaleOffset);
        mIOBuffer.addF32(i.BendPos);
        mIOBuffer.addF32(i.ImgSize);
    }

    public void set(Item i, int index, boolean copyNow) {
        mItemArray[index] = i;
        if (copyNow) {
            copyToArray(i, index);
            mAllocation.subData1D(index * Item.sizeof, Item.sizeof, mIOBuffer.getData());
        }
    }

    public void copyAll() {
        for (int ct=0; ct < mItemArray.length; ct++) {
            copyToArray(mItemArray[ct], ct);
        }
        mAllocation.data(mIOBuffer.getData());
    }


    private FieldPacker mIOBuffer;


}

