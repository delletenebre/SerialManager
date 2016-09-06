package kg.delletenebre.serialmanager;

import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kg.delletenebre.serialmanager.Commands.Command;
import kg.delletenebre.serialmanager.Commands.Commands;
import xdroid.toaster.Toaster;

public class NativeGpio extends Thread {
    private final static String TAG = "GPIO";

    private int pin;
    private String name;
    private static boolean useInterrupt;

    int activeValue = 0;
    int lastValue = 1;

    private static long longPressDelay = 500;
    private static long debounce = 20;
    private long changeStateTime = -1;
    private boolean longPressActive = false;
    private Handler holdButtonHandler = new Handler();
    private Runnable holdButtonRunnable = new Runnable() {
        @Override
        public void run() {
            longPressActive = true;
            Commands.processReceivedData(
                    String.format(Locale.getDefault(), "<%s:%s>", name, "hold"));
        }
    };


    private static boolean generateIOEvents = true;
    private static boolean generateButtonEvents = true;

    private static Map<String, NativeGpio> gpios = new HashMap<>();


    static {
        System.loadLibrary("serial-manager");
    }
    public native static int export(int pin, String direction);
    public native static String getDirection(int pin);
    public native static int getValue(int pin);
    public native static int setValue(int pin, int value);

    public native int getValueByFileDescriptor(int fd, boolean useInterrupt);
    public native int getFileDescriptor(int pin);
    public native int initializate(int pin, String direction, boolean useInterrupt);
    public native void unexport(int pin);


    public NativeGpio(int pin) {
        this.pin = pin;
        this.name = "gpio" + pin;

        SharedPreferences prefs = App.getPrefs();
        useInterrupt = prefs.getBoolean("gpio_use_interrupt", true);
        lastValue = initializate(pin, "in", useInterrupt);

        if (lastValue > -1) {
            setDebounce(Integer.parseInt(prefs.getString("gpio_debounce", "20")));
            setLongPressDelay(Integer.parseInt(prefs.getString("gpio_long_press_delay", "500")));
            setGenerateIOEvents(prefs.getBoolean("gpio_as_io", true));
            setGenerateButtonEvents(prefs.getBoolean("gpio_as_button", true));

            activeValue = (lastValue == 0) ? 1 : 0;
        }
    }

    public void run() {
        int fd = getFileDescriptor(pin);

        while (!Thread.currentThread().isInterrupted()) {
            int currentValue = getValueByFileDescriptor(fd, useInterrupt);

            if (currentValue > -1 && currentValue != lastValue
                    && (System.currentTimeMillis() - changeStateTime) > debounce) {
                lastValue = currentValue;
                changeStateTime = System.currentTimeMillis();

                if (generateIOEvents) {
                    Commands.processReceivedData(
                            String.format(Locale.getDefault(), "<%s:%d>", name, currentValue));
                }

                if (generateButtonEvents) {
                    if (currentValue == activeValue) {
                        longPressActive = false;
                        holdButtonHandler.postDelayed(holdButtonRunnable, longPressDelay);
                    } else if (!longPressActive) {
                        holdButtonHandler.removeCallbacks(holdButtonRunnable);
                        Commands.processReceivedData(
                                String.format(Locale.getDefault(), "<%s:%s>", name, "click"));
                    }
                }
            }
        }

        holdButtonHandler.removeCallbacks(holdButtonRunnable);
        unexport(pin);

        if (App.isDebug()) {
            Log.d(TAG, "GPIO #" + String.valueOf(pin) + " thread is interrupted");
        }
    }

    public static void setValue(int pin, String state) {
        if (pin > 0) {
            String direction = getDirection(pin);

            if (direction == null) {
                export(pin, state.equals("invert") ? "low" : state);
            } else if (direction.equals("in")) {
                Toaster.toast("GPIO #" + String.valueOf(pin) + " error: direction is IN");

                if (App.isDebug()) {
                    Log.w(TAG, "GPIO #" + String.valueOf(pin) + " error: direction is IN");
                }
            } else if (state.equals("invert") || state.equals("low") || state.equals("high")) {
                int currentValue = getValue(pin);
                if (currentValue > -1) {
                    int value;

                    if (state.equals("invert")) {
                        value = (currentValue == 1) ? 0 : 1;
                    } else {
                        value = (state.equals("low")) ? 0 : 1;
                    }

                    if (setValue(pin, value) > -1) {
                        if (App.isDebug()) {
                            Log.d(TAG, "GPIO #" + String.valueOf(pin) + " setted to " + String.valueOf(value));
                        }
                    }
                } else {
                    if (App.isDebug()) {
                        Toaster.toast("GPIO #" + String.valueOf(pin) + " error: value unknown");

                        Log.w(TAG, "GPIO #" + String.valueOf(pin) + " error: value unknown");
                    }
                }
            }
        }
    }

    public static void setLongPressDelay(int value) {
        longPressDelay = value;
    }

    public static void setDebounce(int value) {
        debounce = value;
    }

    public static void setGenerateIOEvents(boolean value) {
        generateIOEvents = value;
    }

    public static void setGenerateButtonEvents(boolean value) {
        generateButtonEvents = value;
    }




    public static void createFromCommands() {
        for (Command command: Commands.getCommands()) {
            createByCommandKey(command.getKey());
        }
    }

    public static void createByCommandKey(final String key) {
        final int pin = getFromCommandKey(key);

        if (pin > -1 && !gpios.containsKey(key)) {
            gpios.put(key, new NativeGpio(pin));
            gpios.get(key).start();

            if (App.isDebug()) {
                Log.d(TAG, "pin created: " + String.valueOf(pin));
            }
        }
    }

    private static int getFromCommandKey(String key) {
        Pattern pattern = Pattern.compile("^gpio([0-9]+)$");
        Matcher matcher = pattern.matcher(key);
        if (matcher.find()) {
            if (App.isDebug()) {
                Log.d(TAG, "GPIO pin parsed: " + matcher.group(1));
            }

            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }

        return -1;
    }

    public static void destroyAll() {
        if (gpios.size() > 0) {
            for (Map.Entry<String, NativeGpio> entry : gpios.entrySet()) {
                entry.getValue().interrupt();
                gpios.remove(entry.getKey());
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

        if (gpios.containsKey(key) && countSameKey < 2) {
            gpios.get(key).interrupt();
            gpios.remove(key);
        }
    }
}