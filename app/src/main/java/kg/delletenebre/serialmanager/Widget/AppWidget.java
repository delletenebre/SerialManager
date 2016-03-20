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

import kg.delletenebre.serialmanager.R;
import kg.delletenebre.serialmanager.SerialService;

/**
 * Implementation of App Widget functionality.
 * App Widget Configuration implemented in {@link AppWidgetSettings AppWidgetSettings}
 */
public class AppWidget extends AppWidgetProvider {

    private static final String TAG = "AppWidget";
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

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
                                int appWidgetId, String value) {
        SharedPreferences prefs = context.getSharedPreferences(
                AppWidgetSettings.PREF_PREFIX_KEY + appWidgetId, Context.MODE_PRIVATE);

        int position = Integer.parseInt(prefs.getString("position", "4"));

        int imageViewId = imageViewIds[position];

        int backgroundColor = prefs.getInt("backgroundColor", Color.parseColor("#88000000"));
        // Construct the RemoteViews object
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.app_widget);
        //views.setTextViewText(R.id.appwidget_text, widgetText);
        views.setInt(R.id.appwidget_container, "setBackgroundColor", backgroundColor);

        for (int id : imageViewIds) {
            views.setViewVisibility(id, View.GONE);
        }
        views.setViewVisibility(imageViewId, View.VISIBLE);
        views.setImageViewBitmap(imageViewId, getFontBitmap(context, prefs, value));


        // onClick open configure activity
        Intent intent = new Intent(context, AppWidgetSettings.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        views.setOnClickPendingIntent(R.id.appwidget_container, pendingIntent);


        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, "---");
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        // When the user deletes the widget, delete the preference associated with it.

        for (int appWidgetId : appWidgetIds) {
            String prefsName = AppWidgetSettings.PREF_PREFIX_KEY + String.valueOf(appWidgetId);
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

    public static Bitmap getFontBitmap(Context context, SharedPreferences prefs, String value) {

        StringBuilder sb = new StringBuilder();
        sb.append(prefs.getString("prefix",
                context.getString(R.string.pref_widget_prefix_default_value)));
        sb.append(value);
        sb.append(prefs.getString("suffix",
                context.getString(R.string.pref_widget_suffix_default_value)));
        String text = StringEscapeUtils.unescapeJava(sb.toString());
        String[] textLines = text.split("\n");

        int fontSize = Integer.parseInt(prefs.getString("fontSize", "24"));
        int fontColor = prefs.getInt("fontColor", Color.WHITE);

        int fontSizePX = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, fontSize,
                context.getResources().getDisplayMetrics());
        int textPadding = (fontSizePX / 9);
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

        int textWidth = (int) (paint.measureText(getLongestString(textLines)) + textPadding * 2);
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

        if (intent.getAction().equals(SerialService.MY_BROADCAST_INTENT)) {
            context = context.getApplicationContext();
            AppWidgetManager appWidgetManager =
                    AppWidgetManager.getInstance(context);

            ComponentName thisWidget =
                    new ComponentName(context, AppWidget.class);

            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
            for (int appWidgetId : appWidgetIds) {
                SharedPreferences prefs = context.getSharedPreferences(
                        AppWidgetSettings.PREF_PREFIX_KEY + appWidgetId, Context.MODE_PRIVATE);

                if (prefs.getString("key", "").equals(intent.getStringExtra("KEY"))) {
                    updateAppWidget(context, appWidgetManager, appWidgetId,
                            intent.getStringExtra("VALUE"));
                }
            }
        }
    }

}

