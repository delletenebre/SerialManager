package kg.delletenebre.serialmanager;

class Command implements Comparable<Command>  {
    protected int id;
    protected String uuid;
    protected String key;
    protected String value;
    protected String scatter;
    protected boolean isThrough;
    protected int actionCategoryId;
    protected String action;



    public Command (int id, String uuid, String key, String value, String scatter, boolean isThrough,
                    int actionCategoryId, String action) {
        this.id = id;
        this.uuid = uuid;
        this.key = key;
        this.value = value;
        this.scatter = scatter;
        this.isThrough = isThrough;
        this.actionCategoryId = actionCategoryId;
        this.action = action;
    }

    @Override
    public int compareTo(Command another) {
        return this.id - another.id;
    }

    public int getId() {
        return id;
    }
    public String getUUID() {
        return uuid;
    }

    public void setKey(String key) {
        this.key = key;
    }
    public void setValue(String value) {
        this.value = value;
    }
    public void setScatter(String scatter) {
        this.scatter = scatter;
    }
    public void setIsThrough(boolean isThrough) {
        this.isThrough = isThrough;
    }
    public void setActionCategoryId(int actionCategoryId) {
        this.actionCategoryId = actionCategoryId;
    }
    public void setAction(String action) {
        this.action = action;
    }

    public void update(String key, String value, String scatter, boolean isThrough,
                       int actionCategoryId, String action) {
        setKey(key);
        setValue(value);
        setScatter(scatter);
        setIsThrough(isThrough);
        setActionCategoryId(actionCategoryId);
        setAction(action);
    }
}
