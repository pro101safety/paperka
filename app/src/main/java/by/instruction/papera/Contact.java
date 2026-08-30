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
        EdgeToEdgeHelper.enable(this);
        setContentView(R.layout.activity_contact);
        View root = ((android.view.ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        EdgeToEdgeHelper.applySystemBarsPadding(root);

        bindPlayStoreLink(R.id.azbuka_link,
                "https://play.google.com/store/apps/details?id=com.instruction.paperka20&hl=ru");
        bindPlayStoreLink(R.id.pro101_link,
                "https://play.google.com/store/apps/details?id=by.instruction.planer");
        bindWebLink(R.id.pc_solutions_link,
                "https://instruction-ot.by/programmnoe-obespechenie/");
    }

    private void bindPlayStoreLink(int viewId, String url) {
        TextView link = findViewById(viewId);
        if (link == null) {
            return;
        }
        link.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.setPackage("com.android.vending");
            try {
                startActivity(intent);
            } catch (android.content.ActivityNotFoundException e) {
                intent.setPackage(null);
                startActivity(intent);
            }
        });
    }

    private void bindWebLink(int viewId, String url) {
        TextView link = findViewById(viewId);
        if (link == null) {
            return;
        }
        link.setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))));
    }
}