package com.zzs.media.simpleplayer

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaSync
import com.zzs.media.logI
import java.nio.ByteBuffer

/**
@author  zzs
@Date 2022/9/7
@describe
 */
class DecodeCallBack(
    private val mediaExtractor: MediaExtractor,
    private val mediaSync: MediaSync,
    @Volatile var isPause: Boolean = false,
    private val isAudio: Boolean = false,
) : MediaCodec.Callback() {

    override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
        if (isPause) {
            Thread.sleep(1)
            return
        }
        val flag = mediaExtractor.sampleFlags
        val sampleTime = mediaExtractor.sampleTime
        if (sampleTime == -1L) return
        val inputBuffer = codec.getInputBuffer(index) ?: return
        inputBuffer.clear()
        val len = mediaExtractor.readSampleData(inputBuffer, 0)
        if (len == 0) return
        codec.queueInputBuffer(index, 0, len, sampleTime, flag)
        if (!isAudio) {
            logI("video sample time = $sampleTime")
        } else {
            logI("audio sample time = $sampleTime")
        }
        mediaExtractor.advance()
    }

    override fun onOutputBufferAvailable(
        codec: MediaCodec,
        index: Int,
        info: MediaCodec.BufferInfo
    ) {
        if (info.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            return
        if (isAudio) {
            val buffer = codec.getOutputBuffer(index) ?: return
            //api 29 MediaSync 的 bug ，如果第一个入队的 buffer 的 remaining 等于 0 ，你将永远不会播放声音
            if (buffer.remaining() > 0) {
                mediaSync.queueAudio(buffer, index, info.presentationTimeUs)
            } else {
                codec.releaseOutputBuffer(index, false)
            }
        } else {
            codec.releaseOutputBuffer(index, info.presentationTimeUs * 1000)
        }

    }

    override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {

    }

    override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {

    }
}