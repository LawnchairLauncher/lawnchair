/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.quickstep;

import static android.view.View.ALPHA;

import static com.android.quickstep.TaskAdapter.CHANGE_EVENT_TYPE_EMPTY_TO_CONTENT;
import static com.android.quickstep.views.TaskItemView.CONTENT_TRANSITION_PROGRESS;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.android.quickstep.views.TaskItemView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * An item animator that is only set and used for the transition from the empty loading UI to
 * the filled task content UI. The animation starts from the bottom to top, changing all valid
 * empty item views to be filled and removing all extra empty views.
 */
public final class ContentFillItemAnimator extends SimpleItemAnimator {

    private static final class PendingAnimation {
        ViewHolder viewHolder;
        int animType;

        PendingAnimation(ViewHolder vh, int type) {
            viewHolder = vh;
            animType = type;
        }
    }

    private static final int ANIM_TYPE_REMOVE = 0;
    private static final int ANIM_TYPE_CHANGE = 1;

    private static final int ITEM_BETWEEN_DELAY = 40;
    private static final int ITEM_CHANGE_DURATION = 150;
    private static final int ITEM_REMOVE_DURATION = 150;

    /**
     * Animations that have been registered to occur together at the next call of
     * {@link #runPendingAnimations()} but have not started.
     */
    private final ArrayList<PendingAnimation> mPendingAnims = new ArrayList<>();

    /**
     * Animations that have started and are running.
     */
    private final ArrayList<ObjectAnimator> mRunningAnims = new ArrayList<>();

    private Runnable mOnFinishRunnable;

    /**
     * Set runnable to run after the content fill animation is fully completed.
     *
     * @param runnable runnable to run on end
     */
    public void setOnAnimationFinishedRunnable(Runnable runnable) {
        mOnFinishRunnable = runnable;
    }

    @Override
    public void setChangeDuration(long changeDuration) {
        throw new UnsupportedOperationException("Cascading item animator cannot have animation "
                + "duration changed.");
    }

    @Override
    public void setRemoveDuration(long removeDuration) {
        throw new UnsupportedOperationException("Cascading item animator cannot have animation "
                + "duration changed.");
    }

    @Override
    public boolean animateRemove(ViewHolder holder) {
        PendingAnimation pendAnim = new PendingAnimation(holder, ANIM_TYPE_REMOVE);
        mPendingAnims.add(pendAnim);
        return true;
    }

    private void animateRemoveImpl(ViewHolder holder, long startDelay) {
        final View view = holder.itemView;
        if (holder.itemView.getAlpha() == 0) {
            // View is already visually removed. We can just get rid of it now.
            view.setAlpha(1.0f);
            dispatchRemoveFinished(holder);
            dispatchFinishedWhenDone();
            return;
        }
        final ObjectAnimator anim = ObjectAnimator.ofFloat(
                holder.itemView, ALPHA, holder.itemView.getAlpha(), 0.0f);
        anim.setDuration(ITEM_REMOVE_DURATION).setStartDelay(startDelay);
        anim.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        dispatchRemoveStarting(holder);
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.setAlpha(1);
                        dispatchRemoveFinished(holder);
                        mRunningAnims.remove(anim);
                        dispatchFinishedWhenDone();
                    }
                }
        );
        anim.start();
        mRunningAnims.add(anim);
    }

    @Override
    public boolean animateAdd(ViewHolder holder) {
        dispatchAddFinished(holder);
        return false;
    }

    @Override
    public boolean animateMove(ViewHolder holder, int fromX, int fromY, int toX,
            int toY) {
        dispatchMoveFinished(holder);
        return false;
    }

    @Override
    public boolean animateChange(ViewHolder oldHolder,
            ViewHolder newHolder, int fromLeft, int fromTop, int toLeft, int toTop) {
        // Only support changes where the holders are the same
        if (oldHolder == newHolder) {
            PendingAnimation pendAnim = new PendingAnimation(oldHolder, ANIM_TYPE_CHANGE);
            mPendingAnims.add(pendAnim);
            return true;
        }
        dispatchChangeFinished(oldHolder, true /* oldItem */);
        dispatchChangeFinished(newHolder, false /* oldItem */);
        return false;
    }

    private void animateChangeImpl(ViewHolder viewHolder, long startDelay) {
        TaskItemView itemView = (TaskItemView) viewHolder.itemView;
        if (itemView.getAlpha() == 0) {
            // View is still not visible, so we can finish the change immediately.
            CONTENT_TRANSITION_PROGRESS.set(itemView, 1.0f);
            dispatchChangeFinished(viewHolder, true /* oldItem */);
            dispatchFinishedWhenDone();
            return;
        }
        final ObjectAnimator anim =
                ObjectAnimator.ofFloat(itemView, CONTENT_TRANSITION_PROGRESS, 0.0f, 1.0f);
        anim.setDuration(ITEM_CHANGE_DURATION).setStartDelay(startDelay);
        anim.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        dispatchChangeStarting(viewHolder, true /* oldItem */);
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        CONTENT_TRANSITION_PROGRESS.set(itemView, 1.0f);
                        dispatchChangeFinished(viewHolder, true /* oldItem */);
                        mRunningAnims.remove(anim);
                        dispatchFinishedWhenDone();
                    }
                }
        );
        anim.start();
        mRunningAnims.add(anim);
    }

    @Override
    public void runPendingAnimations() {
        // Run animations bottom to top.
        mPendingAnims.sort(Comparator.comparingInt(o -> -o.viewHolder.itemView.getBottom()));
        int delay = 0;
        while (!mPendingAnims.isEmpty()) {
            PendingAnimation curAnim = mPendingAnims.remove(0);
            ViewHolder vh = curAnim.viewHolder;
            switch (curAnim.animType) {
                case ANIM_TYPE_REMOVE:
                    animateRemoveImpl(vh, delay);
                    break;
                case ANIM_TYPE_CHANGE:
                    animateChangeImpl(vh, delay);
                    break;
                default:
                    break;
            }
            delay += ITEM_BETWEEN_DELAY;
        }
    }

    @Override
    public void endAnimation(@NonNull ViewHolder item) {
        for (int i = mPendingAnims.size() - 1; i >= 0; i--) {
            endPendingAnimation(mPendingAnims.get(i));
            mPendingAnims.remove(i);
        }
        dispatchFinishedWhenDone();
    }

    @Override
    public void endAnimations() {
        if (!isRunning()) {
            return;
        }
        for (int i = mPendingAnims.size() - 1; i >= 0; i--) {
            endPendingAnimation(mPendingAnims.get(i));
            mPendingAnims.remove(i);
        }
        for (int i = mRunningAnims.size() - 1; i >= 0; i--) {
            ObjectAnimator anim = mRunningAnims.get(i);
            // This calls the on end animation callback which will set values to their end target.
            anim.cancel();
        }
        dispatchFinishedWhenDone();
    }

    private void endPendingAnimation(PendingAnimation pendAnim) {
        ViewHolder item = pendAnim.viewHolder;
        switch (pendAnim.animType) {
            case ANIM_TYPE_REMOVE:
                item.itemView.setAlpha(1.0f);
                dispatchRemoveFinished(item);
                break;
            case ANIM_TYPE_CHANGE:
                CONTENT_TRANSITION_PROGRESS.set(item.itemView, 1.0f);
                dispatchChangeFinished(item, true /* oldItem */);
                break;
            default:
                break;
        }
    }

    @Override
    public boolean isRunning() {
        return !mPendingAnims.isEmpty() || !mRunningAnims.isEmpty();
    }

    @Override
    public boolean canReuseUpdatedViewHolder(@NonNull ViewHolder viewHolder,
            @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()
                && (int) payloads.get(0) == CHANGE_EVENT_TYPE_EMPTY_TO_CONTENT) {
            return true;
        }
        return super.canReuseUpdatedViewHolder(viewHolder, payloads);
    }

    private void dispatchFinishedWhenDone() {
        if (!isRunning()) {
            dispatchAnimationsFinished();
            if (mOnFinishRunnable != null) {
                mOnFinishRunnable.run();
            }
        }
    }
}
