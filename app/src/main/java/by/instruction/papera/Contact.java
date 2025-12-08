package by.instruction.papera;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class Contact extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact);

        TextView azbukaLink = findViewById(R.id.azbuka_link);
        if (azbukaLink != null) {
            azbukaLink.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String url = "https://play.google.com/store/apps/details?id=com.instruction.paperka20&hl=ru";
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(url));
                    // Пытаемся открыть в Google Play Store
                    intent.setPackage("com.android.vending");
                    try {
                        startActivity(intent);
                    } catch (android.content.ActivityNotFoundException e) {
                        // Если Google Play не установлен, открываем в браузере
                        intent.setPackage(null);
                        startActivity(intent);
                    }
                }
            });
        }
    }
}