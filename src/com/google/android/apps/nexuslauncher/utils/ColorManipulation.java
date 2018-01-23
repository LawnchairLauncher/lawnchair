package com.google.android.apps.nexuslauncher.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;

public class ColorManipulation {
    private int[] mPixels;
    private Bitmap mBitmap;
    private Canvas mCanvas;
    private Paint mPaint;
    private final Matrix mMatrix;

    public ColorManipulation() {
        mMatrix = new Matrix();
    }

    public static boolean dC(int RGBA) {
        int maxDiff = 20;
        if ((RGBA >> 24 & 0xFF) < 50) {
            return true;
        }
        int red = RGBA >> 16 & 0xFF;
        int green = RGBA >> 8 & 0xFF;
        int blue = RGBA & 0xFF;

        boolean returnValue = true;
        if (Math.abs(red - green) < maxDiff && Math.abs(red - blue) < maxDiff) {
            if (Math.abs(green - blue) >= maxDiff) {
                returnValue = false;
            }
        } else {
            returnValue = false;
        }
        return returnValue;
    }

    public boolean dB(Bitmap ew) {
        int height = ew.getHeight();
        int width = ew.getWidth();
        Bitmap bitmap;
        if (height > 64 || width > 64) {
            if (mBitmap == null) {
                mBitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
                mCanvas = new Canvas(mBitmap);
                mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                mPaint.setFilterBitmap(true);
            }
            mMatrix.reset();
            mMatrix.setScale(64f / width, 64f / height, 0f, 0f);
            mCanvas.drawColor(0, PorterDuff.Mode.SRC);
            mCanvas.drawBitmap(ew, mMatrix, mPaint);
            ew = mBitmap;
            width = 64;
            height = 64;
            bitmap = ew;
        } else {
            bitmap = ew;
        }
        int pixelCount = height * width;
        resizeIfNecessary(pixelCount);
        bitmap.getPixels(mPixels, 0, width, 0, 0, width, height);
        for (int i = 0; i < pixelCount; ++i) {
            if (!dC(mPixels[i])) {
                return false;
            }
        }
        return true;
    }

    private void resizeIfNecessary(int pixelCount) {
        if (mPixels == null || mPixels.length < pixelCount) {
            mPixels = new int[pixelCount];
        }
    }
}
