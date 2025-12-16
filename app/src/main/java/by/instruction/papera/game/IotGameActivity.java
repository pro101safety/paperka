package by.instruction.papera.game;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

import by.instruction.papera.R;
import by.instruction.papera.data.RecordsStore;

public class IotGameActivity extends AppCompatActivity implements IotGameView.GameListener {

    private IotGameView gameView;
    private TextView timerText;
    private TextView helmetInfo;
    private View resultPanel;
    private View startPanel;
    private TextView resultTime;
    private TextView recordsList;
    private Button restartButton;
    private Button exitButton;
    private Button startButton;
    private TextView warningBubble;
    private RecordsStore recordsStore;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_iot_game);

        gameView = findViewById(R.id.iotGameView);
        timerText = findViewById(R.id.gameTimer);
        helmetInfo = findViewById(R.id.helmetInfo);
        resultPanel = findViewById(R.id.resultPanel);
        startPanel = findViewById(R.id.startPanel);
        resultTime = findViewById(R.id.resultTime);
        recordsList = findViewById(R.id.recordsList);
        restartButton = findViewById(R.id.restartButton);
        exitButton = findViewById(R.id.exitButton);
        startButton = findViewById(R.id.startButton);
        warningBubble = findViewById(R.id.warningBubble);
        recordsStore = new RecordsStore(this);

        gameView.setListener(this);

        restartButton.setOnClickListener(v -> restartGame());
        exitButton.setOnClickListener(v -> {
            finish();
        });
        startButton.setOnClickListener(v -> startGame());

        showStartPanel();
    }

    private void showStartPanel() {
        timerText.setText("00:00");
        resultPanel.setVisibility(View.GONE);
        startPanel.setVisibility(View.VISIBLE);
        timerText.setVisibility(View.GONE);
        helmetInfo.setVisibility(View.GONE);
    }

    private void startGame() {
        resultPanel.setVisibility(View.GONE);
        startPanel.setVisibility(View.GONE);
        warningBubble.setVisibility(View.VISIBLE);
        timerText.setText("00:00");
        helmetInfo.setText(formatShield(0));
        timerText.setVisibility(View.VISIBLE);
        helmetInfo.setVisibility(View.VISIBLE);
        gameView.startNewSession();
    }

    private void restartGame() {
        startGame();
    }

    @Override
    public void onTick(long elapsedMs) {
        String timeText = formatTime(elapsedMs);
        timerText.post(() -> timerText.setText(timeText));
    }

    @Override
    public void onShieldChanged(int hits) {
        runOnUiThread(() -> helmetInfo.setText(formatShield(hits)));
    }

    @Override
    public void onGameOver(long durationMs, boolean endedByPlate) {
        runOnUiThread(() -> {
            resultPanel.setVisibility(View.VISIBLE);
            timerText.setText("00:00");
            resultTime.setText("Время: " + formatTime(durationMs));
            List<Long> records = recordsStore.addRecord(durationMs);
            recordsList.setText(formatRecords(records));
            helmetInfo.setText(formatShield(0));
        });
    }

    private String formatTime(long ms) {
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private String formatRecords(List<Long> records) {
        if (records == null || records.isEmpty()) {
            return "Рекорды: —";
        }
        StringBuilder sb = new StringBuilder("Рекорды:\n");
        for (int i = 0; i < records.size(); i++) {
            sb.append(i + 1)
                    .append(") ")
                    .append(formatTime(records.get(i)))
                    .append("\n");
        }
        return sb.toString().trim();
    }

    private String formatShield(int hits) {
        if (hits >= 2) return "Каска: +2 жизни";
        if (hits == 1) return "Каска: +1 жизнь";
        return "Каска: скорее лови!";
    }

    @Override
    protected void onResume() {
        super.onResume();
        gameView.resumeGame();
    }

    @Override
    protected void onPause() {
        super.onPause();
        gameView.pauseGame();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        gameView.stopGame();
    }
}

