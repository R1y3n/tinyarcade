package com.tiny.chess;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import com.tiny.arcade.ArcadeAudio;
import com.tiny.arcade.ArcadeConfetti;
import com.tiny.arcade.ArcadeGame;
import com.tiny.arcade.ArcadePrefs;
import com.tiny.arcade.ArcadeUi;
import com.tiny.arcade.ChessTheme;
import com.tiny.arcade.CoopLobbyView;
import com.tiny.arcade.CoopSession;
import com.tiny.arcade.GameModeView;
import com.tiny.arcade.MainMenuView;
import com.tiny.arcade.UpdateManager;
import com.tiny.checkers.CheckersView;
import com.tiny.powerof4.PowerOf4View;
import java.util.Arrays;

public class MainActivity extends Activity implements MainMenuView.Listener {
    private static final String LAST_PREFS = "tinyarcade_last";
    private static final String LAST_COOP_GAME = "coop_game";

    private ArcadeGame activeGame;
    private boolean menuVisible = true;
    private boolean themePickerVisible;
    private UpdateManager updateManager;

    private final Runnable openMenu = new Runnable() {
        @Override
        public void run() {
            showMenu();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyOrientation();
        ArcadeAudio.setEnabled(ArcadePrefs.soundEnabled(this));
        updateManager = new UpdateManager(this);
        updateManager.check();
        SharedPreferences prefs = getSharedPreferences(LAST_PREFS, MODE_PRIVATE);
        int game = prefs.getInt(LAST_COOP_GAME, 0);
        if (game != 0 && CoopSession.loadSaved(this, CoopLobbyView.gameKey(game)) != null) {
            showCoopLobby(game, true);
        } else {
            showMenu();
        }
    }

    private void applyOrientation() {
        setRequestedOrientation(ArcadePrefs.horizontalMenu(this)
            ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    private void showMenu() {
        if (activeGame != null) activeGame.onHide();
        activeGame = null;
        menuVisible = true;
        themePickerVisible = false;
        getSharedPreferences(LAST_PREFS, MODE_PRIVATE).edit()
            .remove(LAST_COOP_GAME).commit();
        setContentView(new MainMenuView(this, this));
    }

    @Override
    public void onSettingsRequested() {
        menuVisible = false;
        themePickerVisible = false;
        setContentView(new com.tiny.arcade.SettingsView(this,
            new com.tiny.arcade.SettingsView.Listener() {
                @Override
                public void onSettingsBack() {
                    showMenu();
                }

                @Override
                public void onThemePickerRequested() {
                    showThemePicker();
                }
            }));
    }

    private void showThemePicker() {
        themePickerVisible = true;
        setContentView(new com.tiny.arcade.ThemePickerView(this,
            new com.tiny.arcade.ThemePickerView.Listener() {
                @Override
                public void onThemePickerDone() {
                    onSettingsRequested();
                }
            }));
    }

    @Override
    public void onGameSelected(final int game) {
        if (activeGame != null) activeGame.onHide();
        activeGame = null;
        menuVisible = false;
        setContentView(new GameModeView(this, game, new GameModeView.Listener() {
            @Override
            public void onSolo(int selectedGame) {
                launchGame(selectedGame, null, false);
            }

            @Override
            public void onLocalTwoPlayer(int selectedGame) {
                launchGame(selectedGame, null, true);
            }

            @Override
            public void onMultiplayer(int selectedGame) {
                showCoopLobby(selectedGame);
            }

            @Override
            public void onModeBack() {
                showMenu();
            }
        }));
    }

    private void showCoopLobby(final int game) {
        showCoopLobby(game, false);
    }

    private void showCoopLobby(final int game, boolean autoReconnect) {
        if (activeGame != null) activeGame.onHide();
        activeGame = null;
        menuVisible = false;
        getSharedPreferences(LAST_PREFS, MODE_PRIVATE).edit()
            .putInt(LAST_COOP_GAME, game).commit();
        CoopLobbyView lobby = new CoopLobbyView(this, game, new CoopLobbyView.Listener() {
            @Override
            public void onCoopReady(CoopSession session) {
                launchGame(game, session, false);
            }

            @Override
            public void onCoopBack() {
                showMenu();
            }
        });
        activeGame = lobby;
        setContentView(lobby);
        if (autoReconnect) lobby.reconnectSaved();
    }

    private void launchGame(int game, CoopSession coop) {
        launchGame(game, coop, false);
    }

    private void launchGame(int game, CoopSession coop, boolean localTwoPlayer) {
        if (activeGame != null) activeGame.onHide();
        if (coop == null) {
            getSharedPreferences(LAST_PREFS, MODE_PRIVATE).edit()
                .remove(LAST_COOP_GAME).commit();
        }
        View view;
        ArcadeGame gameScreen;
        if (game == MainMenuView.GAME_CHESS) {
            ChessView chess = new ChessView(this, coop, localTwoPlayer);
            chess.setOnGamesRequested(openMenu);
            view = chess;
            gameScreen = chess;
        } else if (game == MainMenuView.GAME_CHECKERS) {
            CheckersView checkers = new CheckersView(this, openMenu, coop, localTwoPlayer);
            view = checkers;
            gameScreen = checkers;
        } else {
            PowerOf4View power4 = new PowerOf4View(this, openMenu, coop, localTwoPlayer);
            view = power4;
            gameScreen = power4;
        }
        activeGame = gameScreen;
        menuVisible = false;
        setContentView(view);
        gameScreen.onShow();
    }

    @Override
    public void onBackPressed() {
        if (themePickerVisible) {
            onSettingsRequested();
            return;
        }
        if (!menuVisible || activeGame != null) {
            showMenu();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (updateManager != null) updateManager.resumePendingInstall();
        if (activeGame != null) activeGame.onShow();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (activeGame != null) {
            activeGame.persistSessionNow();
            activeGame.onAppPause();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (activeGame != null) activeGame.persistSessionNow();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (activeGame != null) activeGame.persistSessionNow();
    }

    static final class ChessView extends View implements Runnable, ArcadeGame, CoopSession.Listener {
        static final int MAX_PLY = 32;
        static final int MAX_GAME_PLIES = 600;
        static final int TT_SIZE = 1 << 16; // 65,536 entries in RAM
        static final int TT_MASK = TT_SIZE - 1;
        static final int INF = 50000, MATE = 49000;
        static final long ANIM_DURATION_MS = 200L;
        static final long EASTER_DURATION_MS = 3600L;

        static final String PREFS_NAME = "tinychess_prefs";
        static final String KEY_HAS_SAVE = "has_save";
        static final String KEY_SIDE = "human_side";
        static final String KEY_DIFF = "difficulty";
        static final String KEY_MOVES = "moves_hex";
        static final String KEY_SANS = "sans_list";
        static final String KEY_GAME_OVER = "game_over";
        static final String KEY_STATUS = "status_txt";
        static final String KEY_PGN_RES = "pgn_res";

        static final String[] DIFF_NAMES = {"Easy", "Medium", "Hard", "Elite"};

        // Authoritative UI Board (STRICTLY mutated on UI thread only)
        final int[] b = new int[128];
        int castle = 15, ep = -1, turn = 1, halfMoveClock = 0;
        long curHash;

        // Private Search Board (exclusively owned by background engine thread)
        final int[] sb = new int[128];
        int sCastle, sEp, sHalfClock;
        long sHash;
        final long[] sHistHash = new long[MAX_GAME_PLIES];
        int sHistLen = 0;

        int humanSide = 1;       // 1 = White, -1 = Black
        int selectedSide = 1;    // Picker selection
        int difficulty = 0;      // 0=Easy (default), 1=Medium, 2=Hard, 3=Expert
        int theme = 0;           // Chess board palette
        int hintFrom = -1, hintTo = -1;
        long hintUntil = 0L;
        boolean hintThinking = false;
        volatile int hintGen = 0;
        int sel = -1, lastFrom = -1, lastTo = -1;
        int pendingPromoFrom = -1, pendingPromoTo = -1, pendingPromoCap = 0;

        // Smooth Piece Animation State
        int animPiece = 0, animFromSq = -1, animToSq = -1;
        int animCapturedPiece = 0, animCapturedSq = -1;
        int animRookPiece = 0, animRookFromSq = -1, animRookToSq = -1;
        boolean animIsCapture = false, animSoundPlayed = false;
        long animStartMs = 0L;

        // Easter Egg State (10 taps on "TinyArcade" title)
        int titleTapCount = 0;
        long lastTitleTapMs = 0L;
        boolean easterActive = false;
        long easterStartMs = 0L;
        static final int PARTICLE_COUNT = 44;
        final float[] pX = new float[PARTICLE_COUNT];
        final float[] pY = new float[PARTICLE_COUNT];
        final float[] pVx = new float[PARTICLE_COUNT];
        final float[] pVy = new float[PARTICLE_COUNT];
        final float[] pSize = new float[PARTICLE_COUNT];
        final float[] pPhase = new float[PARTICLE_COUNT];
        final int[] pColor = new int[PARTICLE_COUNT];
        final boolean[] pIsBalloon = new boolean[PARTICLE_COUNT];

        // Procedural AudioTracks (zero external assets)
        AudioTrack sndMove;
        AudioTrack sndCapture;
        final ArcadeConfetti confetti = new ArcadeConfetti();
        final CoopSession coop;
        boolean localTwoPlayer;
        String coopWinner = "";

        boolean pickingSide = true;
        boolean hasSavedGame = false;
        boolean thinking = false, gameOver = false;
        String status = "Choose your side & bot strength";
        String pgnResult = "*";

        // Full snapshot history for 100% lossless Undo, Crash-Safe Save & PGN Share
        final int[][] histBoard = new int[MAX_GAME_PLIES][128];
        final int[] histCastle = new int[MAX_GAME_PLIES];
        final int[] histEp = new int[MAX_GAME_PLIES];
        final int[] histTurn = new int[MAX_GAME_PLIES];
        final int[] histHalfClock = new int[MAX_GAME_PLIES];
        final int[] histLastFrom = new int[MAX_GAME_PLIES];
        final int[] histLastTo = new int[MAX_GAME_PLIES];
        final int[] histMove = new int[MAX_GAME_PLIES];
        final long[] histHash = new long[MAX_GAME_PLIES];
        final String[] histSan = new String[MAX_GAME_PLIES];
        int plyCount = 0;

        // Legal target cache for selected piece dot rendering
        final int[] legalTargets = new int[32];
        final boolean[] legalIsCap = new boolean[32];
        int legalTargetCount = 0;

        // Search buffers & Zobrist Transposition Table
        final int[][] moveBuf = new int[MAX_PLY][256];
        final int[][] scoreBuf = new int[MAX_PLY][256];
        final int[] killer1 = new int[MAX_PLY], killer2 = new int[MAX_PLY];
        final long[] searchPath = new long[MAX_PLY];

        final long[] ttKey = new long[TT_SIZE];
        final int[] ttMove = new int[TT_SIZE];
        final short[] ttScore = new short[TT_SIZE];
        final byte[] ttDepth = new byte[TT_SIZE];
        final byte[] ttFlag = new byte[TT_SIZE];

        static final long[][] Z_PC = new long[13][128];
        static final long[] Z_EP = new long[128];
        static final long[] Z_CASTLE = new long[16];
        static final long Z_SIDE;

        volatile int searchGen = 0;
        Thread botThread = null;
        long deadline, nodes;
        volatile boolean abort;
        int bestMoveRoot;

        static final int[] VAL = {0, 100, 320, 330, 500, 900, 20000};
        static final int[] MAT_PTS = {0, 1, 3, 3, 5, 9, 0};
        static final int[] PHASE_W = {0, 0, 1, 1, 2, 4, 0};
        static final int[] N_OFF = {-33, -31, -18, -14, 14, 18, 31, 33};
        static final int[] B_OFF = {-17, -15, 15, 17};
        static final int[] R_OFF = {-16, 16, -1, 1};
        static final int[] K_OFF = {-17, -16, -15, -1, 1, 15, 16, 17};

        // Piece-Square Tables (Rank 8 at top, Rank 1 at bottom, White perspective)
        static final byte[][] PST = {
            // Pawn
            {  0,  0,  0,  0,  0,  0,  0,  0,
              50, 50, 50, 50, 50, 50, 50, 50,
              12, 12, 24, 34, 34, 24, 12, 12,
               6,  6, 12, 28, 28, 12,  6,  6,
               0,  0,  4, 24, 24,  4,  0,  0,
               4, -4, -8,  0,  0, -8, -4,  4,
               4, 10, 10,-22,-22, 10, 10,  4,
               0,  0,  0,  0,  0,  0,  0,  0 },
            // Knight
            {-50,-40,-30,-30,-30,-30,-40,-50,
             -40,-20,  0,  4,  4,  0,-20,-40,
             -30,  4, 14, 18, 18, 14,  4,-30,
             -30,  6, 18, 24, 24, 18,  6,-30,
             -30,  4, 18, 24, 24, 18,  4,-30,
             -30,  6, 14, 18, 18, 14,  6,-30,
             -40,-20,  0,  6,  6,  0,-20,-40,
             -50,-40,-30,-30,-30,-30,-40,-50 },
            // Bishop
            {-20,-10,-10,-10,-10,-10,-10,-20,
             -10,  4,  0,  0,  0,  0,  4,-10,
             -10,  6, 10, 12, 12, 10,  6,-10,
             -10,  4, 10, 14, 14, 10,  4,-10,
             -10,  6,  8, 14, 14,  8,  6,-10,
             -10, 10, 10, 10, 10, 10, 10,-10,
             -10,  8,  0,  0,  0,  0,  8,-10,
             -20,-10,-10,-10,-10,-10,-10,-20 },
            // Rook
            {  0,  0,  2,  4,  4,  2,  0,  0,
               6, 12, 12, 12, 12, 12, 12,  6,
              -4,  0,  0,  0,  0,  0,  0, -4,
              -4,  0,  0,  0,  0,  0,  0, -4,
              -4,  0,  0,  0,  0,  0,  0, -4,
              -4,  0,  0,  0,  0,  0,  0, -4,
              -4,  0,  0,  0,  0,  0,  0, -4,
               0,  0,  2,  8,  8,  4,  0,  0 },
            // Queen
            {-20,-10,-10, -5, -5,-10,-10,-20,
             -10,  0,  0,  0,  0,  0,  0,-10,
             -10,  0,  6,  6,  6,  6,  0,-10,
              -5,  0,  6,  8,  8,  6,  0, -5,
               0,  0,  6,  8,  8,  6,  0, -5,
             -10,  6,  6,  6,  6,  6,  0,-10,
             -10,  0,  6,  0,  0,  0,  0,-10,
             -20,-10,-10, -5, -5,-10,-10,-20 },
            // King Middlegame
            {-30,-40,-40,-50,-50,-40,-40,-30,
             -30,-40,-40,-50,-50,-40,-40,-30,
             -30,-40,-40,-50,-50,-40,-40,-30,
             -30,-40,-40,-50,-50,-40,-40,-30,
             -20,-30,-30,-40,-40,-30,-30,-20,
             -10,-20,-20,-20,-20,-20,-20,-10,
              20, 22, -4,-10,-10, -4, 22, 20,
              20, 32, 14,-10,  0,-10, 32, 20 },
            // King Endgame
            {-50,-30,-20,-20,-20,-20,-30,-50,
             -30,-10,  0,  4,  4,  0,-10,-30,
             -20,  4, 18, 24, 24, 18,  4,-20,
             -20,  6, 24, 32, 32, 24,  6,-20,
             -20,  6, 24, 32, 32, 24,  6,-20,
             -20,  4, 18, 24, 24, 18,  4,-20,
             -30,-10,  0,  4,  4,  0,-10,-30,
             -50,-30,-20,-20,-20,-20,-30,-50 }
        };

        static {
            long s = 0x9E3779B97F4A7C15L;
            for (int p = 0; p < 13; p++)
                for (int sq = 0; sq < 128; sq++) {
                    s ^= s << 13; s ^= s >>> 7; s ^= s << 17;
                    Z_PC[p][sq] = s;
                }
            for (int sq = 0; sq < 128; sq++) {
                s ^= s << 13; s ^= s >>> 7; s ^= s << 17;
                Z_EP[sq] = s;
            }
            for (int c = 0; c < 16; c++) {
                s ^= s << 13; s ^= s >>> 7; s ^= s << 17;
                Z_CASTLE[c] = s;
            }
            s ^= s << 13; s ^= s >>> 7; s ^= s << 17;
            Z_SIDE = s;
        }

        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();
        final RectF ovalTmp = new RectF();
        final RectF btnGames = new RectF();
        final RectF btnNew = new RectF();
        final RectF btnUndo = new RectF();
        final RectF btnShare = new RectF();
        final RectF btnHint = new RectF();
        final RectF titleHitRect = new RectF();
        final RectF btnContinue = new RectF();
        final RectF btnPickWhite = new RectF();
        final RectF btnPickBlack = new RectF();
        final RectF[] btnDiff = new RectF[4];
        final RectF btnStart = new RectF();
        final RectF[] btnPromo = new RectF[4];
        Runnable onGamesRequested;

        ChessView(Context ctx) {
            this(ctx, null);
        }

        ChessView(Context ctx, CoopSession coop) {
            this(ctx, coop, false);
        }

        ChessView(Context ctx, CoopSession coop, boolean localTwoPlayer) {
            super(ctx);
            this.coop = coop;
            this.localTwoPlayer = localTwoPlayer;
            theme = ArcadePrefs.chessTheme(ctx);
            for (int i = 0; i < 4; i++) {
                btnPromo[i] = new RectF();
                btnDiff[i] = new RectF();
            }
            initSynthAudio();
            if (coop == null) {
                setupInitialPosition();
                loadSavedSession();
            } else {
                coop.setListener(this);
                setupInitialPosition();
                humanSide = coop.mySide();
                selectedSide = humanSide;
                difficulty = 0;
                pickingSide = false;
                hasSavedGame = true;
                status = "Connecting to opponent...";
                applyCoopState(coop.state());
            }
        }

        void setOnGamesRequested(Runnable r) {
            onGamesRequested = r;
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

        // --- Crash-Proof Session Persistence (commits synchronously on every move & lifecycle exit) ---

        public synchronized void persistSessionNow() {
            if (coop != null) return;
            try {
                SharedPreferences prefs = getContext().getSharedPreferences(
                    PREFS_NAME + (localTwoPlayer ? "_local" : ""), Context.MODE_PRIVATE);
                SharedPreferences.Editor ed = prefs.edit();
                ed.putInt(KEY_SIDE, selectedSide);
                ed.putInt(KEY_DIFF, difficulty);

                boolean activeSession = (plyCount > 1)
                    || (!pickingSide && (localTwoPlayer || humanSide == -1));
                ed.putBoolean(KEY_HAS_SAVE, activeSession);
                if (activeSession) {
                    StringBuilder mHex = new StringBuilder();
                    StringBuilder sanStr = new StringBuilder();
                    for (int i = 1; i < plyCount; i++) {
                        if (i > 1) {
                            mHex.append(',');
                            sanStr.append('|');
                        }
                        mHex.append(Integer.toHexString(histMove[i]));
                        sanStr.append(histSan[i] != null ? histSan[i] : "");
                    }
                    ed.putInt("active_human_side", humanSide);
                    ed.putString(KEY_MOVES, mHex.toString());
                    ed.putString(KEY_SANS, sanStr.toString());
                    ed.putBoolean(KEY_GAME_OVER, gameOver);
                    ed.putString(KEY_STATUS, status);
                    ed.putString(KEY_PGN_RES, pgnResult);
                }
                ed.commit(); // Synchronous write to disk so even hard kill / force-stop preserves state
            } catch (Throwable ignored) {
            }
        }

        synchronized void loadSavedSession() {
            try {
                SharedPreferences prefs = getContext().getSharedPreferences(
                    PREFS_NAME + (localTwoPlayer ? "_local" : ""), Context.MODE_PRIVATE);
                selectedSide = prefs.getInt(KEY_SIDE, 1);
                difficulty = prefs.getInt(KEY_DIFF, 0); // Default Easy (0) on first launch
                if (difficulty < 0 || difficulty > 3) difficulty = 0;
                hasSavedGame = prefs.getBoolean(KEY_HAS_SAVE, false);

                if (hasSavedGame) {
                    int savedHuman = prefs.getInt("active_human_side", selectedSide);
                    String movesRaw = prefs.getString(KEY_MOVES, "");
                    String sansRaw = prefs.getString(KEY_SANS, "");

                    setupInitialPosition();
                    humanSide = localTwoPlayer ? 1 : savedHuman;

                    if (movesRaw != null && movesRaw.length() > 0) {
                        String[] parts = movesRaw.split(",");
                        String[] sanParts = (sansRaw != null) ? sansRaw.split("\\|", -1) : new String[0];
                        int side = 1;
                        for (int i = 0; i < parts.length; i++) {
                            if (parts[i].length() == 0) continue;
                            int m = (int) Long.parseLong(parts[i], 16);
                            String san = (i < sanParts.length && sanParts[i].length() > 0)
                                ? sanParts[i] : buildSan(m, side);
                            makeMoveUi(m, side);
                            lastFrom = m & 0xFF;
                            lastTo = (m >> 8) & 0xFF;
                            turn = -side;
                            saveSnapshot(m, san);
                            side = -side;
                        }
                    }

                    gameOver = prefs.getBoolean(KEY_GAME_OVER, false);
                    pgnResult = prefs.getString(KEY_PGN_RES, "*");
                    if (gameOver) {
                        status = prefs.getString(KEY_STATUS, "Game Over");
                    } else if (localTwoPlayer) {
                        status = localTurnText();
                    } else if (turn == humanSide) {
                        status = inCheckOn(b, humanSide)
                            ? "Check! Your turn"
                            : ("Your turn (" + (humanSide == 1 ? "White" : "Black") + ")");
                    } else {
                        status = "Bot thinking...";
                    }
                    // Open on menu with "Continue Game" ready anytime
                    pickingSide = true;
                } else {
                    status = localTwoPlayer
                        ? "Pass the device after each turn"
                        : "Choose your side & bot strength";
                }
            } catch (Throwable ignored) {
                hasSavedGame = false;
                setupInitialPosition();
            }
        }

        synchronized void resumeSavedGame() {
            if (!hasSavedGame) return;
            pickingSide = false;
            sel = -1;
            legalTargetCount = 0;
            pendingPromoFrom = -1;
            if (!gameOver && !localTwoPlayer && turn != humanSide) {
                status = "Bot thinking...";
                thinking = true;
                invalidate();
                startBotThread();
            } else {
                thinking = false;
                if (!gameOver) {
                    status = localTwoPlayer ? localTurnText()
                        : (inCheckOn(b, humanSide)
                            ? "Check! Your turn"
                            : ("Your turn (" + (humanSide == 1 ? "White" : "Black") + ")"));
                }
                invalidate();
            }
        }

        // --- Easter Egg Trigger & Particle Physics ---

        void onTitleTapped() {
            long now = SystemClock.uptimeMillis();
            if (now - lastTitleTapMs > 2500L) {
                titleTapCount = 0;
            }
            lastTitleTapMs = now;
            titleTapCount++;
            if (titleTapCount >= 10) {
                titleTapCount = 0;
                launchEasterEgg();
            }
        }

        void launchEasterEgg() {
            int w = Math.max(400, getWidth());
            int h = Math.max(800, getHeight());
            int[] palette = {
                0xFFFF5252, 0xFFFFD740, 0xFF69F0AE,
                0xFF40C4FF, 0xFFE040FB, 0xFFFF6E40, 0xFFF48FB1
            };
            int rng = (int) (System.nanoTime() ^ 0x9E3779B9);
            for (int i = 0; i < PARTICLE_COUNT; i++) {
                rng ^= rng << 13; rng ^= rng >>> 17; rng ^= rng << 5;
                float r1 = ((rng & 0xFFFF) / 65535f);
                rng ^= rng << 13; rng ^= rng >>> 17; rng ^= rng << 5;
                float r2 = ((rng & 0xFFFF) / 65535f);

                boolean balloon = (i % 3 == 0);
                pIsBalloon[i] = balloon;
                pX[i] = w * (0.08f + 0.84f * r1);
                pY[i] = h * (0.62f + 0.34f * r2);
                pVx[i] = (r2 - 0.5f) * w * 0.06f;
                pVy[i] = balloon ? (-h * (0.24f + 0.14f * r1)) : (-h * (0.14f + 0.18f * r2));
                pSize[i] = balloon ? (w * (0.036f + 0.016f * r1)) : (w * (0.014f + 0.010f * r2));
                pPhase[i] = r1 * 6.283f;
                pColor[i] = palette[i % palette.length];
            }
            easterActive = true;
            easterStartMs = SystemClock.uptimeMillis();
            playMoveSound(false);
            invalidate();
        }

        // --- Procedural Wood Audio ---

        void initSynthAudio() {
            try {
                sndMove = buildWoodTrack(false);
                sndCapture = buildWoodTrack(true);
            } catch (Throwable ignored) {
                sndMove = null;
                sndCapture = null;
            }
        }

        static AudioTrack buildWoodTrack(boolean capture) {
            int sr = 22050;
            int samples = capture ? 1850 : 1320;
            short[] pcm = new short[samples];
            int rng = capture ? 0x51A79E3 : 0x2F8C1B5;

            for (int i = 0; i < samples; i++) {
                double t = i / (double) sr;
                double envIn = (i < 10) ? (i / 10.0) : 1.0;

                rng ^= rng << 13;
                rng ^= rng >>> 17;
                rng ^= rng << 5;
                double noise = (rng & 0xFFFF) / 32768.0 - 1.0;

                double sig;
                if (!capture) {
                    double f0 = 188.0 + 78.0 * Math.exp(-t * 190.0);
                    double body = 0.62 * Math.exp(-t * 68.0) * Math.sin(2.0 * Math.PI * f0 * t);
                    double knock = 0.36 * Math.exp(-t * 118.0) * Math.sin(2.0 * Math.PI * 515.0 * t);
                    double click = 0.24 * Math.exp(-t * 260.0) * Math.sin(2.0 * Math.PI * 1340.0 * t);
                    double transientTap = 0.32 * Math.exp(-t * 820.0) * noise;
                    sig = envIn * (body + knock + click + transientTap);
                } else {
                    double strikeClick = 0.45 * Math.exp(-t * 220.0) * Math.sin(2.0 * Math.PI * 760.0 * t);
                    double strikeSnap = 0.38 * Math.exp(-t * 680.0) * noise;
                    double t2 = t - 0.023;
                    double boardThud = 0.0;
                    if (t2 > 0.0) {
                        double env2 = (t2 < 0.0005) ? (t2 / 0.0005) : 1.0;
                        double fLow = 165.0 + 70.0 * Math.exp(-t2 * 170.0);
                        boardThud = env2 * (
                            0.72 * Math.exp(-t2 * 60.0) * Math.sin(2.0 * Math.PI * fLow * t2)
                            + 0.34 * Math.exp(-t2 * 115.0) * Math.sin(2.0 * Math.PI * 440.0 * t2)
                            + 0.26 * Math.exp(-t2 * 750.0) * noise
                        );
                    }
                    sig = envIn * (strikeClick + strikeSnap) + boardThud;
                }

                int val = (int) Math.round(sig * 26000.0);
                if (val > 32767) val = 32767;
                else if (val < -32768) val = -32768;
                pcm[i] = (short) val;
            }

            AudioTrack track = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sr,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                samples * 2,
                AudioTrack.MODE_STATIC
            );
            track.write(pcm, 0, samples);
            return track;
        }

        void playMoveSound(boolean capture) {
            if (!ArcadeAudio.enabled()) return;
            try {
                AudioTrack tr = capture ? sndCapture : sndMove;
                if (tr == null) return;
                if (tr.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    tr.stop();
                }
                tr.reloadStaticData();
                tr.play();
            } catch (Throwable ignored) {
            }
        }

        void triggerMoveAnimation(int m, int side, int movingPiece, int capturedPiece) {
            int from = m & 0xFF, to = (m >> 8) & 0xFF, fl = (m >> 24) & 0xFF;
            animPiece = movingPiece;
            animFromSq = from;
            animToSq = to;
            animCapturedPiece = capturedPiece;
            animCapturedSq = (fl == 1) ? (to + (side == 1 ? 16 : -16)) : to;
            animIsCapture = (capturedPiece != 0);
            animRookPiece = 0;
            if (fl == 2) {
                animRookPiece = 4 * side;
                animRookFromSq = to + 1;
                animRookToSq = to - 1;
            } else if (fl == 3) {
                animRookPiece = 4 * side;
                animRookFromSq = to - 2;
                animRookToSq = to + 1;
            }
            animStartMs = SystemClock.uptimeMillis();
            animSoundPlayed = false;
        }

        synchronized void cancelBot() {
            searchGen++;
            abort = true;
            thinking = false;
            if (botThread != null) {
                botThread.interrupt();
                botThread = null;
            }
        }

        synchronized void setupInitialPosition() {
            cancelBot();
            clearHint();
            confetti.stop();
            Arrays.fill(b, 0);
            int[] back = {4, 2, 3, 5, 6, 3, 2, 4};
            for (int f = 0; f < 8; f++) {
                b[f] = -back[f];
                b[16 + f] = -1;
                b[96 + f] = 1;
                b[112 + f] = back[f];
            }
            castle = 15;
            ep = -1;
            turn = 1;
            halfMoveClock = 0;
            sel = -1;
            legalTargetCount = 0;
            pendingPromoFrom = -1;
            animPiece = 0;
            lastFrom = -1;
            lastTo = -1;
            gameOver = false;
            pgnResult = "*";
            coopWinner = "";
            curHash = computeHash(b, castle, ep, turn);
            plyCount = 0;
            saveSnapshot(0, "");
        }

        String localTurnText() {
            String side = turn == 1 ? "White" : "Black";
            return inCheckOn(b, turn) ? "Check! " + side + "'s turn" : side + "'s turn";
        }

        synchronized void startNewGame(int chosenSide, int chosenDiff) {
            selectedSide = chosenSide;
            humanSide = localTwoPlayer ? 1 : chosenSide;
            difficulty = chosenDiff;
            pickingSide = false;
            clearHint();
            setupInitialPosition();
            hasSavedGame = true;

            if (localTwoPlayer) {
                status = localTurnText();
                persistSessionNow();
                invalidate();
            } else if (humanSide == 1) {
                status = "Your turn (White) · " + DIFF_NAMES[difficulty];
                persistSessionNow();
                invalidate();
            } else {
                status = "Bot thinking...";
                thinking = true;
                persistSessionNow();
                invalidate();
                startBotThread();
            }
        }

        boolean hintAvailable() {
            return coop == null && !localTwoPlayer && !pickingSide && !gameOver
                && !thinking && turn == humanSide
                && ArcadePrefs.assistantEnabled(getContext());
        }

        void clearHint() {
            hintGen++;
            hintThinking = false;
            hintFrom = -1;
            hintTo = -1;
            hintUntil = 0L;
        }

        void requestHint() {
            if (!hintAvailable()) return;
            final int gen = ++hintGen;
            final int side = turn;
            synchronized (this) {
                System.arraycopy(b, 0, sb, 0, 128);
                sCastle = castle;
                sEp = ep;
                sHalfClock = halfMoveClock;
                sHash = curHash;
                sHistLen = plyCount;
                System.arraycopy(histHash, 0, sHistHash, 0, plyCount);
            }
            hintThinking = true;
            hintFrom = -1;
            hintTo = -1;
            status = "Looking for a move...";
            invalidate();
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    bestMoveRoot = 0;
                    nodes = 0;
                    abort = false;
                    deadline = System.currentTimeMillis() + 420L;
                    for (int i = 0; i < MAX_PLY; i++) killer1[i] = killer2[i] = 0;
                    int hintBest = 0;
                    for (int d = 1; d <= 5; d++) {
                        int sc = search(d, -INF, INF, side, 0, true);
                        if (bestMoveRoot != 0) hintBest = bestMoveRoot;
                        if (abort && d > 1) break;
                        if (Math.abs(sc) >= MATE - 64) break;
                    }
                    final int move = hintBest;
                    post(new Runnable() {
                        @Override
                        public void run() {
                            if (gen != hintGen || !hintAvailable()) {
                                hintThinking = false;
                                invalidate();
                                return;
                            }
                            hintThinking = false;
                            if (move != 0) {
                                hintFrom = move & 0xFF;
                                hintTo = (move >> 8) & 0xFF;
                                hintUntil = SystemClock.uptimeMillis() + 6000L;
                                status = "Hint: the glowing move is strong";
                            } else {
                                status = inCheckOn(b, humanSide) ? "Check! Your turn" : "Your turn";
                            }
                            invalidate();
                        }
                    });
                }
            }, "chess-hint");
            t.setDaemon(true);
            t.start();
        }

        void showRematchPrompt() {
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
                        CoopSession.clearSaved(getContext(), "chess");
                        if (onGamesRequested != null) onGamesRequested.run();
                    }
                })
                .setNeutralButton("Cancel", null)
                .show();
        }

        void confirmRestart() {
            if (coop != null || pickingSide) return;
            if (getWindowToken() == null) {
                restartOfflineGame();
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle("Start a new game?");
            builder.setMessage("Your current match will end. Saved progress is replaced.");
            builder.setPositiveButton("New Game", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    restartOfflineGame();
                }
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
        }

        void restartOfflineGame() {
            cancelBot();
            confetti.stop();
            clearHint();
            if (localTwoPlayer) {
                startNewGame(1, difficulty);
                return;
            }
            pickingSide = true;
            status = "Choose your side & bot strength";
            invalidate();
        }

        void startBotThread() {
            thinking = true;
            botThread = new Thread(this);
            botThread.start();
        }

        void saveSnapshot(int moveMade, String san) {
            if (plyCount >= MAX_GAME_PLIES) return;
            System.arraycopy(b, 0, histBoard[plyCount], 0, 128);
            histCastle[plyCount] = castle;
            histEp[plyCount] = ep;
            histTurn[plyCount] = turn;
            histHalfClock[plyCount] = halfMoveClock;
            histLastFrom[plyCount] = lastFrom;
            histLastTo[plyCount] = lastTo;
            histHash[plyCount] = curHash;
            histMove[plyCount] = moveMade;
            histSan[plyCount] = san;
            plyCount++;
        }

        synchronized void undoMove() {
            if (pickingSide || plyCount <= 1) return;
            if (humanSide == -1 && plyCount == 2 && !thinking) return;

            boolean wasThinking = thinking;
            cancelBot();
            confetti.stop();
            pendingPromoFrom = -1;
            animPiece = 0;
            sel = -1;
            legalTargetCount = 0;

            int stepsBack = localTwoPlayer ? 1 : (wasThinking ? 1 : 2);
            int targetIdx = plyCount - 1 - stepsBack;
            int minIdx = (localTwoPlayer || humanSide == 1) ? 0 : 1;
            if (targetIdx < minIdx) targetIdx = minIdx;

            System.arraycopy(histBoard[targetIdx], 0, b, 0, 128);
            castle = histCastle[targetIdx];
            ep = histEp[targetIdx];
            turn = histTurn[targetIdx];
            halfMoveClock = histHalfClock[targetIdx];
            lastFrom = histLastFrom[targetIdx];
            lastTo = histLastTo[targetIdx];
            curHash = histHash[targetIdx];
            plyCount = targetIdx + 1;
            gameOver = false;
            pgnResult = "*";
            hasSavedGame = (plyCount > 1) || (localTwoPlayer && plyCount > 0) || (humanSide == -1);
            status = localTwoPlayer ? localTurnText()
                : (inCheckOn(b, humanSide)
                    ? "Check! Your turn"
                    : ("Your turn (" + (humanSide == 1 ? "White" : "Black") + ") · " + DIFF_NAMES[difficulty]));
            persistSessionNow();
            invalidate();
        }

        void sharePgn() {
            StringBuilder sbPgn = new StringBuilder();
            sbPgn.append("[Event \"TinyArcade ")
                .append(localTwoPlayer ? "Local Co-op" : "Offline (" + DIFF_NAMES[difficulty] + ")")
                .append("\"]\n");
            sbPgn.append("[Site \"Android\"]\n");
            if (localTwoPlayer) {
                sbPgn.append("[White \"Player 1\"]\n");
                sbPgn.append("[Black \"Player 2\"]\n");
            } else {
                sbPgn.append("[White \"").append(humanSide == 1 ? "Human" : "TinyArcade (" + DIFF_NAMES[difficulty] + ")").append("\"]\n");
                sbPgn.append("[Black \"").append(humanSide == -1 ? "Human" : "TinyArcade (" + DIFF_NAMES[difficulty] + ")").append("\"]\n");
            }
            sbPgn.append("[Result \"").append(pgnResult).append("\"]\n\n");

            for (int i = 1; i < plyCount; i++) {
                if ((i & 1) == 1) {
                    sbPgn.append((i + 1) / 2).append(". ");
                }
                sbPgn.append(histSan[i]).append(' ');
            }
            sbPgn.append(pgnResult);

            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT, "TinyArcade Game (PGN)");
            send.putExtra(Intent.EXTRA_TEXT, sbPgn.toString().trim());
            Intent chooser = Intent.createChooser(send, "Share Chess History (PGN)");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(chooser);
        }

        static long computeHash(int[] board, int cstl, int epSq, int sideToMove) {
            long h = Z_CASTLE[cstl];
            if (sideToMove == -1) h ^= Z_SIDE;
            if (epSq != -1) h ^= Z_EP[epSq];
            for (int r = 0; r < 8; r++) {
                for (int f = 0; f < 8; f++) {
                    int sq = (r << 4) | f;
                    if (board[sq] != 0) h ^= Z_PC[board[sq] + 6][sq];
                }
            }
            return h;
        }

        void computeLegalTargetsForSelected() {
            legalTargetCount = 0;
            if (sel == -1) return;
            int n = genMovesOn(b, castle, ep, turn, 0);
            for (int i = 0; i < n; i++) {
                int m = moveBuf[0][i];
                if ((m & 0xFF) != sel) continue;
                int oc = castle, oep = ep, ohc = halfMoveClock;
                long oh = curHash;
                makeMoveUi(m, turn);
                boolean ok = !inCheckOn(b, turn);
                unmakeMoveUi(m, turn, oc, oep, ohc, oh);
                if (!ok) continue;

                int to = (m >> 8) & 0xFF;
                boolean already = false;
                for (int k = 0; k < legalTargetCount; k++) {
                    if (legalTargets[k] == to) { already = true; break; }
                }
                if (!already && legalTargetCount < 32) {
                    legalTargets[legalTargetCount] = to;
                    int cap = (byte) ((m >> 16) & 0xFF);
                    int fl = (m >> 24) & 0xFF;
                    legalIsCap[legalTargetCount] = (cap != 0 || fl == 1);
                    legalTargetCount++;
                }
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            float margin = Math.min(w, h) * 0.032f;
            float boardW = Math.min(w - margin * 2f, h * 0.70f);
            float frame = Math.min(margin, boardW * 0.04f);
            float sq = boardW / 8f;
            float boardLeft = (w - boardW) * 0.5f;
            float outerTop = Math.max((h - (boardW + frame * 2f)) * 0.5f, sq * 2.45f);
            float boardTop = outerTop + frame;
            float trayH = sq * 0.72f;
            float btnH = sq * 0.78f;
            float btnY = Math.max(sq * 0.22f, outerTop - trayH - sq * 0.85f - btnH);

            c.drawColor(ArcadeUi.BG);
            if (!pickingSide && !gameOver) {
                ArcadeUi.drawTurnGlow(c, p, w, h,
                    turn == 1 ? 0x30F0E1C2 : 0x303F6FA8, turn != humanSide);
            }

            // Top Toolbar: [Games]  [New Game]  [Undo]  (Hint)  [Share]
            float pad = sq * 0.14f;
            float gap = sq * 0.12f;
            boolean showHint = hintAvailable();
            int buttonCount = showHint ? 5 : 4;
            float btnW = (w - pad * 2f - gap * (buttonCount - 1)) / buttonCount;
            btnGames.set(pad, btnY, pad + btnW, btnY + btnH);
            btnNew.set(pad + btnW + gap, btnY, pad + btnW * 2f + gap, btnY + btnH);
            btnUndo.set(pad + btnW * 2f + gap * 2f, btnY, pad + btnW * 3f + gap * 2f, btnY + btnH);
            if (showHint) {
                btnHint.set(pad + btnW * 3f + gap * 3f, btnY,
                    pad + btnW * 4f + gap * 3f, btnY + btnH);
                btnShare.set(pad + btnW * 4f + gap * 4f, btnY, w - pad, btnY + btnH);
            } else {
                btnHint.setEmpty();
                btnShare.set(pad + btnW * 3f + gap * 3f, btnY, w - pad, btnY + btnH);
            }

            boolean canUndo = coop == null && !pickingSide
                && (localTwoPlayer
                    ? plyCount > 1
                    : (plyCount > (humanSide == 1 ? 1 : 2) || (thinking && plyCount > 1)));
            ArcadeUi.drawIconButton(c, p, btnGames, ArcadeUi.ICON_HOME, true, ArcadeUi.SLATE);
            ArcadeUi.drawIconButton(c, p, btnNew,
                coop != null ? ArcadeUi.ICON_CLOSE : ArcadeUi.ICON_PLUS, true, ArcadeUi.BLUE);
            ArcadeUi.drawIconButton(c, p, btnUndo, ArcadeUi.ICON_UNDO, canUndo, ArcadeUi.BLUE);
            if (showHint) {
                ArcadeUi.drawIconButton(c, p, btnHint, ArcadeUi.ICON_BULB,
                    !hintThinking, ArcadeUi.GOLD);
            }
            ArcadeUi.drawIconButton(c, p, btnShare, ArcadeUi.ICON_SHARE, plyCount > 1, ArcadeUi.GREEN);

            // Status Banner
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(0xFFF0F0F0);
            p.setTextSize(sq * 0.35f);
            c.drawText(status, w / 2f, outerTop - trayH - sq * 0.22f, p);
            p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            p.setTextAlign(Paint.Align.RIGHT);
            p.setTextSize(sq * 0.24f);
            p.setColor(ArcadeUi.ORANGE_L);
            c.drawText(ChessTheme.name(theme), w - pad, outerTop - trayH - sq * 0.22f - sq * 0.42f, p);
            p.setTextAlign(Paint.Align.CENTER);

            // Eaten pieces aligned along top and bottom borders of the board
            int topSide = -humanSide;
            int botSide = humanSide;
            drawCapturedBorder(c, topSide, botSide, outerTop - trayH * 0.90f, sq);
            drawCapturedBorder(c, botSide, topSide, outerTop + boardW + frame * 2f + trayH * 0.08f, sq);

            // Oak Outer Frame with Coordinate Border (A-H, 1-8)
            p.setStyle(Paint.Style.FILL);
            float frameLeft = boardLeft - frame;
            float frameRight = boardLeft + boardW + frame;
            float frameBottom = outerTop + boardW + frame * 2f;
            p.setColor(ChessTheme.frame(theme));
            c.drawRect(frameLeft, outerTop, frameRight, frameBottom, p);

            p.setStrokeWidth(Math.max(2f, w * 0.004f));
            p.setStyle(Paint.Style.STROKE);
            p.setColor(ChessTheme.frameLine(theme));
            c.drawRect(frameLeft + 1f, outerTop + 1f, frameRight - 1f, frameBottom - 1f, p);
            c.drawRect(boardLeft - 1f, boardTop - 1f, boardLeft + boardW + 1f, boardTop + boardW + 1f, p);

            p.setStyle(Paint.Style.FILL);
            p.setColor(ChessTheme.coord(theme));
            p.setTextSize(frame * 0.62f);
            p.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics cfm = p.getFontMetrics();
            float cyOff = -(cfm.ascent + cfm.descent) / 2f;

            for (int i = 0; i < 8; i++) {
                int fileIdx = (humanSide == 1) ? i : (7 - i);
                int rankNum = (humanSide == 1) ? (8 - i) : (1 + i);
                String fLbl = String.valueOf((char) ('A' + fileIdx));
                String rLbl = String.valueOf(rankNum);

                float cx = boardLeft + (i + 0.5f) * sq;
                c.drawText(fLbl, cx, outerTop + frame * 0.5f + cyOff, p);
                c.drawText(fLbl, cx, frameBottom - frame * 0.5f + cyOff, p);

                float cy = boardTop + (i + 0.5f) * sq + cyOff;
                c.drawText(rLbl, frameLeft + frame * 0.5f, cy, p);
                c.drawText(rLbl, frameRight - frame * 0.5f, cy, p);
            }

            // Smooth piece animation timing
            float animEase = 1f;
            float animLift = 0f;
            boolean animating = false;
            if (animPiece != 0) {
                float rawT = (SystemClock.uptimeMillis() - animStartMs) / (float) ANIM_DURATION_MS;
                if (!animSoundPlayed && rawT >= 0.72f) {
                    animSoundPlayed = true;
                    playMoveSound(animIsCapture);
                }
                if (rawT >= 1.0f) {
                    if (!animSoundPlayed) {
                        animSoundPlayed = true;
                        playMoveSound(animIsCapture);
                    }
                    animPiece = 0;
                    animRookPiece = 0;
                } else {
                    animating = true;
                    float inv = 1f - rawT;
                    animEase = 1f - inv * inv * inv;
                    animLift = (float) Math.sin(Math.PI * rawT) * 0.14f;
                }
            }

            int checkedKingSq = -1;
            if (!pickingSide && inCheckOn(b, turn)) {
                for (int i = 0; i < 128; i++) {
                    if ((i & 0x88) != 0) { i += 7; continue; }
                    if (b[i] == 6 * turn) { checkedKingSq = i; break; }
                }
            }

            // 64 Wood-Grained Squares + Reticles + Vector Pieces
            for (int dispR = 0; dispR < 8; dispR++) {
                for (int dispF = 0; dispF < 8; dispF++) {
                    int r = (humanSide == 1) ? dispR : (7 - dispR);
                    int f = (humanSide == 1) ? dispF : (7 - dispF);
                    int idx = (r << 4) | f;
                    boolean isLight = ((r + f) & 1) == 0;

                    float x = boardLeft + dispF * sq;
                    float y = boardTop + dispR * sq;

                    p.setStyle(Paint.Style.FILL);
                    p.setColor(isLight ? ChessTheme.light(theme) : ChessTheme.dark(theme));
                    c.drawRect(x, y, x + sq, y + sq, p);

                    drawSquareWoodGrain(c, x, y, sq, idx, isLight);

                    if (idx == checkedKingSq) {
                        p.setStyle(Paint.Style.FILL);
                        p.setColor(0x66D93829);
                        c.drawRect(x, y, x + sq, y + sq, p);
                    }
                    if (idx == lastFrom) drawCornerReticle(c, x, y, sq, 0xFF3E7D32);
                    if (idx == lastTo) drawSquareFrame(c, x, y, sq, 0xFF3E7D32, sq * 0.055f);
                    if (idx == sel) drawSquareFrame(c, x, y, sq, 0xFFD4AF37, sq * 0.06f);

                    int piece = b[idx];
                    if (animating) {
                        if (idx == animToSq || (animRookPiece != 0 && idx == animRookToSq)) {
                            piece = 0;
                        }
                        if (idx == animCapturedSq && animCapturedPiece != 0 && animEase < 0.85f) {
                            piece = animCapturedPiece;
                        }
                    }
                    if (piece != 0) {
                        drawVectorPiece(c, piece, x, y, sq, false);
                    }
                }
            }

            // Allowed Movement Dots & Capture Frames
            if (sel != -1 && pendingPromoFrom == -1) {
                for (int i = 0; i < legalTargetCount; i++) {
                    int t = legalTargets[i];
                    int r = t >> 4, f = t & 7;
                    int dispR = (humanSide == 1) ? r : (7 - r);
                    int dispF = (humanSide == 1) ? f : (7 - f);
                    float x = boardLeft + dispF * sq;
                    float y = boardTop + dispR * sq;
                    float cx = x + sq * 0.5f;
                    float cy = y + sq * 0.5f;

                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0xCC2E7D32);
                    c.drawCircle(cx, cy, sq * 0.14f, p);

                    if (legalIsCap[i]) {
                        drawSquareFrame(c, x, y, sq, 0xFFD4AF37, sq * 0.055f);
                    }
                }
            }

            drawMoveHint(c, boardLeft, boardTop, sq);

            if (animating) {
                if (animRookPiece != 0) {
                    drawAnimatedPiece(c, animRookPiece, animRookFromSq, animRookToSq,
                        boardLeft, boardTop, sq, animEase, animLift * 0.5f);
                }
                drawAnimatedPiece(c, animPiece, animFromSq, animToSq,
                    boardLeft, boardTop, sq, animEase, animLift);
                postInvalidateOnAnimation();
            }

            // Promotion Choice Bar
            if (pendingPromoFrom != -1) {
                float boxW = sq * 4.4f, boxH = sq * 1.25f;
                float bx = (w - boxW) / 2f, by = boardTop + (boardW - boxH) / 2f;
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xEE22252A);
                c.drawRoundRect(new RectF(bx, by, bx + boxW, by + boxH), 18f, 18f, p);

                int[] promoTypes = {5, 4, 3, 2};
                for (int i = 0; i < 4; i++) {
                    float cellX = bx + sq * 0.2f + i * sq;
                    btnPromo[i].set(cellX, by + sq * 0.12f, cellX + sq, by + boxH - sq * 0.12f);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0xFF3A3F47);
                    c.drawRoundRect(btnPromo[i], 12f, 12f, p);
                    int pc = promoTypes[i] * turn;
                    drawVectorPiece(c, pc, btnPromo[i].left, btnPromo[i].top, sq, false);
                }
            }

            // Pre-Game Setup Overlay (Continue + Color + Difficulty + Start + 10-Tap Easter Egg Target)
            if (pickingSide) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xD6111111);
                c.drawRect(frameLeft, outerTop, frameRight, frameBottom, p);

                float cardW = Math.min(w * 0.88f, boardW);
                float cardH = localTwoPlayer
                    ? (hasSavedGame ? sq * 4.00f : sq * 2.80f)
                    : (hasSavedGame ? sq * 6.35f : sq * 5.25f);
                float cx = (w - cardW) / 2f;
                float cy = outerTop + (boardW + frame * 2f - cardH) / 2f;

                p.setColor(0xFF23262C);
                c.drawRoundRect(new RectF(cx, cy, cx + cardW, cy + cardH), 26f, 26f, p);

                // Tappable "TinyArcade" Header (10 taps triggers Easter Egg)
                float titleCenterY = cy + sq * 0.68f;
                titleHitRect.set(cx + cardW * 0.15f, cy + sq * 0.12f, cx + cardW * 0.85f, cy + sq * 1.05f);
                p.setTextAlign(Paint.Align.CENTER);
                p.setColor(0xFFFFFFFF);
                p.setTextSize(sq * 0.50f);
                c.drawText("TinyArcade", w / 2f, titleCenterY, p);

                float bPad = sq * 0.30f;
                float curY = cy + sq * 1.05f;

                if (localTwoPlayer) {
                    btnPickWhite.setEmpty();
                    btnPickBlack.setEmpty();
                    for (int i = 0; i < 4; i++) btnDiff[i].setEmpty();

                    if (hasSavedGame) {
                        float contH = sq * 0.86f;
                        btnContinue.set(cx + bPad, curY, cx + cardW - bPad, curY + contH);
                        drawButton(c, btnContinue,
                            gameOver ? "View Final Board" : "Continue game", true, ArcadeUi.GREEN);
                        curY += contH + sq * 0.32f;
                    } else {
                        btnContinue.setEmpty();
                    }

                    p.setTypeface(android.graphics.Typeface.DEFAULT);
                    p.setTextAlign(Paint.Align.CENTER);
                    p.setColor(ArcadeUi.MUTED);
                    p.setTextSize(sq * 0.30f);
                    c.drawText("2 players · pass the device after each turn",
                        w / 2f, curY + sq * 0.28f, p);
                    curY += sq * 0.66f;

                    float startH = sq * 0.94f;
                    btnStart.set(cx + bPad, curY, cx + cardW - bPad, curY + startH);
                    drawButton(c, btnStart, "Start Co-op Game", true, ArcadeUi.ORANGE);
                    return;
                }

                // Optional Continue Button if a saved session exists
                if (hasSavedGame) {
                    float contH = sq * 0.86f;
                    btnContinue.set(cx + bPad, curY, cx + cardW - bPad, curY + contH);
                    int fullMoves = Math.max(1, plyCount / 2);
                    String contLabel = "Continue (" + (humanSide == 1 ? "White" : "Black")
                        + " · " + DIFF_NAMES[difficulty] + " · Move " + fullMoves + ")";
                    drawButton(c, btnContinue, contLabel, true, ArcadeUi.GREEN);
                    curY += contH + sq * 0.28f;
                } else {
                    btnContinue.setEmpty();
                }

                // Section 1: Color Selector
                p.setTextAlign(Paint.Align.LEFT);
                p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                p.setColor(ArcadeUi.MUTED);
                p.setTextSize(sq * 0.26f);
                c.drawText("YOUR SIDE", cx + bPad, curY + sq * 0.22f, p);
                curY += sq * 0.34f;

                float sideBtnW = (cardW - bPad * 3f) / 2f;
                float sideBtnH = sq * 0.96f;
                btnPickWhite.set(cx + bPad, curY, cx + bPad + sideBtnW, curY + sideBtnH);
                btnPickBlack.set(cx + bPad * 2f + sideBtnW, curY, cx + cardW - bPad, curY + sideBtnH);

                drawSelectableButton(c, btnPickWhite, "White", selectedSide == 1, ArcadeUi.GREEN);
                drawSelectableButton(c, btnPickBlack, "Black", selectedSide == -1, ArcadeUi.GREEN);
                curY += sideBtnH + sq * 0.28f;

                // Section 2: Difficulty Selector
                p.setTextAlign(Paint.Align.LEFT);
                p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                p.setColor(ArcadeUi.MUTED);
                p.setTextSize(sq * 0.26f);
                c.drawText("LEVEL", cx + bPad, curY + sq * 0.22f, p);
                curY += sq * 0.34f;

                float diffGap = sq * 0.14f;
                float diffW = (cardW - bPad * 2f - diffGap * 3f) / 4f;
                float diffH = sq * 0.84f;
                for (int i = 0; i < 4; i++) {
                    float dx = cx + bPad + i * (diffW + diffGap);
                    btnDiff[i].set(dx, curY, dx + diffW, curY + diffH);
                    drawSelectableButton(c, btnDiff[i], DIFF_NAMES[i],
                        difficulty == i, ArcadeUi.BLUE);
                }
                curY += diffH + sq * 0.34f;

                // Start New Game Button
                float startH = sq * 0.94f;
                btnStart.set(cx + bPad, curY, cx + cardW - bPad, curY + startH);
                drawButton(c, btnStart, "Play New Session", true, ArcadeUi.ORANGE);
            }

            // Easter Egg Overlay: Balloons, Confetti & "Developed with ❤️" Fading Upward
            if (easterActive) {
                drawEasterEggOverlay(c, w, h, sq);
            }

            if (confetti.draw(c)) {
                postInvalidateOnAnimation();
            }

            if (coop != null && !gameOver && !coop.opponentOnline()) {
                ArcadeUi.drawCoopOffline(c, p, w, h);
            }
            if (coop != null && !gameOver) postInvalidateDelayed(1000L);
        }

        void drawEasterEggOverlay(Canvas c, int w, int h, float sq) {
            long elapsed = SystemClock.uptimeMillis() - easterStartMs;
            if (elapsed >= EASTER_DURATION_MS) {
                easterActive = false;
                return;
            }
            float t = elapsed / (float) EASTER_DURATION_MS; // 0.0 -> 1.0
            float alphaFactor = (t < 0.12f) ? (t / 0.12f) : ((t > 0.65f) ? Math.max(0f, (1f - t) / 0.35f) : 1f);
            int alphaByte = Math.min(255, Math.max(0, (int) (alphaFactor * 255f)));

            float sec = elapsed / 1000f;

            // Draw rising balloons & fluttering confetti
            for (int i = 0; i < PARTICLE_COUNT; i++) {
                float sway = (float) Math.sin(sec * 3.2f + pPhase[i]) * (pIsBalloon[i] ? 24f : 18f);
                float x = pX[i] + pVx[i] * sec + sway;
                float y = pY[i] + pVy[i] * sec;
                int col = (pColor[i] & 0x00FFFFFF) | (alphaByte << 24);

                if (pIsBalloon[i]) {
                    float r = pSize[i];
                    // Balloon string
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(Math.max(1.5f, r * 0.07f));
                    p.setColor((0xDDDDDDDD & 0x00FFFFFF) | (alphaByte << 24));
                    path.reset();
                    path.moveTo(x, y + r * 1.22f);
                    path.quadTo(x - sway * 0.35f, y + r * 1.85f, x + sway * 0.25f, y + r * 2.5f);
                    c.drawPath(path, p);

                    // Balloon body
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(col);
                    ovalTmp.set(x - r, y - r * 1.2f, x + r, y + r * 1.2f);
                    c.drawOval(ovalTmp, p);

                    // Balloon knot
                    path.reset();
                    path.moveTo(x - r * 0.16f, y + r * 1.26f);
                    path.lineTo(x + r * 0.16f, y + r * 1.26f);
                    path.lineTo(x, y + r * 1.15f);
                    path.close();
                    c.drawPath(path, p);

                    // Gloss highlight
                    p.setColor((0x66FFFFFF & 0x00FFFFFF) | ((alphaByte / 2) << 24));
                    ovalTmp.set(x - r * 0.55f, y - r * 0.78f, x - r * 0.22f, y - r * 0.25f);
                    c.drawOval(ovalTmp, p);
                } else {
                    // Rotating confetti ribbon
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(col);
                    float sz = pSize[i];
                    float spin = (float) Math.cos(sec * 7.5f + pPhase[i]);
                    ovalTmp.set(x - sz, y - sz * 0.45f * Math.abs(spin), x + sz, y + sz * 0.45f * Math.abs(spin));
                    c.drawRoundRect(ovalTmp, 4f, 4f, p);
                }
            }

            // Floating "Developed with ❤️" pill drifting smoothly upward to the top and fading out
            float bannerY = h * (0.56f - 0.44f * t);
            float pillW = w * 0.72f;
            float pillH = sq * 0.96f;
            float pillX = (w - pillW) * 0.5f;

            p.setStyle(Paint.Style.FILL);
            p.setColor(((int) (alphaFactor * 220f) << 24) | 0x181B20);
            c.drawRoundRect(new RectF(pillX, bannerY - pillH * 0.5f, pillX + pillW, bannerY + pillH * 0.5f), 28f, 28f, p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.5f);
            p.setColor((alphaByte << 24) | 0xFF6B8B);
            c.drawRoundRect(new RectF(pillX, bannerY - pillH * 0.5f, pillX + pillW, bannerY + pillH * 0.5f), 28f, 28f, p);

            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor((alphaByte << 24) | 0xFFFFFF);
            p.setTextSize(sq * 0.38f);
            Paint.FontMetrics fm = p.getFontMetrics();
            float yOff = -(fm.ascent + fm.descent) / 2f;
            //c.drawText("Developed with \u2764\uFE0F", w * 0.5f, bannerY + yOff, p);
		c.drawText("\u265f\ufe0f", w * 0.5f, bannerY + yOff, p);

            postInvalidateOnAnimation();
        }

        void drawMoveHint(Canvas c, float left, float top, float sq) {
            if (hintFrom < 0 || hintTo < 0) return;
            long now = SystemClock.uptimeMillis();
            if (now > hintUntil) {
                hintFrom = -1;
                hintTo = -1;
                return;
            }
            float pulse = 0.58f + 0.42f * (float) Math.sin(now / 140.0);
            int[] squares = {hintFrom, hintTo};
            for (int i = 0; i < squares.length; i++) {
                int idx = squares[i];
                int r = idx >> 4;
                int f = idx & 7;
                int dispR = humanSide == 1 ? r : 7 - r;
                int dispF = humanSide == 1 ? f : 7 - f;
                float x = left + dispF * sq;
                float y = top + dispR * sq;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(2.5f, sq * 0.07f));
                p.setColor(i == 0 ? 0xFFF0D060 : 0xFF7CE08A);
                p.setAlpha((int) (255 * pulse));
                c.drawCircle(x + sq * 0.5f, y + sq * 0.5f,
                    sq * (i == 0 ? 0.40f : 0.30f), p);
                if (i == 1) {
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(x + sq * 0.5f, y + sq * 0.5f, sq * 0.12f, p);
                }
            }
            p.setStyle(Paint.Style.FILL);
            p.setAlpha(255);
            postInvalidateDelayed(70L);
        }

        void drawSelectableButton(Canvas c, RectF r, String label, boolean selected, int selectedColor) {
            ArcadeUi.drawSelectableButton(c, p, r, label, selected, selectedColor);
        }

        void drawSquareWoodGrain(Canvas c, float x, float y, float sq, int seed, boolean isLight) {
            int h = seed * 747796405 + 289133645;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, sq * 0.018f));
            for (int k = 0; k < 5; k++) {
                h ^= h << 13;
                h ^= h >>> 17;
                h ^= h << 5;
                float frac = ((h & 0xFF) / 255f);
                float gy = y + sq * (0.12f + 0.76f * ((k + frac * 0.6f) / 5f));
                p.setColor(isLight ? ChessTheme.grainLight(theme) : ChessTheme.grainDark(theme));
                c.drawLine(x + sq * 0.04f, gy, x + sq * 0.96f, gy, p);
            }
        }

        void drawCornerReticle(Canvas c, float x, float y, float sq, int color) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(2f, sq * 0.055f));
            p.setColor(color);
            float arm = sq * 0.22f;
            float cx = x + sq * 0.5f, cy = y + sq * 0.5f;
            float gap = sq * 0.07f;

            c.drawLine(cx - gap, cy - gap - arm, cx - gap, cy - gap, p);
            c.drawLine(cx - gap - arm, cy - gap, cx - gap, cy - gap, p);
            c.drawLine(cx + gap, cy - gap - arm, cx + gap, cy - gap, p);
            c.drawLine(cx + gap, cy - gap, cx + gap + arm, cy - gap, p);
            c.drawLine(cx - gap, cy + gap, cx - gap, cy + gap + arm, p);
            c.drawLine(cx - gap - arm, cy + gap, cx - gap, cy + gap, p);
            c.drawLine(cx + gap, cy + gap, cx + gap, cy + gap + arm, p);
            c.drawLine(cx + gap, cy + gap, cx + gap + arm, cy + gap, p);
        }

        void drawSquareFrame(Canvas c, float x, float y, float sq, int color, float stroke) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(stroke);
            p.setColor(color);
            float half = stroke * 0.5f;
            c.drawRect(x + half, y + half, x + sq - half, y + sq - half, p);
        }

        void drawAnimatedPiece(Canvas c, int piece, int fromSq, int toSq,
                               float boardLeft, float boardTop, float sq, float ease, float lift) {
            int fr = fromSq >> 4, ff = fromSq & 7;
            int tr = toSq >> 4, tf = toSq & 7;
            int dFr = (humanSide == 1) ? fr : (7 - fr);
            int dFf = (humanSide == 1) ? ff : (7 - ff);
            int dTr = (humanSide == 1) ? tr : (7 - tr);
            int dTf = (humanSide == 1) ? tf : (7 - tf);

            float sx = boardLeft + dFf * sq;
            float sy = boardTop + dFr * sq;
            float ex = boardLeft + dTf * sq;
            float ey = boardTop + dTr * sq;

            float scale = 1f + lift;
            float drawSq = sq * scale;
            float cx = sx + (ex - sx) * ease + sq * 0.5f;
            float cy = sy + (ey - sy) * ease + sq * 0.5f - lift * sq * 0.35f;

            drawVectorPiece(c, piece, cx - drawSq * 0.5f, cy - drawSq * 0.5f, drawSq, true);
        }

        void drawVectorPiece(Canvas c, int piece, float x, float y, float sq, boolean elevated) {
            boolean isWhite = piece > 0;
            int type = Math.abs(piece);

            int fillCol = isWhite ? 0xFFFDFBF7 : 0xFF171717;
            int strokeCol = isWhite ? 0xFF141414 : 0xFFF5EFE6;
            int detailCol = isWhite ? 0xFF141414 : 0xFFF5EFE6;
            float strokeW = Math.max(1.4f, sq * 0.036f);

            p.setStyle(Paint.Style.FILL);
            p.setColor(elevated ? 0x55000000 : 0x38000000);
            float shOff = elevated ? sq * 0.06f : sq * 0.02f;
            ovalTmp.set(x + sq * 0.18f, y + sq * 0.81f + shOff, x + sq * 0.82f, y + sq * 0.91f + shOff);
            c.drawOval(ovalTmp, p);

            path.reset();
            if (type == 1) {
                path.moveTo(x + sq * 0.24f, y + sq * 0.85f);
                path.lineTo(x + sq * 0.76f, y + sq * 0.85f);
                path.lineTo(x + sq * 0.73f, y + sq * 0.76f);
                path.lineTo(x + sq * 0.67f, y + sq * 0.71f);
                path.quadTo(x + sq * 0.58f, y + sq * 0.58f, x + sq * 0.57f, y + sq * 0.45f);
                path.lineTo(x + sq * 0.64f, y + sq * 0.45f);
                path.lineTo(x + sq * 0.64f, y + sq * 0.40f);
                path.lineTo(x + sq * 0.36f, y + sq * 0.40f);
                path.lineTo(x + sq * 0.36f, y + sq * 0.45f);
                path.lineTo(x + sq * 0.43f, y + sq * 0.45f);
                path.quadTo(x + sq * 0.42f, y + sq * 0.58f, x + sq * 0.33f, y + sq * 0.71f);
                path.lineTo(x + sq * 0.27f, y + sq * 0.76f);
                path.close();

                drawFilledStrokedPath(c, fillCol, strokeCol, strokeW);

                p.setStyle(Paint.Style.FILL);
                p.setColor(fillCol);
                c.drawCircle(x + sq * 0.50f, y + sq * 0.28f, sq * 0.135f, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(strokeW);
                p.setColor(strokeCol);
                c.drawCircle(x + sq * 0.50f, y + sq * 0.28f, sq * 0.135f, p);
                c.drawLine(x + sq * 0.28f, y + sq * 0.76f, x + sq * 0.72f, y + sq * 0.76f, p);

            } else if (type == 4) {
                path.moveTo(x + sq * 0.21f, y + sq * 0.85f);
                path.lineTo(x + sq * 0.79f, y + sq * 0.85f);
                path.lineTo(x + sq * 0.75f, y + sq * 0.76f);
                path.lineTo(x + sq * 0.69f, y + sq * 0.70f);
                path.lineTo(x + sq * 0.65f, y + sq * 0.36f);
                path.lineTo(x + sq * 0.73f, y + sq * 0.31f);
                path.lineTo(x + sq * 0.73f, y + sq * 0.17f);
                path.lineTo(x + sq * 0.62f, y + sq * 0.17f);
                path.lineTo(x + sq * 0.62f, y + sq * 0.24f);
                path.lineTo(x + sq * 0.55f, y + sq * 0.24f);
                path.lineTo(x + sq * 0.55f, y + sq * 0.17f);
                path.lineTo(x + sq * 0.45f, y + sq * 0.17f);
                path.lineTo(x + sq * 0.45f, y + sq * 0.24f);
                path.lineTo(x + sq * 0.38f, y + sq * 0.24f);
                path.lineTo(x + sq * 0.38f, y + sq * 0.17f);
                path.lineTo(x + sq * 0.27f, y + sq * 0.17f);
                path.lineTo(x + sq * 0.27f, y + sq * 0.31f);
                path.lineTo(x + sq * 0.35f, y + sq * 0.36f);
                path.lineTo(x + sq * 0.31f, y + sq * 0.70f);
                path.lineTo(x + sq * 0.25f, y + sq * 0.76f);
                path.close();

                drawFilledStrokedPath(c, fillCol, strokeCol, strokeW);

                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(strokeW * 0.85f);
                p.setColor(detailCol);
                c.drawLine(x + sq * 0.34f, y + sq * 0.35f, x + sq * 0.66f, y + sq * 0.35f, p);
                c.drawLine(x + sq * 0.31f, y + sq * 0.70f, x + sq * 0.69f, y + sq * 0.70f, p);
                c.drawLine(x + sq * 0.25f, y + sq * 0.76f, x + sq * 0.75f, y + sq * 0.76f, p);

            } else if (type == 3) {
                path.moveTo(x + sq * 0.20f, y + sq * 0.85f);
                path.lineTo(x + sq * 0.80f, y + sq * 0.85f);
                path.lineTo(x + sq * 0.75f, y + sq * 0.76f);
                path.lineTo(x + sq * 0.68f, y + sq * 0.70f);
                path.lineTo(x + sq * 0.63f, y + sq * 0.51f);
                path.lineTo(x + sq * 0.67f, y + sq * 0.47f);
                path.cubicTo(x + sq * 0.71f, y + sq * 0.35f, x + sq * 0.62f, y + sq * 0.24f, x + sq * 0.50f, y + sq * 0.18f);
                path.cubicTo(x + sq * 0.38f, y + sq * 0.24f, x + sq * 0.29f, y + sq * 0.35f, x + sq * 0.33f, y + sq * 0.47f);
                path.lineTo(x + sq * 0.37f, y + sq * 0.51f);
                path.lineTo(x + sq * 0.32f, y + sq * 0.70f);
                path.lineTo(x + sq * 0.25f, y + sq * 0.76f);
                path.close();

                drawFilledStrokedPath(c, fillCol, strokeCol, strokeW);

                p.setStyle(Paint.Style.FILL);
                p.setColor(fillCol);
                c.drawCircle(x + sq * 0.50f, y + sq * 0.135f, sq * 0.042f, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(strokeW * 0.9f);
                p.setColor(strokeCol);
                c.drawCircle(x + sq * 0.50f, y + sq * 0.135f, sq * 0.042f, p);

                p.setColor(detailCol);
                c.drawLine(x + sq * 0.50f, y + sq * 0.27f, x + sq * 0.50f, y + sq * 0.40f, p);
                c.drawLine(x + sq * 0.435f, y + sq * 0.335f, x + sq * 0.565f, y + sq * 0.335f, p);
                c.drawLine(x + sq * 0.36f, y + sq * 0.50f, x + sq * 0.64f, y + sq * 0.50f, p);
                c.drawLine(x + sq * 0.26f, y + sq * 0.76f, x + sq * 0.74f, y + sq * 0.76f, p);

            } else if (type == 2) {
                path.moveTo(x + sq * 0.21f, y + sq * 0.85f);
                path.lineTo(x + sq * 0.79f, y + sq * 0.85f);
                path.lineTo(x + sq * 0.75f, y + sq * 0.76f);
                path.lineTo(x + sq * 0.70f, y + sq * 0.70f);
                path.cubicTo(x + sq * 0.75f, y + sq * 0.44f, x + sq * 0.68f, y + sq * 0.23f, x + sq * 0.50f, y + sq * 0.19f);
                path.lineTo(x + sq * 0.48f, y + sq * 0.13f);
                path.lineTo(x + sq * 0.42f, y + sq * 0.19f);
                path.lineTo(x + sq * 0.38f, y + sq * 0.13f);
                path.lineTo(x + sq * 0.36f, y + sq * 0.22f);
                path.cubicTo(x + sq * 0.29f, y + sq * 0.28f, x + sq * 0.23f, y + sq * 0.36f, x + sq * 0.23f, y + sq * 0.42f);
                path.cubicTo(x + sq * 0.24f, y + sq * 0.47f, x + sq * 0.31f, y + sq * 0.47f, x + sq * 0.43f, y + sq * 0.44f);
                path.cubicTo(x + sq * 0.37f, y + sq * 0.52f, x + sq * 0.33f, y + sq * 0.60f, x + sq * 0.32f, y + sq * 0.70f);
                path.lineTo(x + sq * 0.25f, y + sq * 0.76f);
                path.close();

                drawFilledStrokedPath(c, fillCol, strokeCol, strokeW);

                p.setStyle(Paint.Style.FILL);
                p.setColor(detailCol);
                c.drawCircle(x + sq * 0.35f, y + sq * 0.30f, sq * 0.025f, p);
                c.drawCircle(x + sq * 0.27f, y + sq * 0.39f, sq * 0.018f, p);

                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(strokeW * 0.85f);
                c.drawLine(x + sq * 0.26f, y + sq * 0.76f, x + sq * 0.74f, y + sq * 0.76f, p);
                c.drawLine(x + sq * 0.32f, y + sq * 0.70f, x + sq * 0.70f, y + sq * 0.70f, p);

            } else if (type == 5) {
                path.moveTo(x + sq * 0.20f, y + sq * 0.85f);
                path.lineTo(x + sq * 0.80f, y + sq * 0.85f);
                path.lineTo(x + sq * 0.75f, y + sq * 0.75f);
                path.lineTo(x + sq * 0.70f, y + sq * 0.68f);
                path.lineTo(x + sq * 0.79f, y + sq * 0.28f);
                path.lineTo(x + sq * 0.67f, y + sq * 0.43f);
                path.lineTo(x + sq * 0.64f, y + sq * 0.22f);
                path.lineTo(x + sq * 0.56f, y + sq * 0.40f);
                path.lineTo(x + sq * 0.50f, y + sq * 0.19f);
                path.lineTo(x + sq * 0.44f, y + sq * 0.40f);
                path.lineTo(x + sq * 0.36f, y + sq * 0.22f);
                path.lineTo(x + sq * 0.33f, y + sq * 0.43f);
                path.lineTo(x + sq * 0.21f, y + sq * 0.28f);
                path.lineTo(x + sq * 0.30f, y + sq * 0.68f);
                path.lineTo(x + sq * 0.25f, y + sq * 0.75f);
                path.close();

                drawFilledStrokedPath(c, fillCol, strokeCol, strokeW);

                float[] px = {0.20f, 0.35f, 0.50f, 0.65f, 0.80f};
                float[] py = {0.25f, 0.19f, 0.16f, 0.19f, 0.25f};
                for (int i = 0; i < 5; i++) {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(fillCol);
                    c.drawCircle(x + sq * px[i], y + sq * py[i], sq * 0.032f, p);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(strokeW * 0.8f);
                    p.setColor(strokeCol);
                    c.drawCircle(x + sq * px[i], y + sq * py[i], sq * 0.032f, p);
                }

                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(strokeW * 0.85f);
                p.setColor(detailCol);
                c.drawLine(x + sq * 0.30f, y + sq * 0.67f, x + sq * 0.70f, y + sq * 0.67f, p);
                c.drawLine(x + sq * 0.25f, y + sq * 0.75f, x + sq * 0.75f, y + sq * 0.75f, p);

            } else if (type == 6) {
                path.moveTo(x + sq * 0.20f, y + sq * 0.85f);
                path.lineTo(x + sq * 0.80f, y + sq * 0.85f);
                path.lineTo(x + sq * 0.75f, y + sq * 0.75f);
                path.lineTo(x + sq * 0.70f, y + sq * 0.67f);
                path.cubicTo(x + sq * 0.78f, y + sq * 0.45f, x + sq * 0.75f, y + sq * 0.31f, x + sq * 0.62f, y + sq * 0.33f);
                path.cubicTo(x + sq * 0.56f, y + sq * 0.27f, x + sq * 0.44f, y + sq * 0.27f, x + sq * 0.38f, y + sq * 0.33f);
                path.cubicTo(x + sq * 0.25f, y + sq * 0.31f, x + sq * 0.22f, y + sq * 0.45f, x + sq * 0.30f, y + sq * 0.67f);
                path.lineTo(x + sq * 0.25f, y + sq * 0.75f);
                path.close();

                drawFilledStrokedPath(c, fillCol, strokeCol, strokeW);

                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(strokeW * 1.15f);
                p.setColor(strokeCol);
                c.drawLine(x + sq * 0.50f, y + sq * 0.11f, x + sq * 0.50f, y + sq * 0.28f, p);
                c.drawLine(x + sq * 0.42f, y + sq * 0.18f, x + sq * 0.58f, y + sq * 0.18f, p);

                p.setStrokeWidth(strokeW * 0.85f);
                p.setColor(detailCol);
                c.drawLine(x + sq * 0.50f, y + sq * 0.30f, x + sq * 0.50f, y + sq * 0.60f, p);
                c.drawLine(x + sq * 0.30f, y + sq * 0.67f, x + sq * 0.70f, y + sq * 0.67f, p);
                c.drawLine(x + sq * 0.25f, y + sq * 0.75f, x + sq * 0.75f, y + sq * 0.75f, p);
            }
        }

        void drawFilledStrokedPath(Canvas c, int fillCol, int strokeCol, float strokeW) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(fillCol);
            c.drawPath(path, p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(strokeW);
            p.setColor(strokeCol);
            c.drawPath(path, p);
        }

        void drawButton(Canvas c, RectF r, String label, boolean enabled, int activeColor) {
            ArcadeUi.drawButton(c, p, r, label, enabled, activeColor);
        }

        void drawCapturedBorder(Canvas c, int capturerSide, int victimSide, float yTop, float sq) {
            int[] counts = new int[7];
            int myPts = 0, oppPts = 0;

            for (int r = 0; r < 8; r++) {
                for (int f = 0; f < 8; f++) {
                    int pc = b[(r << 4) | f];
                    if (pc == 0) continue;
                    int t = Math.abs(pc);
                    if (pc * capturerSide > 0) myPts += MAT_PTS[t];
                    else oppPts += MAT_PTS[t];
                }
            }

            for (int i = 1; i < plyCount; i++) {
                int m = histMove[i];
                int cap = (byte) ((m >> 16) & 0xFF);
                if (cap * victimSide > 0) {
                    int t = Math.abs(cap);
                    if (t >= 1 && t <= 5) counts[t]++;
                }
            }

            float miniSq = sq * 0.52f;
            float x = sq * 0.14f;
            float step = miniSq * 0.72f;
            float py = yTop + (sq * 0.72f - miniSq) * 0.5f;

            int[] order = {5, 4, 3, 2, 1};
            for (int t : order) {
                for (int k = 0; k < counts[t]; k++) {
                    drawVectorPiece(c, victimSide * t, x, py, miniSq, false);
                    x += step;
                }
            }

            int diff = myPts - oppPts;
            if (diff > 0) {
                p.setStyle(Paint.Style.FILL);
                p.setTextAlign(Paint.Align.LEFT);
                p.setColor(0xFF9CCC65);
                p.setTextSize(sq * 0.30f);
                Paint.FontMetrics fm = p.getFontMetrics();
                float baseline = yTop + sq * 0.36f - (fm.ascent + fm.descent) / 2f;
                c.drawText("+" + diff, x + sq * 0.14f, baseline, p);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() != MotionEvent.ACTION_DOWN) return true;
            float tx = e.getX(), ty = e.getY();

            if (btnGames.contains(tx, ty)) {
                if (onGamesRequested != null) onGamesRequested.run();
                return true;
            }

            if (pickingSide) {
                if (titleHitRect.contains(tx, ty)) {
                    onTitleTapped();
                    return true;
                }
                if (hasSavedGame && btnContinue.contains(tx, ty)) {
                    resumeSavedGame();
                    return true;
                }
                if (!localTwoPlayer && btnPickWhite.contains(tx, ty)) {
                    selectedSide = 1;
                    persistSessionNow();
                    invalidate();
                    return true;
                }
                if (!localTwoPlayer && btnPickBlack.contains(tx, ty)) {
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
                    startNewGame(selectedSide, difficulty);
                    return true;
                }
                return true;
            }

            if (btnNew.contains(tx, ty)) {
                if (coop != null) {
                    if (gameOver && coop.state() != null && coop.state().isFinished()) {
                        showRematchPrompt();
                    } else if (onGamesRequested != null) {
                        onGamesRequested.run();
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
            if (btnShare.contains(tx, ty) && plyCount > 1) {
                sharePgn();
                return true;
            }

            if (pendingPromoFrom != -1) {
                int[] flags = {4, 5, 6, 7};
                for (int i = 0; i < 4; i++) {
                    if (btnPromo[i].contains(tx, ty)) {
                        int move = pendingPromoFrom | (pendingPromoTo << 8)
                            | ((pendingPromoCap & 0xFF) << 16) | (flags[i] << 24);
                        pendingPromoFrom = -1;
                        commitHumanMove(move);
                        return true;
                    }
                }
                pendingPromoFrom = -1;
                invalidate();
                return true;
            }

            if (thinking || gameOver || (!localTwoPlayer && turn != humanSide)
                || (coop != null && !coop.opponentOnline())) return true;

            int w = getWidth(), h = getHeight();
            float margin = Math.min(w, h) * 0.032f;
            float boardW = Math.min(w - margin * 2f, h * 0.70f);
            float frame = Math.min(margin, boardW * 0.04f);
            float sq = boardW / 8f;
            float boardLeft = (w - boardW) * 0.5f;
            float outerTop = Math.max((h - (boardW + frame * 2f)) * 0.5f, sq * 2.45f);
            float boardTop = outerTop + frame;

            int dispF = (int) Math.floor((tx - boardLeft) / sq);
            int dispR = (int) Math.floor((ty - boardTop) / sq);
            if (dispF < 0 || dispF > 7 || dispR < 0 || dispR > 7) return true;

            int r = (humanSide == 1) ? dispR : (7 - dispR);
            int f = (humanSide == 1) ? dispF : (7 - dispF);
            int clicked = (r << 4) | f;

            if (b[clicked] * turn > 0) {
                sel = (sel == clicked) ? -1 : clicked;
                computeLegalTargetsForSelected();
                invalidate();
                return true;
            }

            if (sel != -1) {
                int count = genMovesOn(b, castle, ep, turn, 0);
                for (int i = 0; i < count; i++) {
                    int m = moveBuf[0][i];
                    if ((m & 0xFF) == sel && ((m >> 8) & 0xFF) == clicked) {
                        int oc = castle, oep = ep, ohc = halfMoveClock;
                        long oh = curHash;
                        makeMoveUi(m, turn);
                        boolean legal = !inCheckOn(b, turn);
                        unmakeMoveUi(m, turn, oc, oep, ohc, oh);
                        if (!legal) continue;

                        int fl = (m >> 24) & 0xFF;
                        if (fl >= 4 && fl <= 7) {
                            pendingPromoFrom = sel;
                            pendingPromoTo = clicked;
                            pendingPromoCap = (byte) ((m >> 16) & 0xFF);
                            invalidate();
                            return true;
                        }
                        commitHumanMove(m);
                        return true;
                    }
                }
            }
            return true;
        }

        synchronized void commitHumanMove(int m) {
            clearHint();
            int movingPc = b[m & 0xFF];
            int capPc = (byte) ((m >> 16) & 0xFF);
            int side = turn;
            String san = buildSan(m, side);
            makeMoveUi(m, side);
            triggerMoveAnimation(m, side, movingPc, capPc);

            lastFrom = m & 0xFF;
            lastTo = (m >> 8) & 0xFF;
            sel = -1;
            legalTargetCount = 0;
            turn = -side;
            san += checkSuffix(turn);
            saveSnapshot(m, san);
            hasSavedGame = true;
            coopWinner = "";

            if (coop != null) {
                boolean over = checkEnd(turn);
                if (!over) status = "Opponent's turn";
                sendCoopMoves(over ? coopWinner : "");
                invalidate();
                return;
            }

            if (checkEnd(turn)) {
                persistSessionNow();
                invalidate();
                return;
            }
            status = localTwoPlayer ? localTurnText() : "Bot thinking...";
            persistSessionNow();
            invalidate();
            if (!localTwoPlayer) startBotThread();
        }

        boolean validCoopChess(String[] parts) {
            int[] test = histBoard[0].clone();
            int tc = histCastle[0];
            int te = histEp[0];
            long h = histHash[0];
            int side = 1;
            int[] st = new int[3];
            for (String part : parts) {
                if (part.length() == 0) return false;
                int m;
                try {
                    m = (int) Long.parseLong(part, 16);
                } catch (Throwable bad) {
                    return false;
                }
                int n = genMovesOn(test, tc, te, side, 0);
                boolean legal = false;
                for (int i = 0; i < n; i++) {
                    if (moveBuf[0][i] == m) {
                        legal = true;
                        break;
                    }
                }
                if (!legal) return false;
                h = applyMove(test, m, side, h, st);
                if (inCheckOn(test, side)) return false;
                tc = st[0];
                te = st[1];
                side = -side;
            }
            return true;
        }

        void sendCoopMoves(String winner) {
            if (coop == null) return;
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < plyCount; i++) {
                if (i > 1) sb.append(',');
                sb.append(Integer.toHexString(histMove[i]));
            }
            coop.sendMoves(sb.toString(), plyCount - 1, turn, winner);
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

        synchronized void applyCoopState(CoopSession.State s) {
            if (s == null || !s.isActive()) return;

            int localMoves = Math.max(0, plyCount - 1);
            if (!s.isFinished() && s.ply == 0 && (s.moves == null || s.moves.length() == 0)
                && (gameOver || localMoves > 0)) {
                cancelBot();
                confetti.stop();
                clearHint();
                setupInitialPosition();
                coopWinner = "";
                gameOver = false;
                pickingSide = false;
                status = localTurnText();
                invalidate();
                localMoves = 0;
            }
            String raw = s.moves == null ? "" : s.moves;
            String[] parts = raw.length() == 0 ? new String[0] : raw.split(",");
            if (parts.length < localMoves && !s.isFinished()) {
                if (parts.length == localMoves - 1 && s.turn == coop.mySide()) {
                    sendCoopMoves(gameOver ? coopWinner : "");
                }
                return;
            }
            if (parts.length > localMoves) {
                if (!validCoopChess(parts)) {
                    gameOver = true;
                    status = "Invalid move from opponent";
                    invalidate();
                    return;
                }
                cancelBot();
                confetti.stop();
                setupInitialPosition();
                boolean animateLast = parts.length == localMoves + 1;
                int animMove = 0;
                int animMovingPiece = 0;
                int animCapturedPiece = 0;
                int animSide = 1;
                int side = 1;
                for (int i = 0; i < parts.length; i++) {
                    String part = parts[i];
                    if (part.length() == 0) continue;
                    int m;
                    try {
                        m = (int) Long.parseLong(part, 16);
                    } catch (Throwable bad) {
                        break;
                    }
                    if (animateLast && i == parts.length - 1) {
                        animMove = m;
                        animMovingPiece = b[m & 0xFF];
                        animCapturedPiece = (byte) ((m >> 16) & 0xFF);
                        animSide = side;
                    }
                    String san = buildSan(m, side);
                    makeMoveUi(m, side);
                    lastFrom = m & 0xFF;
                    lastTo = (m >> 8) & 0xFF;
                    turn = -side;
                    saveSnapshot(m, san);
                    side = -side;
                }
                if (animateLast && animMove != 0) {
                    triggerMoveAnimation(animMove, animSide, animMovingPiece, animCapturedPiece);
                }
            }

            humanSide = coop.mySide();
            selectedSide = humanSide;
            pickingSide = false;
            sel = -1;
            legalTargetCount = 0;
            pendingPromoFrom = -1;
            boolean wasOver = gameOver;
            gameOver = s.isFinished();
            if (gameOver) {
                thinking = false;
                if ("0".equals(s.winner)) {
                    pgnResult = "1/2-1/2";
                    status = "Draw";
                } else if ("1".equals(s.winner)) {
                    pgnResult = "1-0";
                } else if ("-1".equals(s.winner)) {
                    pgnResult = "0-1";
                }
                if (!"0".equals(s.winner) && s.winner.length() > 0) {
                    boolean youWon = (s.winner.startsWith("-") ? -1 : 1) == humanSide;
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
                } else if (turn == humanSide) {
                    status = inCheckOn(b, humanSide)
                        ? "Check! Your turn"
                        : ("Your turn (" + (humanSide == 1 ? "White" : "Black") + ")");
                } else {
                    status = "Opponent's turn...";
                }
            }
            invalidate();
        }

        @Override
        public void run() {
            final int myGen;
            final int botSide;
            final int diffMode;
            synchronized (this) {
                myGen = searchGen;
                botSide = -humanSide;
                diffMode = difficulty;
                System.arraycopy(b, 0, sb, 0, 128);
                sCastle = castle;
                sEp = ep;
                sHalfClock = halfMoveClock;
                sHash = curHash;
                sHistLen = plyCount;
                System.arraycopy(histHash, 0, sHistHash, 0, plyCount);
            }

            long startMs = System.currentTimeMillis();
            bestMoveRoot = 0;
            nodes = 0;
            abort = false;

            // Difficulty scaling: Easy (0), Medium (1), Hard (2), Expert (3)
            int maxDepth = (diffMode == 0) ? 2 : (diffMode == 1 ? 4 : (diffMode == 2 ? 6 : 16));
            long budgetMs = (diffMode == 0) ? 140L : (diffMode == 1 ? 320L : (diffMode == 2 ? 680L : 1450L));
            deadline = startMs + budgetMs;

            for (int i = 0; i < MAX_PLY; i++) killer1[i] = killer2[i] = 0;

            int iterBest = 0;
            for (int d = 1; d <= maxDepth; d++) {
                int sc = search(d, -INF, INF, botSide, 0, true);
                if (abort && d > 1) break;
                if (bestMoveRoot != 0) iterBest = bestMoveRoot;
                if (Math.abs(sc) >= MATE - 64) break;
            }

            // On Easy & Medium, introduce natural human-like imprecision so Easy is genuinely beatable
            if (diffMode <= 1 && iterBest != 0) {
                iterBest = pickImperfectMove(botSide, diffMode, iterBest);
            }

            long jitter = (System.nanoTime() >>> 8) % 350L;
            long minWait = 1350L + jitter;
            long elapsed = System.currentTimeMillis() - startMs;
            if (elapsed < minWait && !abort && myGen == searchGen) {
                try {
                    Thread.sleep(minWait - elapsed);
                } catch (InterruptedException ignored) {
                    return;
                }
            }

            synchronized (this) {
                if (myGen != searchGen || pickingSide) return;
                if (iterBest != 0) {
                    int movingPc = b[iterBest & 0xFF];
                    int capPc = (byte) ((iterBest >> 16) & 0xFF);
                    String san = buildSan(iterBest, botSide);
                    makeMoveUi(iterBest, botSide);
                    triggerMoveAnimation(iterBest, botSide, movingPc, capPc);

                    lastFrom = iterBest & 0xFF;
                    lastTo = (iterBest >> 8) & 0xFF;
                    turn = humanSide;
                    san += checkSuffix(turn);
                    saveSnapshot(iterBest, san);
                    hasSavedGame = true;
                }
                thinking = false;
                if (!checkEnd(humanSide)) {
                    status = inCheckOn(b, humanSide)
                        ? "Check! Your turn"
                        : ("Your turn (" + (humanSide == 1 ? "White" : "Black") + ") · " + DIFF_NAMES[difficulty]);
                }
                persistSessionNow();
                postInvalidate();
            }
        }

        // Picks a slightly suboptimal root move on Easy/Medium so lower tiers feel distinct
        int pickImperfectMove(int botSide, int diffMode, int bestMove) {
            int n = genMovesOn(sb, sCastle, sEp, botSide, 0);
            int[] cands = new int[64];
            int[] vals = new int[64];
            int cnt = 0;
            int rng = (int) (System.nanoTime() ^ 0x5BD1E995);

            for (int i = 0; i < n && cnt < 64; i++) {
                int m = moveBuf[0][i];
                int oc = sCastle, oep = sEp, ohc = sHalfClock;
                long oh = sHash;
                sMakeMove(m, botSide);
                boolean ok = !inCheckOn(sb, botSide);
                if (ok) {
                    rng ^= rng << 13; rng ^= rng >>> 17; rng ^= rng << 5;
                    int spread = (diffMode == 0) ? 260 : 95;
                    int noise = (rng % spread);
                    int sc = (m == bestMove ? 60 : 0) + (botSide * sEval()) + noise;
                    cands[cnt] = m;
                    vals[cnt] = sc;
                    cnt++;
                }
                sUnmakeMove(m, botSide, oc, oep, ohc, oh);
            }
            if (cnt == 0) return bestMove;
            int chosen = cands[0], maxV = vals[0];
            for (int i = 1; i < cnt; i++) {
                if (vals[i] > maxV) {
                    maxV = vals[i];
                    chosen = cands[i];
                }
            }
            return chosen;
        }

        String buildSan(int m, int side) {
            int from = m & 0xFF, to = (m >> 8) & 0xFF;
            int cap = (byte) ((m >> 16) & 0xFF), fl = (m >> 24) & 0xFF;
            if (fl == 2) return "O-O";
            if (fl == 3) return "O-O-O";

            int type = Math.abs(b[from]);
            char fromF = (char) ('a' + (from & 7));
            char fromR = (char) ('1' + (7 - (from >> 4)));
            char toF = (char) ('a' + (to & 7));
            char toR = (char) ('1' + (7 - (to >> 4)));
            boolean isCap = (cap != 0 || fl == 1);

            StringBuilder sanBuf = new StringBuilder();
            if (type == 1) {
                if (isCap) sanBuf.append(fromF).append('x');
                sanBuf.append(toF).append(toR);
                if (fl == 4) sanBuf.append("=Q");
                else if (fl == 5) sanBuf.append("=R");
                else if (fl == 6) sanBuf.append("=B");
                else if (fl == 7) sanBuf.append("=N");
            } else {
                char[] pChar = {' ', ' ', 'N', 'B', 'R', 'Q', 'K'};
                sanBuf.append(pChar[type]);

                if (type != 6) {
                    boolean sameFile = false, sameRank = false, ambig = false;
                    int n = genMovesOn(b, castle, ep, side, 0);
                    for (int i = 0; i < n; i++) {
                        int om = moveBuf[0][i];
                        int oFrom = om & 0xFF;
                        if (oFrom != from && ((om >> 8) & 0xFF) == to && Math.abs(b[oFrom]) == type) {
                            int oc = castle, oep = ep, ohc = halfMoveClock;
                            long oh = curHash;
                            makeMoveUi(om, side);
                            boolean ok = !inCheckOn(b, side);
                            unmakeMoveUi(om, side, oc, oep, ohc, oh);
                            if (ok) {
                                ambig = true;
                                if ((oFrom & 7) == (from & 7)) sameFile = true;
                                if ((oFrom >> 4) == (from >> 4)) sameRank = true;
                            }
                        }
                    }
                    if (ambig) {
                        if (!sameFile) sanBuf.append(fromF);
                        else if (!sameRank) sanBuf.append(fromR);
                        else sanBuf.append(fromF).append(fromR);
                    }
                }
                if (isCap) sanBuf.append('x');
                sanBuf.append(toF).append(toR);
            }
            return sanBuf.toString();
        }

        String checkSuffix(int sideToMove) {
            if (!inCheckOn(b, sideToMove)) return "";
            int n = genMovesOn(b, castle, ep, sideToMove, 0);
            for (int i = 0; i < n; i++) {
                int m = moveBuf[0][i], oc = castle, oep = ep, ohc = halfMoveClock;
                long oh = curHash;
                makeMoveUi(m, sideToMove);
                boolean ok = !inCheckOn(b, sideToMove);
                unmakeMoveUi(m, sideToMove, oc, oep, ohc, oh);
                if (ok) return "+";
            }
            return "#";
        }

        boolean isInsufficientMaterial() {
            int wN = 0, bN = 0, wB = 0, bB = 0, wBColor = -1, bBColor = -1;
            for (int r = 0; r < 8; r++) {
                for (int f = 0; f < 8; f++) {
                    int pc = b[(r << 4) | f];
                    if (pc == 0) continue;
                    int t = Math.abs(pc);
                    if (t == 1 || t == 4 || t == 5) return false;
                    if (t == 2) { if (pc > 0) wN++; else bN++; }
                    if (t == 3) {
                        int sqCol = (r + f) & 1;
                        if (pc > 0) { wB++; wBColor = sqCol; }
                        else { bB++; bBColor = sqCol; }
                    }
                }
            }
            int wMinors = wN + wB, bMinors = bN + bB;
            if (wMinors == 0 && bMinors == 0) return true;
            if ((wMinors == 1 && bMinors == 0) || (wMinors == 0 && bMinors == 1)) return true;
            return (wN == 0 && bN == 0 && wB == 1 && bB == 1 && wBColor == bBColor);
        }

        boolean checkEnd(int side) {
            int n = genMovesOn(b, castle, ep, side, 0);
            boolean hasLegal = false;
            for (int i = 0; i < n; i++) {
                int m = moveBuf[0][i], oc = castle, oep = ep, ohc = halfMoveClock;
                long oh = curHash;
                makeMoveUi(m, side);
                boolean ok = !inCheckOn(b, side);
                unmakeMoveUi(m, side, oc, oep, ohc, oh);
                if (ok) { hasLegal = true; break; }
            }
            if (!hasLegal) {
                gameOver = true;
                if (inCheckOn(b, side)) {
                    int winnerSide = -side;
                    pgnResult = (winnerSide == 1) ? "1-0" : "0-1";
                    if (coop != null) {
                        coopWinner = winnerSide == 1 ? "1" : "-1";
                        boolean youWon = winnerSide == humanSide;
                        status = youWon ? "Checkmate - You win!" : "Checkmate - Opponent wins!";
                        if (youWon) confetti.start(getWidth(), getHeight());
                    } else if (localTwoPlayer) {
                        status = "Checkmate - "
                            + (winnerSide == 1 ? "White" : "Black") + " wins!";
                        confetti.start(getWidth(), getHeight());
                    } else {
                        status = (side == humanSide) ? "Checkmate - Bot wins!" : "Checkmate - You win!";
                        if (side != humanSide) confetti.start(getWidth(), getHeight());
                    }
                } else {
                    pgnResult = "1/2-1/2";
                    status = "Draw (Stalemate)";
                    if (coop != null) coopWinner = "0";
                }
                return true;
            }
            if (halfMoveClock >= 100) {
                gameOver = true;
                pgnResult = "1/2-1/2";
                status = "Draw (50-move rule)";
                if (coop != null) coopWinner = "0";
                return true;
            }
            int reps = 0;
            for (int i = plyCount - 1; i >= 0; i -= 2) {
                if (histHash[i] == curHash && ++reps >= 3) {
                    gameOver = true;
                    pgnResult = "1/2-1/2";
                    status = "Draw (Threefold repetition)";
                    if (coop != null) coopWinner = "0";
                    return true;
                }
            }
            if (isInsufficientMaterial()) {
                gameOver = true;
                pgnResult = "1/2-1/2";
                status = "Draw (Insufficient material)";
                if (coop != null) coopWinner = "0";
                return true;
            }
            return false;
        }

        // --- Search Engine Methods (exclusively touch `sb[]`) ---

        boolean isSearchRepetition(int ply) {
            for (int i = ply - 2; i >= 0; i -= 2) {
                if (searchPath[i] == sHash) return true;
            }
            for (int i = sHistLen - 3; i >= 0; i -= 2) {
                if (sHistHash[i] == sHash) return true;
            }
            return false;
        }

        boolean sHasNonPawnMaterial(int side) {
            for (int r = 0; r < 8; r++) {
                for (int f = 0; f < 8; f++) {
                    int pc = sb[(r << 4) | f];
                    if (pc * side > 0) {
                        int t = Math.abs(pc);
                        if (t >= 2 && t <= 5) return true;
                    }
                }
            }
            return false;
        }

        int sEval() {
            int mg = 0, eg = 0, phase = 0;
            int wBishops = 0, bBishops = 0;

            for (int r = 0; r < 8; r++) {
                for (int f = 0; f < 8; f++) {
                    int p = sb[(r << 4) | f];
                    if (p == 0) continue;
                    int type = Math.abs(p);
                    int sq64 = (p > 0) ? (r * 8 + f) : ((r ^ 7) * 8 + f);
                    phase += PHASE_W[type];

                    if (type == 3) {
                        if (p > 0) wBishops++; else bBishops++;
                    }
                    if (type == 6) {
                        int kMg = VAL[6] + PST[5][sq64];
                        int kEg = VAL[6] + PST[6][sq64];
                        if (p > 0) { mg += kMg; eg += kEg; }
                        else { mg -= kMg; eg -= kEg; }
                    } else {
                        int v = VAL[type] + PST[type - 1][sq64];
                        if (p > 0) { mg += v; eg += v; }
                        else { mg -= v; eg -= v; }
                    }
                }
            }
            if (wBishops >= 2) { mg += 30; eg += 45; }
            if (bBishops >= 2) { mg -= 30; eg -= 45; }

            if (phase > 24) phase = 24;
            return (mg * phase + eg * (24 - phase)) / 24;
        }

        int search(int depth, int alpha, int beta, int side, int ply, boolean allowNull) {
            if ((++nodes & 2047) == 0 && (abort || System.currentTimeMillis() >= deadline)) {
                abort = true;
                return 0;
            }
            if (ply >= MAX_PLY - 1) return side * sEval();

            if (ply > 0) {
                if (sHalfClock >= 100 || isSearchRepetition(ply)) return 0;
            }
            searchPath[ply] = sHash;

            boolean inChk = inCheckOn(sb, side);
            if (inChk) depth++;

            if (depth <= 0) return quiesce(alpha, beta, side, ply);

            int origAlpha = alpha;
            int ttIdx = (int) (sHash & TT_MASK);
            int hashMove = 0;

            if (ttKey[ttIdx] == sHash) {
                hashMove = ttMove[ttIdx];
                if (ply > 0 && ttDepth[ttIdx] >= depth) {
                    int sc = ttScore[ttIdx];
                    if (sc > MATE - 64) sc -= ply;
                    else if (sc < -MATE + 64) sc += ply;
                    byte fl = ttFlag[ttIdx];
                    if (fl == 1) return sc;
                    if (fl == 2 && sc <= alpha) return alpha;
                    if (fl == 3 && sc >= beta) return beta;
                }
            }

            if (allowNull && !inChk && ply > 0 && depth >= 3 && sHasNonPawnMaterial(side)) {
                long oh = sHash; int oep = sEp;
                if (sEp != -1) { sHash ^= Z_EP[sEp]; sEp = -1; }
                sHash ^= Z_SIDE;
                int nullSc = -search(depth - 3, -beta, -beta + 1, -side, ply + 1, false);
                sHash = oh; sEp = oep;
                if (abort) return 0;
                if (nullSc >= beta) return beta;
            }

            int n = genMovesOn(sb, sCastle, sEp, side, ply);
            int[] moves = moveBuf[ply];
            int[] scores = scoreBuf[ply];

            for (int i = 0; i < n; i++) {
                int m = moves[i];
                if (m == hashMove) scores[i] = 1_000_000;
                else {
                    int cap = Math.abs((byte) ((m >> 16) & 0xFF));
                    int fl = (m >> 24) & 0xFF;
                    if (cap != 0 || fl >= 4) {
                        int att = Math.abs(sb[m & 0xFF]);
                        scores[i] = 100_000 + VAL[cap] * 10 - VAL[att] + (fl >= 4 ? (10 - fl) * 2000 : 0);
                    } else if (m == killer1[ply]) scores[i] = 90_000;
                    else if (m == killer2[ply]) scores[i] = 80_000;
                    else scores[i] = 0;
                }
            }

            int legal = 0, best = -INF, bestM = 0;
            for (int i = 0; i < n; i++) {
                int bestIdx = i;
                for (int j = i + 1; j < n; j++) {
                    if (scores[j] > scores[bestIdx]) bestIdx = j;
                }
                int m = moves[bestIdx]; moves[bestIdx] = moves[i]; moves[i] = m;
                int tmpS = scores[bestIdx]; scores[bestIdx] = scores[i]; scores[i] = tmpS;

                int oc = sCastle, oep = sEp, ohc = sHalfClock;
                long oh = sHash;
                sMakeMove(m, side);
                if (inCheckOn(sb, side)) {
                    sUnmakeMove(m, side, oc, oep, ohc, oh);
                    continue;
                }
                legal++;

                int sc;
                if (legal == 1) {
                    sc = -search(depth - 1, -beta, -alpha, -side, ply + 1, true);
                } else {
                    int reduction = (legal > 4 && depth >= 3 && !inChk
                        && ((m >> 16) & 0xFF) == 0 && ((m >> 24) & 0xFF) == 0) ? 1 : 0;
                    sc = -search(depth - 1 - reduction, -alpha - 1, -alpha, -side, ply + 1, true);
                    if (sc > alpha && (reduction > 0 || sc < beta)) {
                        sc = -search(depth - 1, -beta, -alpha, -side, ply + 1, true);
                    }
                }
                sUnmakeMove(m, side, oc, oep, ohc, oh);
                if (abort) return 0;

                if (sc > best) {
                    best = sc;
                    bestM = m;
                    if (ply == 0) bestMoveRoot = m;
                }
                if (sc > alpha) alpha = sc;
                if (alpha >= beta) {
                    if (((m >> 16) & 0xFF) == 0 && m != killer1[ply]) {
                        killer2[ply] = killer1[ply];
                        killer1[ply] = m;
                    }
                    break;
                }
            }

            if (legal == 0) return inChk ? (-MATE + ply) : 0;

            int storeSc = best;
            if (storeSc > MATE - 64) storeSc += ply;
            else if (storeSc < -MATE + 64) storeSc -= ply;

            ttKey[ttIdx] = sHash;
            ttMove[ttIdx] = bestM;
            ttScore[ttIdx] = (short) storeSc;
            ttDepth[ttIdx] = (byte) depth;
            ttFlag[ttIdx] = (byte) (best <= origAlpha ? 2 : (best >= beta ? 3 : 1));
            return best;
        }

        int quiesce(int alpha, int beta, int side, int ply) {
            if ((++nodes & 2047) == 0 && (abort || System.currentTimeMillis() >= deadline)) {
                abort = true;
                return 0;
            }
            int standPat = side * sEval();
            if (ply >= MAX_PLY - 1) return standPat;
            if (standPat >= beta) return beta;
            if (standPat > alpha) alpha = standPat;

            int n = genMovesOn(sb, sCastle, sEp, side, ply);
            int[] moves = moveBuf[ply];
            int[] scores = scoreBuf[ply];
            int capCount = 0;

            for (int i = 0; i < n; i++) {
                int m = moves[i];
                int cap = Math.abs((byte) ((m >> 16) & 0xFF));
                int fl = (m >> 24) & 0xFF;
                if (cap != 0 || fl >= 4) {
                    int att = Math.abs(sb[m & 0xFF]);
                    moves[capCount] = m;
                    scores[capCount] = VAL[cap] * 10 - VAL[att] + (fl == 4 ? 800 : 0);
                    capCount++;
                }
            }

            for (int i = 0; i < capCount; i++) {
                int bestIdx = i;
                for (int j = i + 1; j < capCount; j++) {
                    if (scores[j] > scores[bestIdx]) bestIdx = j;
                }
                int m = moves[bestIdx]; moves[bestIdx] = moves[i]; moves[i] = m;
                int tmpS = scores[bestIdx]; scores[bestIdx] = scores[i]; scores[i] = tmpS;

                int oc = sCastle, oep = sEp, ohc = sHalfClock;
                long oh = sHash;
                sMakeMove(m, side);
                if (inCheckOn(sb, side)) {
                    sUnmakeMove(m, side, oc, oep, ohc, oh);
                    continue;
                }
                int sc = -quiesce(-beta, -alpha, -side, ply + 1);
                sUnmakeMove(m, side, oc, oep, ohc, oh);
                if (abort) return 0;

                if (sc >= beta) return beta;
                if (sc > alpha) alpha = sc;
            }
            return alpha;
        }

        // --- Shared Stateless Move Application & Generation ---

        static long applyMove(int[] board, int m, int side, long h, int[] stateOut) {
            int cstl = stateOut[0], epSq = stateOut[1], hClock = stateOut[2];
            int from = m & 0xFF, to = (m >> 8) & 0xFF;
            int cap = (byte) ((m >> 16) & 0xFF), flag = (m >> 24) & 0xFF;
            int piece = board[from];

            if (Math.abs(piece) == 1 || cap != 0) hClock = 0;
            else hClock++;

            h ^= Z_SIDE ^ Z_CASTLE[cstl];
            if (epSq != -1) h ^= Z_EP[epSq];

            h ^= Z_PC[piece + 6][from];
            if (cap != 0) {
                int capSq = (flag == 1) ? (to + (side == 1 ? 16 : -16)) : to;
                h ^= Z_PC[cap + 6][capSq];
            }

            int placed = piece;
            if (flag == 4) placed = 5 * side;
            else if (flag == 5) placed = 4 * side;
            else if (flag == 6) placed = 3 * side;
            else if (flag == 7) placed = 2 * side;

            board[to] = placed;
            board[from] = 0;
            h ^= Z_PC[placed + 6][to];

            if (flag == 1) board[to + (side == 1 ? 16 : -16)] = 0;
            if (flag == 2) {
                board[to - 1] = board[to + 1]; board[to + 1] = 0;
                h ^= Z_PC[4 * side + 6][to + 1] ^ Z_PC[4 * side + 6][to - 1];
            }
            if (flag == 3) {
                board[to + 1] = board[to - 2]; board[to - 2] = 0;
                h ^= Z_PC[4 * side + 6][to - 2] ^ Z_PC[4 * side + 6][to + 1];
            }

            epSq = -1;
            if (Math.abs(piece) == 1 && Math.abs(to - from) == 32) {
                int candEp = (from + to) >> 1;
                if ((((to - 1) & 0x88) == 0 && board[to - 1] == -side)
                    || (((to + 1) & 0x88) == 0 && board[to + 1] == -side)) {
                    epSq = candEp;
                }
            }

            if (from == 116 || to == 116) cstl &= ~3;
            if (from == 4 || to == 4) cstl &= ~12;
            if (from == 119 || to == 119) cstl &= ~1;
            if (from == 112 || to == 112) cstl &= ~2;
            if (from == 7 || to == 7) cstl &= ~4;
            if (from == 0 || to == 0) cstl &= ~8;

            if (epSq != -1) h ^= Z_EP[epSq];
            h ^= Z_CASTLE[cstl];

            stateOut[0] = cstl;
            stateOut[1] = epSq;
            stateOut[2] = hClock;
            return h;
        }

        static void undoMoveOn(int[] board, int m, int side) {
            int from = m & 0xFF, to = (m >> 8) & 0xFF;
            int cap = (byte) ((m >> 16) & 0xFF), flag = (m >> 24) & 0xFF;
            board[from] = (flag >= 4 && flag <= 7) ? side : board[to];
            board[to] = (flag == 1) ? 0 : cap;
            if (flag == 1) board[to + (side == 1 ? 16 : -16)] = -side;
            if (flag == 2) { board[to + 1] = board[to - 1]; board[to - 1] = 0; }
            if (flag == 3) { board[to - 2] = board[to + 1]; board[to + 1] = 0; }
        }

        final int[] uiStateTmp = new int[3];
        void makeMoveUi(int m, int side) {
            uiStateTmp[0] = castle; uiStateTmp[1] = ep; uiStateTmp[2] = halfMoveClock;
            curHash = applyMove(b, m, side, curHash, uiStateTmp);
            castle = uiStateTmp[0]; ep = uiStateTmp[1]; halfMoveClock = uiStateTmp[2];
        }

        void unmakeMoveUi(int m, int side, int oc, int oep, int ohc, long oh) {
            undoMoveOn(b, m, side);
            castle = oc; ep = oep; halfMoveClock = ohc; curHash = oh;
        }

        final int[] sStateTmp = new int[3];
        void sMakeMove(int m, int side) {
            sStateTmp[0] = sCastle; sStateTmp[1] = sEp; sStateTmp[2] = sHalfClock;
            sHash = applyMove(sb, m, side, sHash, sStateTmp);
            sCastle = sStateTmp[0]; sEp = sStateTmp[1]; sHalfClock = sStateTmp[2];
        }

        void sUnmakeMove(int m, int side, int oc, int oep, int ohc, long oh) {
            undoMoveOn(sb, m, side);
            sCastle = oc; sEp = oep; sHalfClock = ohc; sHash = oh;
        }

        static boolean attackedOn(int[] board, int sq, int bySide) {
            int pDir = bySide == 1 ? 16 : -16;
            int p1 = sq + pDir - 1, p2 = sq + pDir + 1;
            if ((p1 & 0x88) == 0 && board[p1] == bySide) return true;
            if ((p2 & 0x88) == 0 && board[p2] == bySide) return true;

            for (int d : N_OFF) {
                int t = sq + d;
                if ((t & 0x88) == 0 && board[t] == 2 * bySide) return true;
            }
            for (int d : K_OFF) {
                int t = sq + d;
                if ((t & 0x88) == 0 && board[t] == 6 * bySide) return true;
            }
            for (int d : R_OFF) {
                for (int t = sq + d; (t & 0x88) == 0; t += d) {
                    if (board[t] != 0) {
                        if (board[t] == 4 * bySide || board[t] == 5 * bySide) return true;
                        break;
                    }
                }
            }
            for (int d : B_OFF) {
                for (int t = sq + d; (t & 0x88) == 0; t += d) {
                    if (board[t] != 0) {
                        if (board[t] == 3 * bySide || board[t] == 5 * bySide) return true;
                        break;
                    }
                }
            }
            return false;
        }

        static boolean inCheckOn(int[] board, int side) {
            for (int i = 0; i < 128; i++) {
                if ((i & 0x88) != 0) { i += 7; continue; }
                if (board[i] == 6 * side) return attackedOn(board, i, -side);
            }
            return false;
        }

        int genMovesOn(int[] board, int cstl, int epSq, int side, int ply) {
            int[] out = moveBuf[ply];
            int c = 0;
            for (int sq = 0; sq < 128; sq++) {
                if ((sq & 0x88) != 0) { sq += 7; continue; }
                int p = board[sq];
                if (p * side <= 0) continue;
                int type = Math.abs(p);

                if (type == 1) {
                    int dir = side == 1 ? -16 : 16;
                    int startR = side == 1 ? 6 : 1, promR = side == 1 ? 0 : 7;
                    int to = sq + dir;
                    if ((to & 0x88) == 0 && board[to] == 0) {
                        if ((to >> 4) == promR) {
                            for (int fl = 4; fl <= 7; fl++)
                                out[c++] = sq | (to << 8) | (fl << 24);
                        } else {
                            out[c++] = sq | (to << 8);
                            int to2 = to + dir;
                            if ((sq >> 4) == startR && board[to2] == 0)
                                out[c++] = sq | (to2 << 8);
                        }
                    }
                    for (int df = -1; df <= 1; df += 2) {
                        int ct = sq + dir + df;
                        if ((ct & 0x88) != 0) continue;
                        if (board[ct] * side < 0) {
                            int capBits = (board[ct] & 0xFF) << 16;
                            if ((ct >> 4) == promR) {
                                for (int fl = 4; fl <= 7; fl++)
                                    out[c++] = sq | (ct << 8) | capBits | (fl << 24);
                            } else {
                                out[c++] = sq | (ct << 8) | capBits;
                            }
                        } else if (ct == epSq) {
                            out[c++] = sq | (ct << 8) | ((-side & 0xFF) << 16) | (1 << 24);
                        }
                    }
                } else if (type == 2 || type == 6) {
                    int[] offs = type == 2 ? N_OFF : K_OFF;
                    for (int d : offs) {
                        int to = sq + d;
                        if ((to & 0x88) == 0 && board[to] * side <= 0)
                            out[c++] = sq | (to << 8) | ((board[to] & 0xFF) << 16);
                    }
                    if (type == 6 && !inCheckOn(board, side)) {
                        if (side == 1 && sq == 116) {
                            if ((cstl & 1) != 0 && board[117] == 0 && board[118] == 0 && board[119] == 4
                                && !attackedOn(board, 117, -1) && !attackedOn(board, 118, -1))
                                out[c++] = 116 | (118 << 8) | (2 << 24);
                            if ((cstl & 2) != 0 && board[115] == 0 && board[114] == 0 && board[113] == 0 && board[112] == 4
                                && !attackedOn(board, 115, -1) && !attackedOn(board, 114, -1))
                                out[c++] = 116 | (114 << 8) | (3 << 24);
                        } else if (side == -1 && sq == 4) {
                            if ((cstl & 4) != 0 && board[5] == 0 && board[6] == 0 && board[7] == -4
                                && !attackedOn(board, 5, 1) && !attackedOn(board, 6, 1))
                                out[c++] = 4 | (6 << 8) | (2 << 24);
                            if ((cstl & 8) != 0 && board[3] == 0 && board[2] == 0 && board[1] == 0 && board[0] == -4
                                && !attackedOn(board, 3, 1) && !attackedOn(board, 2, 1))
                                out[c++] = 4 | (2 << 8) | (3 << 24);
                        }
                    }
                } else {
                    int[] dirs = type == 3 ? B_OFF : (type == 4 ? R_OFF : K_OFF);
                    for (int d : dirs) {
                        for (int to = sq + d; (to & 0x88) == 0; to += d) {
                            if (board[to] == 0) {
                                out[c++] = sq | (to << 8);
                            } else {
                                if (board[to] * side < 0)
                                    out[c++] = sq | (to << 8) | ((board[to] & 0xFF) << 16);
                                break;
                            }
                        }
                    }
                }
            }
            return c;
        }
    }
}
