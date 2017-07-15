/*
 * Copyright (C) 2015 The Android Open Source Project
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
package ch.deletescape.wallpaperpicker.common;

import android.content.Context;
import android.media.ExifInterface;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ExifOrientation {
    private static final String TAG = "ExifOrientation";
    private static final boolean DEBUG = false;

    private static final short SOI = (short) 0xFFD8;   // start of input
    private static final short APP0 = (short) 0xFFE0;
    private static final short APPF = (short) 0xFFEF;
    private static final short APP1 = (short) 0xFFE1;
    private static final short SOS = (short) 0xFFDA;    // start of stream
    private static final short EOI = (short) 0xFFD9;    // end of input

    // The header is available in first 64 bytes, so reading upto 128 bytes
    // should be more than enough.
    private static final int MAX_BYTES_TO_READ = 128 * 1024;

    /**
     * Parses the rotation of the JPEG image from the input stream.
     */
    public static int readRotation(InputStream in, Context context) {
        // Since the platform implementation only takes file input, create a temporary file
        // with just the image header.
        File tempFile = null;
        DataOutputStream tempOut = null;

        try {
            DataInputStream din = new DataInputStream(in);
            int pos = 0;
            if (din.readShort() == SOI) {
                pos += 2;

                short marker = din.readShort();
                pos += 2;

                while ((marker >= APP0 && marker <= APPF) && pos < MAX_BYTES_TO_READ) {
                    int length = din.readUnsignedShort();
                    if (length < 2) {
                        throw new IOException("Invalid header size");
                    }

                    // We only want APP1 headers
                    if (length > 2) {
                        if (marker == APP1) {
                            // Copy the header
                            if (tempFile == null) {
                                tempFile = File.createTempFile(TAG, ".jpg", context.getCacheDir());
                                tempOut = new DataOutputStream(new FileOutputStream(tempFile));
                                tempOut.writeShort(SOI);
                            }

                            tempOut.writeShort(marker);
                            tempOut.writeShort(length);

                            byte[] header = new byte[length - 2];
                            din.read(header);
                            tempOut.write(header);
                        } else {
                            din.skip(length - 2);
                        }
                    }
                    pos += length;

                    marker = din.readShort();
                    pos += 2;
                }

                if (tempOut != null) {
                    // Write empty image data.
                    tempOut.writeShort(SOS);
                    // Write the frame size as 2. Since this includes the size bytes as well
                    // (short = 2 bytes), it implies there is 0 byte of image data.
                    tempOut.writeShort(2);

                    // End of input
                    tempOut.writeShort(EOI);
                    tempOut.close();

                    return readRotation(tempFile.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            if (DEBUG) {
                Log.d(TAG, "Error parsing input stream", e);
            }
        } finally {
            Utils.closeSilently(in);
            Utils.closeSilently(tempOut);
            if (tempFile != null) {
                tempFile.delete();
            }
        }
        return 0;
    }

    /**
     * Parses the rotation of the JPEG image.
     */
    public static int readRotation(String filePath) {
        try {
            ExifInterface exif = new ExifInterface(filePath);
            switch (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0)) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                default:
                    return 0;
            }
        } catch (IOException e) {
            if (DEBUG) {
                Log.d(TAG, "Error reading file", e);
            }
        }
        return 0;
    }
}
