#include <jni.h>

JNIEXPORT jint JNICALL
Java_kg_delletenebre_serialmanager_NativeGpio_initializate(JNIEnv *env, jobject instance,
                                                           jint number, jstring direction_);

JNIEXPORT jint JNICALL
Java_kg_delletenebre_serialmanager_NativeGpio_pool(JNIEnv *env, jobject instance, jint pin);

JNIEXPORT jint JNICALL
Java_kg_delletenebre_serialmanager_NativeGpio_read(JNIEnv *env, jobject instance, jint pool);

JNIEXPORT void JNICALL
Java_kg_delletenebre_serialmanager_NativeGpio_unexport(JNIEnv *env, jobject instance, jint pin);