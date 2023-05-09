package app.lawnchair.qsb.providers

import android.content.Intent
import app.lawnchair.qsb.ThemingMethod
import com.android.launcher3.R

object PixelSearch : QsbSearchProvider(
    id = "pixel_search",
    name = R.string.search_provider_pixel_search,
    // Use same style as Google Search
    icon = R.drawable.ic_super_g_color,
    themingMethod = ThemingMethod.THEME_BY_LAYER_ID,
    packageName = "rk.android.app.pixelsearch",
    website = "https://play.google.com/store/apps/details?id=rk.android.app.pixelsearch",
    type = QsbSearchProviderType.APP,
    supportVoiceIntent = true,
) {

    override fun handleCreateVoiceIntent(): Intent =
        Intent(Intent.ACTION_VOICE_COMMAND)

}
