package by.instruction.papera;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Note {
    public enum Type {
        TEXT,
        PHOTO
    }

    private String id;
    private String title;
    private String content;
    private long timestamp;
    private Type type;
    private String imagePath;
    
    public Note(String id, String title, String content, long timestamp) {
        this(id, title, content, timestamp, Type.TEXT, null);
    }

    public Note(String id, String title, String content, long timestamp, Type type, String imagePath) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.timestamp = timestamp;
        this.type = type == null ? Type.TEXT : type;
        this.imagePath = imagePath;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public boolean hasPhoto() {
        return type == Type.PHOTO && imagePath != null && !imagePath.isEmpty();
    }
    
    public String getDate() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        return dateFormat.format(new Date(timestamp));
    }
    
    public String getTime() {
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return timeFormat.format(new Date(timestamp));
    }
    
    public String getFormattedDateTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
        return dateFormat.format(new Date(timestamp));
    }
}

