#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <linux/limits.h>
#include <poll.h>


#define LOG_TAG "GPIO"
#define LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)



int gpioExport(int pin) {
    FILE *file;

    system("su -c \"chmod -R 202 /sys/class/gpio/export\"");

    file = fopen("/sys/class/gpio/export", "w");
    if (file == NULL) {
        LOGE("Failed to open export for writing! @ line %d", __LINE__);
        return -1;
    }
    fprintf(file, "%d\n", pin);
    fclose(file);

    system("su -c \"chmod -R 200 /sys/class/gpio/export\"");

    return 0;
}

int gpioUnexport(int pin) {
    FILE *file;

    system("su -c \"chmod -R 202 /sys/class/gpio/unexport\"");

    file = fopen("/sys/class/gpio/unexport", "w");
    if (file == NULL) {
        LOGE("Failed to open unexport for writing! @ line %d", __LINE__);
        return -1;
    }
    fprintf(file, "%d\n", pin);
    fclose(file);

    system("su -c \"chmod -R 200 /sys/class/gpio/unexport\"");

    return 0;
}

char* gpioGetDirection(int pin) {
    char *line = malloc(sizeof(char));
    char filepath[PATH_MAX];
    FILE *file;

    snprintf(filepath, sizeof(filepath), "/sys/class/gpio/gpio%d/direction", pin);

    file = fopen(filepath, "r");
    if (file == NULL) {
        LOGE("Failed to open %s for reading! @ line %d", filepath, __LINE__);
        return NULL;
    }

    if (fgets(line, sizeof(line), file) == NULL) {
        LOGE("Failed to read %s! @ line %d", filepath, __LINE__);
        return NULL;
    };
    line[strcspn(line, "\r\n")] = 0;
    fclose(file);

    return line;
}

int gpioSetDirection(int pin, const char *direction) {
    char command[PATH_MAX];
    char filepath[PATH_MAX];
    FILE *file;

    snprintf(filepath, sizeof(filepath), "/sys/class/gpio/gpio%d/direction", pin);

    snprintf(command, sizeof(command), "su -c \"chmod -R 646 %s\"", filepath);
    system(command);

    file = fopen(filepath, "w");
    if (file == NULL) {
        LOGE("Failed to open %s for writing! @ line %d", filepath, __LINE__);
        return -1;
    }
    fprintf(file, "%s\n", direction);
    fclose(file);

    snprintf(command, sizeof(command), "su -c \"chmod -R 644 %s\"", filepath);
    system(command);

    return 0;
}
//
//int gpioSetActiveLow(int pin, int active_low) {
//    char command[PATH_MAX];
//    char filepath[PATH_MAX];
//    FILE *file;
//
//    snprintf(filepath, sizeof(filepath), "/sys/class/gpio/gpio%d/active_low", pin);
//
//    snprintf(command, sizeof(command), "su -c \"chmod -R 646 %s\"", filepath);
//    system(command);
//
//    file = fopen(filepath, "w");
//    if (file == NULL) {
//        LOGE("Failed to open %s for writing! @ line %d", filepath, __LINE__);
//        return -1;
//    }
//    fprintf(file, "%d\n", active_low);
//    fclose(file);
//
//    snprintf(command, sizeof(command), "su -c \"chmod -R 644 %s\"", filepath);
//    system(command);
//
//    return 0;
//}

int gpioSetEdge(int pin, const char *edge) {
    char command[PATH_MAX];
    char filepath[PATH_MAX];
    FILE *file;

    snprintf(filepath, sizeof(filepath), "/sys/class/gpio/gpio%d/edge", pin);

    snprintf(command, sizeof(command), "su -c \"chmod -R 646 %s\"", filepath);
    system(command);

    file = fopen(filepath, "w");
    if (file == NULL) {
        LOGE("Failed to open %s for writing! @ line %d", filepath, __LINE__);
        return -1;
    }
    fprintf(file, "%s\n", edge);
    fclose(file);

    snprintf(command, sizeof(command), "su -c \"chmod -R 644 %s\"", filepath);
    system(command);

    return 0;
}

int gpioGetFileDescriptor(int pin) {
    char filename[PATH_MAX];
    int fd;

    snprintf(filename, sizeof(filename), "/sys/class/gpio/gpio%d/value", pin);
    fd = open(filename, O_RDONLY);
    if (fd < 0) {
        return -1;
    }

    return fd;
}

int gpioGetValueByFileDescriptor(int fd) {
    char value;

    if (pread(fd, &value, sizeof(value), SEEK_SET) != 1) {
        return -1;
    }

    return value - '0';
}

int gpioReadValueByFileDescriptor(int fd, int timeout) {
    struct pollfd pollfd = {
        .fd = fd,
        .events = POLLPRI | POLLERR,
        .revents = 0
    };

    if (poll(&pollfd, 1, timeout) != 1) {
        return -1;
    }

    return gpioGetValueByFileDescriptor(fd);
}

int gpioGetValue(int pin) {
    char *line = malloc(sizeof(char));
    char filepath[PATH_MAX];
    FILE *file;

    snprintf(filepath, sizeof(filepath), "/sys/class/gpio/gpio%d/value", pin);

    file = fopen(filepath, "r");
    if (file == NULL) {
        LOGE("Failed to open %s for reading! @ line %d", filepath, __LINE__);
        return -1;
    }

    if (fgets(line, sizeof(line), file) == NULL) {
        LOGE("Failed to read %s! @ line %d", filepath, __LINE__);
        return -1;
    };
    line[strcspn(line, "\r\n")] = 0;
    fclose(file);

    return atoi(line);
}

int gpioSetValue(int pin, int value) {
    char command[PATH_MAX];
    char filepath[PATH_MAX];
    FILE *file;

    snprintf(filepath, sizeof(filepath), "/sys/class/gpio/gpio%d/value", pin);

    snprintf(command, sizeof(command), "su -c \"chmod -R 646 %s\"", filepath);
    system(command);

    file = fopen(filepath, "w");
    if (file == NULL) {
        LOGE("Failed to open %s for writing! @ line %d", filepath, __LINE__);
        return -1;
    }
    fprintf(file, "%d\n", value);
    fclose(file);

    snprintf(command, sizeof(command), "su -c \"chmod -R 644 %s\"", filepath);
    system(command);

    return 0;
}



JNIEXPORT jint JNICALL
Java_kg_delletenebre_serialmanager_NativeGpio_export(JNIEnv *env, jclass type, jint pin, jstring direction_) {
    const char *direction = (*env)->GetStringUTFChars(env, direction_, 0);

    int gpioStatus;

    gpioStatus = gpioExport(pin);
    if (gpioStatus == -1) {
        (*env)->ReleaseStringUTFChars(env, direction_, direction);
        return gpioStatus;
    }

    gpioStatus = gpioSetDirection(pin, direction);
    if (gpioStatus == -1) {
        (*env)->ReleaseStringUTFChars(env, direction_, direction);
        return gpioStatus;
    }

    (*env)->ReleaseStringUTFChars(env, direction_, direction);
    return 0;
}

JNIEXPORT jstring JNICALL
Java_kg_delletenebre_serialmanager_NativeGpio_getDirection(JNIEnv *env, jobject instance, jint pin) {
    return (*env)->NewStringUTF(env, gpioGetDirection(pin));
}

JNIEXPORT jint JNICALL
Java_kg_delletenebre_serialmanager_NativeGpio_getValue(JNIEnv *env, jclass type, jint pin) {
    return gpioGetValue(pin);
}

JNIEXPORT jint JNICALL
Java_kg_delletenebre_serialmanager_NativeGpio_setValue(JNIEnv *env, jclass type, jint pin, jint value) {
    return gpioSetValue(pin, value);
}


JNIEXPORT jint JNICALL
Java_kg_delletenebre_serialmanager_NativeGpio_initializate(JNIEnv *env, jobject instance,
                                                           jint pin, jstring direction_,
                                                           jboolean useInterrupt) {
    const char *direction = (*env)->GetStringUTFChars(env, direction_, (jboolean *)0);

    int gpioValue;
    int gpioStatus;
    char* gpioDirection;

    gpioValue = gpioGetValue(pin);
    if (gpioValue == -1) {
        gpioStatus = gpioUnexport(pin);
        if (gpioStatus == -1) {
            (*env)->ReleaseStringUTFChars(env, direction_, direction);
            return gpioStatus;
        }

        gpioStatus = gpioExport(pin);
        if (gpioStatus == -1) {
            (*env)->ReleaseStringUTFChars(env, direction_, direction);
            return gpioStatus;
        }
    }

    gpioDirection = gpioGetDirection(pin);
    if (strcmp(gpioDirection, direction) != 0) {
        gpioStatus = gpioSetDirection(pin, direction);
        if (!gpioStatus) {
            (*env)->ReleaseStringUTFChars(env, direction_, direction);
            return gpioStatus;
        }
    }

    if (strcmp(direction, "in") == 0 && useInterrupt) {
        gpioStatus = gpioSetEdge(pin, "both");
        if (gpioStatus == -1) {
            (*env)->ReleaseStringUTFChars(env, direction_, direction);
            return gpioStatus;
        }
    }

    (*env)->ReleaseStringUTFChars(env, direction_, direction);
    return (gpioValue == -1) ? gpioGetValue(pin) : gpioValue;
}

JNIEXPORT jint JNICALL
Java_kg_delletenebre_serialmanager_NativeGpio_getValueByFileDescriptor(JNIEnv *env, jobject instance, jint fd, jboolean useInterrupt) {
    return useInterrupt ? gpioReadValueByFileDescriptor(fd, 100) : gpioGetValueByFileDescriptor(fd);
}

JNIEXPORT jint JNICALL
Java_kg_delletenebre_serialmanager_NativeGpio_getFileDescriptor(JNIEnv *env, jobject instance, jint pin) {
    return gpioGetFileDescriptor(pin);
}

JNIEXPORT void JNICALL
Java_kg_delletenebre_serialmanager_NativeGpio_unexport(JNIEnv *env, jobject instance, jint pin) {
    gpioUnexport(pin);
}