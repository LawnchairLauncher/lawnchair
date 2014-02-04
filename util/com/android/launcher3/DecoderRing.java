/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.launcher3;

import com.android.launcher3.backup.BackupProtos.CheckedMessage;
import com.android.launcher3.backup.BackupProtos.Favorite;
import com.android.launcher3.backup.BackupProtos.Key;
import com.android.launcher3.backup.BackupProtos.Journal;
import com.android.launcher3.backup.BackupProtos.Resource;
import com.android.launcher3.backup.BackupProtos.Screen;
import com.android.launcher3.backup.BackupProtos.Widget;

import com.google.protobuf.nano.InvalidProtocolBufferNanoException;
import com.google.protobuf.nano.MessageNano;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.System;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.CRC32;

import javax.xml.bind.DatatypeConverter;


/**
 * Commandline utility for decoding Launcher3 backup protocol buffers.
 *
 * <P>When using com.android.internal.backup.LocalTransport, the file names are base64-encoded Key
 * protocol buffers with a prefix, that have been base64-encoded again by the transport:
 * <pre>
 *     echo "TDpDQUlnL0pxVTVnOD0=" | launcher_protoutil -k
 * </pre>
 *
 * <P>This tool understands these file names and will use the embedded Key to detect the type and
 * extract the payload automatically:
 * <pre>
 *     launcher_protoutil /tmp/TDpDQUlnL0pxVTVnOD0=
 * </pre>
 *
 * <P>With payload debugging enabled, base64-encoded protocol buffers will be written to the logs.
 * Copy the encoded snippet from the log, and specify the type explicitly, with the Logs flags:
 * <pre>
 *    echo "CAEYLiCJ9JKsDw==" | launcher_protoutil -L -k
 * </pre>
 * For backup payloads it is more convenient to copy the log snippet to a file:
 * <pre>
 *    launcher_protoutil -L -f favorite.log
 * </pre>
 */
class DecoderRing {

    public static final String STANDARD_IN = "**stdin**";

    private static Class[] TYPES = {
            Key.class,
            Favorite.class,
            Screen.class,
            Resource.class,
            Widget.class
    };
    static final int ICON_TYPE_BITMAP = 1;

    public static void main(String[ ] args)
            throws Exception {
        Class defaultType = null;
        boolean extractImages = false;
        boolean fromLogs = false;
        int skip = 0;
        List<File> files = new LinkedList<File>();
        boolean verbose = false;

        for (int i = 0; i < args.length; i++) {
            if ("-k".equals(args[i])) {
                defaultType = Key.class;
            } else if ("-f".equals(args[i])) {
                defaultType = Favorite.class;
            } else if ("-j".equals(args[i])) {
                defaultType = Journal.class;
            } else if ("-i".equals(args[i])) {
                defaultType = Resource.class;
            } else if ("-s".equals(args[i])) {
                defaultType = Screen.class;
            } else if ("-w".equals(args[i])) {
                defaultType = Widget.class;
            } else if ("-S".equals(args[i])) {
                if ((i + 1) < args.length) {
                    skip = Integer.valueOf(args[++i]);
                } else {
                    usage(args);
                }
            } else if ("-x".equals(args[i])) {
                extractImages = true;
            } else if ("-v".equals(args[i])) {
                verbose = true;
            } else if ("-L".equals(args[i])) {
                fromLogs = true;
            } else if (args[i] != null && !args[i].startsWith("-")) {
                files.add(new File(args[i]));
            } else {
                System.err.println("Unsupported flag: " + args[i]);
                usage(args);
            }
        }

        if (defaultType == null && files.isEmpty()) {
            // can't infer file type without the key
            usage(args);
        }

        if (files.size() > 1 && defaultType != null) {
            System.err.println("Explicit type ignored for multiple files.");
            defaultType = null;
        }

        if (files.isEmpty()) {
            files.add(new File(STANDARD_IN));
        }

        for (File source : files) {
            Class type = null;
            if (defaultType == null) {
                Key key = decodeKey(source.getName().getBytes(), fromLogs);
                if (key != null) {
                    type = TYPES[key.type];
                    if (verbose) {
                        System.err.println(source.getName() + " is a " + type.getSimpleName());
                        System.out.println(key.toString());
                    }
                }
            } else {
                type = defaultType;
            }

            // read in the bytes
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            BufferedInputStream input = null;
            if (source.getName() == STANDARD_IN) {
                input = new BufferedInputStream(System.in);
            } else {
                try {
                    input = new BufferedInputStream(new FileInputStream(source));
                } catch (FileNotFoundException e) {
                    System.err.println("failed to open file: " + source + ", " + e);
                    System.exit(1);
                }
            }
            byte[] buffer = new byte[1024];
            try {
                while (input.available() > 0) {
                    int n = input.read(buffer);
                    int offset = 0;
                    if (skip > 0) {
                        offset = Math.min(skip, n);
                        n -= offset;
                        skip -= offset;
                    }
                    if (n > 0) {
                        byteStream.write(buffer, offset, n);
                    }
                }
            } catch (IOException e) {
                System.err.println("failed to read input: " + e);
                System.exit(1);
            }

            MessageNano proto = null;
            byte[] payload = byteStream.toByteArray();
            if (type == Key.class) {
                proto = decodeKey(payload, fromLogs);
            } else if (type != null) {
                proto = decodeBackupData(payload, type, fromLogs);
            }

            // Generic string output
            if (proto != null) {
                System.out.println(proto.toString());
            }

            if (extractImages) {
                String prefix = "stdin";
                if (source != null) {
                    prefix = source.getName();
                }
                // save off the icon bits in a file for inspection
                if (proto instanceof Resource) {
                    Resource icon = (Resource) proto;
                    writeImageData(icon.data, prefix + ".png");
                }

                // save off the icon bits in a file for inspection
                if (proto instanceof Favorite) {
                    Favorite favorite = (Favorite) proto;
                    if (favorite.iconType == ICON_TYPE_BITMAP) {
                        writeImageData(favorite.icon, prefix + ".png");
                    }
                }

                // save off the widget icon and preview bits in files for inspection
                if (proto instanceof Widget) {
                    Widget widget = (Widget) proto;
                    if (widget.icon != null) {
                        writeImageData(widget.icon.data, prefix + "_icon.png");
                    }
                    if (widget.preview != null) {
                        writeImageData(widget.preview.data, prefix + "_preview.png");
                    }
                }
            }
        }
        System.exit(0);
    }

    // In logcat, backup data is base64 encoded, but in localtransport files it is raw
    private static MessageNano decodeBackupData(byte[] payload, Class type, boolean fromLogs)
            throws InstantiationException, IllegalAccessException {
        MessageNano proto;// other types are wrapped in a checksum message
        CheckedMessage wrapper = new CheckedMessage();
        try {
            if (fromLogs) {
                payload = DatatypeConverter.parseBase64Binary(new String(payload));
            }
            MessageNano.mergeFrom(wrapper, payload);
        } catch (InvalidProtocolBufferNanoException e) {
            System.err.println("failed to parse wrapper: " + e);
            System.exit(1);
        }

        CRC32 checksum = new CRC32();
        checksum.update(wrapper.payload);
        if (wrapper.checksum != checksum.getValue()) {
            System.err.println("wrapper checksum failed");
            System.exit(1);
        }

        // decode the actual message
        proto = (MessageNano) type.newInstance();
        try {
            MessageNano.mergeFrom(proto, wrapper.payload);
        } catch (InvalidProtocolBufferNanoException e) {
            System.err.println("failed to parse proto: " + e);
            System.exit(1);
        }
        return proto;
    }

    // In logcat, keys are base64 encoded with no prefix.
    // The localtransport adds a prefix and the base64 encodes the whole thing again.
    private static Key decodeKey(byte[] payload, boolean fromLogs) {
        Key key = new Key();
        try {
            String encodedKey = new String(payload);
            if (!fromLogs) {
                byte[] rawKey = DatatypeConverter.parseBase64Binary(encodedKey);
                if (rawKey[0] != 'L' || rawKey[1] != ':') {
                    System.err.println(encodedKey + " is not a launcher backup key.");
                    return null;
                }
                encodedKey = new String(rawKey, 2, rawKey.length - 2);
            }
            byte[] keyProtoData = DatatypeConverter.parseBase64Binary(encodedKey);
            key = Key.parseFrom(keyProtoData);
        } catch (InvalidProtocolBufferNanoException protoException) {
            System.err.println("failed to extract key from filename: " + protoException);
            return null;
        } catch (IllegalArgumentException base64Exception) {
            System.err.println("failed to extract key from filename: " + base64Exception);
            return null;
        }

        // keys are self-checked
        if (key.checksum != checkKey(key)) {
            System.err.println("key ckecksum failed");
            return null;
        }
        return key;
    }

    private static void writeImageData(byte[] data, String path) {
        FileOutputStream iconFile = null;
        try {
            iconFile = new FileOutputStream(path);
            iconFile.write(data);
            System.err.println("wrote " + path);
        } catch (IOException e) {
            System.err.println("failed to write image file: " + e);
        } finally {
            if (iconFile != null) {
                try {
                    iconFile.close();
                } catch (IOException e) {
                    System.err.println("failed to close the image file: " + e);
                }
            }
        }
    }

    private static long checkKey(Key key) {
        CRC32 checksum = new CRC32();
        checksum.update(key.type);
        checksum.update((int) (key.id & 0xffff));
        checksum.update((int) ((key.id >> 32) & 0xffff));
        if (key.name != null && key.name.length() > 0) {
            checksum.update(key.name.getBytes());
        }
        return checksum.getValue();
    }

    private static void usage(String[] args) {
        System.err.println("launcher_protoutil [-x] [-S b] [-k|-f|-i|-s|-w] [filename]");
        System.err.println("\t-k\tdecode a key");
        System.err.println("\t-f\tdecode a favorite");
        System.err.println("\t-i\tdecode a icon");
        System.err.println("\t-s\tdecode a screen");
        System.err.println("\t-w\tdecode a widget");
        System.err.println("\t-S b\tskip b bytes");
        System.err.println("\t-x\textract image data to files");
        System.err.println("\t-v\tprint key type data, as well as payload");
        System.err.println("\t-l\texpect data from logcat, instead of the local transport");
        System.err.println("\tfilename\tread from filename, not stdin");
        System.exit(1);
    }
}