package com.zzs.media.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.LeScanCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

const val REQUIRE_PERMISSION_CODE = 10086
const val ENABLE_CODE = 10010
const val SCAN_CODE = 10000


val Activity.bleAdapter: BluetoothAdapter?
    get() = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter


@SuppressLint("MissingPermission")
fun Activity.enableBluetooth() {
    //判断是否支持蓝牙
    if (this.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
        //获取蓝牙服务管理
        if (bleAdapter != null) {
            if (!bleAdapter!!.isEnabled) {
                bleAdapter!!.enable()
                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                this.startActivityForResult(intent, ENABLE_CODE)
            }
        }
    } else {
        Toast.makeText(this, "没有蓝牙", Toast.LENGTH_SHORT).show()
    }
}

var Activity.isScanning: Boolean
    get() = false
    set(value) {}

@SuppressLint("MissingPermission")
fun Activity.scanBluetooth(scanCallback: ScanCallback): Boolean {
    if (bleAdapter?.isEnabled == true) {
        val scanner = bleAdapter?.bluetoothLeScanner
        scanner?.startScan(scanCallback)
        isScanning = true
        return true
    }
    return false
}


fun Activity.hasPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

@SuppressLint("MissingPermission")
fun Activity.stopScan(scanCallback: ScanCallback) {
    bleAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
    isScanning = false
}