package ch.deletescape.lawnchair.notification;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import ch.deletescape.lawnchair.Launcher;
import ch.deletescape.lawnchair.LauncherAnimUtils;
import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.anim.PropertyListBuilder;
import ch.deletescape.lawnchair.anim.PropertyResetListener;
import ch.deletescape.lawnchair.popup.PopupContainerWithArrow;

public class NotificationFooterLayout extends FrameLayout {
    private static final Rect sTempRect = new Rect();
    private int mBackgroundColor;
    LayoutParams mIconLayoutParams;
    private LinearLayout mIconRow;
    private final List<NotificationInfo> mNotifications;
    private View mOverflowEllipsis;
    private final List<NotificationInfo> mOverflowNotifications;
    private final boolean mRtl;

    final class C04612 extends AnimatorListenerAdapter {
        C04612() {
        }

        @Override
        public void onAnimationEnd(Animator animator) {
            ((ViewGroup) NotificationFooterLayout.this.getParent()).removeView(NotificationFooterLayout.this);
        }
    }

    public interface IconAnimationEndListener {
        void onIconAnimationEnd(NotificationInfo notificationInfo);
    }

    public NotificationFooterLayout(Context context) {
        this(context, null, 0);
    }

    public NotificationFooterLayout(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public NotificationFooterLayout(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        mNotifications = new ArrayList<>();
        mOverflowNotifications = new ArrayList<>();
        Resources resources = getResources();
        mRtl = Utilities.isRtl(resources);
        int dimensionPixelSize = resources.getDimensionPixelSize(R.dimen.notification_footer_icon_size);
        mIconLayoutParams = new LayoutParams(dimensionPixelSize, dimensionPixelSize);
        mIconLayoutParams.gravity = 16;
        int dimensionPixelSize2 = resources.getDimensionPixelSize(R.dimen.horizontal_ellipsis_offset) + resources.getDimensionPixelSize(R.dimen.horizontal_ellipsis_size);
        mIconLayoutParams.setMarginStart((((resources.getDimensionPixelSize(R.dimen.bg_popup_item_width) - resources.getDimensionPixelSize(R.dimen.notification_footer_icon_row_padding)) - dimensionPixelSize2) - (dimensionPixelSize * 5)) / 5);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mOverflowEllipsis = findViewById(R.id.overflow);
        mIconRow = findViewById(R.id.icon_row);
        mBackgroundColor = getResources().getColor(R.color.popup_background_color);
    }

    public void addNotificationInfo(NotificationInfo notificationInfo) {
        if (mNotifications.size() < 5) {
            mNotifications.add(notificationInfo);
        } else {
            mOverflowNotifications.add(notificationInfo);
        }
    }

    public void commitNotificationInfos() {
        mIconRow.removeAllViews();
        for (int i = 0; i < mNotifications.size(); i++) {
            addNotificationIconForInfo(mNotifications.get(i));
        }
        updateOverflowEllipsisVisibility();
    }

    private void updateOverflowEllipsisVisibility() {
        mOverflowEllipsis.setVisibility(mOverflowNotifications.isEmpty() ? GONE : VISIBLE);
    }

    private View addNotificationIconForInfo(NotificationInfo notificationInfo) {
        View view = new View(getContext());
        view.setBackground(notificationInfo.getIconForBackground(getContext(), mBackgroundColor));
        view.setOnClickListener(notificationInfo);
        view.setTag(notificationInfo);
        view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        mIconRow.addView(view, 0, mIconLayoutParams);
        return view;
    }

    public void animateFirstNotificationTo(Rect rect, final IconAnimationEndListener iconAnimationEndListener) {
        int i;
        AnimatorSet createAnimatorSet = LauncherAnimUtils.createAnimatorSet();
        final View childAt = mIconRow.getChildAt(mIconRow.getChildCount() - 1);
        Rect rect2 = sTempRect;
        childAt.getGlobalVisibleRect(rect2);
        float height = ((float) rect.height()) / ((float) rect2.height());
        Animator ofPropertyValuesHolder = LauncherAnimUtils.ofPropertyValuesHolder(childAt, new PropertyListBuilder().scale(height).translationY((((height * ((float) rect2.height())) - ((float) rect2.height())) / 2.0f) + ((float) (rect.top - rect2.top))).build());
        ofPropertyValuesHolder.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                iconAnimationEndListener.onIconAnimationEnd((NotificationInfo) childAt.getTag());
                NotificationFooterLayout.this.removeViewFromIconRow(childAt);
            }
        });
        createAnimatorSet.play(ofPropertyValuesHolder);
        int marginStart = mIconLayoutParams.width + mIconLayoutParams.getMarginStart();
        if (mRtl) {
            i = -marginStart;
        } else {
            i = marginStart;
        }
        if (!mOverflowNotifications.isEmpty()) {
            NotificationInfo notificationInfo = mOverflowNotifications.remove(0);
            mNotifications.add(notificationInfo);
            createAnimatorSet.play(ObjectAnimator.ofFloat(addNotificationIconForInfo(notificationInfo), ALPHA, 0.0f, 1.0f));
        }
        int childCount = mIconRow.getChildCount() - 1;
        AnimatorListener propertyResetListener = new PropertyResetListener(TRANSLATION_X, 0.0f);
        for (marginStart = 0; marginStart < childCount; marginStart++) {
            Animator ofFloat = ObjectAnimator.ofFloat(mIconRow.getChildAt(marginStart), TRANSLATION_X, (float) i);
            ofFloat.addListener(propertyResetListener);
            createAnimatorSet.play(ofFloat);
        }
        createAnimatorSet.start();
    }

    private void removeViewFromIconRow(View view) {
        mIconRow.removeView(view);
        mNotifications.remove(view.getTag());
        updateOverflowEllipsisVisibility();
        if (mIconRow.getChildCount() == 0) {
            PopupContainerWithArrow open = PopupContainerWithArrow.getOpen(Launcher.getLauncher(getContext()));
            if (open != null) {
                Animator reduceNotificationViewHeight = open.reduceNotificationViewHeight(getHeight(), getResources().getInteger(R.integer.config_removeNotificationViewDuration));
                reduceNotificationViewHeight.addListener(new C04612());
                reduceNotificationViewHeight.start();
            }
        }
    }

    public void trimNotifications(List list) {
        if (isAttachedToWindow() && mIconRow.getChildCount() != 0) {
            Iterator<NotificationInfo> it = mOverflowNotifications.iterator();
            while (it.hasNext()) {
                if (!list.contains((it.next()).notificationKey)) {
                    it.remove();
                }
            }
            for (int childCount = mIconRow.getChildCount() - 1; childCount >= 0; childCount--) {
                View childAt = mIconRow.getChildAt(childCount);
                if (!list.contains(((NotificationInfo) childAt.getTag()).notificationKey)) {
                    removeViewFromIconRow(childAt);
                }
            }
        }
    }
}