package com.personal.rando.group

import com.personal.rando.i18n.tr
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * 对讲走「通话」线路：像打电话一样，声音从蓝牙耳机出、从蓝牙耳机的麦克风进，系统开回声消除和降噪。
 * 没连蓝牙耳机就用手机的喇叭和麦克风。骑到一半连上、断开耳机，自动换过去
 */
class AudioRoute(context: Context, private val onChanged: (String) -> Unit) {

    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var active = false

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = reroute()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = reroute()
    }

    /** 进入通话线路；返回现在用的是什么，比如「蓝牙耳机 AirPods」 */
    fun start(): String {
        val am = audio ?: return tr("手机", "Phone")
        active = true
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        am.registerAudioDeviceCallback(callback, handler)
        return choose(am)
    }

    fun stop() {
        val am = audio ?: return
        if (!active) return
        active = false
        am.unregisterAudioDeviceCallback(callback)
        if (Build.VERSION.SDK_INT >= 31) {
            am.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            am.stopBluetoothSco()
            @Suppress("DEPRECATION")
            am.isBluetoothScoOn = false
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = false
        }
        am.mode = AudioManager.MODE_NORMAL
    }

    private fun reroute() {
        val am = audio ?: return
        if (active) onChanged(choose(am))
    }

    /** 蓝牙耳机（新的 LE 耳机优先）> 手机喇叭；不用听筒，骑车时手机不贴耳朵 */
    private fun choose(am: AudioManager): String {
        if (Build.VERSION.SDK_INT >= 31) {
            val devices = am.availableCommunicationDevices
            val headset = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }
                ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
            val target = headset ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            target?.let { am.setCommunicationDevice(it) }
            return describe(headset)
        }
        val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val bluetooth = outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        val wired = outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET }
        @Suppress("DEPRECATION")
        when {
            bluetooth != null -> {
                am.isSpeakerphoneOn = false
                am.startBluetoothSco()
                am.isBluetoothScoOn = true
            }
            wired != null -> am.isSpeakerphoneOn = false
            else -> am.isSpeakerphoneOn = true
        }
        return describe(bluetooth ?: wired)
    }

    private fun describe(device: AudioDeviceInfo?): String = when (device?.type) {
        null -> tr("手机喇叭和麦克风", "Phone speaker and mic")
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET -> tr("有线耳机", "Wired headset")
        else -> tr("蓝牙耳机 ${device.productName}", "Bluetooth headset ${device.productName}").trim()
    }
}
