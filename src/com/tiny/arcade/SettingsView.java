package com.tiny.arcade;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

public final class SettingsView extends View {
    public interface Listener {
        void onSettingsBack();

        void onThemePickerRequested();
    }

    private final Listener listener;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF back = new RectF();
    private final RectF[] rows = {
        new RectF(), new RectF(), new RectF(), new RectF(), new RectF(), new RectF()
    };
    private int pressed = -1;
    private float contentW;
    private float rowH;
    private float top;

    public SettingsView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setBackgroundColor(ArcadeUi.BG);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        contentW = Math.min(w * 0.88f, h * 0.82f);
        rowH = Math.min(h * 0.088f, w * 0.175f);
        top = h * 0.25f;
        float gap = Math.max(2f, rowH * 0.14f);
        float left = (w - contentW) * 0.5f;
        for (int i = 0; i < rows.length; i++) {
            float y = top + i * (rowH + gap);
            rows[i].set(left, y, left + contentW, y + rowH);
        }
        back.set(left, h * 0.065f, left + rowH * 0.82f, h * 0.065f + rowH * 0.56f);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        c.drawColor(ArcadeUi.BG);
        p.setStyle(Paint.Style.FILL);
        p.setColor(ArcadeUi.ORANGE_L);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(contentW * 0.034f);
        c.drawText("TINY ARCADE", (w - contentW) * 0.5f, h * 0.18f, p);
        p.setColor(ArcadeUi.TEXT);
        p.setTextSize(contentW * 0.095f);
        c.drawText("Settings", (w - contentW) * 0.5f, h * 0.235f, p);
        ArcadeUi.drawIconButton(c, p, back, ArcadeUi.ICON_BACK, true, ArcadeUi.SLATE);

        drawRow(c, 0, "View", ArcadePrefs.horizontalMenu(getContext()) ? "Horizontal" : "Vertical");
        drawRow(c, 1, "Sounds", ArcadePrefs.soundEnabled(getContext()) ? "On" : "Off");
        drawRow(c, 2, "Assistant", ArcadePrefs.assistantEnabled(getContext()) ? "On" : "Off");
        drawRow(c, 3, "Chess board", ChessTheme.name(ArcadePrefs.chessTheme(getContext())));
        drawRow(c, 4, "Update", getContext().getPackageName().endsWith(".nightly")
            ? "Stable only" : "Check now");
        drawRow(c, 5, "Support", "GitHub project");
    }

    private void drawRow(Canvas c, int index, String title, String value) {
        RectF r = rows[index];
        p.setStyle(Paint.Style.FILL);
        p.setColor(pressed == index ? ArcadeUi.CARD_PRESSED : ArcadeUi.CARD);
        c.drawRoundRect(r, rowH * 0.20f, rowH * 0.20f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.3f);
        p.setColor(ArcadeUi.LINE);
        c.drawRoundRect(r, rowH * 0.20f, rowH * 0.20f, p);
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(rowH * 0.27f);
        p.setColor(ArcadeUi.TEXT);
        c.drawText(title, r.left + rowH * 0.24f,
            ArcadeUi.textBaseline(p, r.centerY()), p);
        p.setTypeface(Typeface.DEFAULT);
        p.setTextAlign(Paint.Align.RIGHT);
        p.setTextSize(rowH * 0.205f);
        p.setColor(ArcadeUi.ORANGE_L);
        c.drawText(value, r.right - rowH * 0.24f,
            ArcadeUi.textBaseline(p, r.centerY()), p);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            pressed = hit(x, y);
            invalidate();
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            int hit = hit(x, y);
            int old = pressed;
            pressed = -1;
            invalidate();
            if (old >= 0 && hit == old) activate(hit);
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            pressed = -1;
            invalidate();
        }
        return true;
    }

    private int hit(float x, float y) {
        if (back.contains(x, y)) return rows.length;
        for (int i = 0; i < rows.length; i++) if (rows[i].contains(x, y)) return i;
        return -1;
    }

    private void activate(int index) {
        if (index == rows.length) {
            listener.onSettingsBack();
        } else if (index == 0) {
            boolean horizontal = !ArcadePrefs.horizontalMenu(getContext());
            ArcadePrefs.setHorizontalMenu(getContext(), horizontal);
            if (getContext() instanceof Activity) {
                ((Activity) getContext()).setRequestedOrientation(horizontal
                    ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
            listener.onSettingsBack();
        } else if (index == 1) {
            ArcadePrefs.setSoundEnabled(getContext(), !ArcadePrefs.soundEnabled(getContext()));
            invalidate();
        } else if (index == 2) {
            ArcadePrefs.setAssistantEnabled(getContext(), !ArcadePrefs.assistantEnabled(getContext()));
            invalidate();
        } else if (index == 3) {
            listener.onThemePickerRequested();
        } else if (index == 4) {
            if (getContext().getPackageName().endsWith(".nightly")) {
                android.widget.Toast.makeText(getContext(), "Nightly builds are updated manually.",
                    android.widget.Toast.LENGTH_SHORT).show();
            } else if (getContext() instanceof Activity) {
                new UpdateManager((Activity) getContext()).checkNow();
                android.widget.Toast.makeText(getContext(), "Checking GitHub for updates…",
                    android.widget.Toast.LENGTH_SHORT).show();
            }
        } else if (index == 5) {
            try {
                android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://github.com/R1y3n/tinyarcade"));
                getContext().startActivity(i);
            } catch (Throwable ignored) {
            }
        }
    }
}
