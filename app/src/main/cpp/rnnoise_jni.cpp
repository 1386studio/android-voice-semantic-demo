#include <jni.h>
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <rnnoise.h>

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_voicesemantic_RnnoiseBridge_createNative(JNIEnv *, jclass) {
    return reinterpret_cast<jlong>(rnnoise_create(nullptr));
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_voicesemantic_RnnoiseBridge_processNative(
        JNIEnv *env, jclass, jlong ptr, jshortArray input, jshortArray output) {
    auto *state = reinterpret_cast<DenoiseState *>(ptr);
    if (!state || env->GetArrayLength(input)<480 || env->GetArrayLength(output)<480) return;
    jshort inShort[480];
    env->GetShortArrayRegion(input,0,480,inShort);
    float in[480],out[480];
    for(int i=0;i<480;i++)in[i]=inShort[i];
    rnnoise_process_frame(state,out,in);
    jshort outShort[480];
    for(int i=0;i<480;i++){
        float v=out[i];
        if(!std::isfinite(v))v=0.0f;
        v=std::max(-32768.0f,std::min(32767.0f,v));
        outShort[i]=static_cast<jshort>(std::lrint(v));
    }
    env->SetShortArrayRegion(output,0,480,outShort);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_voicesemantic_RnnoiseBridge_destroyNative(JNIEnv *, jclass,jlong ptr) {
    if(ptr)rnnoise_destroy(reinterpret_cast<DenoiseState *>(ptr));
}
