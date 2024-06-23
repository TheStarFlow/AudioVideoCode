package com.zzs.media

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.zzs.media.bluetooth.BleClientActivity
import com.zzs.media.bluetooth.BleServerActivity
import com.zzs.media.cameraX.CameraXRecordActivity
import com.zzs.media.databinding.ActivityMainBinding
import com.zzs.media.ffmpegplayer.FFmpegPlayerActivity
import com.zzs.media.muxer.MuxerMediaActivity
import com.zzs.media.record2aac.RecordToAACActivity
import com.zzs.media.simpleplayer.SimplePlayerActivity
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.ObservableSource
import io.reactivex.rxjava3.core.Observer
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.functions.Action
import io.reactivex.rxjava3.functions.BiFunction
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import io.reactivex.rxjava3.schedulers.Schedulers

/**
@author  zzs
@Date 2022/5/26
@describe
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpClick()
        bugTest()
    }

    private fun bugTest() {
        val ob1 = Observable.just(1,3,5,7)
        val ob2 = Observable.just(1,3,5,7,9,11)
            .map {
//                if (it==5){
//                    throw IllegalArgumentException("参数 $it 错误")
//                }
                it + 1
            }
        val ob3 = Observable.zip(ob1, ob2, BiFunction { t1, t2 ->

        }).observeOn(Schedulers.io())
        val d = ob3.subscribe({
            Log.d("bugTest","${it}")
        }, {
            Log.d("bugTest",it.toString())
        }, {
            Log.d("bugTest","complete")
        })


    }

    private fun setUpClick() {
        binding.mediaMuxer.onClick {
            startActivity(Intent(this, MuxerMediaActivity::class.java))
        }
        binding.record2aac.onClick {
            startActivity(Intent(this, RecordToAACActivity::class.java))
        }
        binding.cameraXRecord.onClick {
            startActivity(Intent(this, CameraXRecordActivity::class.java))
        }
        binding.simplePlayer.onClick {
            startActivity(Intent(this, SimplePlayerActivity::class.java))
        }
        binding.ffmpegPlayer.onClick {
            startActivity(Intent(this, FFmpegPlayerActivity::class.java))
        }
        binding.bluetoothClient.onClick {
            startActivity(Intent(this, BleClientActivity::class.java))
        }
        binding.bluetoothServer.onClick {
            startActivity(Intent(this, BleServerActivity::class.java))
        }
    }
}