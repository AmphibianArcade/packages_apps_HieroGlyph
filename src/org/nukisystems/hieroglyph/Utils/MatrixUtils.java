package org.nukisystems.hieroglyph.Utils;

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

}
