package com.android.launcher3.reflection.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class b {
    public static byte[] aO(final File p0) {
        int len = (int) p0.length();
        int pos = 0;
        int hasRead = 0;
        byte[] buff = new byte[len];

        try {
            FileInputStream file = new FileInputStream(p0);
            BufferedInputStream in = new BufferedInputStream(file);
            while (pos < len && hasRead >= 0) {
                hasRead = in.read(buff, pos, len - pos);
                pos += hasRead;
            }
            in.close();
            file.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
            return new byte[0];
        }

        return buff;
    }
}
