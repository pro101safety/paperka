package by.instruction.papera;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VoiceSearchActivity extends AppCompatActivity {
    static final String EXTRA_AUTO_LISTEN = "autoListen";
    private static final int MAX_VISIBLE_HITS = 3;

    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private LocalDocumentRetriever retriever;
    private EditText inputQuery;
    private TextView textStatus;
    private LinearLayout resultsContainer;
    private ActivityResultLauncher<String> permissionLauncher;
    private ActivityResultLauncher<Intent> speechLauncher;
    private String lastHighlightQuery;
    private boolean speechRequested;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeHelper.enable(this);
        setContentView(R.layout.activity_voice_search);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        EdgeToEdgeHelper.applyAppBarInsets(toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.widget_search_title));
        }
        toolbar.setNavigationOnClickListener(v -> finish());
        EdgeToEdgeHelper.applyBottomInsets(findViewById(R.id.voiceSearchRoot));

        retriever = new LocalDocumentRetriever(this);
        inputQuery = findViewById(R.id.inputQuery);
        textStatus = findViewById(R.id.textStatus);
        resultsContainer = findViewById(R.id.resultsContainer);
        MaterialButton btnVoice = findViewById(R.id.btnVoice);
        MaterialButton btnSearch = findViewById(R.id.btnSearch);

        btnVoice.setOnClickListener(v -> startVoiceInput());
        btnSearch.setOnClickListener(v -> searchTypedQuery());
        inputQuery.setOnEditorActionListener((v, actionId, event) -> {
            searchTypedQuery();
            return true;
        });

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (Boolean.TRUE.equals(granted)) {
                        launchSpeechRecognizer();
                    } else {
                        textStatus.setText(R.string.widget_search_permission);
                    }
                });
        speechLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                        textStatus.setText(R.string.widget_search_speech_canceled);
                        return;
                    }
                    ArrayList<String> matches = result.getData()
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (matches == null || matches.isEmpty() || TextUtils.isEmpty(matches.get(0))) {
                        textStatus.setText(R.string.widget_search_speech_empty);
                        return;
                    }
                    String query = matches.get(0).trim();
                    inputQuery.setText(query);
                    inputQuery.setSelection(query.length());
                    runSearch(query);
                });

        showStoredResult();
        boolean autoListen = getIntent() == null
                || getIntent().getBooleanExtra(EXTRA_AUTO_LISTEN, true);
        if (autoListen && savedInstanceState == null) {
            startVoiceInput();
        }
    }

    private void showStoredResult() {
        WidgetSearchStore.Result last = WidgetSearchStore.load(this);
        if (last.query == null || last.query.trim().isEmpty()) {
            textStatus.setText(R.string.widget_search_status_ready);
            resultsContainer.removeAllViews();
            return;
        }
        inputQuery.setText(last.query);
        if (last.found) {
            SearchHit stored = new SearchHit(last.documentName, last.snippet, last.fileKey);
            bindHits(java.util.Collections.singletonList(stored), last.highlightQuery);
            textStatus.setText(getString(R.string.widget_search_status_found, last.query));
        } else {
            resultsContainer.removeAllViews();
            textStatus.setText(getString(R.string.widget_search_status_empty, last.query));
        }
    }

    private void startVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            return;
        }
        launchSpeechRecognizer();
    }

    private void launchSpeechRecognizer() {
        if (speechRequested) {
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.widget_search_speech_prompt));
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        try {
            speechRequested = true;
            textStatus.setText(R.string.widget_search_status_listening);
            speechLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            speechRequested = false;
            textStatus.setText(R.string.widget_search_speech_unavailable);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        speechRequested = false;
    }

    private void searchTypedQuery() {
        String query = inputQuery.getText() == null ? "" : inputQuery.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(this, R.string.widget_search_no_query, Toast.LENGTH_SHORT).show();
            return;
        }
        runSearch(query);
    }

    private void runSearch(String query) {
        textStatus.setText(R.string.widget_search_status_searching);
        resultsContainer.removeAllViews();
        backgroundExecutor.execute(() -> {
            try {
                DocumentSectionRegistry.ensureLoaded();
                List<LocalDocumentRetriever.DocumentChunk> chunks =
                        retriever.retrieveTopChunks(query, MAX_VISIBLE_HITS);
                List<SearchHit> sources = retriever.toSources(chunks, MAX_VISIBLE_HITS, query);
                String highlight = retriever.pickHighlightQuery(query);
                runOnUiThread(() -> applySearchResult(query, sources, highlight));
            } catch (Throwable t) {
                runOnUiThread(() -> {
                    textStatus.setText(R.string.widget_search_status_error);
                    resultsContainer.removeAllViews();
                });
            }
        });
    }

    private void applySearchResult(String query, List<SearchHit> sources, String highlight) {
        if (sources == null || sources.isEmpty()) {
            resultsContainer.removeAllViews();
            textStatus.setText(getString(R.string.widget_search_status_empty, query));
            WidgetSearchStore.save(this, WidgetSearchStore.empty(query));
            VoiceSearchWidget.refreshAll(this);
            return;
        }
        bindHits(sources, highlight);
        textStatus.setText(getString(R.string.widget_search_status_found, query));
        SearchHit first = sources.get(0);
        String title = displayTitle(first);
        WidgetSearchStore.save(this, new WidgetSearchStore.Result(
                query, title, first.getSnippet(), first.getFileKey(), highlight, true));
        VoiceSearchWidget.refreshAll(this);
    }

    private void bindHits(List<SearchHit> sources, String highlight) {
        lastHighlightQuery = highlight;
        resultsContainer.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        for (SearchHit source : sources) {
            final String fileKey = source.getFileKey();
            final String title = displayTitle(source);
            final String snippet = source.getSnippet();
            View card = inflater.inflate(R.layout.item_voice_search_hit, resultsContainer, false);
            TextView titleView = card.findViewById(R.id.textHitTitle);
            TextView snippetView = card.findViewById(R.id.textHitSnippet);
            titleView.setText(title);
            snippetView.setText(snippet);
            card.setOnClickListener(v -> openResult(fileKey, title));
            resultsContainer.addView(card);
        }
    }

    private String displayTitle(SearchHit source) {
        String title = source.getDocumentName();
        if (title == null || title.trim().isEmpty()) {
            return DocumentSectionRegistry.resolveDisplayName(source.getFileKey());
        }
        return title;
    }

    private void openResult(String fileKey, String documentName) {
        if (fileKey == null || fileKey.trim().isEmpty()) {
            Toast.makeText(this, R.string.widget_search_open_missing, Toast.LENGTH_SHORT).show();
            return;
        }
        boolean opened = DocumentOpener.open(this, fileKey, documentName, lastHighlightQuery);
        if (!opened) {
            Toast.makeText(this, getString(R.string.file_not_found, fileKey), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        backgroundExecutor.shutdownNow();
        super.onDestroy();
    }
}
