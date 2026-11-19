package com.tiny.arcade;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Locale;

final class ArcadeNet {
    static final String NO_RADIO = "Turn on Wi-Fi or mobile data to play multiplayer.";
    static final String NO_INTERNET =
        "Wi-Fi or mobile data is on, but the internet is unreachable. Check your connection.";

    interface Callback {
        void onResult(Status status);
    }

    static final class Status {
        final boolean radiosOn;
        final boolean online;
        final String message;

        Status(boolean radiosOn, boolean online, String message) {
            this.radiosOn = radiosOn;
            this.online = online;
            this.message = message;
        }
    }

    private ArcadeNet() {
    }

    static void checkAsync(final Context context, final Callback callback) {
        final Context app = context.getApplicationContext();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                final Status status = check(app);
                if (callback == null) return;
                android.os.Handler main = new android.os.Handler(
                    android.os.Looper.getMainLooper());
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onResult(status);
                    }
                });
            }
        }, "arcade-net");
        t.setDaemon(true);
        t.start();
    }

    static Status check(Context context) {
        boolean radiosOn = radiosOn(context);
        boolean online = radiosOn && (reachable("1.1.1.1") || reachable("8.8.8.8"));
        String message = !radiosOn ? NO_RADIO : (!online ? NO_INTERNET : "");
        return new Status(radiosOn, online, message);
    }

    static boolean radiosOn(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo info = cm.getActiveNetworkInfo();
            if (info == null || !info.isConnected()) return false;
            int type = info.getType();
            return type == ConnectivityManager.TYPE_WIFI
                || type == ConnectivityManager.TYPE_MOBILE
                || type == ConnectivityManager.TYPE_ETHERNET;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean reachable(String host) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(
                new String[]{"ping", "-c", "1", "-W", "2", host});
            if (process.waitFor() == 0) return true;
        } catch (Throwable ignored) {
        } finally {
            if (process != null) process.destroy();
        }
        for (int port : new int[]{53, 443}) {
            Socket socket = null;
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), 1500);
                return true;
            } catch (Throwable ignored) {
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return false;
    }

    static String explainError(Context context, Throwable error) {
        String raw = error == null ? "" : error.getMessage();
        String lower = raw == null ? "" : raw.toLowerCase(Locale.US);
        boolean networkProblem = lower.contains("unable to resolve host")
            || lower.contains("no address associated")
            || lower.contains("unknownhost")
            || lower.contains("failed to connect")
            || lower.contains("connection refused")
            || lower.contains("network is unreachable")
            || lower.contains("timed out")
            || lower.contains("timeout")
            || lower.contains("socket")
            || lower.contains("econn")
            || lower.contains("ssl")
            || lower.contains("certificate");

        if (networkProblem) {
            Status status = check(context);
            if (!status.online) return status.message;
            if (lower.contains("ssl") || lower.contains("certificate")) {
                return "Secure connection failed. Check your date and time, then try again.";
            }
            return "Could not reach multiplayer. Please try again.";
        }
        if (lower.contains("http 5")) return "Multiplayer service is busy. Please try again.";
        if (lower.contains("http 401") || lower.contains("http 403")) {
            return "That room is no longer available.";
        }
        if (lower.contains("http 404")) return "Room not found.";
        if (lower.contains("http ")) return "Multiplayer service is unavailable. Please try again.";
        if (raw == null || raw.trim().length() == 0) {
            return "Could not connect to multiplayer. Please try again.";
        }
        if (raw.startsWith("Room ")
            || raw.startsWith("That room ")
            || raw.startsWith("This room ")
            || raw.startsWith("Enter a valid ")
            || raw.startsWith("Could not create room")
            || raw.startsWith("Could not reach that room")
            || raw.startsWith("Could not start an anonymous")
            || raw.startsWith("Multiplayer is not configured")) {
            return raw;
        }
        return "Could not connect to multiplayer. Please try again.";
    }
}
