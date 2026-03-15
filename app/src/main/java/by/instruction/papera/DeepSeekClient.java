package by.instruction.papera;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class DeepSeekClient {
    private static final String API_URL = "https://api.deepseek.com/chat/completions";
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 25000;
    private static final int MAX_CONTEXT_CHARS = 6000;

    public DeepSeekResponse ask(String apiKey, String question, List<LocalDocumentRetriever.DocumentChunk> chunks) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = URI.create(API_URL).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);

            JSONObject payload = new JSONObject();
            payload.put("model", "deepseek-chat");
            payload.put("temperature", 0.2);
            payload.put("max_tokens", 450);

            JSONArray messages = new JSONArray();
            messages.put(new JSONObject()
                    .put("role", "system")
                    .put("content", "Ты помощник по охране труда. Отвечай только на русском языке. Используй только переданные фрагменты документов. Если данных недостаточно, прямо укажи это."));
            messages.put(new JSONObject()
                    .put("role", "user")
                    .put("content", buildGroundedPrompt(question, chunks)));
            payload.put("messages", messages);

            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(body);
            }

            int code = connection.getResponseCode();
            String responseBody = readStream(code >= 200 && code < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream());

            if (code < 200 || code >= 300) {
                throw new RuntimeException("DeepSeek API error " + code + ": " + responseBody);
            }

            JSONObject json = new JSONObject(responseBody);
            JSONArray choices = json.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                throw new RuntimeException("Пустой ответ от DeepSeek");
            }
            JSONObject message = choices.getJSONObject(0).getJSONObject("message");
            String text = message.optString("content", "").trim();
            if (text.isEmpty()) {
                throw new RuntimeException("DeepSeek вернул пустой ответ");
            }
            return new DeepSeekResponse(text);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String buildGroundedPrompt(String question, List<LocalDocumentRetriever.DocumentChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("Вопрос пользователя:\n").append(question).append("\n\n");
        sb.append("Контекст из локальных документов приложения:\n");
        int contextChars = 0;
        for (int i = 0; i < chunks.size(); i++) {
            LocalDocumentRetriever.DocumentChunk chunk = chunks.get(i);
            String text = chunk.text.replaceAll("\\s+", " ").trim();
            if (text.isEmpty()) {
                continue;
            }
            String prefix = "[" + (i + 1) + "] " + chunk.documentName + ": ";
            int remain = MAX_CONTEXT_CHARS - contextChars;
            if (remain <= 0) {
                break;
            }
            String limited = text.length() > remain ? text.substring(0, remain) : text;
            sb.append(prefix).append(limited).append("\n");
            contextChars += limited.length();
        }
        sb.append("\nТребования к ответу:\n");
        sb.append("1) Отвечай строго по контексту.\n");
        sb.append("2) Если в контексте нет данных, скажи, что данных недостаточно.\n");
        sb.append("3) В конце добавь раздел 'Использованные источники' и перечисли номера источников.\n");
        return sb.toString();
    }

    private String readStream(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    public static class DeepSeekResponse {
        public final String text;

        public DeepSeekResponse(String text) {
            this.text = text;
        }
    }
}
