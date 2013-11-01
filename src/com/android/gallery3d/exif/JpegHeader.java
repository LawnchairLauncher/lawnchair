/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.gallery3d.exif;

class JpegHeader {
    public static final short SOI =  (short) 0xFFD8;
    public static final short APP1 = (short) 0xFFE1;
    public static final short APP0 = (short) 0xFFE0;
    public static final short EOI = (short) 0xFFD9;

    /**
     *  SOF (start of frame). All value between SOF0 and SOF15 is SOF marker except for DHT, JPG,
     *  and DAC marker.
     */
    public static final short SOF0 = (short) 0xFFC0;
    public static final short SOF15 = (short) 0xFFCF;
    public static final short DHT = (short) 0xFFC4;
    public static final short JPG = (short) 0xFFC8;
    public static final short DAC = (short) 0xFFCC;

    public static final boolean isSofMarker(short marker) {
        return marker >= SOF0 && marker <= SOF15 && marker != DHT && marker != JPG
                && marker != DAC;
    }
}
