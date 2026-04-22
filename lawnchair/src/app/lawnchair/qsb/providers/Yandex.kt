package app.lawnchair.qsb.providers

import android.content.Intent
import com.android.launcher3.R

data object Yandex : QsbSearchProvider(
    id = "yandex",
    name = R.string.search_provider_yandex,
    icon = R.drawable.ic_yandex,
    themedIcon = R.drawable.ic_yandex_tinted,
    packageName = "com.yandex.searchapp",
    className = "ru.yandex.searchplugin.MainActivity",
    supportVoiceIntent = true,
    website = "https://ya.ru/",
) {

    override fun handleCreateVoiceIntent(): Intent = Intent(action)
        .addFlags(INTENT_FLAGS)
        .setClassName(packageName, "ru.yandex.searchplugin.AssistantActivityAlias")
}
