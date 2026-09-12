package org.nukisystems.hieroglyph.aidl

import android.os.IBinder
import android.os.ServiceManager
import android.os.ServiceSpecificException
import android.util.Log

abstract class HalClientBase(private val tag: String) {

    protected fun logInfo(msg: String) {
        Log.i(tag, msg)
    }

    protected fun logError(what: String, e: ServiceSpecificException) {
        Log.e(tag, "$what failed: serviceSpecificError=${e.errorCode}")
    }

    protected fun logBinderDied() {
        Log.w(tag, "HAL service died, will need to reconnect")
    }

    protected fun waitForBinder(instanceName: String, onDied: () -> Unit): IBinder? {
        val binder = ServiceManager.waitForService(instanceName)
        if (binder == null) {
            Log.e(tag, "Failed to get $instanceName -- check SELinux grants and "
                    + "that the service is registered under this instance name")
            return null
        }
        binder.linkToDeath({ onDied() }, 0)
        return binder
    }
}
