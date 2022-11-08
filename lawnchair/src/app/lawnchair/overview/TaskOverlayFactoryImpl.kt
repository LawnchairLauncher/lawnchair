package app.lawnchair.overview

import android.content.Context
import android.graphics.Matrix
import androidx.annotation.Keep
import com.android.quickstep.TaskOverlayFactory
import com.android.quickstep.views.OverviewActionsView
import com.android.quickstep.views.TaskThumbnailView
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.ThumbnailData

@Keep
class TaskOverlayFactoryImpl(@Suppress("UNUSED_PARAMETER") context: Context) : TaskOverlayFactory() {

    override fun createOverlay(thumbnailView: TaskThumbnailView) = TaskOverlay(thumbnailView)

    class TaskOverlay(
        taskThumbnailView: TaskThumbnailView
    ) : TaskOverlayFactory.TaskOverlay<LawnchairOverviewActionsView>(taskThumbnailView) {

        override fun initOverlay(
            task: Task?,
            thumbnail: ThumbnailData?,
            matrix: Matrix,
            rotated: Boolean
        ) {
            actionsView.updateDisabledFlags(
                OverviewActionsView.DISABLED_NO_THUMBNAIL,
                thumbnail == null
            )

            if (thumbnail != null) {
                actionsView.updateDisabledFlags(OverviewActionsView.DISABLED_ROTATED, rotated)
                val isAllowedByPolicy = mThumbnailView.isRealSnapshot
                actionsView.setCallbacks(OverlayUICallbacksImpl(isAllowedByPolicy, task))
            }
        }

        private inner class OverlayUICallbacksImpl(
            isAllowedByPolicy: Boolean,
            task: Task?
        ) : TaskOverlayFactory.TaskOverlay<LawnchairOverviewActionsView>.OverlayUICallbacksImpl(
            isAllowedByPolicy,
            task
        ), OverlayUICallbacks {

            override fun onShare() {
                if (mIsAllowedByPolicy) {
                    endLiveTileMode { mImageApi.startShareActivity(null) }
                } else {
                    showBlockedByPolicyMessage()
                }
            }

            override fun onLens() {
                if (mIsAllowedByPolicy) {
                    endLiveTileMode { mImageApi.startLensActivity() }
                } else {
                    showBlockedByPolicyMessage()
                }
            }
        }
    }

    sealed interface OverlayUICallbacks : TaskOverlayFactory.OverlayUICallbacks {
        fun onShare()
        fun onLens()
    }
}
