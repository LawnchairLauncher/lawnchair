/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.graphics.Point;
import android.graphics.Rect;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;


public class CellLayoutBoard implements Comparable<CellLayoutBoard> {

    private boolean intersects(Rect r1, Rect r2) {
        // If one rectangle is on left side of other
        if (r1.left > r2.right || r2.left > r1.right) {
            return false;
        }

        // If one rectangle is above other
        if (r1.bottom > r2.top || r2.bottom > r1.top) {
            return false;
        }

        return true;
    }

    private boolean overlapsWithIgnored(Set<Rect> ignoredRectangles, Rect rect) {
        for (Rect ignoredRect : ignoredRectangles) {
            // Using the built in intersects doesn't work because it doesn't account for area 0
            if (intersects(ignoredRect, rect)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int compareTo(CellLayoutBoard cellLayoutBoard) {
        // to be equal they need to have the same number of widgets and the same dimensions
        // their order can be different
        Set<Rect> widgetsSet = new HashSet<>();
        Set<Rect> ignoredRectangles = new HashSet<>();
        for (WidgetRect rect : mWidgetsRects) {
            if (rect.shouldIgnore()) {
                ignoredRectangles.add(rect.mBounds);
            } else {
                widgetsSet.add(rect.mBounds);
            }
        }
        for (WidgetRect rect : cellLayoutBoard.mWidgetsRects) {
            // ignore rectangles overlapping with the area marked by x
            if (overlapsWithIgnored(ignoredRectangles, rect.mBounds)) {
                continue;
            }
            if (!widgetsSet.contains(rect.mBounds)) {
                return -1;
            }
            widgetsSet.remove(rect.mBounds);
        }
        if (!widgetsSet.isEmpty()) {
            return 1;
        }

        // to be equal they need to have the same number of icons their order can be different
        Set<Point> iconsSet = new HashSet<>();
        mIconPoints.forEach(icon -> iconsSet.add(icon.getCoord()));
        for (IconPoint icon : cellLayoutBoard.mIconPoints) {
            if (!iconsSet.contains(icon.getCoord())) {
                return -1;
            }
            iconsSet.remove(icon.getCoord());
        }
        if (!iconsSet.isEmpty()) {
            return 1;
        }
        return 0;
    }

    public static class CellType {
        // The cells marked by this will be filled by 1x1 widgets and will be ignored when
        // validating
        public static final char IGNORE = 'x';
        // The cells marked by this will be filled by app icons
        public static final char ICON = 'i';
        // The cells marked by FOLDER will be filled by folders with 27 app icons inside
        public static final char FOLDER = 'Z';
        // Empty space
        public static final char EMPTY = '-';
        // Widget that will be saved as "main widget" for easier retrieval
        public static final char MAIN_WIDGET = 'm';
        // Everything else will be consider a widget
    }

    public static class WidgetRect {
        public char mType;
        public Rect mBounds;

        WidgetRect(char type, Rect bounds) {
            this.mType = type;
            this.mBounds = bounds;
        }

        int getSpanX() {
            return mBounds.right - mBounds.left + 1;
        }

        int getSpanY() {
            return mBounds.top - mBounds.bottom + 1;
        }

        int getCellX() {
            return mBounds.left;
        }

        int getCellY() {
            return mBounds.bottom;
        }

        boolean shouldIgnore() {
            return this.mType == CellType.IGNORE;
        }

        boolean contains(int x, int y) {
            return mBounds.contains(x, y);
        }

        @Override
        public String toString() {
            return "WidgetRect type = " + mType + " x = " + getCellX() + " | y " + getCellY()
                    + " xs = " + getSpanX() + " ys = " + getSpanY();
        }
    }

    public static class IconPoint {
        public Point coord;
        public char mType;

        public IconPoint(Point coord, char type) {
            this.coord = coord;
            mType = type;
        }

        public char getType() {
            return mType;
        }

        public void setType(char type) {
            mType = type;
        }

        public Point getCoord() {
            return coord;
        }

        public void setCoord(Point coord) {
            this.coord = coord;
        }
    }

    public static class FolderPoint {
        public Point coord;
        public char mType;

        public FolderPoint(Point coord, char type) {
            this.coord = coord;
            mType = type;
        }

        /**
         * [A-Z]: Represents a folder and number of icons in the folder is represented by
         * the order of letter in the alphabet, A=2, B=3, C=4 ... etc.
         */
        public int getNumberIconsInside() {
            return (mType - 'A') + 2;
        }
    }


    private HashSet<Character> mUsedWidgetTypes = new HashSet<>();

    static final int INFINITE = 99999;

    char[][] mWidget = new char[30][30];

    List<WidgetRect> mWidgetsRects = new ArrayList<>();
    Map<Character, WidgetRect> mWidgetsMap = new HashMap<>();

    List<IconPoint> mIconPoints = new ArrayList<>();
    List<FolderPoint> mFolderPoints = new ArrayList<>();

    WidgetRect mMain = null;

    int mWidth, mHeight;

    CellLayoutBoard() {
        for (int x = 0; x < mWidget.length; x++) {
            for (int y = 0; y < mWidget[0].length; y++) {
                mWidget[x][y] = CellType.EMPTY;
            }
        }
    }

    CellLayoutBoard(int width, int height) {
        mWidget = new char[width][height];
        this.mWidth = width;
        this.mHeight = height;
        for (int x = 0; x < mWidget.length; x++) {
            for (int y = 0; y < mWidget[0].length; y++) {
                mWidget[x][y] = CellType.EMPTY;
            }
        }
    }

    public boolean pointInsideRect(int x, int y, WidgetRect rect) {
        Boolean isXInRect = x >= rect.getCellX() && x < rect.getCellX() + rect.getSpanX();
        Boolean isYInRect = y >= rect.getCellY() && y < rect.getCellY() + rect.getSpanY();
        return isXInRect && isYInRect;
    }

    public WidgetRect getWidgetAt(int x, int y) {
        return mWidgetsRects.stream()
                .filter(widgetRect -> pointInsideRect(x, y, widgetRect)).findFirst().orElse(null);
    }

    public List<WidgetRect> getWidgets() {
        return mWidgetsRects;
    }

    public List<IconPoint> getIcons() {
        return mIconPoints;
    }

    public List<FolderPoint> getFolders() {
        return mFolderPoints;
    }

    public WidgetRect getMain() {
        return mMain;
    }

    public WidgetRect getWidgetRect(char c) {
        return mWidgetsMap.get(c);
    }

    private void removeWidgetFromBoard(WidgetRect widget) {
        for (int xi = widget.mBounds.left; xi < widget.mBounds.right; xi++) {
            for (int yi = widget.mBounds.top; yi < widget.mBounds.bottom; yi++) {
                mWidget[xi][yi] = '-';
            }
        }
    }

    private void removeOverlappingItems(Rect rect) {
        // Remove overlapping widgets and remove them from the board
        mWidgetsRects = mWidgetsRects.stream().filter(widget -> {
            if (rect.intersect(widget.mBounds)) {
                removeWidgetFromBoard(widget);
                return false;
            }
            return true;
        }).collect(Collectors.toList());
        // Remove overlapping icons and remove them from the board
        mIconPoints = mIconPoints.stream().filter(iconPoint -> {
            int x = iconPoint.coord.x;
            int y = iconPoint.coord.y;
            if (rect.contains(x, y)) {
                mWidget[x][y] = '-';
                return false;
            }
            return true;
        }).collect(Collectors.toList());

        // Remove overlapping folders and remove them from the board
        mFolderPoints = mFolderPoints.stream().filter(folderPoint -> {
            int x = folderPoint.coord.x;
            int y = folderPoint.coord.y;
            if (rect.contains(x, y)) {
                mWidget[x][y] = '-';
                return false;
            }
            return true;
        }).collect(Collectors.toList());
    }

    private void removeOverlappingItems(Point p) {
        // Remove overlapping widgets and remove them from the board
        mWidgetsRects = mWidgetsRects.stream().filter(widget -> {
            if (widget.mBounds.contains(p.x, p.y)) {
                removeWidgetFromBoard(widget);
                return false;
            }
            return true;
        }).collect(Collectors.toList());
        // Remove overlapping icons and remove them from the board
        mIconPoints = mIconPoints.stream().filter(iconPoint -> {
            int x = iconPoint.coord.x;
            int y = iconPoint.coord.y;
            if (p.x == x && p.y == y) {
                mWidget[x][y] = '-';
                return false;
            }
            return true;
        }).collect(Collectors.toList());

        // Remove overlapping folders and remove them from the board
        mFolderPoints = mFolderPoints.stream().filter(folderPoint -> {
            int x = folderPoint.coord.x;
            int y = folderPoint.coord.y;
            if (p.x == x && p.y == y) {
                mWidget[x][y] = '-';
                return false;
            }
            return true;
        }).collect(Collectors.toList());
    }

    private char getNextWidgetType() {
        for (char type = 'a'; type <= 'z'; type++) {
            if (type == 'i') continue;
            if (mUsedWidgetTypes.contains(type)) continue;
            mUsedWidgetTypes.add(type);
            return type;
        }
        return 'z';
    }

    public void addWidget(int x, int y, int spanX, int spanY, char type) {
        Rect rect = new Rect(x, y + spanY - 1, x + spanX - 1, y);
        removeOverlappingItems(rect);
        WidgetRect widgetRect = new WidgetRect(type, rect);
        mWidgetsRects.add(widgetRect);
        for (int xi = rect.left; xi < rect.right + 1; xi++) {
            for (int yi = rect.bottom; yi < rect.top + 1; yi++) {
                mWidget[xi][yi] = type;
            }
        }
    }

    public void addWidget(int x, int y, int spanX, int spanY) {
        addWidget(x, y, spanX, spanY, getNextWidgetType());
    }

    public void addIcon(int x, int y) {
        Point iconCoord = new Point(x, y);
        removeOverlappingItems(iconCoord);
        mIconPoints.add(new IconPoint(iconCoord, CellType.ICON));
        mWidget[x][y] = 'i';
    }

    public static WidgetRect getWidgetRect(int x, int y, Set<Point> used, char[][] board) {
        char type = board[x][y];
        Queue<Point> search = new ArrayDeque<Point>();
        Point current = new Point(x, y);
        search.add(current);
        used.add(current);
        List<Point> neighbors = new ArrayList<>(List.of(
                new Point(-1, 0),
                new Point(0, -1),
                new Point(1, 0),
                new Point(0, 1))
        );
        Rect widgetRect = new Rect(INFINITE, -INFINITE, -INFINITE, INFINITE);
        while (!search.isEmpty()) {
            current = search.poll();
            widgetRect.top = Math.max(widgetRect.top, current.y);
            widgetRect.right = Math.max(widgetRect.right, current.x);
            widgetRect.bottom = Math.min(widgetRect.bottom, current.y);
            widgetRect.left = Math.min(widgetRect.left, current.x);
            for (Point p : neighbors) {
                Point next = new Point(current.x + p.x, current.y + p.y);
                if (next.x < 0 || next.x >= board.length) continue;
                if (next.y < 0 || next.y >= board[0].length) continue;
                if (board[next.x][next.y] == type && !used.contains(next)) {
                    used.add(next);
                    search.add(next);
                }
            }
        }
        return new WidgetRect(type, widgetRect);
    }

    public static boolean isFolder(char type) {
        return type >= 'A' && type <= 'Z';
    }

    public static boolean isWidget(char type) {
        return type != CellType.ICON && type != CellType.EMPTY && (type >= 'a' && type <= 'z');
    }

    public static boolean isIcon(char type) {
        return type == CellType.ICON;
    }

    private static List<WidgetRect> getRects(char[][] board) {
        Set<Point> used = new HashSet<>();
        List<WidgetRect> widgetsRects = new ArrayList<>();
        for (int x = 0; x < board.length; x++) {
            for (int y = 0; y < board[0].length; y++) {
                if (!used.contains(new Point(x, y)) && isWidget(board[x][y])) {
                    widgetsRects.add(getWidgetRect(x, y, used, board));
                }
            }
        }
        return widgetsRects;
    }

    private static List<IconPoint> getIconPoints(char[][] board) {
        List<IconPoint> iconPoints = new ArrayList<>();
        for (int x = 0; x < board.length; x++) {
            for (int y = 0; y < board[0].length; y++) {
                if (isIcon(board[x][y])) {
                    iconPoints.add(new IconPoint(new Point(x, y), board[x][y]));
                }
            }
        }
        return iconPoints;
    }

    private static List<FolderPoint> getFolderPoints(char[][] board) {
        List<FolderPoint> folderPoints = new ArrayList<>();
        for (int x = 0; x < board.length; x++) {
            for (int y = 0; y < board[0].length; y++) {
                if (isFolder(board[x][y])) {
                    folderPoints.add(new FolderPoint(new Point(x, y), board[x][y]));
                }
            }
        }
        return folderPoints;
    }

    public static WidgetRect getMainFromList(List<CellLayoutBoard> boards) {
        for (CellLayoutBoard board : boards) {
            WidgetRect main = board.getMain();
            if (main != null) {
                return main;
            }
        }
        return null;
    }

    public static WidgetRect getWidgetIn(List<CellLayoutBoard> boards, int x, int y) {
        for (CellLayoutBoard board : boards) {
            WidgetRect main = board.getWidgetAt(x, y);
            if (main != null) {
                return main;
            }
            x -= board.mWidth;
        }
        return null;
    }

    public static CellLayoutBoard boardFromString(String boardStr) {
        String[] lines = boardStr.split("\n");
        CellLayoutBoard board = new CellLayoutBoard();

        for (int y = 0; y < lines.length; y++) {
            String line = lines[y];
            for (int x = 0; x < line.length(); x++) {
                char c = line.charAt(x);
                if (c != CellType.EMPTY) {
                    board.mWidget[x][y] = line.charAt(x);
                }
            }
        }
        board.mHeight = lines.length;
        board.mWidth = lines[0].length();
        board.mWidgetsRects = getRects(board.mWidget);
        board.mWidgetsRects.forEach(widgetRect -> {
            if (widgetRect.mType == CellType.MAIN_WIDGET) {
                board.mMain = widgetRect;
            }
            board.mWidgetsMap.put(widgetRect.mType, widgetRect);
        });
        board.mIconPoints = getIconPoints(board.mWidget);
        board.mFolderPoints = getFolderPoints(board.mWidget);
        return board;
    }

    public String toString(int maxX, int maxY) {
        StringBuilder s = new StringBuilder();
        s.append("board: ");
        s.append(maxX);
        s.append("x");
        s.append(maxY);
        s.append("\n");
        maxX = Math.min(maxX, mWidget.length);
        maxY = Math.min(maxY, mWidget[0].length);
        for (int y = 0; y < maxY; y++) {
            for (int x = 0; x < maxX; x++) {
                s.append(mWidget[x][y]);
            }
            s.append('\n');
        }
        return s.toString();
    }

    @Override
    public String toString() {
        return toString(mWidth, mHeight);
    }

    public static List<CellLayoutBoard> boardListFromString(String boardsStr) {
        String[] lines = boardsStr.split("\n");
        ArrayList<String> individualBoards = new ArrayList<>();
        ArrayList<CellLayoutBoard> boards = new ArrayList<>();
        for (String line : lines) {
            String[] boardSegment = line.split("\\|");
            for (int i = 0; i < boardSegment.length; i++) {
                if (i >= individualBoards.size()) {
                    individualBoards.add(boardSegment[i]);
                } else {
                    individualBoards.set(i, individualBoards.get(i) + "\n" + boardSegment[i]);
                }
            }
        }
        for (String board : individualBoards) {
            boards.add(CellLayoutBoard.boardFromString(board));
        }
        return boards;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }
}
