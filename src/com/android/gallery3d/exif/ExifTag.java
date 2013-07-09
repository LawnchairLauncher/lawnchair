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

import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

/**
 * This class stores information of an EXIF tag. For more information about
 * defined EXIF tags, please read the Jeita EXIF 2.2 standard. Tags should be
 * instantiated using {@link ExifInterface#buildTag}.
 *
 * @see ExifInterface
 */
public class ExifTag {
    /**
     * The BYTE type in the EXIF standard. An 8-bit unsigned integer.
     */
    public static final short TYPE_UNSIGNED_BYTE = 1;
    /**
     * The ASCII type in the EXIF standard. An 8-bit byte containing one 7-bit
     * ASCII code. The final byte is terminated with NULL.
     */
    public static final short TYPE_ASCII = 2;
    /**
     * The SHORT type in the EXIF standard. A 16-bit (2-byte) unsigned integer
     */
    public static final short TYPE_UNSIGNED_SHORT = 3;
    /**
     * The LONG type in the EXIF standard. A 32-bit (4-byte) unsigned integer
     */
    public static final short TYPE_UNSIGNED_LONG = 4;
    /**
     * The RATIONAL type of EXIF standard. It consists of two LONGs. The first
     * one is the numerator and the second one expresses the denominator.
     */
    public static final short TYPE_UNSIGNED_RATIONAL = 5;
    /**
     * The UNDEFINED type in the EXIF standard. An 8-bit byte that can take any
     * value depending on the field definition.
     */
    public static final short TYPE_UNDEFINED = 7;
    /**
     * The SLONG type in the EXIF standard. A 32-bit (4-byte) signed integer
     * (2's complement notation).
     */
    public static final short TYPE_LONG = 9;
    /**
     * The SRATIONAL type of EXIF standard. It consists of two SLONGs. The first
     * one is the numerator and the second one is the denominator.
     */
    public static final short TYPE_RATIONAL = 10;

    private static Charset US_ASCII = Charset.forName("US-ASCII");
    private static final int TYPE_TO_SIZE_MAP[] = new int[11];
    private static final int UNSIGNED_SHORT_MAX = 65535;
    private static final long UNSIGNED_LONG_MAX = 4294967295L;
    private static final long LONG_MAX = Integer.MAX_VALUE;
    private static final long LONG_MIN = Integer.MIN_VALUE;

    static {
        TYPE_TO_SIZE_MAP[TYPE_UNSIGNED_BYTE] = 1;
        TYPE_TO_SIZE_MAP[TYPE_ASCII] = 1;
        TYPE_TO_SIZE_MAP[TYPE_UNSIGNED_SHORT] = 2;
        TYPE_TO_SIZE_MAP[TYPE_UNSIGNED_LONG] = 4;
        TYPE_TO_SIZE_MAP[TYPE_UNSIGNED_RATIONAL] = 8;
        TYPE_TO_SIZE_MAP[TYPE_UNDEFINED] = 1;
        TYPE_TO_SIZE_MAP[TYPE_LONG] = 4;
        TYPE_TO_SIZE_MAP[TYPE_RATIONAL] = 8;
    }

    static final int SIZE_UNDEFINED = 0;

    // Exif TagId
    private final short mTagId;
    // Exif Tag Type
    private final short mDataType;
    // If tag has defined count
    private boolean mHasDefinedDefaultComponentCount;
    // Actual data count in tag (should be number of elements in value array)
    private int mComponentCountActual;
    // The ifd that this tag should be put in
    private int mIfd;
    // The value (array of elements of type Tag Type)
    private Object mValue;
    // Value offset in exif header.
    private int mOffset;

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("yyyy:MM:dd kk:mm:ss");

    /**
     * Returns true if the given IFD is a valid IFD.
     */
    public static boolean isValidIfd(int ifdId) {
        return ifdId == IfdId.TYPE_IFD_0 || ifdId == IfdId.TYPE_IFD_1
                || ifdId == IfdId.TYPE_IFD_EXIF || ifdId == IfdId.TYPE_IFD_INTEROPERABILITY
                || ifdId == IfdId.TYPE_IFD_GPS;
    }

    /**
     * Returns true if a given type is a valid tag type.
     */
    public static boolean isValidType(short type) {
        return type == TYPE_UNSIGNED_BYTE || type == TYPE_ASCII ||
                type == TYPE_UNSIGNED_SHORT || type == TYPE_UNSIGNED_LONG ||
                type == TYPE_UNSIGNED_RATIONAL || type == TYPE_UNDEFINED ||
                type == TYPE_LONG || type == TYPE_RATIONAL;
    }

    // Use builtTag in ExifInterface instead of constructor.
    ExifTag(short tagId, short type, int componentCount, int ifd,
            boolean hasDefinedComponentCount) {
        mTagId = tagId;
        mDataType = type;
        mComponentCountActual = componentCount;
        mHasDefinedDefaultComponentCount = hasDefinedComponentCount;
        mIfd = ifd;
        mValue = null;
    }

    /**
     * Gets the element size of the given data type in bytes.
     *
     * @see #TYPE_ASCII
     * @see #TYPE_LONG
     * @see #TYPE_RATIONAL
     * @see #TYPE_UNDEFINED
     * @see #TYPE_UNSIGNED_BYTE
     * @see #TYPE_UNSIGNED_LONG
     * @see #TYPE_UNSIGNED_RATIONAL
     * @see #TYPE_UNSIGNED_SHORT
     */
    public static int getElementSize(short type) {
        return TYPE_TO_SIZE_MAP[type];
    }

    /**
     * Returns the ID of the IFD this tag belongs to.
     *
     * @see IfdId#TYPE_IFD_0
     * @see IfdId#TYPE_IFD_1
     * @see IfdId#TYPE_IFD_EXIF
     * @see IfdId#TYPE_IFD_GPS
     * @see IfdId#TYPE_IFD_INTEROPERABILITY
     */
    public int getIfd() {
        return mIfd;
    }

    protected void setIfd(int ifdId) {
        mIfd = ifdId;
    }

    /**
     * Gets the TID of this tag.
     */
    public short getTagId() {
        return mTagId;
    }

    /**
     * Gets the data type of this tag
     *
     * @see #TYPE_ASCII
     * @see #TYPE_LONG
     * @see #TYPE_RATIONAL
     * @see #TYPE_UNDEFINED
     * @see #TYPE_UNSIGNED_BYTE
     * @see #TYPE_UNSIGNED_LONG
     * @see #TYPE_UNSIGNED_RATIONAL
     * @see #TYPE_UNSIGNED_SHORT
     */
    public short getDataType() {
        return mDataType;
    }

    /**
     * Gets the total data size in bytes of the value of this tag.
     */
    public int getDataSize() {
        return getComponentCount() * getElementSize(getDataType());
    }

    /**
     * Gets the component count of this tag.
     */

    // TODO: fix integer overflows with this
    public int getComponentCount() {
        return mComponentCountActual;
    }

    /**
     * Sets the component count of this tag. Call this function before
     * setValue() if the length of value does not match the component count.
     */
    protected void forceSetComponentCount(int count) {
        mComponentCountActual = count;
    }

    /**
     * Returns true if this ExifTag contains value; otherwise, this tag will
     * contain an offset value that is determined when the tag is written.
     */
    public boolean hasValue() {
        return mValue != null;
    }

    /**
     * Sets integer values into this tag. This method should be used for tags of
     * type {@link #TYPE_UNSIGNED_SHORT}. This method will fail if:
     * <ul>
     * <li>The component type of this tag is not {@link #TYPE_UNSIGNED_SHORT},
     * {@link #TYPE_UNSIGNED_LONG}, or {@link #TYPE_LONG}.</li>
     * <li>The value overflows.</li>
     * <li>The value.length does NOT match the component count in the definition
     * for this tag.</li>
     * </ul>
     */
    public boolean setValue(int[] value) {
        if (checkBadComponentCount(value.length)) {
            return false;
        }
        if (mDataType != TYPE_UNSIGNED_SHORT && mDataType != TYPE_LONG &&
                mDataType != TYPE_UNSIGNED_LONG) {
            return false;
        }
        if (mDataType == TYPE_UNSIGNED_SHORT && checkOverflowForUnsignedShort(value)) {
            return false;
        } else if (mDataType == TYPE_UNSIGNED_LONG && checkOverflowForUnsignedLong(value)) {
            return false;
        }

        long[] data = new long[value.length];
        for (int i = 0; i < value.length; i++) {
            data[i] = value[i];
        }
        mValue = data;
        mComponentCountActual = value.length;
        return true;
    }

    /**
     * Sets integer value into this tag. This method should be used for tags of
     * type {@link #TYPE_UNSIGNED_SHORT}, or {@link #TYPE_LONG}. This method
     * will fail if:
     * <ul>
     * <li>The component type of this tag is not {@link #TYPE_UNSIGNED_SHORT},
     * {@link #TYPE_UNSIGNED_LONG}, or {@link #TYPE_LONG}.</li>
     * <li>The value overflows.</li>
     * <li>The component count in the definition of this tag is not 1.</li>
     * </ul>
     */
    public boolean setValue(int value) {
        return setValue(new int[] {
                value
        });
    }

    /**
     * Sets long values into this tag. This method should be used for tags of
     * type {@link #TYPE_UNSIGNED_LONG}. This method will fail if:
     * <ul>
     * <li>The component type of this tag is not {@link #TYPE_UNSIGNED_LONG}.</li>
     * <li>The value overflows.</li>
     * <li>The value.length does NOT match the component count in the definition
     * for this tag.</li>
     * </ul>
     */
    public boolean setValue(long[] value) {
        if (checkBadComponentCount(value.length) || mDataType != TYPE_UNSIGNED_LONG) {
            return false;
        }
        if (checkOverflowForUnsignedLong(value)) {
            return false;
        }
        mValue = value;
        mComponentCountActual = value.length;
        return true;
    }

    /**
     * Sets long values into this tag. This method should be used for tags of
     * type {@link #TYPE_UNSIGNED_LONG}. This method will fail if:
     * <ul>
     * <li>The component type of this tag is not {@link #TYPE_UNSIGNED_LONG}.</li>
     * <li>The value overflows.</li>
     * <li>The component count in the definition for this tag is not 1.</li>
     * </ul>
     */
    public boolean setValue(long value) {
        return setValue(new long[] {
                value
        });
    }

    /**
     * Sets a string value into this tag. This method should be used for tags of
     * type {@link #TYPE_ASCII}. The string is converted to an ASCII string.
     * Characters that cannot be converted are replaced with '?'. The length of
     * the string must be equal to either (component count -1) or (component
     * count). The final byte will be set to the string null terminator '\0',
     * overwriting the last character in the string if the value.length is equal
     * to the component count. This method will fail if:
     * <ul>
     * <li>The data type is not {@link #TYPE_ASCII} or {@link #TYPE_UNDEFINED}.</li>
     * <li>The length of the string is not equal to (component count -1) or
     * (component count) in the definition for this tag.</li>
     * </ul>
     */
    public boolean setValue(String value) {
        if (mDataType != TYPE_ASCII && mDataType != TYPE_UNDEFINED) {
            return false;
        }

        byte[] buf = value.getBytes(US_ASCII);
        byte[] finalBuf = buf;
        if (buf.length > 0) {
            finalBuf = (buf[buf.length - 1] == 0 || mDataType == TYPE_UNDEFINED) ? buf : Arrays
                .copyOf(buf, buf.length + 1);
        } else if (mDataType == TYPE_ASCII && mComponentCountActual == 1) {
            finalBuf = new byte[] { 0 };
        }
        int count = finalBuf.length;
        if (checkBadComponentCount(count)) {
            return false;
        }
        mComponentCountActual = count;
        mValue = finalBuf;
        return true;
    }

    /**
     * Sets Rational values into this tag. This method should be used for tags
     * of type {@link #TYPE_UNSIGNED_RATIONAL}, or {@link #TYPE_RATIONAL}. This
     * method will fail if:
     * <ul>
     * <li>The component type of this tag is not {@link #TYPE_UNSIGNED_RATIONAL}
     * or {@link #TYPE_RATIONAL}.</li>
     * <li>The value overflows.</li>
     * <li>The value.length does NOT match the component count in the definition
     * for this tag.</li>
     * </ul>
     *
     * @see Rational
     */
    public boolean setValue(Rational[] value) {
        if (checkBadComponentCount(value.length)) {
            return false;
        }
        if (mDataType != TYPE_UNSIGNED_RATIONAL && mDataType != TYPE_RATIONAL) {
            return false;
        }
        if (mDataType == TYPE_UNSIGNED_RATIONAL && checkOverflowForUnsignedRational(value)) {
            return false;
        } else if (mDataType == TYPE_RATIONAL && checkOverflowForRational(value)) {
            return false;
        }

        mValue = value;
        mComponentCountActual = value.length;
        return true;
    }

    /**
     * Sets a Rational value into this tag. This method should be used for tags
     * of type {@link #TYPE_UNSIGNED_RATIONAL}, or {@link #TYPE_RATIONAL}. This
     * method will fail if:
     * <ul>
     * <li>The component type of this tag is not {@link #TYPE_UNSIGNED_RATIONAL}
     * or {@link #TYPE_RATIONAL}.</li>
     * <li>The value overflows.</li>
     * <li>The component count in the definition for this tag is not 1.</li>
     * </ul>
     *
     * @see Rational
     */
    public boolean setValue(Rational value) {
        return setValue(new Rational[] {
                value
        });
    }

    /**
     * Sets byte values into this tag. This method should be used for tags of
     * type {@link #TYPE_UNSIGNED_BYTE} or {@link #TYPE_UNDEFINED}. This method
     * will fail if:
     * <ul>
     * <li>The component type of this tag is not {@link #TYPE_UNSIGNED_BYTE} or
     * {@link #TYPE_UNDEFINED} .</li>
     * <li>The length does NOT match the component count in the definition for
     * this tag.</li>
     * </ul>
     */
    public boolean setValue(byte[] value, int offset, int length) {
        if (checkBadComponentCount(length)) {
            return false;
        }
        if (mDataType != TYPE_UNSIGNED_BYTE && mDataType != TYPE_UNDEFINED) {
            return false;
        }
        mValue = new byte[length];
        System.arraycopy(value, offset, mValue, 0, length);
        mComponentCountActual = length;
        return true;
    }

    /**
     * Equivalent to setValue(value, 0, value.length).
     */
    public boolean setValue(byte[] value) {
        return setValue(value, 0, value.length);
    }

    /**
     * Sets byte value into this tag. This method should be used for tags of
     * type {@link #TYPE_UNSIGNED_BYTE} or {@link #TYPE_UNDEFINED}. This method
     * will fail if:
     * <ul>
     * <li>The component type of this tag is not {@link #TYPE_UNSIGNED_BYTE} or
     * {@link #TYPE_UNDEFINED} .</li>
     * <li>The component count in the definition for this tag is not 1.</li>
     * </ul>
     */
    public boolean setValue(byte value) {
        return setValue(new byte[] {
                value
        });
    }

    /**
     * Sets the value for this tag using an appropriate setValue method for the
     * given object. This method will fail if:
     * <ul>
     * <li>The corresponding setValue method for the class of the object passed
     * in would fail.</li>
     * <li>There is no obvious way to cast the object passed in into an EXIF tag
     * type.</li>
     * </ul>
     */
    public boolean setValue(Object obj) {
        if (obj == null) {
            return false;
        } else if (obj instanceof Short) {
            return setValue(((Short) obj).shortValue() & 0x0ffff);
        } else if (obj instanceof String) {
            return setValue((String) obj);
        } else if (obj instanceof int[]) {
            return setValue((int[]) obj);
        } else if (obj instanceof long[]) {
            return setValue((long[]) obj);
        } else if (obj instanceof Rational) {
            return setValue((Rational) obj);
        } else if (obj instanceof Rational[]) {
            return setValue((Rational[]) obj);
        } else if (obj instanceof byte[]) {
            return setValue((byte[]) obj);
        } else if (obj instanceof Integer) {
            return setValue(((Integer) obj).intValue());
        } else if (obj instanceof Long) {
            return setValue(((Long) obj).longValue());
        } else if (obj instanceof Byte) {
            return setValue(((Byte) obj).byteValue());
        } else if (obj instanceof Short[]) {
            // Nulls in this array are treated as zeroes.
            Short[] arr = (Short[]) obj;
            int[] fin = new int[arr.length];
            for (int i = 0; i < arr.length; i++) {
                fin[i] = (arr[i] == null) ? 0 : arr[i].shortValue() & 0x0ffff;
            }
            return setValue(fin);
        } else if (obj instanceof Integer[]) {
            // Nulls in this array are treated as zeroes.
            Integer[] arr = (Integer[]) obj;
            int[] fin = new int[arr.length];
            for (int i = 0; i < arr.length; i++) {
                fin[i] = (arr[i] == null) ? 0 : arr[i].intValue();
            }
            return setValue(fin);
        } else if (obj instanceof Long[]) {
            // Nulls in this array are treated as zeroes.
            Long[] arr = (Long[]) obj;
            long[] fin = new long[arr.length];
            for (int i = 0; i < arr.length; i++) {
                fin[i] = (arr[i] == null) ? 0 : arr[i].longValue();
            }
            return setValue(fin);
        } else if (obj instanceof Byte[]) {
            // Nulls in this array are treated as zeroes.
            Byte[] arr = (Byte[]) obj;
            byte[] fin = new byte[arr.length];
            for (int i = 0; i < arr.length; i++) {
                fin[i] = (arr[i] == null) ? 0 : arr[i].byteValue();
            }
            return setValue(fin);
        } else {
            return false;
        }
    }

    /**
     * Sets a timestamp to this tag. The method converts the timestamp with the
     * format of "yyyy:MM:dd kk:mm:ss" and calls {@link #setValue(String)}. This
     * method will fail if the data type is not {@link #TYPE_ASCII} or the
     * component count of this tag is not 20 or undefined.
     *
     * @param time the number of milliseconds since Jan. 1, 1970 GMT
     * @return true on success
     */
    public boolean setTimeValue(long time) {
        // synchronized on TIME_FORMAT as SimpleDateFormat is not thread safe
        synchronized (TIME_FORMAT) {
            return setValue(TIME_FORMAT.format(new Date(time)));
        }
    }

    /**
     * Gets the value as a String. This method should be used for tags of type
     * {@link #TYPE_ASCII}.
     *
     * @return the value as a String, or null if the tag's value does not exist
     *         or cannot be converted to a String.
     */
    public String getValueAsString() {
        if (mValue == null) {
            return null;
        } else if (mValue instanceof String) {
            return (String) mValue;
        } else if (mValue instanceof byte[]) {
            return new String((byte[]) mValue, US_ASCII);
        }
        return null;
    }

    /**
     * Gets the value as a String. This method should be used for tags of type
     * {@link #TYPE_ASCII}.
     *
     * @param defaultValue the String to return if the tag's value does not
     *            exist or cannot be converted to a String.
     * @return the tag's value as a String, or the defaultValue.
     */
    public String getValueAsString(String defaultValue) {
        String s = getValueAsString();
        if (s == null) {
            return defaultValue;
        }
        return s;
    }

    /**
     * Gets the value as a byte array. This method should be used for tags of
     * type {@link #TYPE_UNDEFINED} or {@link #TYPE_UNSIGNED_BYTE}.
     *
     * @return the value as a byte array, or null if the tag's value does not
     *         exist or cannot be converted to a byte array.
     */
    public byte[] getValueAsBytes() {
        if (mValue instanceof byte[]) {
            return (byte[]) mValue;
        }
        return null;
    }

    /**
     * Gets the value as a byte. If there are more than 1 bytes in this value,
     * gets the first byte. This method should be used for tags of type
     * {@link #TYPE_UNDEFINED} or {@link #TYPE_UNSIGNED_BYTE}.
     *
     * @param defaultValue the byte to return if tag's value does not exist or
     *            cannot be converted to a byte.
     * @return the tag's value as a byte, or the defaultValue.
     */
    public byte getValueAsByte(byte defaultValue) {
        byte[] b = getValueAsBytes();
        if (b == null || b.length < 1) {
            return defaultValue;
        }
        return b[0];
    }

    /**
     * Gets the value as an array of Rationals. This method should be used for
     * tags of type {@link #TYPE_RATIONAL} or {@link #TYPE_UNSIGNED_RATIONAL}.
     *
     * @return the value as as an array of Rationals, or null if the tag's value
     *         does not exist or cannot be converted to an array of Rationals.
     */
    public Rational[] getValueAsRationals() {
        if (mValue instanceof Rational[]) {
            return (Rational[]) mValue;
        }
        return null;
    }

    /**
     * Gets the value as a Rational. If there are more than 1 Rationals in this
     * value, gets the first one. This method should be used for tags of type
     * {@link #TYPE_RATIONAL} or {@link #TYPE_UNSIGNED_RATIONAL}.
     *
     * @param defaultValue the Rational to return if tag's value does not exist
     *            or cannot be converted to a Rational.
     * @return the tag's value as a Rational, or the defaultValue.
     */
    public Rational getValueAsRational(Rational defaultValue) {
        Rational[] r = getValueAsRationals();
        if (r == null || r.length < 1) {
            return defaultValue;
        }
        return r[0];
    }

    /**
     * Gets the value as a Rational. If there are more than 1 Rationals in this
     * value, gets the first one. This method should be used for tags of type
     * {@link #TYPE_RATIONAL} or {@link #TYPE_UNSIGNED_RATIONAL}.
     *
     * @param defaultValue the numerator of the Rational to return if tag's
     *            value does not exist or cannot be converted to a Rational (the
     *            denominator will be 1).
     * @return the tag's value as a Rational, or the defaultValue.
     */
    public Rational getValueAsRational(long defaultValue) {
        Rational defaultVal = new Rational(defaultValue, 1);
        return getValueAsRational(defaultVal);
    }

    /**
     * Gets the value as an array of ints. This method should be used for tags
     * of type {@link #TYPE_UNSIGNED_SHORT}, {@link #TYPE_UNSIGNED_LONG}.
     *
     * @return the value as as an array of ints, or null if the tag's value does
     *         not exist or cannot be converted to an array of ints.
     */
    public int[] getValueAsInts() {
        if (mValue == null) {
            return null;
        } else if (mValue instanceof long[]) {
            long[] val = (long[]) mValue;
            int[] arr = new int[val.length];
            for (int i = 0; i < val.length; i++) {
                arr[i] = (int) val[i]; // Truncates
            }
            return arr;
        }
        return null;
    }

    /**
     * Gets the value as an int. If there are more than 1 ints in this value,
     * gets the first one. This method should be used for tags of type
     * {@link #TYPE_UNSIGNED_SHORT}, {@link #TYPE_UNSIGNED_LONG}.
     *
     * @param defaultValue the int to return if tag's value does not exist or
     *            cannot be converted to an int.
     * @return the tag's value as a int, or the defaultValue.
     */
    public int getValueAsInt(int defaultValue) {
        int[] i = getValueAsInts();
        if (i == null || i.length < 1) {
            return defaultValue;
        }
        return i[0];
    }

    /**
     * Gets the value as an array of longs. This method should be used for tags
     * of type {@link #TYPE_UNSIGNED_LONG}.
     *
     * @return the value as as an array of longs, or null if the tag's value
     *         does not exist or cannot be converted to an array of longs.
     */
    public long[] getValueAsLongs() {
        if (mValue instanceof long[]) {
            return (long[]) mValue;
        }
        return null;
    }

    /**
     * Gets the value or null if none exists. If there are more than 1 longs in
     * this value, gets the first one. This method should be used for tags of
     * type {@link #TYPE_UNSIGNED_LONG}.
     *
     * @param defaultValue the long to return if tag's value does not exist or
     *            cannot be converted to a long.
     * @return the tag's value as a long, or the defaultValue.
     */
    public long getValueAsLong(long defaultValue) {
        long[] l = getValueAsLongs();
        if (l == null || l.length < 1) {
            return defaultValue;
        }
        return l[0];
    }

    /**
     * Gets the tag's value or null if none exists.
     */
    public Object getValue() {
        return mValue;
    }

    /**
     * Gets a long representation of the value.
     *
     * @param defaultValue value to return if there is no value or value is a
     *            rational with a denominator of 0.
     * @return the tag's value as a long, or defaultValue if no representation
     *         exists.
     */
    public long forceGetValueAsLong(long defaultValue) {
        long[] l = getValueAsLongs();
        if (l != null && l.length >= 1) {
            return l[0];
        }
        byte[] b = getValueAsBytes();
        if (b != null && b.length >= 1) {
            return b[0];
        }
        Rational[] r = getValueAsRationals();
        if (r != null && r.length >= 1 && r[0].getDenominator() != 0) {
            return (long) r[0].toDouble();
        }
        return defaultValue;
    }

    /**
     * Gets a string representation of the value.
     */
    public String forceGetValueAsString() {
        if (mValue == null) {
            return "";
        } else if (mValue instanceof byte[]) {
            if (mDataType == TYPE_ASCII) {
                return new String((byte[]) mValue, US_ASCII);
            } else {
                return Arrays.toString((byte[]) mValue);
            }
        } else if (mValue instanceof long[]) {
            if (((long[]) mValue).length == 1) {
                return String.valueOf(((long[]) mValue)[0]);
            } else {
                return Arrays.toString((long[]) mValue);
            }
        } else if (mValue instanceof Object[]) {
            if (((Object[]) mValue).length == 1) {
                Object val = ((Object[]) mValue)[0];
                if (val == null) {
                    return "";
                } else {
                    return val.toString();
                }
            } else {
                return Arrays.toString((Object[]) mValue);
            }
        } else {
            return mValue.toString();
        }
    }

    /**
     * Gets the value for type {@link #TYPE_ASCII}, {@link #TYPE_LONG},
     * {@link #TYPE_UNDEFINED}, {@link #TYPE_UNSIGNED_BYTE},
     * {@link #TYPE_UNSIGNED_LONG}, or {@link #TYPE_UNSIGNED_SHORT}. For
     * {@link #TYPE_RATIONAL} or {@link #TYPE_UNSIGNED_RATIONAL}, call
     * {@link #getRational(int)} instead.
     *
     * @exception IllegalArgumentException if the data type is
     *                {@link #TYPE_RATIONAL} or {@link #TYPE_UNSIGNED_RATIONAL}.
     */
    protected long getValueAt(int index) {
        if (mValue instanceof long[]) {
            return ((long[]) mValue)[index];
        } else if (mValue instanceof byte[]) {
            return ((byte[]) mValue)[index];
        }
        throw new IllegalArgumentException("Cannot get integer value from "
                + convertTypeToString(mDataType));
    }

    /**
     * Gets the {@link #TYPE_ASCII} data.
     *
     * @exception IllegalArgumentException If the type is NOT
     *                {@link #TYPE_ASCII}.
     */
    protected String getString() {
        if (mDataType != TYPE_ASCII) {
            throw new IllegalArgumentException("Cannot get ASCII value from "
                    + convertTypeToString(mDataType));
        }
        return new String((byte[]) mValue, US_ASCII);
    }

    /*
     * Get the converted ascii byte. Used by ExifOutputStream.
     */
    protected byte[] getStringByte() {
        return (byte[]) mValue;
    }

    /**
     * Gets the {@link #TYPE_RATIONAL} or {@link #TYPE_UNSIGNED_RATIONAL} data.
     *
     * @exception IllegalArgumentException If the type is NOT
     *                {@link #TYPE_RATIONAL} or {@link #TYPE_UNSIGNED_RATIONAL}.
     */
    protected Rational getRational(int index) {
        if ((mDataType != TYPE_RATIONAL) && (mDataType != TYPE_UNSIGNED_RATIONAL)) {
            throw new IllegalArgumentException("Cannot get RATIONAL value from "
                    + convertTypeToString(mDataType));
        }
        return ((Rational[]) mValue)[index];
    }

    /**
     * Equivalent to getBytes(buffer, 0, buffer.length).
     */
    protected void getBytes(byte[] buf) {
        getBytes(buf, 0, buf.length);
    }

    /**
     * Gets the {@link #TYPE_UNDEFINED} or {@link #TYPE_UNSIGNED_BYTE} data.
     *
     * @param buf the byte array in which to store the bytes read.
     * @param offset the initial position in buffer to store the bytes.
     * @param length the maximum number of bytes to store in buffer. If length >
     *            component count, only the valid bytes will be stored.
     * @exception IllegalArgumentException If the type is NOT
     *                {@link #TYPE_UNDEFINED} or {@link #TYPE_UNSIGNED_BYTE}.
     */
    protected void getBytes(byte[] buf, int offset, int length) {
        if ((mDataType != TYPE_UNDEFINED) && (mDataType != TYPE_UNSIGNED_BYTE)) {
            throw new IllegalArgumentException("Cannot get BYTE value from "
                    + convertTypeToString(mDataType));
        }
        System.arraycopy(mValue, 0, buf, offset,
                (length > mComponentCountActual) ? mComponentCountActual : length);
    }

    /**
     * Gets the offset of this tag. This is only valid if this data size > 4 and
     * contains an offset to the location of the actual value.
     */
    protected int getOffset() {
        return mOffset;
    }

    /**
     * Sets the offset of this tag.
     */
    protected void setOffset(int offset) {
        mOffset = offset;
    }

    protected void setHasDefinedCount(boolean d) {
        mHasDefinedDefaultComponentCount = d;
    }

    protected boolean hasDefinedCount() {
        return mHasDefinedDefaultComponentCount;
    }

    private boolean checkBadComponentCount(int count) {
        if (mHasDefinedDefaultComponentCount && (mComponentCountActual != count)) {
            return true;
        }
        return false;
    }

    private static String convertTypeToString(short type) {
        switch (type) {
            case TYPE_UNSIGNED_BYTE:
                return "UNSIGNED_BYTE";
            case TYPE_ASCII:
                return "ASCII";
            case TYPE_UNSIGNED_SHORT:
                return "UNSIGNED_SHORT";
            case TYPE_UNSIGNED_LONG:
                return "UNSIGNED_LONG";
            case TYPE_UNSIGNED_RATIONAL:
                return "UNSIGNED_RATIONAL";
            case TYPE_UNDEFINED:
                return "UNDEFINED";
            case TYPE_LONG:
                return "LONG";
            case TYPE_RATIONAL:
                return "RATIONAL";
            default:
                return "";
        }
    }

    private boolean checkOverflowForUnsignedShort(int[] value) {
        for (int v : value) {
            if (v > UNSIGNED_SHORT_MAX || v < 0) {
                return true;
            }
        }
        return false;
    }

    private boolean checkOverflowForUnsignedLong(long[] value) {
        for (long v : value) {
            if (v < 0 || v > UNSIGNED_LONG_MAX) {
                return true;
            }
        }
        return false;
    }

    private boolean checkOverflowForUnsignedLong(int[] value) {
        for (int v : value) {
            if (v < 0) {
                return true;
            }
        }
        return false;
    }

    private boolean checkOverflowForUnsignedRational(Rational[] value) {
        for (Rational v : value) {
            if (v.getNumerator() < 0 || v.getDenominator() < 0
                    || v.getNumerator() > UNSIGNED_LONG_MAX
                    || v.getDenominator() > UNSIGNED_LONG_MAX) {
                return true;
            }
        }
        return false;
    }

    private boolean checkOverflowForRational(Rational[] value) {
        for (Rational v : value) {
            if (v.getNumerator() < LONG_MIN || v.getDenominator() < LONG_MIN
                    || v.getNumerator() > LONG_MAX
                    || v.getDenominator() > LONG_MAX) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj instanceof ExifTag) {
            ExifTag tag = (ExifTag) obj;
            if (tag.mTagId != this.mTagId
                    || tag.mComponentCountActual != this.mComponentCountActual
                    || tag.mDataType != this.mDataType) {
                return false;
            }
            if (mValue != null) {
                if (tag.mValue == null) {
                    return false;
                } else if (mValue instanceof long[]) {
                    if (!(tag.mValue instanceof long[])) {
                        return false;
                    }
                    return Arrays.equals((long[]) mValue, (long[]) tag.mValue);
                } else if (mValue instanceof Rational[]) {
                    if (!(tag.mValue instanceof Rational[])) {
                        return false;
                    }
                    return Arrays.equals((Rational[]) mValue, (Rational[]) tag.mValue);
                } else if (mValue instanceof byte[]) {
                    if (!(tag.mValue instanceof byte[])) {
                        return false;
                    }
                    return Arrays.equals((byte[]) mValue, (byte[]) tag.mValue);
                } else {
                    return mValue.equals(tag.mValue);
                }
            } else {
                return tag.mValue == null;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("tag id: %04X\n", mTagId) + "ifd id: " + mIfd + "\ntype: "
                + convertTypeToString(mDataType) + "\ncount: " + mComponentCountActual
                + "\noffset: " + mOffset + "\nvalue: " + forceGetValueAsString() + "\n";
    }

}
