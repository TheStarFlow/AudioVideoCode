package com.zzs.media.simpleplayer

import android.media.*
import android.os.Build
import android.util.Log
import android.view.Surface
import com.zzs.media.logE
import com.zzs.media.logI
import com.zzs.media.record2aac.RecordToAACActivity
import kotlinx.coroutines.*
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
@author  zzs
@Date 2022/9/1
@describe  简单一把梭
 */

class SimpleMp4Player {

    private val mPlayerScore = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var mMp4FilePath: String? = null
    private var mCurrVideoFormat: MediaFormat? = null
    private var mCurrAudioFormat: MediaFormat? = null
    private var mSurface: Surface? = null
    private val isPlaying = AtomicBoolean(false)
    private var audioTrackIndex = -1
    private var videoTrackIndex = -1


    fun setSurface(surface: Surface) {
        mSurface = surface
    }


    fun setPath(path: String) {
        mMp4FilePath = path
    }

    fun start() {
        if (isPlaying.get()) return
        if (mMp4FilePath?.isBlank() == true) {
            logE("文件路径为空")
            return
        }
        if (mSurface == null) {
            logI("render surface null ")
        }
        mPlayerScore.launch(Dispatchers.IO + CoroutineName("DecodeCoroutine")) {
            val prepare = prepareDecode()
            isPlaying.set(prepare)
            if (!prepare) return@launch
            launch(Dispatchers.IO + CoroutineName("VideoDecode")) { startVideoDecode(this) }
            launch(Dispatchers.IO + CoroutineName("AudioDecode")) { startAudioDecode(this) }
        }
    }


    private fun prepareDecode(): Boolean {
        val mediaExtractor = MediaExtractor()
        mediaExtractor.setDataSource(mMp4FilePath!!)
        videoTrackIndex = mediaExtractor.getVideoOrAudioTrackIndex()
        if (videoTrackIndex == -1) {
            logI("no video track")
            return false
        }
        audioTrackIndex = mediaExtractor.getVideoOrAudioTrackIndex(false)
        if (audioTrackIndex >= 0) {
            mCurrAudioFormat = mediaExtractor.getTrackFormat(audioTrackIndex)
        } else {
            logI("no audio track")
        }
        mCurrVideoFormat = mediaExtractor.getTrackFormat(videoTrackIndex)
        val currMIME = mCurrVideoFormat?.getString(MediaFormat.KEY_MIME) ?: return false
        var created: Boolean
        var formatCodec: MediaCodec? = null
        try {
            formatCodec = MediaCodec.createDecoderByType(currMIME)
            created = true
        } catch (e: IllegalArgumentException) {
            logE("$currMIME 不能创建解码器")
            created = false
        } catch (e: IOException) {
            logE("$currMIME 创建解码器失败${e.message}")
            created = false
        } finally {
            formatCodec?.stop()
            formatCodec?.release()
            mediaExtractor.release()
        }
        return created
    }

    private fun startVideoDecode(score: CoroutineScope) {
        val videoDecodeC = mCurrVideoFormat?.getString(MediaFormat.KEY_MIME)
            ?.let { MediaCodec.createDecoderByType(it) } ?: return
        videoDecodeC.configure(mCurrVideoFormat, mSurface, null, 0)
        videoDecodeC.start()
        var isEndOfStream = false
        val mediaCodecInfo = MediaCodec.BufferInfo()
        var maxBufferSize = 100 * 1000
        mCurrVideoFormat?.run {
            if (mCurrVideoFormat?.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE) == true) {
                maxBufferSize =
                    mCurrVideoFormat?.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) ?: maxBufferSize
            }
        }
        val byteBuffer = ByteBuffer.allocateDirect(maxBufferSize)
        var isEnd = false
        val startTime = System.nanoTime()
        val mediaExtractor = MediaExtractor()
        mediaExtractor.setDataSource(mMp4FilePath!!)
        mediaExtractor.selectTrack(videoTrackIndex)
        while ((!isEndOfStream && isPlaying.get()) && score.isActive) {
            val inputIndex = videoDecodeC.dequeueInputBuffer(1_000_0)
            if (inputIndex >= 0) {
                val sampleTime = mediaExtractor.sampleTime
                logI("video sample time = $sampleTime")
                val flag = mediaExtractor.sampleFlags
                if (sampleTime == -1L) break
                val readSize = mediaExtractor.readSampleData(byteBuffer, 0)
                val buffer = videoDecodeC.getInputBuffer(inputIndex)
                buffer?.clear()
                buffer?.put(byteBuffer)
                buffer?.position(0)
                videoDecodeC.queueInputBuffer(
                    inputIndex,
                    0,
                    readSize,
                    startTime / 1000 + sampleTime,
                    flag
                )
                mediaExtractor.advance()
                var outIndex = videoDecodeC.dequeueOutputBuffer(mediaCodecInfo, 1_000_0)
                while (outIndex >= 0) {
                    if (mediaCodecInfo.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                        isEndOfStream = true
                        isEnd = true
                        break
                    }
                    videoDecodeC.releaseOutputBuffer(outIndex, true)
                    outIndex = videoDecodeC.dequeueOutputBuffer(mediaCodecInfo, 1_000_0)
                }
                if (isEnd) break
            }
        }
        logI("video decode break")
        try {
            videoDecodeC.stop()
            videoDecodeC.release()
            mediaExtractor.release()
        } catch (e: Exception) {
            logE("释放出错：$e")
        }
    }

    private fun startAudioDecode(scope: CoroutineScope) {
        val audioDecodeC = mCurrAudioFormat?.getString(MediaFormat.KEY_MIME)
            ?.let { MediaCodec.createDecoderByType(it) } ?: return
        audioDecodeC.configure(mCurrAudioFormat, null, null, 0)
        audioDecodeC.start()
        var isEndOfStream = false
        val mediaCodecInfo = MediaCodec.BufferInfo()
        var maxBufferSize = 100 * 1000
        mCurrAudioFormat?.run {
            if (mCurrAudioFormat?.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE) == true) {
                maxBufferSize =
                    mCurrAudioFormat?.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) ?: maxBufferSize
            }
        }
        val byteBuffer = ByteBuffer.allocateDirect(maxBufferSize)
        val startTime = System.nanoTime()
        val mediaExtractor = MediaExtractor()
        mediaExtractor.setDataSource(mMp4FilePath!!)
        mediaExtractor.selectTrack(audioTrackIndex)
        //初始化一个audioTrack 播放声音
        val audioFormat = mCurrAudioFormat!!
        //采样率
        val sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        } else {
            1
        }
        //采样位数
        val bit = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
            && audioFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)
        ) {
            audioFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else {
            AudioFormat.ENCODING_PCM_16BIT
        }
        val channel =
            if (channelCount > 1) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
        val attributeBuilder = AudioAttributes.Builder()
        attributeBuilder.setUsage(AudioAttributes.USAGE_MEDIA)
            .setLegacyStreamType(AudioManager.STREAM_MUSIC)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        val formatBuilder = AudioFormat.Builder()
        //比特率
        formatBuilder.setSampleRate(sampleRate)
            //通道数 立体声还是单声道
            .setChannelMask(channel)
            //采样位数
            .setEncoding(bit)
        //根据系统函数获取最小bufferSize
        val mini = AudioTrack.getMinBufferSize(
            sampleRate, channel,
            bit
        )
        val audioTrack = AudioTrack(
            attributeBuilder.build(),
            formatBuilder.build(),
            mini,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        audioTrack.play()
        while ((!isEndOfStream && isPlaying.get()) && scope.isActive) {
            val inputIndex = audioDecodeC.dequeueInputBuffer(1_000_0)
            if (inputIndex > 0) {
                val audioTime: Long = mediaExtractor.sampleTime
                logI("audio sample time = $audioTime")
                if (audioTime < 0) {
                    break
                }
                val flag = mediaExtractor.sampleFlags
                val readLen = mediaExtractor.readSampleData(byteBuffer, 0)
                val dpsInputBuffer = audioDecodeC.getInputBuffer(inputIndex)
                dpsInputBuffer?.clear()
                dpsInputBuffer?.put(byteBuffer)
                dpsInputBuffer?.position(0)
                byteBuffer.flip()
                audioDecodeC.queueInputBuffer(
                    inputIndex,
                    0,
                    readLen,
                    startTime + audioTime / 1000,
                    flag
                )
                if (!mediaExtractor.advance()) break
                var outIndex = audioDecodeC.dequeueOutputBuffer(mediaCodecInfo, 1_000_0)
                while (outIndex >= 0) {
                    if (mediaCodecInfo.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                        isEndOfStream = true
                        break
                    }
                    val buffer = audioDecodeC.getOutputBuffer(outIndex)
                    buffer?.position(mediaCodecInfo.offset)
                    buffer?.run {
                        audioTrack.write(
                            buffer, mediaCodecInfo.size,
                            AudioTrack.WRITE_BLOCKING
                        )
                    }
                    audioDecodeC.releaseOutputBuffer(outIndex, false)
                    outIndex = audioDecodeC.dequeueOutputBuffer(mediaCodecInfo, 1_000_0)
                }
            }
        }
        logI("video decode break")
        try {
            audioDecodeC.stop()
            audioDecodeC.release()
            mediaExtractor.release()
            while (audioTrack.playState!=AudioTrack.PLAYSTATE_STOPPED){ }
            audioTrack.stop()
            audioTrack.release()
        } catch (e: Exception) {
            logE("音频 释放出错：$e")
        }
    }


    fun release() {
        try {
            audioTrackIndex = -1
            videoTrackIndex = -1
            mPlayerScore.cancel()
            isPlaying.set(false)
        } catch (e: Throwable) {
            logE("释放出错 ${Log.getStackTraceString(e)}")
        }
    }
}

fun MediaExtractor.getVideoOrAudioTrackIndex(isVideo: Boolean = true): Int {
    for (i in 0 until trackCount) {
        val currTrackFormat = getTrackFormat(i)
        val mimType = currTrackFormat.getString(MediaFormat.KEY_MIME)
        if (isVideo) {
            if (mimType?.startsWith("video/") == true)
                return i
        } else {
            if (mimType?.startsWith("audio/") == true)
                return i
        }
    }
    return -1
}