package by.instruction.papera;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class LevelActivity extends AppCompatActivity implements SensorEventListener {
    private final DecimalFormat angleFormat = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US));
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private TextView textLevelValue;
    private TextView textLevelAxis;
    private TextView textLevelStatus;
    private LevelBubbleView viewLevelBubble;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeHelper.enable(this);
        setContentView(R.layout.activity_level);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        EdgeToEdgeHelper.applyToolbarScreenInsets(toolbar, findViewById(android.R.id.content));
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.level_title));
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        textLevelValue = findViewById(R.id.textLevelValue);
        textLevelAxis = findViewById(R.id.textLevelAxis);
        textLevelStatus = findViewById(R.id.textLevelStatus);
        viewLevelBubble = findViewById(R.id.viewLevelBubble);
        textLevelAxis.setText(getString(R.string.level_axis, "0.0", "0.0"));

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        if (accelerometer == null) {
            textLevelValue.setText("--");
            textLevelStatus.setText(R.string.level_missing_sensor);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER || event.values.length < 3) {
            return;
        }

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        double gravity = Math.sqrt(x * x + y * y + z * z);
        if (gravity < 0.01) {
            return;
        }

        double pitch = Math.toDegrees(Math.atan2(-x, Math.sqrt(y * y + z * z)));
        double roll = Math.toDegrees(Math.atan2(y, z));
        double tilt = Math.sqrt(pitch * pitch + roll * roll);

        String tiltText = angleFormat.format(tilt);
        String xText = angleFormat.format(pitch);
        String yText = angleFormat.format(roll);

        textLevelValue.setText(tiltText + "°");
        textLevelAxis.setText(getString(R.string.level_axis, xText, yText));
        textLevelStatus.setText("");
        if (viewLevelBubble != null) {
            viewLevelBubble.setTiltAngles((float) pitch, (float) roll);
        }
    }

    @Override
    public void onAccuracyChanged(@NonNull Sensor sensor, int accuracy) {
        // Для уровня достаточно текущих значений наклона.
    }
}
