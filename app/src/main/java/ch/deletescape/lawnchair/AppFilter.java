package ch.deletescape.lawnchair;

import android.content.ComponentName;
import android.content.Context;

public interface AppFilter {

    boolean shouldShowApp(ComponentName app, Context context);

}
