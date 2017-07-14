package ch.deletescape.lawnchair.notification;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.view.View.OnClickListener;

import ch.deletescape.lawnchair.Launcher;
import ch.deletescape.lawnchair.LauncherAppState;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.graphics.IconPalette;
import ch.deletescape.lawnchair.popup.PopupContainerWithArrow;
import ch.deletescape.lawnchair.util.PackageUserKey;

public class NotificationInfo implements OnClickListener {
    public final boolean autoCancel;
    public final boolean dismissable;
    public final PendingIntent intent;
    private int mBadgeIcon;
    private int mIconColor;
    private Drawable mIconDrawable;
    private boolean mIsIconLarge;
    public final String notificationKey;
    public final PackageUserKey packageUserKey;
    public final CharSequence text;
    public final CharSequence title;

    public NotificationInfo(Context context, StatusBarNotification statusBarNotification) {
        boolean z;
        Icon icon = null;
        boolean z2 = true;
        packageUserKey = PackageUserKey.fromNotification(statusBarNotification);
        notificationKey = statusBarNotification.getKey();
        Notification notification = statusBarNotification.getNotification();
        title = notification.extras.getCharSequence("android.title");
        text = notification.extras.getCharSequence("android.text");
        mBadgeIcon = Utilities.isAtLeastO() ? notification.getBadgeIconType() : 1; // We need some kind of compat for this
        if (mBadgeIcon != 1 && Utilities.ATLEAST_MARSHMALLOW) {
            icon = notification.getLargeIcon();
        }
        if (icon == null) {
            try {
                Resources res = context.getPackageManager().getResourcesForApplication(statusBarNotification.getPackageName());
                if (Utilities.ATLEAST_MARSHMALLOW) {
                    mIconDrawable = notification.getSmallIcon().loadDrawable(context);
                } else {
                    mIconDrawable = res.getDrawable(notification.icon);
                }
                mIconColor = statusBarNotification.getNotification().color;
                mIsIconLarge = false;
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        } else if (Utilities.ATLEAST_MARSHMALLOW) {
            mIconDrawable = icon.loadDrawable(context);
            mIsIconLarge = true;
        }
        if (mIconDrawable == null) {
            mIconDrawable = new BitmapDrawable(context.getResources(), LauncherAppState.getInstance().getIconCache().getDefaultIcon(statusBarNotification.getUser()));
            mBadgeIcon = 0;
        }
        intent = notification.contentIntent;
        z = (notification.flags & 16) != 0;
        autoCancel = z;
        if ((notification.flags & 2) != 0) {
            z2 = false;
        }
        dismissable = z2;
    }

    @Override
    public void onClick(View view) {
        Launcher launcher = Launcher.getLauncher(view.getContext());
        try {
            intent.send(null, 0, null, null, null, null);//, ActivityOptions.makeClipRevealAnimation(view, 0, 0, view.getWidth(), view.getHeight()).toBundle());
        } catch (CanceledException e) {
            e.printStackTrace();
        }
        if (autoCancel) {
            launcher.getPopupDataProvider().cancelNotification(notificationKey);
        }
        PopupContainerWithArrow.getOpen(launcher).close(true);
    }

    public Drawable getIconForBackground(Context context, int i) {
        if (mIsIconLarge) {
            return mIconDrawable;
        }
        mIconColor = IconPalette.resolveContrastColor(context, mIconColor, i);
        Drawable mutate = mIconDrawable.mutate();
        mutate.setTintList(null);
        mutate.setTint(mIconColor);
        return mutate;
    }

    public boolean isIconLarge() {
        return mIsIconLarge;
    }

    public boolean shouldShowIconInBadge() {
        if (mIsIconLarge && mBadgeIcon == 2) {
            return true;
        }
        return !(mIsIconLarge || mBadgeIcon != 1);
    }
}