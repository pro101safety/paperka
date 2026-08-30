package by.instruction.papera;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;

import java.io.IOException;
import java.io.InputStream;

final class DocumentOpener {
    private DocumentOpener() {
    }

    static String resolveAssetDocFile(Context context, String fileKey) {
        if (context == null || fileKey == null || fileKey.trim().isEmpty()) {
            return null;
        }
        String key = fileKey.trim();
        AssetManager assets = context.getAssets();
        String doc = key + ".doc";
        try (InputStream ignored = assets.open(doc)) {
            return doc;
        } catch (IOException ignored) {
        }
        String docx = key + ".docx";
        try (InputStream ignored = assets.open(docx)) {
            return docx;
        } catch (IOException ignored) {
            return null;
        }
    }

    static Intent createOpenIntent(Context context, String fileKey, String docTitle, String highlightQuery) {
        String actualFileName = resolveAssetDocFile(context, fileKey);
        if (actualFileName == null) {
            return null;
        }
        Intent intent = new Intent(context, FullView.class);
        intent.putExtra("fileName", actualFileName);
        intent.putExtra("docTitle", docTitle == null ? actualFileName : docTitle);
        if (highlightQuery != null && !highlightQuery.trim().isEmpty()) {
            intent.putExtra("initialSearchQuery", highlightQuery.trim());
        }
        return intent;
    }

    static boolean open(Context context, String fileKey, String docTitle, String highlightQuery) {
        Intent intent = createOpenIntent(context, fileKey, docTitle, highlightQuery);
        if (intent == null || context == null) {
            return false;
        }
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
        return true;
    }
}
