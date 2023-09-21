package com.android.internal.util;

import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class ScreenshotRequest implements Parcelable {

    protected ScreenshotRequest(Parcel in) {
    }

    public static final Creator<ScreenshotRequest> CREATOR = new Creator<ScreenshotRequest> ( ) {
        @Override
        public ScreenshotRequest createFromParcel(Parcel in) {
            return new ScreenshotRequest (in);
        }

        @Override
        public ScreenshotRequest[] newArray(int size) {
            return new ScreenshotRequest[size];
        }
    };

    public ScreenshotRequest(int mType , int mSource , ComponentName mTopComponent , int mTaskId , int mUserId , Bitmap mBitmap , Rect mBoundsInScreen , Insets mInsets) {
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel parcel , int i) {
    }

    public static class Builder {

        private final int mType;


        private final int mSource;

        private Bitmap mBitmap;
        private Rect mBoundsInScreen;
        private Insets mInsets = Insets.NONE;
        private int mTaskId = 0;
        private int mUserId = 0;
        private ComponentName mTopComponent;

        /**
         * Begin building a ScreenshotRequest.
         *
         * @param type   The type of the screenshot request, defined by {@link
         *               WindowManager.ScreenshotType}
         * @param source The source of the screenshot request, defined by {@link
         *               WindowManager.ScreenshotSource}
         */
        public Builder(
                 int type,
                int source) {

            mType = type;
            mSource = source;
        }

        /**
         * Construct a new {@link ScreenshotRequest} with the set parameters.
         */
        public ScreenshotRequest build() {



            return new ScreenshotRequest(mType, mSource, mTopComponent, mTaskId, mUserId, mBitmap,
                    mBoundsInScreen, mInsets);
        }

        /**
         * Set the top component associated with this request.
         *
         * @param topComponent The component name of the top component running in the task.
         */
        public Builder setTopComponent(ComponentName topComponent) {
            mTopComponent = topComponent;
            return this;
        }

        /**
         * Set the task id associated with this request.
         *
         * @param taskId The taskId of the task that the screenshot was taken of.
         */
        public Builder setTaskId(int taskId) {
            mTaskId = taskId;
            return this;
        }

        /**
         * Set the user id associated with this request.
         *
         * @param userId The userId of user running the task provided in taskId.
         */
        public Builder setUserId(int userId) {
            mUserId = userId;
            return this;
        }

        /**
         * Set the bitmap associated with this request.
         *
         * @param bitmap The provided screenshot.
         */
        public Builder setBitmap(Bitmap bitmap) {
            mBitmap = bitmap;
            return this;
        }

        /**
         * Set the bounds for the provided bitmap.
         *
         * @param bounds The bounds in screen coordinates that the bitmap originated from.
         */
        public Builder setBoundsOnScreen(Rect bounds) {
            mBoundsInScreen = bounds;
            return this;
        }

        /**
         * Set the insets for the provided bitmap.
         *
         * @param insets The insets that the image was shown with, inside the screen bounds.
         */
        public Builder setInsets(@NonNull Insets insets) {
            mInsets = insets;
            return this;
        }
    }

}