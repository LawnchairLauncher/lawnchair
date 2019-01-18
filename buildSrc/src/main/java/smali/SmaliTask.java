// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package smali;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.gradle.api.DefaultTask;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.jf.smali.Smali;
import org.jf.smali.SmaliOptions;

@SuppressWarnings({"unused", "SpellCheckingInspection"})
public class SmaliTask extends DefaultTask {
    private FileTree mSource;
    private File mDestination;
    private File mSmaliScript;

    @InputFiles
    public FileTree getSource() {
        return mSource;
    }

    public void setSource(FileTree source) {
        mSource = source;
        getInputs().files(source);
    }

    @OutputFile
    public File getDestination() {
        return mDestination;
    }

    public void setDestination(File destination) {
        mDestination = destination;
        getOutputs().file(destination);
    }

    public File getSmaliScript() {
        return mSmaliScript;
    }

    public void setSmaliScript(File smaliScript) {
        mSmaliScript = smaliScript;
    }

    @TaskAction
    void exec() {
        try {
            List<String> fileNames = mSource.getFiles().stream().map(File::toString)
                    .collect(Collectors.toList());
            SmaliOptions options = new SmaliOptions();
            options.outputDexFile = mDestination.getCanonicalPath();
            Smali.assemble(options, fileNames);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}