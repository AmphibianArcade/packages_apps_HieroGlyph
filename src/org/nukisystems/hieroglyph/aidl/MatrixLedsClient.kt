package org.nukisystems.hieroglyph.aidl

import android.os.ServiceSpecificException
import vendor.nukisystems.nanoglyph.DeviceInfo
import vendor.nukisystems.nanoglyph.IMatrixLeds
import vendor.nukisystems.nanoglyph.IMatrixLedsCallback
import vendor.nukisystems.nanoglyph.MatrixPattern

class MatrixLedsClient : HalClientBase("MatrixLedsClient") {
    private var service: IMatrixLeds? = null

    fun connect(): IMatrixLeds? {
        val binder = waitForBinder(
            "vendor.nukisystems.nanoglyph.IMatrixLeds/default"
        ) { service = null; logBinderDied() } ?: return null
        return IMatrixLeds.Stub.asInterface(binder).also { service = it }
    }

    fun getInfo(): DeviceInfo? = try {
        service?.deviceInfo
    } catch (e: ServiceSpecificException) {
        logError("getDeviceInfo", e)
        null
    }

    fun setBrightness(level: Int) {
        try {
            service?.setSolidBrightness(level)
            logInfo("solid brightness set to $level")
        } catch (e: ServiceSpecificException) {
            logError("setSolidBrightness", e)
        }
    }

    fun setSingleBrightness(index: Int, brightness: Int) {
        try {
            service?.setSingleBrightness(index, brightness)
            logInfo("pixel $index set to $brightness")
        } catch (e: ServiceSpecificException) {
            logError("setSingleBrightness", e)
        }
    }

    fun setFrame(values: IntArray) {
        try {
            service?.setFrame(values)
            logInfo("frame set (${values.size} pixels)")
        } catch (e: ServiceSpecificException) {
            logError("setFrame", e)
        }
    }

    fun playPattern(pixelsPerFrame: Int, frameCount: Int, frameData: ByteArray, fps: Int = 10) {
        try {
            val pattern = MatrixPattern().apply {
                this.pixelsPerFrame = pixelsPerFrame
                this.frameCount = frameCount
                this.frameData = frameData
                this.brightness = 255
                this.fps = fps
            }
            service?.loadPattern(pattern)
            service?.startStream()
            logInfo("pattern loaded and streaming ($frameCount frames @ ${fps}fps)")
        } catch (e: ServiceSpecificException) {
            logError("loadPattern/startStream", e)
        }
    }

    fun stop() {
        try {
            service?.stopStream()
            logInfo("stream stopped")
        } catch (e: ServiceSpecificException) {
            logError("stopStream", e)
        }
    }

    fun registerCallback(
        onStateChanged: (Int) -> Unit,
        onDeviceError: (Int) -> Unit
    ) {
        try {
            service?.setCallback(object : IMatrixLedsCallback.Stub() {
                override fun onStreamStateChanged(newState: Int) {
                    onStateChanged(newState)
                }
                override fun onDeviceError(errnoValue: Int) {
                    onDeviceError(errnoValue)
                }

                override fun getInterfaceVersion(): Int {
                    return IMatrixLedsCallback.VERSION
                }

                override fun getInterfaceHash(): String {
                    return IMatrixLedsCallback.HASH
                }
            })
            logInfo("callback registered")
        } catch (e: ServiceSpecificException) {
            logError("setCallback", e)
        }
    }
}
