package app.lawnchair

import android.graphics.Insets
import android.os.Build
import android.view.WindowInsetsAnimationControlListener
import android.view.WindowInsetsAnimationController
import androidx.annotation.RequiresApi
import com.android.launcher3.anim.AnimatedFloat

@RequiresApi(Build.VERSION_CODES.R)
class SearchBarShowInsetsHandler(private val shiftRange: Float) : WindowInsetsAnimationControlListener {

    val progress = AnimatedFloat(this::updateInsets)
    private var animationController: WindowInsetsAnimationController? = null

    override fun onReady(controller: WindowInsetsAnimationController, types: Int) {
        animationController = controller
    }

    override fun onFinished(controller: WindowInsetsAnimationController) {
        animationController = null
    }

    override fun onCancelled(controller: WindowInsetsAnimationController?) {
        animationController = null
    }

    private fun updateInsets() {
        val controller = animationController ?: return
        val shownBottomInset = controller.shownStateInsets.bottom
        val hiddenBottomInset = controller.hiddenStateInsets.bottom
        val targetBottomInset = (hiddenBottomInset + progress.value * shiftRange).toInt()
        val bottomInset = targetBottomInset.coerceIn(hiddenBottomInset, shownBottomInset)
        controller.setInsetsAndAlpha(
            Insets.of(0, 0, 0, bottomInset),
            1f,
            progress.value,
        )
    }

    fun onAnimationEnd() {
        animationController?.finish(progress.value >= 0.5f)
    }
}
