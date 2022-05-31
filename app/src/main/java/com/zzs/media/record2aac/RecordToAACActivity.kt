package com.zzs.media.record2aac

import android.Manifest
import android.media.*
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.zzs.media.checkPermission
import com.zzs.media.databinding.ActivityRecord2AacBinding
import com.zzs.media.onClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue

/**
@author  zzs
@Date 2022/5/25
@describe  录音并且编码成aac文件
 */
class RecordToAACActivity : AppCompatActivity() {
    lateinit var binding: ActivityRecord2AacBinding
    private lateinit var mAudioRecorder: AudioRecord

    companion object {
        const val AUDIO_SAMPLE_RATE = 44100//采样率
        const val AUDIO_PCM_BIT = AudioFormat.ENCODING_PCM_16BIT//采样位数
        const val AUDIO_CHANNEL = AudioFormat.CHANNEL_IN_STEREO //音频声道 单声道
    }

    var pcmFile = ""

    @Volatile
    private var isRecording = false
    private var miniBufferSize = 0
    private val mPcmCacheData by lazy { LinkedBlockingQueue<ByteArray>() }
    private lateinit var mMediaCodec: MediaCodec

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecord2AacBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initAudioRecord()
        pcmFile = cacheDir.absolutePath + "/record.pcm"
        val path = cacheDir.absolutePath + "/record.aac"
        binding.startRecord.onClick {
            startRecord(path)
        }
        binding.stopRecord.onClick {
            stopRecord()
        }
        checkPermission(
            arrayOf(
                Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        )
        val file = File(path)
        if (file.exists()) {
            showFileOutput(file)
        }
    }

    private fun stopRecord() {
        binding.loadingView.root.visibility = View.GONE
        if (mAudioRecorder.state==AudioRecord.RECORDSTATE_RECORDING){
            mAudioRecorder.stop()
            isRecording = false
        }
    }

    private fun startRecord(path: String) {
        binding.loadingView.root.visibility = View.VISIBLE
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                recordAndFetchPcm()
            }
        }
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                encodeToAcc(path)
            }
        }
    }

    private fun recordAndFetchPcm() {
        if (mAudioRecorder.state == AudioRecord.STATE_INITIALIZED) {
            isRecording = true
            showLogI("开始录音获取数据")
            val byteBuffer = ByteBuffer.allocateDirect(miniBufferSize)
            mAudioRecorder.startRecording()
            while (isRecording) {
                val readLen = mAudioRecorder.read(byteBuffer, miniBufferSize)
                if (readLen > 0) {
                    val pcmData = ByteArray(readLen)
                    byteBuffer.get(pcmData, 0, readLen)
                    mPcmCacheData.offer(pcmData)
                    byteBuffer.position()
                    byteBuffer.clear()
                    showLogI("读取pcm数据长度 :$readLen")
                }
            }
        }
    }

    private fun encodeToAcc(path: String) {
        if (!this::mMediaCodec.isInitialized) {
            mMediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        }
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC, AUDIO_SAMPLE_RATE,
            1
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, 96000)
        format.setInteger(
            MediaFormat.KEY_AAC_PROFILE,
            MediaCodecInfo.CodecProfileLevel.AACObjectLC
        )
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, miniBufferSize * 2)
        mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mMediaCodec.start()
        val startTime = System.nanoTime()
        val mediaInfo = MediaCodec.BufferInfo()
        val outputFile = File(path)
        if (outputFile.exists()) {
            outputFile.delete()
        }
        outputFile.createNewFile()
        val fos = FileOutputStream(outputFile)
        try {
            showLogI("开始编码，存储路径${outputFile.absolutePath}")
            while (isRecording || mPcmCacheData.isNotEmpty()) {
                val pcmData = mPcmCacheData.poll() ?: continue
                val inputBufferIndex = mMediaCodec.dequeueInputBuffer(1000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = mMediaCodec.getInputBuffer(inputBufferIndex)
                    inputBuffer?.clear()
                    inputBuffer?.put(pcmData, 0, pcmData.size)
                    mMediaCodec.queueInputBuffer(
                        inputBufferIndex,
                        0,
                        pcmData.size,
                        (System.nanoTime() - startTime) / 1000,
                        0
                    )
                    var outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mediaInfo, 1000)
                    while (outputBufferIndex >= 0) {
                        val outputBuffer = mMediaCodec.getOutputBuffer(outputBufferIndex)
                        outputBuffer?.position(mediaInfo.offset)
                        val encodeData = ByteArray(mediaInfo.size + 7)//添加aac编码头
                        addADTStoPacket(encodeData, encodeData.size)
                        outputBuffer?.get(encodeData, 7, mediaInfo.size)
                        outputBuffer?.clear()
                        fos.write(encodeData)
                        fos.flush()
                        mMediaCodec.releaseOutputBuffer(outputBufferIndex, false)
                        outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mediaInfo, 1000)

                    }
                }
            }
        } finally {
            mMediaCodec.stop()
            mMediaCodec.release()
            fos.close()
            showLogI("编码完成，输出文件")
            runOnUiThread {
                showFileOutput(outputFile)
            }
        }
    }

    private fun showFileOutput(outputFile: File) {
        binding.resulyLayout.visibility = View.VISIBLE
        binding.recordFileName.text = outputFile.name
        binding.filePath.text = outputFile.absolutePath
        binding.playRecord.onClick {
            playRecord(outputFile)
        }
    }

    /**
     *
     * aac解码播放文件
     * */
    private fun playRecord(outputFile: File) {
        lifecycleScope.launch(Dispatchers.IO) {
            val mediaExtractor = MediaExtractor()
            mediaExtractor.setDataSource(outputFile.absolutePath)
            val count = mediaExtractor.trackCount
            var index = -1
            for (i in 0 until count) {
                val trackFormat = mediaExtractor.getTrackFormat(i)
                val type = trackFormat.getString(MediaFormat.KEY_MIME)
                if (type?.startsWith("audio/")==true) {
                    index = i
                    break
                }
            }
            if (index == -1) {
                mediaExtractor.release()
                return@launch
            }
            decodeAndPlay(mediaExtractor, index)
        }
    }

    private fun decodeAndPlay(mediaExtractor: MediaExtractor, index: Int) {
        val file = File(pcmFile)
        if (file.exists()) {
            file.delete()
        }
        file.createNewFile()
        val channel = FileOutputStream(file).channel
        mediaExtractor.selectTrack(index)
        // mediaExtractor.seekTo(0,MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        val format = mediaExtractor.getTrackFormat(index)
        var maxBuffer = 4096
        if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            maxBuffer = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
        }
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return
        val mediaCodec = MediaCodec.createDecoderByType(mime)
        mediaCodec.configure(format, null, null, 0)
        val readAACBuffer = ByteBuffer.allocateDirect(maxBuffer)
        val mediaInfo = MediaCodec.BufferInfo()
        mediaCodec.start()
        var inputBufferIndex = -1
        val count = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)){
            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        }else{
            1
        }
        val outChannel = if (count>1) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_IN_MONO
        val attributeBuilder = AudioAttributes.Builder()
        attributeBuilder.setUsage(AudioAttributes.USAGE_MEDIA)
            .setLegacyStreamType(AudioManager.STREAM_MUSIC)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        val formatBuilder = AudioFormat.Builder()
        formatBuilder.setSampleRate(format.getInteger(MediaFormat.KEY_SAMPLE_RATE))
            .setChannelMask(outChannel)
            .setEncoding(AUDIO_PCM_BIT)
        val mini = AudioTrack.getMinBufferSize(
            AUDIO_SAMPLE_RATE, outChannel,
            AUDIO_PCM_BIT
        )
//        val audioTrack = AudioTrack(
//            AudioManager.STREAM_MUSIC,
//            format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
//            outChannel,
//            AUDIO_PCM_BIT,
//            mini,
//            AudioTrack.MODE_STREAM
//        )
        val audioTrack = AudioTrack(
            attributeBuilder.build(),
            formatBuilder.build(),
            mini,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        audioTrack.play()
        var isEnd = false
        while (!isEnd) {
            inputBufferIndex = mediaCodec.dequeueInputBuffer(1000)
            if (inputBufferIndex >= 0) {
                val inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex)
                inputBuffer?.clear()
                val pts = mediaExtractor.sampleTime
                if (pts.toInt() == -1) {
                    break
                }
                mediaInfo.presentationTimeUs = pts
                mediaInfo.flags = mediaExtractor.sampleFlags
                mediaInfo.size = mediaExtractor.readSampleData(readAACBuffer, 0)
                inputBuffer?.put(readAACBuffer)
                readAACBuffer.flip()
                mediaCodec.queueInputBuffer(
                    inputBufferIndex,
                    0,
                    mediaInfo.size,
                    mediaInfo.presentationTimeUs,
                    mediaInfo.flags
                )
                if (!mediaExtractor.advance()) {
                    break
                }
                var outputBufferIndex = mediaCodec.dequeueOutputBuffer(mediaInfo, 1000)
                while (outputBufferIndex >= 0) {
                    if (mediaInfo.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                        isEnd = true
                        break
                    }
                    val outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex)
                    channel.write(outputBuffer)
                    outputBuffer?.position(mediaInfo.offset)
                    outputBuffer?.run {
                        audioTrack.write(
                            outputBuffer,
                            mediaInfo.size,
                            AudioTrack.WRITE_BLOCKING
                        )
                    }
                    mediaCodec.releaseOutputBuffer(outputBufferIndex, false)
                    outputBufferIndex = mediaCodec.dequeueOutputBuffer(mediaInfo, 1000)
                }
                readAACBuffer.clear()
                readAACBuffer.flip()
            }
        }
        channel.close()
        showLogI("播放完毕")
        while (audioTrack.playState!=AudioTrack.PLAYSTATE_STOPPED){ }
        audioTrack.stop()
        audioTrack.release()
        mediaCodec.stop()
        mediaCodec.release()
        mediaExtractor.release()
    }

    /**
     * 添加ADTS头
     */
    private fun addADTStoPacket(packet: ByteArray, packetLen: Int) {
        val profile = 2 // AAC LC
        val freqIdx = 4 // 44.1KHz
        val chanCfg = 2 // CPE
        // fill in ADTS data
        packet[0] = 0xFF.toByte()
        packet[1] = 0xF9.toByte()
        packet[2] = ((profile - 1 shl 6) + (freqIdx shl 2) + (chanCfg shr 2)).toByte()
        packet[3] = ((chanCfg and 3 shl 6) + (packetLen shr 11)).toByte()
        packet[4] = (packetLen and 0x7FF shr 3).toByte()
        packet[5] = ((packetLen and 7 shl 5) + 0x1F).toByte()
        packet[6] = 0xFC.toByte()
    }


    private fun initAudioRecord() {
        miniBufferSize = AudioRecord.getMinBufferSize(
            AUDIO_SAMPLE_RATE, AUDIO_CHANNEL,
            AUDIO_PCM_BIT
        )
        if (miniBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(
                javaClass.simpleName,
                "audio config is not support,sample rate :${AUDIO_CHANNEL},channel :${AUDIO_CHANNEL}" +
                        ",audio pcm bit : $AUDIO_PCM_BIT"
            )
            return
        }
        mAudioRecorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            AUDIO_SAMPLE_RATE,
            AUDIO_CHANNEL,
            AUDIO_PCM_BIT,
            miniBufferSize
        )
    }

    override fun onStop() {
        super.onStop()
        if (this::mMediaCodec.isInitialized){
            mMediaCodec.release()
            mAudioRecorder.stop()
            mAudioRecorder.release()
        }

    }

    private fun showLogI(msg: String) {
        Log.i(javaClass.simpleName, "--->$msg")
    }

    private fun showLoge(msg: String) {
        Log.e(javaClass.simpleName, "--->$msg")
    }
}