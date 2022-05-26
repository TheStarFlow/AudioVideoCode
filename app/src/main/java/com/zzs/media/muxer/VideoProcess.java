package com.zzs.media.muxer;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @author zzs
 * @Date 2021/3/23
 * @describe
 */
public class VideoProcess {
    private static Context sContext;

    public static void init(Context context) {
        sContext = context.getApplicationContext();
    }


    public static void mixMusic(final String videoPath,
                                final String musicPath,
                                final String outputPath,
                                final long startUs,
                                final long endUs,
                                int videoVolume,
                                int musicVolume) throws Exception {
        final File cache = sContext.getCacheDir();
        final File videoPcm = new File(cache, "videoPcm.pcm");
        final File musicPcm = new File(cache, "musicPcm.pcm");
        final File mixPcm = new File(cache, "adjustPcm.pcm");

        decodeToPcm(videoPath, videoPcm.getAbsolutePath(), startUs, endUs);
        File videoPcmFile = new File(cache, videoPcm.getName() + ".wav");
        new PcmToWavUtil(44100, AudioFormat.CHANNEL_IN_STEREO,
                1, AudioFormat.ENCODING_PCM_16BIT).pcmToWav(videoPcm.getAbsolutePath()
                , videoPcmFile.getAbsolutePath());

        decodeToPcm(musicPath, musicPcm.getAbsolutePath(), startUs, endUs);
        File musicPcmFile = new File(cache, musicPcm.getName() + ".wav");
        new PcmToWavUtil(44100, AudioFormat.CHANNEL_IN_STEREO,
                1, AudioFormat.ENCODING_PCM_16BIT).pcmToWav(musicPcm.getAbsolutePath()
                , musicPcmFile.getAbsolutePath());

        remixPcm(videoPcm.getAbsolutePath(), musicPcm.getAbsolutePath(), mixPcm.getAbsolutePath(), videoVolume, musicVolume);
        File wavFile = new File(cache, mixPcm.getName() + ".wav");
        new PcmToWavUtil(44100, AudioFormat.CHANNEL_IN_STEREO,
                2, AudioFormat.ENCODING_PCM_16BIT).pcmToWav(mixPcm.getAbsolutePath()
                , wavFile.getAbsolutePath());

        
        mixVideoAndMusic(videoPath,outputPath,startUs,endUs,wavFile);


    }

    private static void mixVideoAndMusic(String videoInput, String output, long startTimeUs, long endTimeUs, File wavFile) throws IOException {

        File file = new File(output);
        if (file.exists()){
            file.delete();
        }
        //初始化一个媒体文件（mp4等封装格式）封装容器
        MediaMuxer mediaMuxer = new MediaMuxer(output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        //视频解析器
        MediaExtractor videoExtractor = new MediaExtractor();
        videoExtractor.setDataSource(videoInput);
        //视频轨道
        final int videoTrackIndex = selectTrack(videoExtractor,false);
        final int audioTrackIndex = selectTrack(videoExtractor,true);
        //获取原视频格式
        MediaFormat videoFormat = videoExtractor.getTrackFormat(videoTrackIndex);
        //添加轨道
        final int newVideoTrackIndex = mediaMuxer.addTrack(videoFormat);
        MediaFormat audioFormat = videoExtractor.getTrackFormat(audioTrackIndex);
        //输出文件音频改为aac
        audioFormat.setString(MediaFormat.KEY_MIME,MediaFormat.MIMETYPE_AUDIO_AAC);
        final  int audioBitrate =audioFormat.getInteger(MediaFormat.KEY_BIT_RATE);
        final int newAudioTranIndex = mediaMuxer.addTrack(audioFormat);
        mediaMuxer.start();

        //音频解析
        MediaExtractor audioExtractor = new MediaExtractor();
        audioExtractor.setDataSource(wavFile.getAbsolutePath());
        int max = 100*1000;
        int wavAudioTrackIndex = selectTrack(audioExtractor,true);
        audioExtractor.selectTrack(wavAudioTrackIndex);
        MediaFormat wavFormat = audioExtractor.getTrackFormat(wavAudioTrackIndex);
        int channelCount = wavFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        int sampleRate = wavFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);

        if (wavFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)){
            max = wavFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        }

        //新输出的音频格式
        MediaFormat newAudioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate,channelCount);
        newAudioFormat.setInteger(MediaFormat.KEY_BIT_RATE,audioBitrate);
        newAudioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        newAudioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, max);
        //下面解码wav获取PCM写入相应的音频轨道
        MediaCodec encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        encoder.configure(newAudioFormat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        ByteBuffer buffer = ByteBuffer.allocateDirect(max);
        boolean encodeEnd =false;
        while (!encodeEnd){
            int inputIndex = encoder.dequeueInputBuffer(10000);
            if (inputIndex>=0){
                long audioTime = audioExtractor.getSampleTime();
                if (audioTime<0){
                    encoder.queueInputBuffer(inputIndex,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                }else {
                    int flag = audioExtractor.getSampleFlags();
                    int size = audioExtractor.readSampleData(buffer,0);
                    ByteBuffer dpsInputBuffer = encoder.getInputBuffer(inputIndex);
                    dpsInputBuffer.clear();
                    dpsInputBuffer.put(buffer);
                    dpsInputBuffer.position(0);
                    encoder.queueInputBuffer(inputIndex,0,size,audioTime,flag);
                    audioExtractor.advance();
                }
                int outIndex = encoder.dequeueOutputBuffer(info,10000);
                while (outIndex>=0){
                    if (info.flags==MediaCodec.BUFFER_FLAG_END_OF_STREAM){
                        encodeEnd = true;
                        break;
                    }
                    ByteBuffer dpsOutputBuffer = encoder.getOutputBuffer(outIndex);
                    mediaMuxer.writeSampleData(newAudioTranIndex,dpsOutputBuffer,info);
                    dpsOutputBuffer.clear();
                    encoder.releaseOutputBuffer(outIndex,false);
                    outIndex = encoder.dequeueOutputBuffer(info,10000);
                }
            }
        }
        //音频写入完毕 准备写入视频
        videoExtractor.selectTrack(videoTrackIndex);
        videoExtractor.seekTo(startTimeUs,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        max = videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        buffer = ByteBuffer.allocate(max);
        buffer.clear();
        while (true){
            long sampleTime = videoExtractor.getSampleTime();
            if (sampleTime==-1){
                break;
            }
            if (sampleTime>endTimeUs)
                break;
            if (sampleTime<startTimeUs){
                videoExtractor.advance();
                continue;
            }
            info.flags = videoExtractor.getSampleFlags();
            info.size = videoExtractor.readSampleData(buffer,0);
            info.presentationTimeUs = sampleTime-startTimeUs+500;
            if (info.size<0){
                break;
            }
            //直接从 解析器 把 视频轨道 的数据写到新的 视频容器 的 视频轨道
            mediaMuxer.writeSampleData(newVideoTrackIndex,buffer,info);
            videoExtractor.advance();
        }

        try {
            videoExtractor.release();
            audioExtractor.release();
            encoder.stop();
            encoder.release();
            mediaMuxer.release();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public static void decodeToPcm(String srcPath, String pcmPath, long start, long end) throws Exception {
        if (end < start) return;
        //媒体文件解析器 获取 媒体格式
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(srcPath);
        //选择音频轨道
        int audioTrack = selectTrack(extractor, true);
        extractor.selectTrack(audioTrack);
        extractor.seekTo(start, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        //获取这条轨道的媒体格式
        MediaFormat audioFormat = extractor.getTrackFormat(audioTrack);
        int max = 50 * 1000;
        if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            max = audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        }
        //申请缓存存放数据
        ByteBuffer buffer = ByteBuffer.allocate(max);
        //创建一个解码器解码pcm数据
        MediaCodec mediaCodec = MediaCodec.createDecoderByType(audioFormat.getString(MediaFormat.KEY_MIME));
        //给解码器配置音频的格式
        mediaCodec.configure(audioFormat, null, null, 0);
        //创建pcm输出文件  用以接收 解码出来的pcm文件
        File outFile = new File(pcmPath);
        FileChannel channel = new FileOutputStream(outFile).getChannel();
        //解码开始
        mediaCodec.start();
        //接收解码信息 一个存放信息的对象
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int inputBufferIndex = -1;
        for (; ; ) {
            inputBufferIndex = mediaCodec.dequeueInputBuffer(1000);
            if (inputBufferIndex >= 0) {
                //获取当前解码音频时间
                long sampleTime = extractor.getSampleTime();
                if (sampleTime == -1 || sampleTime > end) {
                    break;
                } else if (sampleTime < start) {
                    //轮转下一个数据
                    extractor.advance();
                    continue;
                }
                //获取解码的一些信息跟数据
                info.flags = extractor.getSampleFlags();
                info.presentationTimeUs = sampleTime;
                info.size = extractor.readSampleData(buffer, 0);
                //把数据给Dsp
                ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex);
                inputBuffer.clear();
                inputBuffer.put(buffer);
                buffer.flip();
                //通知dsp解码
                mediaCodec.queueInputBuffer(inputBufferIndex, 0, info.size, info.presentationTimeUs, info.flags);
                extractor.advance();
            }
            int outputBufferIndex = mediaCodec.dequeueOutputBuffer(info, 1000);
            while (outputBufferIndex >= 0) {
                ByteBuffer outBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);
                channel.write(outBuffer);
                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = mediaCodec.dequeueOutputBuffer(info, 1000);
            }
        }
        mediaCodec.stop();
        mediaCodec.release();
        channel.close();
        extractor.release();
    }


    //根据储存方式混音
    public static void remixPcm(String pcm1, String pcm2, String outPath, int volume1, int volume2) throws Exception {
        float v1 = normaVolume(volume1);
        float v2 = normaVolume(volume2);
        byte[] buffer1 = new byte[2048];
        byte[] buffer2 = new byte[2048];
        byte[] buffer3 = new byte[2048];
        FileInputStream is1 = new FileInputStream(pcm1);
        FileInputStream is2 = new FileInputStream(pcm2);
        FileOutputStream out3 = new FileOutputStream(outPath);
        boolean end1 = false;
        boolean end2 = false;
        short temp2, temp1;
        int temp;
        try {
            while (!end1 || !end2) {

                if (!end1) {
                    end1 = (is1.read(buffer1) == -1);
                    System.arraycopy(buffer1, 0, buffer3, 0, buffer1.length);
                }
                if (!end2) {
                    end2 = (is2.read(buffer2) == -1);
                    for (int i = 0; i < buffer2.length; i += 2) {
                        temp1 = (short) ((buffer1[i] & 0xff) | (buffer1[i + 1] & 0xff) << 8);
                        temp2 = (short) ((buffer2[i] & 0xff) | (buffer2[i + 1] & 0xff) << 8);
                        temp = (int) (temp2 * v2 + temp1 * v1);
                        if (temp > 32767) {
                            temp = 32767;
                        } else if (temp < -32768) {
                            temp = -32768;
                        }
                        buffer3[i] = (byte) (temp & 0xFF);
                        buffer3[i + 1] = (byte) ((temp >>> 8) & 0xFF);
                    }
                }
                out3.write(buffer3);
            }
        } finally {
            is1.close();
            is2.close();
            out3.close();
        }
    }

    public static void mixPcm(String pcm1Path, String pcm2Path, String toPath
            , int vol1, int vol2) throws IOException {
        float volume1 = normaVolume(vol1);
        float volume2 = normaVolume(vol2);
        byte[] buffer1 = new byte[2048];
        byte[] buffer2 = new byte[2048];
        byte[] buffer3 = new byte[2048];

        FileInputStream is1 = new FileInputStream(pcm1Path);
        FileInputStream is2 = new FileInputStream(pcm2Path);

        FileOutputStream fileOutputStream = new FileOutputStream(toPath);

        boolean end1 = false, end2 = false;
        short temp2, temp1;
        int temp;
        try {
            while (!end1 || !end2) {
                if (!end1) {
                    end1 = (is1.read(buffer1) == -1);

                    System.arraycopy(buffer1, 0, buffer3, 0, buffer1.length);
                }
                if (!end2) {
                    end2 = (is2.read(buffer2) == -1);
                    int voice = 0;
                    StringBuilder stringBuilder = new StringBuilder();
                    for (int i = 0; i < buffer2.length; i += 2) {
                        temp1 = (short) ((buffer1[i] & 0xff) | (buffer1[i + 1] & 0xff) << 8);
                        stringBuilder.append(temp1 + " ");
                        temp2 = (short) ((buffer2[i] & 0xff) | (buffer2[i + 1] & 0xff) << 8);
                        temp = (int) (temp2 * volume2 + temp1 * volume1);
                        if (temp > 32767) {
                            temp = 32767;
                        } else if (temp < -32768) {
                            temp = -32768;
                        }
                        buffer3[i] = (byte) (temp & 0xFF);
                        buffer3[i + 1] = (byte) ((temp >>> 8) & 0xFF);
                    }
                    //Log.i(TAG, "mixPcm: " + stringBuilder.toString());
                }
                fileOutputStream.write(buffer3);
            }
        } finally {
            is1.close();
            is2.close();
            fileOutputStream.close();
        }
    }

    private static float normaVolume(int volume) {
        return volume / 100f * 1;
    }

    public static int selectTrack(MediaExtractor extractor, boolean audio) {
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (audio) {
                if (mime.startsWith("audio/")) {
                    return i;
                }
            } else {
                if (mime.startsWith("video/")) {
                    return i;
                }
            }
        }
        return -5;
    }
}
