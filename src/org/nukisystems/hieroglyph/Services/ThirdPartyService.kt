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
import org.nukisystems.hieroglyph.Data
import org.nukisystems.hieroglyph.Manager.AnimationManager
import org.nukisystems.hieroglyph.Manager.GlyphToyManager as toyMgr
import org.nukisystems.hieroglyph.Manager.SettingsManager
import org.nukisystems.hieroglyph.Manager.StatusManager
import org.nukisystems.hieroglyph.Manager.StatusManager.GlyphPriority
import org.nukisystems.hieroglyph.Services.ToyService.ToyIntent as toyIntent
import org.nukisystems.hieroglyph.Utils.CSVUtils
import org.nukisystems.hieroglyph.Utils.ServiceUtils

class ThirdPartyService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val TAG: String = "ThirdPartyService"
    private val matrixScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val bridgeComponent =
        ComponentName("com.nothing.thirdparty", "com.nothing.thirdparty.GlyphService")

    private val binder = object : IGlyphService.Stub() {

        override fun setFrameColors(iArray: IntArray?) {
            return // Method is for segmented LED devices, ignore calls
        }

        fun requestFrame(frame: IntArray, raw: Boolean = false) {
            if (raw) AnimationManager.updateLedFrameRaw(
                CSVUtils.scale12BitTo8BitByte(frame))
            else AnimationManager.updateLedFrame(
                CSVUtils.scale12BitTo8BitByte(frame))
        }

        override fun setMatrixColors(iArray: IntArray?) {
            if (StatusManager.isPriorityActive(GlyphPriority.THIRD_PARTY)
                || StatusManager.isPriorityActive(GlyphPriority.AOD)) {
                iArray?.let { requestFrame(iArray) };
                Log.d(TAG, "setMatrixColors(): received data: ${iArray.contentToString()}")
            }
        }

        override fun setAppMatrixColors(iArray: IntArray?) {
            if (StatusManager.acquire(this@ThirdPartyService, GlyphPriority.THIRD_PARTY, null)
                || StatusManager.isPriorityActive(GlyphPriority.TOY)) {
                iArray?.let { requestFrame(iArray, true) };
                Log.d(TAG, "setAppMatrixColors(): received data: ${iArray.contentToString()}")
            }
        }

        override fun init() {
            Log.d("ThirdPartyService", "init()")
            acquireWakeLock() // Acquire the wake lock when opening the session
            if (!isCallerAod()) StatusManager.acquire(this@ThirdPartyService,
                GlyphPriority.THIRD_PARTY, null)
        }

        override fun unInit() {
            Log.d("ThirdPartyService", "unInit() called")
            if (StatusManager.isPriorityActive(GlyphPriority.THIRD_PARTY)) {
                matrixScope.launch { AnimationManager.clearLEDs() }
            }
            StatusManager.release(this@ThirdPartyService);
            matrixScope.launch {
                delay(2000L)
                restoreAOD()
            }
            releaseWakeLock()
        }

        override fun closeAppMatrix() {
            Log.d("ThirdPartyService", "closeAppMatrix() called")
            unInit()
        }

        override fun setGlyphMatrixTimeout(needTimeout: Boolean) {

        }

        override fun register(str: String) = true
        override fun registerSDK(key: String, device: String) = true
        override fun registerMatrixSDK(key: String): Boolean {
            Log.w(TAG, "register matrix!")
            init()
            return true
        }

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

    private fun isCallerAod(): Boolean {
        val toyData: Data.GlyphToy = toyMgr.currentData ?: return false
        return toyData.supportsAOD
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

    private fun stopAOD() {
        if (SettingsManager.Toys.isAODToyEnabled()) {
            ServiceUtils.startToyService(toyIntent.ACTION_STOP_AOD)
        }
    }

    private fun restoreAOD() {
        if (SettingsManager.Toys.isAODToyEnabled()) {
            ServiceUtils.startToyService(toyIntent.ACTION_START_AOD)
        }
    }

}
