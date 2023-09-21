/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.animation

import android.view.GhostView
import android.view.View
import android.view.ViewGroup
import android.view.ViewRootImpl
import com.android.internal.jank.InteractionJankMonitor

/** A [DialogLaunchAnimator.Controller] that can animate a [View] from/to a dialog. */
class ViewDialogLaunchAnimatorController
internal constructor(
    private val source: View,
    override val cuj: DialogCuj?,
) : DialogLaunchAnimator.Controller {
    override val viewRoot: ViewRootImpl?
        get() = source.viewRootImpl

    override val sourceIdentity: Any = source

    override fun startDrawingInOverlayOf(viewGroup: ViewGroup) {
        // Delay the calls to `source.setVisibility()` during the animation. This must be called
        // before `GhostView.addGhost()` is called because the latter will change the *transition*
        // visibility, which won't be blocked and will affect the normal View visibility that is
        // saved by `setShouldBlockVisibilityChanges()` for a later restoration.
        (source as? LaunchableView)?.setShouldBlockVisibilityChanges(true)

        // Create a temporary ghost of the source (which will make it invisible) and add it
        // to the host dialog.
        GhostView.addGhost(source, viewGroup)
    }

    override fun stopDrawingInOverlay() {
        // Note: here we should remove the ghost from the overlay, but in practice this is
        // already done by the launch controller created below.

        if (source is LaunchableView) {
            // Make sure we allow the source to change its visibility again and restore its previous
            // value.
            source.setShouldBlockVisibilityChanges(false)
        } else {
            // We made the source invisible earlier, so let's make it visible again.
            source.visibility = View.VISIBLE
        }
    }

    override fun createLaunchController(): LaunchAnimator.Controller {
        val delegate = GhostedViewLaunchAnimatorController(source)
        return object : LaunchAnimator.Controller by delegate {
            override fun onLaunchAnimationStart(isExpandingFullyAbove: Boolean) {
                // Remove the temporary ghost added by [startDrawingInOverlayOf]. Another
                // ghost (that ghosts only the source content, and not its background) will
                // be added right after this by the delegate and will be animated.
                GhostView.removeGhost(source)
                delegate.onLaunchAnimationStart(isExpandingFullyAbove)
            }

            override fun onLaunchAnimationEnd(isExpandingFullyAbove: Boolean) {
                delegate.onLaunchAnimationEnd(isExpandingFullyAbove)

                // At this point the view visibility is restored by the delegate, so we delay the
                // visibility changes again and make it invisible while the dialog is shown.
                if (source is LaunchableView) {
                    source.setShouldBlockVisibilityChanges(true)
                    source.setTransitionVisibility(View.INVISIBLE)
                } else {
                    source.visibility = View.INVISIBLE
                }
            }
        }
    }

    override fun createExitController(): LaunchAnimator.Controller {
        return GhostedViewLaunchAnimatorController(source)
    }

    override fun shouldAnimateExit(): Boolean {
        // The source should be invisible by now, if it's not then something else changed
        // its visibility and we probably don't want to run the animation.
        if (source.visibility != View.INVISIBLE) {
            return false
        }

        return source.isAttachedToWindow && ((source.parent as? View)?.isShown ?: true)
    }

    override fun onExitAnimationCancelled() {
        if (source is LaunchableView) {
            // Make sure we allow the source to change its visibility again.
            source.setShouldBlockVisibilityChanges(false)
        } else {
            // If the view is invisible it's probably because of us, so we make it visible
            // again.
            if (source.visibility == View.INVISIBLE) {
                source.visibility = View.VISIBLE
            }
        }
    }

    override fun jankConfigurationBuilder(): InteractionJankMonitor.Configuration.Builder? {
        val type = cuj?.cujType ?: return null
        return InteractionJankMonitor.Configuration.Builder.withView(type, source)
    }
}
