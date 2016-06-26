#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <linux/usbdevice_fs.h>
#include <stdio.h>
#include <stdlib.h>
#include <linux/input.h>
#include <linux/uinput.h>


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





JNIEXPORT jint JNICALL
Java_kg_delletenebre_serialmanager_App_initializateUinput(JNIEnv *env,
                                                                        jobject instance) {

    int fd;
    struct uinput_user_dev uidev;

    system("su -c \"chmod -R 666 /dev/uinput\"");

    fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) {
        LOGE("error: open @ line %d", __LINE__);
        return NULL;
    }

    //config uinput working mode,  mouse or touchscreen?  relative coordinates or absolute coordinate?
//    if (ioctl(fd, UI_SET_EVBIT, EV_KEY) < 0)  {     //support key button
//        LOGE("error: ioctl @ line %d", __LINE__);
//        return 0;
//    }
//    if (ioctl(fd, UI_SET_KEYBIT, BTN_LEFT) < 0) {   //support mouse left key
//        LOGE("error: ioctl @ line %d", __LINE__);
//        return 0;
//    }
    ioctl(fd, UI_SET_EVBIT, EV_KEY);
    ioctl(fd, UI_SET_KEYBIT, KEY_UNKNOWN);

    ioctl(fd, UI_SET_KEYBIT, KEY_APPSELECT);
    ioctl(fd, UI_SET_KEYBIT, KEY_LEFTALT);
    ioctl(fd, UI_SET_KEYBIT, KEY_TAB);

    //TODO ASSIST
    ioctl(fd, UI_SET_KEYBIT, KEY_VOICECOMMAND);
    ioctl(fd, UI_SET_KEYBIT, KEY_SEARCH);

    ioctl(fd, UI_SET_KEYBIT, KEY_MENU);
    ioctl(fd, UI_SET_KEYBIT, KEY_HOME);
    ioctl(fd, UI_SET_KEYBIT, KEY_BACK);
    ioctl(fd, UI_SET_KEYBIT, KEY_FORWARD);

    ioctl(fd, UI_SET_KEYBIT, KEY_BRIGHTNESSDOWN);
    ioctl(fd, UI_SET_KEYBIT, KEY_BRIGHTNESSUP);

    ioctl(fd, UI_SET_KEYBIT, KEY_PHONE);
    //TODO ENDCALL
    ioctl(fd, UI_SET_KEYBIT, KEY_MEDIA);

    ioctl(fd, UI_SET_KEYBIT, KEY_UP);
    ioctl(fd, UI_SET_KEYBIT, KEY_DOWN);
    ioctl(fd, UI_SET_KEYBIT, KEY_LEFT);
    ioctl(fd, UI_SET_KEYBIT, KEY_RIGHT);
    ioctl(fd, UI_SET_KEYBIT, KEY_SELECT);

    ioctl(fd, UI_SET_KEYBIT, KEY_ENTER);
    ioctl(fd, UI_SET_KEYBIT, KEY_ESC);

    ioctl(fd, UI_SET_KEYBIT, KEY_FASTFORWARD);
    ioctl(fd, UI_SET_KEYBIT, KEY_NEXTSONG);
    ioctl(fd, UI_SET_KEYBIT, KEY_PLAYPAUSE);
    ioctl(fd, UI_SET_KEYBIT, KEY_PLAYCD); ioctl(fd, UI_SET_KEYBIT, KEY_PLAY);
    ioctl(fd, UI_SET_KEYBIT, KEY_PAUSECD); ioctl(fd, UI_SET_KEYBIT, KEY_PAUSE);
    ioctl(fd, UI_SET_KEYBIT, KEY_STOPCD); ioctl(fd, UI_SET_KEYBIT, KEY_STOP);
    ioctl(fd, UI_SET_KEYBIT, KEY_PREVIOUSSONG);
    ioctl(fd, UI_SET_KEYBIT, KEY_REWIND);
    ioctl(fd, UI_SET_KEYBIT, KEY_RECORD);

    ioctl(fd, UI_SET_KEYBIT, KEY_VOLUMEUP);
    ioctl(fd, UI_SET_KEYBIT, KEY_VOLUMEDOWN);
    ioctl(fd, UI_SET_KEYBIT, KEY_MUTE);
    ioctl(fd, UI_SET_KEYBIT, KEY_MICMUTE);

    //TODO NOTIFICATION

    ioctl(fd, UI_SET_KEYBIT, KEY_SOUND);
    ioctl(fd, UI_SET_KEYBIT, KEY_CAMERA);
    ioctl(fd, UI_SET_KEYBIT, KEY_ADDRESSBOOK);
    ioctl(fd, UI_SET_KEYBIT, KEY_WWW);
    //ioctl(fd, UI_SET_KEYBIT, KEY_SETUP);

    ioctl(fd, UI_SET_KEYBIT, KEY_POWER);
    ioctl(fd, UI_SET_KEYBIT, KEY_POWER2);
    ioctl(fd, UI_SET_KEYBIT, KEY_SLEEP);
    ioctl(fd, UI_SET_KEYBIT, KEY_WAKEUP);

    ioctl(fd, UI_SET_KEYBIT, KEY_ZOOMIN);
    ioctl(fd, UI_SET_KEYBIT, KEY_ZOOMOUT);







    memset(&uidev, 0, sizeof(uidev));   //create an virtual input device node in /dev/input/***
    snprintf(uidev.name, UINPUT_MAX_NAME_SIZE, "uinput-serialmanager");
    uidev.id.bustype = BUS_USB;
    uidev.id.vendor = 0x1;
    uidev.id.product = 0x1;
    uidev.id.version = 1;

    if (write(fd, &uidev, sizeof(uidev)) < 0) {
        LOGE("error: write @ line %d", __LINE__);
        return NULL;
    }

    if (ioctl(fd, UI_DEV_CREATE) < 0) {
        LOGE("error: ioctl @ line %d", __LINE__);
        return NULL;
    }

    return fd;

//    while(1) {
//    //simulate( x,y) coordinates for mouse
//        switch(rand() % 4) {
//            case 0:
//                dx = -10;
//                dy = -1;
//                break;
//            case 1:
//                dx = 10;
//                dy = 1;
//                break;
//            case 2:
//                dx = -1;
//                dy = 10;
//                break;
//            case 3:
//                dx = 1;
//                dy = -10;
//                break;
//        }
//
//        for(i = 0; i < 20; i++) {
////send input event to kernel input system
//            memset(&ev, 0, sizeof(struct input_event));
//            ev.type = EV_REL;         //send x coordinates
//            ev.code = REL_X;
//            ev.value = dx;
//            if(write(fd, &ev, sizeof(struct input_event)) < 0)
//                die("error: write");
//
//            memset(&ev, 0, sizeof(struct input_event));
//            ev.type = EV_REL;  //send y coordinates
//            ev.code = REL_Y;
//            ev.value = dy;
//            if(write(fd, &ev, sizeof(struct input_event)) < 0)
//                die("error: write");
//
//            memset(&ev, 0, sizeof(struct input_event));
//            ev.type = EV_KEY;  //mouse left key
//            ev.code = BTN_LEFT;
//            ev.value = 1;
//            if(write(fd, &ev, sizeof(struct input_event)) < 0)
//                die("error: write");
//
//            memset(&ev, 0, sizeof(struct input_event));
//            ev.type = EV_SYN; // inform input system to process this input event
//            ev.code = 0;
//            ev.value = 0;
//            if(write(fd, &ev, sizeof(struct input_event)) < 0)
//                die("error: write");
//
//            usleep(15000);
//        }
//
//        sleep(5);
//    }

}

JNIEXPORT void JNICALL
Java_kg_delletenebre_serialmanager_App_destroyUinput(JNIEnv *env, jobject instance, jint fd) {
    if (ioctl(fd, UI_DEV_DESTROY) > -1) {
        close(fd);
    } else {
        LOGE("error: fd or ioctl @ line %d", __LINE__);
    }
}
//
//JNIEXPORT jstring JNICALL
//Java_kg_delletenebre_serialmanager_App_getUinputDevicePath(JNIEnv *env, jobject instance) {
//
//    FILE *fp = NULL;
//    char buffer[1024];
//    char *eventname = NULL;
//    fp = fopen("/proc/bus/input/devices", "r");
//    if (!fp) {
//        LOGE("Unable to open file @ line %d", __LINE__);
//        return NULL;
//    }
//    int findDevice = 0;
//    memset(buffer, 0, sizeof(buffer));
//    while (fgets(buffer, sizeof(buffer), fp)) {
//        if (strstr(buffer, "uinput-serialmanager")) {
//            findDevice = 1;
//        }
//
//        char *ptr = NULL;
//        if ((ptr = strstr(buffer, "Handlers="))) {
//            ptr += strlen("Handlers=");
//            ptr = strstr(ptr, "event");
//            if (ptr) {
//                char *ptr2 = strchr(ptr, ' ');
//                if (ptr2) {
//                    *ptr2 = '\0';
//                }
//                eventname = strdup(ptr);
//                if (!eventname) {
//                    LOGW("Out of memory @ line %d", __LINE__);
//                    break;
//                }
//                if (findDevice == 1) {
//                    LOGE("Keyboard event is /dev/input/%s", eventname);
//                    break;
//                }
//            }
//        }
//    }
//    fclose(fp);
//
//    if (findDevice == 1) {
//        return (*env)->NewStringUTF(env, eventname);
//    } else {
//        return NULL;
//    }
//
//}

static void send_event(int fd, uint16_t type, uint16_t code, int32_t value) {
    struct input_event event;
    memset(&event, 0, sizeof(event));
    event.type = type;
    event.code = code;
    event.value = value;
    if (write(fd, &event, sizeof(event)) < 0) {
        LOGE("send_event error @ line %d", __LINE__);
    }
}

JNIEXPORT void JNICALL
Java_kg_delletenebre_serialmanager_App_sendEvent(JNIEnv *env, jclass type, jint fd, uint16_t code) {
    send_event(fd, EV_KEY, code, 1);
    send_event(fd, EV_SYN, 0, 0);
    send_event(fd, EV_KEY, code, 0);
    send_event(fd, EV_SYN, 0, 0);
}

JNIEXPORT void JNICALL
Java_kg_delletenebre_serialmanager_App_sendEventDouble(JNIEnv *env, jclass type, jint fd, uint16_t code1, uint16_t code2) {
    send_event(fd, EV_KEY, code1, 1);
    send_event(fd, EV_SYN, 0, 0);
    send_event(fd, EV_KEY, code2, 1);
    send_event(fd, EV_SYN, 0, 0);
    send_event(fd, EV_KEY, code2, 0);
    send_event(fd, EV_KEY, code1, 0);
    send_event(fd, EV_SYN, 0, 0);
}

