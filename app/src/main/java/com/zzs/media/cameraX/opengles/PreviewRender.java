package com.zzs.media.cameraX.opengles;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import java.util.concurrent.LinkedBlockingDeque;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * @author zzs
 * @Date 2021/6/17
 * @describe
 */
public class PreviewRender implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    private GLSurfaceView mGlSurface;
    private SurfaceTexture mSurfaceTexture;
    private CameraOpenGL mCameraOpenGlRender;
    private CameraFBOOpenGL mCameraExtRender;
    private int[] mTexture = new int[1];
    private float[] mMatrix = new float[16];
    private SurfaceTextureListener listener;
    private int width;
    private int height;
    private LinkedBlockingDeque<OpenGLPreviewView.RenderSubSurface> mSurfaces = new LinkedBlockingDeque<>();

    public PreviewRender(GLSurfaceView mGlSurface) {
        this.mGlSurface = mGlSurface;
    }

    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
        GLES20.glGenTextures(1, mTexture, 0);
        mSurfaceTexture = new SurfaceTexture(mTexture[0]);
        mSurfaceTexture.setOnFrameAvailableListener(this);
        if (listener != null) {
            listener.onSurfaceTextureCreate(mSurfaceTexture);
        }
        mCameraOpenGlRender = new CameraOpenGL(mGlSurface.getContext());
        mCameraExtRender = new CameraFBOOpenGL(mGlSurface.getContext());
    }

    @Override
    public void onSurfaceChanged(GL10 gl10, int width, int height) {
        this.width = width;
        this.height = height;
        mCameraOpenGlRender.setSize(width, height);
        mCameraExtRender.setSize(width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl10) {
        mSurfaceTexture.updateTexImage();
        mSurfaceTexture.getTransformMatrix(mMatrix);
        mCameraExtRender.setMatrix(mMatrix);
        mCameraOpenGlRender.setMatrix(mMatrix);
        int id = mCameraExtRender.onDraw(mTexture[0]);
        mCameraOpenGlRender.onDraw(id);
        handleSubSurface(id);
    }

    private void handleSubSurface(int id) {
        for (OpenGLPreviewView.RenderSubSurface subSurface : mSurfaces) {
            subSurface.init(mGlSurface.getContext(), EGL14.eglGetCurrentContext());
            subSurface.setSize(width, height);
            subSurface.start();
            subSurface.handleTexture(id, mSurfaceTexture.getTimestamp());
        }
    }

    public SurfaceTexture getSurfaceTexture() {
        return mSurfaceTexture;
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        mGlSurface.requestRender();
    }

    public void setListener(SurfaceTextureListener listener) {
        this.listener = listener;
    }

    public interface SurfaceTextureListener {
        void onSurfaceTextureCreate(SurfaceTexture surfaceTexture);
    }

    public void release() {
        mSurfaceTexture.release();
        mCameraOpenGlRender.release();
        mCameraExtRender.release();
        for (OpenGLPreviewView.RenderSubSurface subSurface : mSurfaces) {
            subSurface.release();
        }
        mSurfaces.clear();
        listener = null;
    }

    public void addSubSurface(OpenGLPreviewView.RenderSubSurface subSurface) {
        mSurfaces.add(subSurface);
    }
}
