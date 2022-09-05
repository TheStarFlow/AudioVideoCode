package com.zzs.media.simpleplayer

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.zzs.media.hideSystemUI
import com.zzs.media.simpleplayer.ui.theme.AudioVideoCodeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class SimplePlayerActivity : ComponentActivity(), SurfaceHolder.Callback {

    private companion object {
        const val FILE_NAME = "demo.mp4"
    }

    private val player = SimpleMp4Player()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AudioVideoCodeTheme {
                SimplePlayer(this) {
                    playControl(it)
                }
            }
        }
    }

    private fun playControl(play: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            val file = File(cacheDir, FILE_NAME)
            if (!file.exists()) {
                file.createNewFile()
                val input = assets.open(FILE_NAME)
                val output = FileOutputStream(file)
                input.use { iin ->
                    output.use { out ->
                        iin.copyTo(out)
                    }
                }
            }
            player.setPath(file.absolutePath)
            if (play) {
                player.start()
            } else {
                player.pause()
            }
        }
    }

    override fun surfaceCreated(p0: SurfaceHolder) {
        player.setSurface(p0.surface)
    }

    override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) {

    }

    override fun surfaceDestroyed(p0: SurfaceHolder) {

    }

    override fun onResume() {
        super.onResume()
        window.decorView.postDelayed({ hideSystemUI() }, 100)
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }


    @Preview(showBackground = true)
    @Composable
    fun PreviewMyUI() {
        AudioVideoCodeTheme {
            SimplePlayer(this)
        }
    }
}

@Composable
fun SimplePlayer(callback: SurfaceHolder.Callback, playAction: (Boolean) -> Unit = {}) {
    Box {
        AndroidView({
            SurfaceView(it).apply {
                holder.addCallback(callback)
            }
        })
        Surface(
            modifier = Modifier.fillMaxHeight(0.15f)
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            color = Color.Transparent
        ) {
            UIControlLayer(playAction)
        }
    }
}

@Composable
fun UIControlLayer(playAction: (Boolean) -> Unit = {}) {
    var progree by remember { mutableStateOf(0f) }
    var playState by remember { mutableStateOf(false) }
    Column {
        Slider(
            value = progree,
            onValueChange = {
                progree = it
            }
        )
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = {
                    playState = !playState
                    playAction.invoke(playState)
                }
            ) {
                val text = if (playState) "暂停" else "播放"
                Text(text)
            }
        }
    }
}