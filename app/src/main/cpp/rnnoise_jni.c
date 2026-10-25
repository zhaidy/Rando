#include <jni.h>
#include <stdint.h>
#include "rnnoise.h"

JNIEXPORT jlong JNICALL
Java_com_personal_rando_group_Rnnoise_nativeCreate(JNIEnv *env, jclass clazz) {
    return (jlong) (intptr_t) rnnoise_create(NULL);
}

JNIEXPORT void JNICALL
Java_com_personal_rando_group_Rnnoise_nativeDestroy(JNIEnv *env, jclass clazz, jlong state) {
    rnnoise_destroy((DenoiseState *) (intptr_t) state);
}

/* 就地处理一帧 480 个样本（48 kHz，10 毫秒），数值范围是 16 位 PCM；返回这帧有人声的概率 */
JNIEXPORT jfloat JNICALL
Java_com_personal_rando_group_Rnnoise_nativeProcess(JNIEnv *env, jclass clazz, jlong state, jfloatArray frame) {
    jfloat *samples = (*env)->GetPrimitiveArrayCritical(env, frame, NULL);
    if (samples == NULL) return 0;
    float vad = rnnoise_process_frame((DenoiseState *) (intptr_t) state, samples, samples);
    (*env)->ReleasePrimitiveArrayCritical(env, frame, samples, 0);
    return vad;
}
