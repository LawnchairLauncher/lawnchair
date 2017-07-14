package ch.deletescape.wallpaperpicker;

import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

/**
 * Callback that toggles the visibility of the target view when crop view is tapped.
 */
public class ToggleOnTapCallback implements CropView.TouchCallback {

    private final View mViewtoToggle;

    private ViewPropertyAnimator mAnim;
    private boolean mIgnoreNextTap;

    public ToggleOnTapCallback(View viewtoHide) {
        mViewtoToggle = viewtoHide;
    }

    @Override
    public void onTouchDown() {
        if (mAnim != null) {
            mAnim.cancel();
        }
        if (mViewtoToggle.getAlpha() == 1f) {
            mIgnoreNextTap = true;
        }

        mAnim = mViewtoToggle.animate();
        mAnim.alpha(0f)
                .setDuration(150)
                .withEndAction(new Runnable() {
                    public void run() {
                        mViewtoToggle.setVisibility(View.INVISIBLE);
                    }
                });

        mAnim.setInterpolator(new AccelerateInterpolator(0.75f));
        mAnim.start();
    }

    @Override
    public void onTouchUp() {
        mIgnoreNextTap = false;
    }

    @Override
    public void onTap() {
        boolean ignoreTap = mIgnoreNextTap;
        mIgnoreNextTap = false;
        if (!ignoreTap) {
            if (mAnim != null) {
                mAnim.cancel();
            }
            mViewtoToggle.setVisibility(View.VISIBLE);
            mAnim = mViewtoToggle.animate();
            mAnim.alpha(1f)
                    .setDuration(150)
                    .setInterpolator(new DecelerateInterpolator(0.75f));
            mAnim.start();
        }
    }
}
