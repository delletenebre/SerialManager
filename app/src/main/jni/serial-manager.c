#include <jni.h>
#include <android/log.h>

#include <unistd.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <linux/usbdevice_fs.h>


static const char* kTAG = "ResetUsbJni";
#define LOGI(...) \
  ((void)__android_log_print(ANDROID_LOG_INFO, kTAG, __VA_ARGS__))
#define LOGW(...) \
  ((void)__android_log_print(ANDROID_LOG_WARN, kTAG, __VA_ARGS__))
#define LOGE(...) \
  ((void)__android_log_print(ANDROID_LOG_ERROR, kTAG, __VA_ARGS__))


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

    LOGI("%s", filename);
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
        LOGI("Reset successful");
    }

    /* Free the memory in concatenated */
    free(command);

    (*env)->ReleaseStringUTFChars(env, filename_, filename);

    return 0;
}