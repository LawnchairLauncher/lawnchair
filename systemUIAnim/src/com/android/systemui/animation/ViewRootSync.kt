package com.android.systemui.animation

import android.view.View
import android.window.SurfaceSyncer

/** A util class to synchronize 2 view roots. */
// TODO(b/200284684): Remove this class.
object ViewRootSync {
    private var surfaceSyncer: SurfaceSyncer? = null

    /**
     * Synchronize the next draw between the view roots of [view] and [otherView], then run [then].
     *
     * Note that in some cases, the synchronization might not be possible (e.g. WM consumed the next
     * transactions) or disabled (temporarily, on low ram devices). In this case, [then] will be
     * called without synchronizing.
     */
    fun synchronizeNextDraw(view: View, otherView: View, then: () -> Unit) {
        if (
            !view.isAttachedToWindow ||
                view.viewRootImpl == null ||
                !otherView.isAttachedToWindow ||
                otherView.viewRootImpl == null ||
                view.viewRootImpl == otherView.viewRootImpl
        ) {
            // No need to synchronize if either the touch surface or dialog view is not attached
            // to a window.
            then()
            return
        }

        surfaceSyncer =
            SurfaceSyncer().apply {
                val syncId = setupSync(Runnable { then() })
                addToSync(syncId, view)
                addToSync(syncId, otherView)
                markSyncReady(syncId)
            }
    }

    /** A Java-friendly API for [synchronizeNextDraw]. */
    @JvmStatic
    fun synchronizeNextDraw(view: View, otherView: View, then: Runnable) {
        synchronizeNextDraw(view, otherView, then::run)
    }
}
