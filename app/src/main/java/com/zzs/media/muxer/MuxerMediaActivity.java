package com.zzs.media.muxer;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.SeekBar;

import com.google.android.material.slider.RangeSlider;
import com.zzs.media.databinding.ActivityMuxerMediaBinding;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 音频与视频混音剪辑
 * */

public class MuxerMediaActivity extends AppCompatActivity implements RangeSlider.OnChangeListener, SeekBar.OnSeekBarChangeListener {

    private ActivityMuxerMediaBinding binding;
    private float startValue = 0f;
    private float endValue = 0f;
    private int videoVolume = 0;
    private int bgVolume = 0;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ProgressDialog progressDialog;
    private Executor executor = Executors.newCachedThreadPool();

    private final String videoPath = Environment.getExternalStorageDirectory() + "/demo.mp4";
    private final String bgPath = Environment.getExternalStorageDirectory() + "/bg.mp3";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMuxerMediaBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }// 在人耳范围 0 - 20000Hz   根据采样定理 44000qhz
        VideoProcess.init(getApplicationContext());
        checkPermission();
        initView();
        doBusiness();
    }

    private void doBusiness() {
        progressDialog.show();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    File video = new File(videoPath);
                    if (!video.exists()) {
                        copyAssets("demo.mp4", video.getAbsolutePath());
                    }
                    File bg = new File(bgPath);
                    if (!bg.exists()) {
                        copyAssets("bg.mp3", bg.getAbsolutePath());
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            binding.videoView.setVideoPath(video.getAbsolutePath());
                            binding.videoView.start();
                            progressDialog.dismiss();
                        }
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                }
                finally {
                    progressDialog.dismiss();
                }

            }
        });
    }

    private void initView() {
        binding.rangeSlider.addOnChangeListener(this);
        binding.rangeSlider.setValues(0.0f, 1.0f);
        binding.videoSeekBar.setOnSeekBarChangeListener(this);
        binding.bgSeekBar.setOnSeekBarChangeListener(this);
        progressDialog = new ProgressDialog(this);
        binding.outputVideo.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mp.start();
            }
        });
        binding.videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mp.start();
            }
        });
    }

    public boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 1);

        }
        return false;
    }


    private void copyAssets(String assetsName, String path) throws IOException {
        AssetFileDescriptor assetFileDescriptor = getAssets().openFd(assetsName);
        FileChannel from = new FileInputStream(assetFileDescriptor.getFileDescriptor()).getChannel();
        File file = new File(path);
        if (!file.exists()){
            boolean create = file.createNewFile();
            if (!create)return;
        }
        FileChannel to = new FileOutputStream(path).getChannel();
        from.transferTo(assetFileDescriptor.getStartOffset(), assetFileDescriptor.getLength(), to);
        from.close();
        to.close();
    }

    @Override
    public void onValueChange(@NonNull RangeSlider slider, float value, boolean fromUser) {
        List<Float> list = slider.getValues();
        startValue = list.get(0);
        endValue = list.get(1);

    }

    public void onClip(View view) {
        progressDialog.show();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                File outFile = new File(getCacheDir(),"output.mp4");
                try {
                    MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
                    metadataRetriever.setDataSource(videoPath);
                    final long duration = Integer.parseInt(metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))* 1000L;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            binding.oriDuration.setText(String.valueOf(duration));
                        }
                    });
                    VideoProcess.mixMusic(videoPath,bgPath,outFile.getAbsolutePath(),(long) (startValue*duration),(long) (endValue*duration),videoVolume,bgVolume);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                            binding.outputVideo.setVideoPath(outFile.getAbsolutePath());
                            binding.outputVideo.start();
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (seekBar.getId() == binding.bgSeekBar.getId()) {
            bgVolume = progress;
        }
        if (seekBar.getId() == binding.videoSeekBar.getId()) {
            videoVolume = progress;
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    public void onOriControl(View view) {
        if (binding.videoView.isPlaying()) {
            binding.videoView.pause();
        } else {
            binding.videoView.start();
        }
    }
}