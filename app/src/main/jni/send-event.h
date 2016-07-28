#include <jni.h>

int Java_kg_delletenebre_serialmanager_App_initializateUinput(JNIEnv *env, jobject instance);
void Java_kg_delletenebre_serialmanager_App_destroyUinput(JNIEnv *env, jobject instance, jint fd);
void send_event(int fd, uint16_t type, uint16_t code, int32_t value);
void Java_kg_delletenebre_serialmanager_App_sendEvent(JNIEnv *env, jclass type, jint fd, uint16_t code);
void Java_kg_delletenebre_serialmanager_App_sendEventDouble(JNIEnv *env, jclass type, jint fd, uint16_t code1, uint16_t code2);