package vn.dichgame;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

public class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE = 1;
    private TextView status;
    private Translator translator;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);

        status = new TextView(this);
        status.setTextSize(16);
        status.setText("1. Tải gói ngôn ngữ (chỉ cần một lần, nên dùng Wi-Fi).\n"
                + "2. Bấm Bắt đầu và cấp quyền.\n"
                + "3. Mở game, chạm nút tròn \"Dịch\". Chạm vào màn hình để đóng bản dịch.");
        box.addView(status);

        addButton(box, "Tải gói Hàn - Việt", this::downloadModel);
        addButton(box, "Bắt đầu", this::start);
        addButton(box, "Dừng", () -> stopService(new Intent(this, OverlayService.class)));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        setContentView(scroll);

        translator = Translation.getClient(new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.KOREAN)
                .setTargetLanguage(TranslateLanguage.VIETNAMESE)
                .build());
    }

    private void addButton(LinearLayout box, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(v -> action.run());
        box.addView(button);
    }

    private void downloadModel() {
        status.setText("Đang tải gói ngôn ngữ...");
        translator.downloadModelIfNeeded(new DownloadConditions.Builder().build())
                .addOnSuccessListener(x -> status.setText("Đã tải xong. Bấm Bắt đầu."))
                .addOnFailureListener(e -> status.setText("Tải lỗi: " + e.getMessage()));
    }

    private void start() {
        if (!Settings.canDrawOverlays(this)) {
            status.setText("Hãy bật quyền hiển thị trên ứng dụng khác, quay lại rồi bấm Bắt đầu.");
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQUEST_CAPTURE) return;
        if (result != RESULT_OK || data == null) {
            status.setText("Bạn chưa cho phép chụp màn hình.");
            return;
        }
        Intent service = new Intent(this, OverlayService.class)
                .putExtra("code", result)
                .putExtra("data", data);
        startForegroundService(service);
        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        translator.close();
        super.onDestroy();
    }
}
