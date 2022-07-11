package app.lawnchair

import android.content.Context
import app.lawnchair.font.FontManager
import app.lawnchair.theme.ResourceTokenApplier
import rikka.layoutinflater.view.LayoutInflaterFactory

class LawnchairLayoutFactory(context: Context) : LayoutInflaterFactory() {

    init {
        addOnViewCreatedListener(FontManager.INSTANCE.get(context).onViewCreatedListener)
        addOnViewCreatedListener(ResourceTokenApplier)
    }
}
