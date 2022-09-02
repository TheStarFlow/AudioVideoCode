package com.zzs.media

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.zzs.media.cameraX.CameraXRecordActivity
import com.zzs.media.databinding.ActivityMainBinding
import com.zzs.media.muxer.MuxerMediaActivity
import com.zzs.media.record2aac.RecordToAACActivity
import com.zzs.media.simpleplayer.SimplePlayer
import com.zzs.media.simpleplayer.SimplePlayerActivity

/**
@author  zzs
@Date 2022/5/26
@describe
 */
class MainActivity:AppCompatActivity() {

   private lateinit var binding: ActivityMainBinding

   override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)
      binding = ActivityMainBinding.inflate(layoutInflater)
      setContentView(binding.root)
      setUpClick()
   }

   private fun setUpClick() {
      binding.mediaMuxer.onClick {
         startActivity(Intent(this,MuxerMediaActivity::class.java))
      }
      binding.record2aac.onClick {
         startActivity(Intent(this,RecordToAACActivity::class.java))
      }
      binding.cameraXRecord.onClick {
         startActivity(Intent(this,CameraXRecordActivity::class.java))

      }
      binding.simplePlayer.onClick {
         startActivity(Intent(this, SimplePlayerActivity::class.java))
      }
   }
}