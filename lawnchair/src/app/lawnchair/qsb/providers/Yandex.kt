package app.lawnchair.qsb.providers

import android.content.Intent
import app.lawnchair.qsb.ThemingMethod
import com.android.launcher3.R

data object Yandex : QsbSearchProvider(
    id = "yandex",
    name = R.string.search_provider_yandex,
    icon = R.drawable.ic_yandex,
    themingMethod = ThemingMethod.THEME_BY_LAYER_ID,
    packageName = "com.yandex.searchapp",
    className = "ru.yandex.searchplugin.MainActivity",
    supportVoiceIntent = true,
    website = "https://ya.ru/",
) {

    override fun handleCreateVoiceIntent(): Intent =
        Intent(action)
            .addFlags(INTENT_FLAGS)
            .setClassName(packageName, "ru.yandex.searchplugin.AssistantActivityAlias")
}
