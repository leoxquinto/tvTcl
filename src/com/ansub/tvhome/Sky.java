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

// A day sky and a night sky, each with looks for rain, overcast, fog and cold weather, drawn once into
// a bitmap: no blur or animation at runtime.
final class Sky {
    private Sky() {}

    // Looks, combined with |. RAIN wins over FOG, FOG over CLOUDY, CLOUDY over PARTLY (partly cloudy),
    // PARTLY over a plain sky; COLD also tints the plain, partly cloudy and cloudy skies.
    static final int RAIN = 1, CLOUDY = 2, FOG = 4, COLD = 8, PARTLY = 16;

    private static final int[] NORMAL_DAY = {0xFF1659B8, 0xFF2F7FDC, 0xFF63A8EE};
    private static final int[] NORMAL_NIGHT = {0xFF03050B, 0xFF0A1128, 0xFF18254A};
    private static final int[] COLD_DAY = {0xFF2B5F94, 0xFF4F86B8, 0xFF86B4D6};
    private static final int[] COLD_NIGHT = {0xFF020610, 0xFF071A2E, 0xFF0F3552};
    private static final int[] PARTLY_DAY = {0xFF28508A, 0xFF4A74A8, 0xFF7C9DBF};
    private static final int[] PARTLY_COLD_DAY = {0xFF254B7E, 0xFF426C9C, 0xFF7392B2};
    private static final int[] CLOUDY_DAY = {0xFF4F6272, 0xFF6C8092, 0xFF93A4B2};
    private static final int[] CLOUDY_COLD_DAY = {0xFF435768, 0xFF5F7385, 0xFF8497A8};
    private static final int[] FOG_DAY = {0xFF66788A, 0xFF8394A3, 0xFFA5B3BF};
    private static final int[] RAINY_DAY = {0xFF3B4E60, 0xFF566D80, 0xFF7B909F};
    private static final int[] RAINY_NIGHT = {0xFF020307, 0xFF080D18, 0xFF101826};

    private static int[] palette(boolean night, int look) {
        boolean cold = (look & COLD) != 0;
        if ((look & RAIN) != 0) return night ? RAINY_NIGHT : RAINY_DAY;
        if ((look & FOG) != 0) return FOG_DAY;
        if ((look & CLOUDY) != 0) return cold ? CLOUDY_COLD_DAY : CLOUDY_DAY;
        if ((look & PARTLY) != 0) return cold ? PARTLY_COLD_DAY : PARTLY_DAY;
        if (cold) return night ? COLD_NIGHT : COLD_DAY;
        return night ? NORMAL_NIGHT : NORMAL_DAY;
    }

    static Bitmap render(int w, int h, boolean night, int look) {
        // At night the stars and the moon are the point: no clouds, partly cloudy or fog then. Rain still
        // gets its dark sky, and the cold only tints it.
        if (night) look &= ~(CLOUDY | FOG | PARTLY);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setDither(true);

        int[] colors = palette(night, look);
        p.setShader(new LinearGradient(0, 0, 0, h, colors, new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        boolean rain = (look & RAIN) != 0;
        if (rain || (look & (CLOUDY | FOG)) != 0) overcast(c, p, w, h, night, rain);
        else {
            if (night) stars(c, p, w, h); else clouds(c, p, w, h, false, (look & PARTLY) != 0);
            celestial(c, p, w, h, night);
        }
        // Mist: all over for fog, only near the ground for cold weather.
        if ((look & FOG) != 0) haze(c, p, w, h, night, night ? 0x20 : 0x30, night ? 0x44 : 0x66);
        else if ((look & COLD) != 0 && !rain && !night) haze(c, p, w, h, night, 0x00, 0x38);

        // A darker foot, so the labels on the dock stay easy to read.
        p.setShader(new LinearGradient(0, h * 0.6f, 0, h, 0x00000000, 0x66000000, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.6f, w, h, p);
        p.setShader(null);
        return bmp;
    }

    // Mist: a pale veil that gets thicker toward the bottom.
    private static void haze(Canvas c, Paint p, int w, int h, boolean night, int aTop, int aBottom) {
        int rgb = night ? 0x9FB4C8 : 0xDCE6EE;
        p.setShader(new LinearGradient(0, 0, 0, h, (aTop << 24) | rgb, (aBottom << 24) | rgb, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
    }

    // Thick grey clouds all over the top. With rain they are darker, and the very top gets darker
    // still, like a storm front.
    private static void overcast(Canvas c, Paint p, int w, int h, boolean night, boolean storm) {
        float u = w / 1280f;
        p.setMaskFilter(new BlurMaskFilter(26 * u, BlurMaskFilter.Blur.NORMAL));
        float[][] spots = {{0.08f, 0.10f, 1.5f}, {0.30f, 0.20f, 1.7f}, {0.55f, 0.08f, 1.6f}, {0.80f, 0.18f, 1.8f},
                {0.98f, 0.08f, 1.4f}, {0.18f, 0.42f, 1.4f}, {0.46f, 0.38f, 1.5f}, {0.72f, 0.45f, 1.4f}}; // x, y, size
        for (float[] k : spots) {
            float x = w * k[0], y = h * k[1], s = k[2] * u;
            p.setColor(night ? (storm ? 0x3D3A4C62 : 0x30405468) : (storm ? 0x66E4EBF0 : 0x55EEF2F6));
            c.drawOval(new RectF(x - 150 * s, y - 28 * s, x + 150 * s, y + 28 * s), p);
            c.drawOval(new RectF(x - 90 * s, y - 52 * s, x + 60 * s, y + 8 * s), p);
            c.drawOval(new RectF(x - 10 * s, y - 40 * s, x + 110 * s, y + 10 * s), p);
        }
        p.setMaskFilter(null);
        if (storm) {
            p.setShader(new LinearGradient(0, 0, 0, h * 0.4f, night ? 0x66000000 : 0x55101820, 0x00000000, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h * 0.4f, p);
            p.setShader(null);
        }
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

    // Scattered clouds by day. Partly cloudy has more of them, thicker and greyer.
    private static void clouds(Canvas c, Paint p, int w, int h, boolean night, boolean partly) {
        float u = w / 1280f;
        p.setMaskFilter(new BlurMaskFilter(14 * u, BlurMaskFilter.Blur.NORMAL));
        float[][] spots = {{0.10f, 0.30f, 1.0f}, {0.38f, 0.17f, 0.8f}, {0.62f, 0.40f, 1.2f},
                {0.90f, 0.52f, 0.9f}, {0.22f, 0.58f, 1.1f}, {0.74f, 0.74f, 1.0f}}; // x, y, size
        float[][] more = {{0.03f, 0.14f, 1.3f}, {0.28f, 0.40f, 1.0f}, {0.50f, 0.58f, 1.3f},
                {0.82f, 0.22f, 1.1f}, {0.96f, 0.36f, 1.2f}, {0.60f, 0.14f, 0.9f}};
        int count = spots.length + (partly ? more.length : 0);
        for (int i = 0; i < count; i++) {
            float[] k = i < spots.length ? spots[i] : more[i - spots.length];
            float x = w * k[0], y = h * k[1], s = k[2] * u * (partly ? 1.15f : 1f);
            p.setColor(partly ? 0x70E6EDF3 : 0x55FFFFFF);
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

    // The moon at night, top middle (there is no sun by day). Low enough that its whole glow fits on
    // the screen, and clear of the clock and the weather pills.
    private static void celestial(Canvas c, Paint p, int w, int h, boolean night) {
        float cx = w * 0.50f, cy = h * 0.36f, u = w / 1280f;
        if (night) {
            p.setShader(glow(cx, cy, 165 * u, 0xB8C8FF, 0x70, 3f));
            c.drawCircle(cx, cy, 165 * u, p);
            p.setShader(null);
            Path moon = new Path();
            moon.addCircle(cx, cy, 46 * u, Path.Direction.CW);
            Path cut = new Path();
            cut.addCircle(cx + 22 * u, cy - 13 * u, 41.5f * u, Path.Direction.CW);
            moon.op(cut, Path.Op.DIFFERENCE);
            p.setColor(0xFFF4F6FF);
            c.drawPath(moon, p);
        }
    }
}
