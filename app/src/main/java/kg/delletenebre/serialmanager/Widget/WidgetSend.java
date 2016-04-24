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

import kg.delletenebre.serialmanager.App;
import kg.delletenebre.serialmanager.EventsReceiver;
import kg.delletenebre.serialmanager.R;

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
        intent.setAction(App.ACTION_SEND_DATA);
        intent.putExtra("widgetId", widgetId);
        intent.putExtra("data", getCurrentValue(prefs, "data", ""));
        intent.putExtra("sendTo", prefs.getString("sendTo", "0"));
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
                if (!new File(context.getFilesDir().getParent(),
                        "/shared_prefs/" + prefsName + ".xml").delete() && App.isDebug()) {
                    Log.w(TAG, "Error deleting preferences file");
                }
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
        int textPadding = (fontSizePX / 2);
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

        int textWidth = WidgetReceive.getLongestStringMeasure(paint, textLines) + textPadding * 2;
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

        int fontSize = 20;
        int fontColor = Color.parseColor("#209E9E9E");

        int fontSizePX = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, fontSize,
                context.getResources().getDisplayMetrics());
        int textPadding = (fontSizePX / 5);
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

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);


        int widgetId = intent.getIntExtra("widgetId", -1);
        if (widgetId == -1) {
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(
                WidgetSendSettings.PREF_PREFIX_KEY + widgetId, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        if (intent.getAction().equals(App.ACTION_SEND_DATA_SUCCESS)) {
            if (App.isDebug()) {
                Log.i(TAG, "****SERVICE_SEND_ACTION_SUCCESS****");
            }

            if (prefs.getString("sendTo", "usb_bt").equals("usb_bt")) {
                boolean status = prefs.getBoolean("status", false);

                if (!status) {
                    editor.putBoolean("status", true);
                    editor.apply();
                } else {
                    return;
                }
            }

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

