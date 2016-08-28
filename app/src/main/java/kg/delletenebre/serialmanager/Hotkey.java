package kg.delletenebre.serialmanager;

import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kg.delletenebre.serialmanager.Commands.Command;
import kg.delletenebre.serialmanager.Commands.Commands;


public class Hotkey extends Thread {
    private final static String TAG = "Hotkey";

    private static Map<String, Hotkey> hotkeys = new HashMap<>();

    private int eventId;
    private String commandKey;

    static {
        System.loadLibrary("serial-manager");
    }
    public native int getFileDescriptor(int eventId);
    public native int readKeysByFileDescriptor(int fd);

    public Hotkey(int eventId, String commandKey) {
        this.eventId = eventId;
        this.commandKey = commandKey;
    }

    public void run() {
        int fd = getFileDescriptor(eventId);
        int currentValue = -2;

        if (fd > -1) {
            while (!Thread.currentThread().isInterrupted() || currentValue != -1) {
                currentValue = readKeysByFileDescriptor(fd);
                if (currentValue > 0) {
                    Commands.processReceivedData(
                            String.format("<%s:%s>", commandKey, 1));
                    Log.d(TAG, String.format("<%s:%s>", commandKey, 1));
                }
            }
        }

        if (App.isDebug()) {
            Log.d(TAG, "event" + String.valueOf(eventId) + " thread is interrupted");
        }

        destroyHotkeyByCommandKey(commandKey, false);
    }




    public static void createHotkeysFromCommands() {
        for (Command command: Commands.getCommands()) {
            createHotkeyByCommandKey(command.getKey());
        }
    }

    public static void createHotkeyByCommandKey(final String key) {
        final int eventId = getEventIdFromCommandKey(key);

        if (eventId > -1 && !hotkeys.containsKey(key)) {
            hotkeys.put(key, new Hotkey(eventId, key));
            hotkeys.get(key).start();

            if (App.isDebug()) {
                Log.d(TAG, "Keycodes listener created: /dev/input/event" + String.valueOf(eventId));
            }
        }
    }

    private static int getEventIdFromCommandKey(String key) {
        Pattern pattern = Pattern.compile("^keycode\\|(.+)\\|(.+)$");
        Matcher matcher = pattern.matcher(key);
        if (matcher.find()) {
            if (App.isDebug()) {
                Log.d(TAG, "Keycodes listener parsed: NAME=" + matcher.group(1)
                        + " | EV=" + matcher.group(2));
            }

            try {
                Process output = Runtime.getRuntime().exec("cat /proc/bus/input/devices");
                BufferedReader input =
                        new BufferedReader(new InputStreamReader(output.getInputStream()));
                String line;
                boolean isNameFind = false;
                boolean isEvFind = false;
                int eventId = -1;
                while((line = input.readLine()) != null) {
                    if (line.isEmpty()) {
                        isNameFind = false;
                        isEvFind = false;
                        eventId = -1;
                    }
                    if (line.startsWith("N: Name=")) {
                        isNameFind = line.substring(9, line.length() - 1).contains(matcher.group(1));
                    } else if (line.startsWith("H: Handlers=") && isNameFind) {
                        int index = line.indexOf("event") + 5;
                        eventId = Integer.parseInt(line.substring(index, index + 1));
                    } else if (line.startsWith("B: EV=") && isNameFind) {
                        isEvFind = line.substring(6, line.length()).equalsIgnoreCase(matcher.group(2));
                    }

                    if (isNameFind && eventId > -1 && isEvFind) {
                        Log.d(TAG, "find /dev/input/event" + String.valueOf(eventId));
                        return eventId;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return -1;
    }

    public static void destroyHotkeys() {
        if (hotkeys.size() > 0) {
            for (Map.Entry<String, Hotkey> entry : hotkeys.entrySet()) {
                entry.getValue().interrupt();
                hotkeys.remove(entry.getKey());
            }
        }
    }

    public static void destroyHotkeyByCommandKey(String key, boolean forced) {
        int countSameKey = 0;

        if (!forced) {
            for (Command command : Commands.getCommands()) {
                if (command.getKey().equals(key)) {
                    countSameKey++;
                }
            }
        }

        if (hotkeys.containsKey(key) && countSameKey < 2) {
            hotkeys.get(key).interrupt();
            hotkeys.remove(key);
        }
    }
}
