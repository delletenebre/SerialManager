package kg.delletenebre.serialmanager;

import android.content.Intent;
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

public class Gpio extends Thread {
    private final static String TAG = "GPIO";

    public String port;
    public int pin;
    int activeValue = 0;
    int lastValue = 1;

    private boolean buttonActive = false;
    private boolean longPressActive = false;

    private long buttonTimer = 0;
    private static long longPressDelay = 500;
    private long pressedTime = -1;
    private long releasedTime = -1;
    private static long debounce = 20;

    private static boolean generateIOEvents = true;
    private static boolean generateButtonEvents = true;

    private static Map<String, Gpio> gpios = new HashMap<>();

    public Gpio(int pin, String direction) {
        this.pin = pin;
        this.port = "gpio" + pin;

        initPin(direction);
    }

    public void run() {
        while(!Thread.currentThread().isInterrupted()) {
            int currentValue = getValue();

            if (currentValue == activeValue) {
                pressedTime = System.currentTimeMillis();

                if ((System.currentTimeMillis() - releasedTime) > debounce) {
                    if (!buttonActive) {
                        buttonActive = true;
                        buttonTimer = System.currentTimeMillis();
                        if (generateIOEvents) {
                            Commands.processReceivedData(
                                    String.format(Locale.getDefault(), "<%s:%d>", port, currentValue));
                        }
                    }

                    if (System.currentTimeMillis() - buttonTimer > longPressDelay
                            && !longPressActive) {

                        longPressActive = true;
                        if (generateButtonEvents) {
                            Commands.processReceivedData(
                                    String.format(Locale.getDefault(), "<%s:%s>", port, "hold"));
                        }

                    }
                }

            } else {
                releasedTime = System.currentTimeMillis();

                if ((System.currentTimeMillis() - pressedTime) > debounce) {
                    if (buttonActive) {
                        if (longPressActive) {
                            longPressActive = false;
                        } else if (generateButtonEvents) {
                            Commands.processReceivedData(
                                    String.format(Locale.getDefault(), "<%s:%s>", port, "click"));
                        }

                        if (generateIOEvents) {
                            Commands.processReceivedData(
                                    String.format(Locale.getDefault(), "<%s:%d>", port, currentValue));
                        }
                        buttonActive = false;
                    }
                }
            }
        }

        this.desactivatePin();
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

    //get direction of gpio
    public String getDirection()
    {
        String command = String.format("cat /sys/class/gpio/%s/direction",this.port);
        try {
            Process p = Runtime.getRuntime().exec(new String[] {"su", "-c", command});
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

    // get state of gpio for input and output
    // test if gpio is configure
    public int getValue()
    {
        try {
            Process p = Runtime.getRuntime().exec(String.format("cat /sys/class/gpio/%s/value", this.port));
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder text = new StringBuilder();
            String line;
            while((line = reader.readLine()) != null){
                text.append(line);
                text.append("\n");
            }
            try {
                String retour= text.toString();
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

    //set value of the output
    public boolean setValue(int value){
        String command = String.format("echo %d > /sys/class/gpio/%s/value", value, this.port);
        try {
            String[] test = new String[] {"su", "-c", command};
            Runtime.getRuntime().exec(test);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // set direction
    public boolean setDirection(String direction){
        String command = String.format("echo %s > /sys/class/gpio/%s/direction", direction,this.port);
        try {
            Runtime.getRuntime().exec(new String[] {"su", "-c", command});
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    //export gpio
    public boolean activatePin(){
        try {
            Runtime.getRuntime().exec(new String[] {"su", "-c",
                    String.format("echo %d > /sys/class/gpio/export", this.pin)});
            Runtime.getRuntime().exec(new String[] {"su", "-c",
                    String.format("chmod 0777 /sys/class/gpio/%s", this.port)});
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // unexport gpio
    public boolean desactivatePin(){
        String command = String.format("echo %d > /sys/class/gpio/unexport", this.pin);
        try {
            Runtime.getRuntime().exec(new String[] {"su", "-c", command});
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    //init the pin
    public int initPin(String direction){
        int retour;
        boolean ret;

        // see if gpio is already set
        retour = getValue();
        lastValue = retour;
        if (retour == -1) {
            // unexport the gpio
            ret = desactivatePin();
            if (!ret) {
                retour = -1;
            }

            //export the gpio
            ret = activatePin();
            if (!ret) {
                retour = -2;
            } else {
                lastValue = getValue();
            }
        }
        if (lastValue == 0) {
            activeValue = 1;
        }

        // get If gpio direction is define
        String ret2 = getDirection();
        if (!ret2.contains(direction)) {
            // set the direction (in or out)
            ret = setDirection(direction);
            if (!ret) {
                retour = -3;
            }
        }

        debounce = Integer.parseInt(App.getPrefs().getString("gpio_debounce", "20"));
        longPressDelay = Integer.parseInt(App.getPrefs().getString("gpio_long_press_delay", "500"));
        generateIOEvents = App.getPrefs().getBoolean("gpio_as_io", true);
        generateButtonEvents = App.getPrefs().getBoolean("gpio_as_button", true);

        return retour;
    }



    public static void createGpiosFromCommands() {
        for (Command command: Commands.getCommands()) {
            createGpioByKey(command.getKey());
        }
    }

    public static void createGpioByKey(final String key) {
        final int pin = getGpioNumberFromCommandKey(key);

        if (pin > -1 && !gpios.containsKey(key)) {
            gpios.put(key, new Gpio(pin, "in"));
            gpios.get(key).start();

            if (App.isDebug()) {
                Log.d(TAG, "pin created: " + String.valueOf(pin));
            }
        }
    }

    private static int getGpioNumberFromCommandKey(String key) {
        final Pattern pattern = Pattern.compile("^gpio(\\d+?)$");
        final Matcher matcher = pattern.matcher(key);
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

//    public static void createGpioByKey(String key) { // for geekbox
//        final Pattern pattern = Pattern.compile("^gpio(\\d+?)\\_([a-d]+?)(\\d+?)$",
//                Pattern.CASE_INSENSITIVE);
//        final Matcher matcher = pattern.matcher(key);
//        if (matcher.find()) {
//            if (App.isDebug()) {
//                Log.d(TAG, "parsed from command: " + matcher.group(1) + "_" + matcher.group(2)
//                        + matcher.group(3));
//            }
//
//            int pin = -1;
//            try {
//                int x = 32 * Integer.parseInt(matcher.group(1));
//                int y = 8;
//                switch (matcher.group(2).toLowerCase()) {
//                    case "a":
//                        y *= 0;
//                        break;
//                    case "b":
//                        y *= 1;
//                        break;
//                    case "c":
//                        y *= 2;
//                        break;
//                    case "d":
//                        y *= 3;
//                        break;
//                }
//                int z = Integer.parseInt(matcher.group(3));
//
//                pin = x + y + z;
//            } catch (NumberFormatException e) {
//                e.printStackTrace();
//            }
//
//            if (pin > -1) {
//                destroyGpioByKey(key);
//
//                gpios.put(key, new Gpio(pin, key, "in"));
//                gpios.get(key).start();
//
//
//                if (App.isDebug()) {
//                    Log.d(TAG, "pin created: " + String.valueOf(pin));
//                }
//            }
//        }
//    }

    public static void destroyGpios() {
        if (gpios.size() > 0) {
            for (Map.Entry<String, Gpio> entry : gpios.entrySet()) {
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
