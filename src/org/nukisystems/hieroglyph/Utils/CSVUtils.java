package org.nukisystems.hieroglyph.Utils;

import static org.nukisystems.hieroglyph.Utils.InterfaceUtils.showToast;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.nukisystems.hieroglyph.Constants.Constants;

import org.nukisystems.hieroglyph.R;

public class CSVUtils {

    private final String TAG = this.getClass().getSimpleName();

    public static void validateAnimation(String csv) throws Exception {
        validateAnimation(csv, true, true);
    }

    public static List<String> validateAnimationWithList(String csv) {
        List<String> errorList = new ArrayList<>();
        try {
            errorList = validateAnimation(csv, false, false);
        } catch (Exception ignored) {

        }

        return errorList;
    }

    private static List<String> validateAnimation(String csv,
                                                 boolean shouldThrow,
                                                 boolean shouldRecover) {
        int currentLine = 1;
        int requiredFrameLength = 0;
        int currentFrameLength;

        List<String> errorList = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new StringReader(csv))) {
            Iterator<String> it = reader.lines().iterator();
            String frame;
            while (it.hasNext()) {
                frame = it.next();
                if (frame != null) {
                    if (shouldRecover) {
                        frame = sanitizeCsvLine(frame);
                    } else {
                        frame = frame.endsWith(",") ? frame.substring(0, frame.length() - 1) : frame;
                    }
                    if (currentLine == 1) {
                        requiredFrameLength = getFrameLength(frame);
                        if (requiredFrameLength == 0) {
                            String error = "Frame length invalid at line 1";
                            errorList.add(error);
                            if (shouldThrow) throw new IllegalStateException(error);
                        }
                    } else {
                        currentFrameLength = getFrameLength(frame);
                        if (currentFrameLength != requiredFrameLength) {
                            String error = "Frame length invalid at line: " + currentLine +
                                    ". Expected " + requiredFrameLength + ", Found "
                                    + currentFrameLength;
                            errorList.add(error);
                            if (shouldThrow) throw new IllegalArgumentException(error);
                        }
                    }

                    try {
                        errorList.addAll(validateFrameBrightness(frame, shouldThrow));
                    } catch (IllegalArgumentException e) {
                        String error = "Failed to parse frame at line "
                                + currentLine + "(" + e.getMessage() + ")";
                        if (shouldThrow) throw new IllegalArgumentException(error);
                    }
                }
            currentLine++;
            }
        } catch (Exception e) {
           if (shouldThrow) throw new IllegalArgumentException("CSV is invalid at line "
                   + currentLine, e);
        }
        return errorList;
    }

    public static int getFrameLength(String frame) {
        return frame.split(",").length;
    }

    public static List<String> validateFrameBrightness(String frame, boolean shouldThrow)
            throws IllegalArgumentException {
        int max = Constants.MAX_PATTERN_BRIGHTNESS;
        int min = 0;
        int idx = 1;

        List<String> errorList = new ArrayList<>();

        for (String brightness : frame.split(",")) {
            int value = Integer.parseInt(brightness);
            if (value > max || value < min) {
                String error = "Brightness value: " + value
                        + " is out of range at index " + idx;
                errorList.add(error);
                if (shouldThrow) {
                    throw new IllegalArgumentException(error);
                }
            }
            idx++;
        }
        return errorList;
    }

    public static void validateFrameBrightness(String frame) throws IllegalArgumentException {
        validateFrameBrightness(frame, true);
    }

    public static int[] scale12BitTo8BitByte(int[] values12Bit) {
        int[] result = new int[values12Bit.length];
        for (int i = 0; i < values12Bit.length; i++) {
            int clamped = Math.clamp(values12Bit[i], 0, 4095);
            result[i] = (clamped * 255 + 2047) / 4095;
        }
        return result;
    }


    public static boolean checkUserAnimation(String animationName) {
        try {
            String csv = new String(ResourceUtils.getAnimation(animationName).readAllBytes(),
                    StandardCharsets.UTF_8);
            validateAnimation(csv);
            if (!isCompatible(csv)) {
                showToast(R.string.glyph_settings_user_animation_incompatible);
                return false;
            }
        } catch (Exception e) {
            showToast(R.string.glyph_settings_user_animation_invalid);
            Log.w(CSVUtils.class.getSimpleName(), e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    static boolean allSame(int[] arr, int start, int end) {
        int first = arr[start];
        for (int i = start + 1; i <= end; i++) {
            if (arr[i] != first) return false;
        }
        return true;
    }

    public static String getDevice(String csv) {

        int frameLength = getFrameLength(sanitizeCsvLine(csv.lines().findFirst().orElse("")));

            switch (frameLength) {
                case 137, (13 * 13)  -> {
                    return Constants.Device.PHONE4A_PRO;
                }
                case 489, (25 * 25) -> {
                    return Constants.Device.PHONE3;
                }
            }
        return "";
    }

    public static boolean isCompatible(String csv) {
        return getDevice(csv).equals(Constants.Device.getDevice());
    }

    public static float[] buildPatternArray(float[]... arrays) {
        int totalLength = 0;
        for (float[] arr : arrays) {
            totalLength += arr.length;
        }

        float[] result = new float[totalLength];
        int pos = 0;
        for (float[] arr : arrays) {
            System.arraycopy(arr, 0, result, pos, arr.length);
            pos += arr.length;
        }
        return result;
    }

    public static int[] buildPatternArray(int[]... arrays) {
        int totalLength = 0;
        for (int[] arr : arrays) {
            totalLength += arr.length;
        }

        int[] result = new int[totalLength];
        int pos = 0;
        for (int[] arr : arrays) {
            System.arraycopy(arr, 0, result, pos, arr.length);
            pos += arr.length;
        }
        return result;
    }

    public static String sanitizeCsvLine(String line) {
        line = line.replaceAll("-\\d+", "0");
        line = line.replaceAll("[^0-9,\n]", "");
        line = line.endsWith(",") ? line.substring(0, line.length() - 1) : line;
        return line;
    }

    public static int getLineCount(String csv) {
        return csv.split("\n", -1).length;
    }

    public static double calcAnimPlaytime(int lineCount) {
        double frameInterval = 1000.0 / 60;
        return lineCount * frameInterval;
    }

    public static double calcAnimPlaytime(String csv) {
        double frameInterval = 1000.0 / 60;
        return getLineCount(csv) * frameInterval;
    }

    public static String toReadableDuration(long ms) {
        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.isEmpty()) sb.append(seconds).append("s");

        return sb.toString().trim();
    }

    public static String toReadableDuration(double ms) {
        return toReadableDuration((long) ms);
    }

    public static Iterator<String> iterateCsvLines(BufferedReader reader)
            throws Exception {

        return iterateCsvLines(reader, false, false);
    }

    public static Iterator<String> iterateCsvLines(BufferedReader reader, boolean reverse)
            throws Exception {

        return iterateCsvLines(reader, reverse, false);
    }

    public static Iterator<String> iterateCsvLines(BufferedReader reader, boolean reverse,
                                                   boolean alternate) throws Exception {
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            line = sanitizeCsvLine(line);
            try {
                line = MatrixUtils.trimToValidFrame(line);
            } catch (Exception e) {
                Log.d("CSVUtils", e.getMessage());
                throw new IllegalArgumentException(e);
            }
            lines.add(line);
        }
        if (alternate) {
            List<String> reversed = new ArrayList<>(lines);
            Collections.reverse(reversed);
            lines.addAll(reversed);
            return lines.iterator();
        }
        if (reverse) Collections.reverse(lines);
        return lines.iterator();
    }

    public static int[] reverseFrameArray(int[] array) {
        int[] copy = new int[array.length];
        for (int i = 0; i < array.length; i++) {
            copy[i] = array[array.length - 1 - i];
        }
        return copy;
    }

    public static float[] reverseFrameArray(float[] array) {
        float[] copy = new float[array.length];
        for (int i = 0; i < array.length; i++) {
            copy[i] = array[array.length - 1 - i];
        }
        return copy;
    }

    public static class Holder {

        public static class oggMeta {

            private static Map<String, String> map;

            public static void setMap(Map<String, String> m) {
                map = m;
            }

            public static Map<String, String> getMap() {
                return map;
            }

            public static void clear() {
                map = null;
            }
        }

        public static class Call {
            private static String csv;

            public static void setCsv(String c) {
                csv = c;
            }

            public static boolean isAvailable() {
                return (csv != null && !csv.isEmpty());
            }

            public static String getCsv() {
                return csv;
            }

            public static void clear() {
                csv = null;
            }
        }

        public static class Notification {
            private static String csv;
            public static void setCsv(String c) {
                csv = c ;
            }

            public static boolean isAvailable() {
                return (csv != null && !csv.isEmpty());
            }

            public static String getCsv() {
                return csv;
            }

            public static void clear() {
                csv = null;
            }
        }

    }

}
