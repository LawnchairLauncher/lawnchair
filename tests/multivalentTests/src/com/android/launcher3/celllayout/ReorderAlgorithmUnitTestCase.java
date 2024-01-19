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

import com.android.launcher3.celllayout.board.CellLayoutBoard;

import java.util.Iterator;

/**
 * Represents a test case for {@code ReorderAlgorithmUnitTest}. The test cases are generated from
 * text, an example of a test is the following:
 *
 * board: 10x8
 * aaaaaaaaai
 * bbbbbcciii
 * ---------f
 * ---------f
 * ---------f
 * ---------i
 * iiddddddii
 * iieeiiiiii
 * arguments: 2 5 7 1 3 1 widget valid
 * board: 10x8
 * bbbbbbbbbi
 * eeeeecciii
 * ---------a
 * ---------a
 * ---------a
 * --zzzzzzzi
 * iiddddddii
 * iiffiiiiii
 *
 *
 * This represents a Workspace boards and a dragged widget that wants to be dropped on the
 * workspace. The endBoard represents the result from such drag
 * The first board is the startBoard, the arguments are as follow: cellX, cellY, widget spanX,
 * widget spanY, minimum spanX, minimum spanX, type of object being drag (icon, widget, folder ),
 * if the resulting board is a valid solution or not reorder was found.
 *
 * For more information on how to read the board please go to the text file
 * reorder_algorithm_test_cases
 */
public class ReorderAlgorithmUnitTestCase {

    CellLayoutBoard startBoard;

    int x, y, spanX, spanY, minSpanX, minSpanY;
    String type;
    boolean isValidSolution;
    CellLayoutBoard endBoard;

    public static ReorderAlgorithmUnitTestCase readNextCase(
            Iterator<CellLayoutTestCaseReader.TestSection> sections) {
        ReorderAlgorithmUnitTestCase testCase = new ReorderAlgorithmUnitTestCase();
        CellLayoutTestCaseReader.Board startBoard =
                (CellLayoutTestCaseReader.Board) sections.next();
        testCase.startBoard = CellLayoutBoard.boardFromString(startBoard.board);
        CellLayoutTestCaseReader.Arguments arguments =
                (CellLayoutTestCaseReader.Arguments) sections.next();
        testCase.x = Integer.parseInt(arguments.arguments[0]);
        testCase.y = Integer.parseInt(arguments.arguments[1]);
        testCase.spanX = Integer.parseInt(arguments.arguments[2]);
        testCase.spanY = Integer.parseInt(arguments.arguments[3]);
        testCase.minSpanX = Integer.parseInt(arguments.arguments[4]);
        testCase.minSpanY = Integer.parseInt(arguments.arguments[5]);
        testCase.type = arguments.arguments[6];
        testCase.isValidSolution = arguments.arguments[7].compareTo("valid") == 0;

        CellLayoutTestCaseReader.Board endBoard = (CellLayoutTestCaseReader.Board) sections.next();
        testCase.endBoard = CellLayoutBoard.boardFromString(endBoard.board);
        return testCase;
    }

    public CellLayoutBoard getStartBoard() {
        return startBoard;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getSpanX() {
        return spanX;
    }

    public void setSpanX(int spanX) {
        this.spanX = spanX;
    }

    public int getSpanY() {
        return spanY;
    }

    public void setSpanY(int spanY) {
        this.spanY = spanY;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isValidSolution() {
        return isValidSolution;
    }

    public void setValidSolution(boolean validSolution) {
        isValidSolution = validSolution;
    }

    public CellLayoutBoard getEndBoard() {
        return endBoard;
    }

    public void setEndBoard(CellLayoutBoard endBoard) {
        this.endBoard = endBoard;
    }

    @Override
    public String toString() {
        String valid = isValidSolution ? "valid" : "invalid";
        return startBoard + "arguments: " + x + " " + y + " " + spanX + " " + spanY + " " + minSpanX
                + " " + minSpanY + " " + type + " " + valid + "\n" + endBoard;
    }
}
