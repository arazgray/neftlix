package com.arazgray.neftlix;

import android.graphics.Bitmap;

import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class BackdropBlur extends BitmapTransformation {
    private static final String ID = "BackdropBlur";
    private final int radius;

    BackdropBlur(int radius) {
        this.radius = Math.max(1, radius);
    }

    @Override
    protected Bitmap transform(BitmapPool pool, Bitmap source, int outWidth, int outHeight) {
        int width = Math.max(1, source.getWidth() / 4);
        int height = Math.max(1, source.getHeight() / 4);
        Bitmap small = Bitmap.createScaledBitmap(source, width, height, true);
        Bitmap output = pool.get(width, height, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(output);
        canvas.drawBitmap(small, 0, 0, null);
        pool.put(small);
        boxBlur(output);
        boxBlur(output);
        return output;
    }

    private void boxBlur(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        int[] blurred = new int[w * h];
        int step = 3;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = 0;
                int g = 0;
                int b = 0;
                int count = 0;
                for (int ky = -radius; ky <= radius; ky += step) {
                    int yy = Math.min(h - 1, Math.max(0, y + ky));
                    for (int kx = -radius; kx <= radius; kx += step) {
                        int xx = Math.min(w - 1, Math.max(0, x + kx));
                        int pixel = pixels[yy * w + xx];
                        r += (pixel >> 16) & 0xFF;
                        g += (pixel >> 8) & 0xFF;
                        b += pixel & 0xFF;
                        count++;
                    }
                }
                blurred[y * w + x] = 0xFF000000 | ((r / count) << 16) | ((g / count) << 8) | (b / count);
            }
        }
        bitmap.setPixels(blurred, 0, w, 0, 0, w, h);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BackdropBlur && ((BackdropBlur) o).radius == radius;
    }

    @Override
    public int hashCode() {
        return ID.hashCode() * 31 + radius;
    }

    @Override
    public void updateDiskCacheKey(MessageDigest messageDigest) {
        messageDigest.update((ID + radius).getBytes(StandardCharsets.UTF_8));
    }
}
