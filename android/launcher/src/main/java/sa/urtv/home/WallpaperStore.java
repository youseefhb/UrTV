package sa.urtv.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import org.json.JSONArray;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Import into private storage: survives reboot and providers that do not persist URI grants. */
final class WallpaperStore {
    static String[] files(Context c) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(Dock.prefs(c).getString("userWallpapers", "[]"));
            for (int i = 0; i < a.length(); i++) {
                String name = a.getString(i);
                if (name.matches("[a-f0-9-]+\\.jpg") && new File(c.getFilesDir(), "user-wallpapers/" + name).isFile()) out.add(name);
            }
        } catch (Exception ignored) { }
        return out.toArray(new String[0]);
    }

    static String importImage(Context c, Uri uri) throws Exception {
        File dir = new File(c.getFilesDir(), "user-wallpapers");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("storage");
        File temp = File.createTempFile("import-", ".tmp", dir);
        Bitmap bitmap = null;
        File dest = new File(dir, UUID.randomUUID() + ".jpg");
        try {
            try (InputStream in = c.getContentResolver().openInputStream(uri); FileOutputStream out = new FileOutputStream(temp)) {
                if (in == null) throw new java.io.IOException("unreadable");
                byte[] buffer = new byte[8192]; int n, total = 0;
                while ((n = in.read(buffer)) != -1) {
                    total += n;
                    if (total > 20 * 1024 * 1024) throw new java.io.IOException("image too large");
                    out.write(buffer, 0, n);
                }
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(temp.getPath(), options);
            if (options.outWidth < 1 || options.outHeight < 1) throw new java.io.IOException("not an image");
            options.inSampleSize = 1;
            while (options.outWidth / options.inSampleSize > 1920 || options.outHeight / options.inSampleSize > 1080)
                options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            bitmap = BitmapFactory.decodeFile(temp.getPath(), options);
            if (bitmap == null) throw new java.io.IOException("decode");
            // Camera photos often encode their orientation in EXIF rather than in the pixels.
            try {
                android.media.ExifInterface exif = new android.media.ExifInterface(temp.getPath());
                int orientation = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
                android.graphics.Matrix matrix = new android.graphics.Matrix();
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
            try (FileOutputStream out = new FileOutputStream(dest)) {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)) throw new java.io.IOException("encode");
            }
            return dest.getName();
        } catch (Exception e) { dest.delete(); throw e; }
        finally { temp.delete(); if (bitmap != null) bitmap.recycle(); }
    }

    static void save(Context c, List<String> names) {
        Dock.prefs(c).edit().putString("userWallpapers", new JSONArray(names).toString())
                .putInt("wallpaper", -1).putLong("wallpaperRevision", System.currentTimeMillis()).commit();
        File dir = new File(c.getFilesDir(), "user-wallpapers");
        File[] old = dir.listFiles();
        if (old != null) for (File f : old) if (f.getName().endsWith(".jpg") && !names.contains(f.getName())) f.delete();
        c.sendBroadcast(new android.content.Intent(Dock.CHANGED).setPackage(c.getPackageName()));
    }
}
