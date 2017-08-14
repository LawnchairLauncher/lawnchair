package ch.deletescape.lawnchair.notification;

import android.animation.Animator;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import java.util.List;

import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.anim.PillHeightRevealOutlineProvider;
import ch.deletescape.lawnchair.graphics.IconPalette;
import ch.deletescape.lawnchair.popup.PopupItemView;

public class NotificationItemView extends PopupItemView {
    private static final Rect sTempRect = new Rect();
    private boolean mAnimatingNextIcon;
    private NotificationFooterLayout mFooter;
    private TextView mHeaderCount;
    private View mDivider;
    private NotificationMainView mMainView;
    private int mNotificationHeaderTextColor;
    private SwipeHelper mSwipeHelper;

    final class C04621 implements NotificationFooterLayout.IconAnimationEndListener {
        C04621() {
        }

        @Override
        public void onIconAnimationEnd(NotificationInfo notificationInfo) {
            if (notificationInfo != null) {
                NotificationItemView.this.mMainView.applyNotificationInfo(notificationInfo, NotificationItemView.this.mIconView, true);
                NotificationItemView.this.mMainView.setVisibility(VISIBLE);
            }
            NotificationItemView.this.mAnimatingNextIcon = false;
        }
    }

    public NotificationItemView(Context context) {
        this(context, null, 0);
    }

    public NotificationItemView(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public NotificationItemView(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        mNotificationHeaderTextColor = 0;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mHeaderCount = findViewById(R.id.notification_count);
        mMainView = findViewById(R.id.main_view);
        mDivider = findViewById(R.id.divider);
        mFooter = findViewById(R.id.footer);
        mSwipeHelper = new SwipeHelper(0, mMainView, getContext());
        mSwipeHelper.setDisableHardwareLayers(true);
    }

    public NotificationMainView getMainView() {
        return mMainView;
    }

    public int getHeightMinusFooter() {
        return getHeight() - (mFooter.getParent() == null ? 0 : mFooter.getHeight());
    }

    public Animator animateHeightRemoval(int i) {
        return new PillHeightRevealOutlineProvider(mPillRect, getBackgroundRadius(), getHeight() - i).createRevealAnimator(this, true);
    }

    public void updateHeader(int i, IconPalette iconPalette) {
        mHeaderCount.setText(i <= 1 ? "" : String.valueOf(i));
        if (iconPalette != null) {
            if (mNotificationHeaderTextColor == 0) {
                mNotificationHeaderTextColor = IconPalette.resolveContrastColor(getContext(),
                        iconPalette.dominantColor, getResources().getColor(R.color.popup_header_background_color));
            }
            mHeaderCount.setTextColor(mNotificationHeaderTextColor);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent motionEvent) {
        if (mMainView.getNotificationInfo() == null) {
            return false;
        }
        getParent().requestDisallowInterceptTouchEvent(true);
        return mSwipeHelper.onInterceptTouchEvent(motionEvent);
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {
        if (mMainView.getNotificationInfo() == null) {
            return false;
        }
        return mSwipeHelper.onTouchEvent(motionEvent) || super.onTouchEvent(motionEvent);
    }

    public void applyNotificationInfos(List list) {
        if (!list.isEmpty()) {
            mMainView.applyNotificationInfo((NotificationInfo) list.get(0), mIconView);
            mDivider.setVisibility(list.size() > 1 ? VISIBLE : INVISIBLE);
            for (int i = 1; i < list.size(); i++) {
                mFooter.addNotificationInfo((NotificationInfo) list.get(i));
            }
            mFooter.commitNotificationInfos();
        }
    }

    public void trimNotifications(List list) {
        if (list.contains(mMainView.getNotificationInfo().notificationKey) || mAnimatingNextIcon) {
            mFooter.trimNotifications(list);
            return;
        }
        mAnimatingNextIcon = true;
        mMainView.setVisibility(INVISIBLE);
        mMainView.setTranslationX(0.0f);
        mIconView.getGlobalVisibleRect(sTempRect);
        mFooter.animateFirstNotificationTo(sTempRect, new C04621());
    }

    @Override
    public int getArrowColor(boolean z) {
        Context context = getContext();
        if (z) {
            return Utilities.resolveAttributeData(context, R.attr.appPopupBgColor);
        } else {
            return Utilities.resolveAttributeData(context, R.attr.appPopupHeaderBgColor);
        }
    }
}