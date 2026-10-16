package app.lawnchair.fold

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.abs

/**
 * MASS: icons have weight. Each icon is a [SpringAnimation] whose target is a displacement toward
 * the hinge proportional to how fast the fold moves × (0.3 + distance from the hinge), so the far
 * icons lag the most; when the motion stops they spring back into their cells. Icons on the fixed
 * half get [FoldTunables.MASS_FIXED_HALF_FACTOR] of the effect. Opening pulls icons toward the hinge,
 * closing flings them away from it; on the cover screen after a fold the cover icons get the
 * fixed-half share, measured from the hinge edge.
 *
 * The speed is the progress velocity, from the hinge or from the timer (so it shows without a
 * sensor), normalized by [FoldScene.timeScale] so the slow replay shows the same displacements; the
 * springs are slowed by the same factor. Nothing else moves and nothing is hidden: the layout is
 * fully visible from the first frame. The transition ends once every spring is at rest (see
 * [FoldStyle.isSettled]).
 */
@RequiresApi(Build.VERSION_CODES.Q)
class MassFoldStyle : FoldStyle() {

    private val tracker = FoldItemTracker(::release)

    private val displacement = object : FloatPropertyCompat<FoldItem>("foldMass") {
        override fun getValue(item: FoldItem): Float = item.value

        override fun setValue(item: FoldItem, value: Float) {
            item.value = value
            apply(item)
        }
    }

    private var maxDisplacementPx = 0f
    private var stiffness = SpringForce.STIFFNESS_LOW
    private var towardHingeX = 0f
    private var towardHingeY = 0f

    override fun onStart(scene: FoldScene): Boolean {
        maxDisplacementPx = scene.dpToPx(FoldTunables.MASS_MAX_DISPLACEMENT_DP)
        val timeScale = scene.timeScale.coerceAtLeast(1f)
        // A spring's period goes with 1/sqrt(stiffness): slowing time by s divides stiffness by s².
        stiffness = (FoldTunables.MASS_STIFFNESS / (timeScale * timeScale)).coerceAtLeast(MIN_STIFFNESS)
        return true
    }

    override fun onPreDraw(scene: FoldScene, progress: Float) {
        val geometry = scene.geometry
        towardHingeX = geometry.towardHingeX
        towardHingeY = geometry.towardHingeY
        tracker.update(scene)
        val speed = (scene.velocity * scene.timeScale / FoldTunables.MASS_REFERENCE_VELOCITY).coerceIn(-1f, 1f)
        val items = tracker.items
        for (i in items.indices) {
            val item = items[i]
            if (item.isWidget) continue
            val share = if (item.onMovingHalf) 1f else FoldTunables.MASS_FIXED_HALF_FACTOR
            val target = maxDisplacementPx * speed * (DISTANCE_BASE + item.distance) * share
            val spring = springOf(item)
            // glow holds the last target given to the spring
            if (spring.isRunning || abs(target - item.glow) > MIN_TARGET_CHANGE || abs(target - item.value) > MIN_TARGET_CHANGE) {
                item.glow = target
                spring.animateToFinalPosition(target)
            }
        }
    }

    override fun isSettled(scene: FoldScene): Boolean {
        val items = tracker.items
        for (i in items.indices) {
            val spring = items[i].styleState as? SpringAnimation ?: continue
            if (spring.isRunning) return false
        }
        return true
    }

    override fun onEnd(scene: FoldScene) {
        tracker.clear()
    }

    private fun springOf(item: FoldItem): SpringAnimation {
        (item.styleState as? SpringAnimation)?.let { return it }
        val spring = SpringAnimation(item, displacement)
        spring.spring = SpringForce(0f)
            .setStiffness(stiffness)
            .setDampingRatio(FoldTunables.MASS_DAMPING_RATIO)
        spring.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS)
        item.styleState = spring
        return spring
    }

    /** Displacement is along the hinge normal: toward the hinge on the moving half, the other way elsewhere. */
    private fun apply(item: FoldItem) {
        val sign = if (item.onMovingHalf) 1f else -1f
        item.setTranslation(towardHingeX * sign * item.value, towardHingeY * sign * item.value)
    }

    private fun release(item: FoldItem) {
        (item.styleState as? SpringAnimation)?.cancel()
        item.styleState = null
        item.value = 0f
        item.glow = 0f
        item.resetTranslation()
    }

    private companion object {
        const val DISTANCE_BASE = 0.3f
        const val MIN_TARGET_CHANGE = 0.5f
        const val MIN_STIFFNESS = 5f
    }
}
