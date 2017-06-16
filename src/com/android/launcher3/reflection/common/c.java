package com.android.launcher3.reflection.common;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.io.DataOutputStream;

public class c
{
    public static void SK(final DataOutputStream dataOutputStream, final Map map) throws IOException {
        dataOutputStream.writeInt(map.size());
        for (final Object o : map.entrySet()) {
            Map.Entry<Object, Object> entry = (Map.Entry<Object, Object>) o;
            SL(dataOutputStream, entry.getKey());
            SL(dataOutputStream, entry.getValue());
        }
    }

    private static void SL(final DataOutputStream dataOutputStream, final Object o) throws IOException {
        int i = 0;
        if (!(o instanceof Integer)) {
            if (!(o instanceof Long)) {
                if (!(o instanceof Float)) {
                    if (!(o instanceof String)) {
                        if (!(o instanceof HashMap)) {
                            if (!(o instanceof int[])) {
                                if (o instanceof float[]) {
                                    final float[] array = (float[])o;
                                    dataOutputStream.writeInt(array.length);
                                    while (i < array.length) {
                                        dataOutputStream.writeFloat(array[i]);
                                        ++i;
                                    }
                                }
                            }
                            else {
                                final int[] array2 = (int[])o;
                                dataOutputStream.writeInt(array2.length);
                                while (i < array2.length) {
                                    dataOutputStream.writeInt(array2[i]);
                                    ++i;
                                }
                            }
                        }
                        else {
                            SK(dataOutputStream, (Map)o);
                        }
                    }
                    else {
                        dataOutputStream.writeUTF((String)o);
                    }
                }
                else {
                    dataOutputStream.writeFloat((float)o);
                }
            }
            else {
                dataOutputStream.writeLong((long)o);
            }
        }
        else {
            dataOutputStream.writeInt((int)o);
        }
    }

    public static HashMap SM(final DataInputStream dataInputStream, final Class clazz, final Class clazz2) throws IOException {
        final HashMap<Object, Object> hashMap = new HashMap<Object, Object>();
        for (int int1 = dataInputStream.readInt(), i = 0; i < int1; ++i) {
            hashMap.put(SN(dataInputStream, clazz), SN(dataInputStream, clazz2));
        }
        return hashMap;
    }

    private static Object SN(final DataInputStream dataInputStream, final Class clazz) throws IOException {
        int i = 0;
        if (clazz == Integer.class) {
            return dataInputStream.readInt();
        }
        if (clazz == Long.class) {
            return dataInputStream.readLong();
        }
        if (clazz == Float.class) {
            return dataInputStream.readFloat();
        }
        if (clazz == String.class) {
            return dataInputStream.readUTF();
        }
        if (clazz == int[].class) {
            final int int1 = dataInputStream.readInt();
            final int[] array = new int[int1];
            while (i < int1) {
                array[i] = dataInputStream.readInt();
                ++i;
            }
            return array;
        }
        if (clazz != float[].class) {
            return null;
        }
        final int int2 = dataInputStream.readInt();
        final float[] array2 = new float[int2];
        while (i < int2) {
            array2[i] = dataInputStream.readFloat();
            ++i;
        }
        return array2;
    }

    public static float[] SO(final double n, final double n2) {
        final double radians = Math.toRadians(n);
        final double radians2 = Math.toRadians(n2);
        final double cos = Math.cos(radians);
        return new float[] { (float)(Math.cos(radians2) * cos), (float)(Math.sin(radians2) * cos), (float)Math.sin(radians) };
    }
}
