/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Dialog
import android.graphics.Color
import android.graphics.Rect
import android.os.Looper
import android.util.Log
import android.util.MathUtils
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewRootImpl
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
import android.widget.FrameLayout
import com.android.internal.jank.InteractionJankMonitor
import com.android.internal.jank.InteractionJankMonitor.CujType
import com.android.systemui.util.registerAnimationOnBackInvoked
import java.lang.IllegalArgumentException
import kotlin.math.roundToInt

private const val TAG = "DialogLaunchAnimator"

/**
 * A class that allows dialogs to be started in a seamless way from a view that is transforming
 * nicely into the starting dialog.
 *
 * This animator also allows to easily animate a dialog into an activity.
 *
 * @see show
 * @see showFromView
 * @see showFromDialog
 * @see createActivityLaunchController
 */
class DialogLaunchAnimator
@JvmOverloads
constructor(
    private val callback: Callback,
    private val interactionJankMonitor: InteractionJankMonitor,
    private val featureFlags: AnimationFeatureFlags,
    private val launchAnimator: LaunchAnimator = LaunchAnimator(TIMINGS, INTERPOLATORS),
    private val isForTesting: Boolean = false,
) {
    private companion object {
        private val TIMINGS = ActivityLaunchAnimator.TIMINGS

        // We use the same interpolator for X and Y axis to make sure the dialog does not move out
        // of the screen bounds during the animation.
        private val INTERPOLATORS =
            ActivityLaunchAnimator.INTERPOLATORS.copy(
                positionXInterpolator = ActivityLaunchAnimator.INTERPOLATORS.positionInterpolator
            )
    }

    /**
     * A controller that takes care of applying the dialog launch and exit animations to the source
     * that triggered the animation.
     */
    interface Controller {
        /** The [ViewRootImpl] of this controller. */
        val viewRoot: ViewRootImpl?

        /**
         * The identity object of the source animated by this controller. This animator will ensure
         * that 2 animations with the same source identity are not going to run at the same time, to
         * avoid flickers when a dialog is shown from the same source more or less at the same time
         * (for instance if the user clicks an expandable button twice).
         */
        val sourceIdentity: Any

        /** The CUJ associated to this controller. */
        val cuj: DialogCuj?

        /**
         * Move the drawing of the source in the overlay of [viewGroup].
         *
         * Once this method is called, and until [stopDrawingInOverlay] is called, the source
         * controlled by this Controller should be drawn in the overlay of [viewGroup] so that it is
         * drawn above all other elements in the same [viewRoot].
         */
        fun startDrawingInOverlayOf(viewGroup: ViewGroup)

        /**
         * Move the drawing of the source back in its original location.
         *
         * @see startDrawingInOverlayOf
         */
        fun stopDrawingInOverlay()

        /**
         * Create the [LaunchAnimator.Controller] that will be called to animate the source
         * controlled by this [Controller] during the dialog launch animation.
         *
         * At the end of this animation, the source should *not* be visible anymore (until the
         * dialog is closed and is animated back into the source).
         */
        fun createLaunchController(): LaunchAnimator.Controller

        /**
         * Create the [LaunchAnimator.Controller] that will be called to animate the source
         * controlled by this [Controller] during the dialog exit animation.
         *
         * At the end of this animation, the source should be visible again.
         */
        fun createExitController(): LaunchAnimator.Controller

        /**
         * Whether we should animate the dialog back into the source when it is dismissed. If this
         * methods returns `false`, then the dialog will simply fade out and
         * [onExitAnimationCancelled] will be called.
         *
         * Note that even when this returns `true`, the exit animation might still be cancelled (in
         * which case [onExitAnimationCancelled] will also be called).
         */
        fun shouldAnimateExit(): Boolean

        /**
         * Called if we decided to *not* animate the dialog into the source for some reason. This
         * means that [createExitController] will *not* be called and this implementation should
         * make sure that the source is back in its original state, before it was animated into the
         * dialog. In particular, the source should be visible again.
         */
        fun onExitAnimationCancelled()

        /**
         * Return the [InteractionJankMonitor.Configuration.Builder] to be used for animations
         * controlled by this controller.
         */
        // TODO(b/252723237): Make this non-nullable
        fun jankConfigurationBuilder(): InteractionJankMonitor.Configuration.Builder?

        companion object {
            /**
             * Create a [Controller] that can animate [source] to and from a dialog.
             *
             * Important: The view must be attached to a [ViewGroup] when calling this function and
             * during the animation. For safety, this method will return null when it is not. The
             * view must also implement [LaunchableView], otherwise this method will throw.
             *
             * Note: The background of [view] should be a (rounded) rectangle so that it can be
             * properly animated.
             */
            fun fromView(source: View, cuj: DialogCuj? = null): Controller? {
                // Make sure the View we launch from implements LaunchableView to avoid visibility
                // issues.
                if (source !is LaunchableView) {
                    throw IllegalArgumentException(
                        "A DialogLaunchAnimator.Controller was created from a View that does not " +
                            "implement LaunchableView. This can lead to subtle bugs where the " +
                            "visibility of the View we are launching from is not what we expected."
                    )
                }

                if (source.parent !is ViewGroup) {
                    Log.e(
                        TAG,
                        "Skipping animation as view $source is not attached to a ViewGroup",
                        Exception(),
                    )
                    return null
                }

                return ViewDialogLaunchAnimatorController(source, cuj)
            }
        }
    }

    /**
     * The set of dialogs that were animated using this animator and that are still opened (not
     * dismissed, but can be hidden).
     */
    // TODO(b/201264644): Remove this set.
    private val openedDialogs = hashSetOf<AnimatedDialog>()

    /**
     * Show [dialog] by expanding it from [view]. If [view] is a view inside another dialog that was
     * shown using this method, then we will animate from that dialog instead.
     *
     * If [animateBackgroundBoundsChange] is true, then the background of the dialog will be
     * animated when the dialog bounds change.
     *
     * Note: The background of [view] should be a (rounded) rectangle so that it can be properly
     * animated.
     *
     * Caveats: When calling this function and [dialog] is not a fullscreen dialog, then it will be
     * made fullscreen and 2 views will be inserted between the dialog DecorView and its children.
     */
    @JvmOverloads
    fun showFromView(
        dialog: Dialog,
        view: View,
        cuj: DialogCuj? = null,
        animateBackgroundBoundsChange: Boolean = false
    ) {
        val controller = Controller.fromView(view, cuj)
        if (controller == null) {
            dialog.show()
        } else {
            show(dialog, controller, animateBackgroundBoundsChange)
        }
    }

    /**
     * Show [dialog] by expanding it from a source controlled by [controller].
     *
     * If [animateBackgroundBoundsChange] is true, then the background of the dialog will be
     * animated when the dialog bounds change.
     *
     * Note: The background of [view] should be a (rounded) rectangle so that it can be properly
     * animated.
     *
     * Caveats: When calling this function and [dialog] is not a fullscreen dialog, then it will be
     * made fullscreen and 2 views will be inserted between the dialog DecorView and its children.
     */
    @JvmOverloads
    fun show(
        dialog: Dialog,
        controller: Controller,
        animateBackgroundBoundsChange: Boolean = false
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw IllegalStateException(
                "showFromView must be called from the main thread and dialog must be created in " +
                    "the main thread"
            )
        }

        // If the view we are launching from belongs to another dialog, then this means the caller
        // intent is to launch a dialog from another dialog.
        val animatedParent =
            openedDialogs.firstOrNull {
                it.dialog.window.decorView.viewRootImpl == controller.viewRoot
            }
        val controller =
            animatedParent?.dialogContentWithBackground?.let {
                Controller.fromView(it, controller.cuj)
            }
                ?: controller

        // Make sure we don't run the launch animation from the same source twice at the same time.
        if (openedDialogs.any { it.controller.sourceIdentity == controller.sourceIdentity }) {
            Log.e(
                TAG,
                "Not running dialog launch animation from source as it is already expanded into a" +
                    " dialog"
            )
            dialog.show()
            return
        }

        val animatedDialog =
            AnimatedDialog(
                launchAnimator = launchAnimator,
                callback = callback,
                interactionJankMonitor = interactionJankMonitor,
                controller = controller,
                onDialogDismissed = { openedDialogs.remove(it) },
                dialog = dialog,
                animateBackgroundBoundsChange = animateBackgroundBoundsChange,
                parentAnimatedDialog = animatedParent,
                forceDisableSynchronization = isForTesting,
                featureFlags = featureFlags,
            )

        openedDialogs.add(animatedDialog)
        animatedDialog.start()
    }

    /**
     * Launch [dialog] from [another dialog][animateFrom] that was shown using [show]. This will
     * allow for dismissing the whole stack.
     *
     * @see dismissStack
     */
    fun showFromDialog(
        dialog: Dialog,
        animateFrom: Dialog,
        cuj: DialogCuj? = null,
        animateBackgroundBoundsChange: Boolean = false
    ) {
        val view =
            openedDialogs.firstOrNull { it.dialog == animateFrom }?.dialogContentWithBackground
        if (view == null) {
            Log.w(
                TAG,
                "Showing dialog $dialog normally as the dialog it is shown from was not shown " +
                    "using DialogLaunchAnimator"
            )
            dialog.show()
            return
        }

        showFromView(
            dialog,
            view,
            animateBackgroundBoundsChange = animateBackgroundBoundsChange,
            cuj = cuj
        )
    }

    /**
     * Create an [ActivityLaunchAnimator.Controller] that can be used to launch an activity from the
     * dialog that contains [View]. Note that the dialog must have been shown using this animator,
     * otherwise this method will return null.
     *
     * The returned controller will take care of dismissing the dialog at the right time after the
     * activity started, when the dialog to app animation is done (or when it is cancelled). If this
     * method returns null, then the dialog won't be dismissed.
     *
     * @param view any view inside the dialog to animate.
     */
    @JvmOverloads
    fun createActivityLaunchController(
        view: View,
        cujType: Int? = null,
    ): ActivityLaunchAnimator.Controller? {
        val animatedDialog =
            openedDialogs.firstOrNull {
                it.dialog.window.decorView.viewRootImpl == view.viewRootImpl
            }
                ?: return null
        return createActivityLaunchController(animatedDialog, cujType)
    }

    /**
     * Create an [ActivityLaunchAnimator.Controller] that can be used to launch an activity from
     * [dialog]. Note that the dialog must have been shown using this animator, otherwise this
     * method will return null.
     *
     * The returned controller will take care of dismissing the dialog at the right time after the
     * activity started, when the dialog to app animation is done (or when it is cancelled). If this
     * method returns null, then the dialog won't be dismissed.
     *
     * @param dialog the dialog to animate.
     */
    @JvmOverloads
    fun createActivityLaunchController(
        dialog: Dialog,
        cujType: Int? = null,
    ): ActivityLaunchAnimator.Controller? {
        val animatedDialog = openedDialogs.firstOrNull { it.dialog == dialog } ?: return null
        return createActivityLaunchController(animatedDialog, cujType)
    }

    private fun createActivityLaunchController(
        animatedDialog: AnimatedDialog,
        cujType: Int? = null
    ): ActivityLaunchAnimator.Controller? {
        // At this point, we know that the intent of the caller is to dismiss the dialog to show
        // an app, so we disable the exit animation into the source because we will never want to
        // run it anyways.
        animatedDialog.exitAnimationDisabled = true

        val dialog = animatedDialog.dialog

        // Don't animate if the dialog is not showing or if we are locked and going to show the
        // primary bouncer.
        if (
            !dialog.isShowing ||
                (!callback.isUnlocked() && !callback.isShowingAlternateAuthOnUnlock())
        ) {
            return null
        }

        val dialogContentWithBackground = animatedDialog.dialogContentWithBackground ?: return null
        val controller =
            ActivityLaunchAnimator.Controller.fromView(dialogContentWithBackground, cujType)
                ?: return null

        // Wrap the controller into one that will instantly dismiss the dialog when the animation is
        // done or dismiss it normally (fading it out) if the animation is cancelled.
        return object : ActivityLaunchAnimator.Controller by controller {
            override val isDialogLaunch = true

            override fun onIntentStarted(willAnimate: Boolean) {
                controller.onIntentStarted(willAnimate)

                if (!willAnimate) {
                    dialog.dismiss()
                }
            }

            override fun onLaunchAnimationCancelled(newKeyguardOccludedState: Boolean?) {
                controller.onLaunchAnimationCancelled()
                enableDialogDismiss()
                dialog.dismiss()
            }

            override fun onLaunchAnimationStart(isExpandingFullyAbove: Boolean) {
                controller.onLaunchAnimationStart(isExpandingFullyAbove)

                // Make sure the dialog is not dismissed during the animation.
                disableDialogDismiss()

                // If this dialog was shown from a cascade of other dialogs, make sure those ones
                // are dismissed too.
                animatedDialog.prepareForStackDismiss()

                // Remove the dim.
                dialog.window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }

            override fun onLaunchAnimationEnd(isExpandingFullyAbove: Boolean) {
                controller.onLaunchAnimationEnd(isExpandingFullyAbove)

                // Hide the dialog then dismiss it to instantly dismiss it without playing the
                // animation.
                dialog.hide()
                enableDialogDismiss()
                dialog.dismiss()
            }

            private fun disableDialogDismiss() {
                dialog.setDismissOverride { /* Do nothing */}
            }

            private fun enableDialogDismiss() {
                // We don't set the override to null given that [AnimatedDialog.OnDialogDismissed]
                // will still properly dismiss the dialog but will also make sure to clean up
                // everything (like making sure that the touched view that triggered the dialog is
                // made VISIBLE again).
                dialog.setDismissOverride(animatedDialog::onDialogDismissed)
            }
        }
    }

    /**
     * Ensure that all dialogs currently shown won't animate into their source when dismissed.
     *
     * This is a temporary API meant to be called right before we both dismiss a dialog and start an
     * activity, which currently does not look good if we animate the dialog into their source at
     * the same time as the activity starts.
     *
     * TODO(b/193634619): Remove this function and animate dialog into opening activity instead.
     */
    fun disableAllCurrentDialogsExitAnimations() {
        openedDialogs.forEach { it.exitAnimationDisabled = true }
    }

    /**
     * Dismiss [dialog]. If it was launched from another dialog using this animator, also dismiss
     * the stack of dialogs and simply fade out [dialog].
     */
    fun dismissStack(dialog: Dialog) {
        openedDialogs.firstOrNull { it.dialog == dialog }?.prepareForStackDismiss()
        dialog.dismiss()
    }

    interface Callback {
        /** Whether the device is currently in dreaming (screensaver) mode. */
        fun isDreaming(): Boolean

        /**
         * Whether the device is currently unlocked, i.e. if it is *not* on the keyguard or if the
         * keyguard can be dismissed.
         */
        fun isUnlocked(): Boolean

        /**
         * Whether we are going to show alternate authentication (like UDFPS) instead of the
         * traditional bouncer when unlocking the device.
         */
        fun isShowingAlternateAuthOnUnlock(): Boolean
    }
}

/**
 * The CUJ interaction associated with opening the dialog.
 *
 * The optional tag indicates the specific dialog being opened.
 */
data class DialogCuj(@CujType val cujType: Int, val tag: String? = null)

private class AnimatedDialog(
    private val launchAnimator: LaunchAnimator,
    private val callback: DialogLaunchAnimator.Callback,
    private val interactionJankMonitor: InteractionJankMonitor,

    /**
     * The controller of the source that triggered the dialog and that will animate into/from the
     * dialog.
     */
    val controller: DialogLaunchAnimator.Controller,

    /**
     * A callback that will be called with this [AnimatedDialog] after the dialog was dismissed and
     * the exit animation is done.
     */
    private val onDialogDismissed: (AnimatedDialog) -> Unit,

    /** The dialog to show and animate. */
    val dialog: Dialog,

    /** Whether we should animate the dialog background when its bounds change. */
    animateBackgroundBoundsChange: Boolean,

    /** Launch animation corresponding to the parent [AnimatedDialog]. */
    private val parentAnimatedDialog: AnimatedDialog? = null,

    /**
     * Whether synchronization should be disabled, which can be useful if we are running in a test.
     */
    private val forceDisableSynchronization: Boolean,
    private val featureFlags: AnimationFeatureFlags,
) {
    /**
     * The DecorView of this dialog window.
     *
     * Note that we access this DecorView lazily to avoid accessing it before the dialog is created,
     * which can sometimes cause crashes (e.g. with the Cast dialog).
     */
    private val decorView by lazy { dialog.window!!.decorView as ViewGroup }

    /**
     * The dialog content with its background. When animating a fullscreen dialog, this is just the
     * first ViewGroup of the dialog that has a background. When animating a normal (not fullscreen)
     * dialog, this is an additional view that serves as a fake window that will have the same size
     * as the dialog window initially had and to which we will set the dialog window background.
     */
    var dialogContentWithBackground: ViewGroup? = null

    /** The background color of [dialog], taking into consideration its window background color. */
    private var originalDialogBackgroundColor = Color.BLACK

    /**
     * Whether we are currently launching/showing the dialog by animating it from its source
     * controlled by [controller].
     */
    private var isLaunching = true

    /** Whether we are currently dismissing/hiding the dialog by animating into its source. */
    private var isDismissing = false

    private var dismissRequested = false
    var exitAnimationDisabled = false

    private var isSourceDrawnInDialog = false
    private var isOriginalDialogViewLaidOut = false

    /** A layout listener to animate the dialog height change. */
    private val backgroundLayoutListener =
        if (animateBackgroundBoundsChange) {
            AnimatedBoundsLayoutListener()
        } else {
            null
        }

    /*
     * A layout listener in case the dialog (window) size changes (for instance because of a
     * configuration change) to ensure that the dialog stays full width.
     */
    private var decorViewLayoutListener: View.OnLayoutChangeListener? = null

    private var hasInstrumentedJank = false

    fun start() {
        val cuj = controller.cuj
        if (cuj != null) {
            val config = controller.jankConfigurationBuilder()
            if (config != null) {
                if (cuj.tag != null) {
                    config.setTag(cuj.tag)
                }

                interactionJankMonitor.begin(config)
                hasInstrumentedJank = true
            }
        }

        // Create the dialog so that its onCreate() method is called, which usually sets the dialog
        // content.
        dialog.create()

        val window = dialog.window!!
        val isWindowFullScreen =
            window.attributes.width == MATCH_PARENT && window.attributes.height == MATCH_PARENT
        val dialogContentWithBackground =
            if (isWindowFullScreen) {
                // If the dialog window is already fullscreen, then we look for the first ViewGroup
                // that has a background (and is not the DecorView, which always has a background)
                // and animate towards that ViewGroup given that this is probably what represents
                // the actual dialog view.
                var viewGroupWithBackground: ViewGroup? = null
                for (i in 0 until decorView.childCount) {
                    viewGroupWithBackground =
                        findFirstViewGroupWithBackground(decorView.getChildAt(i))
                    if (viewGroupWithBackground != null) {
                        break
                    }
                }

                // Animate that view with the background. Throw if we didn't find one, because
                // otherwise it's not clear what we should animate.
                if (viewGroupWithBackground == null) {
                    error("Unable to find ViewGroup with background")
                }

                if (viewGroupWithBackground !is LaunchableView) {
                    error("The animated ViewGroup with background must implement LaunchableView")
                }

                viewGroupWithBackground
            } else {
                // We will make the dialog window (and therefore its DecorView) fullscreen to make
                // it possible to animate outside its bounds.
                //
                // Before that, we add a new View as a child of the DecorView with the same size and
                // gravity as that DecorView, then we add all original children of the DecorView to
                // that new View. Finally we remove the background of the DecorView and add it to
                // the new View, then we make the DecorView fullscreen. This new View now acts as a
                // fake (non fullscreen) window.
                //
                // On top of that, we also add a fullscreen transparent background between the
                // DecorView and the view that we added so that we can dismiss the dialog when this
                // view is clicked. This is necessary because DecorView overrides onTouchEvent and
                // therefore we can't set the click listener directly on the (now fullscreen)
                // DecorView.
                val fullscreenTransparentBackground = FrameLayout(dialog.context)
                decorView.addView(
                    fullscreenTransparentBackground,
                    0 /* index */,
                    FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                )

                val dialogContentWithBackground = LaunchableFrameLayout(dialog.context)
                dialogContentWithBackground.background = decorView.background

                // Make the window background transparent. Note that setting the window (or
                // DecorView) background drawable to null leads to issues with background color (not
                // being transparent) or with insets that are not refreshed. Therefore we need to
                // set it to something not null, hence we are using android.R.color.transparent
                // here.
                window.setBackgroundDrawableResource(android.R.color.transparent)

                // Close the dialog when clicking outside of it.
                fullscreenTransparentBackground.setOnClickListener { dialog.dismiss() }
                dialogContentWithBackground.isClickable = true

                // Make sure the transparent and dialog backgrounds are not focusable by
                // accessibility
                // features.
                fullscreenTransparentBackground.importantForAccessibility =
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO
                dialogContentWithBackground.importantForAccessibility =
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO

                fullscreenTransparentBackground.addView(
                    dialogContentWithBackground,
                    FrameLayout.LayoutParams(
                        window.attributes.width,
                        window.attributes.height,
                        window.attributes.gravity
                    )
                )

                // Move all original children of the DecorView to the new View we just added.
                for (i in 1 until decorView.childCount) {
                    val view = decorView.getChildAt(1)
                    decorView.removeViewAt(1)
                    dialogContentWithBackground.addView(view)
                }

                // Make the window fullscreen and add a layout listener to ensure it stays
                // fullscreen.
                window.setLayout(MATCH_PARENT, MATCH_PARENT)
                decorViewLayoutListener =
                    View.OnLayoutChangeListener {
                        v,
                        left,
                        top,
                        right,
                        bottom,
                        oldLeft,
                        oldTop,
                        oldRight,
                        oldBottom ->
                        if (
                            window.attributes.width != MATCH_PARENT ||
                                window.attributes.height != MATCH_PARENT
                        ) {
                            // The dialog size changed, copy its size to dialogContentWithBackground
                            // and make the dialog window full screen again.
                            val layoutParams = dialogContentWithBackground.layoutParams
                            layoutParams.width = window.attributes.width
                            layoutParams.height = window.attributes.height
                            dialogContentWithBackground.layoutParams = layoutParams
                            window.setLayout(MATCH_PARENT, MATCH_PARENT)
                        }
                    }
                decorView.addOnLayoutChangeListener(decorViewLayoutListener)

                dialogContentWithBackground
            }
        this.dialogContentWithBackground = dialogContentWithBackground
        dialogContentWithBackground.setTag(R.id.tag_dialog_background, true)

        val background = dialogContentWithBackground.background
        originalDialogBackgroundColor =
            GhostedViewLaunchAnimatorController.findGradientDrawable(background)
                ?.color
                ?.defaultColor
                ?: Color.BLACK

        // Make the background view invisible until we start the animation. We use the transition
        // visibility like GhostView does so that we don't mess up with the accessibility tree (see
        // b/204944038#comment17). Given that this background implements LaunchableView, we call
        // setShouldBlockVisibilityChanges() early so that the current visibility (VISIBLE) is
        // restored at the end of the animation.
        dialogContentWithBackground.setShouldBlockVisibilityChanges(true)
        dialogContentWithBackground.setTransitionVisibility(View.INVISIBLE)

        // Make sure the dialog is visible instantly and does not do any window animation.
        val attributes = window.attributes
        attributes.windowAnimations = R.style.Animation_LaunchAnimation

        // Ensure that the animation is not clipped by the display cut-out when animating this
        // dialog into an app.
        attributes.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS

        // Ensure that the animation is not clipped by the navigation/task bars when animating this
        // dialog into an app.
        val wasFittingNavigationBars =
            attributes.fitInsetsTypes and WindowInsets.Type.navigationBars() != 0
        attributes.fitInsetsTypes =
            attributes.fitInsetsTypes and WindowInsets.Type.navigationBars().inv()

        window.attributes = window.attributes

        // We apply the insets ourselves to make sure that the paddings are set on the correct
        // View.
        window.setDecorFitsSystemWindows(false)
        val viewWithInsets = (dialogContentWithBackground.parent as ViewGroup)
        viewWithInsets.setOnApplyWindowInsetsListener { view, windowInsets ->
            val type =
                if (wasFittingNavigationBars) {
                    WindowInsets.Type.displayCutout() or WindowInsets.Type.navigationBars()
                } else {
                    WindowInsets.Type.displayCutout()
                }

            val insets = windowInsets.getInsets(type)
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsets.CONSUMED
        }

        // Start the animation once the background view is properly laid out.
        dialogContentWithBackground.addOnLayoutChangeListener(
            object : View.OnLayoutChangeListener {
                override fun onLayoutChange(
                    v: View,
                    left: Int,
                    top: Int,
                    right: Int,
                    bottom: Int,
                    oldLeft: Int,
                    oldTop: Int,
                    oldRight: Int,
                    oldBottom: Int
                ) {
                    dialogContentWithBackground.removeOnLayoutChangeListener(this)

                    isOriginalDialogViewLaidOut = true
                    maybeStartLaunchAnimation()
                }
            }
        )

        // Disable the dim. We will enable it once we start the animation.
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        // Override the dialog dismiss() so that we can animate the exit before actually dismissing
        // the dialog.
        dialog.setDismissOverride(this::onDialogDismissed)

        if (featureFlags.isPredictiveBackQsDialogAnim) {
            // TODO(b/265923095) Improve animations for QS dialogs on configuration change
            dialog.registerAnimationOnBackInvoked(targetView = dialogContentWithBackground)
        }

        // Show the dialog.
        dialog.show()
        moveSourceDrawingToDialog()
    }

    private fun moveSourceDrawingToDialog() {
        if (decorView.viewRootImpl == null) {
            // Make sure that we have access to the dialog view root to move the drawing to the
            // dialog overlay.
            decorView.post(::moveSourceDrawingToDialog)
            return
        }

        // Move the drawing of the source in the overlay of this dialog, then animate. We trigger a
        // one-off synchronization to make sure that this is done in sync between the two different
        // windows.
        controller.startDrawingInOverlayOf(decorView)
        synchronizeNextDraw(
            then = {
                isSourceDrawnInDialog = true
                maybeStartLaunchAnimation()
            }
        )
    }

    /**
     * Synchronize the next draw of the source and dialog view roots so that they are performed at
     * the same time, in the same transaction. This is necessary to make sure that the source is
     * drawn in the overlay at the same time as it is removed from its original position (or
     * inversely, removed from the overlay when the source is moved back to its original position).
     */
    private fun synchronizeNextDraw(then: () -> Unit) {
        val controllerRootView = controller.viewRoot?.view
        if (forceDisableSynchronization || controllerRootView == null) {
            // Don't synchronize when inside an automated test or if the controller root view is
            // detached.
            then()
            return
        }

        ViewRootSync.synchronizeNextDraw(controllerRootView, decorView, then)
        decorView.invalidate()
        controllerRootView.invalidate()
    }

    private fun findFirstViewGroupWithBackground(view: View): ViewGroup? {
        if (view !is ViewGroup) {
            return null
        }

        if (view.background != null) {
            return view
        }

        for (i in 0 until view.childCount) {
            val match = findFirstViewGroupWithBackground(view.getChildAt(i))
            if (match != null) {
                return match
            }
        }

        return null
    }

    private fun maybeStartLaunchAnimation() {
        if (!isSourceDrawnInDialog || !isOriginalDialogViewLaidOut) {
            return
        }

        // Show the background dim.
        dialog.window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        startAnimation(
            isLaunching = true,
            onLaunchAnimationEnd = {
                isLaunching = false

                // dismiss was called during the animation, dismiss again now to actually dismiss.
                if (dismissRequested) {
                    dialog.dismiss()
                }

                // If necessary, we animate the dialog background when its bounds change. We do it
                // at the end of the launch animation, because the lauch animation already correctly
                // handles bounds changes.
                if (backgroundLayoutListener != null) {
                    dialogContentWithBackground!!.addOnLayoutChangeListener(
                        backgroundLayoutListener
                    )
                }

                if (hasInstrumentedJank) {
                    interactionJankMonitor.end(controller.cuj!!.cujType)
                }
            }
        )
    }

    fun onDialogDismissed() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            dialog.context.mainExecutor.execute { onDialogDismissed() }
            return
        }

        // TODO(b/193634619): Support interrupting the launch animation in the middle.
        if (isLaunching) {
            dismissRequested = true
            return
        }

        if (isDismissing) {
            return
        }

        isDismissing = true
        hideDialogIntoView { animationRan: Boolean ->
            if (animationRan) {
                // Instantly dismiss the dialog if we ran the animation into view. If it was
                // skipped, dismiss() will run the window animation (which fades out the dialog).
                dialog.hide()
            }

            dialog.setDismissOverride(null)
            dialog.dismiss()
        }
    }

    /**
     * Hide the dialog into the source and call [onAnimationFinished] when the animation is done
     * (passing animationRan=true) or if it's skipped (passing animationRan=false) to actually
     * dismiss the dialog.
     */
    private fun hideDialogIntoView(onAnimationFinished: (Boolean) -> Unit) {
        // Remove the layout change listener we have added to the DecorView earlier.
        if (decorViewLayoutListener != null) {
            decorView.removeOnLayoutChangeListener(decorViewLayoutListener)
        }

        if (!shouldAnimateDialogIntoSource()) {
            Log.i(TAG, "Skipping animation of dialog into the source")
            controller.onExitAnimationCancelled()
            onAnimationFinished(false /* instantDismiss */)
            onDialogDismissed(this@AnimatedDialog)
            return
        }

        startAnimation(
            isLaunching = false,
            onLaunchAnimationStart = {
                // Remove the dim background as soon as we start the animation.
                dialog.window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            },
            onLaunchAnimationEnd = {
                val dialogContentWithBackground = this.dialogContentWithBackground!!
                dialogContentWithBackground.visibility = View.INVISIBLE

                if (backgroundLayoutListener != null) {
                    dialogContentWithBackground.removeOnLayoutChangeListener(
                        backgroundLayoutListener
                    )
                }

                controller.stopDrawingInOverlay()
                synchronizeNextDraw {
                    onAnimationFinished(true /* instantDismiss */)
                    onDialogDismissed(this@AnimatedDialog)
                }
            }
        )
    }

    private fun startAnimation(
        isLaunching: Boolean,
        onLaunchAnimationStart: () -> Unit = {},
        onLaunchAnimationEnd: () -> Unit = {}
    ) {
        // Create 2 controllers to animate both the dialog and the source.
        val startController =
            if (isLaunching) {
                controller.createLaunchController()
            } else {
                GhostedViewLaunchAnimatorController(dialogContentWithBackground!!)
            }
        val endController =
            if (isLaunching) {
                GhostedViewLaunchAnimatorController(dialogContentWithBackground!!)
            } else {
                controller.createExitController()
            }
        startController.launchContainer = decorView
        endController.launchContainer = decorView

        val endState = endController.createAnimatorState()
        val controller =
            object : LaunchAnimator.Controller {
                override var launchContainer: ViewGroup
                    get() = startController.launchContainer
                    set(value) {
                        startController.launchContainer = value
                        endController.launchContainer = value
                    }

                override fun createAnimatorState(): LaunchAnimator.State {
                    return startController.createAnimatorState()
                }

                override fun onLaunchAnimationStart(isExpandingFullyAbove: Boolean) {
                    // During launch, onLaunchAnimationStart will be used to remove the temporary
                    // touch surface ghost so it is important to call this before calling
                    // onLaunchAnimationStart on the controller (which will create its own ghost).
                    onLaunchAnimationStart()

                    startController.onLaunchAnimationStart(isExpandingFullyAbove)
                    endController.onLaunchAnimationStart(isExpandingFullyAbove)
                }

                override fun onLaunchAnimationEnd(isExpandingFullyAbove: Boolean) {
                    // onLaunchAnimationEnd is called by an Animator at the end of the animation,
                    // on a Choreographer animation tick. The following calls will move the animated
                    // content from the dialog overlay back to its original position, and this
                    // change must be reflected in the next frame given that we then sync the next
                    // frame of both the content and dialog ViewRoots. However, in case that content
                    // is rendered by Compose, whose compositions are also scheduled on a
                    // Choreographer frame, any state change made *right now* won't be reflected in
                    // the next frame given that a Choreographer frame can't schedule another and
                    // have it happen in the same frame. So we post the forwarded calls to
                    // [Controller.onLaunchAnimationEnd], leaving this Choreographer frame, ensuring
                    // that the move of the content back to its original window will be reflected in
                    // the next frame right after [onLaunchAnimationEnd] is called.
                    dialog.context.mainExecutor.execute {
                        startController.onLaunchAnimationEnd(isExpandingFullyAbove)
                        endController.onLaunchAnimationEnd(isExpandingFullyAbove)

                        onLaunchAnimationEnd()
                    }
                }

                override fun onLaunchAnimationProgress(
                    state: LaunchAnimator.State,
                    progress: Float,
                    linearProgress: Float
                ) {
                    startController.onLaunchAnimationProgress(state, progress, linearProgress)

                    // The end view is visible only iff the starting view is not visible.
                    state.visible = !state.visible
                    endController.onLaunchAnimationProgress(state, progress, linearProgress)

                    // If the dialog content is complex, its dimension might change during the
                    // launch animation. The animation end position might also change during the
                    // exit animation, for instance when locking the phone when the dialog is open.
                    // Therefore we update the end state to the new position/size. Usually the
                    // dialog dimension or position will change in the early frames, so changing the
                    // end state shouldn't really be noticeable.
                    if (endController is GhostedViewLaunchAnimatorController) {
                        endController.fillGhostedViewState(endState)
                    }
                }
            }

        launchAnimator.startAnimation(controller, endState, originalDialogBackgroundColor)
    }

    private fun shouldAnimateDialogIntoSource(): Boolean {
        // Don't animate if the dialog was previously hidden using hide() or if we disabled the exit
        // animation.
        if (exitAnimationDisabled || !dialog.isShowing) {
            return false
        }

        // If we are dreaming, the dialog was probably closed because of that so we don't animate
        // into the source.
        if (callback.isDreaming()) {
            return false
        }

        return controller.shouldAnimateExit()
    }

    /** A layout listener to animate the change of bounds of the dialog background. */
    class AnimatedBoundsLayoutListener : View.OnLayoutChangeListener {
        companion object {
            private const val ANIMATION_DURATION = 500L
        }

        private var lastBounds: Rect? = null
        private var currentAnimator: ValueAnimator? = null

        override fun onLayoutChange(
            view: View,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
            oldLeft: Int,
            oldTop: Int,
            oldRight: Int,
            oldBottom: Int
        ) {
            // Don't animate if bounds didn't actually change.
            if (left == oldLeft && top == oldTop && right == oldRight && bottom == oldBottom) {
                // Make sure that we that the last bounds set by the animator were not overridden.
                lastBounds?.let { bounds ->
                    view.left = bounds.left
                    view.top = bounds.top
                    view.right = bounds.right
                    view.bottom = bounds.bottom
                }
                return
            }

            if (lastBounds == null) {
                lastBounds = Rect(oldLeft, oldTop, oldRight, oldBottom)
            }

            val bounds = lastBounds!!
            val startLeft = bounds.left
            val startTop = bounds.top
            val startRight = bounds.right
            val startBottom = bounds.bottom

            currentAnimator?.cancel()
            currentAnimator = null

            val animator =
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = ANIMATION_DURATION
                    interpolator = Interpolators.STANDARD

                    addListener(
                        object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                currentAnimator = null
                            }
                        }
                    )

                    addUpdateListener { animatedValue ->
                        val progress = animatedValue.animatedFraction

                        // Compute new bounds.
                        bounds.left = MathUtils.lerp(startLeft, left, progress).roundToInt()
                        bounds.top = MathUtils.lerp(startTop, top, progress).roundToInt()
                        bounds.right = MathUtils.lerp(startRight, right, progress).roundToInt()
                        bounds.bottom = MathUtils.lerp(startBottom, bottom, progress).roundToInt()

                        // Set the new bounds.
                        view.left = bounds.left
                        view.top = bounds.top
                        view.right = bounds.right
                        view.bottom = bounds.bottom
                    }
                }

            currentAnimator = animator
            animator.start()
        }
    }

    fun prepareForStackDismiss() {
        if (parentAnimatedDialog == null) {
            return
        }
        parentAnimatedDialog.exitAnimationDisabled = true
        parentAnimatedDialog.dialog.hide()
        parentAnimatedDialog.prepareForStackDismiss()
        parentAnimatedDialog.dialog.dismiss()
    }
}
