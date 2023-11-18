package app.lawnchair.smartspace.model

data class SmartspaceTarget(
    val id: String,
    val headerAction: SmartspaceAction? = null,
    val baseAction: SmartspaceAction? = null,
    val score: Float = 0f,
    val featureType: FeatureType,
) {

    enum class FeatureType {
        FEATURE_UNDEFINED,
        FEATURE_WEATHER,
        FEATURE_CALENDAR,
        FEATURE_COMMUTE_TIME,
        FEATURE_FLIGHT,
        FEATURE_TIPS,
        FEATURE_REMINDER,
        FEATURE_ALARM,
        FEATURE_ONBOARDING,
        FEATURE_SPORTS,
        FEATURE_WEATHER_ALERT,
        FEATURE_CONSENT,
        FEATURE_STOCK_PRICE_CHANGE,
        FEATURE_SHOPPING_LIST,
        FEATURE_LOYALTY_CARD,
        FEATURE_MEDIA,
        FEATURE_BEDTIME_ROUTINE,
        FEATURE_FITNESS_TRACKING,
        FEATURE_ETA_MONITORING,
        FEATURE_MISSED_CALL,
        FEATURE_PACKAGE_TRACKING,
        FEATURE_TIMER,
        FEATURE_STOPWATCH,
        FEATURE_UPCOMING_ALARM,
    }
}
