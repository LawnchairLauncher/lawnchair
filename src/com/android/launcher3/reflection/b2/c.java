package com.android.launcher3.reflection.b2;

import com.android.launcher3.util.Preconditions;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import android.content.SharedPreferences;
import java.util.Set;
import java.io.File;

public class c
{
    private final File S;
    private final Set T;
    private final SharedPreferences U;

    public c(final SharedPreferences u, final File s, final List list) {
        this.U = u;
        this.S = s;
        this.T = new HashSet(list);
    }

    private void N(final File file) {
        int i = 0;
        if (file.isDirectory()) {
            for (File[] listFiles = file.listFiles(); i < listFiles.length; ++i) {
                this.N(listFiles[i]);
            }
            if (file.list().length == 0 && this.T.contains(file.getAbsolutePath())) {
                file.delete();
            }
        }
        else if (this.T.contains(file.getName()) || (file.getParentFile() != null && this.T.contains(file.getParentFile().getAbsolutePath()))) {
            file.delete();
        }
    }

    public void M() {
        synchronized (this) {
            Preconditions.assertNonUiThread();
            this.U.edit().clear().apply();
            if (this.S.exists() && this.S.isDirectory()) {
                final File[] listFiles = this.S.listFiles();
                for (int i = 0; i < listFiles.length; ++i) {
                    this.N(listFiles[i]);
                }
            }
        }
    }
}