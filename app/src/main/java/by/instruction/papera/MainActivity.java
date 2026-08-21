package by.instruction.papera;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ExpandableListView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import android.content.res.Configuration;
import android.util.TypedValue;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import by.instruction.papera.data.CatalogStore;

// Класс для хранения результатов поиска
class SearchResultItem {
    private final String title;
    private final String fileName;
    private final String chapterName;
    
    SearchResultItem(String title, String fileName, String chapterName) {
        this.title = title;
        this.fileName = fileName;
        this.chapterName = chapterName;
    }
    
    public String getTitle() {
        return title;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public String getChapterName() {
        return chapterName;
    }
    
    @Override
    public String toString() {
        return title;
    }
}

public class MainActivity extends AppCompatActivity {

    //объявляем переменные для кнопки назад
    private long backPressedTime;
    private Toast backToast;
    //конец объявления переменных для кнопки назад

    ExpandableListView expandableListView;
    CustomAdapter customAdapter;
    List<Chapter> chapterList;

    // Добавляем недостающие переменные для поиска
    ListView listView;
    ArrayAdapter<SearchResultItem> arrayAdapter;
    List<SearchResultItem> searchResults;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Включаем стандартную раскладку без налезания под статус-бар
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        // Toolbar для стабильного отображения меню/поиска в светлой теме
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null && getSupportActionBar() == null) {
            setSupportActionBar(toolbar);
            // Устанавливаем кастомный заголовок с уменьшенным шрифтом
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(getString(R.string.app_name));
                getSupportActionBar().setDisplayShowTitleEnabled(true);
            }
        }

        // Инициализируем список для поиска
        searchResults = new ArrayList<>();

        //пишем код поиска
        listView = findViewById(R.id.listView);
        arrayAdapter = new ArrayAdapter<SearchResultItem>(this, R.layout.list_customtext, searchResults) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                android.view.View view = super.getView(position, convertView, parent);
                SearchResultItem item = getItem(position);
                if (item != null) {
                    TextView textView = view.findViewById(R.id.list_customeText);
                    textView.setText(item.getTitle());
                }
                return view;
            }
        };
        listView.setAdapter(arrayAdapter);
        
        // Добавляем обработчик нажатий на элементы списка поиска
        listView.setOnItemClickListener((parent, view, position, id) -> {
            SearchResultItem item = searchResults.get(position);
            openDocument(item.getFileName(), item.getTitle());
        });
        //финал кода поиска

        expandableListView = (ExpandableListView) findViewById(R.id.expandableListView);

        // Убираем ручные отступы — теперь тулбар в разметке

        // Не меняем флаги статус-бара вручную — оставляем управление теме
        addData();
        sendData();

        // Проверяем поддержку архитектуры
        checkArchitectureSupport();
    }

    private void checkArchitectureSupport() {
        String arch = System.getProperty("os.arch");
        String abi = Build.SUPPORTED_ABIS[0];

        Log.d("Architecture", "Current architecture: " + arch);
        Log.d("Architecture", "Primary ABI: " + abi);

        // Проверяем поддержку 16 КБ страниц (Android 14+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Log.d("Architecture", "16 KB page size support: Available but disabled for compatibility");
        } else {
            Log.d("Architecture", "16 KB page size support: Not available (requires Android 14+)");
        }

        Log.d("Architecture", "Native libraries compatibility mode: Enabled");
    }

    private void addActionBarPadding() {
        // Получаем высоту ActionBar
        int actionBarHeight = 0;
        if (getSupportActionBar() != null) {
            actionBarHeight = getSupportActionBar().getHeight();
        }

        // Если ActionBar еще не измерен, получаем его высоту из темы
        if (actionBarHeight == 0) {
            TypedValue tv = new TypedValue();
            if (getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
                actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data, getResources().getDisplayMetrics());
            }
        }

        // Добавляем отступ сверху для ExpandableListView
        if (expandableListView != null && actionBarHeight > 0) {
            int topPadding = actionBarHeight + getResources().getDimensionPixelSize(android.R.dimen.app_icon_size) / 4;
            expandableListView.setPadding(
                    expandableListView.getPaddingLeft(),
                    topPadding,
                    expandableListView.getPaddingRight(),
                    expandableListView.getPaddingBottom()
            );

            // Также добавляем отступ для ListView (результаты поиска)
            if (listView != null) {
                listView.setPadding(
                        listView.getPaddingLeft(),
                        topPadding,
                        listView.getPaddingRight(),
                        listView.getPaddingBottom()
                );
            }
        }
    }

    void addData() {
        chapterList = CatalogStore.load(this);
        if (chapterList.isEmpty()) {
            Toast.makeText(this, "Не удалось загрузить каталог документов", Toast.LENGTH_LONG).show();
        }
    }

    void sendData() {
        customAdapter = new CustomAdapter(chapterList, MainActivity.this);
        expandableListView.setAdapter(customAdapter);
    }

    private void performSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            // Очищаем результаты поиска
            searchResults.clear();
            arrayAdapter.notifyDataSetChanged();
            return;
        }

        searchResults.clear();
        String lowerQuery = query.toLowerCase().trim();

        // Поиск по всем главам и темам
        for (Chapter chapter : chapterList) {
            for (Topics topic : chapter.getTopicsList()) {
                if (topic.getTopicName().toLowerCase().contains(lowerQuery)) {
                    searchResults.add(new SearchResultItem(
                        topic.getTopicName(),
                        topic.getFileName(),
                        chapter.getChapterName()
                    ));
                }
            }
        }

        // Обновляем адаптер поиска
        arrayAdapter.notifyDataSetChanged();

        // Показываем результаты поиска
        expandableListView.setVisibility(View.GONE);
        listView.setVisibility(View.VISIBLE);
        
        // Если ничего не найдено, показываем toast
        if (searchResults.isEmpty()) {
            android.widget.Toast.makeText(this, "Ничего не найдено", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        getMenuInflater().inflate(R.menu.main_actionbar_menu, menu);

        // На главном экране скрываем кнопку добавления закладки
        MenuItem bm = menu.findItem(R.id.add_bookmark);
        if (bm != null) bm.setVisible(false);


        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.privacy:
                startActivity(new Intent(MainActivity.this, Privacy.class));
                return true;
            case R.id.contact:
                startActivity(new Intent(MainActivity.this, Contact.class));
                return true;
            case R.id.bookmarks:
                startActivity(new Intent(MainActivity.this, BookmarksActivity.class));
                return true;
            case R.id.notes:
                startActivity(new Intent(MainActivity.this, NotesActivity.class));
                return true;
            case R.id.iot_game:
                startActivity(new Intent(MainActivity.this, by.instruction.papera.game.IotGameActivity.class));
                return true;
            case R.id.search:
                // Показываем диалог поиска
                showSearchDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    //начало кода системной кнопки назад
    @Override
    public void onBackPressed() {
        // Если показываются результаты поиска, возвращаемся к главному экрану
        if (listView != null && listView.getVisibility() == View.VISIBLE) {
            // Очищаем результаты поиска
            searchResults.clear();
            arrayAdapter.notifyDataSetChanged();
            
            // Показываем главный экран
            expandableListView.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
            
            // Обновляем заголовок
            getSupportActionBar().setTitle(getString(R.string.app_name));
            
            return;
        }

        if (backPressedTime + 2000 > System.currentTimeMillis()){
            if (backToast != null) backToast.cancel();
            super.onBackPressed();
            return;
        }else{
            backToast = Toast.makeText(getBaseContext(), "Теперь можно бахнуть кофейку:)", Toast.LENGTH_SHORT);
            backToast.show();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask();
            finishAffinity();
        }
        backPressedTime = System.currentTimeMillis();
    }
    //конец кода системной кнопки назад
    
    // Диалог поиска
    private void showSearchDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Поиск по документам");
        
        // Создаем EditText для ввода поискового запроса
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Введите текст для поиска");
        input.setSingleLine(true);
        
        // Устанавливаем размеры EditText
        android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(50, 20, 50, 20);
        input.setLayoutParams(params);
        
        builder.setView(input);
        
        builder.setPositiveButton("Поиск", (dialog, which) -> {
            String query = input.getText().toString().trim();
            if (!query.isEmpty()) {
                performSearch(query);
            }
        });
        
        builder.setNegativeButton("Отмена", (dialog, which) -> dialog.cancel());
        
        // Кнопка "Очистить"
        builder.setNeutralButton("Очистить", (dialog, which) -> {
            // Очищаем результаты поиска
            searchResults.clear();
            arrayAdapter.notifyDataSetChanged();
            // Показываем основной список
            expandableListView.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
        });
        
        android.app.AlertDialog dialog = builder.create();
        dialog.show();
        
        // Фокус на поле ввода
        input.requestFocus();
        input.selectAll();
    }
    
    // Метод для открытия документа
    private void openDocument(String fileName, String docTitle) {
        // Проверяем наличие файла с расширениями .doc или .docx
        String actualFileName = null;
        try {
            InputStream test = getAssets().open(fileName + ".doc");
            test.close();
            actualFileName = fileName + ".doc";
        } catch (IOException e1) {
            try {
                InputStream test = getAssets().open(fileName + ".docx");
                test.close();
                actualFileName = fileName + ".docx";
            } catch (IOException e2) {
                Toast.makeText(this, "Файл не найден: " + fileName + ".doc/.docx", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        Intent intent = new Intent(this, FullView.class);
        intent.putExtra("fileName", actualFileName);
        intent.putExtra("docTitle", docTitle);
        startActivity(intent);
    }
}