package com.android.launcher3.reflection;

import java.io.IOException;
import com.android.launcher3.reflection.util.b;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.reflection.b2.d;
import java.io.File;
import android.content.SharedPreferences;

public class i
{
    public void aw(final SharedPreferences sharedPreferences, final File file, final e e, final d d, final g g) {
        final int n = 23;
        Preconditions.assertNonUiThread();
        e.ag(file);
        Label_0104: {
            final byte[] ao = b.aO(file);
            try {
                final com.android.launcher3.reflection.common.nano.d from = com.android.launcher3.reflection.common.nano.d.parseFrom(ao);
                final int mb = from.Mb;
                if (mb >= n) {
                    break Label_0104;
                }
                e.ab();
                if (g.aj() && g.ai() == n) {
                    g.am(false);
                    return;
                }
            }
            catch (IOException ex2) {}
            g.am(true);
            return;
        }
        e.ab();
    }
}
