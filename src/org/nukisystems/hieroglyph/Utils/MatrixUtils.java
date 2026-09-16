package org.nukisystems.hieroglyph.Utils;

import android.util.Log;

import org.nukisystems.hieroglyph.Constants.Constants;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class MatrixUtils {

    private static int gridSize = 0;
    private static int minFrameLength = 0;
    private static int maxFrameLength = 0;
    private static int[] matrixRows;

    private static final String TAG = MatrixUtils.class.getSimpleName();

    private ResourceUtils res;

    public static void init() {
        getMaxFrameLength();
        getMinFrameLength();
    }

    public static String trimToValidAnim(String csv) throws Exception {
        BufferedReader reader = new BufferedReader(new StringReader(csv));
        List<String> newLines = new ArrayList<>();

        Iterator<String> it = CSVUtils.iterateCsvLines(reader, false, false);
        while (it.hasNext()) {
            newLines.add(trimToValidFrame(it.next()));
        }

        return String.join("\n", newLines);
    }

    public static String trimToValidFrame(String line) throws Exception {
        String[] fields = line.split(",");
        int len = fields.length;

        if (len != getMaxFrameLength() && len != getMinFrameLength()) {
            throw new IllegalArgumentException(
                    "Expected length " + getMaxFrameLength() + " or " + getMinFrameLength() + ", found " + len);
        }

        if (len == getMinFrameLength()) {
            return line;
        }

        int[] rows = getMatrixRows();
        int gridSize = getGridSize();

        List<String> newFields = new ArrayList<>(getMinFrameLength());
        for (int i = 0; i < gridSize; i++) {
            int rowCount = rows[i];
            String[] section = Arrays.copyOfRange(fields, gridSize * i, gridSize * (i + 1));
            int from = (gridSize - rowCount) / 2;
            newFields.addAll(Arrays.asList(section).subList(from, from + rowCount));
        }

        if (newFields.size() != getMinFrameLength()) {
            throw new IllegalStateException(
                    "Trimmed length " + newFields.size() + " != expected " + getMinFrameLength());
        }

        return String.join(",", newFields);
    }

    public static int[] trimToValidFrame(int[] pattern) throws Exception {
        int len = pattern.length;

        if (len != getMaxFrameLength() && len != getMinFrameLength()) {
            throw new IllegalArgumentException(
                    "Expected length " + getMaxFrameLength() + " or " + getMinFrameLength() + ", found " + len);
        }

        if (len == getMinFrameLength()) {
            return pattern;
        }

        int[] rows = getMatrixRows();
        int gridSize = getGridSize();

        int[] newPattern = new int[getMinFrameLength()];
        int pos = 0;

        for (int i = 0; i < gridSize; i++) {
            int rowCount = rows[i];
            int from = (gridSize - rowCount) / 2;
            int start = gridSize * i + from;

            System.arraycopy(pattern, start, newPattern, pos, rowCount);
            pos += rowCount;
        }

        if (pos != getMinFrameLength()) {
            throw new IllegalStateException(
                    "Trimmed length " + pos + " != expected " + getMinFrameLength());
        }

        return newPattern;
    }

    private static int[] getMatrixRows() {
        if (matrixRows == null) {
            matrixRows = ResourceUtils.getIntArray(Constants.Res.INT_ARRAY_MATRIX_ROWS);
        }
        return matrixRows;
    }

    public static int getGridSize() {
        if (gridSize == 0) {
            gridSize = getMatrixRows().length;
        }
        return gridSize;
    }

    public static int getMaxFrameLength() {
        if (maxFrameLength == 0) maxFrameLength = (getGridSize() * getGridSize());
        return maxFrameLength;
    }

    public static int getMinFrameLength() {
        if (minFrameLength == 0) {
            minFrameLength = Arrays.stream(getMatrixRows()).sum();
        }
        return minFrameLength;
    }

    public static class Row {

        public static int[] fillRows(int[] origFrame, int startIdx, int count, int brightness) {
            int rowLength = getGridSize();
            if (origFrame.length > getMaxFrameLength() || origFrame.length < getMaxFrameLength()) {
                Log.w(TAG, "Incorrect frame length, expected max: " + getMaxFrameLength());
                return origFrame;
            } else if (startIdx > rowLength || startIdx < 1) {
                Log.w(TAG,
                        "Invalid start index, must be within range: index >= 1 <= gridSize, found "
                                + startIdx
                );
                return origFrame;
            } else if (count < 0 || ((startIdx - 1) + count) > getGridSize()) {
                Log.w(TAG,
                        "Invalid count from start, must be within range: start + rowLength < count >= 0, found "
                                + count
                );
                return origFrame;
            } else if (brightness < 0 || brightness > 255) {
                Log.w(TAG,
                        "Invalid brightness value, must be within range: 0 <= brightness >= 255, found "
                                + brightness
                );
                return origFrame;
            }


            int[] newFrame = origFrame.clone();
            if (count == 0) return origFrame;

            for (int row = startIdx; row < startIdx + count; row++) {
                int rowStart = (row - 1) * rowLength;
                for (int i = rowStart; i < rowStart + rowLength; i++) {
                    if (newFrame[i] != brightness) newFrame[i] = brightness;
                }
            }

            return newFrame;

        }

        public static int[] fillRow(int[] origFrame, int idx, int brightness) {
            return fillRows(origFrame, idx, 1, brightness);
        }

        public static int[] clearRow(int[] origFrame, int idx) {
            return clearRows(origFrame, idx, 1);
        }

        public static int[] clearRows(int[] origFrame, int startIdx, int count) {
            return fillRows(origFrame, startIdx, count, 0);
        }
    }

    public static class Rotate {
        public static int[] cw90(int[] flat, int n) {
            int[] rotated = new int[flat.length];
            for (int r = 0; r < n; r++) {
                for (int c = 0; c < n; c++) {
                    rotated[r * n + c] = flat[(n - 1 - c) * n + r];
                }
            }
            return rotated;
        }

        public static int[] cw90(int[] flat) {
            return cw90(flat, getGridSize());
        }

        public static int[] flip(int[] flat, int n) { // 180deg
            int[] rotated = new int[flat.length];
            int last = flat.length - 1;
            for (int i = 0; i < flat.length; i++) {
                rotated[i] = flat[last - i];
            }
            return rotated;
        }

        public static int[] flip(int[] flat) {
            return flip(flat, getGridSize());
        }

        public static int[] ccw90(int[] flat, int n) {
            int[] rotated = new int[flat.length];
            for (int r = 0; r < n; r++) {
                for (int c = 0; c < n; c++) {
                    rotated[r * n + c] = flat[c * n + (n - 1 - r)];
                }
            }
            return rotated;
        }

        public static int[] ccw90(int[] flat) {
            return ccw90(flat, getGridSize());
        }
    }
    
    public static class Shape {

        private static int[] Checkerboard(boolean even, int brightness) {
            int[] pattern = new int[getMaxFrameLength()];

            for (int i = even ? 0 : 1; i < pattern.length; i += 2) {
                pattern[i] = brightness;
            }

            return pattern;
        }

        private static int[] Checkerboard(boolean even) {
            return Checkerboard(even, 255);
        }

        public static int[] checkerboardEven() {
            return Checkerboard(true);
        }

        public static int[] checkerboardEven(int brightness) {
            return Checkerboard(true, brightness);
        }

        public static int[] checkerboardOdd() {
            return Checkerboard(false);
        }

        public static int[] checkerboardOdd(int brightness) {
            return Checkerboard(false, brightness);
        }

        public static int[] Diamond(int radius, int brightness) {
            int[] pattern = new int[getGridSize() * getGridSize()];
            int centerX = getGridSize() / 2;
            int centerY = getGridSize() / 2;

            for (int row = 0; row < getGridSize(); row++) {
                for (int col = 0; col < getGridSize(); col++) {
                    int dist = Math.abs(col - centerX) + Math.abs(row - centerY);
                    pattern[row * getGridSize() + col] = (dist <= radius) ? brightness : 0;
                }
            }

            return pattern;
        }

        public static int[] Cross(int n, int thickness, int distance,
                int onValue, int offValue) {

            int[] grid = new int[n * n];
            Arrays.fill(grid, offValue);

            int center = n / 2;
            int halfThickness = thickness / 2;

            for (int r = 0; r < n; r++) {
                for (int c = 0; c < n; c++) {

                    int distMain = Math.abs(r - c);
                    int distAnti = Math.abs(r + c - (n - 1));

                    int centerDist = Math.max(
                            Math.abs(r - center),
                            Math.abs(c - center));

                    if (centerDist <= distance &&
                            (distMain <= halfThickness ||
                                    distAnti <= halfThickness)) {
                        grid[r * n + c] = onValue;
                    }
                }
            }

            return grid;
        }

        public static int[] Cross(int brightness, int length) {
            return Cross(getGridSize(), 1, length, brightness, 0);
        }

    }
}

