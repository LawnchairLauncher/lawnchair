package ch.deletescape.wallpaperpicker.common;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * An abstraction over input stream creation. Also contains some utility methods
 * for various bitmap operations.
 */
public abstract class InputStreamProvider {

    private static final String TAG = "InputStreamProvider";

    /**
     * Tries to create a new stream or returns null on failure.
     */
    public InputStream newStream() {
        try {
            return newStreamNotNull();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Tries to create a new stream or throws an exception on failure.
     */
    public abstract InputStream newStreamNotNull() throws IOException;

    /**
     * Returns the size of the image, if the stream represents an image.
     */
    public Point getImageBounds() {
        InputStream is = newStream();
        if (is != null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, options);
            Utils.closeSilently(is);
            if (options.outWidth != 0 && options.outHeight != 0) {
                return new Point(options.outWidth, options.outHeight);
            }
        }
        return null;
    }

    public Bitmap readCroppedBitmap(RectF cropBounds, int outWidth, int outHeight, int rotation) {
        // Find crop bounds (scaled to original image size)
        Rect roundedTrueCrop = new Rect();
        Matrix rotateMatrix = new Matrix();
        Point bounds = getImageBounds();
        if (bounds == null) {
            Log.w(TAG, "cannot get bounds for image");
            return null;
        }

        if (rotation > 0) {
            rotateMatrix.setRotate(rotation);

            Matrix inverseRotateMatrix = new Matrix();
            inverseRotateMatrix.setRotate(-rotation);

            cropBounds.roundOut(roundedTrueCrop);
            cropBounds.set(roundedTrueCrop);

            float[] rotatedBounds = new float[]{bounds.x, bounds.y};
            rotateMatrix.mapPoints(rotatedBounds);
            rotatedBounds[0] = Math.abs(rotatedBounds[0]);
            rotatedBounds[1] = Math.abs(rotatedBounds[1]);

            cropBounds.offset(-rotatedBounds[0] / 2, -rotatedBounds[1] / 2);
            inverseRotateMatrix.mapRect(cropBounds);
            cropBounds.offset(bounds.x / 2, bounds.y / 2);
        }

        cropBounds.roundOut(roundedTrueCrop);
        if (roundedTrueCrop.width() <= 0 || roundedTrueCrop.height() <= 0) {
            Log.w(TAG, "crop has bad values for full size image");
            return null;
        }

        // See how much we're reducing the size of the image
        int scaleDownSampleSize = Math.max(1, Math.min(roundedTrueCrop.width() / outWidth,
                roundedTrueCrop.height() / outHeight));
        // Attempt to open a region decoder
        InputStream is = null;
        BitmapRegionDecoder decoder = null;
        try {
            is = newStreamNotNull();
            decoder = BitmapRegionDecoder.newInstance(is, false);
        } catch (IOException e) {
            Log.w(TAG, "cannot open region decoder", e);
        } finally {
            Utils.closeSilently(is);
            is = null;
        }

        Bitmap crop = null;
        if (decoder != null) {
            // Do region decoding to get crop bitmap
            BitmapFactory.Options options = new BitmapFactory.Options();
            if (scaleDownSampleSize > 1) {
                options.inSampleSize = scaleDownSampleSize;
            }
            crop = decoder.decodeRegion(roundedTrueCrop, options);
            decoder.recycle();
        }

        if (crop == null) {
            // BitmapRegionDecoder has failed, try to crop in-memory
            is = newStream();
            Bitmap fullSize = null;
            if (is != null) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                if (scaleDownSampleSize > 1) {
                    options.inSampleSize = scaleDownSampleSize;
                }
                fullSize = BitmapFactory.decodeStream(is, null, options);
                Utils.closeSilently(is);
            }
            if (fullSize != null) {
                // Find out the true sample size that was used by the decoder
                scaleDownSampleSize = bounds.x / fullSize.getWidth();
                cropBounds.left /= scaleDownSampleSize;
                cropBounds.top /= scaleDownSampleSize;
                cropBounds.bottom /= scaleDownSampleSize;
                cropBounds.right /= scaleDownSampleSize;
                cropBounds.roundOut(roundedTrueCrop);

                // Adjust values to account for issues related to rounding
                if (roundedTrueCrop.width() > fullSize.getWidth()) {
                    // Adjust the width
                    roundedTrueCrop.right = roundedTrueCrop.left + fullSize.getWidth();
                }
                if (roundedTrueCrop.right > fullSize.getWidth()) {
                    // Adjust the left and right values.
                    roundedTrueCrop.offset(-(roundedTrueCrop.right - fullSize.getWidth()), 0);
                }
                if (roundedTrueCrop.height() > fullSize.getHeight()) {
                    // Adjust the height
                    roundedTrueCrop.bottom = roundedTrueCrop.top + fullSize.getHeight();
                }
                if (roundedTrueCrop.bottom > fullSize.getHeight()) {
                    // Adjust the top and bottom values.
                    roundedTrueCrop.offset(0, -(roundedTrueCrop.bottom - fullSize.getHeight()));
                }

                crop = Bitmap.createBitmap(fullSize, roundedTrueCrop.left,
                        roundedTrueCrop.top, roundedTrueCrop.width(),
                        roundedTrueCrop.height());
            }
        }

        if (crop == null) {
            return null;
        }
        if (outWidth > 0 && outHeight > 0 || rotation > 0) {
            float[] dimsAfter = new float[]{crop.getWidth(), crop.getHeight()};
            rotateMatrix.mapPoints(dimsAfter);
            dimsAfter[0] = Math.abs(dimsAfter[0]);
            dimsAfter[1] = Math.abs(dimsAfter[1]);

            if (!(outWidth > 0 && outHeight > 0)) {
                outWidth = Math.round(dimsAfter[0]);
                outHeight = Math.round(dimsAfter[1]);
            }

            RectF cropRect = new RectF(0, 0, dimsAfter[0], dimsAfter[1]);
            RectF returnRect = new RectF(0, 0, outWidth, outHeight);

            Matrix m = new Matrix();
            if (rotation == 0) {
                m.setRectToRect(cropRect, returnRect, Matrix.ScaleToFit.FILL);
            } else {
                Matrix m1 = new Matrix();
                m1.setTranslate(-crop.getWidth() / 2f, -crop.getHeight() / 2f);
                Matrix m2 = new Matrix();
                m2.setRotate(rotation);
                Matrix m3 = new Matrix();
                m3.setTranslate(dimsAfter[0] / 2f, dimsAfter[1] / 2f);
                Matrix m4 = new Matrix();
                m4.setRectToRect(cropRect, returnRect, Matrix.ScaleToFit.FILL);

                Matrix c1 = new Matrix();
                c1.setConcat(m2, m1);
                Matrix c2 = new Matrix();
                c2.setConcat(m4, m3);
                m.setConcat(c2, c1);
            }

            Bitmap tmp = Bitmap.createBitmap((int) returnRect.width(),
                    (int) returnRect.height(), Bitmap.Config.ARGB_8888);
            if (tmp != null) {
                Canvas c = new Canvas(tmp);
                Paint p = new Paint();
                p.setFilterBitmap(true);
                c.drawBitmap(crop, m, p);
                crop = tmp;
            }
        }
        return crop;
    }

    public int getRotationFromExif(Context context) {
        InputStream is = null;
        try {
            is = newStreamNotNull();
            return ExifOrientation.readRotation(new BufferedInputStream(is), context);
        } catch (IOException | NullPointerException e) {
            Log.w(TAG, "Getting exif data failed", e);
        } finally {
            Utils.closeSilently(is);
        }
        return 0;
    }

    public static InputStreamProvider fromUri(final Context context, final Uri uri) {
        return new InputStreamProvider() {
            @Override
            public InputStream newStreamNotNull() throws IOException {
                return new BufferedInputStream(context.getContentResolver().openInputStream(uri));
            }
        };
    }

    public static InputStreamProvider fromBytes(final byte[] bytes) {
        return new InputStreamProvider() {
            @Override
            public InputStream newStreamNotNull() {
                return new BufferedInputStream(new ByteArrayInputStream(bytes));
            }
        };
    }

}
