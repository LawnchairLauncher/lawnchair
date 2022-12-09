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


public class CellLayoutBoard {

    public static class CellType {
        // The cells marked by this will be filled by 1x1 widgets and will be ignored when
        // validating
        public static final char IGNORE = 'x';
        // The cells marked by this will be filled by app icons
        public static final char ICON = 'i';
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

        @Override
        public String toString() {
            return "WidgetRect type = " + mType + " bounds = " + mBounds.toString();
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

    static final int INFINITE = 99999;

    char[][] mWidget = new char[30][30];

    List<WidgetRect> mWidgetsRects = new ArrayList<>();
    Map<Character, WidgetRect> mWidgetsMap = new HashMap<>();

    List<IconPoint> mIconPoints = new ArrayList<>();
    Map<Character, IconPoint> mIconsMap = new HashMap<>();

    Point mMain = new Point();

    CellLayoutBoard() {
        for (int x = 0; x < mWidget.length; x++) {
            for (int y = 0; y < mWidget[0].length; y++) {
                mWidget[x][y] = CellType.EMPTY;
            }
        }
    }

    public List<WidgetRect> getWidgets() {
        return mWidgetsRects;
    }

    public List<IconPoint> getIcons() {
        return mIconPoints;
    }

    public Point getMain() {
        return mMain;
    }

    public WidgetRect getWidgetRect(char c) {
        return mWidgetsMap.get(c);
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

    public static boolean isWidget(char type) {
        return type != CellType.ICON && type != CellType.EMPTY;
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

    public static CellLayoutBoard boardFromString(String boardStr) {
        String[] lines = boardStr.split("\n");
        CellLayoutBoard board = new CellLayoutBoard();

        for (int y = 0; y < lines.length; y++) {
            String line = lines[y];
            for (int x = 0; x < line.length(); x++) {
                char c = line.charAt(x);
                if (c == CellType.MAIN_WIDGET) {
                    board.mMain = new Point(x, y);
                }
                if (c != CellType.EMPTY) {
                    board.mWidget[x][y] = line.charAt(x);
                }
            }
        }
        board.mWidgetsRects = getRects(board.mWidget);
        board.mWidgetsRects.forEach(
                widgetRect -> board.mWidgetsMap.put(widgetRect.mType, widgetRect));
        board.mIconPoints = getIconPoints(board.mWidget);
        return board;
    }
}
