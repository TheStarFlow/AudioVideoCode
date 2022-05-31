package com.zzs.media.cameraX

import android.content.Context
import android.util.AttributeSet
import android.view.Surface
import android.widget.FrameLayout
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.core.content.ContextCompat
import com.zzs.media.cameraX.opengles.OpenGLPreviewView

/**
@author  zzs
@Date 2022/5/31
@describe
 */
class CameraRenderView : FrameLayout, Preview.SurfaceProvider {


    private var mSurfaceView: OpenGLPreviewView? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    override fun onSurfaceRequested(request: SurfaceRequest) {
        removeAllViews()
        mSurfaceView = OpenGLPreviewView(context)
        mSurfaceView?.layoutParams =
            LayoutParams(request.resolution.width, request.resolution.height)
        addView(mSurfaceView)
        mSurfaceView?.setSurfaceTextureListener {
            val surface = Surface(it)
            it.setDefaultBufferSize(request.resolution.width, request.resolution.height)
            request.provideSurface(surface, ContextCompat.getMainExecutor(context)) { result ->
                result.surface.release()
                it.release()
            }
        }

    }
}