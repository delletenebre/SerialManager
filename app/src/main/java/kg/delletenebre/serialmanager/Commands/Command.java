package kg.delletenebre.serialmanager.Commands;

import android.graphics.Color;

import java.io.Serializable;

import kg.delletenebre.serialmanager.App;
import kg.delletenebre.serialmanager.R;

public class Command implements Serializable {
    private long id = -1;
    private String type = "default";
    private int gpioPinNumber = -1;
    private String keyboardName = "";
    private String keyboardEv = "";
    private String key = "";
    private String value = "";
    private float scatter = 0.0f;
    private boolean through = false;
    private String category = "none";
    private String action = "";
    private String actionString = "";
    private int position = -1;
    private Overlay overlay = new Overlay();

    public long getId() {
        return id;
    }
    public Command setId(long id) {
        this.id = id;
        return this;
    }

    public String getType() {
        return type;
    }
    public Command setType(String type) {
        this.type = type;
        return this;
    }

    public int getGpioPinNumber() {
        return gpioPinNumber;
    }
    public Command setGpioPinNumber(int gpioPinNumber) {
        this.gpioPinNumber = gpioPinNumber;
        return this;
    }

    public String getKeyboardName() {
        return keyboardName;
    }
    public Command setKeyboardName(String keyboardName) {
        this.keyboardName = keyboardName;
        return this;
    }

    public String getKeyboardEv() {
        return keyboardEv;
    }
    public Command setKeyboardEv(String keyboardEv) {
        this.keyboardEv = keyboardEv;
        return this;
    }

    public String getKey() {
        return key;
    }
    public Command setKey(String key) {
        this.key = key;
        return this;
    }

    public String getValue() {
        return value;
    }
    public Command setValue(String value) {
        this.value = value;
        return this;
    }

    public float getScatter() {
        return scatter;
    }
    public Command setScatter(float scatter) {
        this.scatter = scatter;
        return this;
    }

    public boolean getThrough() {
        return through;
    }
    public Command setThrough(boolean through) {
        this.through = through;
        return this;
    }

    public String getCategory() {
        return category;
    }
    public Command setCategory(String category) {
        this.category = category;
        return this;
    }

    public String getAction() {
        return action;
    }
    public Command setAction(String action) {
        this.action = action;
        return this;
    }

    public void setActionString(String actionString) {
        this.actionString = actionString;
    }
    public String getActionString() {
        return actionString;
    }

    public int getPosition() {
        return position;
    }
    public Command setPosition(int position) {
        this.position = position;
        return this;
    }

    public Overlay getOverlay() {
        return overlay;
    }




    public class Overlay implements Serializable {
        private boolean enabled = false;
        private String text = App.getContext().getString(R.string.pref_co_default_text);
        private int timer = Integer.parseInt(App.getContext().getString(R.string.pref_co_default_timer));
        private boolean hideOnClick = false;
        private String showAnimation = App.getContext().getString(R.string.pref_co_default_show_animation);
        private String hideAnimation = App.getContext().getString(R.string.pref_co_default_hide_animation);
        private String position = App.getContext().getString(R.string.pref_co_default_position);
        private int positionX = Integer.parseInt(App.getContext().getString(R.string.pref_co_default_position_x));
        private int positionY = Integer.parseInt(App.getContext().getString(R.string.pref_co_default_position_y));
        private boolean heightEqualsStatusBar = false;
        private boolean widthEqualsScreen = false;
        private String textAlign = App.getContext().getString(R.string.pref_co_default_text_align);
        private int fontSize = Integer.parseInt(App.getContext().getString(R.string.pref_co_default_font_size));
        private int fontColor = Color.WHITE;
        private int backgroundColor = Color.parseColor("#88000000");

        public boolean isEnabled() {
            return enabled;
        }
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getText() {
            return text;
        }
        public void setText(String text) {
            this.text = text;
        }

        public int getTimer() {
            return timer;
        }
        public void setTimer(int timer) {
            this.timer = timer;
        }

        public boolean isHideOnClick() {
            return hideOnClick;
        }
        public void setHideOnClick(boolean state) {
            this.hideOnClick = state;
        }

        public String getShowAnimation() {
            return showAnimation;
        }
        public void setShowAnimation(String animation) {
            this.showAnimation = animation;
        }

        public String getHideAnimation() {
            return hideAnimation;
        }
        public void setHideAnimation(String animation) {
            this.hideAnimation = animation;
        }

        public String getPosition() {
            return position;
        }
        public void setPosition(String position) {
            this.position = position;
        }

        public boolean isHeightEqualsStatusBar() {
            return heightEqualsStatusBar;
        }
        public void setHeightEqualsStatusBar(boolean heightEqualsStatusBar) {
            this.heightEqualsStatusBar = heightEqualsStatusBar;
        }

        public boolean isWidthEqualsScreen() {
            return widthEqualsScreen;
        }
        public void setWidthEqualsScreen(boolean widthEqualsScreen) {
            this.widthEqualsScreen = widthEqualsScreen;
        }

        public int getPositionX() {
            return positionX;
        }
        public void setPositionX(int positionX) {
            this.positionX = positionX;
        }

        public int getPositionY() {
            return positionY;
        }
        public void setPositionY(int positionY) {
            this.positionY = positionY;
        }

        public String getTextAlign() {
            return textAlign;
        }
        public void setTextAlign(String textAlign) {
            this.textAlign = textAlign;
        }

        public int getFontSize() {
            return fontSize;
        }
        public void setFontSize(int fontSize) {
            this.fontSize = fontSize;
        }

        public int getFontColor() {
            return fontColor;
        }
        public void setFontColor(int fontColor) {
            this.fontColor = fontColor;
        }

        public int getBackgroundColor() {
            return backgroundColor;
        }
        public void setBackgroundColor(int backgroundColor) {
            this.backgroundColor = backgroundColor;
        }
    }
}
