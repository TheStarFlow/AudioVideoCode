package com.zzs.media

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.view.View

/**
@author  zzs
@Date 2022/5/26
@describe
 */
const val CLICK_INTERVAL = 300L
fun View.onClick(interval:Long = CLICK_INTERVAL, ls:(v:View)->Unit){
   var lastime = 0L
   setOnClickListener {
      val now = System.currentTimeMillis()
      if (now-lastime> interval){
         ls.invoke(it)
         lastime = now
      }
   }
}

fun Activity.checkPermission(permissions:Array<String>):Boolean{

   for (permission in permissions){
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
         && checkSelfPermission(
            permission
         ) != PackageManager.PERMISSION_GRANTED
      ) {
         requestPermissions(
            permissions, 1
         )
      }
   }

   return false
}