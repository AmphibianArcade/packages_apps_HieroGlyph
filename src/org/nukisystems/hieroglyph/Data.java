package org.nukisystems.hieroglyph;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

public class Data {

    public static final class CsvContent implements CharSequence {

        private final String value;

        public CsvContent(String value) {
            if (value == null) {
                throw new IllegalArgumentException("CSV content cannot be null");
            }
            this.value = value;
        }

        public String raw() {
            return value;
        }


        @Override
        public int length() {
            return value.length();
        }

        @Override
        public char charAt(int index) {
            return value.charAt(index);
        }

        @NonNull
        @Override
        public CharSequence subSequence(int start, int end) {
            return value.subSequence(start, end);
        }

        @NonNull
        @Override
        public String toString() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CsvContent)) return false;
            return value.equals(((CsvContent) o).value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    public record GlyphToy (String title, Drawable icon, String summary, ComponentName intro,
                            boolean supportsAOD, boolean supportsLongPress) {

        public String getTitle() {
            return this.title;
        }

        public Drawable getDrawable() {
            return this.icon;
        }

        public String getSummary() {
            return this.summary;
        }

        public boolean hasIntroduction() {
            return this.intro != null;
        }
    }

    public static Map<ComponentName, GlyphToy> toyCache = new HashMap<>();

}
