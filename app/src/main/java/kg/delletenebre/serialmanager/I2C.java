package kg.delletenebre.serialmanager;


import android.util.Log;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kg.delletenebre.serialmanager.Commands.Commands;

public class I2C extends Thread {
    private final static String TAG = "I2C";

    private static Map<String, I2C> i2cMap = new HashMap<>();
    private static int detectDelay = 100;

    private String identifier;
    private String device;
    private int slaveAddress;

    static {
        System.loadLibrary("serial-manager");
    }
    private native int i2cOpen(String device, int slaveAddress);
    private native byte[] i2cRead(int fd, byte buffer[], int length);
    private native int i2cWrite(int fd, int mode, int dataArray[], int length);
    private native void i2cClose(int fd);

    public I2C (String device, int slaveAddress, String identifier) {
        this.device = device;
        this.slaveAddress = slaveAddress;
        this.identifier = identifier;

        detectDelay = App.getIntPreference("i2c_request_data_delay", 100);
    }

    public void run() {
        int fd = i2cOpen(device, slaveAddress);

        try {
            while (!Thread.currentThread().isInterrupted()) {
                byte[] buffer = i2cRead(fd, new byte[1024], 1024);

                if (buffer != null && (buffer[0] != 0 && buffer[1] != 0)) {
                    String receivedData = new String(buffer).replaceAll("\\ufffd", "");

                    if (!receivedData.isEmpty()) {
                        if (App.isDebug()) {
                            Log.d(TAG, "received: " + receivedData);
                        }

                        Pattern pattern = Pattern.compile("(<.+?>)");
                        Matcher matcher = pattern.matcher(receivedData);
                        if (matcher.find()) {
                            for (int i = 1; i <= matcher.groupCount(); i++) {
                                Commands.processReceivedData(matcher.group(i));
                            }
                        }
                    }
                }

                Thread.sleep(detectDelay);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        i2cClose(fd);
        destroyByIdentifier(identifier);

        if (App.isDebug()) {
            Log.d(TAG, "/dev/" + device + " thread is interrupted");
        }
    }

    public static void setDetectDelay(int value) {
        detectDelay = value;
    }

    public static void create() {
        setDetectDelay(App.getIntPreference("i2c_read_request_delay", 100));

        List<String> i2cPrefNames = Arrays.asList(App.getPrefs().getString("i2c_devices", "").split(","));
        for (String i2cPrefName: i2cPrefNames) {
            Pattern pattern = Pattern.compile("^(.+?)\\|(\\d+)$");
            Matcher matcher = pattern.matcher(i2cPrefName);
            if (matcher.find()) {
                if (App.isDebug()) {
                    Log.d(TAG, "I2C listener parsed: device = /dev/" + matcher.group(1)
                            + " | slave address = " + matcher.group(2));
                }

                String device = matcher.group(1);
                int slaveAddress = -1;
                try {
                    slaveAddress = Integer.parseInt(matcher.group(2));
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }

                if (!device.isEmpty() && slaveAddress > -1 && !i2cMap.containsKey(i2cPrefName)) {
                    i2cMap.put(i2cPrefName, new I2C(device, slaveAddress, i2cPrefName));
                    i2cMap.get(i2cPrefName).start();

                    if (App.isDebug()) {
                        Log.d(TAG, "I2C listener created: /dev/" + device);
                    }
                }
            }
        }
    }

    public static void destroyAll() {
        if (i2cMap.size() > 0) {
            for (Map.Entry<String, I2C> entry : i2cMap.entrySet()) {
                entry.getValue().interrupt();
                i2cMap.remove(entry.getKey());
            }
        }
    }

    public static void destroyByIdentifier(String identifier) {
        if (i2cMap.containsKey(identifier)) {
            i2cMap.get(identifier).interrupt();
            i2cMap.remove(identifier);
        }
    }
}
