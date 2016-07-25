package kg.delletenebre.serialmanager;

import android.util.Log;

import com.stericson.RootShell.RootShell;
import com.stericson.RootShell.execution.Command;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Locale;

import kg.delletenebre.serialmanager.Commands.Commands;

public class Gpio extends Thread {

    public String port;
    public int pin;
    public String direction = "";
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

    public Gpio(int pin, String direction) {
        this.port = "gpio" + pin;
        this.pin = pin;

        initPin(direction);
    }

    public void run() {
        while(!Thread.currentThread().isInterrupted()) {
            int currentValue = getValue();

            if (currentValue == activeValue) {
                pressedTime = System.currentTimeMillis();
                if (!buttonActive && (System.currentTimeMillis() - releasedTime) > debounce) {
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
                                String.format(Locale.getDefault(), "<%s:%s>", port, "longPress"));
                    }

                }

            } else {
                releasedTime = System.currentTimeMillis();
                if (buttonActive && (System.currentTimeMillis() - pressedTime) > debounce) {
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
        try {
            Command command = new Command(0, String.format("cat /sys/class/gpio/%s/direction",this.port)) {
                @Override
                public void commandOutput(int id, String line) {
                    direction = line;

                    //MUST call the super method when overriding!
                    super.commandOutput(id, line);
                }
            };

            RootShell.getShell(true).add(command);

            return direction;
        } catch (Exception e) {
            e.printStackTrace();
            direction = "";
            return "";
        }
    }

    // get state of gpio for input and output
    // test if gpio is configure
    public int getValue()
    {
        String command = String.format("cat /sys/class/gpio/%s/value",this.port);
        try {
            Process p = Runtime.getRuntime().exec(new String[] {"su", "-c", command});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder text = new StringBuilder();
            String line;
            while((line = reader.readLine()) != null){
                text.append(line);
                text.append("\n");
            }
            try {
                String retour= text.toString();
                if(retour.equals("")){
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
        String command = String.format("echo %d > /sys/class/gpio/export", this.pin);
        try {
            Runtime.getRuntime().exec(new String[] {"su", "-c", command});
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
}
