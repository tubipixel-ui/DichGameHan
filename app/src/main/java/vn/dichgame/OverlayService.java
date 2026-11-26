package vn.dichgame;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class OverlayService extends Service {
    private static final String STOP = "vn.dichgame.STOP";
    private static final String CHANNEL = "dich";
    private static final Pattern HANGUL =
            Pattern.compile("[\\uAC00-\\uD7A3\\u1100-\\u11FF\\u3130-\\u318F]");

    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager windows;
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private TextRecognizer recognizer;
    private Translator translator;
    private TextView button;
    private WindowManager.LayoutParams buttonParams;
    private FrameLayout result;
    private boolean busy;
    private boolean destroyed;
    private int width;
    private int height;

    private final Runnable captureTimeout = () -> {
        closeReader();
        finish("Không chụp được màn hình. Hãy thử lại.");
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (projection != null) return START_NOT_STICKY;

        showNotification();

        Intent data = intent.getParcelableExtra("data");
        if (data == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        projection = getSystemService(MediaProjectionManager.class)
                .getMediaProjection(intent.getIntExtra("code", 0), data);
        if (projection == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        projection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                stopSelf();
            }
        }, main);

        windows = getSystemService(WindowManager.class);
        DisplayMetrics size = screen();
        width = size.widthPixels;
        height = size.heightPixels;
        display = projection.createVirtualDisplay("dich", width, height, size.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, null, null, main);

        recognizer = TextRecognition.getClient(
                new KoreanTextRecognizerOptions.Builder().build());
        translator = Translation.getClient(new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.KOREAN)
                .setTargetLanguage(TranslateLanguage.VIETNAMESE)
                .build());
        addButton();
        return START_NOT_STICKY;
    }

    private void showNotification() {
        getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel(CHANNEL, "Dịch màn hình",
                        NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop = PendingIntent.getService(this, 0,
                new Intent(this, OverlayService.class).setAction(STOP),
                PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle("Dịch Game Hàn đang chạy")
                .setContentText("Chạm để dừng")
                .setContentIntent(stop)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(1, notification);
        }
    }

    private DisplayMetrics screen() {
        DisplayMetrics metrics = new DisplayMetrics();
        windows.getDefaultDisplay().getRealMetrics(metrics);
        return metrics;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private WindowManager.LayoutParams params(int w, int h) {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(w, h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.LEFT;
        p.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        return p;
    }

    private void addButton() {
        button = new TextView(this);
        button.setText("Dịch");
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0xCC00875F);
        button.setBackground(circle);
        buttonParams = params(dp(52), dp(52));
        buttonParams.y = dp(120);

        int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        button.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;
            private int startX, startY;
            private boolean moved;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                float dx = event.getRawX() - downX;
                float dy = event.getRawY() - downY;
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startX = buttonParams.x;
                        startY = buttonParams.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.hypot(dx, dy) > slop) moved = true;
                        if (moved) {
                            DisplayMetrics s = screen();
                            buttonParams.x = clamp(startX + (int) dx, s.widthPixels - dp(52));
                            buttonParams.y = clamp(startY + (int) dy, s.heightPixels - dp(52));
                            windows.updateViewLayout(button, buttonParams);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) capture();
                        return true;
                    default:
                        return true;
                }
            }
        });
        windows.addView(button, buttonParams);
    }

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(value, max));
    }

    private void capture() {
        if (busy) return;
        busy = true;
        removeResult();
        button.setVisibility(View.INVISIBLE);
        // Wait for the button to disappear so it is not in the picture.
        main.postDelayed(this::grabFrame, 200);
    }

    private void grabFrame() {
        if (destroyed) return;
        DisplayMetrics size = screen();
        if (size.widthPixels != width || size.heightPixels != height) {
            width = size.widthPixels;
            height = size.heightPixels;
            display.resize(width, height, size.densityDpi);
        }
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        reader.setOnImageAvailableListener(source -> {
            if (source != reader) return;
            Image image = source.acquireLatestImage();
            if (image == null) return;
            main.removeCallbacks(captureTimeout);
            Bitmap bitmap;
            try {
                bitmap = toBitmap(image);
            } finally {
                image.close();
                closeReader();
            }
            button.setVisibility(View.VISIBLE);
            button.setText("...");
            recognize(bitmap);
        }, main);
        main.postDelayed(captureTimeout, 2000);
        display.setSurface(reader.getSurface());
    }

    private Bitmap toBitmap(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int paddedWidth = plane.getRowStride() / plane.getPixelStride();
        Bitmap padded = Bitmap.createBitmap(paddedWidth, image.getHeight(),
                Bitmap.Config.ARGB_8888);
        padded.copyPixelsFromBuffer(buffer);
        if (paddedWidth == image.getWidth()) return padded;
        Bitmap cropped = Bitmap.createBitmap(padded, 0, 0,
                image.getWidth(), image.getHeight());
        padded.recycle();
        return cropped;
    }

    private void closeReader() {
        if (reader == null) return;
        if (display != null) display.setSurface(null);
        reader.close();
        reader = null;
    }

    private void recognize(Bitmap bitmap) {
        recognizer.process(InputImage.fromBitmap(bitmap, 0)).addOnCompleteListener(task -> {
            bitmap.recycle();
            if (destroyed) return;
            if (!task.isSuccessful()) {
                finish("Lỗi đọc chữ. Hãy thử lại.");
                return;
            }
            List<Text.TextBlock> blocks = new ArrayList<>();
            for (Text.TextBlock block : task.getResult().getTextBlocks()) {
                if (block.getBoundingBox() != null && HANGUL.matcher(block.getText()).find()) {
                    blocks.add(block);
                }
            }
            if (blocks.isEmpty()) {
                finish("Không thấy chữ Hàn trên màn hình.");
                return;
            }
            translate(blocks);
        });
    }

    private void translate(List<Text.TextBlock> blocks) {
        translator.downloadModelIfNeeded(new DownloadConditions.Builder().build())
                .addOnCompleteListener(download -> {
                    if (destroyed) return;
                    if (!download.isSuccessful()) {
                        finish("Chưa tải được gói ngôn ngữ. Hãy kiểm tra mạng.");
                        return;
                    }
                    List<Task<String>> tasks = new ArrayList<>();
                    for (Text.TextBlock block : blocks) {
                        tasks.add(translator.translate(block.getText().replace('\n', ' ')));
                    }
                    Tasks.whenAllComplete(tasks).addOnCompleteListener(all -> {
                        if (destroyed) return;
                        showResult(blocks, tasks);
                        finish(null);
                    });
                });
    }

    private void showResult(List<Text.TextBlock> blocks, List<Task<String>> tasks) {
        result = new FrameLayout(this);
        result.setBackgroundColor(0x22000000);
        result.setOnClickListener(v -> removeResult());
        windows.addView(result, params(width, height));
        FrameLayout target = result;
        target.post(() -> {
            if (target != result) return;
            int[] origin = new int[2];
            target.getLocationOnScreen(origin);
            for (int i = 0; i < blocks.size(); i++) {
                if (!tasks.get(i).isSuccessful()) continue;
                Rect box = blocks.get(i).getBoundingBox();
                int lines = Math.max(1, blocks.get(i).getLines().size());
                float scaled = getResources().getDisplayMetrics().scaledDensity;
                float textSize = Math.max(8, Math.min(13, box.height() / lines / scaled * 0.65f));
                TextView label = new TextView(this);
                label.setText(tasks.get(i).getResult());
                label.setTextColor(Color.WHITE);
                label.setBackgroundColor(0xE6000000);
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
                label.setPadding(dp(3), dp(1), dp(3), dp(1));
                int labelWidth = Math.min(width, Math.max(box.width() + dp(6), dp(120)));
                FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(
                        labelWidth, FrameLayout.LayoutParams.WRAP_CONTENT);
                p.leftMargin = clamp(box.left - origin[0], width - labelWidth);
                p.topMargin = Math.max(0, box.top - origin[1]);
                target.addView(label, p);
            }
        });
        // Keep the button above the translation layer.
        windows.removeView(button);
        windows.addView(button, buttonParams);
    }

    private void removeResult() {
        if (result != null) {
            windows.removeView(result);
            result = null;
        }
    }

    private void finish(String message) {
        busy = false;
        if (destroyed) return;
        button.setVisibility(View.VISIBLE);
        button.setText("Dịch");
        if (message != null) Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        if (windows != null) {
            removeResult();
            if (button != null) windows.removeView(button);
        }
        closeReader();
        if (display != null) display.release();
        if (projection != null) projection.stop();
        if (recognizer != null) recognizer.close();
        if (translator != null) translator.close();
        super.onDestroy();
    }
}
