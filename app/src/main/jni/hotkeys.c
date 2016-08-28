#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <linux/limits.h>
#include <poll.h>
#include "../../../../../../ndk/r12/platforms/android-24/arch-mips64/usr/include/linux/input.h"


#define LOG_TAG "Keyboard-Events"
#define LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)




int hotkeysGetFileDescriptor(int eventId) {
    char command[PATH_MAX];
    char filename[PATH_MAX];
    int fd;

    snprintf(filename, sizeof(filename), "/dev/input/event%d", eventId);

    snprintf(command, sizeof(command), "su -c \"chmod -R 664 %s\"", filename);
    system(command);

    snprintf(filename, sizeof(filename), "/dev/input/event%d", eventId);
    fd = open(filename, O_RDONLY);
    if (fd < 0) {
        LOGE("Failed to open %s for reading! @ line %d", filename, __LINE__);
        return -1;
    }

    return fd;
}

int hotkeysReadValueByFileDescriptor(int fd) {
    struct input_event ev[64];
    //int value;
    size_t size = sizeof(struct input_event);

    if (read(fd, ev, size * 64) < size) {
        LOGE("Failed to read @ line %d", __LINE__);
        return -1;
    }

    //value = ev[0].value;

    //if (value != ' ') {// && ev[1].value == 1 && ev[1].type == 1) { // Only read the key press event
        //LOGD("Code[%d] | Value[%d] | Type[%d]", ev[0].code, ev[0].value, ev[0].type);
    LOGD("Code[%d] | Value[%d] | Type[%d]", ev[1].code, ev[1].value, ev[1].type);
    if (ev[1].value == 1) {
        return ev[1].code;
    }

    return 0;
}


JNIEXPORT jint JNICALL
Java_kg_delletenebre_serialmanager_Hotkey_getFileDescriptor(JNIEnv *env, jobject instance, jint eventId) {
    return hotkeysGetFileDescriptor(eventId);
}

JNIEXPORT jint JNICALL
Java_kg_delletenebre_serialmanager_Hotkey_readKeysByFileDescriptor(JNIEnv *env, jobject instance, jint fd) {
    return hotkeysReadValueByFileDescriptor(fd);
}

//JNIEXPORT jint JNICALL
//Java_kg_delletenebre_serialmanager_NativeGpio_export(JNIEnv *env, jclass type, jint pin, jstring direction_) {
//    const char *direction = (*env)->GetStringUTFChars(env, direction_, 0);
//
//    int gpioStatus;
//
//    gpioStatus = gpioExport(pin);
//    if (gpioStatus == -1) {
//        (*env)->ReleaseStringUTFChars(env, direction_, direction);
//        return gpioStatus;
//    }
//
//    gpioStatus = gpioSetDirection(pin, direction);
//    if (gpioStatus == -1) {
//        (*env)->ReleaseStringUTFChars(env, direction_, direction);
//        return gpioStatus;
//    }
//
//    (*env)->ReleaseStringUTFChars(env, direction_, direction);
//    return 0;
//}
//
//JNIEXPORT jstring JNICALL
//Java_kg_delletenebre_serialmanager_NativeGpio_getDirection(JNIEnv *env, jobject instance, jint pin) {
//    return (*env)->NewStringUTF(env, gpioGetDirection(pin));
//}
//
//JNIEXPORT jint JNICALL
//Java_kg_delletenebre_serialmanager_NativeGpio_getValue(JNIEnv *env, jclass type, jint pin) {
//    return gpioGetValue(pin);
//}
//
//JNIEXPORT jint JNICALL
//Java_kg_delletenebre_serialmanager_NativeGpio_setValue(JNIEnv *env, jclass type, jint pin, jint value) {
//    return gpioSetValue(pin, value);
//}
//
//
//JNIEXPORT jint JNICALL
//Java_kg_delletenebre_serialmanager_NativeGpio_initializate(JNIEnv *env, jobject instance,
//                                                           jint pin, jstring direction_,
//                                                           jboolean useInterrupt) {
//    const char *direction = (*env)->GetStringUTFChars(env, direction_, (jboolean *)0);
//
//    int gpioValue;
//    int gpioStatus;
//    char* gpioDirection;
//
//    gpioValue = gpioGetValue(pin);
//    if (gpioValue == -1) {
//        gpioStatus = gpioUnexport(pin);
//        if (gpioStatus == -1) {
//            (*env)->ReleaseStringUTFChars(env, direction_, direction);
//            return gpioStatus;
//        }
//
//        gpioStatus = gpioExport(pin);
//        if (gpioStatus == -1) {
//            (*env)->ReleaseStringUTFChars(env, direction_, direction);
//            return gpioStatus;
//        }
//    }
//
//    gpioDirection = gpioGetDirection(pin);
//    if (strcmp(gpioDirection, direction) != 0) {
//        gpioStatus = gpioSetDirection(pin, direction);
//        if (!gpioStatus) {
//            (*env)->ReleaseStringUTFChars(env, direction_, direction);
//            return gpioStatus;
//        }
//    }
//
//    if (strcmp(direction, "in") == 0 && useInterrupt) {
//        gpioStatus = gpioSetEdge(pin, "both");
//        if (gpioStatus == -1) {
//            (*env)->ReleaseStringUTFChars(env, direction_, direction);
//            return gpioStatus;
//        }
//    }
//
//    (*env)->ReleaseStringUTFChars(env, direction_, direction);
//    return (gpioValue == -1) ? gpioGetValue(pin) : gpioValue;
//}
//
//JNIEXPORT jint JNICALL
//Java_kg_delletenebre_serialmanager_NativeGpio_getValueByFileDescriptor(JNIEnv *env, jobject instance, jint fd, jboolean useInterrupt) {
//    return useInterrupt ? gpioReadValueByFileDescriptor(fd, 100) : gpioGetValueByFileDescriptor(fd);
//}
//

//
//JNIEXPORT void JNICALL
//Java_kg_delletenebre_serialmanager_NativeGpio_unexport(JNIEnv *env, jobject instance, jint pin) {
//    gpioUnexport(pin);
//}