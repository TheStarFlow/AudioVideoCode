package com.yougu.audiopcm;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @author zzs
 * @Date 2021/3/9
 * @describe
 */
public class MusicProcess {

    // mp3 -> pcm  -> mp3
    public void clip(String srcPath, String outPath, int start, int end) {
        if (end < start) return;
        MediaExtractor mediaExtractor = new MediaExtractor();
        try {
            mediaExtractor.setDataSource(srcPath);
            int audioTrack = selectTrack(mediaExtractor);
            mediaExtractor.selectTrack(audioTrack);//！！！选择好特定的轨道
            mediaExtractor.seekTo(start, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            //准备接收压缩数据 要确定接收容器大小
            MediaFormat format = mediaExtractor.getTrackFormat(audioTrack);
            int maxBufferSize = 100 * 1000;
            if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                maxBufferSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
            }
            //buffer 接收MediaExtractor 提取出来的压缩MP3数据
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(maxBufferSize);


            File pcmToAAcFile = new File(Environment.getExternalStorageDirectory(), "out.pcm");
            FileChannel channel = new FileOutputStream(pcmToAAcFile).getChannel();

            MediaCodec mediaCodec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            //设置解码器信息 直接从封装文件通道获取的解码器信息
            mediaCodec.configure(format, null, null, 0);
            mediaCodec.start();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (true) {
                int decodeIndex = mediaCodec.dequeueInputBuffer(10000);
                if (decodeIndex >= 0) {
                    long simple = mediaExtractor.getSampleTime();
                    if (simple == -1) {
                        break;//音频结束了直接退出
                    } else if (simple < start) {
                        mediaExtractor.advance();//还没有seekto到指定的时间 这次的数据不处理 直接快进到下一个
                        continue;

                    } else if (simple > end) {
                        break;
                    } else {
                        //获取数据   处理数据解码 压缩数据
                        info.size = mediaExtractor.readSampleData(byteBuffer, 0);
                        info.presentationTimeUs = simple;
                        info.flags = mediaExtractor.getSampleFlags();
                        //byteBuffer //里面放 的 压缩数据
                        byte[] bytes = new byte[byteBuffer.remaining()];
                        byteBuffer.get(bytes);
                        ByteBuffer sDpsByteBuffer = mediaCodec.getInputBuffer(decodeIndex);
                        sDpsByteBuffer.put(bytes);
                        mediaCodec.queueInputBuffer(decodeIndex, 0, info.size, info.presentationTimeUs, info.flags);//通知mediacodec解码
                        mediaExtractor.advance();//释放上一帧的压缩数据
                    }
                    int outputIndex = mediaCodec.dequeueOutputBuffer(info, 10000);
                    while (outputIndex >= 0) {
                        ByteBuffer sDpsOutBuffer = mediaCodec.getOutputBuffer(outputIndex);
                        channel.write(sDpsOutBuffer);
                        mediaCodec.releaseOutputBuffer(outputIndex, false);
                        outputIndex = mediaCodec.dequeueOutputBuffer(info, 10000);
                    }

                }
            }
            channel.close();
            mediaExtractor.release();
            mediaCodec.stop();
            mediaCodec.release();

            File wavFile = new File(outPath);
            new PcmToWavUtil(44100, AudioFormat.CHANNEL_IN_STEREO,
                    2, AudioFormat.ENCODING_PCM_16BIT).pcmToWav(pcmToAAcFile.getAbsolutePath()
                    , wavFile.getAbsolutePath());
            Log.i("David", "mixAudioTrack: 转换完毕");


            // new PcmToWavUtil().pcmToWav(pcmToAAcFile.getAbsolutePath(),outPath);
            // PcmToMp3.convertAudioFiles(pcmToAAcFile.getAbsolutePath(), outPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //选择音轨

    private int selectTrack(MediaExtractor mediaExtractor) {
        int count = mediaExtractor.getTrackCount();//音轨数量
        for (int i = 0; i < count; i++) {
            MediaFormat format = mediaExtractor.getTrackFormat(i); //音轨配置
            String mime = format.getString(MediaFormat.KEY_MIME);  //获取轨道格式
            if (mime.startsWith("audio/")) { //音频 以 audio开头  视频以video开头
                return i;
            }
        }
        return -1;
    }


    public static void mixPcmTrack(String videoInput
            , String audioInput, String output
            , Integer start, Integer end
            , int videoVolume, int audioVolume) throws Exception{

    }
}
