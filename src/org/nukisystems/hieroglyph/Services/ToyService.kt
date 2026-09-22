package org.nukisystems.hieroglyph.Services

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.hardware.input.InputManager
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.InputMonitor
import android.view.InputChannel
import android.view.InputEventReceiver
import android.view.InputEvent
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.nukisystems.hieroglyph.Constants.Constants
import org.nukisystems.hieroglyph.Data
import org.nukisystems.hieroglyph.Manager.GlyphToyManager
import org.nukisystems.hieroglyph.Manager.SettingsManager
import org.nukisystems.hieroglyph.Utils.ResourceUtils
import kotlin.time.Duration.Companion.milliseconds

class ToyService : Service() {
    private val TAG = "ToyService"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var buttonMonitor: ButtonMonitor? = null

    object ToyIntent {
        const val ACTION_START_AOD = "start_aod"
        const val ACTION_STOP_AOD = "stop_aod"
        const val ACTION_CYCLE_NEXT = "next_toy"
        const val ACTION_START_TOY = "start_toy"
        const val ACTION_DEACTIVATE = "stop_toy"
    }

    private lateinit var toyManager: GlyphToyManager

    inner class LocalBinder : Binder() {
        fun getService(): ToyService = this@ToyService
    }
    private val localBinder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        toyManager = GlyphToyManager(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder = localBinder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ToyIntent.ACTION_CYCLE_NEXT -> {
                scope.launch { toyManager.cycleNext() }
            }
            ToyIntent.ACTION_START_TOY -> {
                toyManager.activate()
            }
            ToyIntent.ACTION_START_AOD -> {
               toyManager.activateAOD()
            }
            ToyIntent.ACTION_STOP_AOD -> {
                toyManager.deactivateAOD()
            }
            ToyIntent.ACTION_DEACTIVATE -> toyManager.deactivateCurrent()
        }
        if (Constants.CONTEXT == null) Constants.CONTEXT = applicationContext

        if (buttonMonitor == null && !Constants.Device.isPhone4aPro()) {
            buttonMonitor = ButtonMonitor().apply { register() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "ToyService destroyed, deactivating current toy")
        toyManager.deactivateCurrent()
        buttonMonitor?.unregister()
        buttonMonitor = null
        super.onDestroy()
    }

    inner class ButtonMonitor {

        private val holdThresholdMs: Long = 500L
        private var isDown = false
        private var holdFired = false
        private var holdJob: Job? = null

        private val receiverThread = HandlerThread("ScanCodeReceiverThread").apply { start() }
        private val workThread = HandlerThread("ScanCodeWorkThread").apply { start() }

        private var inputMonitor: InputMonitor? = null
        var receiver: InputEventReceiver? = null

        fun register() {
            val inputManager = this@ToyService.getSystemService(InputManager::class.java)
            val displayId = Display.DEFAULT_DISPLAY
            val buttonScanCode = ResourceUtils.getInteger(Constants.Res.INT_GLYPH_BUTTON_SCANCODE)

            val monitor = inputManager.monitorGestureInput("GlyphToyService", displayId)
            inputMonitor = monitor

            Log.d(TAG, "Starting button monitor")

            receiver = object : InputEventReceiver(monitor.inputChannel, receiverThread.looper) {
                override fun onInputEvent(event: InputEvent) {
                    if (event is KeyEvent && event.scanCode == buttonScanCode) {
                        when (event.action) {
                            KeyEvent.ACTION_DOWN -> {
                                if (!isDown && event.repeatCount == 0) {
                                    isDown = true
                                    holdFired = false
                                    holdJob = scope.launch {
                                        toyManager.notifyButtonDown()
                                        delay(holdThresholdMs.milliseconds)
                                        holdFired = true
                                        toyManager.notifyButtonUp()
                                    }
                                }
                            }

                            KeyEvent.ACTION_UP -> {
                                if (isDown) {
                                    isDown = false
                                    holdJob?.cancel()
                                    if (!holdFired) {
                                        scope.launch { toyManager.cycleNext() }
                                    }
                                }
                            }
                        }
                    }
                    finishInputEvent(event, false)
                }
            }
        }

        fun unregister() {
            receiver?.dispose()
            receiver = null
            inputMonitor?.dispose()
            inputMonitor = null
        }
    }
}
