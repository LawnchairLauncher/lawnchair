/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.theme.utils

/**
 * Tonal Palette structure in Material.
 *
 * A tonal palette is comprised of 5 tonal ranges. Each tonal range includes the 13 stops, or
 * tonal swatches.
 *
 * Tonal range names are:
 * - Neutral (N)
 * - Neutral variant (NV)
 * - Primary (P)
 * - Secondary (S)
 * - Tertiary (T)
 */
class TonalPalette(
    // The neutral tonal range from the generated dynamic color palette.
    // Ordered from the lightest shade [neutral100] to the darkest shade [neutral0].
    val neutral100: Int,
    val neutral99: Int,
    val neutral98: Int,
    val neutral96: Int,
    val neutral95: Int,
    val neutral94: Int,
    val neutral92: Int,
    val neutral90: Int,
    val neutral87: Int,
    val neutral80: Int,
    val neutral70: Int,
    val neutral60: Int,
    val neutral50: Int,
    val neutral40: Int,
    val neutral30: Int,
    val neutral24: Int,
    val neutral22: Int,
    val neutral20: Int,
    val neutral17: Int,
    val neutral12: Int,
    val neutral10: Int,
    val neutral6: Int,
    val neutral4: Int,
    val neutral0: Int,

    // The neutral variant tonal range, sometimes called "neutral 2",  from the
    // generated dynamic color palette.
    // Ordered from the lightest shade [neutralVariant100] to the darkest shade [neutralVariant0].
    val neutralVariant100: Int,
    val neutralVariant99: Int,
    val neutralVariant98: Int,
    val neutralVariant96: Int,
    val neutralVariant95: Int,
    val neutralVariant94: Int,
    val neutralVariant92: Int,
    val neutralVariant90: Int,
    val neutralVariant87: Int,
    val neutralVariant80: Int,
    val neutralVariant70: Int,
    val neutralVariant60: Int,
    val neutralVariant50: Int,
    val neutralVariant40: Int,
    val neutralVariant30: Int,
    val neutralVariant24: Int,
    val neutralVariant22: Int,
    val neutralVariant20: Int,
    val neutralVariant17: Int,
    val neutralVariant12: Int,
    val neutralVariant10: Int,
    val neutralVariant6: Int,
    val neutralVariant4: Int,
    val neutralVariant0: Int,

    // The primary tonal range from the generated dynamic color palette.
    // Ordered from the lightest shade [primary100] to the darkest shade [primary0].
    val primary100: Int,
    val primary99: Int,
    val primary95: Int,
    val primary90: Int,
    val primary80: Int,
    val primary70: Int,
    val primary60: Int,
    val primary50: Int,
    val primary40: Int,
    val primary30: Int,
    val primary20: Int,
    val primary10: Int,
    val primary0: Int,

    // The secondary tonal range from the generated dynamic color palette.
    // Ordered from the lightest shade [secondary100] to the darkest shade [secondary0].
    val secondary100: Int,
    val secondary99: Int,
    val secondary95: Int,
    val secondary90: Int,
    val secondary80: Int,
    val secondary70: Int,
    val secondary60: Int,
    val secondary50: Int,
    val secondary40: Int,
    val secondary30: Int,
    val secondary20: Int,
    val secondary10: Int,
    val secondary0: Int,

    // The tertiary tonal range from the generated dynamic color palette.
    // Ordered from the lightest shade [tertiary100] to the darkest shade [tertiary0].
    val tertiary100: Int,
    val tertiary99: Int,
    val tertiary95: Int,
    val tertiary90: Int,
    val tertiary80: Int,
    val tertiary70: Int,
    val tertiary60: Int,
    val tertiary50: Int,
    val tertiary40: Int,
    val tertiary30: Int,
    val tertiary20: Int,
    val tertiary10: Int,
    val tertiary0: Int,
)

