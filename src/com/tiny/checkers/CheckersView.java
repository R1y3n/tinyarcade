package com.tiny.checkers;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

public final class CheckersView extends View implements ArcadeGame, CoopSession.Listener {
    private static final String PREFS = "tinycheckers_prefs";
    private static final String PREFS_LOCAL = "tinycheckers_local_prefs";
    private static final String K_SIDE = "human_side";
    private static final String K_SELECTED = "selected_side";
    private static final String K_DIFF = "difficulty";
    private static final String K_HAS = "has_save";
    private static final String K_LOG = "move_log";
    private static final String K_OVER = "game_over";
    private static final String K_STATUS = "status_txt";

    private static final String[] DIFF_NAMES = {"Easy", "Medium", "Hard", "Elite"};
    private static final int MAX_TURNS = 320;
    private static final int INF = 100000;
    private static final int WIN_SCORE = 90000;
    private static final long ANIM_MS = 180L;
    private static final int[] DR = {-1, -1, 1, 1};
    private static final int[] DC = {-1, 1, -1, 1};

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

    private final int[] b = new int[64];
    private final int[][] histBoard = new int[MAX_TURNS][64];
    private final int[] histTurn = new int[MAX_TURNS];
    private final int[] histNoCap = new int[MAX_TURNS];
    private final int[] histToken = new int[MAX_TURNS];
    private final int[] histLastFrom = new int[MAX_TURNS];
    private final int[] histLastTo = new int[MAX_TURNS];
    private int turnCount;

    private final ArrayList<int[]> log = new ArrayList<int[]>();
    private final ArrayList<int[]> candidates = new ArrayList<int[]>();
    private final int[] targetSquares = new int[32];
    private final boolean[] targetCapture = new boolean[32];
    private int targetCount;

    private int humanSide = 1;
    private int selectedSide = 1;
    private int difficulty;
    private int turn = 1;
    private int noCap;
    private int sel = -1;
    private int lastFrom = -1;
    private int lastTo = -1;
    private int hopIndex;
    private boolean setup = true;
    private boolean hasSaved;
    private boolean thinking;
    private boolean gameOver;
    private boolean pendingJump;
    private boolean turnHadCapture;
    private String status = "Choose your side & bot strength";
    private String coopWinner = "";

    private final int[] animPath = new int[24];
    private int animPathLen;
    private int animSeg;
    private int animPiece;
    private long animStartMs;

    private volatile int searchGen;
    private volatile boolean aiAbort;
    private Thread botThread;
    private long aiDeadline;
    private int aiNodes;

    private volatile int hintGen;
    private volatile boolean hintThinking;
    private int hintFrom = -1;
    private int hintTo = -1;
    private long hintUntil;

    public CheckersView(Context ctx, Runnable onMenu) {
        this(ctx, onMenu, null, false);
    }

    public CheckersView(Context ctx, Runnable onMenu, CoopSession coop) {
        this(ctx, onMenu, coop, false);
    }

    public CheckersView(Context ctx, Runnable onMenu, CoopSession coop, boolean localTwoPlayer) {
        super(ctx);
        this.onMenu = onMenu;
        this.coop = coop;
        this.localTwoPlayer = localTwoPlayer;
        resetBoard();
        if (coop == null) {
            loadSession();
        } else {
            coop.setListener(this);
            setup = false;
            hasSaved = true;
            humanSide = coop.mySide();
            selectedSide = humanSide;
            turnCount = 0;
            log.clear();
            saveTurnStart();
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

            boolean active = log.size() > 0 || gameOver;
            ed.putBoolean(K_HAS, active);
            if (active) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < log.size(); i++) {
                    if (i > 0) sb.append(',');
                    int[] hop = log.get(i);
                    sb.append(Integer.toHexString(hop[0])).append('.')
                        .append(Integer.toHexString(hop[1])).append('.')
                        .append(hop[2]);
                }
                ed.putString(K_LOG, sb.toString());
                ed.putBoolean(K_OVER, gameOver);
                ed.putString(K_STATUS, status);
            }
            ed.commit();
            hasSaved = active;
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

            String raw = prefs.getString(K_LOG, "");
            resetBoard();
            log.clear();
            turnCount = 0;
            saveTurnStart();

            if (raw != null && raw.length() > 0) {
                boolean turnCap = false;
                String[] tokens = raw.split(",");
                for (String token : tokens) {
                    String[] parts = token.split("\\.");
                    if (parts.length < 2) continue;
                    int from;
                    int to;
                    try {
                        from = Integer.parseInt(parts[0], 16);
                        to = Integer.parseInt(parts[1], 16);
                    } catch (Throwable bad) {
                        continue;
                    }
                    if (from < 0 || from > 63 || to < 0 || to > 63) continue;
                    int end = parts.length > 2 && "1".equals(parts[2]) ? 1 : 0;
                    int side = turn;
                    boolean cap = applyHop(b, from, to, side);
                    if (cap) turnCap = true;
                    log.add(new int[]{from, to, end});
                    lastFrom = from;
                    lastTo = to;

                    if (end == 1) {
                        if (turnCap) noCap = 0;
                        else noCap++;
                        turnCap = false;
                        turn = -turn;
                        sel = -1;
                        pendingJump = false;
                        saveTurnStart();
                    } else {
                        pendingJump = true;
                    }
                }

                gameOver = prefs.getBoolean(K_OVER, false);
                String savedStatus = prefs.getString(K_STATUS, "");
                if (pendingJump) {
                    sel = lastTo;
                    hopIndex = 0;
                    candidates.clear();
                    addTurnsStartingAt(b, turn, sel, candidates);
                    status = "Continue your jump";
                } else if (gameOver) {
                    status = savedStatus.length() > 0 ? savedStatus : "Game over";
                } else if (localTwoPlayer) {
                    status = currentTurnText();
                } else if (turn == humanSide) {
                    status = "Your turn · Tap a piece";
                } else {
                    status = "Bot's turn";
                }
                hasSaved = true;
            } else {
                hasSaved = false;
                status = localTwoPlayer
                    ? "Pass the device after each turn"
                    : "Choose your side & bot strength";
            }
            setup = true;
            turnHadCapture = false;
        } catch (Throwable ignored) {
            hasSaved = false;
            setup = true;
            resetBoard();
        }
    }

    private void resetBoard() {
        Arrays.fill(b, 0);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 8; c++) {
                if (((r + c) & 1) == 1) b[r * 8 + c] = -1;
            }
        }
        for (int r = 5; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (((r + c) & 1) == 1) b[r * 8 + c] = 1;
            }
        }
        turn = 1;
        noCap = 0;
        sel = -1;
        lastFrom = -1;
        lastTo = -1;
        hopIndex = 0;
        pendingJump = false;
        gameOver = false;
        thinking = false;
        turnHadCapture = false;
        candidates.clear();
        targetCount = 0;
        animPathLen = 0;
        animPiece = 0;
    }

    private void saveTurnStart() {
        if (turnCount >= MAX_TURNS) return;
        System.arraycopy(b, 0, histBoard[turnCount], 0, 64);
        histTurn[turnCount] = turn;
        histNoCap[turnCount] = noCap;
        histToken[turnCount] = log.size();
        histLastFrom[turnCount] = lastFrom;
        histLastTo[turnCount] = lastTo;
        turnCount++;
    }

    private void startNewGame() {
        cancelBot();
        confetti.stop();
        clearHint();
        if (!localTwoPlayer) humanSide = selectedSide;
        else humanSide = 1;
        setup = false;
        resetBoard();
        log.clear();
        turnCount = 0;
        saveTurnStart();
        hasSaved = true;
        lastFrom = -1;
        lastTo = -1;
        status = currentTurnText();
        persistSessionNow();
        invalidate();
        if (!localTwoPlayer && turn != humanSide) startBot();
    }

    private void showRematchPrompt() {
        new AlertDialog.Builder(getContext())
            .setTitle("Rematch in this room?")
            .setMessage("Both players will return to the starting position. The room code stays the same.")
            .setPositiveButton("Rematch", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    coop.requestRematch();
                }
            })
            .setNegativeButton("Exit room", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    CoopSession.clearSaved(getContext(), "checkers");
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
        hintFrom = -1;
        hintTo = -1;
        status = "Looking for a move...";
        invalidate();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                final int[] move = chooseBotTurn(root, side, 1);
                post(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != hintGen || !hintAvailable()) {
                            hintThinking = false;
                            invalidate();
                            return;
                        }
                        hintThinking = false;
                        if (move != null && move.length >= 2) {
                            hintFrom = move[0];
                            hintTo = move[1];
                            hintUntil = SystemClock.uptimeMillis() + 6000L;
                            status = "Hint: move the glowing piece";
                        } else {
                            status = currentTurnText();
                        }
                        invalidate();
                    }
                });
            }
        }, "checkers-hint");
        t.setDaemon(true);
        t.start();
    }

    private void clearHint() {
        hintGen++;
        hintThinking = false;
        hintFrom = -1;
        hintTo = -1;
        hintUntil = 0L;
    }

    private void resumeGame() {
        if (!hasSaved) return;
        setup = false;
        sel = pendingJump ? lastTo : -1;
        if (gameOver) {
            invalidate();
            return;
        }
        if (pendingJump) {
            hopIndex = 0;
            candidates.clear();
            addTurnsStartingAt(b, turn, sel, candidates);
            refreshTargets();
            status = "Continue your jump";
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
        if (coop != null) return turn == humanSide ? "Your turn · Tap a piece" : "Opponent's turn...";
        if (localTwoPlayer) return (turn == 1 ? "Cream" : "Crimson") + "'s turn · Tap a piece";
        return turn == humanSide ? "Your turn · Tap a piece" : "Bot thinking...";
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

    private void applyBotResult(int gen, int[] move) {
        if (gen != searchGen || setup || gameOver) return;
        thinking = false;
        if (move == null) {
            gameOver = true;
            status = "You win! · Tap New Game";
            confetti.start(getWidth(), getHeight());
            persistSessionNow();
            invalidate();
            return;
        }
        applyBotTurn(move, turn);
    }

    private static final class BotSearch implements Runnable {
        private final CheckersView view;
        private final int gen;
        private final int side;
        private final int diff;
        private final int[] root;

        BotSearch(CheckersView view, int gen, int side, int diff, int[] root) {
            this.view = view;
            this.gen = gen;
            this.side = side;
            this.diff = diff;
            this.root = root;
        }

        @Override
        public void run() {
            long started = SystemClock.uptimeMillis();
            int[] move = view.chooseBotTurn(root, side, diff);
            long minWait = 1000L + ((System.nanoTime() >>> 8) % 350L);
            long elapsed = SystemClock.uptimeMillis() - started;
            if (elapsed < minWait) {
                try {
                    Thread.sleep(minWait - elapsed);
                } catch (InterruptedException ignored) {
                    return;
                }
            }
            view.post(new BotDone(view, gen, move));
        }
    }

    private static final class BotDone implements Runnable {
        private final CheckersView view;
        private final int gen;
        private final int[] move;

        BotDone(CheckersView view, int gen, int[] move) {
            this.view = view;
            this.gen = gen;
            this.move = move;
        }

        @Override
        public void run() {
            view.applyBotResult(gen, move);
        }
    }

    private void applyBotTurn(int[] seq, int side) {
        int movingPiece = seq.length > 0 ? b[seq[0]] : 0;
        turnHadCapture = false;
        for (int i = 1; i < seq.length; i++) {
            int from = seq[i - 1];
            int to = seq[i];
            boolean cap = applyHop(b, from, to, side);
            if (cap) turnHadCapture = true;
            lastFrom = from;
            lastTo = to;
            log.add(new int[]{from, to, i == seq.length - 1 ? 1 : 0});
        }
        if (seq.length >= 2) triggerMoveAnimation(seq, movingPiece);
        if (turnHadCapture) audio.play(true);
        else audio.play(false);
        finishTurn();
    }

    private void triggerMoveAnimation(int[] path, int piece) {
        if (path == null || path.length < 2 || piece == 0) return;
        int n = Math.min(path.length, animPath.length);
        System.arraycopy(path, 0, animPath, 0, n);
        animPathLen = n;
        animSeg = 0;
        animPiece = piece;
        animStartMs = SystemClock.uptimeMillis();
        invalidate();
    }

    private void clearMoveAnimation() {
        animPathLen = 0;
        animPiece = 0;
    }

    private void finishTurn() {
        clearHint();
        sel = -1;
        hopIndex = 0;
        pendingJump = false;
        candidates.clear();
        targetCount = 0;
        turn = -turn;
        if (turnHadCapture) noCap = 0;
        else noCap++;
        turnHadCapture = false;

        saveTurnStart();
        if (coop != null) {
            coopWinner = "";
            boolean over = checkEnd();
            if (!over) {
                status = turn == humanSide
                    ? "Your turn · Tap a piece" : "Opponent's turn...";
            }
            sendCoopMoves(over ? coopWinner : "");
            invalidate();
            return;
        }

        if (checkEnd()) {
            persistSessionNow();
            invalidate();
            return;
        }

        if (localTwoPlayer) {
            status = currentTurnText();
            persistSessionNow();
            invalidate();
        } else if (turn == humanSide) {
            status = "Your turn · Tap a piece";
            persistSessionNow();
            invalidate();
        } else {
            status = "Bot thinking...";
            persistSessionNow();
            invalidate();
            startBot();
        }
    }

    private boolean checkEnd() {
        ArrayList<int[]> moves = generateTurns(b, turn);
        if (moves.isEmpty()) {
            gameOver = true;
            thinking = false;
            if (coop != null) {
                int winnerSide = -turn;
                coopWinner = winnerSide == 1 ? "1" : "-1";
                boolean youWon = winnerSide == humanSide;
                status = youWon ? "You win! · Tap Exit" : "Opponent wins · Tap Exit";
                if (youWon) confetti.start(getWidth(), getHeight());
            } else {
                boolean humanWon = turn != humanSide;
                status = localTwoPlayer
                    ? ((turn == 1 ? "Crimson" : "Cream") + " wins · Tap New Game")
                    : (humanWon ? "You win! · Tap New Game" : "Bot wins · Tap New Game");
                if (localTwoPlayer || humanWon) confetti.start(getWidth(), getHeight());
            }
            return true;
        }
        if (noCap >= 80) {
            gameOver = true;
            thinking = false;
            status = "Draw · 40 moves without a capture";
            if (coop != null) coopWinner = "0";
            return true;
        }
        return false;
    }

    private boolean validCoopCheckers(String raw) {
        int[] bd = histBoard[0].clone();
        int side = 1;
        ArrayList<int[]> cand = null;
        int chain = 0;
        int prevTo = -1;
        boolean inTurn = false;
        String[] tokens = raw.length() == 0 ? new String[0] : raw.split(",");
        for (String token : tokens) {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return false;
            int from;
            int to;
            try {
                from = Integer.parseInt(parts[0], 16);
                to = Integer.parseInt(parts[1], 16);
            } catch (Throwable bad) {
                return false;
            }
            if (from < 0 || from > 63 || to < 0 || to > 63) return false;
            int end = parts.length > 2 && "1".equals(parts[2]) ? 1 : 0;
            if (!inTurn) {
                ArrayList<int[]> all = generateTurns(bd, side);
                cand = new ArrayList<int[]>();
                for (int i = 0; i < all.size(); i++) {
                    int[] t = all.get(i);
                    if (t.length >= 2 && t[0] == from && t[1] == to) cand.add(t);
                }
                if (cand.isEmpty()) return false;
                chain = 1;
                inTurn = true;
            } else {
                if (from != prevTo) return false;
                ArrayList<int[]> next = new ArrayList<int[]>();
                for (int i = 0; i < cand.size(); i++) {
                    int[] t = cand.get(i);
                    if (t.length > chain + 1 && t[chain] == from && t[chain + 1] == to) {
                        next.add(t);
                    }
                }
                if (next.isEmpty()) return false;
                cand = next;
                chain++;
            }
            applyHop(bd, from, to, side);
            prevTo = to;
            if (end == 1) {
                boolean complete = false;
                for (int i = 0; i < cand.size(); i++) {
                    if (cand.get(i).length == chain + 1) {
                        complete = true;
                        break;
                    }
                }
                if (!complete) return false;
                side = -side;
                inTurn = false;
                cand = null;
                chain = 0;
                prevTo = -1;
            }
        }
        return !inTurn;
    }

    private void sendCoopMoves(String winner) {
        if (coop == null) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < log.size(); i++) {
            if (i > 0) sb.append(',');
            int[] hop = log.get(i);
            sb.append(Integer.toHexString(hop[0])).append('.')
                .append(Integer.toHexString(hop[1])).append('.')
                .append(hop[2]);
        }
        coop.sendMoves(sb.toString(), Math.max(0, turnCount - 1), turn, winner);
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
        int localTurns = Math.max(0, turnCount - 1);
        if (!s.isFinished() && s.ply == 0 && (s.moves == null || s.moves.length() == 0)
            && (gameOver || localTurns > 0)) {
            cancelBot();
            clearHint();
            resetBoard();
            log.clear();
            turnCount = 0;
            saveTurnStart();
            coopWinner = "";
            gameOver = false;
            status = currentTurnText();
            hasSaved = true;
            invalidate();
            localTurns = 0;
        }
        if (s.ply < localTurns && !s.isFinished()) {
            if (s.ply == localTurns - 1 && s.turn == coop.mySide()) {
                sendCoopMoves(gameOver ? coopWinner : "");
            }
            return;
        }

        String raw = s.moves == null ? "" : s.moves;
        if (s.ply > localTurns && !validCoopCheckers(raw)) {
            gameOver = true;
            status = "Invalid move from opponent";
            invalidate();
            return;
        }

        if (s.ply > localTurns) {
            int localTokens = log.size();
            boolean animateTurn = s.ply == localTurns + 1;
            int[] newPath = new int[24];
            int newPathLen = 0;
            int newPiece = 0;
            resetBoard();
            log.clear();
            turnCount = 0;
            saveTurnStart();
            boolean turnCap = false;
            if (raw.length() > 0) {
                String[] tokens = raw.split(",");
                for (int tokenIndex = 0; tokenIndex < tokens.length; tokenIndex++) {
                    String token = tokens[tokenIndex];
                    String[] parts = token.split("\\.");
                    if (parts.length < 2) continue;
                    int from;
                    int to;
                    try {
                        from = Integer.parseInt(parts[0], 16);
                        to = Integer.parseInt(parts[1], 16);
                    } catch (Throwable bad) {
                        break;
                    }
                    if (from < 0 || from > 63 || to < 0 || to > 63) break;
                    if (animateTurn && tokenIndex >= localTokens) {
                        if (newPathLen == 0) {
                            newPiece = b[from];
                            newPath[newPathLen++] = from;
                        }
                        if (newPathLen < newPath.length) newPath[newPathLen++] = to;
                    }
                    int end = parts.length > 2 && "1".equals(parts[2]) ? 1 : 0;
                    int side = turn;
                    boolean cap = applyHop(b, from, to, side);
                    if (cap) turnCap = true;
                    log.add(new int[]{from, to, end});
                    lastFrom = from;
                    lastTo = to;
                    if (end == 1) {
                        if (turnCap) noCap = 0;
                        else noCap++;
                        turnCap = false;
                        turn = -turn;
                        saveTurnStart();
                    }
                }
            }
            if (animateTurn && newPathLen >= 2 && newPiece != 0) {
                int[] trim = new int[newPathLen];
                System.arraycopy(newPath, 0, trim, 0, newPathLen);
                triggerMoveAnimation(trim, newPiece);
            }
            pendingJump = false;
            sel = -1;
            hopIndex = 0;
            candidates.clear();
            targetCount = 0;
        }

        humanSide = coop.mySide();
        selectedSide = humanSide;
        setup = false;
        hasSaved = true;
        thinking = false;
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
                status = turn == humanSide ? "Your turn · Tap a piece" : "Opponent's turn...";
            }
        }
        invalidate();
    }

    private void shareGame() {
        StringBuilder sb = new StringBuilder("TinyArcade Checkers\n");
        if (log.size() > 0) {
            for (int i = 0; i < log.size(); i++) {
                if (i > 0) sb.append(' ');
                int[] hop = log.get(i);
                sb.append(Integer.toHexString(hop[0])).append('-')
                    .append(Integer.toHexString(hop[1]));
            }
            sb.append('\n');
        }
        sb.append(status);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "TinyArcade Checkers");
        send.putExtra(Intent.EXTRA_TEXT, sb.toString());
        Intent chooser = Intent.createChooser(send, "TinyArcade Checkers");
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(chooser);
    }

    private void undoMove() {
        if (setup) return;
        clearHint();
        clearMoveAnimation();

        if (pendingJump) {
            int target = turnCount - 1;
            if (target < 0) return;
            restoreSnapshot(target);
            turnCount = target + 1;
            trimLog(Math.min(histToken[target], log.size()));
            gameOver = false;
            pendingJump = false;
            hopIndex = 0;
            sel = -1;
            candidates.clear();
            targetCount = 0;
            hasSaved = log.size() > 0;
            status = localTwoPlayer ? currentTurnText() : "Your turn · Tap a piece";
            turnHadCapture = false;
            persistSessionNow();
            invalidate();
            return;
        }

        boolean wasThinking = thinking;
        cancelBot();
        confetti.stop();
        int target = localTwoPlayer ? turnCount - 2
            : ((wasThinking || turn != humanSide) ? turnCount - 2 : turnCount - 3);
        int min = localTwoPlayer ? 0 : (humanSide == 1 ? 0 : 1);
        if (target < min) target = min;
        if (target < 0 || target >= turnCount) return;

        restoreSnapshot(target);
        turnCount = target + 1;
        trimLog(Math.min(histToken[target], log.size()));
        gameOver = false;
        pendingJump = false;
        hopIndex = 0;
        sel = -1;
        candidates.clear();
        targetCount = 0;
        lastFrom = histLastFrom[target];
        lastTo = histLastTo[target];
        hasSaved = log.size() > 0;
        status = currentTurnText();
        turnHadCapture = false;
        persistSessionNow();
        invalidate();
    }

    private void restoreSnapshot(int index) {
        System.arraycopy(histBoard[index], 0, b, 0, 64);
        turn = histTurn[index];
        noCap = histNoCap[index];
        lastFrom = histLastFrom[index];
        lastTo = histLastTo[index];
    }

    private void trimLog(int size) {
        while (log.size() > size) log.remove(log.size() - 1);
    }

    private boolean applyHop(int[] bd, int from, int to, int side) {
        int pc = bd[from];
        bd[from] = 0;
        int fr = from / 8;
        int tr = to / 8;
        boolean capture = Math.abs(tr - fr) == 2;
        if (capture) bd[(from + to) / 2] = 0;
        bd[to] = pc;

        if (Math.abs(pc) == 1) {
            if ((side == 1 && tr == 0) || (side == -1 && tr == 7)) {
                bd[to] = side * 2;
            }
        }
        return capture;
    }

    private boolean applyTurn(int[] bd, int[] seq, int side) {
        boolean captured = false;
        for (int i = 1; i < seq.length; i++) {
            if (applyHop(bd, seq[i - 1], seq[i], side)) captured = true;
        }
        return captured;
    }

    private ArrayList<int[]> generateTurns(int[] bd, int side) {
        ArrayList<int[]> captures = new ArrayList<int[]>();
        int[] seq = new int[16];
        for (int sq = 0; sq < 64; sq++) {
            if (bd[sq] * side <= 0) continue;
            seq[0] = sq;
            collectJumps(bd, sq, side, seq, 0, captures);
        }
        if (!captures.isEmpty()) return captures;

        ArrayList<int[]> quiet = new ArrayList<int[]>();
        for (int sq = 0; sq < 64; sq++) {
            int pc = bd[sq];
            if (pc * side <= 0) continue;
            boolean king = Math.abs(pc) == 2;
            int r = sq / 8;
            int col = sq % 8;
            for (int d = 0; d < 4; d++) {
                int dr = DR[d];
                int dc = DC[d];
                if (!king && dr != (side == 1 ? -1 : 1)) continue;
                int nr = r + dr;
                int nc = col + dc;
                if (nr < 0 || nr > 7 || nc < 0 || nc > 7) continue;
                int to = nr * 8 + nc;
                if (bd[to] == 0) quiet.add(new int[]{sq, to});
            }
        }
        return quiet;
    }

    private boolean collectJumps(int[] bd, int sq, int side, int[] seq, int depth,
                                 ArrayList<int[]> out) {
        int pc = bd[sq];
        boolean king = Math.abs(pc) == 2;
        int r = sq / 8;
        int col = sq % 8;
        boolean any = false;

        for (int d = 0; d < 4; d++) {
            int dr = DR[d];
            int dc = DC[d];
            if (!king && dr != (side == 1 ? -1 : 1)) continue;

            int mr = r + dr;
            int mc = col + dc;
            int lr = r + dr * 2;
            int lc = col + dc * 2;
            if (lr < 0 || lr > 7 || lc < 0 || lc > 7) continue;

            int mid = mr * 8 + mc;
            int to = lr * 8 + lc;
            int target = bd[mid];
            if (target == 0 || target * side > 0 || bd[to] != 0) continue;

            any = true;
            int[] nb = bd.clone();
            nb[sq] = 0;
            nb[mid] = 0;
            nb[to] = pc;
            seq[depth + 1] = to;

            boolean promoted = false;
            if (!king && ((side == 1 && lr == 0) || (side == -1 && lr == 7))) {
                nb[to] = side * 2;
                promoted = true;
            }

            if (promoted) {
                out.add(Arrays.copyOf(seq, depth + 2));
            } else {
                boolean extended = collectJumps(nb, to, side, seq, depth + 1, out);
                if (!extended) out.add(Arrays.copyOf(seq, depth + 2));
            }
        }
        return any;
    }

    private boolean isCaptureMove(ArrayList<int[]> moves) {
        if (moves.isEmpty()) return false;
        int[] m = moves.get(0);
        return Math.abs((m[1] / 8) - (m[0] / 8)) == 2;
    }

    private void addTurnsStartingAt(int[] bd, int side, int sq, ArrayList<int[]> out) {
        ArrayList<int[]> all = generateTurns(bd, side);
        for (int i = 0; i < all.size(); i++) {
            int[] t = all.get(i);
            if (t[0] == sq) out.add(t);
        }
    }

    private void selectPiece(int sq) {
        ArrayList<int[]> all = generateTurns(b, turn);
        boolean captures = isCaptureMove(all);
        candidates.clear();
        for (int i = 0; i < all.size(); i++) {
            int[] t = all.get(i);
            if (t[0] == sq) candidates.add(t);
        }
        if (candidates.isEmpty()) {
            sel = -1;
            targetCount = 0;
            status = captures ? "Capture is mandatory" : "That piece has no moves";
            invalidate();
            return;
        }
        sel = sq;
        hopIndex = 0;
        pendingJump = false;
        status = captures ? "Choose a capture" : "Tap a highlighted square";
        refreshTargets();
        invalidate();
    }

    private void refreshTargets() {
        targetCount = 0;
        if (sel < 0) return;
        for (int i = 0; i < candidates.size(); i++) {
            int[] t = candidates.get(i);
            int next = hopIndex + 1;
            if (t.length <= next) continue;
            int to = t[next];
            boolean exists = false;
            for (int k = 0; k < targetCount; k++) {
                if (targetSquares[k] == to) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                targetSquares[targetCount] = to;
                targetCapture[targetCount] = Math.abs((to / 8) - (sel / 8)) == 2;
                targetCount++;
            }
        }
    }

    private void playDestination(int to) {
        if (sel < 0) return;

        int next = hopIndex + 1;
        ArrayList<int[]> filtered = new ArrayList<int[]>();
        for (int i = 0; i < candidates.size(); i++) {
            int[] t = candidates.get(i);
            if (t.length > next && t[next] == to) filtered.add(t);
        }
        if (filtered.isEmpty()) return;

        candidates.clear();
        candidates.addAll(filtered);

        int from = sel;
        int movingPiece = b[from];
        boolean cap = applyHop(b, from, to, turn);
        if (cap) turnHadCapture = true;
        triggerMoveAnimation(new int[]{from, to}, movingPiece);
        lastFrom = from;
        lastTo = to;
        hopIndex++;
        sel = to;

        boolean more = false;
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).length > hopIndex + 1) {
                more = true;
                break;
            }
        }

        log.add(new int[]{from, to, more ? 0 : 1});
        audio.play(cap);

        if (more) {
            pendingJump = true;
            status = "Continue your jump";
            refreshTargets();
            persistSessionNow();
            invalidate();
        } else {
            finishTurn();
        }
    }

    private boolean isCaptureHint(int sq) {
        ArrayList<int[]> all = generateTurns(b, turn);
        if (!isCaptureMove(all)) return false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i)[0] == sq) return true;
        }
        return false;
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        float margin = Math.min(w, h) * 0.032f;
        float boardW = Math.min(w - margin * 2f, h * 0.68f);
        float sq = boardW / 8f;
        float boardLeft = (w - boardW) * 0.5f;
        float boardTop = Math.max((h - boardW) * 0.5f + sq * 0.16f,
            sq * 2.32f);
        float btnH = Math.min(sq * 0.78f, boardTop * 0.20f);
        float btnY = Math.max(sq * 0.18f, boardTop - btnH - sq * 0.96f);

        c.drawColor(ArcadeUi.BG);
        if (!setup && !gameOver) {
            ArcadeUi.drawTurnGlow(c, p, w, h,
                turn == 1 ? 0x30F0E1C2 : 0x30B84A5A, turn != humanSide);
        }

        float pad = sq * 0.16f;
        float gap = sq * 0.14f;
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
            && (pendingJump || (localTwoPlayer ? log.size() > 0 : log.size() > (humanSide == 1 ? 0 : 1)));
        ArcadeUi.drawIconButton(c, p, btnGames, ArcadeUi.ICON_HOME, true, ArcadeUi.SLATE);
        ArcadeUi.drawIconButton(c, p, btnNew,
            coop != null ? ArcadeUi.ICON_CLOSE : ArcadeUi.ICON_PLUS, true, ArcadeUi.BLUE);
        ArcadeUi.drawIconButton(c, p, btnUndo, ArcadeUi.ICON_UNDO, canUndo, ArcadeUi.BLUE);
        if (showHint) {
            ArcadeUi.drawIconButton(c, p, btnHint, ArcadeUi.ICON_BULB,
                !hintThinking, ArcadeUi.GOLD);
        }
        ArcadeUi.drawIconButton(c, p, btnShare, ArcadeUi.ICON_SHARE,
            coop != null || log.size() > 0, ArcadeUi.GREEN);

        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(sq * 0.30f);
        p.setColor(gameOver
            ? (localTwoPlayer
                ? (status.startsWith("Cream") ? 0xFFF0E1C2 : ArcadeUi.CORAL)
                : (status.startsWith("You") ? ArcadeUi.GREEN_L : ArcadeUi.CORAL))
            : (localTwoPlayer || turn == humanSide ? ArcadeUi.TEXT : ArcadeUi.MUTED));
        c.drawText(status, w * 0.5f, boardTop - sq * 0.24f, p);
        p.setTypeface(Typeface.DEFAULT);

        drawBoard(c, w, boardLeft, boardTop, boardW, sq);
        drawStrengthHint(c, boardLeft, boardTop, sq);
        if (setup) drawSetup(c, w, boardTop, boardW, sq);

        if (confetti.draw(c)) {
            postInvalidateOnAnimation();
        }
        if (coop != null && !gameOver && !coop.opponentOnline()) {
            ArcadeUi.drawCoopOffline(c, p, w, h);
        }
        if (coop != null && !gameOver) postInvalidateDelayed(1000L);
        if (animPathLen >= 2) postInvalidateOnAnimation();
    }

    private void drawStrengthHint(Canvas c, float left, float top, float sq) {
        if (hintFrom < 0 || hintTo < 0) return;
        long now = SystemClock.uptimeMillis();
        if (now > hintUntil) {
            hintFrom = -1;
            hintTo = -1;
            return;
        }
        float pulse = 0.62f + 0.38f * (float) Math.sin(now / 140.0);
        int[] squares = {hintFrom, hintTo};
        for (int i = 0; i < squares.length; i++) {
            int idx = squares[i];
            if (idx < 0 || idx > 63) continue;
            int r = idx / 8;
            int col = idx % 8;
            int dr = humanSide == 1 ? r : 7 - r;
            int dc = humanSide == 1 ? col : 7 - col;
            float cx = left + dc * sq + sq * 0.5f;
            float cy = top + dr * sq + sq * 0.5f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(2.5f, sq * 0.075f));
            p.setColor((i == 0 ? 0xDDF0D060 : 0xFF7CE08A) & 0xFFFFFFFF);
            p.setAlpha((int) (255 * pulse));
            c.drawCircle(cx, cy, sq * (i == 0 ? 0.40f : 0.30f), p);
            if (i == 1) {
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, cy, sq * 0.12f, p);
            }
        }
        p.setStyle(Paint.Style.FILL);
        postInvalidateDelayed(70L);
    }

    private void drawBoard(Canvas c, int w, float left, float top, float boardW, float sq) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x66000000);
        c.drawRoundRect(left - sq * 0.12f + 2f, top - sq * 0.12f + 5f,
            left + boardW + sq * 0.12f + 2f, top + boardW + sq * 0.12f + 5f,
            sq * 0.16f, sq * 0.16f, p);

        p.setColor(0xFF3B2414);
        c.drawRoundRect(left - sq * 0.12f, top - sq * 0.12f,
            left + boardW + sq * 0.12f, top + boardW + sq * 0.12f,
            sq * 0.16f, sq * 0.16f, p);

        boolean captureHints = !setup && !thinking && !gameOver
            && (localTwoPlayer || turn == humanSide) && sel == -1
            && (coop == null || coop.opponentOnline());
        boolean[] hinted = new boolean[64];
        if (captureHints) {
            ArrayList<int[]> all = generateTurns(b, turn);
            if (isCaptureMove(all)) {
                for (int i = 0; i < all.size(); i++) hinted[all.get(i)[0]] = true;
            }
        }

        for (int dr = 0; dr < 8; dr++) {
            for (int dc = 0; dc < 8; dc++) {
                int r = humanSide == 1 ? dr : 7 - dr;
                int col = humanSide == 1 ? dc : 7 - dc;
                int idx = r * 8 + col;
                float x = left + dc * sq;
                float y = top + dr * sq;
                boolean light = ((r + col) & 1) == 0;

                p.setStyle(Paint.Style.FILL);
                p.setColor(light ? 0xFFE6CFAF : 0xFF7D5333);
                c.drawRect(x, y, x + sq, y + sq, p);

                p.setColor(light ? 0x12FFFFFF : 0x16000000);
                c.drawRect(x, y, x + sq, y + sq * 0.34f, p);

                if (idx == lastFrom || idx == lastTo) {
                    p.setColor(0x332E8B57);
                    c.drawRect(x, y, x + sq, y + sq, p);
                    drawSquareFrame(c, x, y, sq, 0xFF2E8B57, sq * 0.045f);
                }
                if (idx == sel) {
                    drawSquareFrame(c, x, y, sq, ArcadeUi.GOLD, sq * 0.07f);
                }
            }
        }

        for (int i = 0; i < targetCount; i++) {
            int to = targetSquares[i];
            int r = to / 8;
            int col = to % 8;
            int dr = humanSide == 1 ? r : 7 - r;
            int dc = humanSide == 1 ? col : 7 - col;
            float x = left + dc * sq;
            float y = top + dr * sq;
            float cx = x + sq * 0.5f;
            float cy = y + sq * 0.5f;

            p.setStyle(Paint.Style.FILL);
            p.setColor(targetCapture[i] ? 0xDDD48837 : 0xCC2E8B57);
            c.drawCircle(cx, cy, sq * (targetCapture[i] ? 0.17f : 0.14f), p);
            if (targetCapture[i]) {
                drawSquareFrame(c, x, y, sq, 0xFFD48837, sq * 0.045f);
            }
        }

        boolean animating = false;
        int animFromSq = -1;
        int animToSq = -1;
        float animEase = 1f;
        float animLift = 0f;
        if (animPathLen >= 2 && animPiece != 0) {
            long now = SystemClock.uptimeMillis();
            while (animPathLen >= 2 && now - animStartMs >= ANIM_MS) {
                animSeg++;
                animStartMs += ANIM_MS;
                if (animSeg >= animPathLen - 1) {
                    clearMoveAnimation();
                    break;
                }
            }
            if (animPathLen >= 2) {
                float raw = (now - animStartMs) / (float) ANIM_MS;
                if (raw < 0f) raw = 0f;
                if (raw > 1f) raw = 1f;
                animEase = 1f - (float) Math.pow(1f - raw, 3f);
                animLift = (float) Math.sin(Math.PI * raw) * sq * 0.20f;
                animFromSq = animPath[animSeg];
                animToSq = animPath[animSeg + 1];
                animating = true;
            }
        }

        for (int idx = 0; idx < 64; idx++) {
            int pc = b[idx];
            if (pc == 0) continue;
            if (animating && idx == animPath[animPathLen - 1]) continue;
            int r = idx / 8;
            int col = idx % 8;
            int dr = humanSide == 1 ? r : 7 - r;
            int dc = humanSide == 1 ? col : 7 - col;
            float x = left + dc * sq;
            float y = top + dr * sq;
            drawPiece(c, pc, x, y, sq);
            if (hinted[idx] && pc * turn > 0) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(sq * 0.045f);
                p.setColor(ArcadeUi.CORAL);
                c.drawCircle(x + sq * 0.5f, y + sq * 0.5f, sq * 0.39f, p);
                p.setStyle(Paint.Style.FILL);
            }
        }

        if (animating) {
            int fr = animFromSq / 8, fc = animFromSq % 8;
            int tr = animToSq / 8, tc = animToSq % 8;
            int fdr = humanSide == 1 ? fr : 7 - fr;
            int fdc = humanSide == 1 ? fc : 7 - fc;
            int tdr = humanSide == 1 ? tr : 7 - tr;
            int tdc = humanSide == 1 ? tc : 7 - tc;
            float fx = left + fdc * sq + sq * 0.5f;
            float fy = top + fdr * sq + sq * 0.5f;
            float tx = left + tdc * sq + sq * 0.5f;
            float ty = top + tdr * sq + sq * 0.5f;
            float x = fx + (tx - fx) * animEase;
            float y = fy + (ty - fy) * animEase - animLift;
            drawPiece(c, animPiece, x - sq * 0.5f, y - sq * 0.5f, sq);
        }

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f, sq * 0.025f));
        p.setColor(0xFF3B2414);
        c.drawRect(left, top, left + boardW, top + boardW, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawPiece(Canvas c, int pc, float x, float y, float sq) {
        int side = pc > 0 ? 1 : -1;
        boolean king = Math.abs(pc) == 2;
        int outer = side == 1 ? 0xFFF2E3C8 : 0xFFD9543F;
        int inner = side == 1 ? 0xFFE2CDA9 : 0xFFB83E2C;
        int ring = side == 1 ? 0xFF8A6A44 : 0xFFF08A73;

        float cx = x + sq * 0.5f;
        float cy = y + sq * 0.5f;
        float r = sq * 0.35f;

        p.setStyle(Paint.Style.FILL);
        p.setColor(0x44000000);
        c.drawCircle(cx + sq * 0.035f, cy + sq * 0.055f, r, p);

        p.setColor(outer);
        c.drawCircle(cx, cy, r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sq * 0.035f);
        p.setColor(ring);
        c.drawCircle(cx, cy, r * 0.92f, p);

        p.setStyle(Paint.Style.FILL);
        p.setColor(inner);
        c.drawCircle(cx, cy, r * 0.58f, p);

        p.setColor(0x33FFFFFF);
        c.drawCircle(cx - r * 0.28f, cy - r * 0.30f, r * 0.21f, p);

        if (king) {
            float kw = r * 0.94f;
            float kh = r * 0.56f;
            path.reset();
            path.moveTo(cx - kw * 0.50f, cy + kh * 0.30f);
            path.lineTo(cx - kw * 0.34f, cy - kh * 0.36f);
            path.lineTo(cx - kw * 0.11f, cy + kh * 0.04f);
            path.lineTo(cx, cy - kh * 0.52f);
            path.lineTo(cx + kw * 0.11f, cy + kh * 0.04f);
            path.lineTo(cx + kw * 0.34f, cy - kh * 0.36f);
            path.lineTo(cx + kw * 0.50f, cy + kh * 0.30f);
            path.close();
            p.setStyle(Paint.Style.FILL);
            p.setColor(side == 1 ? ArcadeUi.ORANGE : 0xFFF2E3C8);
            c.drawPath(path, p);
        }
    }

    private void drawSquareFrame(Canvas c, float x, float y, float sq, int color, float width) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f, width));
        p.setColor(color);
        c.drawRect(x + width * 0.5f, y + width * 0.5f,
            x + sq - width * 0.5f, y + sq - width * 0.5f, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawSetup(Canvas c, int w, float boardTop, float boardW, float sq) {
        float boardLeft = (w - boardW) * 0.5f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xD6111111);
        c.drawRect(boardLeft - sq * 0.32f, boardTop - sq * 0.10f,
            boardLeft + boardW + sq * 0.32f, boardTop + boardW, p);

        float cardW = Math.min(w * 0.88f, boardW + sq * 0.64f);
        float cardH = sq * (localTwoPlayer ? (hasSaved ? 4.15f : 2.85f)
            : (hasSaved ? 6.55f : 5.55f));
        float cx = (w - cardW) * 0.5f;
        float cy = boardTop + (boardW - cardH) * 0.5f;

        p.setColor(0xFF23262C);
        c.drawRoundRect(new RectF(cx, cy, cx + cardW, cy + cardH), 26f, 26f, p);

        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(ArcadeUi.TEXT);
        p.setTextSize(sq * 0.50f);
        c.drawText("Checkers", w * 0.5f, cy + sq * 0.62f, p);

        float dotY = cy + sq * 0.94f;
        float dotGap = sq * 0.22f;
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 4; i++) {
            p.setColor((i & 1) == 0 ? 0xFFF0E1C2 : 0xFFB84A5A);
            c.drawCircle(w * 0.5f + (i - 1.5f) * dotGap, dotY, sq * 0.08f, p);
        }

        float bPad = sq * 0.30f;
        float curY = cy + sq * 1.20f;

        if (localTwoPlayer) {
            btnSideA.setEmpty();
            btnSideB.setEmpty();
            for (int i = 0; i < 4; i++) btnDiff[i].setEmpty();

            if (hasSaved) {
                float contH = sq * 0.86f;
                btnContinue.set(cx + bPad, curY, cx + cardW - bPad, curY + contH);
                drawButton(c, btnContinue,
                    gameOver ? "View Final Board" : "Continue game", true, ArcadeUi.GREEN);
                curY += contH + sq * 0.32f;
            } else {
                btnContinue.setEmpty();
            }

            p.setTypeface(Typeface.DEFAULT);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(ArcadeUi.MUTED);
            p.setTextSize(sq * 0.30f);
            c.drawText("2 players · pass the device after each turn",
                w * 0.5f, curY + sq * 0.28f, p);
            curY += sq * 0.66f;

            float startH = sq * 0.94f;
            btnStart.set(cx + bPad, curY, cx + cardW - bPad, curY + startH);
            drawButton(c, btnStart, "Start Co-op Game", true, ArcadeUi.ORANGE);
            return;
        }

        if (hasSaved) {
            float contH = sq * 0.86f;
            btnContinue.set(cx + bPad, curY, cx + cardW - bPad, curY + contH);
            String side = humanSide == 1 ? "Cream" : "Crimson";
            String contLabel = gameOver
                ? "View Final Board"
                : "Continue (" + side + " · " + DIFF_NAMES[difficulty] + ")";
            drawButton(c, btnContinue, contLabel, true, ArcadeUi.GREEN);
            curY += contH + sq * 0.25f;
        } else {
            btnContinue.setEmpty();
        }

        drawSectionLabel(c, "YOUR SIDE", cx + bPad, curY + sq * 0.22f, sq);
        curY += sq * 0.34f;

        float sideBtnW = (cardW - bPad * 3f) / 2f;
        float sideBtnH = sq * 0.92f;
        btnSideA.set(cx + bPad, curY, cx + bPad + sideBtnW, curY + sideBtnH);
        btnSideB.set(cx + bPad * 2f + sideBtnW, curY, cx + cardW - bPad, curY + sideBtnH);
        drawSelectableButton(c, btnSideA, "Cream", selectedSide == 1, ArcadeUi.GREEN);
        drawSelectableButton(c, btnSideB, "Crimson", selectedSide == -1, ArcadeUi.GREEN);
        curY += sideBtnH + sq * 0.25f;

        drawSectionLabel(c, "LEVEL", cx + bPad, curY + sq * 0.22f, sq);
        curY += sq * 0.34f;

        float gap = sq * 0.14f;
        float diffW = (cardW - bPad * 2f - gap * 3f) / 4f;
        float diffH = sq * 0.82f;
        for (int i = 0; i < 4; i++) {
            float dx = cx + bPad + i * (diffW + gap);
            btnDiff[i].set(dx, curY, dx + diffW, curY + diffH);
            drawSelectableButton(c, btnDiff[i], DIFF_NAMES[i], difficulty == i, ArcadeUi.BLUE);
        }
        curY += diffH + sq * 0.28f;

        float startH = sq * 0.94f;
        btnStart.set(cx + bPad, curY, cx + cardW - bPad, curY + startH);
        drawButton(c, btnStart, "Play New Session", true, ArcadeUi.ORANGE);
    }

    private void drawSectionLabel(Canvas c, String label, float x, float baseline, float sq) {
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.LEFT);
        p.setColor(ArcadeUi.MUTED);
        p.setTextSize(sq * 0.26f);
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
        if (e.getAction() != MotionEvent.ACTION_DOWN) return true;
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
            if (coop != null || log.size() > 0) shareGame();
            return true;
        }

        if (thinking || gameOver || (!localTwoPlayer && turn != humanSide)
            || (coop != null && !coop.opponentOnline())) return true;

        int w = getWidth();
        int h = getHeight();
        float margin = Math.min(w, h) * 0.032f;
        float boardW = Math.min(w - margin * 2f, h * 0.68f);
        float sq = boardW / 8f;
        float boardLeft = (w - boardW) * 0.5f;
        float boardTop = Math.max((h - boardW) * 0.5f + sq * 0.16f,
            sq * 2.32f);

        int dc = (int) Math.floor((tx - boardLeft) / sq);
        int dr = (int) Math.floor((ty - boardTop) / sq);
        if (dc < 0 || dc > 7 || dr < 0 || dr > 7) return true;

        int r = humanSide == 1 ? dr : 7 - dr;
        int col = humanSide == 1 ? dc : 7 - dc;
        int idx = r * 8 + col;

        if (hopIndex > 0) {
            for (int i = 0; i < targetCount; i++) {
                if (targetSquares[i] == idx) {
                    playDestination(idx);
                    return true;
                }
            }
            return true;
        }

        if (b[idx] * turn > 0) {
            if (sel == idx) {
                sel = -1;
                candidates.clear();
                targetCount = 0;
                status = currentTurnText();
                invalidate();
            } else {
                selectPiece(idx);
            }
            return true;
        }

        if (sel >= 0) {
            for (int i = 0; i < targetCount; i++) {
                if (targetSquares[i] == idx) {
                    playDestination(idx);
                    return true;
                }
            }
            sel = -1;
            candidates.clear();
            targetCount = 0;
            status = currentTurnText();
            invalidate();
        }
        return true;
    }

    private int[] chooseBotTurn(int[] root, int side, int diff) {
        ArrayList<int[]> moves = generateTurns(root, side);
        if (moves.isEmpty()) return null;
        if (diff == 0 && rng.nextFloat() < 0.34f) {
            return moves.get(rng.nextInt(moves.size()));
        }

        orderMoves(moves);
        int maxDepth = diff == 0 ? 1 : (diff == 1 ? 3 : (diff == 2 ? 5 : 7));
        long budget = diff == 0 ? 120L : (diff == 1 ? 300L : (diff == 2 ? 650L : 1100L));
        aiDeadline = System.currentTimeMillis() + budget;
        aiAbort = false;
        aiNodes = 0;

        int[] bestOverall = moves.get(0);
        for (int d = 1; d <= maxDepth; d++) {
            int bestScore = -INF;
            int[] bestThis = null;
            for (int i = 0; i < moves.size(); i++) {
                int[] mv = moves.get(i);
                int[] nb = root.clone();
                applyTurn(nb, mv, side);
                int sc = -negamax(nb, -side, d - 1, -INF, INF);
                if (sc > bestScore) {
                    bestScore = sc;
                    bestThis = mv;
                }
                if (aiAbort) break;
            }
            if (bestThis != null && (!aiAbort || d == 1)) bestOverall = bestThis;
            if (aiAbort && d > 1) break;
            if (bestScore >= WIN_SCORE - 1000) break;
        }
        return bestOverall;
    }

    private int negamax(int[] bd, int side, int depth, int alpha, int beta) {
        if ((++aiNodes & 255) == 0 && System.currentTimeMillis() > aiDeadline) aiAbort = true;
        if (aiAbort) return 0;
        if (depth <= 0) return evaluate(bd, side);

        ArrayList<int[]> moves = generateTurns(bd, side);
        if (moves.isEmpty()) return -WIN_SCORE;
        orderMoves(moves);

        int best = -INF;
        for (int i = 0; i < moves.size(); i++) {
            int[] nb = bd.clone();
            applyTurn(nb, moves.get(i), side);
            int sc = -negamax(nb, -side, depth - 1, -beta, -alpha);
            if (sc > best) best = sc;
            if (best > alpha) alpha = best;
            if (alpha >= beta) break;
            if (aiAbort) break;
        }
        return best;
    }

    private int evaluate(int[] bd, int side) {
        int score = 0;
        for (int idx = 0; idx < 64; idx++) {
            int pc = bd[idx];
            if (pc == 0) continue;
            int abs = Math.abs(pc);
            int row = idx / 8;
            int col = idx % 8;
            int value = abs == 2 ? 160 : 100;
            if (abs == 1) {
                int adv = pc > 0 ? 7 - row : row;
                value += adv * 4;
                if ((pc > 0 && row == 7) || (pc < 0 && row == 0)) value += 6;
            } else {
                value += 8 - Math.abs(3.5f - col) * 2;
            }
            score += pc * side > 0 ? value : -value;
        }
        return score;
    }

    private void orderMoves(ArrayList<int[]> moves) {
        for (int i = 1; i < moves.size(); i++) {
            int[] key = moves.get(i);
            int j = i - 1;
            while (j >= 0 && moves.get(j).length < key.length) {
                moves.set(j + 1, moves.get(j));
                j--;
            }
            moves.set(j + 1, key);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelBot();
        if (coop != null) coop.pause();
        audio.release();
        super.onDetachedFromWindow();
    }
}
