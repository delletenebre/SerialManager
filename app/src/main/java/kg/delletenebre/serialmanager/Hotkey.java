package kg.delletenebre.serialmanager;

import android.icu.text.SymbolTable;
import android.os.Handler;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kg.delletenebre.serialmanager.Commands.Command;
import kg.delletenebre.serialmanager.Commands.Commands;


public class Hotkey extends Thread {
    private final static String TAG = "Hotkey";

    private static Map<String, Hotkey> hotkeys = new HashMap<>();
    private static int detectDelay = 100;
    private static final int detectKeyboardDelay = 5000;

    private static Handler detectKeyboardsHandler = new Handler();
    private static Runnable detectKeyboardsRunnable = new Runnable() {
        @Override
        public void run() {
            Hotkey.createFromCommands();
            detectKeyboardsHandler.postDelayed(detectKeyboardsRunnable, detectKeyboardDelay);
        }
    };

    private int eventId;
    private String commandIdentifier;
    private Keycode keycode;
    private Handler delayDetectKeyPressHandler = new Handler();
    private ArrayList<Integer> pressedKeys = new ArrayList<>();

    static {
        System.loadLibrary("serial-manager");
    }
    public native int getFileDescriptor(int eventId);
    public native String readKeysByFileDescriptor(int fd);

    public Hotkey(int eventId, String commandIdentifier) {
        this.eventId = eventId;
        this.commandIdentifier = commandIdentifier;
    }

    public void run() {
        int fd = getFileDescriptor(eventId);

        Runnable delayDetectKeyPressRunnable = new Runnable() {
            @Override
            public void run() {
                if (keycode != null) {
                    if (pressedKeys.size() > 0) {
                        StringBuilder stringBuilder = new StringBuilder();
                        for (Integer pressedKey: pressedKeys) {
                            stringBuilder.append(pressedKey).append("+");
                        }
                        Commands.processReceivedData(
                                String.format("<%s:%s>", commandIdentifier,
                                        stringBuilder.toString().substring(
                                                0, stringBuilder.length() - 1)));
                    }

                }
            }
        };

        if (fd > -1) {
            String currentValue;

            while (!Thread.currentThread().isInterrupted()) {
                currentValue = readKeysByFileDescriptor(fd);

                if (App.isDebug()) {
                    Log.d(TAG, String.valueOf(currentValue));
                }

                if (currentValue.equals("error")) {
                    Thread.currentThread().interrupt();
                    break;
                }

                if (!currentValue.isEmpty() && currentValue.contains(".")) {
                    keycode = new Keycode(currentValue);
                    if (keycode.code > 0) {
                        delayDetectKeyPressHandler.removeCallbacks(delayDetectKeyPressRunnable);

                        if (keycode.isPressed) {
                            pressedKeys.add(keycode.code);
                            delayDetectKeyPressHandler.postDelayed(delayDetectKeyPressRunnable, detectDelay);
                        } else {
                            pressedKeys.removeAll(Collections.singleton(keycode.code));
                        }
                    }

                    if (App.isDebug()) {
                        Log.d(TAG, String.format("code: %s | text:%s | isPressed:%s",
                                keycode.code, keycode.text, keycode.isPressed));
                    }
                }
            }
        }

        delayDetectKeyPressHandler.removeCallbacks(delayDetectKeyPressRunnable);
        destroyByIdentifier(commandIdentifier);

        if (App.isDebug()) {
            Log.d(TAG, "event" + String.valueOf(eventId) + " thread is interrupted");
        }
    }

    class Keycode {
        protected boolean isPressed = false;
        protected int code = 0;
        protected String text = "";

        public Keycode(String string) {
            String[] result = string.split("\\.");
            int[] part1 = splitToInt(result[0], "\\|");
            int[] part2 = splitToInt(result[1], "\\|");

            if (part2[0] > 0) {
                this.code = part2[0];
                this.isPressed = part2[1] == 1;
                this.text = "";
            } else if (part1[0] > 0) {
                this.code = part1[0];
                this.isPressed = part1[1] == 1;
                this.text = "";
            }
        }

        private int[] splitToInt(String string, String divider) {
            String[] str = string.split(divider);
            int[] result = new int[str.length];
            for (int i = 0; i < result.length; i++) {
                try {
                    result[i] = Integer.parseInt(str[i]);
                } catch (NumberFormatException e) {
                    result[i] = -1;
                    e.printStackTrace();
                }
            }

            return result;
        }
    }


    public static void startAutodetectKeyboards() {
        detectKeyboardsHandler.removeCallbacks(detectKeyboardsRunnable);
        detectKeyboardsHandler.postDelayed(detectKeyboardsRunnable, detectKeyboardDelay);
    }

    public static void stopAutodetectKeyboards() {
        detectKeyboardsHandler.removeCallbacks(detectKeyboardsRunnable);
    }

    public static void setDetectDelay(int value) {
        detectDelay = value;
    }

    public static void createFromCommands() {
        setDetectDelay(App.getIntPreference("hotkeys_detect_delay", 100));

        for (Command command: Commands.getCommands()) {
            create(command);
        }
    }

    public static void create(Command command) {
        String parameterName = command.getKeyboardName();
        String parameterEv = command.getKeyboardEv();
        if (command.getType().equals("keyboard") && !parameterName.isEmpty() && !hotkeys.containsKey(command.getKey())) {
            int eventId = -1;

            try {
                Process output = Runtime.getRuntime().exec("cat /proc/bus/input/devices");
                BufferedReader input =
                        new BufferedReader(new InputStreamReader(output.getInputStream()));
                String line;
                boolean isNameFind = false;
                boolean isEvFind = false;
                while((line = input.readLine()) != null) {
                    if (line.isEmpty()) {
                        isNameFind = false;
                        isEvFind = false;
                        eventId = -1;
                    }
                    if (line.startsWith("N: Name=")) {
                        isNameFind = line.substring(9, line.length() - 1).contains(parameterName);
                    } else if (line.startsWith("H: Handlers=") && isNameFind) {
                        int index = line.indexOf("event") + 5;
                        eventId = Integer.parseInt(line.substring(index, index + 1));
                    } else if (line.startsWith("B: EV=") && isNameFind) {
                        isEvFind = line.substring(6, line.length()).equalsIgnoreCase(parameterEv);
                    }

                    if (isNameFind && eventId > -1 && isEvFind) {
                        Log.d(TAG, "find /dev/input/event" + String.valueOf(eventId));
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (eventId > -1) {
                String commandIdentifier = createCommandIdentifier(parameterName, parameterEv);
                hotkeys.put(commandIdentifier, new Hotkey(eventId, commandIdentifier));
                hotkeys.get(commandIdentifier).start();

                if (App.isDebug()) {
                    Log.d(TAG, "Keyboards listener created: /dev/input/event" + String.valueOf(eventId));
                }
            }
        }
    }

    public static void destroyAll() {
        detectKeyboardsHandler.removeCallbacks(detectKeyboardsRunnable);

        if (hotkeys.size() > 0) {
            for (Map.Entry<String, Hotkey> entry : hotkeys.entrySet()) {
                entry.getValue().interrupt();
                hotkeys.remove(entry.getKey());
            }
        }
    }

    public static void destroyByIdentifier(String identifier) {
        int countSameKey = 0;

        for (Command command : Commands.getCommands()) {
            if (createCommandIdentifier(command.getKeyboardName(), command.getKeyboardEv()).equals(identifier)) {
                countSameKey++;
            }
        }

        if (hotkeys.containsKey(identifier) && countSameKey < 2) {
            hotkeys.get(identifier).interrupt();
            hotkeys.remove(identifier);
        }
    }

    public static String createCommandIdentifier(String part1, String part2) {
        return String.format("$keyboard|%s|%s", part1, part2);
    }
}
