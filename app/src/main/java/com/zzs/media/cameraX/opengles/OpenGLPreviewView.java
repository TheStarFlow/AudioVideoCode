package com.zzs.media.cameraX.opengles;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGLContext;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.AttributeSet;
import android.view.Surface;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author zzs
 * @Date 2021/6/17
 * @describe
 */
public class OpenGLPreviewView extends GLSurfaceView {


    public void setSurfaceTextureListener(PreviewRender.SurfaceTextureListener listener) {
        mRender.setListener(listener);
    }

    private PreviewRender mRender;

    public OpenGLPreviewView(Context context) {
        this(context, null);
    }

    public OpenGLPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public void addSubSurface(RenderSubSurface subSurface) {
        mRender.addSubSurface(subSurface);
    }

    private void init() {
        setEGLContextClientVersion(2);
        mRender = new PreviewRender(this);
        setRenderer(mRender);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    public SurfaceTexture getSurfaceTexture() {
        return mRender.getSurfaceTexture();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mRender.release();
    }

    private interface TextureHandler {

        void setSize(int width, int height);

        void init(Context context, EGLContext eglContext);

        void handleTexture(int texture, long timeStamp);

        void release();

        void start();
    }

    public abstract static class RenderSubSurface implements TextureHandler {
        private Context mContext;
        private EGLContext mEglContext;
        private int width;
        private int height;
        private Handler mHandler;
        private AtomicBoolean mFetchEgl = new AtomicBoolean();
        private AtomicBoolean mStart = new AtomicBoolean();
        private EGLEnvHelper mEnvHelper;
        private CameraOpenGL mOpenGl;
        private Surface mSurface;
        private HandlerThread handlerThread;

        @Override
        public void setSize(int width, int height) {
            this.width = width;
            this.height = height;
        }

        @Override
        public void init(Context context, EGLContext eglContext) {
            this.mContext = context.getApplicationContext();
            this.mEglContext = eglContext;
        }

        @Override
        public void handleTexture(final int texture, final long timeStamp) {
            if (!mFetchEgl.get()) return;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mOpenGl.onDraw(texture);
                    mEnvHelper.dealWith(timeStamp);
                }
            });
        }

        @Override
        public void release() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mOpenGl != null) {
                        mOpenGl.release();
                    }
                    if (mEnvHelper != null) {
                        mEnvHelper.release();
                    }
                    mStart.set(false);
                    handlerThread.quitSafely();
                    mFetchEgl.set(false);
                    mSurface = null;
                    mContext = null;
                    mEglContext = null;
                    mOpenGl = null;
                    mEnvHelper = null;
                }
            });

        }

        @Override
        public void start() {
            if (mStart.get()) return;
            mStart.set(true);
            mSurface = getSurface();
            if (mSurface == null) return;
            handlerThread = new HandlerThread("codec-gl");
            handlerThread.start();
            mHandler = new Handler(handlerThread.getLooper());
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mEnvHelper = new EGLEnvHelper(mContext, mEglContext, mSurface, width, height);
                    mOpenGl = new CameraOpenGL(mContext);
                    mOpenGl.setSize(width, height);
                    mFetchEgl.set(true);
                }
            });
        }

        public abstract Surface getSurface();
    }
}
