package com.tiny.arcade;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * A compact, allocation-light board theme slider. Each visible option draws a
 * real miniature board with the theme colours, and the large preview updates
 * while the strip is dragged.
 */
public final class ThemePickerView extends View {
    public interface Listener {
        void onThemePickerDone();
        void onThemePickerCancel();
    }

    private static final String[] FILES = {"A", "B", "C", "D", "E", "F", "G", "H"};
    private static final String[] RANKS = {"8", "7", "6", "5", "4", "3", "2", "1"};
    private static final String[] POSITIONS = {
        "1 of 10", "2 of 10", "3 of 10", "4 of 10", "5 of 10",
        "6 of 10", "7 of 10", "8 of 10", "9 of 10", "10 of 10"
    };
    // A sparse mid-game arrangement keeps the preview readable at any size.
    private static final int[][] PIECES = {
        {6, 1, 1}, {6, 3, 1}, {6, 5, 1},
        {1, 2, 0}, {1, 4, 0}, {1, 6, 0},
        {4, 4, 1}, {3, 3, 0}
    };
    private static final int PRESS_NONE = 0;
    private static final int PRESS_BACK = 1;
    private static final int PRESS_DONE = 2;
    private static final int PRESS_STRIP = 3;

    private final Listener listener;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics fm = new Paint.FontMetrics();
    private final Path path = new Path();
    private final RectF back = new RectF();
    private final RectF done = new RectF();
    private final RectF preview = new RectF();
    private final RectF strip = new RectF();
    private final RectF tmp = new RectF();
    private final RectF mini = new RectF();
    private final float touchSlop;

    private int selected;
    private int committed;
    private int pressed = PRESS_NONE;
    private boolean dragging;
    private boolean stripDown;
    private float downX;
    private float downScroll;
    private float pad;
    private float cellSize;
    private float cellStep;
    private float contentWidth;
    private float scroll;
    private float maxScroll;

    public ThemePickerView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        this.selected = ArcadePrefs.chessTheme(context);
        this.committed = selected;
        setBackgroundColor(ArcadeUi.BG);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        pad = Math.min(w, h) * 0.055f;
        boolean wide = w > h;
        float topH = Math.max(40f, Math.min(Math.min(w, h) * 0.085f, 72f));

        back.set(pad, pad, pad + topH, pad + topH);
        float doneW = Math.min(w * 0.26f, topH * 2.6f);
        float doneH = topH * 0.78f;
        done.set(w - pad - doneW, pad + (topH - doneH) * 0.5f,
            w - pad, pad + (topH + doneH) * 0.5f);

        cellSize = wide
            ? Math.min(h * 0.17f, w * 0.15f)
            : Math.min(w * 0.22f, h * 0.17f);
        cellSize = Math.max(44f, cellSize);
        float cellGap = Math.max(7f, cellSize * 0.10f);
        cellStep = cellSize + cellGap;
        contentWidth = ChessTheme.COUNT * cellStep - cellGap;
        float stripH = cellSize * 1.25f;
        strip.set(pad, h - pad - stripH, w - pad, h - pad);
        maxScroll = Math.max(0f, contentWidth - strip.width());

        float headerBottom = pad + topH * 1.30f;
        float nameSpace = Math.max(22f, Math.min(w, h) * 0.07f);
        float maxByHeight = strip.top - headerBottom - nameSpace * 2f;
        float maxByWidth = w - pad * 2f;
        float bigSize = Math.min(maxByWidth, maxByHeight);
        bigSize = Math.min(bigSize, wide ? h * 0.48f : h * 0.40f);
        bigSize = Math.max(72f, bigSize);
        float previewTop = (headerBottom + strip.top - nameSpace) * 0.5f - bigSize * 0.5f;
        previewTop = Math.max(previewTop, headerBottom + nameSpace * 0.3f);
        if (previewTop + bigSize > strip.top - nameSpace * 0.4f) {
            previewTop = Math.max(headerBottom, strip.top - nameSpace * 0.4f - bigSize);
        }
        preview.set((w - bigSize) * 0.5f, previewTop,
            (w + bigSize) * 0.5f, previewTop + bigSize);

        centerOn(selected);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        c.drawColor(ArcadeUi.BG);

        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(ArcadeUi.TEXT);
        p.setTextSize(Math.min(w, h) * 0.058f);
        float topH = Math.max(40f, Math.min(Math.min(w, h) * 0.085f, 72f));
        c.drawText("Chess board", w * 0.5f, baseline(pad + topH * 0.62f), p);
        p.setTypeface(Typeface.DEFAULT);
        p.setTextSize(Math.min(w, h) * 0.031f);
        p.setColor(ArcadeUi.MUTED);
        c.drawText("Tap or drag to preview", w * 0.5f,
            baseline(pad + topH * 1.10f), p);

        ArcadeUi.drawIconButton(c, p, back, ArcadeUi.ICON_BACK, true, ArcadeUi.SLATE);
        ArcadeUi.drawButton(c, p, done, "Done", true, ArcadeUi.ORANGE);

        drawBoard(c, preview, selected, true);

        float gap = strip.top - preview.bottom;
        float nameCenter = preview.bottom + Math.min(gap * 0.36f, preview.height() * 0.17f);
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(ArcadeUi.ORANGE_L);
        p.setTextSize(preview.height() * 0.075f);
        c.drawText(ChessTheme.name(selected), w * 0.5f, baseline(nameCenter), p);

        boolean showHint = strip.top - nameCenter > preview.height() * 0.13f;
        if (showHint) {
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(preview.height() * 0.038f);
            p.setColor(ArcadeUi.DIM);
            c.drawText(POSITIONS[selected], w * 0.5f,
                baseline(nameCenter + preview.height() * 0.090f), p);
        }

        c.save();
        c.clipRect(strip);
        float centering = Math.max(0f, (strip.width() - contentWidth) * 0.5f);
        float baseLeft = strip.left + centering - scroll;
        for (int i = 0; i < ChessTheme.COUNT; i++) {
            float x = baseLeft + i * cellStep;
            if (x + cellSize < strip.left || x > strip.right) continue;
            drawCell(c, x, i);
        }
        c.restore();
    }

    private void drawCell(Canvas c, float x, int theme) {
        float top = strip.top;
        float bottom = top + cellSize * 1.25f;

        tmp.set(x, top + 1f, x + cellSize, bottom - 1f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(theme == selected ? ArcadeUi.CARD_PRESSED : ArcadeUi.CARD);
        c.drawRoundRect(tmp, cellSize * 0.13f, cellSize * 0.13f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(theme == selected ? Math.max(2f, cellSize * 0.025f) : 1.2f);
        p.setColor(theme == selected ? ArcadeUi.ORANGE_L : ArcadeUi.LINE);
        c.drawRoundRect(tmp, cellSize * 0.13f, cellSize * 0.13f, p);

        float boardSize = cellSize * 0.80f;
        mini.set(x + (cellSize - boardSize) * 0.5f, top + cellSize * 0.075f,
            x + (cellSize + boardSize) * 0.5f, top + cellSize * 0.075f + boardSize);
        drawBoard(c, mini, theme, false);

        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(theme == selected ? ArcadeUi.ORANGE_L : ArcadeUi.MUTED);
        p.setTextSize(cellSize * 0.115f);
        c.drawText(ChessTheme.name(theme), x + cellSize * 0.5f,
            baseline(top + cellSize * 1.15f), p);
    }

    private void drawBoard(Canvas c, RectF r, int theme, boolean large) {
        float frame = r.width() * (large ? 0.058f : 0.055f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(ChessTheme.frame(theme));
        c.drawRect(r.left - frame, r.top - frame, r.right + frame, r.bottom + frame, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, r.width() * 0.006f));
        p.setColor(ChessTheme.frameLine(theme));
        c.drawRect(r.left - frame + 0.5f, r.top - frame + 0.5f,
            r.right + frame - 0.5f, r.bottom + frame - 0.5f, p);

        float sq = r.width() / 8f;
        p.setStyle(Paint.Style.FILL);
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                boolean light = ((row + col) & 1) == 0;
                float x = r.left + col * sq;
                float y = r.top + row * sq;
                p.setColor(light ? ChessTheme.light(theme) : ChessTheme.dark(theme));
                c.drawRect(x, y, x + sq, y + sq, p);
            }
        }

        if (large) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, sq * 0.030f));
            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    boolean light = ((row + col) & 1) == 0;
                    float x = r.left + col * sq;
                    float y = r.top + row * sq;
                    p.setColor(light ? ChessTheme.grainLight(theme) : ChessTheme.grainDark(theme));
                    c.drawLine(x + sq * 0.12f, y + sq * 0.62f,
                        x + sq * 0.88f, y + sq * 0.50f, p);
                    c.drawLine(x + sq * 0.08f, y + sq * 0.78f,
                        x + sq * 0.70f, y + sq * 0.68f, p);
                }
            }
        }

        drawPieces(c, r, sq, large);
        if (large) drawCoords(c, r, frame, sq, theme);
    }

    private void drawPieces(Canvas c, RectF r, float sq, boolean large) {
        if (!large && cellSize < 66f) return;
        for (int[] piece : PIECES) {
            float cx = r.left + piece[1] * sq + sq * 0.5f;
            float cy = r.top + piece[0] * sq + sq * 0.5f;
            drawPawn(c, cx, cy, sq, piece[2] == 1);
        }
    }

    private void drawPawn(Canvas c, float cx, float cy, float sq, boolean white) {
        int fill = white ? 0xFFFDFBF7 : 0xFF171717;
        int stroke = white ? 0xFF141414 : 0xFFF5EFE6;
        float sw = Math.max(1f, sq * 0.035f);

        p.setStyle(Paint.Style.FILL);
        p.setColor(0x33000000);
        c.drawOval(cx - sq * 0.30f, cy + sq * 0.31f,
            cx + sq * 0.30f, cy + sq * 0.39f, p);

        path.reset();
        path.moveTo(cx - sq * 0.28f, cy + sq * 0.32f);
        path.lineTo(cx + sq * 0.28f, cy + sq * 0.32f);
        path.lineTo(cx + sq * 0.23f, cy + sq * 0.20f);
        path.quadTo(cx + sq * 0.12f, cy + sq * 0.10f, cx + sq * 0.10f, cy - sq * 0.02f);
        path.quadTo(cx + sq * 0.28f, cy - sq * 0.10f, cx + sq * 0.17f, cy - sq * 0.21f);
        path.cubicTo(cx + sq * 0.10f, cy - sq * 0.33f,
            cx - sq * 0.10f, cy - sq * 0.33f, cx - sq * 0.17f, cy - sq * 0.21f);
        path.quadTo(cx - sq * 0.28f, cy - sq * 0.10f, cx - sq * 0.10f, cy - sq * 0.02f);
        path.quadTo(cx - sq * 0.12f, cy + sq * 0.10f, cx - sq * 0.23f, cy + sq * 0.20f);
        path.close();

        p.setStyle(Paint.Style.FILL);
        p.setColor(fill);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(sw);
        p.setColor(stroke);
        c.drawPath(path, p);

        p.setStyle(Paint.Style.FILL);
        p.setColor(fill);
        c.drawCircle(cx, cy - sq * 0.22f, sq * 0.105f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sw);
        p.setColor(stroke);
        c.drawCircle(cx, cy - sq * 0.22f, sq * 0.105f, p);
        c.drawLine(cx - sq * 0.26f, cy + sq * 0.32f,
            cx + sq * 0.26f, cy + sq * 0.32f, p);
    }

    private void drawCoords(Canvas c, RectF r, float frame, float sq, int theme) {
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(Math.max(7f, frame * 0.56f));
        p.setColor(ChessTheme.coord(theme));
        p.getFontMetrics(fm);
        float off = -(fm.ascent + fm.descent) * 0.5f;
        for (int i = 0; i < 8; i++) {
            float x = r.left + (i + 0.5f) * sq;
            c.drawText(FILES[i], x, r.top - frame * 0.5f + off, p);
            c.drawText(RANKS[i], r.left - frame * 0.5f,
                r.top + (i + 0.5f) * sq + off, p);
        }
    }

    private float baseline(float centerY) {
        p.getFontMetrics(fm);
        return centerY - (fm.ascent + fm.descent) * 0.5f;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        int action = event.getAction();

        if (action == MotionEvent.ACTION_DOWN) {
            dragging = false;
            stripDown = false;
            downX = x;
            downScroll = scroll;
            if (back.contains(x, y)) {
                pressed = PRESS_BACK;
            } else if (done.contains(x, y)) {
                pressed = PRESS_DONE;
            } else if (strip.contains(x, y)) {
                pressed = PRESS_STRIP;
                stripDown = true;
            } else {
                pressed = PRESS_NONE;
            }
            invalidate();
            return true;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            if (pressed == PRESS_STRIP && stripDown) {
                float dx = x - downX;
                if (!dragging && maxScroll > 0f && Math.abs(dx) > touchSlop) dragging = true;
                if (dragging) {
                    float next = clamp(downScroll - dx, 0f, maxScroll);
                    if (next != scroll) {
                        scroll = next;
                        int nearest = nearestIndex();
                        if (nearest != selected) selected = nearest;
                        invalidate();
                    }
                }
            }
            return true;
        }

        if (action == MotionEvent.ACTION_UP) {
            int old = pressed;
            pressed = PRESS_NONE;
            stripDown = false;
            if (old == PRESS_BACK && back.contains(x, y)) {
                invalidate();
                listener.onThemePickerCancel();
                return true;
            }
            if (old == PRESS_DONE && done.contains(x, y)) {
                invalidate();
                listener.onThemePickerDone();
                return true;
            }
            if (old == PRESS_STRIP) {
                if (dragging) {
                    selected = nearestIndex();
                    centerOn(selected);
                    invalidate();
                } else {
                    int hit = cellAt(x, y);
                    if (hit >= 0) {
                        selected = hit;
                        centerOn(hit);
                        invalidate();
                    }
                }
            }
            dragging = false;
            invalidate();
            return true;
        }

        if (action == MotionEvent.ACTION_CANCEL) {
            selected = committed;
            centerOn(selected);
            pressed = PRESS_NONE;
            dragging = false;
            stripDown = false;
            invalidate();
        }
        return true;
    }

    private int cellAt(float x, float y) {
        if (!strip.contains(x, y)) return -1;
        float centering = Math.max(0f, (strip.width() - contentWidth) * 0.5f);
        float local = x - strip.left - centering + scroll;
        int index = (int) Math.floor(local / cellStep);
        if (index < 0 || index >= ChessTheme.COUNT) return -1;
        float within = local - index * cellStep;
        if (within < 0f || within > cellSize) return -1;
        return index;
    }

    private int nearestIndex() {
        float centering = Math.max(0f, (strip.width() - contentWidth) * 0.5f);
        float center = strip.width() * 0.5f - centering + scroll;
        int index = Math.round((center - cellSize * 0.5f) / cellStep);
        if (index < 0) return 0;
        if (index >= ChessTheme.COUNT) return ChessTheme.COUNT - 1;
        return index;
    }

    private void centerOn(int index) {
        if (maxScroll <= 0f || strip.width() <= 0f) {
            scroll = 0f;
            return;
        }
        float target = index * cellStep + cellSize * 0.5f - strip.width() * 0.5f;
        scroll = clamp(target, 0f, maxScroll);
    }

    public void saveSelection() {
        if (selected == committed) return;
        ArcadePrefs.setChessTheme(getContext(), selected);
        committed = selected;
    }

    public void cancelSelection() {
        selected = committed;
        centerOn(selected);
        invalidate();
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }
}
