package com.zzs.media.ffmpegplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.zzs.media.hideSystemUI

/**
@author  zzs
@Date 2022/9/6
@describe
 */
class FFmpegPlayerActivity : ComponentActivity() {

    companion object {


        init {
            System.loadLibrary("native-mine")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val stringFromJNI = getStringFromJNI()
        setContent {
            PlayerScreen(stringFromJNI)
        }

    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    private external fun getStringFromJNI(): String
}

@Composable
fun PlayerScreen(text: String) {
    Surface(color = Color.Cyan, modifier = Modifier.fillMaxHeight().fillMaxWidth()) {
        Box(
            contentAlignment = Alignment.Center, modifier = Modifier.wrapContentWidth()
                .wrapContentHeight().background(Color.Red)
        ) {
            Text(text)
        }
    }
}