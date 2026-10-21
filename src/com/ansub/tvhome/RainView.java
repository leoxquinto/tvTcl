package com.ansub.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;
import java.util.Random;

// Rain falling over the sky, behind the tiles: slanted streaks in two batches (near and far), moved
// about 30 times a second, and only while the home screen is in front and it is raining.
final class RainView extends View {
    private static final int MAX = 150;
    private static final int[] DROPS = {0, 50, 95, 150}; // none, light, moderate, heavy
    private static final float SLANT = 0.2f;              // sideways travel per unit of fall
    private final Paint far = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint near = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random(5);
    private final float[] x = new float[MAX], y = new float[MAX], len = new float[MAX], speed = new float[MAX];
    private final float[] farPts = new float[MAX * 4], nearPts = new float[MAX * 4];
    private int level, count;
    private boolean running;
    private long last;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (count == 0 || !running) return;
            long now = SystemClock.uptimeMillis();
            step(Math.min(0.1f, (now - last) / 1000f));
            last = now;
            invalidate();
            postDelayed(this, 33);
        }
    };

    RainView(Context context) {
        super(context);
        far.setColor(0x55DCE8F8);
        near.setColor(0x99E6F0FF);
        far.setStrokeCap(Paint.Cap.ROUND);
        near.setStrokeCap(Paint.Cap.ROUND);
        setFocusable(false);
    }

    // 0 = no rain, 1 = light, 2 = moderate, 3 = heavy.
    void setLevel(int newLevel) {
        newLevel = Math.max(0, Math.min(3, newLevel));
        if (newLevel == level) return;
        level = newLevel;
        count = DROPS[level];
        scatter();
        schedule();
        invalidate();
    }

    // Only runs while the home screen is in front.
    void setRunning(boolean on) {
        running = on;
        schedule();
    }

    private void schedule() {
        removeCallbacks(tick);
        if (count > 0 && running && isAttachedToWindow()) {
            last = SystemClock.uptimeMillis();
            post(tick);
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        schedule();
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        float s = h / 1080f;
        far.setStrokeWidth(Math.max(1f, 1.6f * s));
        near.setStrokeWidth(Math.max(1.5f, 2.6f * s));
        scatter();
    }

    private void scatter() {
        for (int i = 0; i < count; i++) respawn(i, true);
    }

    private void respawn(int i, boolean anywhere) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        boolean isNear = i % 3 == 0;
        len[i] = isNear ? 38 + rnd.nextFloat() * 26 : 18 + rnd.nextFloat() * 14;
        speed[i] = isNear ? 1500 + rnd.nextFloat() * 400 : 900 + rnd.nextFloat() * 300;
        x[i] = rnd.nextFloat() * (w + h * SLANT);
        y[i] = anywhere ? rnd.nextFloat() * h : -len[i] * (h / 1080f) * rnd.nextFloat();
    }

    private void step(float dt) {
        int h = getHeight();
        if (h == 0) return;
        float s = h / 1080f;
        for (int i = 0; i < count; i++) {
            float fall = speed[i] * s * dt;
            y[i] += fall;
            x[i] -= fall * SLANT;
            if (y[i] - len[i] * s > h || x[i] < -len[i] * s) respawn(i, false);
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        if (count == 0) return;
        float s = getHeight() / 1080f;
        int nf = 0, nn = 0;
        for (int i = 0; i < count; i++) {
            boolean isNear = i % 3 == 0;
            float[] pts = isNear ? nearPts : farPts;
            int k = (isNear ? nn++ : nf++) * 4;
            float l = len[i] * s;
            pts[k] = x[i] + l * SLANT;
            pts[k + 1] = y[i] - l;
            pts[k + 2] = x[i];
            pts[k + 3] = y[i];
        }
        canvas.drawLines(farPts, 0, nf * 4, far);
        canvas.drawLines(nearPts, 0, nn * 4, near);
    }
}
