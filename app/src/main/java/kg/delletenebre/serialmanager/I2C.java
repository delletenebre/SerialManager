package kg.delletenebre.serialmanager;


import android.util.Log;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class I2C {
    public interface ReadCallback {
        void onReceivedData(String data);
    }

    private final static String TAG = "I2C";

    private int fd;
    private ReadThread readThread = new ReadThread();

    static {
        System.loadLibrary("serial-manager");
    }
    private native int open(String device, int slaveAddress);
    private native byte[] read(int fd, byte buffer[], int length);
    private native int write(int fd, int mode, int dataArray[], int length);
    private native void close();

    public I2C(String path, int slaveAddress)
            throws SecurityException, IOException {
        if ((fd = open(path, slaveAddress)) < 0) {
            Log.e(TAG, "native open() returns -1");
            throw new IOException();
        }

        readThread.start();
    }

    public void destroy() {
        readThread.interrupt();
        close();
    }

    public void read(ReadCallback callback) {
        this.readThread.setCallback(callback);
    }

    public void write(byte[] bytes) {
        int[] data = byteToIntArray(bytes);
        write(fd, 0, data, data.length);
    }

    private static int[] byteToIntArray(byte[] input) {
        int[] ret = new int[input.length];
        for (int i = 0; i < input.length; i++) {
            ret[i] = input[i] & 0xff; // Range 0 to 255, not -128 to 127
        }
        return ret;
    }

    private class ReadThread extends Thread {
        private ReadCallback callback;

        public void setCallback(ReadCallback callback) {
            this.callback = callback;
        }

        public void run() {

            while(!Thread.currentThread().isInterrupted()) {
                byte[] buffer = read(fd, new byte[1024], 1024);

                if (buffer != null && (buffer[0] != 0 && buffer[1] != 0)) {
                    String receivedData = new String(buffer);
                    receivedData = receivedData.substring(0, receivedData.indexOf("\ufffd"));

                    if (!receivedData.isEmpty()) {
                        if (App.isDebug()) {
                            Log.d(TAG, "received: " + receivedData);
                        }

                        this.onReceivedData(receivedData);
                    }
                }
            }
        }

        private void onReceivedData(String data) {
            if (callback != null) {
                callback.onReceivedData(data);
            }
        }
    }




    // *** STATIC I2C **** //
    private static Map<String, I2C> openedDevices = new HashMap<>();
    private static ReadCallback receiveCallback = new ReadCallback() {
        @Override
        public void onReceivedData(String message) {
            ConnectionService.onDataReceive("i2c", message.getBytes());
        }
    };


    public static void openDevices() {
        String[] devices = App.getPrefs().getString("i2c_devices", "").replace(" ", "").split(",");

        for (String device: devices) {
            if (!device.isEmpty()) {// && !openedDevices.containsKey(device)) {
                Pattern pattern = Pattern.compile("^(.+?)\\|(\\d+)$");
                Matcher matcher = pattern.matcher(device);
                if (matcher.find()) {
                    if (App.isDebug()) {
                        Log.d(TAG, "I2C listener parsed: device = /dev/" + matcher.group(1)
                                + " | slave address = " + matcher.group(2));
                    }

                    String deviceName = matcher.group(1);
                    int slaveAddress = -1;
                    try {
                        slaveAddress = Integer.parseInt(matcher.group(2));
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }

                    if (!deviceName.isEmpty() && slaveAddress > -1 && !openedDevices.containsKey(device)) {
                        try {
                            I2C i2cDevice = new I2C("/dev/" + deviceName, slaveAddress);
//                            i2cDevice.read(receiveCallback);
                            i2cDevice.read(new ReadCallback() {
                                @Override
                                public void onReceivedData(String message) {
                                    ConnectionService.onDataReceive("i2c", message.getBytes());
                                }
                            });

                            openedDevices.put(device, i2cDevice);

                            if (App.isDebug()) {
                                Log.d(TAG, "I2C listener created: /dev/" + deviceName);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    public static void destroyAll() {
        for (Map.Entry<String, I2C> entry : openedDevices.entrySet()) {
            entry.getValue().destroy();
            openedDevices.remove(entry.getKey());
        }
    }

    public static String send(String data) {
        final String mode = "i2c";

        if (openedDevices.size() > 0) {
            if (App.isDebug()) {
                Log.d(TAG, "Data to send [ " + mode + " ]: " + data);
            }

            if (App.getPrefs().getBoolean("crlf", true)) {
                data += "\r\n";
            }

            for (Map.Entry<String, I2C> entry : openedDevices.entrySet()) {
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
