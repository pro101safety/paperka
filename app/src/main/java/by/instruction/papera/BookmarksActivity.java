package by.instruction.papera;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BookmarksActivity extends AppCompatActivity {

    private ListView listView;
    private BookmarkAdapter adapter;
    private final List<Item> items = new ArrayList<>();
    private final List<Item> filteredItems = new ArrayList<>();
    private String lastSearchQuery = "";

    private static class Item {
        final String fileKey;
        final int pageIndex;
        final String title;
        final long timestamp;
        final String uniqueId;
        final String snippet;
        
        Item(String fileKey, int pageIndex, String title, long timestamp, String uniqueId) {
            this(fileKey, pageIndex, title, timestamp, uniqueId, "");
        }

        Item(String fileKey, int pageIndex, String title, long timestamp, String uniqueId, String snippet) {
            this.fileKey = fileKey;
            this.pageIndex = pageIndex;
            this.title = title;
            this.timestamp = timestamp;
            this.uniqueId = uniqueId;
            this.snippet = snippet == null ? "" : snippet;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookmarks);

        // Гарантируем корректное размещение под статус-баром в обеих темах
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            // Не используем ActionBar, работаем только через Toolbar-меню, чтобы исключить конфликты в тёмной теме
            toolbar.getMenu().clear();
            toolbar.inflateMenu(R.menu.bookmarks_menu);
            toolbar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.action_search) {
                    showSearchDialog();
                    return true;
                }
                return false;
            });
        }

        listView = findViewById(R.id.listView);

        try {
            loadData();
        } catch (Throwable t) {
            // В случае неожиданной ошибки загрузки — показываем пустой список, чтобы не падать
            filteredItems.clear();
        }
        adapter = new BookmarkAdapter();
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Item it = filteredItems.get(position);
                android.util.Log.d("BookmarkJump", "Открываем закладку - fileName: " + it.fileKey + ", pageIndex: " + it.pageIndex);
                Intent intent = new Intent(BookmarksActivity.this, FullView.class);
                intent.putExtra("fileName", it.fileKey);
                intent.putExtra("jumpToPage", it.pageIndex);
                startActivity(intent);
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                Item it = filteredItems.get(position);
                new AlertDialog.Builder(BookmarksActivity.this)
                        .setTitle("Удалить закладку?")
                        .setMessage(it.title)
                        .setPositiveButton("Удалить", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    BookmarkStore.removeBookmarkById(BookmarksActivity.this, it.fileKey, it.uniqueId);
                                    loadData();
                                    adapter.notifyDataSetChanged();
                                } catch (Throwable ignored) { }
                            }
                        })
                        .setNegativeButton("Отмена", null)
                        .show();
                return true;
            }
        });
    }

    private void loadData() {
        items.clear();
        // Гарантируем порядок вставки по ключам с помощью LinkedHashMap
        Map<String, ?> all = new LinkedHashMap<>(getSharedPreferences("bookmarks_store", MODE_PRIVATE).getAll());
        for (String key : all.keySet()) {
            List<BookmarkStore.BookmarkEntry> entries = BookmarkStore.getBookmarks(this, key);
            for (BookmarkStore.BookmarkEntry e : entries) {
                items.add(new Item(key, e.pageIndex, e.title, e.timestampMillis, e.uniqueId, e.snippet));
            }
        }
        // Сортируем по времени создания: новые сверху, старые снизу
        try {
            java.util.Collections.sort(items, (a, b) -> Long.compare(b.timestamp, a.timestamp));
        } catch (Throwable ignored) { }
        // Применяем последний поисковый запрос, чтобы список оставался согласованным
        applyFilter(lastSearchQuery);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Меню обрабатывается напрямую тулбаром
        return false;
    }

    private void applyFilter(String q) {
        try {
            lastSearchQuery = (q == null) ? "" : q;
            String needle = lastSearchQuery.trim().toLowerCase();
            filteredItems.clear();
            if (needle.isEmpty()) {
                filteredItems.addAll(items);
            } else {
                for (int i = 0; i < items.size(); i++) {
                    Item it = items.get(i);
                    String titleLower = it.title == null ? "" : it.title.toLowerCase();
                    String snippetLower = it.snippet == null ? "" : it.snippet.toLowerCase();
                    String fileLower = it.fileKey == null ? "" : it.fileKey.toLowerCase();
                    if (titleLower.contains(needle) || snippetLower.contains(needle) || fileLower.contains(needle)) {
                        filteredItems.add(it);
                    }
                }
            }
            if (adapter != null) adapter.notifyDataSetChanged();
        } catch (Throwable ignored) { }

    }

    private void showSearchDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Поиск по закладкам");

        final EditText input = new EditText(this);
        input.setHint("Введите текст");
        input.setSingleLine(true);
        input.setText(lastSearchQuery);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        int margin = (int) (getResources().getDisplayMetrics().density * 16);
        params.setMargins(margin, margin, margin, margin);
        input.setLayoutParams(params);

        builder.setView(input);

        builder.setPositiveButton("Поиск", (dialog, which) -> applyFilter(input.getText().toString()));
        builder.setNegativeButton("Отмена", (dialog, which) -> dialog.dismiss());
        builder.setNeutralButton("Очистить", (dialog, which) -> applyFilter(""));

        AlertDialog dialog = builder.create();
        dialog.show();
        input.requestFocus();
        input.setSelection(input.getText().length());
    }

    private static String formatDate(long ts) {
        if (ts <= 0) return "";
        java.text.DateFormat df = android.text.format.DateFormat.getMediumDateFormat(null);
        java.text.DateFormat tf = android.text.format.DateFormat.getTimeFormat(null);
        return df.format(new java.util.Date(ts)) + " " + tf.format(new java.util.Date(ts));
    }

    private class BookmarkAdapter extends android.widget.BaseAdapter {
        @Override
        public int getCount() { return filteredItems.size(); }

        @Override
        public Object getItem(int position) { return filteredItems.get(position); }

        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = getLayoutInflater().inflate(R.layout.item_bookmark_card, parent, false);
            }
            TextView title = v.findViewById(R.id.title);
            TextView subtitle = v.findViewById(R.id.subtitle);
            TextView pageInfo = v.findViewById(R.id.page_info);
            Item it = filteredItems.get(position);
            
            // Название документа
            title.setText(it.title);
            
            // Показ страницы и сниппета (если есть)
            if (pageInfo != null) {
                StringBuilder sb = new StringBuilder();
                sb.append("Стр. ").append(it.pageIndex + 1);
                if (it.snippet != null && !it.snippet.trim().isEmpty()) {
                    sb.append(" — ");
                    String sn = it.snippet.trim();
                    if (sn.length() > 120) sn = sn.substring(0, 120) + "…";
                    sb.append(sn);
                }
                pageInfo.setVisibility(View.VISIBLE);
                pageInfo.setText(sb.toString());
            }
            
            // Отладочная информация
            android.util.Log.d("BookmarkDisplay", "Item: " + it.title + ", pageIndex: " + it.pageIndex + ", Display: " + (it.pageIndex + 1));
            
            // Дата создания
            String dateStr;
            try {
                java.text.DateFormat df = android.text.format.DateFormat.getMediumDateFormat(BookmarksActivity.this);
                java.text.DateFormat tf = android.text.format.DateFormat.getTimeFormat(BookmarksActivity.this);
                dateStr = df.format(new java.util.Date(it.timestamp)) + " " + tf.format(new java.util.Date(it.timestamp));
            } catch (Throwable t) { dateStr = ""; }
            subtitle.setText("Создано: " + dateStr);
            return v;
        }
    }
}


