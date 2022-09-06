#include <jni.h>
#include <string>

extern "C" {
    #include "libavcodec/avcodec.h"
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_zzs_media_ffmpegplayer_FFmpegPlayerActivity_getStringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = avcodec_configuration();
    return env->NewStringUTF(hello.c_str());
}