package by.instruction.papera;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class NoteStore {
    
    private static final String PREFS_NAME = "notes_store";
    private static final String NOTES_KEY = "notes_list";
    
    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    public static List<Note> getAllNotes(Context context) {
        List<Note> notes = new ArrayList<>();
        String raw = getPrefs(context).getString(NOTES_KEY, null);
        if (raw == null || raw.isEmpty()) {
            return notes;
        }
        
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String id = obj.optString("id", "");
                String title = obj.optString("title", "");
                String content = obj.optString("content", "");
                long timestamp = obj.optLong("timestamp", System.currentTimeMillis());
                String typeRaw = obj.optString("type", Note.Type.TEXT.name());
                String imagePath = obj.optString("imagePath", null);
                Note.Type type;
                try {
                    type = Note.Type.valueOf(typeRaw);
                } catch (IllegalArgumentException e) {
                    type = Note.Type.TEXT;
                }
                String folderId = obj.optString("folderId", Note.DEFAULT_FOLDER_ID);
                notes.add(new Note(id, title, content, timestamp, type, imagePath, folderId));
            }
        } catch (JSONException e) {
            // Ignore
        }
        
        return notes;
    }
    
    public static void saveNote(Context context, Note note) {
        if (note.getType() == null) {
            note.setType(note.hasPhoto() ? Note.Type.PHOTO : Note.Type.TEXT);
        }
        if (note.getFolderId() == null || note.getFolderId().isEmpty()) {
            note.setFolderId(Note.DEFAULT_FOLDER_ID);
        }
        List<Note> notes = getAllNotes(context);
        
        // Если заметка с таким ID уже существует, обновляем её
        boolean found = false;
        for (int i = 0; i < notes.size(); i++) {
            if (notes.get(i).getId().equals(note.getId())) {
                notes.set(i, note);
                found = true;
                break;
            }
        }
        
        // Если не найдена, добавляем новую
        if (!found) {
            notes.add(note);
        }
        
        saveAllNotes(context, notes);
    }
    
    public static void deleteNote(Context context, String noteId) {
        List<Note> notes = getAllNotes(context);
        List<Note> filtered = new ArrayList<>();
        for (Note note : notes) {
            if (!note.getId().equals(noteId)) {
                filtered.add(note);
            }
        }
        saveAllNotes(context, filtered);
    }

    public static void deleteNotesInFolder(Context context, String folderId) {
        if (folderId == null) {
            folderId = Note.DEFAULT_FOLDER_ID;
        }
        List<Note> notes = getAllNotes(context);
        List<Note> filtered = new ArrayList<>();
        for (Note note : notes) {
            if (!folderId.equals(note.getFolderId())) {
                filtered.add(note);
            } else if (note.hasPhoto()) {
                try {
                    String path = note.getImagePath();
                    if (path != null) {
                        File file = new File(path);
                        if (file.exists()) {
                            file.delete();
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        saveAllNotes(context, filtered);
    }
    
    private static void saveAllNotes(Context context, List<Note> notes) {
        JSONArray arr = new JSONArray();
        for (Note note : notes) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("id", note.getId());
                obj.put("title", note.getTitle());
                obj.put("content", note.getContent());
                obj.put("timestamp", note.getTimestamp());
                obj.put("type", note.getType() == null ? Note.Type.TEXT.name() : note.getType().name());
                obj.put("folderId", note.getFolderId());
                if (note.getImagePath() != null) {
                    obj.put("imagePath", note.getImagePath());
                } else {
                    obj.remove("imagePath");
                }
            } catch (JSONException e) {
                // Ignore
            }
            arr.put(obj);
        }
        // Используем commit() для гарантированного сохранения критически важных данных
        // commit() синхронно записывает данные и гарантирует сохранение до возврата
        // Это важно для сохранения заметок при обновлении приложения
        SharedPreferences.Editor editor = getPrefs(context).edit();
        editor.putString(NOTES_KEY, arr.toString());
        editor.commit(); // commit() гарантирует немедленное сохранение на диск
    }
    
    public static String generateId() {
        return "note_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
    }
}

