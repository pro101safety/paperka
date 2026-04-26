package by.instruction.papera;

import android.Manifest;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import com.google.android.material.appbar.MaterialToolbar;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class NoiseMeterActivity extends AppCompatActivity {
    private static final int SAMPLE_RATE = 44100;
    private static final int ALERT_DB = 80;
    private static final double DB_OFFSET = 90.0;

    private final DecimalFormat dbFormat = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US));
    private ActivityResultLauncher<String> audioPermissionLauncher;
    private TextView textNoiseValue;
    private TextView textNoiseUnit;
    private TextView textNoiseStatus;
    private TextView textNoiseAlert;
    private ProgressBar progressNoiseLevel;
    private AudioRecord audioRecord;
    private Thread recorderThread;
    private volatile boolean isRecording;
    private int warningColor;
    private int normalColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_noise_meter);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.noise_meter_title));
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        textNoiseValue = findViewById(R.id.textNoiseValue);
        textNoiseUnit = findViewById(R.id.textNoiseUnit);
        textNoiseStatus = findViewById(R.id.textNoiseStatus);
        textNoiseAlert = findViewById(R.id.textNoiseAlert);
        progressNoiseLevel = findViewById(R.id.progressNoiseLevel);
        normalColor = ContextCompat.getColor(this, R.color.purple_200);
        warningColor = 0xFFFF3B30;

        audioPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (Boolean.TRUE.equals(isGranted)) {
                startNoiseMonitoring();
            } else {
                textNoiseValue.setText("--");
                textNoiseStatus.setText(R.string.noise_meter_permission_required);
                Toast.makeText(this, R.string.noise_meter_permission_denied, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startNoiseMonitoring();
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopNoiseMonitoring();
    }

    private void startNoiseMonitoring() {
        if (isRecording) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            textNoiseValue.setText("--");
            textNoiseStatus.setText(R.string.noise_meter_permission_required);
            return;
        }

        int minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        if (minBufferSize <= 0) {
            textNoiseValue.setText("--");
            textNoiseStatus.setText(R.string.noise_meter_permission_required);
            return;
        }

        int bufferSize = Math.max(minBufferSize, 4096);
        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
        );

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release();
            audioRecord = null;
            textNoiseValue.setText("--");
            textNoiseStatus.setText(R.string.noise_meter_permission_required);
            return;
        }

        try {
            audioRecord.startRecording();
        } catch (IllegalStateException | SecurityException e) {
            audioRecord.release();
            audioRecord = null;
            textNoiseValue.setText("--");
            textNoiseStatus.setText(R.string.noise_meter_permission_required);
            return;
        }
        isRecording = true;
        textNoiseStatus.setText("");

        recorderThread = new Thread(() -> readAudioLoop(bufferSize), "NoiseMeterRecorder");
        recorderThread.start();
    }

    private void readAudioLoop(int bufferSize) {
        short[] buffer = new short[bufferSize];
        while (isRecording && audioRecord != null) {
            int read = audioRecord.read(buffer, 0, buffer.length);
            if (read <= 0) {
                continue;
            }

            double squareSum = 0.0;
            for (int i = 0; i < read; i++) {
                double normalized = buffer[i] / 32768.0;
                squareSum += normalized * normalized;
            }
            double rms = Math.sqrt(squareSum / read);
            double db = rms > 0.0 ? (20.0 * Math.log10(rms) + DB_OFFSET) : 0.0;
            double displayDb = Math.max(0.0, Math.min(120.0, db));

            runOnUiThread(() -> updateNoiseDisplay(displayDb));
        }
    }

    private void updateNoiseDisplay(double db) {
        textNoiseValue.setText(dbFormat.format(db));
        int progress = (int) Math.round(Math.max(0.0, Math.min(120.0, db)));
        progressNoiseLevel.setProgress(progress);
        boolean isDanger = db > ALERT_DB;
        int color = isDanger ? warningColor : normalColor;
        textNoiseValue.setTextColor(color);
        textNoiseUnit.setTextColor(color);
        progressNoiseLevel.setProgressTintList(ColorStateList.valueOf(color));
        textNoiseAlert.setVisibility(isDanger ? View.VISIBLE : View.GONE);
    }

    private void stopNoiseMonitoring() {
        isRecording = false;
        if (audioRecord != null) {
            try {
                audioRecord.stop();
            } catch (IllegalStateException ignored) {
                // Игнорируем гонки остановки при сворачивании activity.
            }
        }
        if (recorderThread != null) {
            try {
                recorderThread.join(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            recorderThread = null;
        }
        if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
        }
    }
}
