package com.tiny.arcade;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

public final class ArcadeUi {
    public static final int BG = 0xFF14161A;
    public static final int CARD = 0xFF20242B;
    public static final int CARD_PRESSED = 0xFF2B313A;
    public static final int TEXT = 0xFFF7F8FA;
    public static final int MUTED = 0xFFAAB3C0;
    public static final int DIM = 0xFF75808E;
    public static final int LINE = 0xFF343B46;
    public static final int ORANGE = 0xFFD48837;
    public static final int ORANGE_L = 0xFFF0A64F;
    public static final int GREEN = 0xFF2E8B57;
    public static final int GREEN_L = 0xFF3FAE74;
    public static final int BLUE = 0xFF3F6FA8;
    public static final int BLUE_L = 0xFF5B8FD4;
    public static final int SLATE = 0xFF4A5568;
    public static final int GOLD = 0xFFE2B33C;
    public static final int CORAL = 0xFFE2644E;
    public static final int PURPLE = 0xFFA78BFA;
    public static final int DISABLED = 0xFF2A2E34;
    public static final int DISABLED_TEXT = 0xFF7A8491;

    private ArcadeUi() {
    }

    public static void drawButton(Canvas c, Paint p, RectF r, String label, boolean enabled, int color) {
        float rad = Math.min(r.height() * 0.30f, 22f);

        p.setStyle(Paint.Style.FILL);
        p.setColor(0x33000000);
        c.drawRoundRect(r.left + 1f, r.top + 3f, r.right + 1f, r.bottom + 3f, rad, rad, p);

        p.setColor(enabled ? color : DISABLED);
        c.drawRoundRect(r, rad, rad, p);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.4f);
        p.setColor(enabled ? 0x66FFFFFF : 0xFF3A414B);
        c.drawRoundRect(r, rad, rad, p);

        p.setStyle(Paint.Style.FILL);
        p.setColor(enabled ? 0x2EFFFFFF : 0x10FFFFFF);
        c.drawRoundRect(r.left + rad, r.top + 1.5f, r.right - rad, r.top + 2.5f, 1f, 1f, p);

        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(enabled ? 0xFFFFFFFF : DISABLED_TEXT);
        p.setTextSize(r.height() * 0.38f);
        Paint.FontMetrics fm = p.getFontMetrics();
        float yOff = -(fm.ascent + fm.descent) / 2f;
        c.drawText(label, r.centerX(), r.centerY() + yOff, p);
    }

    public static void drawSelectableButton(Canvas c, Paint p, RectF r, String label,
                                            boolean selected, int color) {
        drawButton(c, p, r, label, true, selected ? color : 0xFF343B45);

        if (!selected) return;

        float rad = Math.min(r.height() * 0.30f, 22f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3f);
        p.setColor(0xFFFFFFFF);
        c.drawRoundRect(r, rad, rad, p);

        float cr = Math.min(r.height() * 0.16f, 13f);
        float cx = r.right - cr - 5f;
        float cy = r.top + cr + 5f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(cx, cy, cr, p);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.8f, cr * 0.28f));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(color);
        Path path = new Path();
        path.moveTo(cx - cr * 0.45f, cy + cr * 0.02f);
        path.lineTo(cx - cr * 0.08f, cy + cr * 0.38f);
        path.lineTo(cx + cr * 0.50f, cy - cr * 0.38f);
        c.drawPath(path, p);
        p.setStrokeCap(Paint.Cap.BUTT);
    }

    public static void drawBadge(Canvas c, Paint p, RectF r, String label, int color) {
        float rad = r.height() * 0.5f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawRoundRect(r, rad, rad, p);

        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(0xFF1A1D22);
        p.setTextSize(r.height() * 0.56f);
        Paint.FontMetrics fm = p.getFontMetrics();
        float yOff = -(fm.ascent + fm.descent) / 2f;
        c.drawText(label, r.centerX(), r.centerY() + yOff, p);
    }

    public static float textBaseline(Paint p, float centerY) {
        Paint.FontMetrics fm = p.getFontMetrics();
        return centerY - (fm.ascent + fm.descent) / 2f;
    }

    private static Shader turnGlowShader;
    private static int turnGlowColor;
    private static boolean turnGlowFromTop;
    private static float turnGlowW;
    private static float turnGlowH;

    /**
     * Draws a soft vertical wash from the active player's side of the screen.
     * fromTop=true fades downward; false fades upward from the bottom edge.
     */
    public static void drawTurnGlow(Canvas c, Paint p, float width, float height,
                                    int color, boolean fromTop) {
        if (turnGlowShader == null || color != turnGlowColor
            || fromTop != turnGlowFromTop
            || width != turnGlowW || height != turnGlowH) {
            float startY = fromTop ? 0f : height;
            float endY = fromTop ? height * 0.62f : height * 0.38f;
            turnGlowShader = new LinearGradient(0f, startY, 0f, endY,
                color, color & 0x00FFFFFF, Shader.TileMode.CLAMP);
            turnGlowColor = color;
            turnGlowFromTop = fromTop;
            turnGlowW = width;
            turnGlowH = height;
        }
        p.setStyle(Paint.Style.FILL);
        p.setShader(turnGlowShader);
        c.drawRect(0f, 0f, width, height, p);
        p.setShader(null);
    }

    public static final int ICON_HOME = 1;
    public static final int ICON_PLUS = 2;
    public static final int ICON_UNDO = 3;
    public static final int ICON_SHARE = 4;
    public static final int ICON_COPY = 5;
    public static final int ICON_CLOSE = 6;
    public static final int ICON_BACK = 7;
    public static final int ICON_PLAY = 8;
    public static final int ICON_SOLO = 9;
    public static final int ICON_COOP = 10;
    public static final int ICON_BOT = 11;
    public static final int ICON_CLOCK = 12;
    public static final int ICON_CHECK = 13;
    public static final int ICON_WIFI = 14;
    public static final int ICON_OFFLINE = 15;
    public static final int ICON_TROPHY = 16;
    public static final int ICON_DRAW = 17;
    public static final int ICON_WARN = 18;
    public static final int ICON_DOOR = 19;
    public static final int ICON_HOUSE_PLUS = 20;
    public static final int ICON_GEAR = 21;
    public static final int ICON_BULB = 22;

    public static void drawIconButton(Canvas c, Paint p, RectF r, int icon,
                                      boolean enabled, int color) {
        drawButton(c, p, r, "", enabled, color);
        float size = Math.min(r.height() * 0.52f, r.width() * 0.42f);
        drawIcon(c, p, r.centerX(), r.centerY(), size, icon,
            enabled ? 0xFFFFFFFF : DISABLED_TEXT);
    }

    public static void drawIcon(Canvas c, Paint p, float cx, float cy, float s,
                                int icon, int color) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.6f, s * 0.11f));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);
        Path q = new Path();
        switch (icon) {
            case ICON_HOME:
                q.moveTo(cx - s * 0.46f, cy - s * 0.02f);
                q.lineTo(cx, cy - s * 0.44f);
                q.lineTo(cx + s * 0.46f, cy - s * 0.02f);
                c.drawPath(q, p);
                c.drawLine(cx - s * 0.34f, cy - s * 0.02f,
                    cx + s * 0.34f, cy - s * 0.02f, p);
                c.drawRect(cx - s * 0.30f, cy - s * 0.02f,
                    cx + s * 0.30f, cy + s * 0.43f, p);
                q.reset();
                q.moveTo(cx - s * 0.10f, cy + s * 0.43f);
                q.lineTo(cx - s * 0.10f, cy + s * 0.13f);
                q.lineTo(cx + s * 0.10f, cy + s * 0.13f);
                q.lineTo(cx + s * 0.10f, cy + s * 0.43f);
                c.drawPath(q, p);
                break;
            case ICON_PLUS:
                c.drawLine(cx - s * 0.38f, cy, cx + s * 0.38f, cy, p);
                c.drawLine(cx, cy - s * 0.38f, cx, cy + s * 0.38f, p);
                break;
            case ICON_UNDO:
                q.moveTo(cx + s * 0.34f, cy + s * 0.34f);
                q.cubicTo(cx + s * 0.34f, cy - s * 0.10f,
                    cx + s * 0.18f, cy - s * 0.24f,
                    cx - s * 0.10f, cy - s * 0.24f);
                q.lineTo(cx - s * 0.39f, cy - s * 0.24f);
                c.drawPath(q, p);
                q.reset();
                q.moveTo(cx - s * 0.39f, cy - s * 0.24f);
                q.lineTo(cx - s * 0.10f, cy - s * 0.48f);
                q.moveTo(cx - s * 0.39f, cy - s * 0.24f);
                q.lineTo(cx - s * 0.10f, cy);
                c.drawPath(q, p);
                break;
            case ICON_SHARE:
                float dx = s * 0.30f;
                float dy = s * 0.30f;
                c.drawLine(cx - dx, cy, cx + dx, cy - dy, p);
                c.drawLine(cx - dx, cy, cx + dx, cy + dy, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx - dx, cy, s * 0.12f, p);
                c.drawCircle(cx + dx, cy - dy, s * 0.12f, p);
                c.drawCircle(cx + dx, cy + dy, s * 0.12f, p);
                break;
            case ICON_COPY:
                c.drawRoundRect(new RectF(cx - s * 0.35f, cy - s * 0.34f,
                    cx + s * 0.15f, cy + s * 0.22f), s * 0.10f, s * 0.10f, p);
                c.drawRoundRect(new RectF(cx - s * 0.12f, cy - s * 0.15f,
                    cx + s * 0.38f, cy + s * 0.40f), s * 0.10f, s * 0.10f, p);
                break;
            case ICON_CLOSE:
                c.drawLine(cx - s * 0.32f, cy - s * 0.32f,
                    cx + s * 0.32f, cy + s * 0.32f, p);
                c.drawLine(cx + s * 0.32f, cy - s * 0.32f,
                    cx - s * 0.32f, cy + s * 0.32f, p);
                break;
            case ICON_BACK:
                q.moveTo(cx + s * 0.18f, cy - s * 0.36f);
                q.lineTo(cx - s * 0.22f, cy);
                q.lineTo(cx + s * 0.18f, cy + s * 0.36f);
                c.drawPath(q, p);
                break;
            case ICON_PLAY:
                p.setStyle(Paint.Style.FILL);
                q.moveTo(cx - s * 0.24f, cy - s * 0.36f);
                q.lineTo(cx + s * 0.38f, cy);
                q.lineTo(cx - s * 0.24f, cy + s * 0.36f);
                q.close();
                c.drawPath(q, p);
                break;
            case ICON_SOLO:
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, cy - s * 0.25f, s * 0.20f, p);
                p.setStyle(Paint.Style.STROKE);
                c.drawArc(new RectF(cx - s * 0.36f, cy - s * 0.08f,
                    cx + s * 0.36f, cy + s * 0.62f), 195f, 150f, false, p);
                break;
            case ICON_COOP:
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx - s * 0.20f, cy - s * 0.25f, s * 0.17f, p);
                c.drawCircle(cx + s * 0.20f, cy - s * 0.25f, s * 0.17f, p);
                p.setStyle(Paint.Style.STROKE);
                c.drawArc(new RectF(cx - s * 0.52f, cy - s * 0.06f,
                    cx + s * 0.08f, cy + s * 0.56f), 195f, 150f, false, p);
                c.drawArc(new RectF(cx - s * 0.08f, cy - s * 0.06f,
                    cx + s * 0.52f, cy + s * 0.56f), 195f, 150f, false, p);
                break;
            case ICON_BOT:
                c.drawRoundRect(new RectF(cx - s * 0.38f, cy - s * 0.24f,
                    cx + s * 0.38f, cy + s * 0.32f), s * 0.12f, s * 0.12f, p);
                c.drawLine(cx, cy - s * 0.44f, cx, cy - s * 0.24f, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, cy - s * 0.48f, s * 0.07f, p);
                c.drawCircle(cx - s * 0.16f, cy + s * 0.02f, s * 0.055f, p);
                c.drawCircle(cx + s * 0.16f, cy + s * 0.02f, s * 0.055f, p);
                break;
            case ICON_CLOCK:
                c.drawCircle(cx, cy, s * 0.38f, p);
                c.drawLine(cx, cy, cx, cy - s * 0.24f, p);
                c.drawLine(cx, cy, cx + s * 0.21f, cy + s * 0.11f, p);
                break;
            case ICON_CHECK:
                q.moveTo(cx - s * 0.34f, cy + s * 0.02f);
                q.lineTo(cx - s * 0.08f, cy + s * 0.28f);
                q.lineTo(cx + s * 0.36f, cy - s * 0.30f);
                c.drawPath(q, p);
                break;
            case ICON_WIFI:
                c.drawArc(new RectF(cx - s * 0.40f, cy - s * 0.34f,
                    cx + s * 0.40f, cy + s * 0.46f), 225f, 90f, false, p);
                c.drawArc(new RectF(cx - s * 0.24f, cy - s * 0.18f,
                    cx + s * 0.24f, cy + s * 0.30f), 225f, 90f, false, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, cy + s * 0.30f, s * 0.07f, p);
                break;
            case ICON_OFFLINE:
                drawIcon(c, p, cx, cy, s, ICON_WIFI, color);
                p.setStyle(Paint.Style.STROKE);
                p.setColor(CORAL);
                c.drawLine(cx - s * 0.44f, cy - s * 0.40f,
                    cx + s * 0.44f, cy + s * 0.42f, p);
                break;
            case ICON_TROPHY:
                q.moveTo(cx - s * 0.30f, cy - s * 0.36f);
                q.lineTo(cx + s * 0.30f, cy - s * 0.36f);
                q.lineTo(cx + s * 0.24f, cy + s * 0.02f);
                q.cubicTo(cx + s * 0.16f, cy + s * 0.30f,
                    cx - s * 0.16f, cy + s * 0.30f, cx - s * 0.24f, cy + s * 0.02f);
                q.close();
                c.drawPath(q, p);
                c.drawLine(cx, cy + s * 0.24f, cx, cy + s * 0.38f, p);
                c.drawLine(cx - s * 0.24f, cy + s * 0.42f,
                    cx + s * 0.24f, cy + s * 0.42f, p);
                break;
            case ICON_DRAW:
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx - s * 0.24f, cy, s * 0.17f, p);
                c.drawCircle(cx + s * 0.24f, cy, s * 0.17f, p);
                p.setStyle(Paint.Style.STROKE);
                c.drawLine(cx - s * 0.05f, cy - s * 0.10f,
                    cx + s * 0.05f, cy - s * 0.10f, p);
                c.drawLine(cx - s * 0.05f, cy + s * 0.10f,
                    cx + s * 0.05f, cy + s * 0.10f, p);
                break;
            case ICON_WARN:
                p.setStyle(Paint.Style.FILL);
                q.moveTo(cx, cy - s * 0.42f);
                q.lineTo(cx + s * 0.44f, cy + s * 0.38f);
                q.lineTo(cx - s * 0.44f, cy + s * 0.38f);
                q.close();
                c.drawPath(q, p);
                p.setColor(0xFF1A1D22);
                c.drawRect(cx - s * 0.055f, cy - s * 0.15f,
                    cx + s * 0.055f, cy + s * 0.15f, p);
                c.drawCircle(cx, cy + s * 0.26f, s * 0.055f, p);
                break;
            case ICON_DOOR:
                c.drawRect(cx - s * 0.30f, cy - s * 0.40f,
                    cx + s * 0.22f, cy + s * 0.40f, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx + s * 0.09f, cy + s * 0.05f, s * 0.055f, p);
                p.setStyle(Paint.Style.STROKE);
                c.drawLine(cx + s * 0.32f, cy, cx + s * 0.45f, cy, p);
                c.drawLine(cx + s * 0.37f, cy - s * 0.08f,
                    cx + s * 0.45f, cy, p);
                c.drawLine(cx + s * 0.37f, cy + s * 0.08f,
                    cx + s * 0.45f, cy, p);
                break;
            case ICON_HOUSE_PLUS:
                drawIcon(c, p, cx - s * 0.08f, cy - s * 0.04f, s * 0.78f, ICON_HOME, color);
                p.setStyle(Paint.Style.STROKE);
                p.setColor(color);
                c.drawLine(cx + s * 0.28f, cy + s * 0.10f,
                    cx + s * 0.50f, cy + s * 0.10f, p);
                c.drawLine(cx + s * 0.39f, cy - s * 0.01f,
                    cx + s * 0.39f, cy + s * 0.21f, p);
                break;
            case ICON_GEAR:
                c.drawCircle(cx, cy, s * 0.20f, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, cy, s * 0.055f, p);
                p.setStyle(Paint.Style.STROKE);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4.0;
                    float ax = (float) Math.cos(a);
                    float ay = (float) Math.sin(a);
                    c.drawLine(cx + ax * s * 0.27f, cy + ay * s * 0.27f,
                        cx + ax * s * 0.40f, cy + ay * s * 0.40f, p);
                }
                break;
            case ICON_BULB:
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, cy - s * 0.12f, s * 0.24f, p);
                p.setStyle(Paint.Style.STROKE);
                c.drawLine(cx - s * 0.13f, cy + s * 0.20f,
                    cx + s * 0.13f, cy + s * 0.20f, p);
                c.drawLine(cx - s * 0.10f, cy + s * 0.32f,
                    cx + s * 0.10f, cy + s * 0.32f, p);
                c.drawLine(cx - s * 0.075f, cy + s * 0.43f,
                    cx + s * 0.075f, cy + s * 0.43f, p);
                break;
        }
        p.setStyle(Paint.Style.FILL);
        p.setStrokeCap(Paint.Cap.BUTT);
        p.setStrokeJoin(Paint.Join.MITER);
    }

    public static void drawCoopOffline(Canvas c, Paint p, int w, int h) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xB80A0C0F);
        c.drawRect(0, 0, w, h, p);

        float cardW = Math.min(w * 0.78f, h * 0.56f);
        float cardH = cardW * 0.38f;
        RectF card = new RectF((w - cardW) * 0.5f, (h - cardH) * 0.5f,
            (w + cardW) * 0.5f, (h + cardH) * 0.5f);
        p.setColor(0xFF23262C);
        c.drawRoundRect(card, 24f, 24f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f);
        p.setColor(0x55FFFFFF);
        c.drawRoundRect(card, 24f, 24f, p);

        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(CORAL);
        p.setTextSize(cardW * 0.070f);
        c.drawText("Opponent offline", card.centerX(), card.top + cardH * 0.36f, p);
        p.setTypeface(Typeface.DEFAULT);
        p.setColor(MUTED);
        p.setTextSize(cardW * 0.038f);
        c.drawText("Waiting to reconnect...", card.centerX(), card.top + cardH * 0.56f, p);
        p.setColor(DIM);
        p.setTextSize(cardW * 0.031f);
        c.drawText("Moves unlock automatically", card.centerX(), card.top + cardH * 0.73f, p);
    }
}
