package com.zzs.media.cameraX

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Surface
import android.widget.FrameLayout
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCase
import androidx.camera.core.impl.ImageOutputConfig
import androidx.core.content.ContextCompat
import com.zzs.media.cameraX.opengles.OpenGLPreviewView

/**
@author  zzs
@Date 2022/5/31
@describe  自己提供surface 并使用 openGl 预览，但是对于输出转换，看不懂实现 google 的源码，搬运不过来
 */
class CameraRenderView : OpenGLPreviewView, Preview.SurfaceProvider {



    private var mUseCase:UseCase?=null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    @SuppressLint("RestrictedApi")
    override fun onSurfaceRequested(request: SurfaceRequest) {
       surfaceTexture?.let {
           val surface = Surface(it)
           it.setDefaultBufferSize(request.resolution.width, request.resolution.height)
           request.provideSurface(surface, ContextCompat.getMainExecutor(context)) { result ->
               result.surface.release()
               it.release()
           }
       }
        mUseCase?.run {
            val degree = request.camera.cameraInfo.sensorRotationDegrees
            val rect = Rect(0,0,500,500)
            request.updateTransformationInfo(SurfaceRequest.
            TransformationInfo.of(rect,degree,(currentConfig as ImageOutputConfig).getAppTargetRotation(ImageOutputConfig.ROTATION_NOT_SPECIFIED)))
        }

    }

    fun attach(useCase: UseCase?){
        mUseCase = useCase
    }

}