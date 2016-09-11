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
import java.util.HashMap;
import java.util.Map;


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
    private native static FileDescriptor open(String path, int baudrate);
    public native void close();

    public NativeSerial(String path, int baudrate)
            throws SecurityException, IOException {
        fd = open((new File(path)).getAbsolutePath(), baudrate);

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





    // *** STATIC Serial **** //
    private static Map<String, NativeSerial> openedSerialPorts = new HashMap<>();
//    private static ReadCallback receiveCallback = new ReadCallback() {
//        @Override
//        public void onReceivedData(String message) {
//            ConnectionService.onDataReceive("serial", message.getBytes());
//        }
//    };

    public static void openPorts() {
        int baudrate = App.getIntPreference("serial_baudrate", 115200);
        String[] devices = App.getPrefs().getString("serial_devices", "").replace(" ", "").split(",");

        for (String device: devices) {
            if (!device.isEmpty() && !openedSerialPorts.containsKey(device)) {
                try {
                    NativeSerial serialPort = new NativeSerial("/dev/" + device, baudrate);
                    serialPort.read(new ReadCallback() {
                        @Override
                        public void onReceivedData(String message) {
                            ConnectionService.onDataReceive("serial", message.getBytes());
                        }
                    });

                    openedSerialPorts.put(device, serialPort);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void destroyAll() {
        for (Map.Entry<String, NativeSerial> entry : openedSerialPorts.entrySet()) {
            entry.getValue().destroy();
            openedSerialPorts.remove(entry.getKey());
        }
    }

    public static String send(String data) {
        final String mode = "serial";

        if (openedSerialPorts.size() > 0) {
            if (App.isDebug()) {
                Log.d(TAG, "Data to send [ " + mode + " ]: " + data);
            }

            if (App.getPrefs().getBoolean("crlf", true)) {
                data += "\r\n";
            }

            for (Map.Entry<String, NativeSerial> entry : openedSerialPorts.entrySet()) {
                entry.getValue().write(data.getBytes());
            }

            return mode;
        } else {
            if (App.isDebug()) {
                Log.w(TAG, "Can't send data [" + data + "] via " + mode + ". No connected devices");
            }
        }

        return null;
    }

}