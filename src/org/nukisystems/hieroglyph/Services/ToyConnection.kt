package org.nukisystems.hieroglyph.Services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import org.nukisystems.hieroglyph.Constants.Constants

class ToyConnection(
    private val context: Context,
    val component: ComponentName,
    val isAOD: Boolean
) {
    private var toyMessenger: Messenger? = null
    private var bound = false

    private val replyMessenger = Messenger(Handler(Looper.getMainLooper()))

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            toyMessenger = Messenger(service)
            bound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            toyMessenger = null
            bound = false
        }
        override fun onBindingDied(name: ComponentName?) { bound = false }
        override fun onNullBinding(name: ComponentName?) {
            Log.w("ToyConnection", "Toy returned null binding — not messenger-based?")
        }
    }

    fun bind() {
        val intent = Intent(Constants.External.TOY_INTENT).apply {
            this.component = this@ToyConnection.component
        }
        bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        if (!bound) return
        try {
            context.unbindService(connection)
        } catch (e: Exception) {
            Log.w("ToyConnection", "unbind failed", e)
        } finally {
            bound = false
            toyMessenger = null
        }
    }

    fun notifyAod() = sendEvent(Constants.GlyphToy.EVENT_AOD)
    fun notifyLongPress() = sendEvent(Constants.GlyphToy.EVENT_CHANGE)

    fun sendEvent(event: String, extra: Bundle = Bundle()) {
        val data = extra.apply { putString(Constants.GlyphToy.MSG_GLYPH_TOY_DATA, event) }
        val msg = Message.obtain(null, Constants.GlyphToy.MSG_GLYPH_TOY).apply {
            this.data = data
            replyTo = replyMessenger
        }
        try {
            toyMessenger?.send(msg)
        } catch (e: RemoteException) {
            Log.w("ToyConnection", "Toy died mid-send", e)
            bound = false
            toyMessenger = null
        }
    }

}
