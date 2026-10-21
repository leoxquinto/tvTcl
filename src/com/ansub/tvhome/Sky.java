package com.ansub.tvhome;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import java.util.Random;

// A day sky and a night sky, with a grey rainy version of each, drawn once into a bitmap: no blur or
// animation at runtime.
final class Sky {
    private Sky() {}

    static Bitmap render(int w, int h, boolean night, boolean rainy) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setDither(true);

        int[] colors = rainy ? (night ? new int[] {0xFF020307, 0xFF080D18, 0xFF101826}
                        : new int[] {0xFF3B4E60, 0xFF566D80, 0xFF7B909F})
                : night ? new int[] {0xFF03050B, 0xFF0A1128, 0xFF18254A}
                : new int[] {0xFF1659B8, 0xFF2F7FDC, 0xFF63A8EE};
        p.setShader(new LinearGradient(0, 0, 0, h, colors, new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        if (rainy) overcast(c, p, w, h, night);
        else {
            if (night) stars(c, p, w, h); else clouds(c, p, w, h);
            celestial(c, p, w, h, night);
        }

        // A darker foot, so the labels on the dock stay easy to read.
        p.setShader(new LinearGradient(0, h * 0.6f, 0, h, 0x00000000, 0x66000000, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.6f, w, h, p);
        p.setShader(null);
        return bmp;
    }

    // Rain: thick grey clouds all over the top, darker at the very top, like a storm front.
    private static void overcast(Canvas c, Paint p, int w, int h, boolean night) {
        float u = w / 1280f;
        p.setMaskFilter(new BlurMaskFilter(26 * u, BlurMaskFilter.Blur.NORMAL));
        float[][] spots = {{0.08f, 0.10f, 1.5f}, {0.30f, 0.20f, 1.7f}, {0.55f, 0.08f, 1.6f}, {0.80f, 0.18f, 1.8f},
                {0.98f, 0.08f, 1.4f}, {0.18f, 0.42f, 1.4f}, {0.46f, 0.38f, 1.5f}, {0.72f, 0.45f, 1.4f}}; // x, y, size
        for (float[] k : spots) {
            float x = w * k[0], y = h * k[1], s = k[2] * u;
            p.setColor(night ? 0x3D3A4C62 : 0x66E4EBF0);
            c.drawOval(new RectF(x - 150 * s, y - 28 * s, x + 150 * s, y + 28 * s), p);
            c.drawOval(new RectF(x - 90 * s, y - 52 * s, x + 60 * s, y + 8 * s), p);
            c.drawOval(new RectF(x - 10 * s, y - 40 * s, x + 110 * s, y + 10 * s), p);
        }
        p.setMaskFilter(null);
        p.setShader(new LinearGradient(0, 0, 0, h * 0.4f, night ? 0x66000000 : 0x55101820, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h * 0.4f, p);
        p.setShader(null);
    }

    private static void stars(Canvas c, Paint p, int w, int h) {
        Random r = new Random(11);
        float u = w / 1280f;
        for (int i = 0; i < 170; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h * 0.78f;
            float rad = (0.7f + r.nextFloat() * 1.5f) * u;
            int a = 80 + r.nextInt(176);
            p.setColor((a << 24) | 0x00FFFFFF);
            c.drawCircle(x, y, rad, p);
        }
        for (int i = 0; i < 12; i++) { // a few brighter ones, with a soft glow
            float x = r.nextFloat() * w, y = r.nextFloat() * h * 0.6f;
            p.setShader(new RadialGradient(x, y, 9 * u, 0x99FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, 9 * u, p);
            p.setShader(null);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(x, y, 1.8f * u, p);
        }
    }

    private static void clouds(Canvas c, Paint p, int w, int h) {
        float u = w / 1280f;
        p.setMaskFilter(new BlurMaskFilter(14 * u, BlurMaskFilter.Blur.NORMAL));
        float[][] spots = {{0.10f, 0.30f, 1.0f}, {0.38f, 0.17f, 0.8f}, {0.62f, 0.40f, 1.2f},
                {0.90f, 0.52f, 0.9f}, {0.22f, 0.58f, 1.1f}, {0.74f, 0.74f, 1.0f}}; // x, y, size
        for (float[] k : spots) {
            float x = w * k[0], y = h * k[1], s = k[2] * u;
            p.setColor(0x55FFFFFF);
            c.drawOval(new RectF(x - 120 * s, y - 22 * s, x + 120 * s, y + 22 * s), p);
            c.drawOval(new RectF(x - 70 * s, y - 44 * s, x + 40 * s, y + 6 * s), p);
            c.drawOval(new RectF(x - 10 * s, y - 34 * s, x + 90 * s, y + 8 * s), p);
        }
        p.setMaskFilter(null);
    }

    // A soft glow: the alpha falls off smoothly (the higher the power, the tighter) and reaches the
    // edge flat, so there is no visible rim.
    private static RadialGradient glow(float cx, float cy, float r, int rgb, int peak, float power) {
        int n = 12;
        int[] cols = new int[n + 1];
        float[] pos = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n, f = (float) Math.pow(1 - t, power);
            cols[i] = (Math.round(peak * f) << 24) | (rgb & 0x00FFFFFF);
            pos[i] = t;
        }
        return new RadialGradient(cx, cy, r, cols, pos, Shader.TileMode.CLAMP);
    }

    // The moon at night, top middle (there is no sun by day). Low enough that the glow isn't cut by the
    // top of the screen, and clear of the clock and the weather pill.
    private static void celestial(Canvas c, Paint p, int w, int h, boolean night) {
        float cx = w * 0.55f, cy = h * 0.26f, u = w / 1280f;
        if (night) {
            p.setShader(glow(cx, cy, 190 * u, 0xB8C8FF, 0x70, 3f));
            c.drawCircle(cx, cy, 190 * u, p);
            p.setShader(null);
            Path moon = new Path();
            moon.addCircle(cx, cy, 42 * u, Path.Direction.CW);
            Path cut = new Path();
            cut.addCircle(cx + 20 * u, cy - 12 * u, 38 * u, Path.Direction.CW);
            moon.op(cut, Path.Op.DIFFERENCE);
            p.setColor(0xFFF4F6FF);
            c.drawPath(moon, p);
        }
    }
}
