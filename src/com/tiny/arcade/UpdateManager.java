package com.tiny.arcade;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import javax.net.ssl.HttpsURLConnection;

/** GitHub release checker and compact in-app APK downloader. */
public final class UpdateManager {
    private static final String REPO = "R1y3n/tinyarcade";
    private static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final String ASSET_STABLE = "tinyarcade.apk";
    private static final long CHECK_INTERVAL = 12L * 60L * 60L * 1000L;
    private static final long STALE_AFTER = 10L * 24L * 60L * 60L * 1000L;
    private boolean showedPrompt;
    private File pendingInstall;
    private final Activity activity;

    public UpdateManager(Activity activity) {
        this.activity = activity;
    }

    public void checkNow() {
        if (!supported()) return;
        ArcadePrefs.setUpdateLastCheck(activity, 0L);
        check();
    }

    public void check() {
        if (!supported()) return;
        String savedVersion = ArcadePrefs.updateVersion(activity);
        if (savedVersion.length() > 0 && !newer(savedVersion, currentVersion())) {
            ArcadePrefs.clearUpdate(activity);
            ArcadePrefs.setSkippedVersion(activity, "");
        }
        long checkedAt = ArcadePrefs.updateLastCheck(activity);
        if (checkedAt > 0L && System.currentTimeMillis() - checkedAt < CHECK_INTERVAL) {
            showSavedUpdate();
            return;
        }
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    HttpsURLConnection connection = (HttpsURLConnection) new URL(API).openConnection();
                    PinnedTls.configure(connection, "api.github.com");
                    connection.setRequestProperty("Accept", "application/vnd.github+json");
                    connection.setRequestProperty("User-Agent", "TinyArcade-Android");
                    connection.setConnectTimeout(12000);
                    connection.setReadTimeout(16000);
                    int response = connection.getResponseCode();
                    if (response != 200) throw new java.io.IOException("GitHub release HTTP " + response);
                    String json = read(connection.getInputStream());
                    connection.disconnect();
                    org.json.JSONObject release = new org.json.JSONObject(json);
                    String tag = release.optString("tag_name", "").trim();
                    if (tag.startsWith("v") || tag.startsWith("V")) tag = tag.substring(1);
                    String page = release.optString("html_url", "https://github.com/" + REPO + "/releases/latest");
                    String notes = release.optString("body", "");
                    String apk = "";
                    long size = 0L;
                    String digest = "";
                    org.json.JSONArray assets = release.optJSONArray("assets");
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            org.json.JSONObject item = assets.optJSONObject(i);
                            if (item == null) continue;
                            if (ASSET_STABLE.equalsIgnoreCase(item.optString("name", ""))) {
                                apk = item.optString("browser_download_url", "");
                                size = item.optLong("size", 0L);
                                digest = item.optString("digest", "");
                                break;
                            }
                        }
                    }
                    ArcadePrefs.setUpdateLastCheck(activity, System.currentTimeMillis());
                    if (newer(tag, currentVersion())) {
                        ArcadePrefs.saveUpdate(activity, tag, apk, page, size, digest, notes);
                        ArcadePrefs.setSkippedVersion(activity, "");
                        if (ArcadePrefs.updateFirstSeen(activity, tag) == 0L) {
                            ArcadePrefs.setUpdateFirstSeen(activity, tag, System.currentTimeMillis());
                        }
                    } else {
                        ArcadePrefs.clearUpdate(activity);
                    }
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showSavedUpdate();
                        }
                    });
                } catch (Throwable ignored) {
                    ArcadePrefs.setUpdateLastCheck(activity, System.currentTimeMillis());
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showSavedUpdate();
                        }
                    });
                }
            }
        }, "release-check");
        t.setDaemon(true);
        t.start();
    }

    private void showSavedUpdate() {
        String version = ArcadePrefs.updateVersion(activity);
        if (version.length() == 0) return;
        if (!showedPrompt && !version.equals(ArcadePrefs.skippedVersion(activity))) {
            showedPrompt = true;
            showPrompt(version);
        } else showStaleWarning(version);
    }

    private void showPrompt(final String version) {
        if (activity.isFinishing()) return;
        String notes = ArcadePrefs.updateNotes(activity);
        if (notes.length() > 650) notes = notes.substring(0, 650) + "…";
        String message = "Version " + version + " is available.\n\n"
            + (notes.length() == 0 ? "Update TinyArcade for the latest fixes and online compatibility." : notes)
            + "\n\nDownloads start on Wi-Fi only.";
        AlertDialog.Builder dialog = new AlertDialog.Builder(activity)
            .setTitle("Update available")
            .setMessage(message)
            .setPositiveButton("Download", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    downloadOrOpenPage(version);
                }
            })
            .setNegativeButton("Skip", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    ArcadePrefs.setSkippedVersion(activity, version);
                    showStaleWarning(version);
                }
            });
        dialog.setNeutralButton("Release page", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                openPage(ArcadePrefs.updatePageUrl(activity));
            }
        });
        dialog.show();
    }

    private void showStaleWarning(String version) {
        if (activity.isFinishing()) return;
        long first = ArcadePrefs.updateFirstSeen(activity, version);
        if (first == 0L) return;
        if (System.currentTimeMillis() - first < STALE_AFTER) return;
        new AlertDialog.Builder(activity)
            .setTitle("TinyArcade is out of date")
            .setMessage("Online play may break with older versions. Version " + version
                + " has been available for 10 days.")
            .setPositiveButton("Get update", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    downloadOrOpenPage(version);
                }
            })
            .setNegativeButton("Later", null)
            .show();
    }

    private void downloadOrOpenPage(String version) {
        if (!wifiConnected()) {
            openPage(ArcadePrefs.updatePageUrl(activity));
            return;
        }
        String url = ArcadePrefs.updateApkUrl(activity);
        if (url.length() == 0) {
            openPage(ArcadePrefs.updatePageUrl(activity));
            return;
        }
        String filename = "tinyarcade-" + version.replaceAll("[^A-Za-z0-9._-]", "_") + ".apk";
        File dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) dir = new File(activity.getFilesDir(), "updates");
        File output = new File(dir, filename);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    download(url, output, ArcadePrefs.updateSize(activity),
                        ArcadePrefs.updateDigest(activity));
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            install(output);
                        }
                    });
                } catch (Throwable e) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            openPage(ArcadePrefs.updatePageUrl(activity));
                        }
                    });
                }
            }
        }, "apk-update");
        t.setDaemon(true);
        t.start();
        android.widget.Toast.makeText(activity, "Downloading update over Wi-Fi…", android.widget.Toast.LENGTH_SHORT).show();
    }

    private void download(String url, File output, long expectedSize, String expectedDigest) throws Exception {
        URL current = new URL(url);
        HttpURLConnection connection = null;
        for (int redirects = 0; redirects < 6; redirects++) {
            connection = (HttpURLConnection) current.openConnection();
            if (connection instanceof HttpsURLConnection) {
                PinnedTls.configure((HttpsURLConnection) connection, current.getHost());
            }
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(false);
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null) throw new java.io.IOException("Missing redirect");
                current = new URL(current, location);
                String host = current.getHost().toLowerCase(Locale.US);
                if (!host.equals("release-assets.githubusercontent.com")
                    && !host.endsWith(".githubusercontent.com")) {
                    throw new java.io.IOException("Untrusted release download host");
                }
                continue;
            }
            if (status != 200) throw new java.io.IOException("Download HTTP " + status);
            break;
        }
        if (connection == null || connection.getResponseCode() != 200) {
            throw new java.io.IOException("Release download failed");
        }
        File parent = output.getParentFile();
        if (parent != null) parent.mkdirs();
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        InputStream input = connection.getInputStream();
        FileOutputStream out = new FileOutputStream(output);
        byte[] buf = new byte[8192];
        long total = 0L;
        int n;
        while ((n = input.read(buf)) >= 0) {
            out.write(buf, 0, n);
            sha.update(buf, 0, n);
            total += n;
        }
        out.close();
        input.close();
        connection.disconnect();
        if (expectedSize > 0 && total != expectedSize) {
            output.delete();
            throw new java.io.IOException("APK size mismatch");
        }
        if (expectedDigest != null && expectedDigest.startsWith("sha256:")) {
            String actual = hex(sha.digest());
            if (!actual.equalsIgnoreCase(expectedDigest.substring(7))) {
                output.delete();
                throw new java.io.IOException("APK digest mismatch");
            }
        }
    }

    public void resumePendingInstall() {
        if (pendingInstall == null) return;
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            return;
        }
        File apk = pendingInstall;
        pendingInstall = null;
        install(apk);
    }

    private void install(File apk) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            pendingInstall = apk;
            try {
                Intent permission = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(permission);
                android.widget.Toast.makeText(activity, "Allow TinyArcade to install updates, then return here.",
                    android.widget.Toast.LENGTH_LONG).show();
            } catch (Throwable ignored) {
                android.widget.Toast.makeText(activity, "Allow installs from this source in Android settings.",
                    android.widget.Toast.LENGTH_LONG).show();
            }
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            // Android 7+ requires a content URI; older installers only accept file URIs.
            Uri target = Build.VERSION.SDK_INT >= 24
                ? UpdateFileProvider.uriFor(activity, apk)
                : Uri.fromFile(apk);
            intent.setDataAndType(target, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(intent);
        } catch (Throwable ignored) {
            openPage(ArcadePrefs.updatePageUrl(activity));
        }
    }

    private boolean wifiConnected() {
        try {
            ConnectivityManager cm = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = cm == null ? null : cm.getActiveNetworkInfo();
            return info != null && info.isConnected()
                && info.getType() == ConnectivityManager.TYPE_WIFI;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void openPage(String url) {
        try {
            if (url == null || !url.startsWith("https://github.com/")) {
                url = "https://github.com/" + REPO + "/releases/latest";
            }
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable ignored) {
        }
    }

    private boolean supported() {
        return !activity.getPackageName().endsWith(".nightly");
    }

    private String currentVersion() {
        try {
            return activity.getPackageManager()
                .getPackageInfo(activity.getPackageName(), 0).versionName;
        } catch (Throwable ignored) {
            return "0";
        }
    }

    private static boolean newer(String candidate, String current) {
        int[] a = versionParts(candidate);
        int[] b = versionParts(current);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int av = i < a.length ? a[i] : 0;
            int bv = i < b.length ? b[i] : 0;
            if (av != bv) return av > bv;
        }
        return false;
    }

    private static int[] versionParts(String v) {
        String[] parts = (v == null ? "" : v).split("[^0-9]+");
        int[] values = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { values[i] = Integer.parseInt(parts[i]); }
            catch (Throwable ignored) { values[i] = 0; }
        }
        return values;
    }

    private static String read(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[4096];
        int n;
        while ((n = in.read(b)) >= 0) out.write(b, 0, n);
        in.close();
        return out.toString("UTF-8");
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(Locale.US, "%02x", b & 0xFF));
        return out.toString();
    }
}
