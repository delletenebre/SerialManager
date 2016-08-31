#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <linux/limits.h>
#include <sys/stat.h>
#include "linux/input.h"
//../../../../../../ndk/r12/platforms/android-24/arch-mips64/usr/include/

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

int isFdExists(int fd) {
    struct stat statbuf;
    fstat(fd, &statbuf);
    return (statbuf.st_nlink > 0);
}

char* hotkeysReadValueByFileDescriptor(int fd) {
    struct input_event ev[64];
    //char *result = malloc(sizeof(char) * PATH_MAX);
    char result[PATH_MAX];
    size_t size = sizeof(struct input_event);

    if (!isFdExists(fd) || read(fd, ev, size * 64) < size) {
        LOGE("Failed to read @ line %d", __LINE__);
        return "error";
    }

    LOGD("%d|%d;%d|%d", ev[0].code, ev[0].value, ev[1].code, ev[1].value);

    snprintf(result, PATH_MAX, "%d|%d.%d|%d", ev[0].code, ev[0].value, ev[1].code, ev[1].value);
    return result;
}


JNIEXPORT jint JNICALL
Java_kg_delletenebre_serialmanager_Hotkey_getFileDescriptor(JNIEnv *env, jobject instance, jint eventId) {
    return hotkeysGetFileDescriptor(eventId);
}

JNIEXPORT jstring JNICALL
Java_kg_delletenebre_serialmanager_Hotkey_readKeysByFileDescriptor(JNIEnv *env, jobject instance, jint fd) {
    return (*env)->NewStringUTF(env, hotkeysReadValueByFileDescriptor(fd));
}
