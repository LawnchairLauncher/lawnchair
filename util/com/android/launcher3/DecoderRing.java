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
import java.util.zip.CRC32;

/**
 * Commandline utility for decoding protos written to the android logs during debugging.
 *
 * base64 -D icon.log > icon.bin
 * java -classpath $ANDROID_HOST_OUT/framework/protoutil.jar:$ANDROID_HOST_OUT/../common/obj/JAVA_LIBRARIES/host-libprotobuf-java-2.3.0-nano_intermediates/javalib.jar \
 *   com.android.launcher3.DecoderRing -i icon.bin
 *
 * TODO: write a wrapper to setup the classpath
 */
class DecoderRing {
    public static void main(String[ ] args)
            throws Exception {
        File source = null;
        Class type = Key.class;
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
            } else if (args[i] != null && !args[i].startsWith("-")) {
                source = new File(args[i]);
            } else {
                System.err.println("Unsupported flag: " + args[i]);
                usage(args);
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
        System.err.println("read this many bytes: " + byteStream.size());

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
                System.err.println("key ckecksum failed");
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
                System.err.println("wrapper ckecksum failed");
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

        // save off the icon bits in a file for inspection
        if (proto instanceof Resource) {
            Resource icon = (Resource) proto;
            final String path = "icon.webp";
            FileOutputStream iconFile = new FileOutputStream(path);
            iconFile.write(icon.data);
            iconFile.close();
            System.err.println("wrote " + path);
        }

        // save off the widget icon and preview bits in files for inspection
        if (proto instanceof Widget) {
            Widget widget = (Widget) proto;
            if (widget.icon != null) {
                final String path = "widget_icon.webp";
                FileOutputStream iconFile = new FileOutputStream(path);
                iconFile.write(widget.icon.data);
                iconFile.close();
                System.err.println("wrote " + path);
            }
            if (widget.preview != null) {
                final String path = "widget_preview.webp";
                FileOutputStream iconFile = new FileOutputStream(path);
                iconFile.write(widget.preview.data);
                iconFile.close();
                System.err.println("wrote " + path);
            }
        }

        // success
        System.exit(0);
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
        System.err.println("DecoderRing type [input]");
        System.err.println("\t-k\tdecode a key");
        System.err.println("\t-f\tdecode a favorite");
        System.err.println("\t-i\tdecode a icon");
        System.err.println("\t-s\tdecode a screen");
        System.err.println("\t-w\tdecode a widget");
        System.err.println("\t-s b\tskip b bytes");
        System.err.println("\tfilename\tread from filename, not stdin");
        System.exit(1);
    }
}