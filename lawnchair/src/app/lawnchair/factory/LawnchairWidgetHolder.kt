package app.lawnchair.factory

import android.content.Context
import com.android.internal.annotations.Keep
import com.android.launcher3.widget.LauncherWidgetHolder
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class LawnchairWidgetHolder @AssistedInject constructor(
    @Assisted("UI_CONTEXT") context: Context,
) : LauncherWidgetHolder(context) {

    @Keep
    @AssistedFactory
    interface Factory : WidgetHolderFactory {
        override fun newInstance(@Assisted("UI_CONTEXT") context: Context): LauncherWidgetHolder
    }
}
