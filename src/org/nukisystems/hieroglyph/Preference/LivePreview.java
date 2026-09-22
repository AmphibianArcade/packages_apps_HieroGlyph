package org.nukisystems.hieroglyph.Preference;

import android.content.Context;
import android.util.Log;

import org.nukisystems.hieroglyph.Data.CsvContent;
import org.nukisystems.hieroglyph.Manager.AnimationManager;
import org.nukisystems.hieroglyph.Manager.StatusManager;
import org.nukisystems.hieroglyph.Manager.StatusManager.GlyphOwner;
import org.nukisystems.hieroglyph.Manager.StatusManager.GlyphPriority;

public class LivePreview implements GlyphOwner {

    public LivePreview(Context ctx, Object owner) {
        this.ctx = ctx;
        this.owner = owner;
    }

    public interface StateListener {
        void onPreviewStopped();
    }

    private volatile boolean running = false;
    private Thread animationThread;
    private StateListener listener;

    private CsvContent csv;
    private String animationName;
    boolean shouldReverse;
    boolean shouldAlternate;

    private Context ctx;
    private Object owner;
    private final GlyphPriority priority = GlyphPriority.PREVIEW;


    public void setStateListener(StateListener listener) {
        this.listener = listener;
    }

    public void setAnimation(String animationName, boolean reverse, boolean alternate) {
        this.animationName = animationName;
        this.shouldReverse = reverse;
        this.shouldAlternate = alternate;
        this.csv = null;
    }

    public void setAnimation(String animationName) {
        setAnimation(animationName, false, false);
    }

    public void setAnimation(CsvContent animationCsv, boolean reverse, boolean alternate) {
        this.csv = animationCsv;
        this.shouldReverse = reverse;
        this.shouldAlternate = alternate;
        this.animationName = null;
    }

    public void setAnimation(CsvContent animationCsv, String animationName,
                             boolean reverse, boolean alternate) {
        this.csv = animationCsv;
        this.animationName = animationName;
        this.shouldReverse = reverse;
        this.shouldAlternate = alternate;
    }

    public void setAnimation(CsvContent animationCsv, String animationName) {
        this.csv = animationCsv;
        this.animationName = animationName;
        this.shouldReverse = false;
        this.shouldAlternate = false;
    }

    public void setAnimation(CsvContent animationCsv) {
        setAnimation(csv, null, false, false);
    }

    public void start() {
        boolean gotIt = StatusManager.acquire(owner, GlyphPriority.PREVIEW, this);
        if (!gotIt) {
            notifyStopped();
            return;
        }

        running = true;
        animationThread = new Thread(() -> {
            AnimationManager.Coordinator.get().stream(
                    ctx, owner, priority,
                    (csv != null ? csv.toString() : null), animationName,
                    shouldReverse,
                    shouldAlternate, this::notifyStopped);
        });
        if (csv != null || !animationName.isEmpty()) {
            animationThread.start();
        } else {
            Log.w("LivePreview", "No animation set!");
        }
    }

    public void stop() {
        running = false;
        if (animationThread != null) animationThread.interrupt();
        AnimationManager.Coordinator.get().cancelCurrent();
        StatusManager.release(this);
        notifyStopped();
    }


    @Override
    public void onSuspended() {
        stop();
    }

    @Override
    public void onActivated() {

    }

    private void notifyStopped() {
        running = false;
        if (listener != null) {
            listener.onPreviewStopped();
        }
    }

    public boolean isRunning() {
        return running;
    }
}
