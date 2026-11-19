package com.tiny.arcade;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;

public final class GameModeView extends View {
    public interface Listener {
        void onSolo(int game);

        void onLocalTwoPlayer(int game);

        void onMultiplayer(int game);

        void onModeBack();
    }

    private final int game;
    private final Listener listener;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF tmp = new RectF();
    private final RectF soloCard = new RectF();
    private final RectF localCard = new RectF();
    private final RectF multiplayerCard = new RectF();
    private final RectF back = new RectF();

    private Shader bg;
    private Shader glow;
    private float contentW;
    private float cardH;
    private int pressed = -1;
    private boolean netChecked;
    private boolean netChecking;
    private boolean netOnline;
    private boolean netRadios;
    private boolean pendingCoopLaunch;
    private String netMessage = "";

    public GameModeView(Context context, int game, Listener listener) {
        super(context);
        this.game = game;
        this.listener = listener;
        setBackgroundColor(ArcadeUi.BG);
        refreshNetwork();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == View.VISIBLE && !netChecking) refreshNetwork();
    }

    private void refreshNetwork() {
        if (netChecking) return;
        netChecking = true;
        invalidate();
        ArcadeNet.checkAsync(getContext(), new ArcadeNet.Callback() {
            @Override
            public void onResult(ArcadeNet.Status status) {
                netChecking = false;
                netChecked = true;
                netOnline = status.online;
                netRadios = status.radiosOn;
                netMessage = status.message;
                invalidate();
                if (pendingCoopLaunch) {
                    pendingCoopLaunch = false;
                    if (status.online) listener.onMultiplayer(game);
                    else showNetworkDialog();
                }
            }
        });
    }

    private void requestCoop() {
        if (netChecked) {
            if (netOnline) {
                listener.onMultiplayer(game);
            } else {
                pendingCoopLaunch = true;
                showNetworkDialog();
            }
            return;
        }
        pendingCoopLaunch = true;
        refreshNetwork();
    }

    private void showNetworkDialog() {
        if (getWindowToken() == null) {
            pendingCoopLaunch = false;
            return;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(getContext());
        b.setTitle("Multiplayer needs internet");
        b.setMessage(netMessage.length() > 0 ? netMessage : ArcadeNet.NO_INTERNET);
        if (netRadios) {
            b.setPositiveButton("Retry", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    pendingCoopLaunch = true;
                    refreshNetwork();
                }
            });
            b.setNeutralButton("Settings", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    pendingCoopLaunch = false;
                    openNetworkSettings();
                }
            });
        } else {
            b.setPositiveButton("Open settings", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    pendingCoopLaunch = false;
                    openNetworkSettings();
                }
            });
            b.setNeutralButton("Retry", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    pendingCoopLaunch = true;
                    refreshNetwork();
                }
            });
        }
        b.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                pendingCoopLaunch = false;
            }
        });
        b.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                pendingCoopLaunch = false;
            }
        });
        b.show();
    }

    private void openNetworkSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
        } catch (Throwable ignored) {
        }
    }

    private String gameName() {
        if (game == MainMenuView.GAME_CHESS) return "Chess";
        if (game == MainMenuView.GAME_CHECKERS) return "Checkers";
        return "Power of 4";
    }

    private int accent() {
        if (game == MainMenuView.GAME_CHESS) return ArcadeUi.ORANGE;
        if (game == MainMenuView.GAME_CHECKERS) return ArcadeUi.CORAL;
        return ArcadeUi.GOLD;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        contentW = Math.min(w * 0.86f, h * 0.62f);
        cardH = Math.min(contentW * 0.25f, h * 0.13f);
        float gap = cardH * 0.14f;
        float left = (w - contentW) * 0.5f;
        float top = h * 0.39f;
        soloCard.set(left, top, left + contentW, top + cardH);
        localCard.set(left, top + cardH + gap, left + contentW, top + cardH * 2f + gap);
        multiplayerCard.set(left, top + (cardH + gap) * 2f,
            left + contentW, top + cardH * 3f + gap * 2f);
        float bw = contentW * 0.30f;
        back.set(left, h * 0.075f, left + bw, h * 0.075f + cardH * 0.48f);

        bg = new LinearGradient(0f, 0f, 0f, h, 0xFF12151A, 0xFF090B0E, Shader.TileMode.CLAMP);
        glow = new RadialGradient(w * 0.85f, h * 0.10f, w * 0.80f,
            0x26D48837, 0x00000000, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        p.setStyle(Paint.Style.FILL);
        p.setShader(bg);
        c.drawRect(0, 0, w, h, p);
        p.setShader(glow);
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        p.setColor(0x08FFFFFF);
        float step = Math.max(72f, w * 0.115f);
        for (float x = step; x < w; x += step) c.drawLine(x, 0, x, h, p);
        for (float y = step; y < h; y += step) c.drawLine(0, y, w, y, p);
        p.setStyle(Paint.Style.FILL);

        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.LEFT);
        p.setColor(accent());
        p.setTextSize(contentW * 0.030f);
        c.drawText("TINY ARCADE", (w - contentW) * 0.5f, h * 0.26f, p);

        p.setColor(ArcadeUi.TEXT);
        p.setTextSize(contentW * 0.115f);
        c.drawText(gameName(), (w - contentW) * 0.5f, h * 0.34f, p);

        boolean multiplayerReady = !netChecked || netOnline;
        drawModeCard(c, soloCard, ArcadeUi.ICON_SOLO, "Solo", "vs bot",
            true, ArcadeUi.GREEN, 0);
        drawModeCard(c, localCard, ArcadeUi.ICON_COOP, "Co-op", "2 players · 1 screen",
            true, ArcadeUi.GOLD, 1);
        drawModeCard(c, multiplayerCard, ArcadeUi.ICON_COOP, "Multiplayer", "online room",
            multiplayerReady, ArcadeUi.BLUE, 2);

        ArcadeUi.drawIconButton(c, p, back, ArcadeUi.ICON_BACK, true, ArcadeUi.SLATE);
    }

    private void drawModeCard(Canvas c, RectF r, int icon, String title, String subtitle,
                              boolean enabled, int color, int index) {
        float rad = cardH * 0.16f;
        float pad = cardH * 0.20f;
        boolean down = pressed == index;
        int tint = enabled ? color : 0xFF555C66;

        p.setStyle(Paint.Style.FILL);
        p.setColor(0x55000000);
        c.drawRoundRect(r.left + 2f, r.top + 5f, r.right + 2f, r.bottom + 5f, rad, rad, p);
        p.setColor(down ? ArcadeUi.CARD_PRESSED : ArcadeUi.CARD);
        c.drawRoundRect(r, rad, rad, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(down ? 2.4f : 1.3f);
        p.setColor((tint & 0x00FFFFFF) | 0xAA000000);
        c.drawRoundRect(r, rad, rad, p);
        p.setStyle(Paint.Style.FILL);

        float iconSize = cardH * 0.56f;
        float ix = r.left + pad;
        float iy = r.top + (cardH - iconSize) * 0.5f;
        tmp.set(ix, iy, ix + iconSize, iy + iconSize);
        p.setColor(0xFF171A1F);
        c.drawRoundRect(tmp, iconSize * 0.25f, iconSize * 0.25f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.4f);
        p.setColor((tint & 0x00FFFFFF) | 0x66000000);
        c.drawRoundRect(tmp, iconSize * 0.25f, iconSize * 0.25f, p);
        p.setStyle(Paint.Style.FILL);
        ArcadeUi.drawIcon(c, p, ix + iconSize * 0.5f, iy + iconSize * 0.5f,
            iconSize * 0.60f, icon, tint);

        float textX = ix + iconSize + cardH * 0.20f;
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.LEFT);
        p.setColor(enabled ? ArcadeUi.TEXT : ArcadeUi.DIM);
        p.setTextSize(cardH * 0.19f);
        c.drawText(title, textX, ArcadeUi.textBaseline(p, r.centerY() - cardH * 0.11f), p);

        p.setTypeface(Typeface.DEFAULT);
        p.setColor(enabled ? ArcadeUi.MUTED : ArcadeUi.DIM);
        float subSize = cardH * 0.13f;
        float maxTextW = r.right - cardH * 0.42f - textX;
        p.setTextSize(subSize);
        float measured = p.measureText(subtitle);
        if (measured > maxTextW) p.setTextSize(subSize * maxTextW / measured);
        c.drawText(subtitle, textX, ArcadeUi.textBaseline(p, r.centerY() + cardH * 0.16f), p);

        float cr = cardH * 0.13f;
        float cx = r.right - cardH * 0.23f;
        float cy = r.centerY();
        p.setColor(tint);
        c.drawCircle(cx, cy, cr, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f, cr * 0.2f));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(0xFF171A1F);
        path.reset();
        path.moveTo(cx - cr * 0.18f, cy - cr * 0.40f);
        path.lineTo(cx + cr * 0.24f, cy);
        path.lineTo(cx - cr * 0.18f, cy + cr * 0.40f);
        c.drawPath(path, p);
        p.setStrokeCap(Paint.Cap.BUTT);
        p.setStyle(Paint.Style.FILL);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            pressed = hit(e.getX(), e.getY());
            invalidate();
            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_UP) {
            int hit = hit(e.getX(), e.getY());
            int old = pressed;
            pressed = -1;
            invalidate();
            if (hit >= 0 && hit == old) {
                playSoundEffect(android.view.SoundEffectConstants.CLICK);
                if (hit == 0) listener.onSolo(game);
                else if (hit == 1) listener.onLocalTwoPlayer(game);
                else if (hit == 2) requestCoop();
                else if (hit == 3) listener.onModeBack();
            }
            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_CANCEL) {
            pressed = -1;
            invalidate();
        }
        return true;
    }

    private int hit(float x, float y) {
        if (soloCard.contains(x, y)) return 0;
        if (localCard.contains(x, y)) return 1;
        if (multiplayerCard.contains(x, y)) return 2;
        if (back.contains(x, y)) return 3;
        return -1;
    }
}
