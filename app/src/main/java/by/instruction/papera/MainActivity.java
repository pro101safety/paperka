package by.instruction.papera;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
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

import android.content.res.Configuration;

import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationView;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

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
    private String lastCatalogSearchQuery = "";
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private MaterialToolbar toolbar;
    private ActivityResultLauncher<String> callPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeHelper.enable(this);
        setContentView(R.layout.activity_main);

        // Toolbar для стабильного отображения меню/поиска в светлой теме
        toolbar = findViewById(R.id.toolbar);
        EdgeToEdgeHelper.applyToolbarScreenInsets(toolbar, findViewById(R.id.root));
        if (toolbar != null && getSupportActionBar() == null) {
            setSupportActionBar(toolbar);
            // Устанавливаем кастомный заголовок с уменьшенным шрифтом
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(getString(R.string.app_name));
                getSupportActionBar().setDisplayShowTitleEnabled(true);
            }
        }
        if (toolbar != null) {
            toolbar.setNavigationIcon(R.drawable.ic_baseline_menu_24);
            toolbar.setNavigationOnClickListener(v -> toggleDrawer());
            toolbar.setOnMenuItemClickListener(this::onOptionsItemSelected);
        }

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.navigation_view);
        EdgeToEdgeHelper.applyNavigationViewInsets(navigationView);
        callPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (Boolean.TRUE.equals(isGranted)) {
                placeCallDirectly();
            } else {
                Toast.makeText(this, R.string.emergency_call_permission_rationale, Toast.LENGTH_SHORT).show();
                openDialerFallback();
            }
        });
        if (navigationView != null) {
            navigationView.setNavigationItemSelectedListener(item -> {
                boolean handled = handleMenuItem(item.getItemId());
                if (handled) {
                    closeDrawer();
                }
                return handled;
            });

            MenuItem emergencyItem = navigationView.getMenu().findItem(R.id.emergency_112);
            if (emergencyItem != null && emergencyItem.getActionView() != null) {
                View emergencyButton = emergencyItem.getActionView().findViewById(R.id.btnEmergencyMenu112);
                if (emergencyButton != null) {
                    emergencyButton.setOnClickListener(v -> {
                        closeDrawer();
                        startEmergencyCall();
                    });
                }
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
            openDocument(item.getFileName(), item.getTitle(), lastCatalogSearchQuery);
        });
        //финал кода поиска

        expandableListView = (ExpandableListView) findViewById(R.id.expandableListView);

        // Убираем ручные отступы — теперь тулбар в разметке

        // Не меняем флаги статус-бара вручную — оставляем управление теме
        addData();
        DocumentSectionRegistry.refreshFromChapters(chapterList);
        sendData();

        setupBackPressedHandler();
    }

    @Override
    protected void onDestroy() {
        dismissToolbarMenus();
        super.onDestroy();
    }

    private void dismissToolbarMenus() {
        closeOptionsMenu();
        if (toolbar != null) {
            toolbar.dismissPopupMenus();
        }
    }

    void addData() {
        chapterList = ChapterCatalog.build();
    }


    void sendData() {
        customAdapter = new CustomAdapter(chapterList, MainActivity.this);
        expandableListView.setAdapter(customAdapter);
    }

    private void performSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            // Очищаем результаты поиска
            lastCatalogSearchQuery = "";
            searchResults.clear();
            arrayAdapter.notifyDataSetChanged();
            return;
        }

        searchResults.clear();
        lastCatalogSearchQuery = query.trim();
        String lowerQuery = lastCatalogSearchQuery.toLowerCase();

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
            Toast.makeText(this, R.string.search_nothing_found, Toast.LENGTH_SHORT).show();
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
        if (handleMenuItem(item.getItemId())) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    //начало кода системной кнопки назад
    private void setupBackPressedHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                    return;
                }

                // Если показываются результаты поиска, возвращаемся к главному экрану
                if (listView != null && listView.getVisibility() == View.VISIBLE) {
                    searchResults.clear();
                    arrayAdapter.notifyDataSetChanged();

                    expandableListView.setVisibility(View.VISIBLE);
                    listView.setVisibility(View.GONE);

                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle(getString(R.string.app_name));
                    }

                    return;
                }

                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    if (backToast != null) {
                        backToast.cancel();
                    }
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    return;
                }

                backToast = Toast.makeText(getBaseContext(), R.string.exit_toast_coffee, Toast.LENGTH_SHORT);
                backToast.show();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    finishAndRemoveTask();
                    finishAffinity();
                }
                backPressedTime = System.currentTimeMillis();
            }
        });
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
        openDocument(fileName, docTitle, null);
    }

    private void openDocument(String fileName, String docTitle, String initialSearchQuery) {
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
                Toast.makeText(this, getString(R.string.file_not_found, fileName), Toast.LENGTH_SHORT).show();
                return;
            }
        }

        Intent intent = new Intent(this, FullView.class);
        intent.putExtra("fileName", actualFileName);
        intent.putExtra("docTitle", docTitle);
        if (initialSearchQuery != null && !initialSearchQuery.trim().isEmpty()) {
            intent.putExtra("initialSearchQuery", initialSearchQuery.trim());
        }
        startActivity(intent);
    }

    private boolean handleMenuItem(int id) {
        if (id == R.id.privacy) {
            startActivity(new Intent(MainActivity.this, Privacy.class));
            return true;
        } else if (id == R.id.contact) {
            startActivity(new Intent(MainActivity.this, Contact.class));
            return true;
        } else if (id == R.id.bookmarks) {
            startActivity(new Intent(MainActivity.this, BookmarksActivity.class));
            return true;
        } else if (id == R.id.notes) {
            startActivity(new Intent(MainActivity.this, NotesActivity.class));
            return true;
        } else if (id == R.id.iot_game) {
            startActivity(new Intent(MainActivity.this, by.instruction.papera.game.IotGameActivity.class));
            return true;
        } else if (id == R.id.voice_search) {
            Intent voiceSearch = new Intent(MainActivity.this, VoiceSearchActivity.class);
            voiceSearch.putExtra(VoiceSearchActivity.EXTRA_AUTO_LISTEN, false);
            startActivity(voiceSearch);
            return true;
        } else if (id == R.id.luxometer) {
            startActivity(new Intent(MainActivity.this, LuxometerActivity.class));
            return true;
        } else if (id == R.id.noise_meter) {
            startActivity(new Intent(MainActivity.this, NoiseMeterActivity.class));
            return true;
        } else if (id == R.id.compass) {
            startActivity(new Intent(MainActivity.this, CompassActivity.class));
            return true;
        } else if (id == R.id.level) {
            startActivity(new Intent(MainActivity.this, LevelActivity.class));
            return true;
        } else if (id == R.id.vibrometer) {
            startActivity(new Intent(MainActivity.this, VibrometerActivity.class));
            return true;
        } else if (id == R.id.emergency_112) {
            startEmergencyCall();
            return true;
        } else if (id == R.id.search) {
            // Показываем диалог поиска
            showSearchDialog();
            return true;
        }
        return false;
    }

    private void closeDrawer() {
        if (drawerLayout != null) {
            drawerLayout.closeDrawer(GravityCompat.START, true);
        }
    }

    private void toggleDrawer() {
        if (drawerLayout == null) {
            return;
        }
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            drawerLayout.openDrawer(GravityCompat.START);
        }
    }

    private void startEmergencyCall() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            placeCallDirectly();
        } else {
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE);
        }
    }

    private void placeCallDirectly() {
        Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:112"));
        if (intent.resolveActivity(getPackageManager()) != null) {
            try {
                startActivity(intent);
            } catch (SecurityException e) {
                openDialerFallback();
            }
        } else {
            openDialerFallback();
        }
    }

    private void openDialerFallback() {
        Intent dialIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"));
        if (dialIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(dialIntent);
        } else {
            Toast.makeText(this, R.string.emergency_call_failed, Toast.LENGTH_SHORT).show();
        }
    }
}