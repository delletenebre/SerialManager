package kg.delletenebre.serialmanager;

import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
    public native int read(int pool);
    public native int pool(int pin);
    public native int initializate(int pin, String direction, boolean useInterrupt);
    public native void unexport(int pin);
    public native static boolean exportAndDirection(int pin, String direction);
    public native static void setValue(int pin, int value);
    public native int setActiveLow(int pin, int state);

    public NativeGpio(int pin) {
        createPin(pin, "in");
    }

    public NativeGpio(int pin, int active_low) {
        createPin(pin, "in", active_low);
    }

    private void createPin(int pin, String direction) {
        this.pin = pin;
        this.name = "gpio" + pin;

        SharedPreferences prefs = App.getPrefs();
        setDebounce(Integer.parseInt(prefs.getString("gpio_debounce", "20")));
        setLongPressDelay(Integer.parseInt(prefs.getString("gpio_long_press_delay", "500")));
        setGenerateIOEvents(prefs.getBoolean("gpio_as_io", true));
        setGenerateButtonEvents(prefs.getBoolean("gpio_as_button", true));
        useInterrupt = prefs.getBoolean("gpio_use_interrupt", true);

        initializate(pin, direction, useInterrupt);

        lastValue = getValue(pin);
        if (lastValue == 0) {
            activeValue = 1;
        }
    }

    private void createPin(int pin, String direction, int activeLowState) {
        createPin(pin, direction);
        setActiveLow(pin, activeLowState);
    }

    public void run() {
        int pool = (useInterrupt) ? pool(pin) : -1;

        while (!Thread.currentThread().isInterrupted()) {
            int currentValue = useInterrupt ? read(pool) : getValue(pin);

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
    }

    private static int getValue(int pin) {
        try {
            Process p = Runtime.getRuntime().exec(String.format("cat /sys/class/gpio/gpio%s/value", pin));
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder text = new StringBuilder();
            String line;
            while((line = reader.readLine()) != null){
                text.append(line);
                text.append("\n");
            }
            try {
                String retour = text.toString();
                if (retour.equals("")){
                    return -1;
                } else 	{
                    return Integer.parseInt(retour.substring(0, 1));
                }
            } catch(NumberFormatException nfe) {
                return -1;
            }
        } catch (IOException e) {
            return -1;
        }
    }

    public static String getDirection(int pin) {
        String command = String.format("cat /sys/class/gpio/gpio%s/direction", pin);
        try {
            Process p = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder text = new StringBuilder();
            String line;
            while((line = reader.readLine()) != null) {
                text.append(line);
                text.append("\n");
            }

            return text.toString();
        } catch (IOException e) {
            return "";
        }
    }

    public static void setState(int pin, String state) {
        if (pin > 0) {
            String direction = getDirection(pin);

            if (direction.isEmpty() || direction.equals("in")) {
                if (!exportAndDirection(pin, state.equals("invert") ? "low" : state)) {
                    Toaster.toast("GPIO: error");
                }
            } else if (state.equals("low") || state.equals("high")) {
                setValue(pin, state.equals("low") ? 0 : 1);
            } else if (state.equals("invert")) {
                int value = getValue(pin);
                if (value > -1) {
                    setValue(pin, (value == 1) ? 0 : 1);
                } else {
                    Toaster.toast("GPIO: value error");
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




    public static void createGpiosFromCommands() {
        for (Command command: Commands.getCommands()) {
            createGpioByKey(command.getKey());
        }
    }

    public static void createGpioByKey(final String key) {
        final int pin = getGpioFromCommandKey(key);
        //pin[0] = pin_number
        //pin[1] = active_low

        if (pin > -1 && !gpios.containsKey(key)) {
            gpios.put(key, new NativeGpio(pin, 0));
            gpios.get(key).start();

            if (App.isDebug()) {
                Log.d(TAG, "pin created: " + String.valueOf(pin));
            }
        }
    }

    private static int getGpioFromCommandKey(String key) {
//        Pattern pattern = Pattern.compile("^gpio([0-9]+)@([0|1])$");
//        Matcher matcher = pattern.matcher(key);
//        if (matcher.find()) {
//            if (App.isDebug()) {
//                Log.d(TAG, "pin parsed: " + matcher.group(1));
//            }
//
//            try {
//                return new int[] {Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))};
//            } catch (NumberFormatException e) {
//                e.printStackTrace();
//            }
//        }

        Pattern pattern = Pattern.compile("^gpio([0-9]+)$");
        Matcher matcher = pattern.matcher(key);
        if (matcher.find()) {
            if (App.isDebug()) {
                Log.d(TAG, "pin parsed: " + matcher.group(1));
            }

            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }

        return -1;
    }

    public static void destroyGpios() {
        if (gpios.size() > 0) {
            for (Map.Entry<String, NativeGpio> entry : gpios.entrySet()) {
                entry.getValue().interrupt();
                gpios.remove(entry.getKey());
            }
        }
    }

    public static void destroyGpioByKey(String key, boolean forced) {
        int countSameKey = 0;

        if (!forced) {
            for (Command command : Commands.getCommands()) {
                if (command.getKey().equals(key)) {
                    countSameKey++;
                }
            }
        }

        if (gpios.containsKey(key) && countSameKey < 2) {
            gpios.get(key).interrupt();
            gpios.remove(key);
        }
    }
}