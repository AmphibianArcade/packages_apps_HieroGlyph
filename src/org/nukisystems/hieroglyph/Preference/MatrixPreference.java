package org.nukisystems.hieroglyph.Preference;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Iterator;

import org.nukisystems.hieroglyph.R;
import org.nukisystems.hieroglyph.Constants.Constants;
import org.nukisystems.hieroglyph.Utils.CSVUtils;
import org.nukisystems.hieroglyph.Utils.MatrixUtils;
import org.nukisystems.hieroglyph.Utils.ResourceUtils;
import org.nukisystems.hieroglyph.View.MatrixDisplayView;

public class MatrixPreference extends Preference {

    private final String TAG = "MatrixPreference";
    private final boolean DEBUG = true;

    private String animationName;
    private boolean animationTerminated;
    private volatile boolean animationPaused = true;
    private boolean animationReversed = false;
    private int animationTimeBetween = 0;
    private boolean alternateOnce = false;

    private View mRootView;
    private volatile MatrixDisplayView matrixDisplay;

    public MatrixPreference(Context context) {
        super(context);
        setLayout(R.layout.glyph_settings_preview);
    }
    public MatrixPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayout(R.layout.glyph_settings_preview);
    }

    private void setLayout(int layoutResource) {
        setLayoutResource(R.layout.glyph_settings_preview_frame);
        mRootView = LayoutInflater.from(getContext())
                .inflate(layoutResource, null, false);
        setShouldDisableView(false);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        holder.itemView.setFocusable(isSelectable());
        holder.itemView.setClickable(isSelectable());

        FrameLayout layout = (FrameLayout) holder.itemView;
        layout.removeAllViews();
        ViewGroup parent = (ViewGroup) mRootView.getParent();
        if (parent != null) {
            parent.removeView(mRootView);
        }
        layout.addView(mRootView);

        matrixDisplay = mRootView.findViewById(R.id.matrixDisplay);
        matrixDisplay.setCols(ResourceUtils.getIntArray(Constants.Res.ARRAY_MATRIX_ROWS).length);
        matrixDisplay.setValidCountPerRow(ResourceUtils.getIntArray(Constants.Res.ARRAY_MATRIX_ROWS));
    }

    @Override
    public void onAttached() {
        super.onAttached();
        if (DEBUG) Log.d(TAG, "onAttached");
        startAnimation();
    }

    @Override
    public void onDetached() {
        super.onDetached();
        if (DEBUG) Log.d(TAG, "onDetached");
        stopAnimation();
    }

    private void startAnimation() {
        animationThread.start();
    }

    private void stopAnimation() {
        animationTerminated = true;
        animationThread.interrupt();
    }

    public void updateAnimation(boolean play, String name, int time, boolean reverse, boolean shouldAlternate) {
        alternateOnce = shouldAlternate;
        animationTimeBetween = time;
        animationName = name;
        animationPaused = !play;
        animationReversed = reverse;
        animationThread.interrupt();
    }

    public void updateAnimation(boolean play, int time, boolean reverse) {
        updateAnimation(play, animationName, time, reverse, false);
    }

    public void updateAnimation(boolean play, String name, int time, boolean reverse) {
        updateAnimation(play, name, time, reverse, false);
    }

    public void updateAnimation(boolean play, String name, int time) {
        updateAnimation(play, name, time, false, false);
    }

    public void updateAnimation(boolean play) {
        updateAnimation(play, animationName, animationTimeBetween, animationReversed, alternateOnce);
    }


    Thread animationThread = new Thread() {
        @Override
        public void run() {
            while (!animationTerminated) {
                while (animationPaused) {
                    Thread.onSpinWait();
                }
                String playMode = (animationReversed) ? "reverse" : "forwards";
                if (DEBUG) Log.d(TAG, "Displaying animation | name: " + animationName + " mode: " + playMode);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        ResourceUtils.getAnimation(animationName)))) {
                    if (alternateOnce) animationReversed = false;
                    Iterator<String> it = CSVUtils.iterateCsvLines(reader, animationReversed, alternateOnce);
                    while (it.hasNext()) {
                       String[] split = it.next().split(",");
                        if (split.length == matrixDisplay.countValidCells()) {
                            int[] frame = new int[split.length];  
                            for (int i = 0; i < split.length; i++) {
                                frame[i] = Integer.parseInt(split[i]);
                            }
                            matrixDisplay.post(() -> matrixDisplay.setFrameFromFlatValues(frame));
                        } else {
                            Log.w(TAG, "CSV line length mismatch: expected " + matrixDisplay.countValidCells() + ", got " + split.length);
                        }
                        Thread.sleep(16, 666000);
                    }
                    Thread.sleep(animationTimeBetween);
                } catch (Exception e) {
                    if (DEBUG) Log.d(TAG, "Exception while displaying animation | name: " + animationName + " | exception: " + e);
                } finally {
                    if (animationPaused && matrixDisplay != null) {
                        int[] blank = new int[matrixDisplay.countValidCells()];
                        matrixDisplay.post(() -> matrixDisplay.setFrameFromFlatValues((blank)));
                    }
                }
            }
        }
    };
}
