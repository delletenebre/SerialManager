package kg.delletenebre.serialmanager.Widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
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
import android.view.View;
import android.widget.RemoteViews;

import org.apache.commons.lang3.StringEscapeUtils;

import java.io.File;

import kg.delletenebre.serialmanager.App;
import kg.delletenebre.serialmanager.R;

public class WidgetReceive extends AppWidgetProvider {

    private static final String TAG = "WidgetReceive";
    private static final int[] imageViewIds = {
            R.id.appwidget_image_text_top_left,
            R.id.appwidget_image_text_top_center,
            R.id.appwidget_image_text_top_right,
            R.id.appwidget_image_text_middle_left,
            R.id.appwidget_image_text_middle_center,
            R.id.appwidget_image_text_middle_right,
            R.id.appwidget_image_text_bottom_left,
            R.id.appwidget_image_text_bottom_center,
            R.id.appwidget_image_text_bottom_right,
    };

    static void updateAppWidget(Context context, AppWidgetManager widgetManager,
                                int widgetId, String value) {
        SharedPreferences prefs = context.getSharedPreferences(
                WidgetReceiveSettings.PREF_PREFIX_KEY + widgetId, Context.MODE_PRIVATE);

        int position = Integer.parseInt(prefs.getString("position", "4"));

        int imageViewId = imageViewIds[position];

        int backgroundColor = prefs.getInt("backgroundColor", Color.parseColor("#88000000"));
        // Construct the RemoteViews object
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_receive);
        //views.setTextViewText(R.id.appwidget_text, widgetText);
        views.setInt(R.id.appwidget_container, "setBackgroundColor", backgroundColor);

        for (int id : imageViewIds) {
            views.setViewVisibility(id, View.GONE);
        }
        views.setViewVisibility(imageViewId, View.VISIBLE);
        views.setImageViewBitmap(imageViewId, getFontBitmap(context, prefs, value));


        // onClick open configure activity
        Intent intent = new Intent(context, WidgetReceiveSettings.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        views.setOnClickPendingIntent(R.id.appwidget_container, pendingIntent);


        // Instruct the widget manager to update the widget
        widgetManager.updateAppWidget(widgetId, views);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager widgetManager, int[] widgetIds) {
        for (int widgetId : widgetIds) {
            updateAppWidget(context, widgetManager, widgetId, "---");
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        // When the user deletes the widget, delete the preference associated with it.

        for (int appWidgetId : appWidgetIds) {
            String prefsName = WidgetReceiveSettings.PREF_PREFIX_KEY + String.valueOf(appWidgetId);
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

    public static Bitmap getFontBitmap(Context context, SharedPreferences prefs, String value) {

        String text =
                prefs.getString("prefix",
                    context.getString(R.string.pref_widget_prefix_default_value))
                + value
                + prefs.getString("suffix",
                    context.getString(R.string.pref_widget_suffix_default_value));

        text = StringEscapeUtils.unescapeJava(text);
        String[] textLines = text.split("\n");

        int fontSize = Integer.parseInt(prefs.getString("fontSize", "24"));
        int fontColor = prefs.getInt("fontColor", Color.WHITE);

        int fontSizePX = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, fontSize,
                context.getResources().getDisplayMetrics());
        int textPadding = (fontSizePX / 5);
        Paint paint = new Paint();
        Typeface typeface = Typeface.createFromAsset(context.getAssets(),
                "fonts/fontawesome-webfont.ttf");
        if (prefs.getBoolean("useCustomFont", false)) {
            try {
                typeface = Typeface.createFromFile(prefs.getString("fontFile", ""));
            } catch (RuntimeException ex) {
                Log.e(TAG, ex.getMessage());
            }
        }

        paint.setAntiAlias(true);
        paint.setTypeface(typeface);
        paint.setColor(fontColor);
        paint.setTextSize(fontSizePX);

        int textWidth = getLongestStringMeasure(paint, textLines) + textPadding * 2;//(int) (paint.measureText(getLongestString()) + textPadding * 4);

        int lineCount = textLines.length > 0 ? textLines.length : 1;
        int height = (int) (lineCount * fontSizePX / 0.85);
        Bitmap bitmap = Bitmap.createBitmap(textWidth, height, Bitmap.Config.ARGB_4444);
        Canvas canvas = new Canvas(bitmap);

        Paint.Align textAlign = Paint.Align.CENTER;
        int textX = canvas.getWidth() / 2;
        switch( prefs.getString("textAlign", "0") ) {
            case "0":
                textAlign = Paint.Align.LEFT;
                textX = textPadding;
                break;

            case "2":
                textAlign = Paint.Align.RIGHT;
                textX = canvas.getWidth() - textPadding;
                break;

        }
        paint.setTextAlign(textAlign);
        //paint.getTextBounds(text, 0, text.length(), new Rect());

        int textY = fontSizePX;
        for (String line: textLines) {
            canvas.drawText(line, textX, textY, paint);
            textY += -paint.ascent() + paint.descent();
            //canvas.drawText(line, pad, fontSizePX, paint);

        }

        return bitmap;
    }

    public static int getLongestStringMeasure(Paint paint, String[] array) {
        float maxWidth = 0;
        for (String s : array) {
            float textWidth = paint.measureText(s);
            if (textWidth > maxWidth) {
                maxWidth = textWidth;
            }
        }

        return (int)maxWidth;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        String key = "";
        String val = "";

        if (intent.getAction().equals(App.ACTION_NEW_DATA_RECEIVED)) {
            key = intent.getStringExtra("key");
            val = intent.getStringExtra("value");
        } else if (intent.getAction().equals("org.kangaroo.rim.action.ACTION_DATA_RECEIVE")) {
            key = intent.getStringExtra("org.kangaroo.rim.device.EXTRA_COMMAND");
            val = intent.getStringExtra("org.kangaroo.rim.device.EXTRA_ARGS");
        }

        if ( !key.isEmpty() ) {
            context = context.getApplicationContext();
            AppWidgetManager widgetManager =
                    AppWidgetManager.getInstance(context);

            ComponentName thisWidget =
                    new ComponentName(context, WidgetReceive.class);

            int[] widgetIds = widgetManager.getAppWidgetIds(thisWidget);
            for (int widgetId : widgetIds) {
                SharedPreferences prefs = context.getSharedPreferences(
                        WidgetReceiveSettings.PREF_PREFIX_KEY + widgetId, Context.MODE_PRIVATE);

                if (prefs.getString("key", "").equals(key)) {
                    updateAppWidget(context, widgetManager, widgetId, val);
                }
            }
        }
    }

}

