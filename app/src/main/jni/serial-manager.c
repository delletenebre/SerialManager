#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <linux/usbdevice_fs.h>
#include <stdlib.h>


#define LOG_TAG "Serial-Manager"
#define LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)


JNIEXPORT jint JNICALL
Java_kg_delletenebre_serialmanager_ConnectionService_resetUsbDevice(JNIEnv *env, jobject instance,
                                                                    jstring filename_) {
    const char *filename = (*env)->GetStringUTFChars(env, filename_, 0);

    int result, fd, rc;
    char *command;
    char *cmdPrefix = "su -c \"chmod -R 777 ",
         *cmdSuffix = "\"";

    command = malloc(strlen(cmdPrefix) + strlen(filename) + strlen(cmdSuffix) + 1);
    strcpy(command, cmdPrefix);
    strcat(command, filename);
    strcat(command, cmdSuffix);

    system(command);

    LOGD("%s", filename);
    fd = open(filename, O_WRONLY);
    if (fd < 0) {
        LOGE("Error opening output file @ line %d", __LINE__);
        return fd;
    }

    rc = ioctl(fd, USBDEVFS_RESET, 0);
    close(fd);

    if (rc < 0) {
        LOGE("Error in ioctl @ line %d", __LINE__);
        return rc;
    } else {
        LOGD("Reset successful");
    }

    /* Free the memory in concatenated */
    free(command);

    (*env)->ReleaseStringUTFChars(env, filename_, filename);

    return 0;
}