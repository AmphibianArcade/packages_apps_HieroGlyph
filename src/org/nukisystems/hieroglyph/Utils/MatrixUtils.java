package org.nukisystems.hieroglyph.Utils;

import android.util.Log;

import org.nukisystems.hieroglyph.Constants.Constants;
import org.nukisystems.hieroglyph.Data.CsvContent;

import java.util.Arrays;

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

    public static CsvContent trimToValidAnim(CsvContent csv) {
        return new CsvContent(trimToValidAnim(csv.toString()));
    }

    public static String trimToValidFrame(String csvLine) {
        String[] parts = csvLine.split(",");
        int[] pattern = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            pattern[i] = Integer.parseInt(parts[i].trim());
        }

        int[] trimmed = null;
        try {
            trimmed = trimToValidFrame(pattern);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < trimmed.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(trimmed[i]);
        }
        return sb.toString();
    }

    public static String trimToValidAnim(String csvLines) {
        String[] lines = csvLines.split("\\r?\\n");
        StringBuilder result = new StringBuilder();

        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            if (!result.isEmpty()) result.append("\n");
            result.append(trimToValidFrame(line));
        }

        return result.toString();
    }

    public static int[] trimToValidFrame(int[] pattern) {
        int len = pattern.length;

        if (len != getMaxFrameLength() && len != getMinFrameLength()) {
            Log.e(TAG, "Expected length " + getMaxFrameLength() + " or " + getMinFrameLength() + ", found " + len);
            return new int[getMinFrameLength()];
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
            Log.e(TAG, "Trimmed length " + pos + " != expected " + getMinFrameLength());
            return new int[getMinFrameLength()];
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

    public static class Volume {

        public enum Style {
            LINEAR(0),
            CHECKERBOARD(1),
            CHECKERBOARD_LINEAR(2),
            DIAMOND_FILL(3),
            RADIAL_CHECKERBOARD_FADE_OUT(4),
            RADIAL_OUTWARD(5),
            RADIAL_FADE_OUTWARD(6),
            RADIAL_INWARD(7),
            RADIAL_CHECKERBOARD_OUTWARD(8);

            private final int value;

            Style(int value) {
                this.value = value;
            }

            public int getValue() {
                return value;
            }

            public static Style fromInt(int value) throws IllegalArgumentException {
                for (Style style : values()) {
                    if (style.value == value) {
                        return style;
                    }
                }
                throw new IllegalArgumentException("Unknown value: " + value);
            }
        }

        public static int[] generateFrame(Style style, int level) {
            return generateFrame(style, level, 0);
        }

        public static int[] generateFrame(Style style, int level, int rotation) {
            int[] volumeMatrixFrame = new int[MatrixUtils.getMaxFrameLength()];
            switch (style) {
                case LINEAR -> { // Row/Column step (Linear)
                    int fillCount = Math.toIntExact(
                            Math.round((level * (double) getGridSize()) / 100D));
                    volumeMatrixFrame = Row.fill(
                            volumeMatrixFrame, 1, fillCount, Constants.MAX_PATTERN_BRIGHTNESS);
                    volumeMatrixFrame =
                            switch (rotation) {
                                case 1 -> Rotate.cw90(volumeMatrixFrame);
                                case 2 -> Rotate.flip(volumeMatrixFrame);
                                case 3 -> Rotate.ccw90(volumeMatrixFrame);
                                default -> volumeMatrixFrame;
                            };
                }

                case CHECKERBOARD -> { // Checkerboard brightness
                    int brightness = Math.toIntExact(
                            Math.round((level * (double) Constants.MAX_PATTERN_BRIGHTNESS / 10D)));
                    volumeMatrixFrame = Shape.checkerboardOdd(brightness);
                }
                case CHECKERBOARD_LINEAR -> { // Checkerboard row step
                    int fillCount =  Math.toIntExact(
                            Math.round((level * (double) getGridSize()) / 100D));
                    int[] newFrame = new int[volumeMatrixFrame.length];
                    for (int i = 1; i <= fillCount; i++) {
                        if (i % 2 == 0) {
                            newFrame = Row.fill(newFrame, i, 1,  Row.fillSingleEven());
                        } else {
                          newFrame = Row.fill(newFrame, i, 1, Row.fillSingleOdd());
                        }
                    }
                    volumeMatrixFrame = newFrame;
                    volumeMatrixFrame =
                            switch (rotation) {
                                case 1 -> Rotate.cw90(volumeMatrixFrame);
                                case 2 -> Rotate.flip(volumeMatrixFrame);
                                case 3 -> Rotate.ccw90(volumeMatrixFrame);
                                default -> volumeMatrixFrame;
                            };
                }
                case DIAMOND_FILL -> { // Diamond pattern fill
                    int scale = Math.toIntExact(
                            Math.round((level * (double) getGridSize() / 150D)));
                    volumeMatrixFrame = Shape.Diamond(scale, Constants.MAX_PATTERN_BRIGHTNESS);
                }
                case RADIAL_CHECKERBOARD_FADE_OUT -> { // Checkerboard brightness fade from center
                    volumeMatrixFrame = Mask.radialFadeOut(Shape.checkerboardOdd(), level);
                }
                case RADIAL_OUTWARD -> {
                    volumeMatrixFrame = new int[getMaxFrameLength()];
                    Arrays.fill(volumeMatrixFrame, Constants.MAX_PATTERN_BRIGHTNESS);
                    volumeMatrixFrame = Mask.radialClampOut(volumeMatrixFrame, level);
                }
                case RADIAL_FADE_OUTWARD -> {
                    volumeMatrixFrame = new int[getMaxFrameLength()];
                    Arrays.fill(volumeMatrixFrame, Constants.MAX_PATTERN_BRIGHTNESS);
                    volumeMatrixFrame = Mask.radialFadeOut(volumeMatrixFrame, level);
                }
                case RADIAL_INWARD -> {
                    volumeMatrixFrame = new int[getMaxFrameLength()];
                    Arrays.fill(volumeMatrixFrame, Constants.MAX_PATTERN_BRIGHTNESS);
                    volumeMatrixFrame = Mask.radialClampIn(volumeMatrixFrame, level);
                }
                case RADIAL_CHECKERBOARD_OUTWARD -> {
                    volumeMatrixFrame = Mask.radialClampOut(Shape.checkerboardOdd(), level);
                }
            }

            return volumeMatrixFrame;
        }
    }

    public static class Mask {
        public static int[] radialFadeOut(int[] frame, int level) {
            if (level >= 100) return frame.clone();

            int gridSize = getGridSize();
            int[] newFrame = new int[frame.length];

            double centerRow = (gridSize - 1) / 2.0;
            double centerCol = (gridSize - 1) / 2.0;
            double maxDist = Math.sqrt(centerRow * centerRow + centerCol * centerCol);

            double radius = (level / 100.0) * maxDist;

            for (int row = 0; row < gridSize; row++) {
                for (int col = 0; col < gridSize; col++) {
                    int idx = row * gridSize + col;
                    double dist = Math.sqrt(
                            Math.pow(row - centerRow, 2) + Math.pow(col - centerCol, 2));

                    double falloff = radius <= 0 ? 0.0 : Math.max(0.0, 1.0 - (dist / radius));

                    int masked = (int) Math.round(frame[idx] * falloff);
                    newFrame[idx] = Math.clamp(masked, 0, Constants.MAX_PATTERN_BRIGHTNESS);
                }
            }

            return newFrame;
        }

        public static int[] radialClampOut(int[] frame, int level) {
            if (level >= 100) return frame.clone();

            int gridSize = getGridSize();
            int[] newFrame = new int[frame.length];

            double centerRow = (gridSize - 1) / 2.0;
            double centerCol = (gridSize - 1) / 2.0;

            // Clamp to the edge instead of the corners.
            double maxDist = Math.min(centerRow, centerCol);

            double radius = (level / 100.0) * maxDist;

            for (int row = 0; row < gridSize; row++) {
                for (int col = 0; col < gridSize; col++) {
                    int idx = row * gridSize + col;

                    double dist = Math.sqrt(
                            Math.pow(row - centerRow, 2) +
                                    Math.pow(col - centerCol, 2));

                    // Solid mask: everything inside the radius is visible.
                    newFrame[idx] = dist <= radius
                            ? frame[idx]
                            : 0;
                }
            }

            return newFrame;
        }

        public static int[] radialClampIn(int[] frame, int level) {
            if (level >= 100) return frame.clone();

            int gridSize = getGridSize();
            int[] newFrame = new int[frame.length];

            double centerRow = (gridSize - 1) / 2.0;
            double centerCol = (gridSize - 1) / 2.0;

            // Clamp to the edge rather than the corners.
            double maxDist = Math.min(centerRow, centerCol);

            // At level 0, radius is maxDist.
            // At level 100, radius is 0.
            double radius = (1.0 - level / 100.0) * maxDist;

            for (int row = 0; row < gridSize; row++) {
                for (int col = 0; col < gridSize; col++) {
                    int idx = row * gridSize + col;

                    double dist = Math.sqrt(
                            Math.pow(row - centerRow, 2) +
                                    Math.pow(col - centerCol, 2));

                    // Outside the radius = visible.
                    double mask = dist >= radius ? 1.0 : 0.0;

                    int masked = (int) Math.round(frame[idx] * mask);
                    newFrame[idx] = Math.clamp(masked, 0, Constants.MAX_PATTERN_BRIGHTNESS);
                }
            }

            return newFrame;
        }
    }

    public static class Row {

        public static int[] fill(int[] origFrame, int startIdx, int count, int brightness) {
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
            } else if (brightness < 0 || brightness > Constants.MAX_PATTERN_BRIGHTNESS) {
                Log.w(TAG,
                        "Invalid brightness value, must be within range: 0 <= brightness >= 4095, found "
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

        public static int[] fill(int[] origFrame, int startIdx, int count, int[] values) {
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
            } else if (values == null || values.length != rowLength) {
                Log.w(TAG,
                        "Invalid values array, must have length equal to grid size: expected "
                                + rowLength + ", found " + (values == null ? "null" : values.length)
                );
                return origFrame;
            }

            for (int v : values) {
                if (v < 0 || v > Constants.MAX_PATTERN_BRIGHTNESS) {
                    Log.w(TAG,
                            "Invalid brightness value in values array, must be within range: 0 <= brightness >= 4095, found "
                                    + v
                    );
                    return origFrame;
                }
            }

            int[] newFrame = origFrame.clone();
            if (count == 0) return origFrame;

            for (int row = startIdx; row < startIdx + count; row++) {
                int rowStart = (row - 1) * rowLength;
                for (int i = 0; i < rowLength; i++) {
                    if (newFrame[rowStart + i] != values[i]) newFrame[rowStart + i] = values[i];
                }
            }

            return newFrame;
        }

        public static int[] fill(int[] origFrame, int count, int[] values) {
            return fill(origFrame, 1,  count, values);
        }

        public static int[] fillSingle(int length, int brightness, boolean even) {
            int[] rowArr = new int[length];

            for (int i = even ? 0 : 1; i < rowArr.length; i += 2) {
                rowArr[i] = brightness;
            }

            return rowArr;
        }

        public static int[] fillSingleOdd() {
            return fillSingle(getGridSize(), Constants.MAX_PATTERN_BRIGHTNESS, false);
        }

        public static int[] fillSingleEven() {
            return fillSingle(getGridSize(), Constants.MAX_PATTERN_BRIGHTNESS, true);
        }


        public static int[] fill(int[] origFrame, int idx, int brightness) {
            return fill(origFrame, idx, 1, brightness);
        }

        public static int[] clear(int[] origFrame, int idx) {
            return clear(origFrame, idx, 1);
        }

        public static int[] clear(int[] origFrame, int startIdx, int count) {
            return fill(origFrame, startIdx, count, 0);
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
            return Checkerboard(even, Constants.MAX_PATTERN_BRIGHTNESS);
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

