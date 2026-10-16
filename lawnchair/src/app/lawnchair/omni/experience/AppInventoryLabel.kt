package app.lawnchair.omni.experience

/** Preserve real localized labels; a provider failure must never become the text "null". */
internal fun resolveAppLabel(label: CharSequence?, packageName: String): String = label?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: packageName
