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

import com.android.launcher3.backup.BackupProtos;
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
import java.util.zip.CRC32;

import javax.xml.bind.DatatypeConverter;


/**
 * Commandline utility for decoding Launcher3 backup protocol buffers.
 *
 * <P>When using com.android.internal.backup.LocalTransport, the file names are base64-encoded Key
 * protocol buffers with a prefix, that have been base64-encoded again by the transport:
 * <pre>
 *     echo TDpDQUlnL0pxVTVnOD0= | base64 -D | dd bs=1 skip=2 | base64 -D | launcher_protoutil -k
 * </pre>
 *
 * <P>This tool understands these file names and will use the embedded Key to detect the type and
 * extract the payload automatically:
 * <pre>
 *     launcher_protoutil /tmp/TDpDQUlnL0pxVTVnOD0=
 * </pre>
 *
 * <P>With payload debugging enabled, base64-encoded protocol buffers will be written to the logs.
 * Copy the encoded log snippet into a file, and specify the type explicitly:
 * <pre>
 *    base64 -D icon.log > icon.bin
 *    launcher_protoutil -i icon.bin
 * </pre>
 */
class DecoderRing {
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
        File source = null;
        Class type = null;
        boolean extractImages = false;
        int skip = 0;

        for (int i = 0; i < args.length; i++) {
            if ("-k".equals(args[i])) {
                type = Key.class;
            } else if ("-f".equals(args[i])) {
                type = Favorite.class;
            } else if ("-j".equals(args[i])) {
                type = Journal.class;
            } else if ("-i".equals(args[i])) {
                type = Resource.class;
            } else if ("-s".equals(args[i])) {
                type = Screen.class;
            } else if ("-w".equals(args[i])) {
                type = Widget.class;
            } else if ("-S".equals(args[i])) {
                if ((i + 1) < args.length) {
                    skip = Integer.valueOf(args[++i]);
                } else {
                    usage(args);
                }
            } else if ("-x".equals(args[i])) {
                extractImages = true;
            } else if (args[i] != null && !args[i].startsWith("-")) {
                source = new File(args[i]);
            } else {
                System.err.println("Unsupported flag: " + args[i]);
                usage(args);
            }
        }

        if (type == null) {
            if (source == null) {
                usage(args);
            } else {
                Key key = new Key();
                try {
                    byte[] rawKey = DatatypeConverter.parseBase64Binary(source.getName());
                    if (rawKey[0] != 'L' || rawKey[1] != ':') {
                        System.err.println("you must specify the payload type. " +
                                source.getName() + " is not a launcher backup key.");
                        System.exit(1);
                    }
                    String encodedPayload = new String(rawKey, 2, rawKey.length - 2);
                    byte[] keyProtoData = DatatypeConverter.parseBase64Binary(encodedPayload);
                    key = Key.parseFrom(keyProtoData);
                } catch (InvalidProtocolBufferNanoException protoException) {
                    System.err.println("failed to extract key from filename: " + protoException);
                    System.exit(1);
                } catch (IllegalArgumentException base64Exception) {
                    System.err.println("failed to extract key from filename: " + base64Exception);
                    System.exit(1);
                }
                // keys are self-checked
                if (key.checksum != checkKey(key)) {
                    System.err.println("key ckecksum failed");
                    System.exit(1);
                }
                type = TYPES[key.type];
                System.err.println("This is a " + type.getSimpleName() + " backup");
            }
        }

        // read in the bytes
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        BufferedInputStream input = null;
        if (source == null) {
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
        if (type == Key.class) {
            Key key = new Key();
            try {
                key = Key.parseFrom(byteStream.toByteArray());
            } catch (InvalidProtocolBufferNanoException e) {
                System.err.println("failed to parse proto: " + e);
                System.exit(1);
            }
            // keys are self-checked
            if (key.checksum != checkKey(key)) {
                System.err.println("key checksum failed");
                System.exit(1);
            }
            proto = key;
        } else {
            // other types are wrapped in a checksum message
            CheckedMessage wrapper = new CheckedMessage();
            try {
                MessageNano.mergeFrom(wrapper, byteStream.toByteArray());
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
        }

        // Generic string output
        System.out.println(proto.toString());

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

        // success
        System.exit(0);
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
        System.err.println("\tfilename\tread from filename, not stdin");
        System.exit(1);
    }
}