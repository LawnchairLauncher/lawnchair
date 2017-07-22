package ch.deletescape.lawnchair.badge;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Shader;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.List;

import ch.deletescape.lawnchair.notification.NotificationInfo;
import ch.deletescape.lawnchair.notification.NotificationKeyData;
import ch.deletescape.lawnchair.util.PackageUserKey;

public class BadgeInfo {
    private Shader mNotificationIcon;
    private NotificationInfo mNotificationInfo;
    private List<NotificationKeyData> mNotificationKeys = new ArrayList<>();
    private PackageUserKey mPackageUserKey;
    private int mTotalCount;

    public BadgeInfo(PackageUserKey packageUserKey) {
        mPackageUserKey = packageUserKey;
    }

    public boolean addOrUpdateNotificationKey(NotificationKeyData notificationKeyData) {
        NotificationKeyData notificationKeyData2 = null;
        int indexOf = mNotificationKeys.indexOf(notificationKeyData);
        if (indexOf != -1) {
            notificationKeyData2 = mNotificationKeys.get(indexOf);
        }
        if (notificationKeyData2 == null) {
            boolean add = mNotificationKeys.add(notificationKeyData);
            if (add) {
                mTotalCount += notificationKeyData.count;
            }
            return add;
        } else if (notificationKeyData2.count == notificationKeyData.count) {
            return false;
        } else {
            mTotalCount -= notificationKeyData2.count;
            mTotalCount += notificationKeyData.count;
            notificationKeyData2.count = notificationKeyData.count;
            return true;
        }
    }

    public boolean removeNotificationKey(NotificationKeyData notificationKeyData) {
        boolean remove = mNotificationKeys.remove(notificationKeyData);
        if (remove) {
            mTotalCount -= notificationKeyData.count;
        }
        return remove;
    }

    public List<NotificationKeyData> getNotificationKeys() {
        return mNotificationKeys;
    }

    public int getNotificationCount() {
        return Math.min(mTotalCount, 999);
    }

    public void setNotificationToShow(NotificationInfo notificationInfo) {
        mNotificationInfo = notificationInfo;
        mNotificationIcon = null;
    }

    public boolean hasNotificationToShow() {
        return mNotificationInfo != null;
    }

    public Shader getNotificationIconForBadge(Context context, int i, int i2, int i3) {
        if (mNotificationInfo == null) {
            return null;
        }
        if (mNotificationIcon == null) {
            Drawable newDrawable = mNotificationInfo.getIconForBackground(context, i).getConstantState().newDrawable();
            int i4 = i2 - (i3 * 2);
            newDrawable.setBounds(0, 0, i4, i4);
            Bitmap createBitmap = Bitmap.createBitmap(i2, i2, Config.ARGB_8888);
            Canvas canvas = new Canvas(createBitmap);
            canvas.translate((float) i3, (float) i3);
            newDrawable.draw(canvas);
            mNotificationIcon = new BitmapShader(createBitmap, TileMode.CLAMP, TileMode.CLAMP);
        }
        return mNotificationIcon;
    }

    public boolean isIconLarge() {
        return mNotificationInfo != null && mNotificationInfo.isIconLarge();
    }

    public boolean shouldBeInvalidated(BadgeInfo badgeInfo) {
        if (!mPackageUserKey.equals(badgeInfo.mPackageUserKey)) {
            return false;
        }
        if (getNotificationCount() == badgeInfo.getNotificationCount()) {
            return hasNotificationToShow();
        }
        return true;
    }
}