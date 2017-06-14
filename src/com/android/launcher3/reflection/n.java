package com.android.launcher3.reflection;

public class n
{
    static String generateRandomDeviceId() {
        final int n = 15;
        final StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; ++i) {
            sb.append(Integer.toHexString((int)(Math.random() * 16.0)));
        }
        return sb.toString();
    }
}
