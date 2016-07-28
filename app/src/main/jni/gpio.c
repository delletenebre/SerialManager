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


jmethodID cb_method_id;
jclass cb_class;
jobject cb_object;
JNIEnv *cb_save_env;


int gpio_export(int pin) {
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

int gpio_unexport(int pin) {
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

int gpio_set_direction(int pin, const char *direction) {
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

int gpio_set_edge(int pin, const char *edge) {
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

int gpio_poll(int pin) {
    char filename[PATH_MAX];
    int fd;
    char c;

    snprintf(filename, sizeof(filename), "/sys/class/gpio/gpio%d/value", pin);
    fd = open(filename, O_RDONLY);
    if (fd < 0) {
        return -1;
    }

    read(fd, &c, sizeof(c));

    return fd;
}

int gpio_get(int fd, int timeout) {
    char c;

    struct pollfd pollfd = {
        .fd = fd,
        .events = POLLPRI | POLLERR,
        .revents = 0
    };

    if (poll(&pollfd, 1, timeout) != 1) {
        return -1;
    }

    if (pread(fd, &c, sizeof(c), SEEK_SET) != 1) {
        return -1;
    }

    return c - '0';
}

int gpio_set_value(int pin, int value) {
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
Java_kg_delletenebre_serialmanager_NativeGpio_initializate(JNIEnv *env, jobject instance,
                                                           jint pin, jstring direction_,
                                                           jboolean useInterrupt) {
    const char *direction = (*env)->GetStringUTFChars(env, direction_, (jboolean *)0);

    int export = gpio_export(pin);
    if (export > -1) {
        LOGD("GPIO EXPORTED");

        int io = gpio_set_direction(pin, direction);
        if (io > -1) {
            LOGD("GPIO DIRECTION SET");

            if (useInterrupt) {
                int edge = gpio_set_edge(pin, "both");
                if (edge > -1) {
                    LOGD("GPIO EDGE SET");
                }
            }
        }
    }

    (*env)->ReleaseStringUTFChars(env, direction_, direction);
}

JNIEXPORT jint JNICALL
Java_kg_delletenebre_serialmanager_NativeGpio_pool(JNIEnv *env, jobject instance, jint pin) {

    return gpio_poll(pin);

}

JNIEXPORT jint JNICALL
Java_kg_delletenebre_serialmanager_NativeGpio_read(JNIEnv *env, jobject instance, jint pool) {

    return gpio_get(pool, -1);

}

JNIEXPORT void JNICALL
Java_kg_delletenebre_serialmanager_NativeGpio_unexport(JNIEnv *env, jobject instance, jint pin) {

    gpio_unexport(pin);

}

JNIEXPORT jboolean JNICALL
Java_kg_delletenebre_serialmanager_NativeGpio_exportAndDirection(JNIEnv *env, jclass type, jint pin,
                                                                 jstring direction_) {
    const char *direction = (*env)->GetStringUTFChars(env, direction_, (jboolean *)0);
    int export, io;

    export = gpio_export(pin);
        io = gpio_set_direction(pin, direction);

    (*env)->ReleaseStringUTFChars(env, direction_, direction);

    return (jboolean) ((export > -1) && (io > -1));
}

JNIEXPORT void JNICALL
Java_kg_delletenebre_serialmanager_NativeGpio_setValue(JNIEnv *env, jclass type, jint pin,
                                                       jint value) {

    gpio_set_value(pin, value);

}