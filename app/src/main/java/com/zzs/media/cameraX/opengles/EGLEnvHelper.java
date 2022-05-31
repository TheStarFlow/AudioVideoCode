package com.zzs.media.cameraX.opengles;

import android.content.Context;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.view.Surface;

/**
 * @author zzs
 * @Date 2021/6/17
 * @describe 配置edl
 */
public class EGLEnvHelper {

    private final EGLDisplay mEglDisplay;
    private final EGLConfig mEglConfig;
    private final EGLContext mEglContext;
    private final EGLSurface mEglSurface;

    public EGLEnvHelper(Context context, EGLContext mGlContext, Surface surface, int width, int height) {
        mEglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mEglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("init egl display failed");
        }
        int[] version = new int[2];
        if (!EGL14.eglInitialize(mEglDisplay, version, 0, version, 1)) {
            throw new RuntimeException("eglInitialize failed,check your sdk version");
        }

        int[] config = {EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE};
        int[] numConfigs = new int[1];
        EGLConfig[] configs = new EGLConfig[1];
        //EGL 选择配置
        if (!EGL14.eglChooseConfig(mEglDisplay, config, 0, configs, 0, configs.length,
                numConfigs, 0)) {
            throw new RuntimeException("EGL error " + EGL14.eglGetError());
        }
        mEglConfig = configs[0];
        int[] context_attr_list = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        mEglContext = EGL14.eglCreateContext(mEglDisplay, mEglConfig, mGlContext, context_attr_list, 0);
        if (mEglContext == EGL14.EGL_NO_CONTEXT) {
            throw new RuntimeException("EGL error " + EGL14.eglGetError());
        }
        int[] surface_attr_list = {
                EGL14.EGL_NONE
        };
        mEglSurface = EGL14.eglCreateWindowSurface(mEglDisplay, mEglConfig, surface, surface_attr_list, 0);
        // mEglSurface == null
        if (mEglSurface == null) {
            throw new RuntimeException("EGL error " + EGL14.eglGetError());
        }
        /**
         * 绑定当前线程的显示器display mEglDisplay  虚拟 物理设备
         */
        if (!EGL14.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext)) {
            throw new RuntimeException("EGL error " + EGL14.eglGetError());
        }
    }

    public void release() {
        EGL14.eglDestroySurface(mEglDisplay, mEglSurface);
        EGL14.eglMakeCurrent(mEglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroyContext(mEglDisplay, mEglContext);
        EGL14.eglReleaseThread();
        EGL14.eglTerminate(mEglDisplay);
    }

    public void dealWith(long stamp) {
        EGLExt.eglPresentationTimeANDROID(mEglDisplay, mEglSurface, stamp);
        EGL14.eglSwapBuffers(mEglDisplay, mEglSurface);
    }
}
