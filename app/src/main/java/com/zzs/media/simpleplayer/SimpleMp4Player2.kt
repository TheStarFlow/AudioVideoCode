package com.zzs.media.simpleplayer

import android.media.*
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Surface
import com.zzs.media.logE
import com.zzs.media.logI
import kotlinx.coroutines.*
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
@author  zzs
@Date 2022/9/1
@describe  MediaSync 和 MediaCodeC 同步使用
 */

class SimpleMp4Player2 {

    private val mPlayerScore = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var mMp4FilePath: String? = null
    private var mCurrVideoFormat: MediaFormat? = null
    private var mCurrAudioFormat: MediaFormat? = null
    private var mSurface: Surface? = null
    private val isPlaying = AtomicBoolean(false)
    private val isPause = AtomicBoolean(false)
    private var audioTrackIndex = -1
    private var videoTrackIndex = -1

    private lateinit var mVideoMediaExtractor: MediaExtractor
    private lateinit var mAudioMediaExtractor: MediaExtractor
    private var mDuration = 0L

    private var videoWidth = 0
    private var videoHeight = 0

    private var mPlayListener: IPlayerListener? = null
    private val mMediaSync by lazy { MediaSync() }

    fun setPlayListener(listener: IPlayerListener) {
        mPlayListener = listener
    }


    fun setSurface(surface: Surface) {
        mSurface = surface
        if (mCurrVideoFormat != null) {
            mMediaSync.setSurface(surface)
        }
    }


    fun setPath(path: String) {
        mMp4FilePath = path
    }

    fun pause() {
        isPause.set(true)
    }

    fun isPlaying() = isPlaying.get() && !isPause.get()

    fun start() {
        if (isPlaying.get()) {
            isPause.compareAndSet(true, false)
            return
        }
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
        val videoDuration = mCurrVideoFormat?.getLong(MediaFormat.KEY_DURATION) ?: 0L
        val audioDuration = mCurrAudioFormat?.getLong(MediaFormat.KEY_DURATION) ?: 0L
        videoWidth = mCurrVideoFormat?.getInteger(MediaFormat.KEY_WIDTH) ?: 0
        videoHeight = mCurrVideoFormat?.getInteger(MediaFormat.KEY_HEIGHT) ?: 0
        mDuration = max(videoDuration, audioDuration)
        mPlayListener?.onFetchMediaInfo(mDuration / 1000, videoWidth, videoHeight)
        var formatCodec: MediaCodec? = null
        try {
            formatCodec = MediaCodec.createDecoderByType(currMIME)
            created = true
        } catch (e: IllegalArgumentException) {
            logE("不能创建 $currMIME 解码器")
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

    fun seekTo(timeUs: Long) {
        mPlayerScore.launch(Dispatchers.IO) {
            if (!isPlaying.get()) return@launch
            val isPaused = isPause.get()
            isPause.compareAndSet(false,true)
            val seekTo = min(timeUs, mDuration)
            mAudioMediaExtractor.seekTo(seekTo, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            mVideoMediaExtractor.seekTo(seekTo, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            if (!isPaused){
                isPause.set(false)
            }
        }
    }

    private fun startVideoDecode(score: CoroutineScope) {
        val videoCodeC = mCurrVideoFormat?.getString(MediaFormat.KEY_MIME)
            ?.let { MediaCodec.createDecoderByType(it) } ?: return
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
        val startTime = System.nanoTime()
        mVideoMediaExtractor = MediaExtractor()
        mVideoMediaExtractor.setDataSource(mMp4FilePath!!)
        mVideoMediaExtractor.selectTrack(videoTrackIndex)
        if (mSurface != null) {
            mMediaSync.setSurface(mSurface)
            val surface = mMediaSync.createInputSurface()
            videoCodeC.configure(mCurrVideoFormat, surface, null, 0)
        } else {
            videoCodeC.configure(mCurrVideoFormat, mSurface, null, 0)
        }
        videoCodeC.start()
        while ((!isEndOfStream && isPlaying.get()) && score.isActive) {
            if (isPause.get()) {
                Thread.sleep(1)
                continue
            }
            val sampleTime = mVideoMediaExtractor.sampleTime
            val inputIndex = videoCodeC.dequeueInputBuffer(1_000_0)
            if (inputIndex >= 0) {
                logI("video sample time = $sampleTime")
                val flag = mVideoMediaExtractor.sampleFlags
                if (sampleTime == -1L) break
                val readSize = mVideoMediaExtractor.readSampleData(byteBuffer, 0)
                val buffer = videoCodeC.getInputBuffer(inputIndex)
                buffer?.clear()
                buffer?.put(byteBuffer)
                buffer?.position(0)
                videoCodeC.queueInputBuffer(
                    inputIndex,
                    0,
                    readSize,
                    sampleTime,
                    flag
                )
                mVideoMediaExtractor.advance()
                var outIndex = videoCodeC.dequeueOutputBuffer(mediaCodecInfo, 1_000_0)
                while (outIndex >= 0 && score.isActive) {
                    if (mediaCodecInfo.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                        isEndOfStream = true
                        break
                    }
                    videoCodeC.releaseOutputBuffer(outIndex, 0)
                    outIndex = videoCodeC.dequeueOutputBuffer(mediaCodecInfo, 1_000_0)
                }
            }
        }
        logI("video decode break")
        try {
            videoCodeC.stop()
            videoCodeC.release()
            mVideoMediaExtractor.release()
        } catch (e: Exception) {
            logE("释放出错：$e")
        } finally {
            resetState()
        }
    }

    private fun startAudioDecode(scope: CoroutineScope) {
        val audioCodeC = mCurrAudioFormat?.getString(MediaFormat.KEY_MIME)
            ?.let { MediaCodec.createDecoderByType(it) } ?: return
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
        mAudioMediaExtractor = MediaExtractor()
        mAudioMediaExtractor.setDataSource(mMp4FilePath!!)
        mAudioMediaExtractor.selectTrack(audioTrackIndex)

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
        audioCodeC.configure(mCurrAudioFormat, null, null, 0)
        audioCodeC.start()
        mMediaSync.setAudioTrack(audioTrack)
        mMediaSync.setCallback(object : MediaSync.Callback() {
            override fun onAudioBufferConsumed(
                sync: MediaSync,
                audioBuffer: ByteBuffer,
                bufferId: Int
            ) {
                if (scope.isActive&&!isEndOfStream){
                    audioCodeC.releaseOutputBuffer(bufferId, false)
                }
            }

        }, null)
        mMediaSync.playbackParams = PlaybackParams().setSpeed(1.0f)
        mMediaSync.syncParams = SyncParams().setSyncSource(SyncParams.SYNC_SOURCE_DEFAULT)
        while ((!isEndOfStream && isPlaying.get()) && scope.isActive) {
            if (isPause.get()) {
                Thread.sleep(1)
                continue
            }
            val inputIndex = audioCodeC.dequeueInputBuffer(1_000_0) ?: -1
            if (inputIndex > 0) {
                val audioTime: Long = mAudioMediaExtractor.sampleTime
                logI("audio sample time = $audioTime")
                if (audioTime < 0) {
                    break
                }
                val flag = mAudioMediaExtractor.sampleFlags
                val readLen = mAudioMediaExtractor.readSampleData(byteBuffer, 0)
                val dpsInputBuffer = audioCodeC.getInputBuffer(inputIndex)
                dpsInputBuffer?.clear()
                dpsInputBuffer?.put(byteBuffer)
                dpsInputBuffer?.position(0)
                byteBuffer.flip()
                audioCodeC.queueInputBuffer(
                    inputIndex,
                    0,
                    readLen,
                    startTime + audioTime / 1000,
                    flag
                )
                if (!mAudioMediaExtractor.advance()) break
                var outIndex = audioCodeC.dequeueOutputBuffer(mediaCodecInfo, 1_000_0) ?: -1
                while (outIndex >= 0 && scope.isActive) {
                    if (mediaCodecInfo.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                        isEndOfStream = true
                        break
                    }
                    val buffer = audioCodeC.getOutputBuffer(outIndex)?:continue
                    if (buffer.remaining()>0){
                        mMediaSync.queueAudio(buffer,outIndex,mediaCodecInfo.presentationTimeUs)
                    }else{
                        audioCodeC.releaseOutputBuffer(outIndex,false)
                    }
                    outIndex = audioCodeC.dequeueOutputBuffer(mediaCodecInfo, 1_000_0)
                }
            }
        }
        logI("video decode break")
        try {
            audioCodeC.stop()
            audioCodeC.release()
            mAudioMediaExtractor.release()
            audioTrack.stop()
            audioTrack.release()
        } catch (e: Exception) {
            logE("音频 释放出错：$e")
        } finally {
            resetState()
        }
    }

    private fun resetState() {
        isPlaying.set(false)
        isPause.set(false)
    }


    fun release() {
        try {
            audioTrackIndex = -1
            videoTrackIndex = -1
            resetState()
            mPlayListener = null
            if (this::mAudioMediaExtractor.isInitialized) {
                mAudioMediaExtractor.release()
            }
            if (this::mVideoMediaExtractor.isInitialized) {
                mVideoMediaExtractor.release()
            }
            mMediaSync.playbackParams = PlaybackParams().setSpeed(0.0f)
            mMediaSync.release()
        } catch (e: Throwable) {
            logE("释放出错 ${Log.getStackTraceString(e)}")
        } finally {
            mPlayerScore.cancel()
        }
    }
}