package com.tiny.powerof4;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import com.tiny.arcade.ArcadeAudio;
import com.tiny.arcade.ArcadeConfetti;
import com.tiny.arcade.ArcadeGame;
import com.tiny.arcade.ArcadePrefs;
import com.tiny.arcade.ArcadeUi;
import com.tiny.arcade.CoopSession;
import java.util.Arrays;
import java.util.Random;

public final class PowerOf4View extends View implements ArcadeGame, CoopSession.Listener {
    private static final String PREFS = "tinypower4_prefs";
    private static final String PREFS_LOCAL = "tinypower4_local_prefs";
    private static final String K_SIDE = "human_side";
    private static final String K_SELECTED = "selected_side";
    private static final String K_DIFF = "difficulty";
    private static final String K_HAS = "has_save";
    private static final String K_MOVES = "move_cols";
    private static final String K_OVER = "game_over";
    private static final String K_STATUS = "status_txt";

    private static final String[] DIFF_NAMES = {"Easy", "Medium", "Hard", "Elite"};
    private static final int[] COL_ORDER = {3, 2, 4, 1, 5, 0, 6};

    private static final int COLS = 7;
    private static final int ROWS = 6;
    private static final int CELLS = COLS * ROWS;
    private static final int INF = 100000;
    private static final int WIN_SCORE = 90000;
    private static final long DROP_MS = 220L;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF tmp = new RectF();
    private final RectF btnGames = new RectF();
    private final RectF btnNew = new RectF();
    private final RectF btnUndo = new RectF();
    private final RectF btnShare = new RectF();
    private final RectF btnHint = new RectF();
    private final RectF btnContinue = new RectF();
    private final RectF btnSideA = new RectF();
    private final RectF btnSideB = new RectF();
    private final RectF btnStart = new RectF();
    private final RectF[] btnDiff = {new RectF(), new RectF(), new RectF(), new RectF()};

    private final Runnable onMenu;
    private final CoopSession coop;
    private boolean localTwoPlayer;
    private final ArcadeAudio audio = new ArcadeAudio();
    private final ArcadeConfetti confetti = new ArcadeConfetti();
    private final Random rng = new Random();

    private final int[] b = new int[CELLS];
    private final int[] moves = new int[CELLS];
    private final boolean[] winMask = new boolean[CELLS];
    private int moveCount;
    private int winCount;
    private int turn = 1;
    private int lastCol = -1;
    private int lastRow = -1;

    private int humanSide = 1;
    private int selectedSide = 1;
    private int difficulty;
    private boolean setup = true;
    private boolean hasSaved;
    private boolean thinking;
    private boolean gameOver;
    private String status = "Choose your side & bot strength";
    private String coopWinner = "";

    private volatile int searchGen;
    private volatile boolean aiAbort;
    private Thread botThread;
    private long aiDeadline;
    private int aiNodes;

    private volatile int hintGen;
    private volatile boolean hintThinking;
    private int hintCol = -1;
    private long hintUntil;

    private float cell;
    private float boardLeft;
    private float boardTop;
    private float boardW;
    private float boardH;
    private int pressedCol = -1;

    private int animDropCol = -1;
    private int animDropRow = -1;
    private int animDropSide;
    private long animDropStartMs;

    public PowerOf4View(Context ctx, Runnable onMenu) {
        this(ctx, onMenu, null, false);
    }

    public PowerOf4View(Context ctx, Runnable onMenu, CoopSession coop) {
        this(ctx, onMenu, coop, false);
    }

    public PowerOf4View(Context ctx, Runnable onMenu, CoopSession coop, boolean localTwoPlayer) {
        super(ctx);
        this.onMenu = onMenu;
        this.coop = coop;
        this.localTwoPlayer = localTwoPlayer;
        if (coop == null) {
            loadSession();
        } else {
            coop.setListener(this);
            resetState();
            setup = false;
            hasSaved = true;
            humanSide = coop.mySide();
            selectedSide = humanSide;
            status = "Connecting to opponent...";
            applyCoopState(coop.state());
        }
    }

    @Override
    public void onHide() {
        if (coop != null) coop.pause();
        else cancelBot();
        persistSessionNow();
    }

    @Override
    public void onShow() {
        if (coop != null) {
            coop.setListener(this);
            coop.start();
            applyCoopState(coop.state());
        }
    }

    @Override
    public void onAppPause() {
        if (coop != null) coop.pause();
    }

    @Override
    public synchronized void persistSessionNow() {
        if (coop != null) return;
        try {
            SharedPreferences prefs = getContext().getSharedPreferences(
                localTwoPlayer ? PREFS_LOCAL : PREFS, Context.MODE_PRIVATE);
            SharedPreferences.Editor ed = prefs.edit();
            ed.putInt(K_SELECTED, selectedSide);
            ed.putInt(K_DIFF, difficulty);
            ed.putInt(K_SIDE, humanSide);
            ed.putBoolean(K_HAS, hasSaved);

            if (hasSaved) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < moveCount; i++) {
                    if (i > 0) sb.append(',');
                    sb.append(moves[i]);
                }
                ed.putString(K_MOVES, sb.toString());
                ed.putBoolean(K_OVER, gameOver);
                ed.putString(K_STATUS, status);
            }
            ed.commit();
        } catch (Throwable ignored) {
        }
    }

    private synchronized void loadSession() {
        try {
            SharedPreferences prefs = getContext().getSharedPreferences(
                localTwoPlayer ? PREFS_LOCAL : PREFS, Context.MODE_PRIVATE);
            selectedSide = prefs.getInt(K_SELECTED, prefs.getInt(K_SIDE, 1));
            if (selectedSide != -1 && selectedSide != 1) selectedSide = 1;
            difficulty = prefs.getInt(K_DIFF, 0);
            if (difficulty < 0 || difficulty > 3) difficulty = 0;
            humanSide = prefs.getInt(K_SIDE, selectedSide);
            if (humanSide != -1 && humanSide != 1) humanSide = selectedSide;
            if (localTwoPlayer) humanSide = 1;

            resetState();
            String raw = prefs.getString(K_MOVES, "");
            if (raw != null && raw.length() > 0) {
                String[] parts = raw.split(",");
                for (String part : parts) {
                    int col;
                    try {
                        col = Integer.parseInt(part);
                    } catch (Throwable bad) {
                        break;
                    }
                    if (col < 0 || col >= COLS) break;
                    int side = turn;
                    int row = dropInColumn(b, col, side);
                    if (row < 0) break;
                    moves[moveCount++] = col;
                    lastCol = col;
                    lastRow = row;
                    turn = -turn;
                    if (markWin(b, row, col, side)) {
                        gameOver = true;
                        break;
                    }
                    if (moveCount >= CELLS) {
                        gameOver = true;
                        break;
                    }
                }
            }

            hasSaved = prefs.getBoolean(K_HAS, false) || moveCount > 0 || gameOver;
            if (gameOver) {
                boolean storedOver = prefs.getBoolean(K_OVER, true);
                if (!storedOver) gameOver = winCount > 0 || moveCount >= CELLS;
            }
            setup = true;
            String savedStatus = prefs.getString(K_STATUS, "");
            status = gameOver && savedStatus.length() > 0
                ? savedStatus : (localTwoPlayer
                    ? "Pass the device after each turn"
                    : "Choose your side & bot strength");
        } catch (Throwable ignored) {
            hasSaved = false;
            setup = true;
            resetState();
        }
    }

    private void resetState() {
        clearHint();
        animDropCol = -1;
        animDropRow = -1;
        animDropSide = 0;
        Arrays.fill(b, 0);
        Arrays.fill(winMask, false);
        moveCount = 0;
        winCount = 0;
        turn = 1;
        lastCol = -1;
        lastRow = -1;
        gameOver = false;
        thinking = false;
        pressedCol = -1;
    }

    private void startNewGame() {
        cancelBot();
        confetti.stop();
        clearHint();
        humanSide = localTwoPlayer ? 1 : selectedSide;
        setup = false;
        resetState();
        hasSaved = true;
        status = currentTurnText();
        persistSessionNow();
        invalidate();
        if (!localTwoPlayer && turn != humanSide) startBot();
    }

    private void showRematchPrompt() {
        new AlertDialog.Builder(getContext())
            .setTitle("Rematch in this room?")
            .setMessage("Both players will return to an empty board. The room code stays the same.")
            .setPositiveButton("Rematch", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    coop.requestRematch();
                }
            })
            .setNegativeButton("Exit room", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    CoopSession.clearSaved(getContext(), "power4");
                    if (onMenu != null) onMenu.run();
                }
            })
            .setNeutralButton("Cancel", null)
            .show();
    }

    private void confirmRestart() {
        if (coop != null || setup) return;
        if (getWindowToken() == null) {
            restartOfflineGame();
            return;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(getContext());
        b.setTitle("Start a new game?");
        b.setMessage("Your current match will end. Saved progress is replaced.");
        b.setPositiveButton("New Game", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                restartOfflineGame();
            }
        });
        b.setNegativeButton("Cancel", null);
        b.show();
    }

    private void restartOfflineGame() {
        cancelBot();
        confetti.stop();
        clearHint();
        if (localTwoPlayer) {
            startNewGame();
            return;
        }
        setup = true;
        status = "Choose your side & bot strength";
        invalidate();
    }

    private boolean hintAvailable() {
        return coop == null && !localTwoPlayer && !setup && !gameOver && !thinking
            && turn == humanSide && ArcadePrefs.assistantEnabled(getContext());
    }

    private void requestHint() {
        if (!hintAvailable()) return;
        final int gen = ++hintGen;
        final int[] root = b.clone();
        final int side = turn;
        hintThinking = true;
        hintCol = -1;
        status = "Looking for a move...";
        invalidate();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                final int col = chooseBotColumn(root, side, 1);
                post(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != hintGen || !hintAvailable()) {
                            hintThinking = false;
                            invalidate();
                            return;
                        }
                        hintThinking = false;
                        if (col >= 0) {
                            hintCol = col;
                            hintUntil = SystemClock.uptimeMillis() + 6000L;
                            status = "Hint: drop in the glowing column";
                        } else {
                            status = currentTurnText();
                        }
                        invalidate();
                    }
                });
            }
        }, "power4-hint");
        t.setDaemon(true);
        t.start();
    }

    private void clearHint() {
        hintGen++;
        hintThinking = false;
        hintCol = -1;
        hintUntil = 0L;
    }

    private void resumeGame() {
        if (!hasSaved) return;
        setup = false;
        pressedCol = -1;
        if (gameOver) {
            status = gameOverText();
            invalidate();
            return;
        }
        if (localTwoPlayer || coop != null || turn == humanSide) {
            status = currentTurnText();
            invalidate();
        } else {
            status = "Bot thinking...";
            invalidate();
            startBot();
        }
    }

    private String currentTurnText() {
        if (coop != null) return turn == humanSide ? humanTurnText() : "Opponent's turn...";
        if (localTwoPlayer) return (turn == 1 ? "Yellow" : "Red") + "'s turn · Drop";
        return turn == humanSide ? humanTurnText() : "Bot thinking...";
    }

    private void cancelBot() {
        searchGen++;
        aiAbort = true;
        thinking = false;
        Thread t = botThread;
        botThread = null;
        if (t != null) t.interrupt();
    }

    private void startBot() {
        if (setup || gameOver) return;
        cancelBot();
        final int gen = searchGen;
        final int side = turn;
        final int diff = difficulty;
        final int[] root = b.clone();
        aiAbort = false;
        thinking = true;
        status = "Bot thinking...";
        invalidate();

        botThread = new Thread(new BotSearch(this, gen, side, diff, root));
        botThread.start();
    }

    private void applyBotResult(int gen, int side, int col) {
        if (gen != searchGen || setup || gameOver) return;
        thinking = false;
        if (col < 0) {
            gameOver = true;
            status = "Draw · Board full";
            persistSessionNow();
            invalidate();
            return;
        }
        dropCommitted(col, side);
    }

    private static final class BotSearch implements Runnable {
        private final PowerOf4View view;
        private final int gen;
        private final int side;
        private final int diff;
        private final int[] root;

        BotSearch(PowerOf4View view, int gen, int side, int diff, int[] root) {
            this.view = view;
            this.gen = gen;
            this.side = side;
            this.diff = diff;
            this.root = root;
        }

        @Override
        public void run() {
            long started = SystemClock.uptimeMillis();
            int col = view.chooseBotColumn(root, side, diff);
            long minWait = 900L + ((System.nanoTime() >>> 8) % 350L);
            long elapsed = SystemClock.uptimeMillis() - started;
            if (elapsed < minWait) {
                try {
                    Thread.sleep(minWait - elapsed);
                } catch (InterruptedException ignored) {
                    return;
                }
            }
            view.post(new BotDone(view, gen, side, col));
        }
    }

    private static final class BotDone implements Runnable {
        private final PowerOf4View view;
        private final int gen;
        private final int side;
        private final int col;

        BotDone(PowerOf4View view, int gen, int side, int col) {
            this.view = view;
            this.gen = gen;
            this.side = side;
            this.col = col;
        }

        @Override
        public void run() {
            view.applyBotResult(gen, side, col);
        }
    }

    private int dropInColumn(int[] bd, int col, int side) {
        for (int r = ROWS - 1; r >= 0; r--) {
            int idx = r * COLS + col;
            if (bd[idx] == 0) {
                bd[idx] = side;
                return r;
            }
        }
        return -1;
    }

    private boolean isWin(int[] bd, int row, int col, int side) {
        int[][] dirs = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        for (int[] d : dirs) {
            int count = 1;
            int r = row + d[0];
            int c = col + d[1];
            while (r >= 0 && r < ROWS && c >= 0 && c < COLS
                && bd[r * COLS + c] == side) {
                count++;
                r += d[0];
                c += d[1];
            }
            r = row - d[0];
            c = col - d[1];
            while (r >= 0 && r < ROWS && c >= 0 && c < COLS
                && bd[r * COLS + c] == side) {
                count++;
                r -= d[0];
                c -= d[1];
            }
            if (count >= 4) return true;
        }
        return false;
    }

    private boolean markWin(int[] bd, int row, int col, int side) {
        int[][] dirs = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        for (int[] d : dirs) {
            int sr = row;
            int sc = col;
            while (sr - d[0] >= 0 && sr - d[0] < ROWS && sc - d[1] >= 0 && sc - d[1] < COLS
                && bd[(sr - d[0]) * COLS + sc - d[1]] == side) {
                sr -= d[0];
                sc -= d[1];
            }

            int[] line = new int[CELLS];
            int n = 0;
            int r = sr;
            int c = sc;
            while (r >= 0 && r < ROWS && c >= 0 && c < COLS
                && bd[r * COLS + c] == side) {
                line[n++] = r * COLS + c;
                r += d[0];
                c += d[1];
            }
            if (n >= 4) {
                for (int i = 0; i < n; i++) {
                    if (!winMask[line[i]]) {
                        winMask[line[i]] = true;
                        winCount++;
                    }
                }
                return true;
            }
        }
        return false;
    }

    private void dropCommitted(int col, int side) {
        clearHint();
        int row = dropInColumn(b, col, side);
        if (row < 0) return;

        moves[moveCount++] = col;
        lastCol = col;
        lastRow = row;
        turn = -turn;
        triggerDropAnimation(col, row, side);

        coopWinner = "";
        boolean won = markWin(b, row, col, side);
        if (won) {
            gameOver = true;
            if (coop != null) {
                coopWinner = side == 1 ? "1" : "-1";
                status = side == humanSide ? "You win! · Tap Exit" : "Opponent wins · Tap Exit";
            } else if (localTwoPlayer) {
                status = (side == 1 ? "Yellow" : "Red") + " wins! · Tap New Game";
            } else {
                status = side == humanSide ? "You win! · Tap New Game" : "Bot wins · Tap New Game";
            }
            if (localTwoPlayer || side == humanSide) confetti.start(getWidth(), getHeight());
            audio.play(true);
        } else if (moveCount >= CELLS) {
            gameOver = true;
            status = "Draw · Board full";
            if (coop != null) coopWinner = "0";
            audio.play(true);
        } else {
            if (coop != null) {
                status = turn == humanSide ? humanTurnText() : "Opponent's turn...";
            } else if (localTwoPlayer) {
                status = currentTurnText();
            } else {
                status = turn == humanSide ? humanTurnText() : "Bot thinking...";
            }
            audio.play(false);
        }

        hasSaved = true;
        if (coop != null) {
            sendCoopMoves(gameOver ? coopWinner : "");
            invalidate();
            return;
        }
        persistSessionNow();
        invalidate();

        if (!gameOver && !localTwoPlayer && turn != humanSide) startBot();
    }

    private void triggerDropAnimation(int col, int row, int side) {
        animDropCol = col;
        animDropRow = row;
        animDropSide = side;
        animDropStartMs = SystemClock.uptimeMillis();
        invalidate();
    }

    private void clearDropAnimation() {
        animDropCol = -1;
        animDropRow = -1;
        animDropSide = 0;
    }

    private boolean validCoopPower4(String raw, CoopSession.State s) {
        int[] test = new int[CELLS];
        int side = 1;
        int lastRow = -1;
        int lastCol = -1;
        int lastSide = 0;
        boolean lastWin = false;
        String[] parts = raw.length() == 0 ? new String[0] : raw.split(",");
        for (int i = 0; i < parts.length; i++) {
            int col;
            try {
                col = Integer.parseInt(parts[i]);
            } catch (Throwable bad) {
                return false;
            }
            if (col < 0 || col >= COLS) return false;
            int row = dropInColumn(test, col, side);
            if (row < 0) return false;
            boolean win = isWin(test, row, col, side);
            if (win && i < parts.length - 1) return false;
            lastRow = row;
            lastCol = col;
            lastSide = side;
            lastWin = win;
            side = -side;
        }
        if (s.isFinished()) {
            if ("0".equals(s.winner)) {
                if (lastWin || parts.length != CELLS) return false;
            } else if ("1".equals(s.winner) || "-1".equals(s.winner)) {
                int winnerSide = s.winner.startsWith("-") ? -1 : 1;
                if (!lastWin || lastSide != winnerSide) return false;
            }
        } else if (lastWin) {
            return false;
        }
        return true;
    }

    private void sendCoopMoves(String winner) {
        if (coop == null) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < moveCount; i++) {
            if (i > 0) sb.append(',');
            sb.append(moves[i]);
        }
        coop.sendMoves(sb.toString(), moveCount, turn, winner);
    }

    @Override
    public void onCoopState(CoopSession session, CoopSession.State s) {
        if (session != coop || s == null) return;
        applyCoopState(s);
    }

    @Override
    public void onCoopError(CoopSession session, String message) {
        if (session != coop || gameOver) return;
        status = message;
        invalidate();
    }

    private void applyCoopState(CoopSession.State s) {
        if (s == null || !s.isActive()) return;
        if (!s.isFinished() && s.ply == 0 && (s.moves == null || s.moves.length() == 0)
            && (gameOver || moveCount > 0)) {
            cancelBot();
            clearHint();
            resetState();
            coopWinner = "";
            gameOver = false;
            setup = false;
            hasSaved = true;
            status = currentTurnText();
            invalidate();
        }
        if (s.ply < moveCount && !s.isFinished()) {
            if (s.ply == moveCount - 1 && s.turn == coop.mySide()) {
                sendCoopMoves(gameOver ? coopWinner : "");
            }
            return;
        }

        String rawCheck = s.moves == null ? "" : s.moves;
        if (s.ply > moveCount && !validCoopPower4(rawCheck, s)) {
            gameOver = true;
            status = "Invalid move from opponent";
            invalidate();
            return;
        }

        if (s.ply > moveCount) {
            boolean animateLast = s.ply == moveCount + 1;
            int animCol = -1;
            int animRow = -1;
            int animSide = 0;
            resetState();
            String raw = s.moves == null ? "" : s.moves;
            if (raw.length() > 0) {
                String[] parts = raw.split(",");
                for (int i = 0; i < parts.length; i++) {
                    String part = parts[i];
                    int col;
                    try {
                        col = Integer.parseInt(part);
                    } catch (Throwable bad) {
                        break;
                    }
                    if (col < 0 || col >= COLS) break;
                    int side = turn;
                    int row = dropInColumn(b, col, side);
                    if (row < 0) break;
                    moves[moveCount++] = col;
                    lastCol = col;
                    lastRow = row;
                    if (animateLast && i == parts.length - 1) {
                        animCol = col;
                        animRow = row;
                        animSide = side;
                    }
                    turn = -turn;
                    markWin(b, row, col, side);
                }
            }
            if (animateLast && animCol >= 0 && animRow >= 0) {
                triggerDropAnimation(animCol, animRow, animSide);
            }
        }

        humanSide = coop.mySide();
        selectedSide = humanSide;
        setup = false;
        hasSaved = true;
        thinking = false;
        pressedCol = -1;
        boolean wasOver = gameOver;
        gameOver = s.isFinished();
        if (gameOver) {
            if ("0".equals(s.winner)) {
                status = "Draw";
            } else if ("1".equals(s.winner) || "-1".equals(s.winner)) {
                int winnerSide = s.winner.startsWith("-") ? -1 : 1;
                boolean youWon = winnerSide == humanSide;
                status = youWon ? "You win! · Tap Exit" : "Opponent wins · Tap Exit";
                if (!wasOver) {
                    confetti.stop();
                    if (youWon) confetti.start(getWidth(), getHeight());
                }
            }
        } else {
            turn = s.turn;
            if (!coop.opponentOnline()) {
                status = "Opponent offline — waiting to reconnect...";
            } else {
                status = turn == humanSide ? humanTurnText() : "Opponent's turn...";
            }
        }
        invalidate();
    }

    private String humanTurnText() {
        return "Your turn · Drop " + (humanSide == 1 ? "Yellow" : "Red");
    }

    private String gameOverText() {
        return status;
    }

    private void shareGame() {
        StringBuilder sb = new StringBuilder("TinyArcade Power of 4\n");
        if (moveCount > 0) {
            for (int i = 0; i < moveCount; i++) {
                if (i > 0) sb.append(' ');
                sb.append(moves[i] + 1);
            }
            sb.append('\n');
        }
        sb.append(status);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "TinyArcade Power of 4");
        send.putExtra(Intent.EXTRA_TEXT, sb.toString());
        Intent chooser = Intent.createChooser(send, "TinyArcade Power of 4");
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(chooser);
    }

    private void undoMove() {
        if (setup) return;
        clearHint();
        clearDropAnimation();
        int min = localTwoPlayer ? 0 : (humanSide == 1 ? 0 : 1);
        if (moveCount <= min) return;

        cancelBot();
        confetti.stop();
        int target = localTwoPlayer ? moveCount - 1
            : (turn == humanSide ? moveCount - 2 : moveCount - 1);
        if (target < min) target = min;
        if (target >= moveCount) return;

        rebuild(target);
        hasSaved = moveCount > 0;
        status = currentTurnText();
        persistSessionNow();
        invalidate();
    }

    private void rebuild(int count) {
        Arrays.fill(b, 0);
        Arrays.fill(winMask, false);
        winCount = 0;
        lastCol = -1;
        lastRow = -1;
        gameOver = false;
        thinking = false;

        int safe = Math.min(count, moveCount);
        for (int i = 0; i < safe; i++) {
            int col = moves[i];
            int side = (i % 2 == 0) ? 1 : -1;
            int row = dropInColumn(b, col, side);
            if (row < 0) break;
            lastCol = col;
            lastRow = row;
        }
        moveCount = safe;
        turn = (moveCount % 2 == 0) ? 1 : -1;
    }

    private int columnAt(float x, float y) {
        computeGeometry();
        if (y < boardTop || y > boardTop + boardH) return -1;
        if (x < boardLeft || x > boardLeft + boardW) return -1;
        int col = (int) ((x - boardLeft) / cell);
        if (col < 0 || col >= COLS) return -1;
        return col;
    }

    private boolean canPlay() {
        return !setup && !thinking && !gameOver
            && (localTwoPlayer || turn == humanSide)
            && (coop == null || coop.opponentOnline());
    }

    private void computeGeometry() {
        int w = getWidth();
        int h = getHeight();
        float margin = Math.min(w, h) * 0.032f;
        boardW = Math.min(w - margin * 2f, h * 0.68f);
        cell = boardW / COLS;
        boardH = cell * ROWS;
        boardLeft = (w - boardW) * 0.5f;
        boardTop = Math.max((h - boardH) * 0.5f + cell * 0.20f, cell * 1.55f);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        computeGeometry();
        float btnH = Math.min(cell * 0.78f, boardTop * 0.20f);
        float btnY = Math.max(cell * 0.18f, boardTop - btnH - cell * 0.96f);

        c.drawColor(ArcadeUi.BG);
        if (!setup && !gameOver) {
            ArcadeUi.drawTurnGlow(c, p, w, h,
                turn == 1 ? 0x30E2B33C : 0x30E2644E, turn != humanSide);
        }

        float pad = cell * 0.16f;
        float gap = cell * 0.14f;
        boolean showHint = coop == null && !localTwoPlayer && !setup && !gameOver
            && !thinking && turn == humanSide
            && ArcadePrefs.assistantEnabled(getContext());
        int buttonCount = showHint ? 5 : 4;
        float bw = (w - pad * 2f - gap * (buttonCount - 1)) / buttonCount;
        btnGames.set(pad, btnY, pad + bw, btnY + btnH);
        btnNew.set(pad + bw + gap, btnY, pad + bw * 2f + gap, btnY + btnH);
        btnUndo.set(pad + bw * 2f + gap * 2f, btnY, pad + bw * 3f + gap * 2f, btnY + btnH);
        if (showHint) {
            btnHint.set(pad + bw * 3f + gap * 3f, btnY,
                pad + bw * 4f + gap * 3f, btnY + btnH);
            btnShare.set(pad + bw * 4f + gap * 4f, btnY, w - pad, btnY + btnH);
        } else {
            btnHint.setEmpty();
            btnShare.set(pad + bw * 3f + gap * 3f, btnY, w - pad, btnY + btnH);
        }

        boolean canUndo = coop == null && !setup
            && (localTwoPlayer ? moveCount > 0 : moveCount > (humanSide == 1 ? 0 : 1));
        ArcadeUi.drawIconButton(c, p, btnGames, ArcadeUi.ICON_HOME, true, ArcadeUi.SLATE);
        ArcadeUi.drawIconButton(c, p, btnNew,
            coop != null ? ArcadeUi.ICON_CLOSE : ArcadeUi.ICON_PLUS,
            coop != null || !setup, ArcadeUi.BLUE);
        ArcadeUi.drawIconButton(c, p, btnUndo, ArcadeUi.ICON_UNDO, canUndo, ArcadeUi.BLUE);
        if (showHint) {
            ArcadeUi.drawIconButton(c, p, btnHint, ArcadeUi.ICON_BULB,
                !hintThinking, ArcadeUi.GOLD);
        }
        ArcadeUi.drawIconButton(c, p, btnShare, ArcadeUi.ICON_SHARE,
            coop != null || moveCount > 0, ArcadeUi.GREEN);

        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(cell * 0.29f);
        p.setColor(gameOver
            ? (localTwoPlayer
                ? (status.startsWith("Yellow") ? ArcadeUi.GOLD : ArcadeUi.CORAL)
                : (status.startsWith("You") ? ArcadeUi.GREEN_L : ArcadeUi.CORAL))
            : (localTwoPlayer ? ArcadeUi.TEXT : (turn == humanSide ? ArcadeUi.TEXT : ArcadeUi.MUTED)));
        c.drawText(status, w * 0.5f, boardTop - cell * 0.34f, p);
        p.setTypeface(Typeface.DEFAULT);

        drawDropHints(c);
        drawBoard(c);
        drawColumnHint(c);
        if (setup) drawSetup(c, w, cell);

        if (confetti.draw(c)) {
            postInvalidateOnAnimation();
        }
        if (coop != null && !gameOver && !coop.opponentOnline()) {
            ArcadeUi.drawCoopOffline(c, p, w, h);
        }
        if (coop != null && !gameOver) postInvalidateDelayed(1000L);
        if (animDropRow >= 0) postInvalidateOnAnimation();
    }

    private void drawDropHints(Canvas c) {
        if (!canPlay()) return;
        p.setStyle(Paint.Style.FILL);
        for (int col = 0; col < COLS; col++) {
            if (b[col] != 0) continue;
            float cx = boardLeft + cell * (col + 0.5f);
            float cy = boardTop - cell * 0.13f;
            float s = cell * (col == pressedCol ? 0.15f : 0.10f);
            path.reset();
            path.moveTo(cx - s, cy - s * 0.65f);
            path.lineTo(cx + s, cy - s * 0.65f);
            path.lineTo(cx, cy + s * 0.75f);
            path.close();
            p.setColor(col == pressedCol ? ArcadeUi.GOLD : 0x99FFFFFF);
            c.drawPath(path, p);
        }
    }

    private void drawColumnHint(Canvas c) {
        if (hintCol < 0 || hintCol >= COLS) return;
        long now = SystemClock.uptimeMillis();
        if (now > hintUntil) {
            hintCol = -1;
            return;
        }
        float pulse = 0.45f + 0.30f * (float) Math.sin(now / 150.0);
        p.setStyle(Paint.Style.FILL);
        p.setColor(ArcadeUi.GOLD);
        p.setAlpha((int) (255 * pulse));
        c.drawRoundRect(boardLeft + cell * hintCol + cell * 0.06f,
            boardTop + cell * 0.06f,
            boardLeft + cell * (hintCol + 1) - cell * 0.06f,
            boardTop + boardH - cell * 0.06f,
            cell * 0.30f, cell * 0.30f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2.5f, cell * 0.06f));
        p.setColor(0xFFF6DF8A);
        p.setAlpha(255);
        c.drawRoundRect(boardLeft + cell * hintCol + cell * 0.10f,
            boardTop + cell * 0.10f,
            boardLeft + cell * (hintCol + 1) - cell * 0.10f,
            boardTop + boardH - cell * 0.10f,
            cell * 0.26f, cell * 0.26f, p);
        p.setStyle(Paint.Style.FILL);
        postInvalidateDelayed(70L);
    }

    private void drawBoard(Canvas c) {
        float frame = cell * 0.12f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x66000000);
        c.drawRoundRect(boardLeft - frame + 2f, boardTop - frame + 6f,
            boardLeft + boardW + frame + 2f, boardTop + boardH + frame + 6f,
            cell * 0.18f, cell * 0.18f, p);

        p.setColor(0xFF1D4E78);
        c.drawRoundRect(boardLeft - frame, boardTop - frame,
            boardLeft + boardW + frame, boardTop + boardH + frame,
            cell * 0.18f, cell * 0.18f, p);

        if (pressedCol >= 0 && canPlay() && b[pressedCol] == 0) {
            p.setColor(0x22FFFFFF);
            c.drawRect(boardLeft + pressedCol * cell, boardTop,
                boardLeft + (pressedCol + 1) * cell, boardTop + boardH, p);
        }

        for (int r = 0; r < ROWS; r++) {
            for (int col = 0; col < COLS; col++) {
                float cx = boardLeft + cell * (col + 0.5f);
                float cy = boardTop + cell * (r + 0.5f);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF0D1822);
                c.drawCircle(cx, cy, cell * 0.405f, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(1.5f, cell * 0.018f));
                p.setColor(0x66203042);
                c.drawCircle(cx, cy, cell * 0.405f, p);
            }
        }

        boolean animating = animDropRow >= 0 && animDropCol >= 0 && animDropSide != 0;
        if (animating) {
            long now = SystemClock.uptimeMillis();
            if (now - animDropStartMs >= DROP_MS) {
                clearDropAnimation();
                animating = false;
            }
        }
        int animHidden = animating ? animDropRow * COLS + animDropCol : -1;
        for (int idx = 0; idx < CELLS; idx++) {
            int side = b[idx];
            if (side == 0) continue;
            if (idx == animHidden) continue;
            int r = idx / COLS;
            int col = idx % COLS;
            float cx = boardLeft + cell * (col + 0.5f);
            float cy = boardTop + cell * (r + 0.5f);
            drawDisc(c, cx, cy, side, idx == lastRow * COLS + lastCol);
            if (winMask[idx]) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(2.5f, cell * 0.055f));
                p.setColor(0xFFFFFFFF);
                c.drawCircle(cx, cy, cell * 0.30f, p);
                p.setStrokeWidth(Math.max(2f, cell * 0.025f));
                p.setColor(ArcadeUi.GOLD);
                c.drawCircle(cx, cy, cell * 0.38f, p);
            }
        }
        if (animating) {
            float raw = (SystemClock.uptimeMillis() - animDropStartMs) / (float) DROP_MS;
            if (raw < 0f) raw = 0f;
            if (raw > 1f) raw = 1f;
            float ease = raw * raw;
            float cx = boardLeft + cell * (animDropCol + 0.5f);
            float startY = boardTop - cell * 1.25f;
            float endY = boardTop + cell * (animDropRow + 0.5f);
            float cy = startY + (endY - startY) * ease;
            drawDisc(c, cx, cy, animDropSide, false);
        }
        p.setStyle(Paint.Style.FILL);
    }

    private void drawDisc(Canvas c, float cx, float cy, int side, boolean last) {
        int outer = side == 1 ? 0xFFF2C94C : 0xFFE2644E;
        int inner = side == 1 ? 0xFFD9A62E : 0xFFB83E2C;
        int shine = side == 1 ? 0x88FFF7D6 : 0x88FFD8D0;

        p.setStyle(Paint.Style.FILL);
        p.setColor(0x55000000);
        c.drawCircle(cx + cell * 0.025f, cy + cell * 0.055f, cell * 0.365f, p);

        p.setColor(outer);
        c.drawCircle(cx, cy, cell * 0.365f, p);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.5f, cell * 0.022f));
        p.setColor(inner);
        c.drawCircle(cx, cy, cell * 0.325f, p);

        p.setStyle(Paint.Style.FILL);
        p.setColor(inner);
        c.drawCircle(cx, cy, cell * 0.245f, p);
        p.setColor(shine);
        c.drawCircle(cx - cell * 0.09f, cy - cell * 0.10f, cell * 0.085f, p);

        if (last) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(2f, cell * 0.028f));
            p.setColor(0xCCFFFFFF);
            c.drawCircle(cx, cy, cell * 0.355f, p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    private void drawSetup(Canvas c, int w, float cell) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xD6111111);
        c.drawRect(boardLeft - cell * 0.32f, boardTop - cell * 0.16f,
            boardLeft + boardW + cell * 0.32f, boardTop + boardH, p);

        float cardW = Math.min(w * 0.88f, boardW + cell * 0.64f);
        float cardH = cell * (localTwoPlayer ? (hasSaved ? 4.15f : 2.85f)
            : (hasSaved ? 6.45f : 5.45f));
        float cx = (w - cardW) * 0.5f;
        float cy = boardTop + (boardH - cardH) * 0.5f;

        p.setColor(0xFF23262C);
        c.drawRoundRect(new RectF(cx, cy, cx + cardW, cy + cardH), 26f, 26f, p);

        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(ArcadeUi.TEXT);
        p.setTextSize(cell * 0.50f);
        c.drawText("Power of 4", w * 0.5f, cy + cell * 0.62f, p);

        float dotY = cy + cell * 0.94f;
        float dotGap = cell * 0.20f;
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 4; i++) {
            p.setColor((i & 1) == 0 ? ArcadeUi.GOLD : ArcadeUi.CORAL);
            c.drawCircle(w * 0.5f + (i - 1.5f) * dotGap, dotY, cell * 0.075f, p);
        }

        float bPad = cell * 0.30f;
        float curY = cy + cell * 1.20f;

        if (localTwoPlayer) {
            btnSideA.setEmpty();
            btnSideB.setEmpty();
            for (int i = 0; i < 4; i++) btnDiff[i].setEmpty();

            if (hasSaved) {
                float contH = cell * 0.86f;
                btnContinue.set(cx + bPad, curY, cx + cardW - bPad, curY + contH);
                drawButton(c, btnContinue,
                    gameOver ? "View Final Board" : "Continue game", true, ArcadeUi.GREEN);
                curY += contH + cell * 0.32f;
            } else {
                btnContinue.setEmpty();
            }

            p.setTypeface(Typeface.DEFAULT);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(ArcadeUi.MUTED);
            p.setTextSize(cell * 0.30f);
            c.drawText("2 players · pass the device after each turn",
                w * 0.5f, curY + cell * 0.28f, p);
            curY += cell * 0.66f;

            float startH = cell * 0.94f;
            btnStart.set(cx + bPad, curY, cx + cardW - bPad, curY + startH);
            drawButton(c, btnStart, "Start Co-op Game", true, ArcadeUi.ORANGE);
            return;
        }

        if (hasSaved) {
            float contH = cell * 0.86f;
            btnContinue.set(cx + bPad, curY, cx + cardW - bPad, curY + contH);
            String side = humanSide == 1 ? "Yellow" : "Red";
            String contLabel = gameOver
                ? "View Final Board"
                : "Continue (" + side + " · " + DIFF_NAMES[difficulty] + ")";
            drawButton(c, btnContinue, contLabel, true, ArcadeUi.GREEN);
            curY += contH + cell * 0.25f;
        } else {
            btnContinue.setEmpty();
        }

        drawSectionLabel(c, "YOUR SIDE", cx + bPad, curY + cell * 0.22f, cell);
        curY += cell * 0.34f;

        float sideBtnW = (cardW - bPad * 3f) / 2f;
        float sideBtnH = cell * 0.92f;
        btnSideA.set(cx + bPad, curY, cx + bPad + sideBtnW, curY + sideBtnH);
        btnSideB.set(cx + bPad * 2f + sideBtnW, curY, cx + cardW - bPad, curY + sideBtnH);
        drawSelectableButton(c, btnSideA, "Yellow", selectedSide == 1, ArcadeUi.GOLD);
        drawSelectableButton(c, btnSideB, "Red", selectedSide == -1, ArcadeUi.CORAL);
        curY += sideBtnH + cell * 0.25f;

        drawSectionLabel(c, "LEVEL", cx + bPad, curY + cell * 0.22f, cell);
        curY += cell * 0.34f;

        float gap = cell * 0.14f;
        float diffW = (cardW - bPad * 2f - gap * 3f) / 4f;
        float diffH = cell * 0.82f;
        for (int i = 0; i < 4; i++) {
            float dx = cx + bPad + i * (diffW + gap);
            btnDiff[i].set(dx, curY, dx + diffW, curY + diffH);
            drawSelectableButton(c, btnDiff[i], DIFF_NAMES[i], difficulty == i, ArcadeUi.BLUE);
        }
        curY += diffH + cell * 0.28f;

        float startH = cell * 0.94f;
        btnStart.set(cx + bPad, curY, cx + cardW - bPad, curY + startH);
        drawButton(c, btnStart, "Play New Session", true, ArcadeUi.ORANGE);
    }

    private void drawSectionLabel(Canvas c, String label, float x, float baseline, float cell) {
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.LEFT);
        p.setColor(ArcadeUi.MUTED);
        p.setTextSize(cell * 0.26f);
        c.drawText(label, x, baseline, p);
    }

    private void drawButton(Canvas c, RectF r, String label, boolean enabled, int color) {
        ArcadeUi.drawButton(c, p, r, label, enabled, color);
    }

    private void drawSelectableButton(Canvas c, RectF r, String label,
                                      boolean selected, int color) {
        ArcadeUi.drawSelectableButton(c, p, r, label, selected, color);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getAction();

        if (action == MotionEvent.ACTION_CANCEL) {
            pressedCol = -1;
            invalidate();
            return true;
        }

        if (action == MotionEvent.ACTION_UP) {
            if (pressedCol >= 0) {
                int old = pressedCol;
                pressedCol = -1;
                int col = columnAt(e.getX(), e.getY());
                if (col == old && canPlay() && b[col] == 0) {
                    dropCommitted(col, turn);
                } else {
                    invalidate();
                }
                return true;
            }
            return true;
        }

        if (action != MotionEvent.ACTION_DOWN) return true;

        float tx = e.getX();
        float ty = e.getY();

        if (btnGames.contains(tx, ty)) {
            if (onMenu != null) onMenu.run();
            return true;
        }

        if (setup) {
            if (hasSaved && btnContinue.contains(tx, ty)) {
                resumeGame();
                return true;
            }
            if (!localTwoPlayer && btnSideA.contains(tx, ty)) {
                selectedSide = 1;
                persistSessionNow();
                invalidate();
                return true;
            }
            if (!localTwoPlayer && btnSideB.contains(tx, ty)) {
                selectedSide = -1;
                persistSessionNow();
                invalidate();
                return true;
            }
            if (!localTwoPlayer) {
                for (int i = 0; i < 4; i++) {
                    if (btnDiff[i].contains(tx, ty)) {
                        difficulty = i;
                        persistSessionNow();
                        invalidate();
                        return true;
                    }
                }
            }
            if (btnStart.contains(tx, ty)) {
                startNewGame();
                return true;
            }
            return true;
        }

        if (btnNew.contains(tx, ty)) {
            if (coop != null) {
                if (gameOver && coop.state() != null && coop.state().isFinished()) {
                    showRematchPrompt();
                } else if (onMenu != null) {
                    onMenu.run();
                }
                return true;
            }
            confirmRestart();
            return true;
        }
        if (btnUndo.contains(tx, ty)) {
            if (coop == null) undoMove();
            return true;
        }
        if (btnHint.contains(tx, ty)) {
            requestHint();
            return true;
        }
        if (btnShare.contains(tx, ty)) {
            if (coop != null || moveCount > 0) shareGame();
            return true;
        }

        if (!canPlay()) return true;

        int col = columnAt(tx, ty);
        if (col >= 0 && b[col] == 0) {
            pressedCol = col;
            invalidate();
        }
        return true;
    }

    private int chooseBotColumn(int[] root, int side, int diff) {
        int[] legal = new int[COLS];
        int count = 0;
        for (int i = 0; i < COLS; i++) {
            if (root[i] == 0) legal[count++] = i;
        }
        if (count == 0) return -1;
        if (diff == 0 && rng.nextFloat() < 0.34f) {
            return legal[rng.nextInt(count)];
        }

        int maxDepth = diff == 0 ? 1 : (diff == 1 ? 3 : (diff == 2 ? 5 : 7));
        long budget = diff == 0 ? 100L : (diff == 1 ? 260L : (diff == 2 ? 560L : 950L));
        aiDeadline = System.currentTimeMillis() + budget;
        aiAbort = false;
        aiNodes = 0;

        int bestOverall = legal[0];
        for (int depth = 1; depth <= maxDepth; depth++) {
            int bestScore = -INF;
            int bestCol = -1;
            for (int i = 0; i < COLS; i++) {
                int col = COL_ORDER[i];
                if (root[col] != 0) continue;
                int[] nb = root.clone();
                int row = dropInColumn(nb, col, side);
                if (row < 0) continue;
                int score;
                if (isWin(nb, row, col, side)) {
                    score = WIN_SCORE;
                } else {
                    score = -negamax(nb, -side, depth - 1, -INF, INF);
                }
                if (score > bestScore) {
                    bestScore = score;
                    bestCol = col;
                }
                if (aiAbort) break;
            }
            if (bestCol >= 0 && (!aiAbort || depth == 1)) bestOverall = bestCol;
            if (aiAbort && depth > 1) break;
            if (bestScore >= WIN_SCORE - 1000) break;
        }
        return bestOverall;
    }

    private int negamax(int[] bd, int side, int depth, int alpha, int beta) {
        if ((++aiNodes & 255) == 0 && System.currentTimeMillis() > aiDeadline) aiAbort = true;
        if (aiAbort) return 0;

        boolean any = false;
        for (int i = 0; i < COLS; i++) {
            if (bd[i] == 0) {
                any = true;
                break;
            }
        }
        if (!any) return 0;

        if (depth <= 0) return evaluate(bd, side);

        int best = -INF;
        for (int i = 0; i < COLS; i++) {
            int col = COL_ORDER[i];
            if (bd[col] != 0) continue;
            int[] nb = bd.clone();
            int row = dropInColumn(nb, col, side);
            if (row < 0) continue;
            int score;
            if (isWin(nb, row, col, side)) {
                score = WIN_SCORE + depth;
            } else {
                score = -negamax(nb, -side, depth - 1, -beta, -alpha);
            }
            if (score > best) best = score;
            if (best > alpha) alpha = best;
            if (alpha >= beta) break;
            if (aiAbort) break;
        }
        return best;
    }

    private int evaluate(int[] bd, int side) {
        int score = 0;

        for (int r = 0; r < ROWS; r++) {
            int pc = bd[r * COLS + 3];
            if (pc == side) score += 5;
            else if (pc == -side) score -= 5;
        }

        for (int r = 0; r < ROWS; r++) {
            for (int col = 0; col < COLS - 3; col++) {
                score += window(bd, r * COLS + col, 1, side);
            }
        }
        for (int col = 0; col < COLS; col++) {
            for (int r = 0; r < ROWS - 3; r++) {
                score += window(bd, r * COLS + col, COLS, side);
            }
        }
        for (int r = 0; r < ROWS - 3; r++) {
            for (int col = 0; col < COLS - 3; col++) {
                score += window(bd, r * COLS + col, COLS + 1, side);
            }
        }
        for (int r = 0; r < ROWS - 3; r++) {
            for (int col = 3; col < COLS; col++) {
                score += window(bd, r * COLS + col, COLS - 1, side);
            }
        }
        return score;
    }

    private int window(int[] bd, int start, int step, int side) {
        int mine = 0;
        int opp = 0;
        int empty = 0;
        for (int i = 0; i < 4; i++) {
            int pc = bd[start + i * step];
            if (pc == side) mine++;
            else if (pc == -side) opp++;
            else empty++;
        }
        if (mine > 0 && opp > 0) return 0;
        if (mine == 4) return 100000;
        if (opp == 4) return -100000;
        if (mine == 3 && empty == 1) return 55;
        if (mine == 2 && empty == 2) return 8;
        if (mine == 1 && empty == 3) return 1;
        if (opp == 3 && empty == 1) return -60;
        if (opp == 2 && empty == 2) return -9;
        if (opp == 1 && empty == 3) return -1;
        return 0;
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelBot();
        if (coop != null) coop.pause();
        audio.release();
        super.onDetachedFromWindow();
    }
}
