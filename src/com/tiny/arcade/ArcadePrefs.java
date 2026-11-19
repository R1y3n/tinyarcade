package com.tiny.arcade;

import android.content.Context;
import android.content.SharedPreferences;

public final class ArcadePrefs {
    private static final String PREFS = "tinyarcade_settings";
    private static final String K_SOUND = "sound_enabled";
    private static final String K_ASSISTANT = "assistant_enabled";
    private static final String K_HORIZONTAL = "menu_horizontal";
    private static final String K_CHESS_THEME = "chess_theme";
    private static final String K_SKIPPED_VERSION = "skipped_version";
    private static final String K_UPDATE_FIRST_SEEN = "update_first_seen_";
    private static final String K_UPDATE_LAST_CHECK = "update_last_check";
    private static final String K_UPDATE_VERSION = "update_version";
    private static final String K_UPDATE_APK = "update_apk";
    private static final String K_UPDATE_PAGE = "update_page";
    private static final String K_UPDATE_SIZE = "update_size";
    private static final String K_UPDATE_DIGEST = "update_digest";
    private static final String K_UPDATE_NOTES = "update_notes";

    private ArcadePrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean soundEnabled(Context context) {
        return prefs(context).getBoolean(K_SOUND, true);
    }

    public static void setSoundEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(K_SOUND, enabled).commit();
        ArcadeAudio.setEnabled(enabled);
    }

    public static boolean assistantEnabled(Context context) {
        return prefs(context).getBoolean(K_ASSISTANT, true);
    }

    public static void setAssistantEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(K_ASSISTANT, enabled).commit();
    }

    public static boolean horizontalMenu(Context context) {
        return prefs(context).getBoolean(K_HORIZONTAL, false);
    }

    public static void setHorizontalMenu(Context context, boolean horizontal) {
        prefs(context).edit().putBoolean(K_HORIZONTAL, horizontal).commit();
    }

    public static int chessTheme(Context context) {
        int theme = prefs(context).getInt(K_CHESS_THEME, 0);
        return theme < 0 || theme >= ChessTheme.COUNT ? 0 : theme;
    }

    public static void setChessTheme(Context context, int theme) {
        prefs(context).edit().putInt(K_CHESS_THEME, theme).commit();
    }

    public static String skippedVersion(Context context) {
        return prefs(context).getString(K_SKIPPED_VERSION, "");
    }

    public static void setSkippedVersion(Context context, String version) {
        prefs(context).edit().putString(K_SKIPPED_VERSION, version).commit();
    }

    public static long updateFirstSeen(Context context, String version) {
        return prefs(context).getLong(K_UPDATE_FIRST_SEEN + version, 0L);
    }

    public static void setUpdateFirstSeen(Context context, String version, long time) {
        prefs(context).edit().putLong(K_UPDATE_FIRST_SEEN + version, time).commit();
    }

    public static long updateLastCheck(Context context) {
        return prefs(context).getLong(K_UPDATE_LAST_CHECK, 0L);
    }

    public static void setUpdateLastCheck(Context context, long time) {
        prefs(context).edit().putLong(K_UPDATE_LAST_CHECK, time).commit();
    }

    public static void saveUpdate(Context context, String version, String apkUrl,
                                  String pageUrl, long size, String digest, String notes) {
        prefs(context).edit()
            .putString(K_UPDATE_VERSION, version)
            .putString(K_UPDATE_APK, apkUrl)
            .putString(K_UPDATE_PAGE, pageUrl)
            .putLong(K_UPDATE_SIZE, size)
            .putString(K_UPDATE_DIGEST, digest)
            .putString(K_UPDATE_NOTES, notes)
            .commit();
    }

    public static void setUpdateVersion(Context context, String version) {
        prefs(context).edit().putString(K_UPDATE_VERSION, version).commit();
    }

    public static String updateVersion(Context context) {
        return prefs(context).getString(K_UPDATE_VERSION, "");
    }

    public static String updateApkUrl(Context context) {
        return prefs(context).getString(K_UPDATE_APK, "");
    }

    public static String updatePageUrl(Context context) {
        return prefs(context).getString(K_UPDATE_PAGE, "");
    }

    public static long updateSize(Context context) {
        return prefs(context).getLong(K_UPDATE_SIZE, 0L);
    }

    public static String updateDigest(Context context) {
        return prefs(context).getString(K_UPDATE_DIGEST, "");
    }

    public static String updateNotes(Context context) {
        return prefs(context).getString(K_UPDATE_NOTES, "");
    }

    public static void clearUpdate(Context context) {
        prefs(context).edit()
            .remove(K_UPDATE_VERSION)
            .remove(K_UPDATE_APK)
            .remove(K_UPDATE_PAGE)
            .remove(K_UPDATE_SIZE)
            .remove(K_UPDATE_DIGEST)
            .remove(K_UPDATE_NOTES)
            .commit();
    }
}
