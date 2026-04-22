package app.lawnchair.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.android.launcher3.R

// Thanks https://gitlab.com/nongthaihoang/google-sans-prime/-/commit/0f7b9d29f6ffe5005d22d81af264a86106f2450d
object GoogleSansFlex {
    object Display {
        object Normal {
            /**
             * Google Sans Flex Normal (400), Normal
             *
             * Fallback to Google Sans for unsupported glyphs
             **/
            @OptIn(ExperimentalTextApi::class)
            val Large = FontFamily(
                Font(
                    R.font.googlesansflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Normal.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(57.sp),
                    ),
                ),
                Font(
                    R.font.googlesansflex_variable, // Google Sans
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Normal.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(57.sp),
                    ),
                ),
            )

            /**
             * Google Sans Flex Normal (400), Normal
             *
             * Fallback to Google Sans for unsupported glyphs
             **/
            @OptIn(ExperimentalTextApi::class)
            val Medium = FontFamily(
                Font(
                    R.font.googlesansflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Normal.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(45.sp),
                    ),
                ),
                Font(
                    R.font.googlesansflex_variable, // Google Sans
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Normal.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(45.sp),
                    ),
                ),
            )

            /**
             * Google Sans Flex Normal (400), Normal
             *
             * Fallback to Google Sans for unsupported glyphs
             **/
            @OptIn(ExperimentalTextApi::class)
            val Small = FontFamily(
                Font(
                    R.font.googlesansflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Normal.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(36.sp),
                    ),
                ),
                Font(
                    R.font.googlesansflex_variable, // Google Sans
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Normal.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(36.sp),
                    ),
                ),
            )
        }
        object Emphasized {
            /**
             * Google Sans Flex Medium (500), Emphasized
             *
             * Fallback to Google Sans for unsupported glyphs (no round variation settings)
             **/
            @OptIn(ExperimentalTextApi::class)
            val Large = FontFamily(
                Font(
                    R.font.googlesansflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        ExtendedFontVariation.round(100),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(57.sp),
                    ),
                ),
                Font(
                    R.font.googlesansflex_variable, // Google Sans
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(57.sp),
                    ),
                ),
            )

            /**
             * Google Sans Flex Medium (500), Emphasized
             *
             * Fallback to Google Sans for unsupported glyphs (no round variation settings)
             **/
            @OptIn(ExperimentalTextApi::class)
            val Medium = FontFamily(
                Font(
                    R.font.googlesansflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        ExtendedFontVariation.round(100),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(45.sp),
                    ),
                ),
                Font(
                    R.font.googlesansflex_variable, // Google Sans
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(45.sp),
                    ),
                ),
            )

            /**
             * Google Sans Flex Medium (500), Emphasized
             *
             * Fallback to Google Sans for unsupported glyphs (no round variation settings)
             **/
            @OptIn(ExperimentalTextApi::class)
            val Small = FontFamily(
                Font(
                    R.font.googlesansflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        ExtendedFontVariation.round(100),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(36.sp),
                    ),
                ),
                Font(
                    R.font.googlesansflex_variable, // Google Sans
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(36.sp),
                    ),
                ),
            )
        }
    }
    object Headline {
        object Normal {
            /**
             * Google Sans Flex Normal (400), Normal
             *
             * Fallback to Google Sans for unsupported glyphs
             **/
            @OptIn(ExperimentalTextApi::class)
            val Large = FontFamily(
                Font(
                    R.font.googlesansflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Normal.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(32.sp),
                    ),
                ),
                Font(
                    R.font.googlesansflex_variable, // Google Sans
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Normal.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(32.sp),
                    ),
                ),
            )

            /**
             * Google Sans Flex Normal (400), Normal
             *
             * Fallback to Google Sans for unsupported glyphs
             **/
            @OptIn(ExperimentalTextApi::class)
            val Medium = FontFamily(
                Font(
                    R.font.googlesansflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Normal.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(28.sp),
                    ),
                ),
                Font(
                    R.font.googlesansflex_variable, // Google Sans
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Normal.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(28.sp),
                    ),
                ),
            )

            /**
             * Google Sans Flex Normal (400), Normal
             *
             * Fallback to Google Sans for unsupported glyphs
             **/
            @OptIn(ExperimentalTextApi::class)
            val Small = FontFamily(
                Font(
                    R.font.googlesansflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Normal.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(24.sp),
                    ),
                ),
                Font(
                    R.font.googlesansflex_variable, // Google Sans
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Normal.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(24.sp),
                    ),
                ),
            )
        }
        object Emphasized {
            /**
             * Google Sans Flex Medium (500), Emphasized
             *
             * Fallback to Google Sans for unsupported glyphs (no round variation settings)
             **/
            @OptIn(ExperimentalTextApi::class)
            val Large = FontFamily(
                Font(
                    R.font.googlesansflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        ExtendedFontVariation.round(100),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(32.sp),
                    ),
                ),
                Font(
                    R.font.googlesansflex_variable, // Google Sans
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(32.sp),
                    ),
                ),
            )

            /**
             * Google Sans Flex Medium (500), Emphasized
             *
             * Fallback to Google Sans for unsupported glyphs (no round variation settings)
             **/
            @OptIn(ExperimentalTextApi::class)
            val Medium = FontFamily(
                Font(
                    R.font.googlesansflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        ExtendedFontVariation.round(100),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(28.sp),
                    ),
                ),
                Font(
                    R.font.googlesansflex_variable, // Google Sans
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(28.sp),
                    ),
                ),
            )

            /**
             * Google Sans Flex Medium (500), Emphasized
             *
             * Fallback to Google Sans for unsupported glyphs (no round variation settings)
             **/
            @OptIn(ExperimentalTextApi::class)
            val Small = FontFamily(
                Font(
                    R.font.googlesansflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        ExtendedFontVariation.round(100),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(24.sp),
                    ),
                ),
                Font(
                    R.font.googlesansflex_variable, // Google Sans
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(24.sp),
                    ),
                ),
            )
        }
    }
    object Title {
        object Normal {
            /**
             * Google Sans Flex Medium (500), Normal
             *
             * Fallback to Google Sans for unsupported glyphs
             **/
            @OptIn(ExperimentalTextApi::class)
            val Large = FontFamily(
                Font(
                    R.font.googlesansflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(22.sp),
                    ),
                ),
                Font(
                    R.font.googlesansflex_variable, // Google Sans
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(22.sp),
                    ),
                ),
            )

            /**
             * Google Sans Flex Medium (500), Normal
             *
             * Fallback to Google Sans for unsupported glyphs
             **/
            @OptIn(ExperimentalTextApi::class)
            val Medium = FontFamily(
                Font(
                    R.font.googlesansflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(16.sp),
                    ),
                ),
                Font(
                    R.font.googlesansflex_variable, // Google Sans
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(16.sp),
                    ),
                ),
            )

            /**
             * Google Sans Flex Medium (500), Normal
             *
             * Fallback to Google Sans for unsupported glyphs
             **/
            @OptIn(ExperimentalTextApi::class)
            val Small = FontFamily(
                Font(
                    R.font.googlesansflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(14.sp),
                    ),
                ),
                Font(
                    R.font.googlesansflex_variable, // Google Sans
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(14.sp),
                    ),
                ),
            )
        }
        object Emphasized {
            /**
             * Google Sans Flex Medium (500), Emphasized
             *
             * Fallback to Google Sans for unsupported glyphs (no round variation settings)
             **/
            @OptIn(ExperimentalTextApi::class)
            val Large = FontFamily(
                Font(
                    R.font.googlesansflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        ExtendedFontVariation.round(100),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(22.sp),
                    ),
                ),
                Font(
                    R.font.googlesansflex_variable, // Google Sans
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(22.sp),
                    ),
                ),
            )

            /**
             * Google Sans Flex Medium (500), Emphasized
             *
             * Fallback to Google Sans for unsupported glyphs (no round variation settings)
             **/
            @OptIn(ExperimentalTextApi::class)
            val Medium = FontFamily(
                Font(
                    R.font.googlesansflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        ExtendedFontVariation.round(100),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(16.sp),
                    ),
                ),
                Font(
                    R.font.googlesansflex_variable, // Google Sans
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(16.sp),
                    ),
                ),
            )

            /**
             * Google Sans Flex Medium (500), Emphasized
             *
             * Fallback to Google Sans for unsupported glyphs (no round variation settings)
             **/
            @OptIn(ExperimentalTextApi::class)
            val Small = FontFamily(
                Font(
                    R.font.googlesansflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        ExtendedFontVariation.round(100),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(14.sp),
                    ),
                ),
                Font(
                    R.font.googlesansflex_variable, // Google Sans
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(14.sp),
                    ),
                ),
            )
        }
    }
    object Body {
        object Normal {
            /**
             * Google Sans Flex Normal (400), Normal
             *
             * Fallback to Google Sans for unsupported glyphs
             **/
            @OptIn(ExperimentalTextApi::class)
            val Large = FontFamily(
                Font(
                    R.font.googlesansflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Normal.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(16.sp),
                    ),
                ),
                Font(
                    R.font.googlesansflex_variable, // Google Sans
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Normal.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(16.sp),
                    ),
                ),
            )

            /**
             * Google Sans Flex Normal (400), Normal
             *
             * Fallback to Google Sans for unsupported glyphs
             **/
            @OptIn(ExperimentalTextApi::class)
            val Medium = FontFamily(
                Font(
                    R.font.googlesansflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Normal.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(14.sp),
                    ),
                ),
                Font(
                    R.font.googlesansflex_variable, // Google Sans
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Normal.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(14.sp),
                    ),
                ),
            )

            /**
             * Google Sans Flex Normal (400), Normal
             *
             * Fallback to Google Sans for unsupported glyphs
             **/
            @OptIn(ExperimentalTextApi::class)
            val Small = FontFamily(
                Font(
                    R.font.googlesansflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Normal.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(12.sp),
                    ),
                ),
                Font(
                    R.font.googlesansflex_variable, // Google Sans
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Normal.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(12.sp),
                    ),
                ),
            )
        }
        object Emphasized {
            /**
             * Google Sans Flex Medium (500), Emphasized
             *
             * Fallback to Google Sans for unsupported glyphs (no round variation settings)
             **/
            @OptIn(ExperimentalTextApi::class)
            val Large = FontFamily(
                Font(
                    R.font.googlesansflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        ExtendedFontVariation.round(100),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(16.sp),
                    ),
                ),
                Font(
                    R.font.googlesansflex_variable, // Google Sans
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(16.sp),
                    ),
                ),
            )

            /**
             * Google Sans Flex Medium (500), Emphasized
             *
             * Fallback to Google Sans for unsupported glyphs (no round variation settings)
             **/
            @OptIn(ExperimentalTextApi::class)
            val Medium = FontFamily(
                Font(
                    R.font.googlesansflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        ExtendedFontVariation.round(100),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(14.sp),
                    ),
                ),
                Font(
                    R.font.googlesansflex_variable, // Google Sans
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(14.sp),
                    ),
                ),
            )

            /**
             * Google Sans Flex Medium (500), Emphasized
             *
             * Fallback to Google Sans for unsupported glyphs (no round variation settings)
             **/
            @OptIn(ExperimentalTextApi::class)
            val Small = FontFamily(
                Font(
                    R.font.googlesansflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        ExtendedFontVariation.round(100),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(12.sp),
                    ),
                ),
                Font(
                    R.font.googlesansflex_variable, // Google Sans
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(12.sp),
                    ),
                ),
            )
        }
    }
    object Label {
        object Normal {
            /**
             * Google Sans Flex Medium (500), Normal
             *
             * Fallback to Google Sans for unsupported glyphs
             **/
            @OptIn(ExperimentalTextApi::class)
            val Small = FontFamily(
                Font(
                    R.font.googlesansflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(11.sp),
                    ),
                ),
                Font(
                    R.font.googlesansflex_variable, // Google Sans
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(11.sp),
                    ),
                ),
            )

            /**
             * Google Sans Flex Medium (500), Normal
             *
             * Fallback to Google Sans for unsupported glyphs
             **/
            @OptIn(ExperimentalTextApi::class)
            val Medium = FontFamily(
                Font(
                    R.font.googlesansflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(12.sp),
                    ),
                ),
                Font(
                    R.font.googlesansflex_variable, // Google Sans
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(12.sp),
                    ),
                ),
            )

            /**
             * Google Sans Flex Medium (500), Normal
             *
             * Fallback to Google Sans for unsupported glyphs
             **/
            @OptIn(ExperimentalTextApi::class)
            val Large = FontFamily(
                Font(
                    R.font.googlesansflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(14.sp),
                    ),
                ),
                Font(
                    R.font.googlesansflex_variable, // Google Sans
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.Medium.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(14.sp),
                    ),
                ),
            )
        }
        object Emphasized {
            /**
             * Google Sans Flex Semibold (600), Emphasized
             *
             * Fallback to Google Sans for unsupported glyphs (no round variation settings)
             **/
            @OptIn(ExperimentalTextApi::class)
            val Small = FontFamily(
                Font(
                    R.font.googlesansflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.SemiBold.weight),
                        ExtendedFontVariation.round(100),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(11.sp),
                    ),
                ),
                Font(
                    R.font.googlesansflex_variable, // Google Sans
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.SemiBold.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(11.sp),
                    ),
                ),
            )

            /**
             * Google Sans Flex Semibold (600), Emphasized
             *
             * Fallback to Google Sans for unsupported glyphs (no round variation settings)
             **/
            @OptIn(ExperimentalTextApi::class)
            val Medium = FontFamily(
                Font(
                    R.font.googlesansflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.SemiBold.weight),
                        ExtendedFontVariation.round(100),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(12.sp),
                    ),
                ),
                Font(
                    R.font.googlesansflex_variable, // Google Sans
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.SemiBold.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(12.sp),
                    ),
                ),
            )

            /**
             * Google Sans Flex Semibold (600), Emphasized
             *
             * Fallback to Google Sans for unsupported glyphs (no round variation settings)
             **/
            @OptIn(ExperimentalTextApi::class)
            val Large = FontFamily(
                Font(
                    R.font.googlesansflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.SemiBold.weight),
                        ExtendedFontVariation.round(100),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(14.sp),
                    ),
                ),
                Font(
                    R.font.googlesansflex_variable, // Google Sans
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(FontWeight.SemiBold.weight),
                        FontVariation.width(100f),
                        FontVariation.grade(0),
                        FontVariation.opticalSizing(14.sp),
                    ),
                ),
            )
        }
    }
}
