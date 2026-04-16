package app.lawnchair.folder

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Rect
import android.view.View
import android.view.animation.AnimationUtils
import app.lawnchair.LawnchairLauncher
import com.android.launcher3.R
import com.android.launcher3.folder.Folder
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.views.ActivityContext

object FolderCloseAnimHelper {

    @JvmStatic
    fun animateClose(folder: Folder, folderIcon: FolderIcon?): Boolean {
        if (folderIcon == null || !folderIcon.hasOverrideIcon()) return false

        val dragLayer = ActivityContext.lookupContext<LawnchairLauncher>(folder.context).dragLayer

        val iconPos = Rect()
        dragLayer.getDescendantRectRelativeToSelf(folderIcon, iconPos)

        val folderPos = Rect()
        dragLayer.getDescendantRectRelativeToSelf(folder, folderPos)

        val targetTx = iconPos.exactCenterX() - folderPos.exactCenterX()
        val targetTy = iconPos.exactCenterY() - folderPos.exactCenterY()

        folder.pivotX = folder.width / 2f
        folder.pivotY = folder.height / 2f

        folderIcon.setIconVisible(true)
        folderIcon.alpha = 0f

        val a = AnimatorSet()
        a.playTogether(
            ObjectAnimator.ofFloat(folder, View.ALPHA, 1f, 0f),
            ObjectAnimator.ofFloat(folder, View.SCALE_X, 1f, 0.5f),
            ObjectAnimator.ofFloat(folder, View.SCALE_Y, 1f, 0.5f),
            ObjectAnimator.ofFloat(folder, View.TRANSLATION_X, 0f, targetTx),
            ObjectAnimator.ofFloat(folder, View.TRANSLATION_Y, 0f, targetTy),
        )
        a.duration = 250
        a.interpolator = AnimationUtils.loadInterpolator(
            folder.context,
            R.interpolator.standard_interpolator,
        )
        a.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                folder.lcSetAnimatingClosed(true)
            }
            override fun onAnimationEnd(animation: Animator) {
                folder.alpha = 1f
                folder.scaleX = 1f
                folder.scaleY = 1f
                folder.translationX = 0f
                folder.translationY = 0f
                folderIcon.alpha = 1f
                folder.lcCloseComplete(true)
                folder.lcSetAnimatingClosed(false)
            }
        })
        folder.lcAddAnimationStartListeners(a)
        a.start()

        folderIcon.animate().alpha(1f).setStartDelay(50).setDuration(150).start()
        return true
    }
}
