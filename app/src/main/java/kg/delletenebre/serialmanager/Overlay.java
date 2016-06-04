package kg.delletenebre.serialmanager;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import kg.delletenebre.serialmanager.Commands.Command;
import xdroid.toaster.Toaster;

public class Overlay implements Handler.Callback {
    private static final String TAG = "** HUD **";
    private static final Handler sHandler = new Handler(Looper.getMainLooper(), new Overlay());
    private static final WindowManager windowManager =
            (WindowManager) App.getContext().getSystemService(Context.WINDOW_SERVICE);

    private View overlayView;
    private int overlayHeight;
    private int overlayTimer;

    private Overlay() {}

    public static void show(Command command, String value) {
        List<Object> data = new ArrayList<>();
        data.add(command);
        data.add(value);
        Message.obtain(sHandler, 0, data).sendToTarget();
    }

    @Override
    public boolean handleMessage(Message msg) {
        List<Object> data = (List<Object>) msg.obj;
        Command command = (Command) data.get(0);
        Command.Overlay overlaySettings = command.getOverlay();
        String value = (String) data.get(1);

        String text = overlaySettings.getText();
        text = text.replaceAll("(<key>)", command.getKey());
        text = text.replaceAll("(<value>)", value);


        Context context = App.getContext();

        DisplayMetrics displaymetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displaymetrics);
        int screenWidth = displaymetrics.widthPixels;
        int screenHeight = displaymetrics.heightPixels;
        int layoutHeight = overlaySettings.isHeightEqualsStatusBar()
                ? App.getStatusBarHeight()
                : WindowManager.LayoutParams.WRAP_CONTENT;
        int layoutWidth = overlaySettings.isWidthEqualsScreen()
                ? WindowManager.LayoutParams.MATCH_PARENT
                : WindowManager.LayoutParams.WRAP_CONTENT;

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                layoutWidth, layoutHeight,
                WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);

        params.gravity = getGravity(overlaySettings.getPosition());
        params.x = overlaySettings.getPositionX();
        params.y = overlaySettings.getPositionY();
        overlayView = ((LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE))
                .inflate(R.layout.hud, null);
        overlayView.setBackgroundColor(overlaySettings.getBackgroundColor());

        overlayHeight = layoutHeight;
        TextView textView = (TextView) overlayView.findViewById(R.id.hudText);
        if (textView != null) {

            if (overlaySettings.isWidthEqualsScreen()) {
                textView.setLayoutParams(
                        new RelativeLayout.LayoutParams(
                                RelativeLayout.LayoutParams.MATCH_PARENT,
                                RelativeLayout.LayoutParams.WRAP_CONTENT));
            }

            textView.setTextColor(overlaySettings.getFontColor());
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, overlaySettings.getFontSize());
            textView.setGravity(getTextAlignment(overlaySettings.getTextAlign()));
            textView.setText(text);
            int fontSize = (int) textView.getTextSize();
            int paddingLR = fontSize / 2;
            int paddingTB = overlaySettings.isHeightEqualsStatusBar() ? 0 : paddingLR;
            overlayView.setPadding(paddingLR, paddingTB, paddingLR, paddingTB);

            if (!overlaySettings.isHeightEqualsStatusBar()) {
                overlayHeight = textView.getHeight() + fontSize +
                        + overlayView.getPaddingBottom() + overlayView.getPaddingTop();
            }
        }

        overlayTimer = overlaySettings.getTimer();
        if (overlayTimer < 1) {
            overlayTimer = 1000;
        }

        windowManager.addView(overlayView, params);

        overlayView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                hide();
                return true;
            }
        });

        show();

        return true;
    }

    private void show() {
        overlayView.post(new Runnable() {
            @Override
            public void run() {
                overlayView.setTranslationY(-overlayHeight);
                overlayView.animate().translationY(0).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        super.onAnimationStart(animation);
                        overlayView.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                hide();
                            }
                        }, overlayTimer);
                    }
                });
            }
        });
    }

    private void hide() {
        if (overlayView != null) {
            overlayView.post(new Runnable() {
                @Override
                public void run() {
                    overlayView.animate().translationY(-overlayHeight).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            overlayView.setVisibility(View.GONE);

                            if (overlayView.getWindowToken() != null) {
                                windowManager.removeView(overlayView);
                            }
                        }
                    });
                }
            });
        }
    }

    private int getGravity(String position) {
        int gravity = Gravity.TOP | Gravity.START;
        switch (position) {
            case "topLeft":
                gravity = Gravity.TOP | Gravity.LEFT;
                break;

            case "topCenter":
                gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                break;

            case "topRight":
                gravity = Gravity.TOP | Gravity.RIGHT;
                break;

            case "middleLeft":
                gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
                break;

            case "middleCenter":
                gravity = Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL;
                break;

            case "middleRight":
                gravity = Gravity.CENTER_VERTICAL | Gravity.RIGHT;
                break;

            case "bottomLeft":
                gravity = Gravity.BOTTOM | Gravity.LEFT;
                break;

            case "bottomCenter":
                gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                break;

            case "bottomRight":
                gravity = Gravity.BOTTOM | Gravity.RIGHT;
                break;
        }

        return gravity;
    }

    private int getTextAlignment(String align) {
        int gravity = Gravity.START;
        switch (align) {
            case "left":
                gravity = Gravity.LEFT;
                break;

            case "center":
                gravity = Gravity.CENTER;
                break;

            case "right":
                gravity = Gravity.RIGHT;
                break;
        }

        return gravity;
    }
}