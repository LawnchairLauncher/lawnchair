package ch.deletescape.lawnchair;

import android.content.ComponentName;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

public interface AppFilter {

    boolean shouldShowApp(ComponentName app, Context context);

}
