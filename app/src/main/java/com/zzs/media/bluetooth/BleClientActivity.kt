package com.zzs.media.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.zzs.media.base.BasicCompose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BleClientActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            postState("蓝牙权限获取")
        } else {
            postState("蓝牙权限拒绝")
            checkPermission()
        }
    }

    private val gattCallback = MyGattCallBack()


    private val bluetoothDevices = mutableListOf<BleDevice>()

    private val mutableDevicesList = MutableLiveData<List<BleDevice>>()

    private val devices: LiveData<List<BleDevice>> = mutableDevicesList


    private var scanCallback: ScanCallback = object : ScanCallback() {


        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = BleDevice(device = result?.device, result = result)
            if (!bluetoothDevices.contains(device)) {
                bluetoothDevices.add(device)
                mutableDevicesList.postValue(bluetoothDevices.sortedByDescending {
                    it.result?.rssi
                }.toList())
                Log.i("BlueTooth", "扫描到新设备~ ${result}")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            postState("扫描蓝牙失败 $errorCode")
        }
    }

    private var mBluetoothGatt: BluetoothGatt? = null

    private var isConnected = false

    private val connectStateList = mutableListOf<String>()
    private val connectStates = MutableLiveData<List<String>>()


    private fun postState(value: String) {
        connectStateList.add(value)
        connectStates.postValue(connectStateList.toList())
    }


    private fun getService(): BluetoothGattService? {
        if (mBluetoothGatt != null) {
            val services = mBluetoothGatt?.services ?: return null
            if (services.isEmpty()) return null
            return mBluetoothGatt?.getService(BleServerActivity.MY_UUID)
        }
        return null
    }

    private fun getCharacteristic(service: BluetoothGattService?): BluetoothGattCharacteristic? {
        if (service != null) {
            val characteristics = service.characteristics ?: return null
            if (characteristics.isEmpty()) return null
            return service.getCharacteristic(BleServerActivity.CH_UUID)
        }
        return null
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        checkPermission()
        enableBluetooth()
        setContent {
            BasicCompose(
                dpWidth = 375f,
                dpHeight = 750f
            ) {
                Column {
                    Row {
                        Button(onClick = {
                            scanBluetooth(scanCallback)
                        }) {
                            Text(text = "开始扫描")
                        }
                        Button(onClick = {
                            stopScan(scanCallback)
                        }) {
                            Text(text = "停止扫描")
                        }
                    }
                    val list by devices.observeAsState()
                    if (list != null) {
                        LazyColumn(
                            modifier = Modifier
                                .height(450.dp)
                                .background(color = Color.LightGray.copy(alpha = 0.2f))
                        ) {
                            items(list!!.size) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight()
                                        .padding(10.dp),
                                ) {
                                    Column {
                                        Text(
                                            text = list!![it].device?.name ?: "匿名设备",
                                            fontSize = 20.sp
                                        )
                                        Text(
                                            text = "信号强度：${list!![it].result?.rssi}",
                                            fontSize = 12.sp,
                                            color = Color.LightGray
                                        )
                                        Text(
                                            text = "硬件地址：${list!![it].device?.address}",
                                            fontSize = 12.sp,
                                            color = Color.LightGray
                                        )
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    Button(onClick = {
                                        closeConn()
                                        mBluetoothGatt = list!![it].device?.connectGatt(
                                            this@BleClientActivity,
                                            false, gattCallback
                                        )
                                    }) {
                                        Text(text = "连接")
                                    }
                                }

                            }
                        }
                    }
                    var inputText by remember {
                        mutableStateOf("")
                    }
                    Column {
                        Row {
                            TextField(value = inputText, onValueChange = {
                                inputText = it
                            })
                            Button(onClick = {
                                launchIo {
                                    writeData(inputText)
                                }
                            }) {
                                Text(text = "写入")
                            }
                        }
                        Button(onClick = {
                            launchIo {
                                readData()
                            }
                        }) {
                            Text(text = "读取")
                        }
                    }
                    val stateList by connectStates.observeAsState()
                    if (stateList != null) {
                        val state = rememberLazyListState()
                        LaunchedEffect(key1 = stateList!!.size) {
                            state.animateScrollToItem(stateList!!.lastIndex)
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), state = state) {
                            items(stateList!!) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight(),
                                    border = BorderStroke(width = 1.dp, color = Color.DarkGray),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(text = it, modifier = Modifier.padding(4.dp))
                                }
                            }
                        }
                    }


                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun readData() {
        val service = getService()
        val characteristic = getCharacteristic(service) ?: return
        mBluetoothGatt?.setCharacteristicNotification(characteristic,true)
        val call = mBluetoothGatt?.readCharacteristic(characteristic)
        postState("开始读取操作：${call}")
    }

    @SuppressLint("MissingPermission", "NewApi")
    private fun writeData(inputText: String) {
        val service = getService()
        val characteristic = getCharacteristic(service) ?: return
        characteristic.value = inputText.toByteArray()
        val call = mBluetoothGatt?.writeCharacteristic(characteristic)
        postState("开始写入操作：${call}")

    }


    private fun checkPermission() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT) ||
            !hasPermission(Manifest.permission.BLUETOOTH_SCAN) ||
            !hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE) ||
            !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ||
            !hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_SCAN)
                requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }


    fun closeConn() {
        if (mBluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                mBluetoothGatt?.disconnect()
                mBluetoothGatt?.close()
                mBluetoothGatt = null
            }

        }
    }

    override fun onStop() {
        super.onStop()
        stopScan(scanCallback)
    }


    private inline fun launchIo(crossinline invoke: () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            invoke()
        }
    }


    private inner class MyGattCallBack : BluetoothGattCallback() {


        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            launchIo {
                postState("发现服务回调")
                val service = gatt?.getService(BleServerActivity.MY_UUID) ?: return@launchIo
                postState("发现服务 ${service.uuid}")
                val character = service.getCharacteristic(BleServerActivity.CH_UUID)
                postState("发现特征 ${character.uuid}")
                character.value = "123".toByteArray()
                mBluetoothGatt?.setCharacteristicNotification(character, true)
                mBluetoothGatt?.readCharacteristic(character)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            postState("特征值改变： uuid: ${characteristic.uuid} value:${String(value)}")
        }

        //链接状态改变
        @SuppressLint("MissionPermission", "MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            //status 上一个状态   newState 新的状态
            super.onConnectionStateChange(gatt, status, newState)
            launchIo {
                if (status == BluetoothGatt.GATT_SUCCESS
                    && newState == BluetoothGatt.STATE_CONNECTED
                ) {
                    isConnected = true
                    gatt?.discoverServices()
                } else {
                    isConnected = false
                    closeConn()
                }
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    var text: String = "..."
                    when (newState) {
                        BluetoothGatt.STATE_CONNECTED -> {
                            text = "已连接"
                        }

                        BluetoothGatt.STATE_CONNECTING -> {
                            text = "正在连接"

                        }

                        BluetoothGatt.STATE_DISCONNECTED -> {
                            text = "已断开连接"

                        }

                        BluetoothGatt.STATE_DISCONNECTING -> {
                            text = "正在断开连接"
                        }
                    }
                    postState("${gatt?.device?.name} - ${gatt?.device?.address}   --->  $text")
                } else {
                    postState("${gatt?.device?.name} - ${gatt?.device?.address}   --->  连接错误")
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            postState("特征值读取操作状态  -> $status,content : ${String(value)}")
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (characteristic != null) {
                val sb = StringBuilder()
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    sb.append("写入数据成功,内容:${String(characteristic.value)}")
                } else {
                    sb.append("写入数据失败，内容:${String(characteristic.value)}")
                }
                postState(sb.toString())
            }
        }
    }
}