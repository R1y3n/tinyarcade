package com.tiny.arcade;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

public final class CoopSession {
    public static final int ROLE_HOST = 1;
    public static final int ROLE_GUEST = -1;

    private static final String AUTH_PREFS = "tinyarcade_coop_auth";
    private static final String AUTH_REFRESH = "refresh_token";
    private static final String AUTH_UID = "uid";
    private static final String META_PREFIX = "coop_room_";
    private static final String PENDING_PREFIX = "coop_pending_";
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final long HEARTBEAT_MS = 4000L;
    private static final long OFFLINE_MS = 30000L;
    private static final long ACTIVATION_MS = 30000L;
    private static final long ROOM_TIMEOUT_MS = 600000L;

    public interface Listener {
        void onCoopState(CoopSession session, State state);

        void onCoopError(CoopSession session, String message);
    }

    public static final class State {
        public String status = "";
        public String hostUid = "";
        public String guestUid = "";
        public String moves = "";
        public String winner = "";
        public int hostColor = 1;
        public int turn = 1;
        public int ply;
        public long hostPing;
        public long guestPing;

        public boolean hasGuest() {
            return guestUid != null && guestUid.length() > 0;
        }

        public boolean isWaiting() {
            return "waiting".equals(status);
        }

        public boolean isActive() {
            return "active".equals(status) || "finished".equals(status);
        }

        public boolean isFinished() {
            return "finished".equals(status);
        }
    }

    public static final class Saved {
        public final String code;
        public final boolean host;
        public final int hostColor;

        Saved(String code, boolean host, int hostColor) {
            this.code = code;
            this.host = host;
            this.hostColor = hostColor;
        }

        public int side() {
            return host ? hostColor : -hostColor;
        }
    }

    private final Context context;
    private final String gameKey;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SecureRandom random = new SecureRandom();
    private volatile Listener listener;

    private final Object lock = new Object();
    private volatile State state = new State();
    private volatile boolean running;
    private volatile int generation;
    private volatile long lastOpponentSeen;
    private final boolean host;
    private volatile int hostColor;
    private volatile String code;
    private boolean freshHost;

    private volatile String authToken = "";
    private volatile String authUid = "";
    private volatile long authExpiryMs;
    private volatile HttpsURLConnection streamConnection;
    private volatile String androidCert;
    private Thread streamThread;
    private Thread heartbeatThread;

    public CoopSession(Context context, String gameKey, boolean host, String code,
                       int hostColor, Listener listener) {
        this.context = context.getApplicationContext();
        this.gameKey = gameKey;
        this.host = host;
        this.code = code == null ? "" : code.trim().toUpperCase(Locale.US);
        this.hostColor = hostColor == 1 ? 1 : -1;
        this.listener = listener;
        this.freshHost = host && this.code.length() == 0;
        if (freshHost) this.code = makeCode();
    }

    public static boolean isConfigured() {
        return FirebaseSecrets.API_KEY.length() > 0
            && FirebaseSecrets.DATABASE_URL.startsWith("https://");
    }

    public static Saved loadSaved(Context context, String gameKey) {
        SharedPreferences prefs = context.getSharedPreferences(META_PREFIX + gameKey, Context.MODE_PRIVATE);
        String code = prefs.getString("code", "");
        if (code == null || code.length() != 6) return null;
        boolean host = prefs.getBoolean("host", false);
        int hostColor = prefs.getInt("host_color", 1) == -1 ? -1 : 1;
        return new Saved(code, host, hostColor);
    }

    public static void clearSaved(Context context, String gameKey) {
        context.getSharedPreferences(META_PREFIX + gameKey, Context.MODE_PRIVATE)
            .edit().clear().commit();
    }

    private void saveMeta() {
        context.getSharedPreferences(META_PREFIX + gameKey, Context.MODE_PRIVATE)
            .edit()
            .putString("code", code)
            .putBoolean("host", host)
            .putInt("host_color", hostColor)
            .commit();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public String code() {
        return code;
    }

    public boolean isHost() {
        return host;
    }

    public int mySide() {
        return host ? hostColor : -hostColor;
    }

    public State state() {
        return state;
    }

    public boolean opponentOnline() {
        if (!state.hasGuest()) return false;
        long seen = lastOpponentSeen;
        return seen > 0 && SystemClock.uptimeMillis() - seen < OFFLINE_MS;
    }

    public void start() {
        if (!isConfigured()) {
            postError("Multiplayer is not configured in this build.");
            return;
        }
        synchronized (lock) {
            if (running) return;
            running = true;
            generation++;
            final int gen = generation;
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    connectWorker(gen);
                }
            }, "coop-connect");
            t.setDaemon(true);
            t.start();
        }
    }

    public void pause() {
        synchronized (lock) {
            running = false;
            generation++;
        }
        closeStream();
        Thread hb = heartbeatThread;
        heartbeatThread = null;
        if (hb != null) hb.interrupt();
    }

    public void close() {
        pause();
    }

    public void cancelRoom() {
        pause();
        context.getSharedPreferences(PENDING_PREFIX + gameKey, Context.MODE_PRIVATE)
            .edit().clear().commit();
        final String roomCode = code;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ensureAuth();
                    http("DELETE", roomPath(roomCode), null, authToken);
                } catch (Throwable ignored) {
                }
            }
        }, "coop-cancel");
        t.setDaemon(true);
        t.start();
    }

    public void requestRematch() {
        final State current = state;
        if (current == null || !current.isFinished()) return;
        final int gen = generation;
        final String tokenCode = code;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ensureAuth();
                    JSONObject patch = new JSONObject();
                    patch.put("status", "active");
                    patch.put("moves", "");
                    patch.put("ply", 0);
                    patch.put("turn", 1);
                    patch.put("winner", "");
                    HttpResult result = http("PATCH", roomPath(tokenCode), patch, authToken);
                    if (result.status != 200) throw new IOException("Could not start rematch (HTTP " + result.status + ").");
                    State reset = new State();
                    reset.status = "active";
                    reset.hostUid = current.hostUid;
                    reset.guestUid = current.guestUid;
                    reset.hostColor = current.hostColor;
                    reset.turn = 1;
                    reset.ply = 0;
                    reset.moves = "";
                    reset.winner = "";
                    reset.hostPing = current.hostPing;
                    reset.guestPing = current.guestPing;
                    lastOpponentSeen = SystemClock.uptimeMillis();
                    publish(reset);
                } catch (Throwable e) {
                    postError(ArcadeNet.explainError(context, e));
                }
            }
        }, "coop-rematch");
        t.setDaemon(true);
        t.start();
    }

    public void sendMoves(final String moves, final int ply, final int turn, final String winner) {
        final int gen = generation;
        final String tokenCode = code;
        savePending(moves, ply, turn, winner, tokenCode);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                if (!running || gen != generation) return;
                try {
                    ensureAuth();
                    JSONObject patch = new JSONObject();
                    patch.put("moves", moves == null ? "" : moves);
                    patch.put("ply", ply);
                    patch.put("turn", turn == 1 ? 1 : -1);
                    patch.put("winner", winner == null ? "" : winner);
                    patch.put("status", winner != null && winner.length() > 0 ? "finished" : "active");
                    IOException last = null;
                    for (int attempt = 0; attempt < 3; attempt++) {
                        try {
                            HttpResult r = http("PATCH", roomPath(tokenCode), patch, authToken);
                            if (r.status == 200) {
                                clearPending(ply);
                                return;
                            }
                            if (r.status == 401 || r.status == 403) {
                                authExpiryMs = 0L;
                                ensureAuth();
                                continue;
                            }
                            last = new IOException("HTTP " + r.status);
                        } catch (IOException e) {
                            last = e;
                        }
                        try {
                            Thread.sleep(350L + attempt * 500L);
                        } catch (InterruptedException ignored) {
                            return;
                        }
                    }
                    if (last != null) postError(ArcadeNet.explainError(context, last));
                } catch (Throwable e) {
                    postError(ArcadeNet.explainError(context, e));
                }
            }
        }, "coop-move");
        t.setDaemon(true);
        t.start();
    }

    private void connectWorker(int gen) {
        try {
            ensureAuth();
            if (freshHost) {
                createRoom(gen);
            } else {
                joinOrResume(gen);
            }
            if (!running || gen != generation) return;
            saveMeta();
            flushPending(gen);
            startHeartbeat(gen);
            startStream(gen);
        } catch (Throwable e) {
            if (running && gen == generation) {
                running = false;
                postError(ArcadeNet.explainError(context, e));
            }
        }
    }

    private void createRoom(int gen) throws Exception {
        JSONObject room = new JSONObject();
        room.put("status", "waiting");
        room.put("hostUid", authUid);
        room.put("hostColor", hostColor);
        room.put("hostPing", new JSONObject().put(".sv", "timestamp"));
        room.put("turn", 1);
        room.put("ply", 0);
        room.put("moves", "");
        room.put("winner", "");

        IOException last = null;
        for (int attempt = 0; attempt < 6 && running && gen == generation; attempt++) {
            if (code == null || code.length() != 6) code = makeCode();
            HttpResult r = http("PUT", roomPath(code), room, authToken);
            if (r.status == 200) {
                freshHost = false;
                lastOpponentSeen = SystemClock.uptimeMillis();
                State s = new State();
                s.status = "waiting";
                s.hostUid = authUid;
                s.hostColor = hostColor;
                s.turn = 1;
                s.ply = 0;
                s.moves = "";
                s.winner = "";
                publish(s);
                return;
            }
            if (r.status == 401 || r.status == 403) {
                code = makeCode();
                last = new IOException("Could not create room.");
                continue;
            }
            last = new IOException("Could not create room (HTTP " + r.status + ").");
        }
        throw last != null ? last : new IOException("Could not create room.");
    }

    private void joinOrResume(int gen) throws Exception {
        if (code == null || code.length() != 6) throw new IOException("Enter a valid 6-character code.");
        HttpResult fetched = http("GET", roomPath(code), null, authToken);
        if (fetched.status == 401 || fetched.status == 403) throw new IOException("Room not found or unavailable.");
        if (fetched.status != 200) throw new IOException("Could not reach that room.");
        if (fetched.body == null || "null".equals(fetched.body.trim())) {
            throw new IOException("Room not found or unavailable.");
        }
        JSONObject obj = new JSONObject(fetched.body);
        State s = parseState(obj);
        if (roomExpired(s)) {
            http("DELETE", roomPath(code), null, authToken);
            clearSaved(context, gameKey);
            throw new IOException("That room expired after 10 minutes of inactivity.");
        }
        hostColor = s.hostColor == -1 ? -1 : 1;

        if (host) {
            if (!authUid.equals(s.hostUid)) throw new IOException("This room belongs to another host.");
            lastOpponentSeen = SystemClock.uptimeMillis();
            publish(s);
            maybeActivate(gen, s);
            return;
        }

        if (s.guestUid.length() == 0) {
            JSONObject patch = new JSONObject();
            patch.put("guestUid", authUid);
            patch.put("guestPing", new JSONObject().put(".sv", "timestamp"));
            HttpResult joined = http("PATCH", roomPath(code), patch, authToken);
            if (joined.status != 200) throw new IOException("That room is full or no longer available.");
            s.guestUid = authUid;
            s.guestPing = 0L;
        } else if (!authUid.equals(s.guestUid)) {
            throw new IOException("That room already has two players.");
        }
        saveMeta();
        touchOpponent(s, true);
        publish(s);
    }

    private void maybeActivate(int gen, State s) {
        if (!host || !running || gen != generation) return;
        if (!"waiting".equals(s.status) || !s.hasGuest()) return;
        if (!guestFresh(s)) return;
        final String roomCode = code;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject patch = new JSONObject();
                    patch.put("status", "active");
                    http("PATCH", roomPath(roomCode), patch, authToken);
                } catch (Throwable ignored) {
                }
            }
        }, "coop-activate");
        t.setDaemon(true);
        t.start();
    }

    private void savePending(String moves, int ply, int turn, String winner, String roomCode) {
        context.getSharedPreferences(PENDING_PREFIX + gameKey, Context.MODE_PRIVATE)
            .edit()
            .putString("code", roomCode)
            .putString("moves", moves == null ? "" : moves)
            .putInt("ply", ply)
            .putInt("turn", turn == 1 ? 1 : -1)
            .putString("winner", winner == null ? "" : winner)
            .commit();
    }

    private void clearPending(int ply) {
        SharedPreferences prefs = context.getSharedPreferences(
            PENDING_PREFIX + gameKey, Context.MODE_PRIVATE);
        if (code.equals(prefs.getString("code", "")) && prefs.getInt("ply", -1) == ply) {
            prefs.edit().clear().commit();
        }
    }

    private void flushPending(int gen) {
        SharedPreferences prefs = context.getSharedPreferences(
            PENDING_PREFIX + gameKey, Context.MODE_PRIVATE);
        String pendingCode = prefs.getString("code", "");
        if (pendingCode.length() == 0) return;
        if (!code.equals(pendingCode)) {
            prefs.edit().clear().commit();
            return;
        }
        int ply = prefs.getInt("ply", -1);
        State s = state;
        if (s == null || ply < 0) return;
        if (s.ply >= ply) {
            prefs.edit().clear().commit();
            return;
        }
        if (s.ply + 1 == ply && s.turn == -prefs.getInt("turn", 1)) {
            sendMoves(prefs.getString("moves", ""), ply,
                prefs.getInt("turn", 1), prefs.getString("winner", ""));
        }
    }

    private boolean guestFresh(State s) {
        if (s == null || !s.hasGuest()) return false;
        if (opponentOnline()) return true;
        if (s.guestPing <= 0) return false;
        long age = System.currentTimeMillis() - s.guestPing;
        return age >= 0 && age < ACTIVATION_MS;
    }

    private void startHeartbeat(final int gen) {
        Thread old = heartbeatThread;
        if (old != null && old.isAlive()) return;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                while (running && gen == generation) {
                    try {
                        Thread.sleep(HEARTBEAT_MS);
                    } catch (InterruptedException e) {
                        return;
                    }
                    if (!running || gen != generation) return;
                    try {
                        ensureAuth();
                        JSONObject patch = new JSONObject();
                        patch.put(host ? "hostPing" : "guestPing",
                            new JSONObject().put(".sv", "timestamp"));
                        http("PATCH", roomPath(code), patch, authToken);
                        if (lastOpponentSeen > 0
                            && SystemClock.uptimeMillis() - lastOpponentSeen > ROOM_TIMEOUT_MS) {
                            expireRoom();
                            return;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        }, "coop-heartbeat");
        t.setDaemon(true);
        heartbeatThread = t;
        t.start();
    }

    private void startStream(final int gen) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                streamLoop(gen);
            }
        }, "coop-stream");
        t.setDaemon(true);
        streamThread = t;
        t.start();
    }

    private void streamLoop(int gen) {
        while (running && gen == generation) {
            HttpsURLConnection conn = null;
            try {
                ensureAuth();
                URL url = new URL(FirebaseSecrets.DATABASE_URL + roomPath(code)
                    + "?auth=" + enc(authToken));
                conn = (HttpsURLConnection) url.openConnection();
                PinnedTls.configure(conn, url.getHost());
                conn.setRequestProperty("Accept", "text/event-stream");
                conn.setRequestProperty("Cache-Control", "no-cache");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(0);
                streamConnection = conn;
                int status = conn.getResponseCode();
                if (status != 200) {
                    closeQuietly(conn);
                    if (running && gen == generation) {
                        if (status == 401 || status == 403) {
                            authExpiryMs = 0L;
                            postError("Room connection was rejected. Reconnecting...");
                        }
                        sleepQuietly(1400L);
                    }
                    continue;
                }

                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
                String event = "";
                StringBuilder data = new StringBuilder();
                boolean firstEvent = true;
                String line;
                while (running && gen == generation && (line = reader.readLine()) != null) {
                    if (line.length() == 0) {
                        if (data.length() > 0) {
                            handleEvent(event, data.toString());
                            if (firstEvent) {
                                firstEvent = false;
                                flushPending(gen);
                            }
                        }
                        event = "";
                        data.setLength(0);
                    } else if (line.startsWith("event:")) {
                        event = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        if (data.length() > 0) data.append('\n');
                        data.append(line.substring(5).trim());
                    }
                }
            } catch (Throwable e) {
                if (running && gen == generation) {
                    postError("Connection lost. Reconnecting...");
                    sleepQuietly(1600L);
                }
            } finally {
                closeQuietly(conn);
                if (streamConnection == conn) streamConnection = null;
            }
        }
    }

    private void handleEvent(String event, String raw) {
        if (raw == null || raw.length() == 0 || "null".equals(raw)) {
            if ("put".equals(event)) postError("This room was closed.");
            return;
        }
        try {
            JSONObject msg = new JSONObject(raw);
            String path = msg.optString("path", "/");
            JSONObject data = msg.optJSONObject("data");
            if (data == null) {
                if ("put".equals(event) && ("/".equals(path) || path.length() == 0)) {
                    postError("This room was closed.");
                }
                return;
            }
            State current = state;
            State next;
            if ("/".equals(path) || path.length() == 0) {
                next = "patch".equals(event) ? merge(current, data) : parseState(data);
            } else {
                next = merge(current, new JSONObject().put(path.substring(1), data));
            }
            touchOpponent(next, false);
            publish(next);
            if (host) maybeActivate(generation, next);
        } catch (Throwable ignored) {
        }
    }

    private boolean roomExpired(State s) {
        if (s == null) return false;
        long latest = Math.max(s.hostPing, s.guestPing);
        long age = latest > 0L ? System.currentTimeMillis() - latest : Long.MAX_VALUE;
        return age >= ROOM_TIMEOUT_MS;
    }

    private void touchOpponent(State next, boolean initial) {
        State old = state;
        long now = SystemClock.uptimeMillis();
        long nextPing = host ? next.guestPing : next.hostPing;
        long oldPing = old == null ? 0L : (host ? old.guestPing : old.hostPing);
        boolean changed = nextPing != oldPing;
        if (initial || changed) {
            if (host && next.hasGuest()) lastOpponentSeen = now;
            if (!host && next.hostUid.length() > 0) lastOpponentSeen = now;
        }
    }

    private void expireRoom() {
        if (!running) return;
        final String roomCode = code;
        pause();
        clearSaved(context, gameKey);
        context.getSharedPreferences(PENDING_PREFIX + gameKey, Context.MODE_PRIVATE)
            .edit().clear().commit();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ensureAuth();
                    http("DELETE", roomPath(roomCode), null, authToken);
                } catch (Throwable ignored) {
                }
            }
        }, "coop-expire");
        t.setDaemon(true);
        t.start();
        postError("Room closed after 10 minutes of inactivity.");
    }

    private void publish(State s) {
        state = s;
        main.post(new Runnable() {
            @Override
            public void run() {
                Listener l = listener;
                if (l == null) return;
                State snap = state;
                l.onCoopState(CoopSession.this, snap);
            }
        });
    }

    private void postError(final String message) {
        main.post(new Runnable() {
            @Override
            public void run() {
                Listener l = listener;
                if (l != null) l.onCoopError(CoopSession.this, message);
            }
        });
    }

    private static State parseState(JSONObject o) {
        State s = new State();
        s.status = o.optString("status", "");
        s.hostUid = o.optString("hostUid", "");
        s.guestUid = o.optString("guestUid", "");
        s.moves = o.optString("moves", "");
        s.winner = o.optString("winner", "");
        s.hostColor = o.optInt("hostColor", 1) == -1 ? -1 : 1;
        s.turn = o.optInt("turn", 1) == -1 ? -1 : 1;
        s.ply = Math.max(0, o.optInt("ply", 0));
        s.hostPing = o.optLong("hostPing", 0L);
        s.guestPing = o.optLong("guestPing", 0L);
        return s;
    }

    private static State merge(State old, JSONObject patch) {
        State s = new State();
        if (old != null) {
            s.status = old.status;
            s.hostUid = old.hostUid;
            s.guestUid = old.guestUid;
            s.moves = old.moves;
            s.winner = old.winner;
            s.hostColor = old.hostColor;
            s.turn = old.turn;
            s.ply = old.ply;
            s.hostPing = old.hostPing;
            s.guestPing = old.guestPing;
        }
        if (patch.has("status")) s.status = patch.optString("status", s.status);
        if (patch.has("hostUid")) s.hostUid = patch.optString("hostUid", s.hostUid);
        if (patch.has("guestUid")) s.guestUid = patch.optString("guestUid", s.guestUid);
        if (patch.has("moves")) s.moves = patch.optString("moves", s.moves);
        if (patch.has("winner")) s.winner = patch.optString("winner", s.winner);
        if (patch.has("hostColor")) s.hostColor = patch.optInt("hostColor", s.hostColor) == -1 ? -1 : 1;
        if (patch.has("turn")) s.turn = patch.optInt("turn", s.turn) == -1 ? -1 : 1;
        if (patch.has("ply")) s.ply = Math.max(0, patch.optInt("ply", s.ply));
        if (patch.has("hostPing")) s.hostPing = patch.optLong("hostPing", s.hostPing);
        if (patch.has("guestPing")) s.guestPing = patch.optLong("guestPing", s.guestPing);
        return s;
    }

    private void ensureAuth() throws IOException {
        String token = authToken;
        if (token != null && token.length() > 0 && System.currentTimeMillis() < authExpiryMs - 60000L) {
            return;
        }
        synchronized (lock) {
            token = authToken;
            if (token != null && token.length() > 0 && System.currentTimeMillis() < authExpiryMs - 60000L) {
                return;
            }
            SharedPreferences prefs = context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE);
            String refresh = prefs.getString(AUTH_REFRESH, "");
            if (refresh != null && refresh.length() > 0) {
                try {
                    refreshAuth(refresh);
                    return;
                } catch (Throwable ignored) {
                }
            }
            signUpAnonymous();
        }
    }

    private void signUpAnonymous() throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("returnSecureToken", true);
        } catch (Throwable ignored) {
        }
        HttpResult r = http("POST",
            "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=" + enc(FirebaseSecrets.API_KEY),
            body, null);
        if (r.status != 200) throw new IOException(
            "Could not start an anonymous multiplayer session (HTTP " + r.status + ").");
        try {
            JSONObject o = new JSONObject(r.body);
            String token = o.optString("idToken", "");
            String refresh = o.optString("refreshToken", "");
            String uid = o.optString("localId", "");
            int expires = o.optInt("expiresIn", 3600);
            if (token.length() == 0 || uid.length() == 0) throw new IOException("Bad auth response.");
            authToken = token;
            authUid = uid;
            authExpiryMs = System.currentTimeMillis() + expires * 1000L;
            context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)
                .edit().putString(AUTH_REFRESH, refresh).putString(AUTH_UID, uid).commit();
        } catch (IOException e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException("Could not parse the auth response.");
        }
    }

    private void refreshAuth(String refreshToken) throws IOException {
        String form = "grant_type=refresh_token&refresh_token=" + enc(refreshToken);
        HttpResult r = httpForm(
            "https://securetoken.googleapis.com/v1/token?key=" + enc(FirebaseSecrets.API_KEY), form);
        if (r.status != 200) throw new IOException(
            "Could not refresh multiplayer sign-in (HTTP " + r.status + ").");
        try {
            JSONObject o = new JSONObject(r.body);
            String token = o.optString("id_token", "");
            String uid = o.optString("user_id", "");
            String refresh = o.optString("refresh_token", refreshToken);
            int expires = o.optInt("expires_in", 3600);
            if (token.length() == 0 || uid.length() == 0) throw new IOException("Bad refresh response.");
            authToken = token;
            authUid = uid;
            authExpiryMs = System.currentTimeMillis() + expires * 1000L;
            context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)
                .edit().putString(AUTH_REFRESH, refresh).putString(AUTH_UID, uid).commit();
        } catch (IOException e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException("Could not parse refresh response.");
        }
    }

    private String roomPath(String roomCode) {
        return "/rooms/" + roomCode + ".json";
    }

    /**
     * Package + signing-certificate headers let a Google API key be restricted to
     * this app. Raw REST calls do not add them automatically the way Play Services
     * clients do.
     */
    private void addAndroidKeyHeaders(HttpURLConnection conn) {
        String cert = androidCert();
        if (cert.length() == 0) return;
        conn.setRequestProperty("X-Android-Package", context.getPackageName());
        conn.setRequestProperty("X-Android-Cert", cert);
    }

    private String androidCert() {
        String cached = androidCert;
        if (cached != null) return cached;
        synchronized (this) {
            if (androidCert != null) return androidCert;
            String value = "";
            try {
                PackageManager pm = context.getPackageManager();
                Signature[] signatures = pm.getPackageInfo(context.getPackageName(),
                    PackageManager.GET_SIGNATURES).signatures;
                if (signatures != null && signatures.length > 0) {
                    byte[] digest = MessageDigest.getInstance("SHA-1")
                        .digest(signatures[0].toByteArray());
                    StringBuilder hex = new StringBuilder(digest.length * 2);
                    for (byte b : digest) hex.append(String.format(Locale.US, "%02X", b & 0xFF));
                    value = hex.toString();
                }
            } catch (Throwable ignored) {
            }
            androidCert = value;
            return value;
        }
    }

    private HttpResult http(String method, String path, JSONObject body, String token) throws IOException {
        String urlText = path.startsWith("http") ? path : FirebaseSecrets.DATABASE_URL + path;
        if (token != null && token.length() > 0 && !path.startsWith("http")) {
            urlText += (urlText.indexOf('?') >= 0 ? "&" : "?") + "auth=" + enc(token);
        }
        HttpURLConnection conn = (HttpURLConnection) new URL(urlText).openConnection();
        if (conn instanceof HttpsURLConnection) {
            PinnedTls.configure((HttpsURLConnection) conn, new URL(urlText).getHost());
            addAndroidKeyHeaders(conn);
        }
        conn.setRequestMethod(method);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("Accept", "application/json");
        byte[] payload = null;
        if (body != null) {
            payload = body.toString().getBytes("UTF-8");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        }
        if (payload != null) {
            OutputStream out = conn.getOutputStream();
            out.write(payload);
            out.close();
        }
        int status = conn.getResponseCode();
        InputStream in = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
        String text = readAll(in);
        conn.disconnect();
        return new HttpResult(status, text);
    }

    private HttpResult httpForm(String urlText, String form) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlText).openConnection();
        if (conn instanceof HttpsURLConnection) {
            PinnedTls.configure((HttpsURLConnection) conn, new URL(urlText).getHost());
            addAndroidKeyHeaders(conn);
        }
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        byte[] payload = form.getBytes("UTF-8");
        OutputStream out = conn.getOutputStream();
        out.write(payload);
        out.close();
        int status = conn.getResponseCode();
        InputStream in = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
        String text = readAll(in);
        conn.disconnect();
        return new HttpResult(status, text);
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[2048];
        int n;
        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        in.close();
        return out.toString("UTF-8");
    }

    private void closeStream() {
        HttpsURLConnection c = streamConnection;
        streamConnection = null;
        closeQuietly(c);
    }

    private static void closeQuietly(HttpURLConnection c) {
        if (c == null) return;
        try {
            c.disconnect();
        } catch (Throwable ignored) {
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    private String makeCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        return sb.toString();
    }

    private static String enc(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Throwable e) {
            return "";
        }
    }

    private static final class HttpResult {
        final int status;
        final String body;

        HttpResult(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
