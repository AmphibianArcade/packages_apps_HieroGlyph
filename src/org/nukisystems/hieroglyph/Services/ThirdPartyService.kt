package org.nukisystems.hieroglyph.Services

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import org.nukisystems.hieroglyph.Manager.AnimationManager
import org.nukisystems.hieroglyph.Manager.StatusManager
import com.nothing.thirdparty.IGlyphService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.nukisystems.hieroglyph.Utils.CSVUtils
import org.nukisystems.hieroglyph.Utils.MatrixUtils
import org.nukisystems.hieroglyph.aidl.NanoGlyphManager

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

        override fun setMatrixColors(iArray: IntArray?) {
            Log.d(TAG, "setMatrixColors(): received data: ${iArray.contentToString()}")
            matrixScope.launch {
                iArray?.let {
                    NanoGlyphManager.setMatrixFrame(
                        CSVUtils.scale12BitTo8BitByte(
                            MatrixUtils.trimToValidFrame(it)))
                };
            }
        }

        override fun setAppMatrixColors(iArray: IntArray?) {
            Log.d(TAG, "setAppMatrixColors(): received data: ${iArray.contentToString()}")
            matrixScope.launch {
                iArray?.let {
                    NanoGlyphManager.setMatrixFrame(
                        CSVUtils.scale12BitTo8BitByte(
                            MatrixUtils.trimToValidFrame(it)))
                };
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
            StatusManager.setAnimationActive(true);
        }

        override fun closeSession() {
            Log.d("ThirdPartyService", "closeSession()")
            matrixScope.launch {
                NanoGlyphManager.setMatrixFrame(IntArray(MatrixUtils.getMinFrameLength()))
            }
            StatusManager.setAnimationActive(false);
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
        releaseWakeLock()
    }

    private fun ensureBridge() {
        val intent = Intent().apply {
            component = bridgeComponent
        }
        try {
            this.stopService(intent)
            this.startService(intent)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Failed to restart GlyphBridge service", e)
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
