package app.lawnchair.factory

import android.appwidget.AppWidgetHost
import android.content.Context
import android.widget.RemoteViews
import com.android.internal.annotations.Keep
import com.android.launcher3.uioverrides.QuickstepWidgetHolder
import com.android.launcher3.widget.LauncherWidgetHolder
import java.util.function.IntConsumer

class LawnchairWidgetHolder(
    context: Context,
    intConsumer: IntConsumer?,
    interactionHandler: RemoteViews.InteractionHandler?,
) : QuickstepWidgetHolder(context, intConsumer, interactionHandler) {

    @Keep
    class LawnchairHolderFactory
    @Suppress("unused")
    constructor(context: Context) :
        QuickstepHolderFactory(context) {

        /**
         * @param context The context of the caller
         * @param appWidgetRemovedCallback The callback that is called when widgets are removed
         * @param interactionHandler The interaction handler when the widgets are clicked
         * @return A new [LauncherWidgetHolder] instance
         */
        override fun newInstance(
            context: Context,
            appWidgetRemovedCallback: IntConsumer?,
            interactionHandler: RemoteViews.InteractionHandler?,
        ): LauncherWidgetHolder {
            return try {
                LawnchairWidgetHolder(context, appWidgetRemovedCallback, interactionHandler)
            } catch (t: Throwable) {
                object : LauncherWidgetHolder(context, appWidgetRemovedCallback) {
                    override fun createHost(
                        context: Context,
                        appWidgetRemovedCallback: IntConsumer?,
                    ): AppWidgetHost {
                        val host = super.createHost(context, appWidgetRemovedCallback)
                        if (interactionHandler != null) {
                            host.setInteractionHandler(interactionHandler)
                        }
                        return host
                    }
                }
            }
        }
    }
}
