package kg.delletenebre.serialmanager.Widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.util.Log;
import android.util.TypedValue;
import android.widget.RemoteViews;

import org.apache.commons.lang3.StringEscapeUtils;

import java.io.File;

import kg.delletenebre.serialmanager.EventsReceiver;
import kg.delletenebre.serialmanager.R;
import kg.delletenebre.serialmanager.SerialService;
import xdroid.toaster.Toaster;

public class WidgetSend extends AppWidgetProvider {

    private static final String TAG = "WidgetSend";
    private static final String DEFAULT_WIDGET_TEXT = "\uf0a6";

    static void updateAppWidget(Context context, AppWidgetManager widgetManager,
                                int widgetId) {
        SharedPreferences prefs = context.getSharedPreferences(
                WidgetSendSettings.PREF_PREFIX_KEY + widgetId, Context.MODE_PRIVATE);

        // Construct the RemoteViews object
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_send);
        views.setInt(R.id.widget_container, "setBackgroundColor",
                Color.parseColor(getCurrentValue(prefs, "backgroundColor", "#88000000")));

        views.setImageViewBitmap(R.id.widget_text, getFontBitmap(context, prefs));
        views.setImageViewBitmap(R.id.widget_settings_button, getSettingsBitmap(context));


        // onClick open configure activity
        Intent intent = new Intent(context, WidgetSendSettings.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        views.setOnClickPendingIntent(R.id.widget_settings_button, pendingIntent);

        intent = new Intent(context, EventsReceiver.class);
        intent.setAction(SerialService.WIDGET_SEND_ACTION);
        intent.putExtra("widgetId", widgetId);
        intent.putExtra("data", getCurrentValue(prefs, "data", ""));
        intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
        views.setOnClickPendingIntent(R.id.widget_text, PendingIntent.getBroadcast(context, 0, intent, 0));


        // Instruct the widget manager to update the widget
        widgetManager.updateAppWidget(widgetId, views);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager widgetManager, int[] widgetIds) {
        for (int widgetId : widgetIds) {
            updateAppWidget(context, widgetManager, widgetId);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        // When the user deletes the widget, delete the preference associated with it.

        for (int appWidgetId : appWidgetIds) {
            String prefsName = WidgetSendSettings.PREF_PREFIX_KEY + String.valueOf(appWidgetId);
            context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply();

            try {
                Thread.sleep(250);
                new File(context.getFilesDir().getParent(),
                        "/shared_prefs/" + prefsName + ".xml").delete();
            } catch (InterruptedException e) {
                Log.e(TAG, e.getMessage());
            }

        }
    }

    public static Bitmap getFontBitmap(Context context, SharedPreferences prefs) {

        String text = StringEscapeUtils.unescapeJava(getCurrentValue(prefs, "text", DEFAULT_WIDGET_TEXT));
        String[] textLines = text.split("\n");

        int fontSize = Integer.parseInt(getCurrentValue(prefs, "fontSize", "24"));
        int fontColor = Color.parseColor(getCurrentValue(prefs, "fontColor", "#ffffff"));

        int fontSizePX = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, fontSize,
                context.getResources().getDisplayMetrics());
        int textPadding = (fontSizePX / 9);
        Paint paint = new Paint();
        Typeface typeface = Typeface.createFromAsset(context.getAssets(),
                "fonts/fontawesome-webfont.ttf");
        if (prefs.getBoolean("useCustomFont", false)) {
            try {
                typeface = Typeface.createFromFile(prefs.getString("fontFile", ""));
            } catch (RuntimeException e) {
                Log.e(TAG, e.getLocalizedMessage());
            }
        }

        paint.setAntiAlias(true);
        paint.setTypeface(typeface);
        paint.setColor(fontColor);
        paint.setTextSize(fontSizePX);

        int textWidth = (int) (paint.measureText(getLongestString(textLines)) + textPadding * 2);
        int lineCount = textLines.length > 0 ? textLines.length : 1;
        int height = (int) (lineCount * fontSizePX / 0.85);
        Bitmap bitmap = Bitmap.createBitmap(textWidth, height, Bitmap.Config.ARGB_4444);
        Canvas canvas = new Canvas(bitmap);

        int textX = canvas.getWidth() / 2;
        paint.setTextAlign(Paint.Align.CENTER);

        int textY = fontSizePX;
        for (String line: textLines) {
            canvas.drawText(line, textX, textY, paint);
            textY += -paint.ascent() + paint.descent();
        }

        return bitmap;
    }
    public static Bitmap getSettingsBitmap(Context context) {
        String text = "\uf013";

        int fontSize = 18;
        int fontColor = Color.parseColor("#889E9E9E");

        int fontSizePX = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, fontSize,
                context.getResources().getDisplayMetrics());
        int textPadding = (fontSizePX / 9);
        Paint paint = new Paint();
        Typeface typeface = Typeface.createFromAsset(context.getAssets(),
                "fonts/fontawesome-webfont.ttf");

        paint.setAntiAlias(true);
        paint.setTypeface(typeface);
        paint.setColor(fontColor);
        paint.setTextSize(fontSizePX);

        int textWidth = (int) (paint.measureText(text) + textPadding * 2);
        int height = (int) (fontSizePX / 0.85);
        Bitmap bitmap = Bitmap.createBitmap(textWidth, height, Bitmap.Config.ARGB_4444);
        Canvas canvas = new Canvas(bitmap);

        int textX = canvas.getWidth() / 2;
        paint.setTextAlign(Paint.Align.CENTER);

        canvas.drawText(text, textX, fontSizePX, paint);

        return bitmap;
    }

    public static String getLongestString(String[] array) {
        int maxLength = 0;
        String longestString = null;
        for (String s : array) {
            if (s.length() > maxLength) {
                maxLength = s.length();
                longestString = s;
            }
        }
        return longestString;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        Log.i(TAG, "****SERVICE_SEND_ACTION_COMPLETE****");

        if (intent.getAction().equals(SerialService.SERVICE_SEND_ACTION_COMPLETE)) {
            int widgetId = intent.getIntExtra("widgetId", -1);

            if (widgetId > -1) {
                SharedPreferences prefs = context.getSharedPreferences(
                        WidgetSendSettings.PREF_PREFIX_KEY + widgetId, Context.MODE_PRIVATE);

                if (prefs.getBoolean("switch", false)) {
                    String switchData = prefs.getString("data", "");
                    if (!switchData.isEmpty() && switchData.contains("|")) {
                        String switchDataArray[] = switchData.split("\\|");
                        int switchDataId = prefs.getInt("switch_data_id", 0);

                        if (switchDataId < switchDataArray.length - 1) {
                            switchDataId++;
                        } else {
                            switchDataId = 0;
                        }

                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putInt("switch_data_id", switchDataId);
                        editor.apply();
                    }
                }

                context = context.getApplicationContext();
                AppWidgetManager widgetManager =
                        AppWidgetManager.getInstance(context);

                updateAppWidget(context, widgetManager, widgetId);
            }
        }
    }

//    public static String getCurrentSendingData(SharedPreferences prefs) {
//        String data = prefs.getString("data", "");
//
//        if (prefs.getBoolean("switch", false)) {
//            if (!data.isEmpty() && data.contains("|")) {
//                String switchValues[] = data.split("\\|");
//                int switchDataId = prefs.getInt("switch_data_id", 0);
//
//                if (switchDataId > switchValues.length - 1) {
//                    switchDataId = 0;
//                }
//
//                data = switchValues[switchDataId];
//            }
//        }
//
//        return data;
//    }
//
//    public static String getCurrentText(SharedPreferences prefs) {
//        String text = prefs.getString("text", DEFAULT_WIDGET_TEXT);
//
//        if (prefs.getBoolean("switch", false)) {
//            if (!text.isEmpty() && text.contains("|")) {
//                String switchValues[] = text.split("\\|");
//                int switchDataId = prefs.getInt("switch_data_id", 0);
//
//                if (switchDataId > switchValues.length - 1) {
//                    switchDataId = 0;
//                }
//
//                text = switchValues[switchDataId];
//            }
//        }
//
//        if (text.isEmpty()) {
//            text = DEFAULT_WIDGET_TEXT;
//        }
//
//        return text;
//    }
//
//    public static String getCurrentTextColor(SharedPreferences prefs) {
//        String color = prefs.getString("fontColor", "");
//
//        if (prefs.getBoolean("switch", false)) {
//            if (!color.isEmpty() && color.contains("|")) {
//                String switchValues[] = color.split("\\|");
//                int switchDataId = prefs.getInt("switch_data_id", 0);
//
//                if (switchDataId > switchValues.length - 1) {
//                    switchDataId = 0;
//                }
//
//                color = switchValues[switchDataId];
//            }
//        }
//
//        if (color.isEmpty()) {
//            color = "#fff";
//        }
//
//        return color;
//    }

    public static String getCurrentValue(SharedPreferences prefs, String prefName, String defaultValue) {
        String value = prefs.getString(prefName, defaultValue);

        if (!value.isEmpty() && value.contains("|")) {
            String switchValues[] = value.split("\\|");
            int switchDataId = prefs.getInt("switch_data_id", 0);

            if (!prefs.getBoolean("switch", false) || switchDataId > switchValues.length - 1) {
                switchDataId = 0;
            }

            value = switchValues[switchDataId];
        }

        if (value.isEmpty()) {
            value = defaultValue;
        }

        return value;
    }

}

