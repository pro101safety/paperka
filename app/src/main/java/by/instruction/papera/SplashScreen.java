package by.instruction.papera;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

public class SplashScreen extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 1300L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeHelper.enable(this);
        setContentView(R.layout.activity_splash_screen);
        android.view.View root = ((android.view.ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        EdgeToEdgeHelper.applySystemBarsPadding(root);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(SplashScreen.this, MainActivity.class));
            finish();
        }, SPLASH_DELAY_MS);
    }
}
