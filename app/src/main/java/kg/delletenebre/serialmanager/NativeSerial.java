package kg.delletenebre.serialmanager;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class NativeSerial {
    public interface ReadCallback {
        void onReceivedData(String data);
    }

    private final static String TAG = "NativeSerial";

    private FileDescriptor fd; // this variable used by JNI
    private InputStream inputStream;
    private OutputStream outputStream;
    private ReadThread readThread = new ReadThread();


    static {
        System.loadLibrary("serial-manager");
    }
    private native static FileDescriptor open(String path, int baudrate, int flags);
    public native void close();

    public NativeSerial(String device_path, int baudrate, int flags)
            throws SecurityException, IOException {
        File device = new File(device_path);

        fd = open(device.getAbsolutePath(), baudrate, flags);

        if (fd == null) {
            Log.e(TAG, "native open returns null");
            throw new IOException();
        }

        inputStream = new BufferedInputStream(new FileInputStream(fd));
        outputStream = new FileOutputStream(fd);

        readThread.start();
    }

    public void destroy() {
        readThread.interrupt();

        try {
            inputStream.close();
            outputStream.close();
        } catch (Exception e) {}

        inputStream = null;
        outputStream = null;

        close();
    }

    public void read(ReadCallback callback) {
        this.readThread.setCallback(callback);
    }

    public void write(byte[] buffer) {
        try {
            outputStream.write(buffer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private class ReadThread extends Thread {
        private ReadCallback callback;

        public void setCallback(ReadCallback callback) {
            this.callback = callback;
        }

        public void run() {
            StringBuilder sb = new StringBuilder();

            while(!Thread.currentThread().isInterrupted()) {
                try {
                    int data = inputStream.read();

                    if (data > -1) {
                        if (data == 0x0D || data == 0x0A) {
                            if (sb.length() > 0) {
                                this.onReceivedData(sb.toString());

                                sb = new StringBuilder();
                            }
                        } else {
                            sb.append((char) data);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }

            try {
                inputStream.close();
                outputStream.close();
            } catch (Exception e) {}
        }

        private void onReceivedData(String data) {
            if (callback != null) {
                callback.onReceivedData(data);
            }
        }
    }

}