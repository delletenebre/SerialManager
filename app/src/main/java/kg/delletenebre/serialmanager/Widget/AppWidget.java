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
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.util.TypedValue;
import android.widget.RemoteViews;

import org.apache.commons.lang3.StringEscapeUtils;

import java.io.File;

import kg.delletenebre.serialmanager.R;

/**
 * Implementation of App Widget functionality.
 * App Widget Configuration implemented in {@link AppWidgetSettings AppWidgetSettings}
 */
public class AppWidget extends AppWidgetProvider {

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
                                int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(
                AppWidgetSettings.PREF_PREFIX_KEY + appWidgetId, Context.MODE_PRIVATE);

        CharSequence widgetText = prefs.getString("title", "Example");
        int widgetFontSize = Integer.parseInt(prefs.getString("fontSize", "24"));
        // Construct the RemoteViews object
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.app_widget);
        //views.setTextViewText(R.id.appwidget_text, widgetText);
        views.setImageViewBitmap(R.id.appwidget_image_text, getFontBitmap(context,
                StringEscapeUtils.unescapeJava(widgetText.toString()),
                Color.BLACK,
                widgetFontSize));


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
        // There may be multiple widgets active, so update all of them
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        // When the user deletes the widget, delete the preference associated with it.
        for (int appWidgetId : appWidgetIds) {
            String prefsName = AppWidgetSettings.PREF_PREFIX_KEY + appWidgetId;
            context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit();
            try {
                Thread.sleep(250);
                new File(context.getFilesDir().getParent(),
                        "/shared_prefs/" + prefsName + ".xml").delete();
            } catch (InterruptedException e) {}

        }
    }

    @Override
    public void onEnabled(Context context) {
        // Enter relevant functionality for when the first widget is created
    }

    @Override
    public void onDisabled(Context context) {
        // Enter relevant functionality for when the last widget is disabled
    }

    public static Bitmap getFontBitmap(Context context, String text, int color, float fontSizeSP) {
        int fontSizePX = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, fontSizeSP,
                context.getResources().getDisplayMetrics());
        int pad = (fontSizePX / 9);
        Paint paint = new Paint();
        Typeface typeface = Typeface.createFromAsset(context.getAssets(), "fonts/fontawesome-webfont.ttf");
        paint.setAntiAlias(true);
        paint.setTypeface(typeface);
        paint.setColor(color);
        paint.setTextSize(fontSizePX);

        int textWidth = (int) (paint.measureText(text) + pad * 2);
        int height = (int) (fontSizePX / 0.75);
        Bitmap bitmap = Bitmap.createBitmap(textWidth, height, Bitmap.Config.ARGB_4444);
        Canvas canvas = new Canvas(bitmap);

        Rect r = new Rect();
        paint.getTextBounds(text, 0, text.length(), r);
        canvas.drawText(text, pad, fontSizePX, paint);
        return bitmap;
    }

}

