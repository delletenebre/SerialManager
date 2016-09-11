#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <linux/limits.h>
//#include "i2c-dev.h"


#define LOG_TAG "I2C"
#define LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)


//int i2cWrite(int fd, char* data) {
//    size_t bufferLength = strlen(data);
//    ssize_t writedLength;
//
//    if ((writedLength = write(fd, data, bufferLength)) != bufferLength) {
//        LOGE("write fail i2c (%d) @ line %d", writedLength, __LINE__);
//        return -1;
//    }
//
//    LOGD("I2C: %d byte(s) written @ line %d", writedLength, __LINE__);
//
//    free(data);
//
//    return 0;
//}


//***************************************************************************
// Open I2C device
//***************************************************************************
JNIEXPORT jint JNICALL
Java_kg_delletenebre_serialmanager_I2C_i2cOpen(JNIEnv *env, jobject instance,
                                                        jstring _device, jint slaveAddress) {
    const char *device = (*env)->GetStringUTFChars(env, _device, (jboolean *)0);
    char command[PATH_MAX];
    char i2cDevice[PATH_MAX];
    snprintf(i2cDevice, sizeof(i2cDevice), "/dev/%s", device);
    (*env)->ReleaseStringUTFChars(env, _device, device);

    int fd;

    snprintf(command, sizeof(command), "su -c \"chmod -R 646 %s\"", i2cDevice);
    system(command);

    fd = open(i2cDevice, O_RDWR);
    if (fd < 0) {
        LOGE("Failed to open /dev/i2c! @ line %d", __LINE__);
        return -1;
    }

    if (ioctl(fd, 0x0703, slaveAddress) < 0) {
        LOGE("Can't open slave device by address %d @ line %d", slaveAddress, __LINE__);
        return -2;
    }

    return fd;
}


//***************************************************************************
// Read data from the I2C device
//***************************************************************************
JNIEXPORT jbyteArray JNICALL
Java_kg_delletenebre_serialmanager_I2C_i2cRead(JNIEnv * env, jobject instance, jint fd,
                                                        jbyteArray buffer, jint length) {

    char* bufByte;

    if (length <= 0) {
        LOGE("I2C: buffer length <= 0 @ line %d", __LINE__);
        goto err0;
    }

    bufByte = (char*)malloc((size_t)length);
    if (bufByte == 0) {
        LOGE("I2C: no memory @ line %d", __LINE__);
        goto err0;
    }

    //memset(bufByte, '\0', (size_t)length);
    if ((int)read(fd, bufByte, (size_t)length) != length) {
        //LOGE("I2C: read fail @ line %d", __LINE__);
        goto err1;
    } else if (bufByte[0] == 0 && bufByte[1] == 255) {
        //LOGD("I2C: read bad @ line %d", __LINE__);
    } else {
        //LOGD("I2C: read success %d | %d @ line %d", bufByte[0], bufByte[1], __LINE__);
        (*env)->SetByteArrayRegion(env, buffer, 0, length, (jbyte *) bufByte);
    }

    err1:
    free(bufByte);
    err0:
    return buffer;
}


//***************************************************************************
// Write data to the I2C device
//***************************************************************************
JNIEXPORT jint JNICALL
Java_kg_delletenebre_serialmanager_I2C_i2cWrite(JNIEnv *env, jobject instance, jint fd,
                                                         jint mode, jintArray dataArray,
                                                         jint length) {
    jint *bufInt;
    char *bufByte;
    int i = 0, j = 0;

    if (length <= 0) {
        LOGE("I2C: buffer length <= 0 @ line %d", __LINE__);
        goto err0;
    }

    bufInt = (jint *) malloc(length * sizeof(int));
    if (bufInt == 0) {
        LOGE("I2C: no memory @ line %d", __LINE__);
        goto err0;
    }
    bufByte = (char*) malloc((size_t) (length + 1));
    if (bufByte == 0) {
        LOGE("I2C: no memory @ line %d", __LINE__);
        goto err1;
    }

    (*env)->GetIntArrayRegion(env, dataArray, 0, length, bufInt);

    bufByte[0] = (char) mode;
    for (i = 0; i < length; i++) {
        bufByte[i] = (char) bufInt[i];
    }

    if (write(fd, bufByte, (size_t) length) != length) {
        LOGE("I2C: write fail @ line %d", __LINE__);
        goto err2;
    }

    LOGD("I2C: write success @ line %d", __LINE__);

    free(bufByte);
    free(bufInt);

    return 0;

    err2:
    free(bufByte);
    err1:
    free(bufInt);
    err0:
    return -1;
}

//***************************************************************************
// Close the I2C device
//***************************************************************************
JNIEXPORT void JNICALL
Java_kg_delletenebre_serialmanager_I2C_i2cClose(JNIEnv *env, jobject instance, jint fd) {
    close(fd);
}
