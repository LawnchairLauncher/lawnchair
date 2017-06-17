package com.android.launcher3.reflection;

import com.android.launcher3.Utilities;
import android.content.SharedPreferences;
import com.android.launcher3.reflection.filter.a;
import com.android.launcher3.reflection.b2.d;
import java.io.File;
import java.util.regex.Pattern;

public class g
{
    static final String PREF_KEY_BACKGROUND_MODEL_VERSION = "background_model_version";
    static final String PREF_KEY_PROGRESS = "staged_batch_training_progress";
    public static final Pattern aG;
    public final File aH;
    public h aI;
    public final d aJ;
    public final a aK;
    public e aL;
    public final SharedPreferences aM;

    static {
        aG = Pattern.compile("^InProgress:(.+)$");
    }

    public g(final d aj, final SharedPreferences am, final File ah, final e al, final a ak) {
        this.aJ = aj;
        this.aM = am;
        this.aH = ah;
        this.aL = al;
        this.aK = ak;
        this.aI = null;
    }

    public void ak(final Throwable t, final h h) {
        synchronized (this) {
            if (this.aI == h) {
                this.aM.edit().remove("background_model_version").remove("staged_batch_training_progress").apply();
                this.aH.delete();
                this.aI = null;
            }
        }
    }

    public void al(final e e, final h h) {
        synchronized (this) {
            if (this.aI == h) {
                this.aL.X(e);
                this.aL.af();
                this.aH.delete();
                this.aI = null;
            }
        }
    }

    public int ai() {
        synchronized (this) {
            return this.aM.getInt("background_model_version", 0);
        }
    }

    public boolean aj() {
        synchronized (this) {
            final String string = this.aM.getString("staged_batch_training_progress", (String)null);
            return string != null && g.aG.matcher(string).find() && false; //CHANGE BACK..?
        }
    }

    public void am(final boolean b) {
        Label_0104: {
            if (!b) {
                break Label_0104;
            }
            try {
                this.aM.edit().putString("staged_batch_training_progress", "New").putInt("background_model_version", 23).apply();
                this.aH.delete();
                this.aI = new h(this);
                Utilities.THREAD_POOL_EXECUTOR.execute(this.aI);
                return;
            }
            finally {
            }
        }
    }
}