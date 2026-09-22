package org.nukisystems.hieroglyph.Manager

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.nukisystems.hieroglyph.Constants.Constants
import org.nukisystems.hieroglyph.Data
import org.nukisystems.hieroglyph.Services.ToyConnection
import org.nukisystems.hieroglyph.Utils.MatrixUtils
import org.nukisystems.hieroglyph.Utils.ResourceUtils
import org.nukisystems.hieroglyph.aidl.NanoGlyphManager
import org.nukisystems.hieroglyph.Manager.StatusManager.GlyphPriority;
import org.nukisystems.hieroglyph.Manager.SettingsManager.Toys as config
import org.nukisystems.hieroglyph.Manager.SettingsManager as settings

class GlyphToyManager (
    private val context: Context
) : StatusManager.GlyphOwner {
    private var TAG = "GlyphToyManager"
    private var current: ToyConnection? = null
    private var currentData: Data.GlyphToy? = null

    private val aodHandler = Handler(Looper.getMainLooper())
    private var aodRunnable: Runnable? = null

    private val toyOwner = object : StatusManager.GlyphOwner {
        override fun onSuspended() { }
        override fun onActivated() { }
    }
    private val aodOwner = this

    companion object {
        @JvmField
        var isAodActive = false
        var aodPlayable = false
    }

    fun queryInstalledToys(): Map<ComponentName, Data.GlyphToy> =
        ResourceUtils.Toys.get(Constants.CONTEXT)

    fun enabledToysInOrder(): List<ComponentName>
    = config.getEnabledToysComponents()

    fun getCurrentAODToy(): ComponentName? = config.getAODToyComponent()
    fun getCurrentToy(): ComponentName? = ComponentName.unflattenFromString(config.getLastToy())

    fun activate(component: ComponentName? = getCurrentToy(), isAod: Boolean = false) {
        if (component == null || !queryInstalledToys().containsKey(component)) {
            Log.w(TAG, "Tried to start a toy that doesn't exist?: $component")
            return
        }
        current?.unbind()
        if (!isAod) StatusManager.acquire(toyOwner, GlyphPriority.TOY, toyOwner)
        current = ToyConnection(context, component, isAod).also { it.bind() }
        currentData = queryInstalledToys()[component]
        if (!isAod) config.setLastToy(current?.component?.flattenToString())
    }

    fun activateAOD() {
        val flipEnabled = settings.isGlyphFlipEnabled()
        val aodComponent: ComponentName = getCurrentAODToy() ?: return
        if (!config.isAODToyEnabled() || !flipEnabled) return
        activate(aodComponent, true)
        onEnterAod()
    }

    suspend fun showIcon(toyComponent: ComponentName) {
        val toyData: Data.GlyphToy? = queryInstalledToys()[toyComponent]
        if (toyData != null) {
            try {
                StatusManager.acquire(toyOwner, GlyphPriority.TOY, toyOwner)
            val toyBitmap: BitmapDrawable? = toyData.drawable as BitmapDrawable?
            NanoGlyphManager.setMatrixFrame(
                MatrixUtils.Bitmap.convertToGlyphMatrix(toyBitmap?.bitmap))
            } finally {
                StatusManager.release(toyOwner)
            }
        }
    }

    suspend fun cycleNext() {
        val ordered = enabledToysInOrder()
        if (ordered.isEmpty()) return
        val currentIndex = ordered.indexOfFirst { it == current?.component }
        val next = ordered[(currentIndex + 1) % ordered.size]
        current?.notifyLongPress()
        showIcon(next)
        config.setLastToy(next.flattenToString());
    }

    fun onEnterAod() {
        StatusManager.acquire(aodOwner, GlyphPriority.AOD, aodOwner)
        if (currentData?.supportsAOD == true) {
            startAodTicking()
        }
    }

    fun onExitAod() {
        AnimationManager.clearLEDs()
        StatusManager.release(aodOwner)
        stopAodTicking()
        aodPlayable = false
    }

    private fun startAodTicking() {
        if (aodRunnable != null) return
        aodRunnable = object : Runnable {
            override fun run() {
                if (StatusManager.isOwnedBy(aodOwner)) {
                    notifyAod()
                }
                aodHandler.postDelayed(this, 1000)
            }
        }
        aodHandler.post(aodRunnable!!)
        aodHandler.postDelayed({ aodPlayable = true }, 2500)
    }

    private fun stopAodTicking() {
        aodRunnable?.let { aodHandler.removeCallbacks(it) }
        aodRunnable = null
        aodPlayable = false
    }

    fun deactivateCurrent() {
        current?.unbind()
        current = null
        currentData = null
        StatusManager.release(toyOwner)
    }

    fun deactivateAOD() {
        deactivateCurrent()
        onExitAod()
    }

    fun notifyAod() = current?.sendEvent(Constants.GlyphToy.EVENT_AOD)
    fun notifyButtonDown() = current?.sendEvent(Constants.GlyphToy.EVENT_ACTION_DOWN)
    fun notifyButtonUp() = current?.sendEvent(Constants.GlyphToy.EVENT_ACTION_UP)

    override fun onSuspended() {
        stopAodTicking()
    }

    override fun onActivated() {
        startAodTicking()
    }

}
