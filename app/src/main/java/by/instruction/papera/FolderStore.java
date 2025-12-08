package by.instruction.papera;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FolderStore {

    static final String PREFS_NAME = "notes_store";
    private static final String FOLDERS_KEY = "note_folders";
    public static final String DEFAULT_FOLDER_ID = Note.DEFAULT_FOLDER_ID;
    public static final String DEFAULT_FOLDER_NAME = "Общие";

    private FolderStore() {}

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static List<NoteFolder> getAllFolders(Context context) {
        List<NoteFolder> folders = new ArrayList<>();
        String raw = getPrefs(context).getString(FOLDERS_KEY, null);
        if (raw != null && !raw.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String id = obj.optString("id", "");
                    String name = obj.optString("name", "");
                    long createdAt = obj.optLong("createdAt", System.currentTimeMillis());
                    if (!id.isEmpty() && !name.isEmpty()) {
                        folders.add(new NoteFolder(id, name, createdAt));
                    }
                }
            } catch (JSONException ignored) {
            }
        }

        if (folders.isEmpty()) {
            folders.add(ensureDefaultFolder(context));
        } else {
            ensureDefaultFolder(context);
        }

        Collections.sort(folders, Comparator.comparingLong(NoteFolder::getCreatedAt));
        return folders;
    }

    public static NoteFolder ensureDefaultFolder(Context context) {
        List<NoteFolder> folders = internalGetFolders(context);
        for (NoteFolder folder : folders) {
            if (DEFAULT_FOLDER_ID.equals(folder.getId())) {
                return folder;
            }
        }
        NoteFolder defaultFolder = new NoteFolder(DEFAULT_FOLDER_ID, DEFAULT_FOLDER_NAME, System.currentTimeMillis());
        folders.add(0, defaultFolder);
        saveAllFolders(context, folders);
        return defaultFolder;
    }

    public static void saveFolder(Context context, NoteFolder folder) {
        List<NoteFolder> folders = internalGetFolders(context);
        boolean updated = false;
        for (int i = 0; i < folders.size(); i++) {
            if (folders.get(i).getId().equals(folder.getId())) {
                folders.set(i, folder);
                updated = true;
                break;
            }
        }
        if (!updated) {
            folders.add(folder);
        }
        saveAllFolders(context, folders);
    }

    public static void deleteFolder(Context context, String folderId) {
        if (folderId == null || DEFAULT_FOLDER_ID.equals(folderId)) {
            return;
        }
        List<NoteFolder> folders = internalGetFolders(context);
        List<NoteFolder> updated = new ArrayList<>();
        for (NoteFolder folder : folders) {
            if (!folder.getId().equals(folderId)) {
                updated.add(folder);
            }
        }
        saveAllFolders(context, updated);
    }

    public static boolean folderNameExists(Context context, String name) {
        List<NoteFolder> folders = internalGetFolders(context);
        for (NoteFolder folder : folders) {
            if (folder.getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public static String generateFolderId() {
        return "folder_" + System.currentTimeMillis();
    }

    private static List<NoteFolder> internalGetFolders(Context context) {
        List<NoteFolder> folders = new ArrayList<>();
        String raw = getPrefs(context).getString(FOLDERS_KEY, null);
        if (raw != null && !raw.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String id = obj.optString("id", "");
                    String name = obj.optString("name", "");
                    long createdAt = obj.optLong("createdAt", System.currentTimeMillis());
                    if (!id.isEmpty() && !name.isEmpty()) {
                        folders.add(new NoteFolder(id, name, createdAt));
                    }
                }
            } catch (JSONException ignored) {
            }
        }
        return folders;
    }

    private static void saveAllFolders(Context context, List<NoteFolder> folders) {
        JSONArray arr = new JSONArray();
        for (NoteFolder folder : folders) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("id", folder.getId());
                obj.put("name", folder.getName());
                obj.put("createdAt", folder.getCreatedAt());
            } catch (JSONException ignored) {
            }
            arr.put(obj);
        }
        getPrefs(context).edit().putString(FOLDERS_KEY, arr.toString()).apply();
    }
}

