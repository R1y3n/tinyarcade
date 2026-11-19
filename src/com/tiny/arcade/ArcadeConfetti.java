package com.tiny.arcade;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import java.util.Random;

public final class ArcadeConfetti {
    private static final int COUNT = 120;
    private static final long DURATION_MS = 5200L;
    private static final int[] COLORS = {
        ArcadeUi.GOLD, ArcadeUi.ORANGE_L, ArcadeUi.GREEN_L, ArcadeUi.BLUE_L,
        ArcadeUi.CORAL, ArcadeUi.PURPLE, 0xFFFFFFFF
    };

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rng = new Random();
    private final float[] px = new float[COUNT];
    private final float[] py = new float[COUNT];
    private final float[] vx = new float[COUNT];
    private final float[] vy = new float[COUNT];
    private final float[] size = new float[COUNT];
    private final float[] spin = new float[COUNT];
    private final float[] phase = new float[COUNT];
    private final int[] color = new int[COUNT];
    private final boolean[] round = new boolean[COUNT];

    private boolean active;
    private long startMs;
    private float width;
    private float height;

    public void stop() {
        active = false;
    }

    public void start(int w, int h) {
        width = Math.max(1, w);
        height = Math.max(1, h);
        startMs = SystemClock.uptimeMillis();
        active = true;

        for (int i = 0; i < COUNT; i++) {
            px[i] = rng.nextFloat() * width;
            py[i] = rng.nextFloat() * height * 1.35f;
            vx[i] = (rng.nextFloat() - 0.5f) * width * 0.075f;
            vy[i] = -(height * (0.06f + rng.nextFloat() * 0.13f));
            size[i] = width * (0.009f + rng.nextFloat() * 0.017f);
            spin[i] = (rng.nextFloat() - 0.5f) * 720f;
            phase[i] = rng.nextFloat() * 6.283f;
            color[i] = COLORS[rng.nextInt(COLORS.length)];
            round[i] = rng.nextFloat() < 0.28f;
        }
    }

    public boolean draw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        long elapsed = now - startMs;
        if (!active || elapsed >= DURATION_MS) {
            active = false;
            return false;
        }

        float sec = elapsed / 1000f;
        float fade = elapsed > DURATION_MS - 900L
            ? Math.max(0f, (DURATION_MS - elapsed) / 900f) : 1f;
        float span = height * 1.35f;

        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < COUNT; i++) {
            float sway = (float) Math.sin(sec * 2.7f + phase[i]) * width * 0.028f;
            float x = px[i] + vx[i] * sec + sway;
            float y = py[i] + vy[i] * sec;
            y = ((y % span) + span) % span;

            int a = Math.min(255, Math.max(0, (int) (fade * 255f)));
            int col = (color[i] & 0x00FFFFFF) | (a << 24);
            p.setColor(col);

            c.save();
            c.translate(x, y);
            c.rotate((spin[i] * sec + phase[i] * 57.2958f) % 360f);
            if (round[i]) {
                c.drawCircle(0f, 0f, size[i] * 0.62f, p);
            } else {
                c.drawRect(-size[i], -size[i] * 0.42f, size[i], size[i] * 0.42f, p);
            }
            c.restore();
        }
        return true;
    }
}
