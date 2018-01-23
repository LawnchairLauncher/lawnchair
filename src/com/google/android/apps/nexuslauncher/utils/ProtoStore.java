package com.google.android.apps.nexuslauncher.utils;

import android.content.Context;
import android.util.Log;

import com.android.launcher3.util.IOUtils;
import com.google.protobuf.nano.MessageNano;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

public class ProtoStore {
    private final Context mContext;

    public ProtoStore(Context context) {
        mContext = context.getApplicationContext();
    }

    public void dw(MessageNano p1, String name) {
        try {
            FileOutputStream fos = mContext.openFileOutput(name, 0);
            if (p1 != null) {
                fos.write(MessageNano.toByteArray(p1));
            } else {
                Log.d("ProtoStore", "deleting " + name);
                mContext.deleteFile(name);
            }
        } catch (FileNotFoundException e) {
            Log.d("ProtoStore", "file does not exist " + name);
        } catch (Exception e) {
            Log.e("ProtoStore", "unable to write file " + name, e);
        }
    }

    public boolean dv(String name, MessageNano a) {
        File fileStreamPath = mContext.getFileStreamPath(name);
        try {
            MessageNano.mergeFrom(a, IOUtils.toByteArray(fileStreamPath));
            return true;
        }
        catch (FileNotFoundException ex2) {
            Log.d("ProtoStore", "no cached data");
        }
        catch (Exception ex) {
            Log.e("ProtoStore", "unable to load data", ex);
        }
        return false;
    }
}
