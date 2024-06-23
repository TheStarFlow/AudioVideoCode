package com.zzs.media.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.zzs.media.base.BasicCompose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID


typealias BLC = BluetoothGattCharacteristic

class BleServerActivity : ComponentActivity() {

    companion object {
        val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        val CH_UUID = UUID.fromString("00001103-0000-1000-8000-00805F9B34FB")
        val DE_UUID = UUID.fromString("00001105-0000-1000-8000-00805F9B34FB")
    }


    private val connectStateList = mutableListOf<String>()
    private val connectStatesLiveData = MutableLiveData<List<String>>()

    private fun postState(value: String) {
        connectStateList.add(value)
        connectStatesLiveData.postValue(connectStateList.toList())
    }


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


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermission()
        setContent {
            BasicCompose(dpWidth = 375f, dpHeight = 750f) {
                Column {
                    Text(
                        text = "蓝牙服务端",
                        color = Color(0xff333333),
                        fontSize = 24.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Row(horizontalArrangement = Arrangement.Center) {
                        Button(onClick = {
                            lifecycleScope.launch {
                                startBleServer()
                            }
                        }) {
                            Text(text = "开启蓝牙服务端")
                        }
                        Spacer(modifier = Modifier.width(20.dp))
                        Button(onClick = {
                            lifecycleScope.launch {
                                closeBleServer()
                            }
                        }) {
                            Text(text = "关闭蓝牙服务端")
                        }
                    }
                    val list by connectStatesLiveData.observeAsState()
                    if (list != null) {
                        val state = rememberLazyListState()
                        LaunchedEffect(key1 = list!!.size) {
                            state.animateScrollToItem(list!!.lastIndex)
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(1.dp), state = state) {
                            items(list!!) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight()
                                        ,
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
        enableBluetooth()

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


    private var isAdvertising = false


    private val isSucceed = MutableLiveData(false)


    private val serviceCallBack = object : AdvertiseCallback() {

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            postState("广播开启失败 $errorCode")
            isSucceed.postValue(false)
        }

        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            postState("广播开启成功")
            isSucceed.postValue(true)
        }
    }


    private val serverGattCallback = object : BluetoothGattServerCallback() {


        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
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
                postState("${device?.name} - ${device?.address} -> $text")

            } else {
                postState("${device?.name} - ${device?.address} -> 连错错误")
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            super.onServiceAdded(status, service)
            postState("添加服务->${service?.uuid.toString()}")
            service?.characteristics?.forEach {
                postState("添加特征->${it.uuid}")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            //回复响应客户端
            if (characteristic != null) {
                val call = mGattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    characteristic.value
                )
                postState("读取请求回调->,回复状态:${call},回复内容:${String(characteristic.value)}")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (value != null) {
                val call = mGattServer?.sendResponse(
                    device,
                    requestId,
                    0,
                    0,
                    "abcae".toByteArray()
                )
                postState("写入请求回调->回复状态:$call,写入内容:${String(value)}")
            }
            lifecycleScope.launch {
                delay(2000)
                characteristic?.value = "bbbbbbb".toByteArray()
                mGattServer?.notifyCharacteristicChanged(device,characteristic,false)
            }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor?
        ) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor)
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            descriptor: BluetoothGattDescriptor?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            super.onDescriptorWriteRequest(
                device,
                requestId,
                descriptor,
                preparedWrite,
                responseNeeded,
                offset,
                value
            )
        }

        override fun onExecuteWrite(device: BluetoothDevice?, requestId: Int, execute: Boolean) {
            super.onExecuteWrite(device, requestId, execute)
        }

        override fun onNotificationSent(device: BluetoothDevice?, status: Int) {
            super.onNotificationSent(device, status)
        }

        override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
            super.onMtuChanged(device, mtu)
        }

        override fun onPhyUpdate(device: BluetoothDevice?, txPhy: Int, rxPhy: Int, status: Int) {
            super.onPhyUpdate(device, txPhy, rxPhy, status)
        }

        override fun onPhyRead(device: BluetoothDevice?, txPhy: Int, rxPhy: Int, status: Int) {
            super.onPhyRead(device, txPhy, rxPhy, status)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun startBleServer() {
        withContext(Dispatchers.IO) {
            startAdvertiser()
            startGattService()
        }
    }

    @SuppressLint("MissingPermission")
    private fun closeBleServer() {
        advertiser?.stopAdvertising(serviceCallBack)
        mGattServer?.clearServices()
        mGattServer?.close()
    }

    private var mGattServer: BluetoothGattServer? = null

    @SuppressLint("MissingPermission")
    private fun startGattService() {
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mGattServer = manager.openGattServer(this, serverGattCallback)
        val service = BluetoothGattService(MY_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val character = BluetoothGattCharacteristic(
            CH_UUID, BluetoothGattCharacteristic.PROPERTY_READ or
                    BluetoothGattCharacteristic.PERMISSION_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY
                    or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE or
                    BluetoothGattCharacteristic.PERMISSION_READ
        )

        // 构造描述
        val bluetoothGattDescriptor = BluetoothGattDescriptor(
            DE_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        character.value = "aaaaaaaaaa".toByteArray()
        // 添加描述到特征中
        character.addDescriptor(bluetoothGattDescriptor);
        // 添加特征到服务中
        service.addCharacteristic(character);
        // 添加服务
        mGattServer?.addService(service)

    }


    private var advertiser: BluetoothLeAdvertiser? = null

    @SuppressLint("MissingPermission")
    private fun startAdvertiser() {
        val adapter = bleAdapter ?: return
        val s = adapter.setName("LM001")
        postState("设置蓝牙名称：$s")
        advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser != null && bleAdapter!!.isEnabled) {
            val setting = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setTimeout(0)
                .setConnectable(true).build()
            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(true)
                .addServiceUuid(ParcelUuid(MY_UUID))
                .build()
            advertiser?.startAdvertising(setting, data, serviceCallBack)
        } else {
            postState("手机不支持Ble广播")
        }
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        mGattServer?.close()
        mGattServer?.close()
    }
}