package com.tiny.arcade;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.os.SystemClock;
import android.text.InputFilter;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;

public final class CoopLobbyView extends View implements ArcadeGame, CoopSession.Listener {
    private static final long COPY_FEEDBACK_MS = 2400L;

    public interface Listener {
        void onCoopReady(CoopSession session);

        void onCoopBack();
    }

    private final int game;
    private final Listener listener;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF btnHost = new RectF();
    private final RectF btnJoin = new RectF();
    private final RectF btnShare = new RectF();
    private final RectF btnCancel = new RectF();
    private final RectF btnReconnect = new RectF();
    private final RectF btnSideA = new RectF();
    private final RectF btnSideB = new RectF();
    private final RectF btnBack = new RectF();
    private final RectF btnCopy = new RectF();
    private final RectF codeHit = new RectF();
    private CoopSession.Saved saved;

    private Shader bg;
    private Shader glow;
    private CoopSession session;
    private CoopSession.State state;
    private int hostColor = 1;
    private String error = "";
    private boolean connecting;
    private boolean readyFired;
    private boolean handedOff;
    private boolean joinMode;
    private int pressed = -1;
    private float contentW;
    private boolean copied;
    private long copiedAt;

    public CoopLobbyView(Context context, int game, Listener listener) {
        super(context);
        this.game = game;
        this.listener = listener;
        this.saved = CoopSession.loadSaved(context, gameKey(game));
        setBackgroundColor(ArcadeUi.BG);
    }

    public void reconnectSaved() {
        if (saved != null) startReconnect();
    }

    public static String gameKey(int game) {
        if (game == MainMenuView.GAME_CHESS) return "chess";
        if (game == MainMenuView.GAME_CHECKERS) return "checkers";
        return "power4";
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

    private String[] sideNames() {
        if (game == MainMenuView.GAME_CHESS) return new String[]{"White", "Black"};
        if (game == MainMenuView.GAME_CHECKERS) return new String[]{"Cream", "Crimson"};
        return new String[]{"Yellow", "Red"};
    }

    @Override
    public void onHide() {
        if (!handedOff && session != null) session.pause();
    }

    @Override
    public void onShow() {
        if (!handedOff && session != null) session.start();
    }

    @Override
    public void onAppPause() {
        if (!handedOff && session != null) session.pause();
    }

    @Override
    public void persistSessionNow() {
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        contentW = Math.min(w * 0.86f, h * 0.62f);
        float left = (w - contentW) * 0.5f;
        float top = h * 0.49f;
        float cardH = Math.min(contentW * 0.28f, h * 0.145f);
        btnHost.set(left, top, left + contentW, top + cardH);
        btnJoin.set(left, top + cardH + cardH * 0.16f,
            left + contentW, top + cardH * 2f + cardH * 0.16f);
        btnShare.set(left, h * 0.60f, left + contentW, h * 0.60f + cardH * 0.72f);
        btnCancel.set(left, h * 0.60f + cardH * 0.90f, left + contentW,
            h * 0.60f + cardH * 0.90f + cardH * 0.58f);
        btnReconnect.set(left, btnJoin.bottom + cardH * 0.22f, left + contentW,
            btnJoin.bottom + cardH * 0.22f + cardH * 0.48f);
        float sw = (contentW - cardH * 0.18f) * 0.5f;
        float sy = btnHost.top - cardH * 0.62f;
        btnSideA.set(left, sy, left + sw, sy + cardH * 0.42f);
        btnSideB.set(left + sw + cardH * 0.18f, sy, left + contentW, sy + cardH * 0.42f);
        btnBack.set(left, h * 0.075f, left + contentW * 0.30f, h * 0.075f + cardH * 0.48f);

        bg = new LinearGradient(0f, 0f, 0f, h, 0xFF12151A, 0xFF090B0E, Shader.TileMode.CLAMP);
        glow = new RadialGradient(w * 0.18f, h * 0.08f, w * 0.80f,
            0x26A78BFA, 0x00000000, Shader.TileMode.CLAMP);
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
        c.drawText("MULTIPLAYER · " + gameName().toUpperCase(), (w - contentW) * 0.5f, h * 0.25f, p);

        p.setColor(ArcadeUi.TEXT);
        p.setTextSize(contentW * 0.105f);
        c.drawText("Play together", (w - contentW) * 0.5f, h * 0.33f, p);

        if (session == null) {
            roomChooser(c, w, h);
        } else {
            connectingRoom(c, w, h);
        }

        if (error.length() > 0) {
            p.setTypeface(Typeface.DEFAULT);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(ArcadeUi.CORAL);
            p.setTextSize(contentW * 0.036f);
            c.drawText(error, w * 0.5f, h * 0.94f, p);
        }

        ArcadeUi.drawIconButton(c, p, btnBack, ArcadeUi.ICON_BACK, true, ArcadeUi.SLATE);
        if (session != null) postInvalidateOnAnimation();
    }

    private void roomChooser(Canvas c, int w, int h) {
        String[] sides = sideNames();
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.LEFT);
        p.setColor(ArcadeUi.MUTED);
        p.setTextSize(contentW * 0.034f);
        c.drawText("YOUR SIDE AS HOST", btnHost.left, btnSideA.top - contentW * 0.022f, p);
        ArcadeUi.drawSelectableButton(c, p, btnSideA, sides[0], hostColor == 1, ArcadeUi.GREEN);
        ArcadeUi.drawSelectableButton(c, p, btnSideB, sides[1], hostColor == -1, ArcadeUi.GREEN);

        drawRoomCard(c, btnHost, ArcadeUi.ICON_HOUSE_PLUS, "Host a Room",
            "Create a code", ArcadeUi.BLUE, 0);
        drawRoomCard(c, btnJoin, ArcadeUi.ICON_DOOR, "Join a Room",
            "Enter a friend's code", ArcadeUi.GOLD, 1);

        if (saved != null) {
            ArcadeUi.drawButton(c, p, btnReconnect, "Reconnect " + saved.code,
                !connecting, ArcadeUi.GREEN);
        }
    }

    private void connectingRoom(Canvas c, int w, int h) {
        CoopSession.State s = state;
        String code = session.code();
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(ArcadeUi.MUTED);
        p.setTextSize(contentW * 0.036f);
        c.drawText(joinMode ? "JOINING ROOM" : "ROOM CODE", w * 0.5f, h * 0.38f, p);

        p.setTextSize(contentW * 0.155f);
        float codeBaseline = h * 0.47f;
        float codeW = p.measureText(code);
        float copyS = contentW * 0.105f;
        btnCopy.set(w * 0.5f + codeW * 0.5f + contentW * 0.035f,
            codeBaseline - copyS * 0.72f,
            w * 0.5f + codeW * 0.5f + contentW * 0.035f + copyS,
            codeBaseline + copyS * 0.28f);
        codeHit.set(w * 0.5f - codeW * 0.5f, codeBaseline - copyS * 0.72f,
            btnCopy.right, codeBaseline + copyS * 0.28f);

        long now = SystemClock.uptimeMillis();
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 420.0);
        int accent = accent();
        Paint.FontMetrics fm = p.getFontMetrics();
        RectF glow = new RectF(w * 0.5f - codeW * 0.5f,
            codeBaseline + fm.ascent, w * 0.5f + codeW * 0.5f,
            codeBaseline + fm.descent);
        RectF glowOuter = new RectF(glow);
        glow.inset(-contentW * 0.010f, -contentW * 0.008f);
        glowOuter.inset(-contentW * 0.022f, -contentW * 0.018f);
        p.setStyle(Paint.Style.FILL);
        p.setColor((accent & 0x00FFFFFF) | (((int) (10 + 14 * pulse)) << 24));
        c.drawRoundRect(glowOuter, contentW * 0.034f, contentW * 0.034f, p);
        p.setColor((accent & 0x00FFFFFF) | (((int) (24 + 28 * pulse)) << 24));
        c.drawRoundRect(glow, contentW * 0.026f, contentW * 0.026f, p);

        p.setStyle(Paint.Style.FILL);
        p.setColor(ArcadeUi.TEXT);
        c.drawText(code, w * 0.5f, codeBaseline, p);
        if (copied && now - copiedAt >= COPY_FEEDBACK_MS) copied = false;
        ArcadeUi.drawIconButton(c, p, btnCopy,
            copied ? ArcadeUi.ICON_CHECK : ArcadeUi.ICON_COPY, true,
            copied ? ArcadeUi.GREEN : ArcadeUi.SLATE);
        if (copied) {
            long age = now - copiedAt;
            float fade = 1f;
            long fadeStart = COPY_FEEDBACK_MS - 700L;
            if (age > fadeStart) fade = Math.max(0f, 1f - (age - fadeStart) / 700f);
            int alpha = (int) (255f * fade);
            float rr = contentW * 0.044f;
            float px = btnCopy.right + rr + contentW * 0.025f;
            float py = btnCopy.centerY();
            p.setStyle(Paint.Style.FILL);
            p.setColor((ArcadeUi.GREEN & 0x00FFFFFF) | (alpha << 24));
            c.drawCircle(px, py, rr, p);
            ArcadeUi.drawIcon(c, p, px, py, rr * 1.05f, ArcadeUi.ICON_CHECK,
                (alpha << 24) | 0x00FFFFFF);
        }

        p.setTypeface(Typeface.DEFAULT);
        p.setColor(ArcadeUi.MUTED);
        p.setTextSize(contentW * 0.038f);
        String line;
        if (s != null && s.isActive()) {
            line = "Opponent connected. Starting game...";
        } else if (s != null && s.isWaiting() && !joinMode) {
            if (!s.hasGuest()) {
                line = "Waiting for your friend...";
            } else if (session.opponentOnline()) {
                line = "Opponent joined. Starting game...";
            } else {
                line = "Opponent offline — waiting to reconnect...";
            }
        } else if (s != null && s.isWaiting() && joinMode) {
            line = "Waiting for the host to start...";
        } else if (connecting) {
            line = joinMode ? "Connecting to room..." : "Creating room...";
        } else {
            line = "Preparing room...";
        }
        c.drawText(line, w * 0.5f, h * 0.54f, p);

        ArcadeUi.drawIconButton(c, p, btnShare, ArcadeUi.ICON_SHARE, !joinMode, ArcadeUi.BLUE);
        ArcadeUi.drawIconButton(c, p, btnCancel, ArcadeUi.ICON_CLOSE, true, ArcadeUi.CORAL);
    }

    private void drawRoomCard(Canvas c, RectF r, int icon, String title, String subtitle,
                              int color, int index) {
        float rad = r.height() * 0.16f;
        float pad = r.height() * 0.20f;
        boolean down = pressed == index;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x55000000);
        c.drawRoundRect(r.left + 2f, r.top + 5f, r.right + 2f, r.bottom + 5f, rad, rad, p);
        p.setColor(down ? ArcadeUi.CARD_PRESSED : ArcadeUi.CARD);
        c.drawRoundRect(r, rad, rad, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(down ? 2.4f : 1.3f);
        p.setColor((color & 0x00FFFFFF) | 0xAA000000);
        c.drawRoundRect(r, rad, rad, p);
        p.setStyle(Paint.Style.FILL);

        float iconSize = r.height() * 0.54f;
        float ix = r.left + pad;
        float iy = r.top + (r.height() - iconSize) * 0.5f;
        RectF tile = new RectF(ix, iy, ix + iconSize, iy + iconSize);
        p.setColor(0xFF171A1F);
        c.drawRoundRect(tile, iconSize * 0.25f, iconSize * 0.25f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.4f);
        p.setColor((color & 0x00FFFFFF) | 0x66000000);
        c.drawRoundRect(tile, iconSize * 0.25f, iconSize * 0.25f, p);
        p.setStyle(Paint.Style.FILL);
        ArcadeUi.drawIcon(c, p, ix + iconSize * 0.5f, iy + iconSize * 0.5f,
            iconSize * 0.58f, icon, color);

        float textX = ix + iconSize + r.height() * 0.18f;
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.LEFT);
        p.setColor(ArcadeUi.TEXT);
        p.setTextSize(r.height() * 0.20f);
        c.drawText(title, textX, r.top + r.height() * 0.48f, p);

        p.setTypeface(Typeface.DEFAULT);
        p.setColor(ArcadeUi.MUTED);
        p.setTextSize(r.height() * 0.095f);
        c.drawText(subtitle, textX, r.top + r.height() * 0.73f, p);

        float cr = r.height() * 0.13f;
        float cx = r.right - pad - cr;
        float cy = r.centerY();
        p.setColor(color);
        c.drawCircle(cx, cy, cr, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f, cr * 0.2f));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(0xFF171A1F);
        Path path = new Path();
        path.moveTo(cx - cr * 0.18f, cy - cr * 0.40f);
        path.lineTo(cx + cr * 0.24f, cy);
        path.lineTo(cx - cr * 0.18f, cy + cr * 0.40f);
        c.drawPath(path, p);
        p.setStrokeCap(Paint.Cap.BUTT);
        p.setStyle(Paint.Style.FILL);
    }

    @Override
    public void onCoopState(CoopSession session, CoopSession.State state) {
        if (session != this.session) return;
        this.state = state;
        this.connecting = false;
        this.error = "";
        if (state.isActive() && !readyFired) {
            readyFired = true;
            handedOff = true;
            listener.onCoopReady(session);
            return;
        }
        invalidate();
    }

    @Override
    public void onCoopError(CoopSession session, String message) {
        if (session != this.session) return;
        this.error = message;
        this.connecting = false;
        invalidate();
    }

    private void startHost() {
        error = "";
        copied = false;
        connecting = true;
        joinMode = false;
        readyFired = false;
        handedOff = false;
        if (session != null) session.close();
        session = new CoopSession(getContext(), gameKey(game), true, null, hostColor, this);
        session.start();
        invalidate();
    }

    private void askJoinCode() {
        final EditText input = new EditText(getContext());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        input.setHint("ABC234");
        new AlertDialog.Builder(getContext())
            .setTitle("Join a multiplayer room")
            .setMessage("Enter the 6-character code from your friend.")
            .setView(input)
            .setPositiveButton("Join", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String text = input.getText().toString().trim().toUpperCase();
                    if (!text.matches("[A-HJ-NP-Z2-9]{6}")) {
                        error = "That code is not valid.";
                        invalidate();
                        return;
                    }
                    startJoin(text);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void startJoin(String code) {
        error = "";
        copied = false;
        connecting = true;
        joinMode = true;
        readyFired = false;
        handedOff = false;
        if (session != null) session.close();
        session = new CoopSession(getContext(), gameKey(game), false, code, 1, this);
        session.start();
        invalidate();
    }

    private void startReconnect() {
        if (saved == null) return;
        error = "";
        copied = false;
        connecting = true;
        joinMode = !saved.host;
        readyFired = false;
        handedOff = false;
        if (session != null) session.close();
        session = new CoopSession(getContext(), gameKey(game), saved.host, saved.code,
            saved.hostColor, this);
        session.start();
        invalidate();
    }

    private void cancelRoom() {
        if (session != null) {
            session.cancelRoom();
            session = null;
        }
        CoopSession.clearSaved(getContext(), gameKey(game));
        saved = null;
        state = null;
        connecting = false;
        copied = false;
        error = "";
        readyFired = false;
        joinMode = false;
        invalidate();
    }

    private void copyCode() {
        if (session == null) return;
        ClipboardManager cm = (ClipboardManager) getContext()
            .getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("TinyArcade room code", session.code()));
        }
        copied = true;
        copiedAt = SystemClock.uptimeMillis();
        invalidate();
    }

    private void shareCode() {
        if (session == null) return;
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "TinyArcade " + gameName() + " multiplayer");
        send.putExtra(Intent.EXTRA_TEXT,
            "Join my TinyArcade " + gameName() + " room. Code: " + session.code());
        Intent chooser = Intent.createChooser(send, "Share room code");
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(chooser);
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
                if (hit == 0) startHost();
                else if (hit == 1) askJoinCode();
                else if (hit == 2) startReconnect();
                else if (hit == 3) shareCode();
                else if (hit == 4) cancelRoom();
                else if (hit == 5) hostColor = 1;
                else if (hit == 6) hostColor = -1;
                else if (hit == 7) {
                    if (session != null) session.pause();
                    listener.onCoopBack();
                } else if (hit == 8) {
                    copyCode();
                }
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
        if (btnBack.contains(x, y)) return 7;
        if (session == null) {
            if (btnHost.contains(x, y)) return 0;
            if (btnJoin.contains(x, y)) return 1;
            if (saved != null && btnReconnect.contains(x, y)) return 2;
            if (btnSideA.contains(x, y)) return 5;
            if (btnSideB.contains(x, y)) return 6;
        } else {
            if (!joinMode && btnShare.contains(x, y)) return 3;
            if (btnCancel.contains(x, y)) return 4;
            if (codeHit.contains(x, y)) return 8;
        }
        return -1;
    }
}
