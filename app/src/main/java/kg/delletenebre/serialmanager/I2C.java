package kg.delletenebre.serialmanager;


import android.util.Log;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kg.delletenebre.serialmanager.Commands.Command;
import kg.delletenebre.serialmanager.Commands.Commands;

public class I2C extends Thread {
    private final static String TAG = "I2C";

    private static Map<String, I2C> i2cMap = new HashMap<>();
    private static int detectDelay = 100;

    private String commandKey;
    private String device;
    private int slaveAddress;

    static {
        System.loadLibrary("serial-manager");
    }
    private native int i2cOpen(String device, int slaveAddress);
    private native byte[] i2cRead(int fd, byte buffer[], int length);
    private native int i2cWrite(int fd, int mode, int dataArray[], int length);
    private native void i2cClose(int fd);

    public I2C (String device, int slaveAddress, String commandKey) {
        this.device = device;
        this.slaveAddress = slaveAddress;
        this.commandKey = commandKey;

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
        destroyByCommandKey(commandKey);

        if (App.isDebug()) {
            Log.d(TAG, "/dev/" + device + " thread is interrupted");
        }
    }

    public static void setDetectDelay(int value) {
        detectDelay = value;
    }

    public static void createFromCommands() {
        setDetectDelay(App.getIntPreference("i2c_read_request_delay", 100));

        for (Command command: Commands.getCommands()) {
            createByCommandKey(command.getKey());
        }
    }

    public static void createByCommandKey(final String key) {
        Pattern pattern = Pattern.compile("^(i2c-\\d+)\\|(\\d+)$");
        Matcher matcher = pattern.matcher(key);
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

            if (!device.isEmpty() && slaveAddress > -1 && !i2cMap.containsKey(key)) {
                i2cMap.put(key, new I2C(device, slaveAddress, key));
                i2cMap.get(key).start();

                if (App.isDebug()) {
                    Log.d(TAG, "I2C listener created: /dev/" + device);
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

    public static void destroyByCommandKey(String key) {
        int countSameKey = 0;


        for (Command command : Commands.getCommands()) {
            if (command.getKey().equals(key)) {
                countSameKey++;
            }
        }


        if (i2cMap.containsKey(key) && countSameKey < 2) {
            i2cMap.get(key).interrupt();
            i2cMap.remove(key);
        }
    }
}
