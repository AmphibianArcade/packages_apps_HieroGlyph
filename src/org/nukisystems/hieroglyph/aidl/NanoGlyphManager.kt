package org.nukisystems.hieroglyph.aidl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import vendor.nukisystems.nanoglyph.DeviceInfo
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

object NanoGlyphManager {

    private val matrix = MatrixLedsClient()
    private val redLed = RedLedClient()

    private val connectMutex = Mutex()
    private var matrixConnected = false
    private var redLedConnected = false

    fun interface BoolCallback {
        fun onResult(value: Boolean)
    }

    private suspend fun ensureMatrixConnected(): Boolean {
        if (matrixConnected) return true
        connectMutex.withLock {
            if (!matrixConnected) {
                matrixConnected = withContext(Dispatchers.IO) { matrix.connect() != null }
            }
        }
        return matrixConnected
    }

    private suspend fun ensureRedLedConnected(): Boolean {
        if (redLedConnected) return true
        connectMutex.withLock {
            if (!redLedConnected) {
                redLedConnected = withContext(Dispatchers.IO) { redLed.connect() != null }
            }
        }
        return redLedConnected
    }

    suspend fun getMatrixInfo(): DeviceInfo? {
        if (!ensureMatrixConnected()) return null
        return withContext(Dispatchers.IO) { matrix.getInfo() }
    }

    suspend fun setMatrixBrightness(level: Int) {
        if (!ensureMatrixConnected()) return
        withContext(Dispatchers.IO) { matrix.setBrightness(level) }
    }

    suspend fun setMatrixSingleBrightness(index: Int, brightness: Int) {
        if (!ensureMatrixConnected()) return
        withContext(Dispatchers.IO) { matrix.setSingleBrightness(index, brightness) }
    }

    suspend fun playMatrixPattern(frames: List<IntArray>, fps: Int) {
        if (!ensureMatrixConnected()) return
        withContext(Dispatchers.IO) {
            val info = matrix.getInfo() ?: return@withContext
            val pixelsPerFrame = info.pixelCount
            val frameData = ByteArray(frames.size * pixelsPerFrame)
            var offset = 0
            for (frame in frames) {
                for (value in frame) frameData[offset++] = value.coerceIn(0, 255).toByte()
            }
            matrix.playPattern(pixelsPerFrame, frames.size, frameData, fps)
        }
    }

    suspend fun playMatrixPatternAndAwaitCompletion(
        frames: List<IntArray>,
        fps: Int
    ): Boolean {

        if (!ensureMatrixConnected()) return false

        return withContext(Dispatchers.IO) {
            val info = matrix.getInfo() ?: return@withContext false
            val pixelsPerFrame = info.pixelCount
            val frameData = ByteArray(frames.size * pixelsPerFrame)
            var offset = 0
            for (frame in frames) {
                for (value in frame) {
                    frameData[offset++] =
                        value.coerceIn(0, 255).toByte()
                }
            }

            try {
                matrix.playPatternAndAwaitCompletion(
                    pixelsPerFrame,
                    frames.size,
                    frameData,
                    fps
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun setMatrixFrame(frame: IntArray) {
        if (!ensureMatrixConnected()) return
        withContext(Dispatchers.IO) {
            matrix.setFrame(frame)
        }
    }

    suspend fun stopMatrix() {
        if (!ensureMatrixConnected()) return
        withContext(Dispatchers.IO) { matrix.stop() }
    }

    suspend fun isRedLedSupported(): Boolean {
        if (!ensureRedLedConnected()) return false
        return withContext(Dispatchers.IO) { redLed.isSupported() }
    }

    suspend fun setRedLedBrightness(brightness: Int) {
        if (!ensureRedLedConnected()) return
        withContext(Dispatchers.IO) { redLed.setBrightness(brightness) }
    }

    suspend fun getRedLedBrightness(): Int? {
        if (!ensureRedLedConnected()) return null
        return withContext(Dispatchers.IO) { redLed.getBrightness() }
    }

    object Java {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        @JvmStatic
        @JvmOverloads
        fun tryConnect(timeoutMs: Long = 2000): Boolean = runBlocking {
            withTimeoutOrNull(timeoutMs.milliseconds) {
                val matrixOk = async(Dispatchers.IO) { ensureMatrixConnected() }
                val redLedOk = async(Dispatchers.IO) { ensureRedLedConnected() }
                matrixOk.await() && redLedOk.await()
            } ?: false 
        }

        object RedLed {

            @JvmStatic
            fun isSupported(): Boolean = runBlocking {
                isRedLedSupported()
            }

            @JvmStatic
            fun getBrightness(callback: (Int?) -> Unit) {
                scope.launch {
                    callback(getRedLedBrightness())
                }
            }

            @JvmStatic
            @JvmOverloads
            fun setBrightness(brightness: Int, onComplete: Runnable? = null) {
                scope.launch {
                    setRedLedBrightness(brightness)
                    onComplete?.run()
                }
            }

        }

        object Matrix {

            @JvmStatic
            @JvmOverloads
            fun playPattern(frames: List<IntArray>, fps: Int, onComplete: Runnable? = null) {
                scope.launch {
                    playMatrixPattern(frames, fps)
                    onComplete?.run()
                }
            }

            @JvmStatic
            fun playPatternAndAwaitCompletion(
                frames: List<IntArray>,
                fps: Int,
                onComplete: BoolCallback
            ) {
                try {
                    runBlocking {
                        val result = playMatrixPatternAndAwaitCompletion(frames, fps)
                        onComplete.onResult(result)
                    }
                } catch (e: InterruptedException) {
                  // Playback interrupted
                } catch (e: CancellationException) {
                // Playback cancelled
                }
            }

            @JvmStatic
            @JvmOverloads
            fun setFrame(frame: IntArray, onComplete: Runnable? = null) {
                scope.launch {
                    setMatrixFrame(frame)
                    onComplete?.run()
                }
            }

            @JvmStatic
            @JvmOverloads
            fun setBrightness(brightness: Int, onComplete: Runnable? = null) {
                scope.launch {
                    setMatrixBrightness(brightness)
                    onComplete?.run()
                }
            }

            @JvmStatic
            @JvmOverloads
            fun setSingle(index: Int, brightness: Int, onComplete: Runnable? = null) {
                scope.launch {
                    setMatrixSingleBrightness(index, brightness)
                    onComplete?.run()
                }
            }

            @JvmStatic
            @JvmOverloads
            fun stop(onComplete: Runnable? = null) {
                scope.launch {
                    stopMatrix()
                    onComplete?.run()
                }
            }
        }
    }
}
