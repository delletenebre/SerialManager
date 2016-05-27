package kg.delletenebre.serialmanager.Commands;

import java.io.Serializable;

public class Command implements Serializable {
    private long id;
    private String key;
    private String value;
    private float scatter;
    private boolean through;
    private String category;
    private String action;
    private String actionString;
    private int position;


    public Command(long id, String key, String value, float scatter, boolean through,
                   String category, String action, String actionString, int position) {
        this.id = id;
        this.key = key;
        this.value = value;
        this.scatter = scatter;
        this.through = through;
        this.category = category;
        this.action = action;
        this.actionString = actionString;
        this.position = position;
    }

    public long getId() {
        return id;
    }
    public Command setId(long id) {
        this.id = id;
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
}
