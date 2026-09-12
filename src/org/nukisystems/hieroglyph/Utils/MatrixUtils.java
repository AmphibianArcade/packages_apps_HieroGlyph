package org.nukisystems.hieroglyph.Utils;

import org.nukisystems.hieroglyph.Constants.Constants;
import org.nukisystems.hieroglyph.Utils.CSVUtils;
import org.nukisystems.hieroglyph.Utils.CSVUtils;
import org.nukisystems.hieroglyph.Utils.ResourceUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

    private static int[] getMatrixRows() {
        if (matrixRows == null) {
            matrixRows = ResourceUtils.getIntArray(Constants.Res.ARRAY_MATRIX_ROWS);
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
