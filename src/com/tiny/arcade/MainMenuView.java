package com.tiny.arcade;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

public final class MainMenuView extends View {
    public static final int GAME_CHESS = 0;
    public static final int GAME_CHECKERS = 1;
    public static final int GAME_POWER4 = 2;

    public interface Listener {
        void onGameSelected(int game);

        void onSettingsRequested();
    }

    private static final String[] NAMES = {"Chess", "Checkers", "Power of 4"};
    private static final int[] ACCENTS = {ArcadeUi.ORANGE, ArcadeUi.CORAL, ArcadeUi.GOLD};
    private static final boolean[] IS_NEW = {false, true, true};

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF tmp = new RectF();
    private final RectF[] cards = {new RectF(), new RectF(), new RectF()};
    private final RectF settingsButton = new RectF();
    private final Listener listener;

    private Shader bgShader;
    private Shader glowWarm;
    private Shader glowCool;

    private float contentW;
    private float headerTop;
    private float cardsTop;
    private float cardH;
    private float gap;
    private float footerY;
    private int pressed = -1;

    public MainMenuView(Context ctx, Listener listener) {
        super(ctx);
        this.listener = listener;
        setBackgroundColor(ArcadeUi.BG);
        ArcadeAudio.setEnabled(ArcadePrefs.soundEnabled(ctx));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        contentW = Math.min(w * 0.88f, h * 0.64f);
        cardH = Math.min(contentW * 0.235f, h * 0.155f);
        gap = cardH * 0.14f;

        float headerH = Math.max(cardH * 1.55f, h * 0.145f);
        float footerH = h * 0.065f;
        float totalH = headerH + cardH * 3f + gap * 2f + footerH;
        float top = (h - totalH) * 0.5f;
        if (top < h * 0.045f) top = h * 0.045f;

        headerTop = top;
        cardsTop = top + headerH;
        footerY = cardsTop + cardH * 3f + gap * 2f + footerH * 0.62f;

        float left = (w - contentW) * 0.5f;
        settingsButton.set(w - left - cardH * 0.72f, Math.max(h * 0.035f, top),
            w - left, Math.max(h * 0.035f, top) + cardH * 0.62f);
        for (int i = 0; i < cards.length; i++) {
            float y = cardsTop + i * (cardH + gap);
            cards[i].set(left, y, left + contentW, y + cardH);
        }

        bgShader = new LinearGradient(0f, 0f, 0f, h,
            0xFF111318, 0xFF0A0C0F, Shader.TileMode.CLAMP);
        glowWarm = new RadialGradient(w * 0.86f, h * 0.06f, w * 0.72f,
            0x24D48837, 0x00000000, Shader.TileMode.CLAMP);
        glowCool = new RadialGradient(w * 0.04f, h * 0.94f, w * 0.66f,
            0x1AA78BFA, 0x00000000, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();

        p.setStyle(Paint.Style.FILL);
        p.setShader(bgShader);
        c.drawRect(0, 0, w, h, p);
        p.setShader(glowWarm);
        c.drawRect(0, 0, w, h, p);
        p.setShader(glowCool);
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        drawGrid(c, w, h);
        drawHeader(c, w);
        ArcadeUi.drawIconButton(c, p, settingsButton, ArcadeUi.ICON_GEAR, true, ArcadeUi.SLATE);
        for (int i = 0; i < cards.length; i++) drawCard(c, i);
        drawFooter(c, w);
    }

    private void drawGrid(Canvas c, int w, int h) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        p.setColor(0x08FFFFFF);
        float step = Math.max(72f, w * 0.115f);
        for (float x = step; x < w; x += step) c.drawLine(x, 0, x, h, p);
        for (float y = step; y < h; y += step) c.drawLine(0, y, w, y, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawHeader(Canvas c, int w) {
        float labelSize = contentW * 0.030f;
        float titleSize = contentW * 0.115f;
        float subSize = contentW * 0.036f;
        float labelY = headerTop + labelSize;
        float titleY = labelY + titleSize * 1.22f;
        float subY = titleY + subSize * 1.55f;

        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.LEFT);
        p.setColor(ArcadeUi.ORANGE_L);
        p.setTextSize(labelSize);
        c.drawText("", (w - contentW) * 0.5f, labelY, p);

        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setColor(ArcadeUi.TEXT);
        p.setTextSize(titleSize);
        c.drawText("TinyArcade", (w - contentW) * 0.5f, titleY, p);

        p.setTypeface(Typeface.DEFAULT);
        p.setColor(ArcadeUi.MUTED);
        p.setTextSize(subSize);
        c.drawText("",
            (w - contentW) * 0.5f, subY, p);
    }

    private void drawCard(Canvas c, int i) {
        RectF r = cards[i];
        int accent = ACCENTS[i];
        boolean down = pressed == i;
        float rad = cardH * 0.16f;
        float pad = cardH * 0.125f;

        p.setStyle(Paint.Style.FILL);
        p.setColor(0x55000000);
        c.drawRoundRect(r.left + 2f, r.top + 5f, r.right + 2f, r.bottom + 5f, rad, rad, p);

        p.setColor(down ? ArcadeUi.CARD_PRESSED : ArcadeUi.CARD);
        c.drawRoundRect(r, rad, rad, p);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(down ? 2.6f : 1.4f);
        p.setColor((accent & 0x00FFFFFF) | (down ? 0xFF000000 : 0x77000000));
        c.drawRoundRect(r, rad, rad, p);

        p.setStyle(Paint.Style.FILL);
        p.setColor(accent);
        c.drawRoundRect(r.left + 1f, r.top + cardH * 0.18f,
            r.left + 4.5f, r.bottom - cardH * 0.18f, 2f, 2f, p);

        float icon = cardH * 0.54f;
        float ix = r.left + cardH * 0.18f;
        float iy = r.top + (cardH - icon) * 0.5f;
        tmp.set(ix, iy, ix + icon, iy + icon);
        p.setColor(0xFF171A1F);
        c.drawRoundRect(tmp, icon * 0.27f, icon * 0.27f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.2f);
        p.setColor((accent & 0x00FFFFFF) | 0x55000000);
        c.drawRoundRect(tmp, icon * 0.27f, icon * 0.27f, p);
        p.setStyle(Paint.Style.FILL);

        if (i == GAME_CHESS) drawChessIcon(c, ix + icon * 0.5f, iy + icon * 0.5f, icon);
        else if (i == GAME_CHECKERS) drawCheckersIcon(c, ix + icon * 0.5f, iy + icon * 0.5f, icon);
        else drawPower4Icon(c, ix + icon * 0.5f, iy + icon * 0.5f, icon, accent);

        float textX = ix + icon + cardH * 0.17f;
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.LEFT);
        p.setColor(ArcadeUi.TEXT);
        p.setTextSize(cardH * 0.205f);
        c.drawText(NAMES[i], textX, r.top + cardH * 0.60f, p);

        if (IS_NEW[i]) {
            float bw = cardH * 0.34f;
            float bh = cardH * 0.145f;
            tmp.set(r.right - pad - bw, r.top + pad * 0.75f,
                r.right - pad, r.top + pad * 0.75f + bh);
            ArcadeUi.drawBadge(c, p, tmp, "NEW", accent);
        }

        float cr = cardH * 0.14f;
        float cx = r.right - cardH * 0.22f;
        float cy = r.top + cardH * 0.59f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(accent);
        c.drawCircle(cx, cy, cr, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2.2f, cr * 0.19f));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(0xFF171A1F);
        path.reset();
        path.moveTo(cx - cr * 0.20f, cy - cr * 0.42f);
        path.lineTo(cx + cr * 0.25f, cy);
        path.lineTo(cx - cr * 0.20f, cy + cr * 0.42f);
        c.drawPath(path, p);
        p.setStrokeCap(Paint.Cap.BUTT);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawFooter(Canvas c, int w) {
        p.setTypeface(Typeface.DEFAULT);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(ArcadeUi.DIM);
        p.setTextSize(contentW * 0.026f);
        c.drawText("Tiny · No accounts · No ads · No noise", w * 0.5f, footerY, p);
    }

    private void drawChessIcon(Canvas c, float cx, float cy, float s) {
        float k = s * 0.0145f;
        c.save();
        c.translate(cx - 54f * k, cy - 56.5f * k);
        c.scale(k, k);

        path.reset();
        path.moveTo(36f, 80f);
        path.lineTo(72f, 80f);
        path.lineTo(69f, 74f);
        path.lineTo(65f, 70f);
        path.cubicTo(68f, 53f, 64f, 40f, 53f, 37f);
        path.lineTo(51.5f, 33f);
        path.lineTo(48f, 37f);
        path.lineTo(45.5f, 33f);
        path.lineTo(44f, 39f);
        path.cubicTo(39.5f, 43f, 36f, 48f, 36f, 52f);
        path.cubicTo(36.5f, 55f, 41f, 55f, 48.5f, 53f);
        path.cubicTo(44.5f, 58f, 42f, 63f, 41.5f, 70f);
        path.lineTo(37f, 74f);
        path.close();

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFF7F8FA);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.2f);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(0xFF171A20);
        c.drawPath(path, p);
        c.drawLine(38f, 74f, 70f, 74f, p);
        c.drawLine(42f, 70f, 64f, 70f, p);

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF171A20);
        c.drawCircle(44f, 44f, 1.5f, p);
        c.drawCircle(39.1f, 49.5f, 1.1f, p);

        c.restore();
        p.setStyle(Paint.Style.FILL);
    }

    private void drawCheckersIcon(Canvas c, float cx, float cy, float s) {
        float r = s * 0.265f;
        float ax = cx - s * 0.10f;
        float ay = cy + s * 0.11f;
        float bx = cx + s * 0.13f;
        float by = cy - s * 0.09f;

        p.setColor(0x44000000);
        tmp.set(ax - r * 1.04f, ay + r * 0.62f, ax + r * 1.04f, ay + r * 1.06f);
        c.drawOval(tmp, p);
        tmp.set(bx - r * 1.04f, by + r * 0.62f, bx + r * 1.04f, by + r * 1.06f);
        c.drawOval(tmp, p);

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFD9543F);
        c.drawCircle(ax, ay, r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.025f);
        p.setColor(0xFFF08A73);
        c.drawCircle(ax, ay, r * 0.92f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFB83E2C);
        c.drawCircle(ax, ay, r * 0.58f, p);

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFF2E3C8);
        c.drawCircle(bx, by, r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.025f);
        p.setColor(0xFF8A6A44);
        c.drawCircle(bx, by, r * 0.92f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFE2CDA9);
        c.drawCircle(bx, by, r * 0.58f, p);

        float kw = r * 0.88f;
        float kh = r * 0.54f;
        path.reset();
        path.moveTo(bx - kw * 0.50f, by + kh * 0.30f);
        path.lineTo(bx - kw * 0.35f, by - kh * 0.35f);
        path.lineTo(bx - kw * 0.12f, by + kh * 0.05f);
        path.lineTo(bx, by - kh * 0.50f);
        path.lineTo(bx + kw * 0.12f, by + kh * 0.05f);
        path.lineTo(bx + kw * 0.35f, by - kh * 0.35f);
        path.lineTo(bx + kw * 0.50f, by + kh * 0.30f);
        path.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(ArcadeUi.ORANGE);
        c.drawPath(path, p);
    }

    private void drawPower4Icon(Canvas c, float cx, float cy, float s, int accent) {
        float bw = s * 0.68f;
        float bh = s * 0.58f;
        float left = cx - bw * 0.5f;
        float top = cy - bh * 0.5f;
        float cell = bw / 4f;

        tmp.set(left, top, left + bw, top + bh);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF14202C);
        c.drawRoundRect(tmp, s * 0.07f, s * 0.07f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.4f);
        p.setColor((accent & 0x00FFFFFF) | 0x77000000);
        c.drawRoundRect(tmp, s * 0.07f, s * 0.07f, p);

        p.setStyle(Paint.Style.FILL);
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                c.drawCircle(left + cell * (col + 0.5f), top + cell * (row + 0.5f),
                    cell * 0.28f, pColor(0xFF080B0E));
            }
        }

        p.setColor(ArcadeUi.GOLD);
        for (int col = 0; col < 4; col++) {
            c.drawCircle(left + cell * (col + 0.5f), top + cell * 3.5f, cell * 0.25f, p);
        }
        p.setColor(ArcadeUi.CORAL);
        c.drawCircle(left + cell * 1.5f, top + cell * 2.5f, cell * 0.25f, p);
        c.drawCircle(left + cell * 2.5f, top + cell * 1.5f, cell * 0.25f, p);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.2f);
        p.setColor(0x99FFFFFF);
        c.drawLine(left + cell * 0.5f, top + cell * 3.5f,
            left + cell * 3.5f, top + cell * 3.5f, p);
        p.setStyle(Paint.Style.FILL);
    }

    private Paint pColor(int color) {
        p.setColor(color);
        return p;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            pressed = settingsButton.contains(e.getX(), e.getY()) ? 3
                : findCard(e.getX(), e.getY());
            invalidate();
            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_UP) {
            int hit = settingsButton.contains(e.getX(), e.getY()) ? 3
                : findCard(e.getX(), e.getY());
            int old = pressed;
            pressed = -1;
            invalidate();
            if (hit >= 0 && hit == old) {
                playSoundEffect(android.view.SoundEffectConstants.CLICK);
                if (hit == 3) listener.onSettingsRequested();
                else listener.onGameSelected(hit);
            }
            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_CANCEL) {
            pressed = -1;
            invalidate();
        }
        return true;
    }

    private int findCard(float x, float y) {
        for (int i = 0; i < cards.length; i++) {
            if (cards[i].contains(x, y)) return i;
        }
        return -1;
    }
}
