package by.instruction.papera;

import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AiChatActivity extends AppCompatActivity {
    private static final String SMALLTALK_ASSET = "ai_smalltalk_rules.json";
    private final List<AiChatMessage> messages = new ArrayList<>();
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final List<SmallTalkRule> smallTalkRules = new ArrayList<>();
    private AiChatAdapter adapter;
    private RecyclerView recyclerView;
    private EditText inputMessage;
    private TextView textChatMode;
    private LocalDocumentRetriever retriever;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_chat);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.ai_chat_title));
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        retriever = new LocalDocumentRetriever(this);
        loadSmallTalkRules();
        recyclerView = findViewById(R.id.chatRecyclerView);
        inputMessage = findViewById(R.id.inputMessage);
        textChatMode = findViewById(R.id.textChatMode);
        MaterialButton btnSend = findViewById(R.id.btnSend);
        android.view.View chatInputContainer = findViewById(R.id.chatInputContainer);

        adapter = new AiChatAdapter(this::openSourceDocument);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        updateModeIndicator(getString(R.string.ai_chat_mode_indexing), 0xFF555555);
        applyBottomInsets(chatInputContainer);

        btnSend.setOnClickListener(v -> submitQuestion());
        inputMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitQuestion();
                return true;
            }
            return false;
        });
        addAssistantMessage("Привет! Задайте вопрос по охране труда. Я отвечу по документам, доступным в приложении.", null);

        // Прогреваем индекс в фоне, чтобы сократить задержку первого ответа.
        backgroundExecutor.execute(() -> {
            try {
                retriever.ensureIndexBuilt();
                runOnUiThread(() -> updateModeIndicator(getString(R.string.ai_chat_mode_offline), 0xFF8A5B00));
            } catch (Throwable t) {
                runOnUiThread(() -> {
                    updateModeIndicator(getString(R.string.ai_chat_mode_offline), 0xFF8A5B00);
                    Toast.makeText(this, "Индекс документов загружен частично (ограничение памяти)", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void submitQuestion() {
        String query = inputMessage.getText().toString().trim();
        if (TextUtils.isEmpty(query)) {
            Toast.makeText(this, R.string.ai_chat_no_query, Toast.LENGTH_SHORT).show();
            return;
        }
        if (query.length() > 500) {
            query = query.substring(0, 500);
        }

        inputMessage.setText("");
        addUserMessage(query);
        addAssistantMessage(getString(R.string.ai_chat_loading), null);
        final int loadingIndex = messages.size() - 1;
        final String safeQuery = query;

        backgroundExecutor.execute(() -> {
            try {
                String freeChatAnswer = tryBuildFreeChatAnswer(safeQuery);
                if (freeChatAnswer != null) {
                    runOnUiThread(() -> {
                        updateModeIndicator(getString(R.string.ai_chat_mode_offline), 0xFF8A5B00);
                        replaceLoadingMessage(loadingIndex, freeChatAnswer, new ArrayList<>());
                    });
                    return;
                }

                List<LocalDocumentRetriever.DocumentChunk> chunks = retriever.retrieveTopChunks(safeQuery, 5);
                if (chunks.isEmpty()) {
                    runOnUiThread(() -> replaceLoadingMessage(loadingIndex, getString(R.string.ai_chat_no_context), new ArrayList<>()));
                    return;
                }

                List<AiSource> sources = retriever.toSources(chunks, 3, safeQuery);
                final String offlineText = buildOfflineAnswer(safeQuery, chunks);
                runOnUiThread(() -> {
                    updateModeIndicator(getString(R.string.ai_chat_mode_offline), 0xFF8A5B00);
                    replaceLoadingMessage(loadingIndex, offlineText, sources);
                });
            } catch (Throwable t) {
                runOnUiThread(() -> {
                    updateModeIndicator(getString(R.string.ai_chat_mode_offline), 0xFF8A5B00);
                    replaceLoadingMessage(loadingIndex, "Не удалось обработать запрос из-за ограничения памяти. Попробуйте более короткий запрос.", new ArrayList<>());
                });
            }
        });
    }

    private String tryBuildFreeChatAnswer(String query) {
        String normalized = normalizeUserQuery(query);
        if (normalized.isEmpty()) {
            return null;
        }

        for (SmallTalkRule rule : smallTalkRules) {
            if (rule == null || rule.response == null || rule.response.trim().isEmpty()) {
                continue;
            }
            if (containsAny(normalized, rule.patterns.toArray(new String[0]))) {
                return rule.response;
            }
        }
        return null;
    }

    private boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeUserQuery(String query) {
        if (query == null) {
            return "";
        }
        return query.toLowerCase()
                .replace('ё', 'е')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void loadSmallTalkRules() {
        smallTalkRules.clear();
        smallTalkRules.addAll(defaultSmallTalkRules());
        try {
            String json = readAssetText(SMALLTALK_ASSET);
            if (json == null || json.trim().isEmpty()) {
                return;
            }
            JSONObject root = new JSONObject(json);
            JSONArray rules = root.optJSONArray("rules");
            if (rules == null || rules.length() == 0) {
                return;
            }
            List<SmallTalkRule> parsed = new ArrayList<>();
            for (int i = 0; i < rules.length(); i++) {
                JSONObject item = rules.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String response = item.optString("response", "").trim();
                JSONArray patternsJson = item.optJSONArray("patterns");
                if (response.isEmpty() || patternsJson == null || patternsJson.length() == 0) {
                    continue;
                }
                List<String> patterns = new ArrayList<>();
                for (int j = 0; j < patternsJson.length(); j++) {
                    String p = normalizeUserQuery(patternsJson.optString(j, ""));
                    if (!p.isEmpty()) {
                        patterns.add(p);
                    }
                }
                if (!patterns.isEmpty()) {
                    parsed.add(new SmallTalkRule(patterns, response));
                }
            }
            if (!parsed.isEmpty()) {
                smallTalkRules.clear();
                smallTalkRules.addAll(parsed);
            }
        } catch (Exception ignored) {
            // fallback already loaded
        }
    }

    private String readAssetText(String fileName) throws IOException {
        try (InputStream is = getAssets().open(fileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    private List<SmallTalkRule> defaultSmallTalkRules() {
        List<SmallTalkRule> rules = new ArrayList<>();
        rules.add(new SmallTalkRule(
                asList("привет", "здравствуй", "добрый день", "добрый вечер", "салам"),
                "Привет! Я оффлайн-помощник ПАПЕРКА. Могу отвечать на простые вопросы и помогать искать информацию по охране труда в локальных документах."
        ));
        rules.add(new SmallTalkRule(
                asList("как тебя зовут", "твое имя", "кто ты", "ты кто"),
                "Я ИИ-помощник в приложении ПАПЕРКА по охране труда."
        ));
        rules.add(new SmallTalkRule(
                asList("кто создал", "кто разработал", "кто сделал приложение", "автор приложения"),
                "Приложение ПАПЕРКА создано разработчиком проекта ПАПЕРКА по охране труда. Контакты доступны в разделе \"Контакты\"."
        ));
        rules.add(new SmallTalkRule(
                asList("что ты умеешь", "чем поможешь", "как ты работаешь"),
                "Я работаю оффлайн: ищу релевантные разделы в локальных документах приложения, показываю цитаты и источники с переходом в документ."
        ));
        rules.add(new SmallTalkRule(
                asList("спасибо", "благодарю", "благодарность"),
                "Пожалуйста! Если хотите, могу уточнить ответ по конкретной теме охраны труда."
        ));
        rules.add(new SmallTalkRule(
                asList("который час", "сколько времени", "дата сегодня"),
                "Я не обращаюсь к интернету и системным часам устройства в чате, но могу помочь с вопросами по охране труда."
        ));
        return rules;
    }

    private List<String> asList(String... values) {
        List<String> out = new ArrayList<>();
        for (String value : values) {
            String normalized = normalizeUserQuery(value);
            if (!normalized.isEmpty()) {
                out.add(normalized);
            }
        }
        return out;
    }

    private void updateModeIndicator(String text, int color) {
        if (textChatMode == null) {
            return;
        }
        textChatMode.setText(text);
        textChatMode.setTextColor(color);
    }

    private String buildOfflineAnswer(String query, List<LocalDocumentRetriever.DocumentChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("Оффлайн-режим: ответ сформирован только по локальным документам приложения.\n");
        sb.append("Релевантные фрагменты по вопросу: ").append(query).append("\n\n");
        int count = Math.min(3, chunks.size());
        for (int i = 0; i < count; i++) {
            LocalDocumentRetriever.DocumentChunk chunk = chunks.get(i);
            String text = chunk.text.replaceAll("\\s+", " ").trim();
            if (text.length() > 240) {
                text = text.substring(0, 240) + "...";
            }
            sb.append(i + 1)
                    .append(") ")
                    .append(chunk.documentName)
                    .append(": ")
                    .append(text)
                    .append("\n");
        }
        return sb.toString();
    }

    private void addUserMessage(String text) {
        messages.add(new AiChatMessage(AiChatMessage.Role.USER, text, null));
        refreshChat();
    }

    private void addAssistantMessage(String text, List<AiSource> sources) {
        messages.add(new AiChatMessage(AiChatMessage.Role.ASSISTANT, text, sources));
        refreshChat();
    }

    private void replaceLoadingMessage(int loadingIndex, String finalText, List<AiSource> sources) {
        if (loadingIndex < 0 || loadingIndex >= messages.size()) {
            addAssistantMessage(finalText, sources);
            return;
        }
        messages.set(loadingIndex, new AiChatMessage(AiChatMessage.Role.ASSISTANT, finalText, sources));
        refreshChat();
    }

    private void refreshChat() {
        adapter.submit(messages);
        recyclerView.post(() -> recyclerView.scrollToPosition(Math.max(0, messages.size() - 1)));
    }

    private void applyBottomInsets(android.view.View inputContainer) {
        if (inputContainer == null) {
            return;
        }
        final int baseBottomPadding = inputContainer.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(inputContainer, (v, insets) -> {
            Insets navInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int extraBottom = Math.max(0, navInsets.bottom);
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), baseBottomPadding + extraBottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(inputContainer);
    }

    private void openSourceDocument(AiSource source) {
        if (source == null || source.getFileKey() == null || source.getFileKey().trim().isEmpty()) {
            Toast.makeText(this, "Источник не содержит ссылки на документ", Toast.LENGTH_SHORT).show();
            return;
        }
        String fileKey = source.getFileKey().trim();
        String actualFileName = resolveAssetDocFile(fileKey);
        if (actualFileName == null) {
            Toast.makeText(this, "Документ не найден в assets: " + fileKey, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, FullView.class);
        intent.putExtra("fileName", actualFileName);
        intent.putExtra("docTitle", source.getDocumentName());
        startActivity(intent);
    }

    private String resolveAssetDocFile(String fileKey) {
        AssetManager assets = getAssets();
        String doc = fileKey + ".doc";
        try (InputStream is = assets.open(doc)) {
            return doc;
        } catch (IOException ignored) {
        }

        String docx = fileKey + ".docx";
        try (InputStream is = assets.open(docx)) {
            return docx;
        } catch (IOException ignored) {
            return null;
        }
    }

    private static class SmallTalkRule {
        final List<String> patterns;
        final String response;

        SmallTalkRule(List<String> patterns, String response) {
            this.patterns = patterns;
            this.response = response;
        }
    }

    @Override
    protected void onDestroy() {
        backgroundExecutor.shutdownNow();
        super.onDestroy();
    }
}
