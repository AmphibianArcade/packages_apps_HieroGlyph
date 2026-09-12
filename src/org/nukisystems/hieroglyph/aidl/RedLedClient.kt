package org.nukisystems.hieroglyph.aidl

import android.os.ServiceSpecificException
import vendor.nukisystems.nanoglyph.IRedLed

class RedLedClient : HalClientBase("RedLedClient") {
    private var service: IRedLed? = null

    fun connect(): IRedLed? {
        val binder = waitForBinder(
            "vendor.nukisystems.nanoglyph.IRedLed/default"
        ) { service = null; logBinderDied() } ?: return null
        return IRedLed.Stub.asInterface(binder).also { service = it }
    }

    fun isSupported(): Boolean = try {
        service?.isSupported ?: false
    } catch (e: ServiceSpecificException) {
        logError("isSupported", e)
        false
    }

    fun setBrightness(brightness: Int) {
        try {
            service?.setBrightness(brightness)
            logInfo("red LED brightness set to $brightness")
        } catch (e: ServiceSpecificException) {
            logError("setBrightness", e)
        }
    }

    fun getBrightness(): Int? = try {
        service?.brightness
    } catch (e: ServiceSpecificException) {
        logError("getBrightness", e)
        null
    }
}
