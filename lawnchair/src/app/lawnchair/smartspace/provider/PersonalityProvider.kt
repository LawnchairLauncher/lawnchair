package app.lawnchair.smartspace.provider

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import app.lawnchair.smartspace.model.SmartspaceAction
import app.lawnchair.smartspace.model.SmartspaceScores
import app.lawnchair.smartspace.model.SmartspaceTarget
import app.lawnchair.util.broadcastReceiverFlow
import com.android.launcher3.R
import java.util.Calendar
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.flow.map

class PersonalityProvider(context: Context) : SmartspaceDataSource(
    context,
    R.string.smartspace_personality_provider,
    { smartspacePersonalityProvider },
) {
    private val morningStrings = context.resources.getStringArray(R.array.smartspace_personality_greetings_morning)
    private val eveningStrings = context.resources.getStringArray(R.array.smartspace_personality_greetings_evening)
    private val nightStrings = context.resources.getStringArray(R.array.smartspace_personality_greetings_night)

    override val internalTargets = broadcastReceiverFlow(
        context,
        IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)

            // Difficulty testing? I know you do, uncomment this.
            // addAction(Intent.ACTION_SCREEN_ON)
        },
    ).map {
        val time = Calendar.getInstance()
        listOfNotNull(getSmartspaceTarget(time))
    }

    private fun getSmartspaceTarget(time: Calendar): SmartspaceTarget? {
        val randomIndex = abs(Random(time.dayOfYear).nextInt())

        val greeting = when {
            isMorning(time) -> morningStrings[randomIndex % morningStrings.size]
            isEvening(time) -> eveningStrings[randomIndex % eveningStrings.size]
            isNight(time) -> nightStrings[randomIndex % nightStrings.size]
            else -> return null
        }

        /* TODO: We really need target's expiration time which isn't supported on new Smartspace
         *   ImplRef: LawnchairSmartspaceController.kt @ 10-dev */
        return SmartspaceTarget(
            id = "personalityGreeting",
            headerAction = SmartspaceAction(
                id = "personalityGreetingAction",
                title = greeting,
            ),
            score = SmartspaceScores.SCORE_PERSONALITY,
            featureType = SmartspaceTarget.FeatureType.FEATURE_REMINDER,
        )
    }

    private fun isMorning(time: Calendar) = time.hourOfDay in 5 until 9
    private fun isEvening(time: Calendar) = time.hourOfDay in 19 until 21
    private fun isNight(time: Calendar) = time.hourOfDay in 22 until 24
            || time.hourOfDay in 0 until 4

    private val Calendar.dayOfYear: Int get() = get(Calendar.DAY_OF_YEAR)
    private val Calendar.hourOfDay: Int get() = get(Calendar.HOUR_OF_DAY)
}
