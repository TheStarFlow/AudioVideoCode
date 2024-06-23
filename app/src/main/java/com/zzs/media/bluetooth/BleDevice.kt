package com.zzs.media.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult

data class BleDevice(val device: BluetoothDevice?, val result: ScanResult?){


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BleDevice

        if (device?.equals(other.device)==true) return true
        if (result?.equals(other.result)==false) return false

        return true
    }

    override fun hashCode(): Int {
        var result1 = device?.hashCode() ?: 0
        result1 = 31 * result1 + result.hashCode()
        return result1
    }
}
