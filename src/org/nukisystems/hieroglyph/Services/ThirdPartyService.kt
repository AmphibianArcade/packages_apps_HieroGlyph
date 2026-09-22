package org.nukisystems.hieroglyph.Services

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.nothing.thirdparty.IGlyphService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.nukisystems.hieroglyph.Manager.AnimationManager
import org.nukisystems.hieroglyph.Manager.GlyphToyManager
import org.nukisystems.hieroglyph.Manager.StatusManager
import org.nukisystems.hieroglyph.Manager.StatusManager.GlyphPriority
import org.nukisystems.hieroglyph.Utils.CSVUtils
import kotlin.time.Duration.Companion.milliseconds

class ThirdPartyService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val TAG: String = "ThirdPartyService"
    private val matrixScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val bridgeComponent =
        ComponentName("com.nothing.thirdparty", "com.nothing.thirdparty.GlyphService")

    private val activationGapMs: Int = 2000

    @Volatile
    private var readyToDraw = true

    private val binder = object : IGlyphService.Stub() {

        override fun setFrameColors(iArray: IntArray?) {
            return // Method is for segmented LED devices, ignore calls
        }

        fun requestFrame(frame: IntArray, raw: Boolean = false) {
            if (!readyToDraw) return
            if (raw) AnimationManager.updateLedFrameRaw(
                CSVUtils.scale12BitTo8BitByte(frame))
            else AnimationManager.updateLedFrame(
                CSVUtils.scale12BitTo8BitByte(frame))
        }

        override fun setMatrixColors(iArray: IntArray?) {
            if (StatusManager.isPriorityActive(GlyphPriority.AOD) && GlyphToyManager.aodPlayable) {
                iArray?.let { requestFrame(iArray) };
                Log.d(TAG, "setMatrixColors(): received data: ${iArray.contentToString()}")
            }
        }

        override fun setAppMatrixColors(iArray: IntArray?) {
            if (StatusManager.isPriorityActive(GlyphPriority.THIRD_PARTY)
                || StatusManager.isPriorityActive(GlyphPriority.TOY)) {
                iArray?.let { requestFrame(iArray, true) };
                Log.d(TAG, "setAppMatrixColors(): received data: ${iArray.contentToString()}")
            }
        }

        override fun setGlyphMatrixTimeout(needTimeout: Boolean) {

        }

        override fun closeAppMatrix() {
            Log.d("ThirdPartyService", "closeAppMatrix() called")
            closeSession()
        }

        override fun openSession() {
            Log.d("ThirdPartyService", "openSession()")
            acquireWakeLock() // Acquire the wake lock when opening the session
            StatusManager.acquire(this@ThirdPartyService,
                GlyphPriority.THIRD_PARTY, null)
        }

        override fun closeSession() {
            Log.d("ThirdPartyService", "closeSession()")
            matrixScope.launch {
                AnimationManager.clearLEDs()
            }
            StatusManager.release(this@ThirdPartyService);
            releaseWakeLock() // Release the wake lock when closing the session
        }

        override fun register(str: String) = true
        override fun registerSDK(key: String, device: String) = true
        override fun registerMatrixSDK(key: String) = true

    }

    override fun onBind(intent: Intent?): IGlyphService.Stub {
        return binder
    }

    override fun onCreate() {
        ensureBridge()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureBridge()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        val intent = Intent().apply {
            component = bridgeComponent
        }
        try {
            this.stopService(intent)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Failed to stop GlyphBridge service. not running?", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "No permission to start service", e)
        }
        releaseWakeLock()
        StatusManager.release(this@ThirdPartyService);
    }

    private fun ensureBridge() {
        val intent = Intent().apply { component = bridgeComponent }
        try {
            this.startService(intent)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Failed to start GlyphBridge service", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "No permission to start service", e)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ThirdPartyService::WakeLock")
            wakeLock?.acquire()
            Log.d("ThirdPartyService", "WakeLock acquired")
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock != null) {
            wakeLock?.release()
            wakeLock = null
            Log.d("ThirdPartyService", "WakeLock released")
        }
    }

}
