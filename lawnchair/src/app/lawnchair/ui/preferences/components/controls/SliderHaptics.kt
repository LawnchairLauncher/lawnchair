/*
 * Copyright 2026, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.ui.preferences.components.controls

import android.os.Build
import android.os.VibrationAttributes
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.android.msdl.data.model.MSDLToken
import com.google.android.msdl.domain.InteractionProperties

@Keep
@RequiresApi(Build.VERSION_CODES.S)
internal fun playScaledSliderHaptic(
    playerWrapper: MSDLPlayerWrapper,
    token: MSDLToken,
    scale: Float,
) {
    val properties = InteractionProperties.DynamicVibrationScale(
        scale = scale,
        vibrationAttributes = VibrationAttributes.Builder()
            .setUsage(VibrationAttributes.USAGE_TOUCH)
            .build(),
    )
    playerWrapper.playToken(token, properties)
}
