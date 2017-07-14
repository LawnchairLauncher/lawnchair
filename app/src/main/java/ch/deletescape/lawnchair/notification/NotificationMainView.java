package ch.deletescape.lawnchair.notification;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import ch.deletescape.lawnchair.ItemInfo;
import ch.deletescape.lawnchair.Launcher;
import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.util.Themes;

public class NotificationMainView extends FrameLayout implements SwipeHelper.Callback {
    private int mBackgroundColor;
    private NotificationInfo mNotificationInfo;
    private ViewGroup mTextAndBackground;
    private TextView mTextView;
    private TextView mTitleView;

    public NotificationMainView(Context context) {
        this(context, null, 0);
    }

    public NotificationMainView(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public NotificationMainView(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        this.mTextAndBackground = findViewById(R.id.text_and_background);
        ColorDrawable colorDrawable = (ColorDrawable) this.mTextAndBackground.getBackground();
        this.mBackgroundColor = getResources().getColor(R.color.popup_background_color);
        this.mTextAndBackground.setBackground(new RippleDrawable(ColorStateList.valueOf(Themes.getAttrColor(getContext(), 16843820)), colorDrawable, null));
        this.mTitleView = this.mTextAndBackground.findViewById(R.id.title);
        this.mTextView = this.mTextAndBackground.findViewById(R.id.text);
    }

    public void applyNotificationInfo(NotificationInfo notificationInfo, View view) {
        applyNotificationInfo(notificationInfo, view, false);
    }

    public void applyNotificationInfo(NotificationInfo notificationInfo, View view, boolean z) {
        this.mNotificationInfo = notificationInfo;
        CharSequence charSequence = this.mNotificationInfo.title;
        CharSequence charSequence2 = this.mNotificationInfo.text;
        if (TextUtils.isEmpty(charSequence) || TextUtils.isEmpty(charSequence2)) {
            this.mTitleView.setMaxLines(2);
            TextView textView = this.mTitleView;
            if (!TextUtils.isEmpty(charSequence)) {
                charSequence2 = charSequence;
            }
            textView.setText(charSequence2);
            this.mTextView.setVisibility(GONE);
        } else {
            this.mTitleView.setText(charSequence);
            this.mTextView.setText(charSequence2);
        }
        view.setBackground(this.mNotificationInfo.getIconForBackground(getContext(), this.mBackgroundColor));
        if (this.mNotificationInfo.intent != null) {
            setOnClickListener(this.mNotificationInfo);
        }
        setTranslationX(0.0f);
        setTag(new ItemInfo());
        if (z) {
            ObjectAnimator.ofFloat(this.mTextAndBackground, ALPHA, new float[]{0.0f, 1.0f}).setDuration(150).start();
        }
    }

    public NotificationInfo getNotificationInfo() {
        return this.mNotificationInfo;
    }

    @Override
    public View getChildAtPosition(MotionEvent motionEvent) {
        return this;
    }

    @Override
    public boolean canChildBeDismissed(View view) {
        return this.mNotificationInfo != null && this.mNotificationInfo.dismissable;
    }

    @Override
    public boolean isAntiFalsingNeeded() {
        return false;
    }

    @Override
    public void onBeginDrag(View view) {
    }

    @Override
    public void onChildDismissed(View view) {
        Launcher launcher = Launcher.getLauncher(getContext());
        launcher.getPopupDataProvider().cancelNotification(this.mNotificationInfo.notificationKey);
    }

    @Override
    public void onDragCancelled(View view) {
    }

    @Override
    public void onChildSnappedBack(View view, float f) {
    }

    @Override
    public boolean updateSwipeProgress(View view, boolean z, float f) {
        return true;
    }

    @Override
    public float getFalsingThresholdFactor() {
        return 1.0f;
    }
}