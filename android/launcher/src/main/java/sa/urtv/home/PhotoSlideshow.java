package sa.urtv.home;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Offline APK assets; never uses the wallpaper layers or takes D-pad focus. API 24+. */
final class PhotoSlideshow {
    static final long INTERVAL_MS = 10_000;
    private final Activity activity;
    private final FrameLayout frame;
    private final ImageView image;
    private final TextView status;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final List<String> photos = new ArrayList<>();
    private boolean active, destroyed, shown;
    private int index, generation;
    private final Runnable advance = this::loadNext;

    PhotoSlideshow(Activity activity) {
        this.activity = activity;
        frame = new FrameLayout(activity);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(0xDD071810);
        bg.setCornerRadius(dp(12)); bg.setStroke(dp(1), 0xAAD6AF6B);
        frame.setBackground(bg); frame.setPadding(dp(4), dp(4), dp(4), dp(4));
        frame.setFocusable(false); frame.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        status = new TextView(activity); status.setText("لا توجد صور"); status.setTextColor(Color.WHITE);
        status.setTextSize(12); status.setGravity(Gravity.CENTER);
        frame.addView(status, new FrameLayout.LayoutParams(-1, -1));
        image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER); // Entire image, preserving original aspect ratio.
        image.setContentDescription("عرض الصور"); image.setFocusable(false);
        frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
        try {
            String[] names = activity.getAssets().list("slideshow");
            if (names != null) for (String name : names) {
                String lower = name.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp"))
                    photos.add(name);
            }
            Collections.sort(photos);
        } catch (java.io.IOException ignored) { }
    }
    View view() { return frame; }
    void resume() {
        if (destroyed || active) return;
        active = true;
        if (shown) handler.postDelayed(advance, INTERVAL_MS); else loadNext();
    }
    void pause() { active = false; generation++; handler.removeCallbacks(advance); }
    void destroy() { pause(); destroyed = true; decoder.shutdownNow(); image.setImageDrawable(null); }

    private void loadNext() {
        if (!active || destroyed || photos.isEmpty()) return;
        final String name = photos.get(index++ % photos.size());
        if (index >= photos.size()) index = 0;
        final int ticket = ++generation;
        decoder.execute(() -> {
            Bitmap bitmap = decode(name);
            handler.post(() -> {
                if (!active || destroyed || ticket != generation) { if (bitmap != null) bitmap.recycle(); return; }
                if (bitmap != null) {
                    image.setImageBitmap(bitmap); status.setVisibility(View.GONE); shown = true;
                } else if (!shown) status.setText("تعذر عرض الصورة");
                handler.postDelayed(advance, INTERVAL_MS);
            });
        });
    }
    private Bitmap decode(String name) {
        String path = "slideshow/" + name;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
            try (InputStream in = activity.getAssets().open(path)) { BitmapFactory.decodeStream(in, null, options); }
            if (options.outWidth < 1 || options.outHeight < 1) return null;
            options.inSampleSize = 1;
            while (options.outWidth / options.inSampleSize > 512 || options.outHeight / options.inSampleSize > 512)
                options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            Bitmap bitmap;
            try (InputStream in = activity.getAssets().open(path)) { bitmap = BitmapFactory.decodeStream(in, null, options); }
            if (bitmap == null) return null;
            try (InputStream in = activity.getAssets().open(path)) {
                int orientation = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
                Matrix matrix = new Matrix();
                switch (orientation) {
                    case 2: matrix.setScale(-1, 1); break;
                    case 3: matrix.setRotate(180); break;
                    case 4: matrix.setScale(1, -1); break;
                    case 5: matrix.setRotate(90); matrix.postScale(-1, 1); break;
                    case 6: matrix.setRotate(90); break;
                    case 7: matrix.setRotate(-90); matrix.postScale(-1, 1); break;
                    case 8: matrix.setRotate(-90); break;
                    default: break;
                }
                if (!matrix.isIdentity()) {
                    Bitmap corrected = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                    if (corrected != bitmap) { bitmap.recycle(); bitmap = corrected; }
                }
            } catch (java.io.IOException ignored) { }
            return bitmap;
        } catch (java.io.IOException | RuntimeException e) { return null; }
    }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
}
