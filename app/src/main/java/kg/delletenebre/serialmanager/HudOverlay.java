package kg.delletenebre.serialmanager;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.PixelFormat;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;

import kg.delletenebre.serialmanager.Commands.Command;

public class HudOverlay {
    private static final String TAG = "** HUD **";

    private WindowManager windowManager;
    private View overlayView;
    private int overlayHeight;
    private int overlayTimer;
    private Command.Overlay overlay;


    public HudOverlay(Command.Overlay overlay, String key, String value) {
        Context context = App.getContext();
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        this.overlay = overlay;

        //DisplayMetrics displaymetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(new DisplayMetrics());
//        int screenWidth = displaymetrics.widthPixels;
//        int screenHeight = displaymetrics.heightPixels;
        int layoutHeight = overlay.isHeightEqualsStatusBar()
                ? App.getStatusBarHeight()
                : WindowManager.LayoutParams.WRAP_CONTENT;
        int layoutWidth = overlay.isWidthEqualsScreen()
                ? WindowManager.LayoutParams.MATCH_PARENT
                : WindowManager.LayoutParams.WRAP_CONTENT;

        int hideOnClickAsFlag = overlay.isHideOnClick()
                ? WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                : WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                layoutWidth, layoutHeight,
                WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,

                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | hideOnClickAsFlag,

                PixelFormat.TRANSLUCENT);

        params.gravity = getGravity(overlay.getPosition());
        params.x = overlay.getPositionX();
        params.y = overlay.getPositionY();
        overlayView = ((LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE))
                .inflate(R.layout.hud, null);
        overlayView.setBackgroundColor(overlay.getBackgroundColor());

        overlayHeight = layoutHeight;
        TextView textView = (TextView) overlayView.findViewById(R.id.hudText);
        if (textView != null) {

            if (overlay.isWidthEqualsScreen()) {
                textView.setLayoutParams(
                        new RelativeLayout.LayoutParams(
                                RelativeLayout.LayoutParams.MATCH_PARENT,
                                RelativeLayout.LayoutParams.WRAP_CONTENT));
            }

            textView.setTextColor(overlay.getFontColor());
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, overlay.getFontSize());
            textView.setGravity(getTextAlignment(overlay.getTextAlign()));
            textView.setText(App.prepareText(overlay.getText(), key, value));
            int fontSize = (int) textView.getTextSize();
            int paddingLR = fontSize / 2;
            int paddingTB = overlay.isHeightEqualsStatusBar() ? 0 : paddingLR;
            overlayView.setPadding(paddingLR, paddingTB, paddingLR, paddingTB);

            if (!overlay.isHeightEqualsStatusBar()) {
                overlayHeight = textView.getHeight() + fontSize +
                        + overlayView.getPaddingBottom() + overlayView.getPaddingTop();
            }
        }

        overlayTimer = overlay.getTimer();
        if (overlayTimer < 1) {
            overlayTimer = 1000;
        }

        windowManager.addView(overlayView, params);

        if (overlay.isHideOnClick()) {
            overlayView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    removeOverlay();
                }
            });
        }
    }

    public void show() {
        overlayView.post(showRunnable);
    }
    private Runnable showRunnable = new Runnable() {
        @Override
        public void run() {
            ViewPropertyAnimator viewPropertyAnimator = null;
            int modificator = 0;

            switch (overlay.getShowAnimation()) {
                //* -------- -------- -------- -------- *//
                case "none":
                    overlayView.setAlpha(0.9f);
                    viewPropertyAnimator = overlayView.animate().alpha(1.0f);
                    break;
                //* -------- -------- -------- -------- *//
                case "fade":
                    overlayView.setAlpha(0.0f);
                    viewPropertyAnimator = overlayView.animate().alpha(1.0f);
                    break;
                //* -------- -------- -------- -------- *//
                case "slide_up":
                case "slide_down":
                    modificator = overlay.getShowAnimation().equals("slide_up") ? 1 : -1;
                    overlayView.setTranslationY(modificator * overlayHeight);
                    viewPropertyAnimator = overlayView.animate().translationY(0);
                    break;
                //* -------- -------- -------- -------- *//
                case "slide_up_fade":
                case "slide_down_fade":
                    modificator = overlay.getShowAnimation().equals("slide_up_fade") ? 1 : -1;
                    overlayView.setAlpha(0.0f);
                    overlayView.setTranslationY(modificator * overlayHeight);
                    viewPropertyAnimator = overlayView.animate().alpha(1.0f).translationY(0);
                    break;
                //* -------- -------- -------- -------- *//
            }


            if (viewPropertyAnimator != null) {
                viewPropertyAnimator.setListener(showAnimatorListenerAdapter);
            }
        }
    };
    private Runnable hideRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                ViewPropertyAnimator viewPropertyAnimator = null;

                switch (overlay.getHideAnimation()) {
                    //* -------- -------- -------- -------- *//
                    case "none":
                        overlayView.setAlpha(0.1f);
                        viewPropertyAnimator = overlayView.animate().alpha(0.0f);
                        break;
                    //* -------- -------- -------- -------- *//
                    case "fade":
                        viewPropertyAnimator = overlayView.animate().alpha(0.0f);
                        break;
                    //* -------- -------- -------- -------- *//
                    case "slide_up":
                        viewPropertyAnimator = overlayView.animate().translationY(-overlayHeight);
                        break;
                    //* -------- -------- -------- -------- *//
                    case "slide_down":
                        viewPropertyAnimator = overlayView.animate().translationY(overlayHeight);
                        break;
                    //* -------- -------- -------- -------- *//
                    case "slide_up_fade":
                        viewPropertyAnimator = overlayView.animate().alpha(0.0f).translationY(-overlayHeight);
                        break;
                    //* -------- -------- -------- -------- *//
                    case "slide_down_fade":
                        viewPropertyAnimator = overlayView.animate().alpha(0.0f).translationY(overlayHeight);
                        break;
                    //* -------- -------- -------- -------- *//
                }

                if (viewPropertyAnimator != null) {
                    viewPropertyAnimator.setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);

                            removeOverlay();
                        }
                    });
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };



    private AnimatorListenerAdapter showAnimatorListenerAdapter = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationStart(Animator animation) {
            overlayView.setVisibility(View.VISIBLE);

            super.onAnimationStart(animation);
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            overlayView.postDelayed(hideRunnable, overlayTimer);

            super.onAnimationEnd(animation);
        }
    };


    private void removeOverlay() {
        if (overlayView != null) {
            overlayView.setAlpha(0.0f);
            overlayView.setVisibility(View.GONE);

            overlayView.removeCallbacks(showRunnable);
            overlayView.removeCallbacks(hideRunnable);

            windowManager.removeView(overlayView);
            overlayView = null;
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