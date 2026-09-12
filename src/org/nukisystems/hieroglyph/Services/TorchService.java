package org.nukisystems.hieroglyph.Services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;

import org.nukisystems.hieroglyph.Constants.Constants;
import org.nukisystems.hieroglyph.Manager.AnimationManager;
import org.nukisystems.hieroglyph.Manager.StatusManager;
import org.nukisystems.hieroglyph.Utils.FileUtils;
import org.nukisystems.hieroglyph.aidl.NanoGlyphManager;

public class TorchService extends Service {

    private PowerManager.WakeLock wakeLock;

    public static volatile boolean isRunning = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        if (Constants.CONTEXT == null) {
            Constants.CONTEXT = getApplicationContext();
        }
        PowerManager pm = Constants.CONTEXT.getSystemService(PowerManager.class);
        wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                getApplication().getPackageName() +
                        ":" +  getClass().getSimpleName()
        );
        if (!isRunning) isRunning = true;
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null) {
            String action = intent.getAction();

            if (Constants.ACTION_TORCH_ENABLE.equals(action)) {
                setTorch(true);
            } else if (Constants.ACTION_TORCH_DISABLE.equals(action)) {
                setTorch(false);
            }
        }
        if (!isRunning) isRunning = true;

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        setTorch(false);
        isRunning = false;
        super.onDestroy();
    }
    
    public void setTorch(boolean state) {
        if (state && !wakeLock.isHeld()) wakeLock.acquire();
        NanoGlyphManager.Java.Matrix.setBrightness(state ? 255 : 0);

        StatusManager.setAllLedsActive(state);
        if (!state && wakeLock.isHeld()) wakeLock.release();
    }

}
