package by.instruction.papera;

import android.content.Intent;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class Privacy extends AppCompatActivity {

    private static final String PLAY_URL = "https://play.google.com/store/apps/details?id=by.instruction.papera&hl=ru";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy);

        TextView versionValue = findViewById(R.id.text_version_value);
        if (versionValue != null) {
            String version = getString(R.string.about_version_value, BuildConfig.VERSION_NAME);
            versionValue.setText(version);
        }

        TextView linkView = findViewById(R.id.text_play_link);
        if (linkView != null) {
            linkView.setPaintFlags(linkView.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            linkView.setOnClickListener(v -> openPlayPage());
        }
    }

    private void openPlayPage() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_URL));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
