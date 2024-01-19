/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3.celllayout;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import android.graphics.Point;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class CellLayoutTestCaseReader {

    public abstract static class TestSection {
        State state;

        public TestSection(State state) {
            this.state = state;
        }

        public State getState() {
            return state;
        }

        public void setState(State state) {
            this.state = state;
        }
    }

    public static class Comment extends TestSection {
        public Comment() {
            super(State.COMMENT);
        }
    }

    public static class Arguments extends TestSection {
        String[] arguments;

        public Arguments(String[] arguments) {
            super(State.ARGUMENTS);
            this.arguments = arguments;
        }
    }

    public static class Board extends TestSection {
        public Point gridSize;
        public String board;

        public Board(Point gridSize, String board) {
            super(State.BOARD);
            this.gridSize = gridSize;
            this.board = board;
        }
    }

    public enum State {
        START,
        ARGUMENTS,
        BOARD,
        END,
        COMMENT
    }

    String mTest;

    protected CellLayoutTestCaseReader(String test) {
        mTest = test;
    }

    public static CellLayoutTestCaseReader readFromFile(String fileName) throws IOException {
        String fileStr = new BufferedReader(new InputStreamReader(
                getInstrumentation().getContext().getAssets().open(fileName))
        ).lines().collect(Collectors.joining("\n"));
        return new CellLayoutTestCaseReader(fileStr);
    }

    private State getStateFromLine(String line) {
        String typeWithColons = line.trim().split(" ")[0].trim();
        String type = typeWithColons.substring(0, typeWithColons.length() - 1);
        try {
            return Enum.valueOf(State.class, type.toUpperCase());
        } catch (Exception e) {
            throw new RuntimeException(
                    "The given tag " + typeWithColons + " doesn't match with the existing tags");
        }
    }

    private String removeTag(String line) {
        return line.split(":")[1];
    }

    private TestSection parseNextLine(Iterator<String> it) {
        String line = it.next();
        if (line.trim().charAt(0) == '#') {
            return new Comment();
        }
        State state = getStateFromLine(line);
        line = removeTag(line);
        switch (state) {
            case ARGUMENTS:
                return new Arguments(parseArgumentsLine(line));
            case BOARD:
                Point grid = parseGridSize(line);
                return new Board(grid, parseBoard(it, grid.y));
            default:
                return new Comment();
        }
    }

    public List<TestSection> parse() {
        List<TestSection> sections = new ArrayList<>();
        String[] lines = mTest.split("\n");
        Iterator<String> it = Arrays.stream(lines).iterator();
        while (it.hasNext()) {
            TestSection section = parseNextLine(it);
            if (section.state == State.COMMENT) {
                continue;
            }
            sections.add(section);
        }
        return sections;
    }

    private String parseBoard(Iterator<String> it, int rows) {
        StringBuilder board = new StringBuilder();
        for (int j = 0; j < rows; j++) {
            board.append(it.next() + "\n");
        }
        return board.toString();
    }

    private String[] parseArgumentsLine(String line) {
        return Arrays.stream(line.trim().split(" ")).map(String::trim).toArray(String[]::new);
    }

    private Point parseGridSize(String line) {
        String[] values = line.toLowerCase(Locale.ROOT).split("x");
        int x = Integer.parseInt(values[0].trim());
        int y = Integer.parseInt(values[1].trim());
        return new Point(x, y);
    }
}
