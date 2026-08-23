package com.maoozos.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.webkit.WebViewAssetLoader;
import android.webkit.WebResourceResponse;
import android.widget.Toast;
import android.graphics.Color;
import android.view.View;
import android.view.Window;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends android.app.Activity {
    private static final int REQ_NOTIFICATIONS = 7001;
    private static final int FILE_CHOOSER = 7002;
    private static final int SAVE_BACKUP = 7003;
    private String pendingBackupJson;
    private String pendingBackupMime = "application/json";
    private WebView webView;
    private WebViewAssetLoader assetLoader;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NotificationHelper.ensureChannel(this);
        configureWindow();
        buildWebView();
        if (savedInstanceState == null) webView.loadUrl("https://appassets.androidplatform.net/assets/index.html");
        else webView.restoreState(savedInstanceState);
    }


    private void configureWindow() {
        Window w = getWindow();
        w.setStatusBarColor(Color.rgb(7, 17, 31));
        w.setNavigationBarColor(Color.rgb(7, 17, 31));
        if (Build.VERSION.SDK_INT >= 23) {
            w.getDecorView().setSystemUiVisibility(0);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void buildWebView() {
        assetLoader = new WebViewAssetLoader.Builder().addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(11,18,32));
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setTextZoom(100);
        settings.setDefaultFontSize(16);
        settings.setDefaultFixedFontSize(13);
        settings.setOffscreenPreRaster(false);
        settings.setSupportMultipleWindows(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                Toast.makeText(MainActivity.this, "MaoozOS recovered from a WebView rendering problem.", Toast.LENGTH_LONG).show();
                buildWebView();
                webView.loadUrl("https://appassets.androidplatform.net/assets/index.html");
                return true;
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (u != null && ("http".equalsIgnoreCase(u.getScheme()) || "https".equalsIgnoreCase(u.getScheme()))) {
                    openExternal(u);
                    return true;
                }
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                try {
                    Uri u = Uri.parse(url);
                    if ("http".equalsIgnoreCase(u.getScheme()) || "https".equalsIgnoreCase(u.getScheme())) {
                        openExternal(u);
                        return true;
                    }
                } catch (Exception ignored) {}
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    Intent i = params.createIntent();
                    startActivityForResult(i, FILE_CHOOSER);
                    return true;
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "No file picker is available.", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });

        webView.addJavascriptInterface(new AndroidBridge(this), "MaoozAndroid");
    }

    private void openExternal(Uri uri) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No browser is available for this link.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER && fileCallback != null) {
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    results = new Uri[n];
                    for (int i = 0; i < n; i++) results[i] = data.getClipData().getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
            }
            fileCallback.onReceiveValue(results);
            fileCallback = null;
            return;
        }
        if (requestCode == SAVE_BACKUP) {
            try {
                if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingBackupJson != null) {
                    Uri uri = data.getData();
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                        if (out == null) throw new IllegalStateException("The selected destination could not be opened.");
                        out.write(pendingBackupJson.getBytes(StandardCharsets.UTF_8));
                        out.flush();
                    }
                    Toast.makeText(this, "MaoozOS backup exported successfully.", Toast.LENGTH_LONG).show();
                } else if (resultCode == RESULT_CANCELED) {
                    Toast.makeText(this, "Backup export cancelled. Your data was not changed.", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Backup could not be saved: " + (e.getMessage() == null ? "Unknown error." : e.getMessage()), Toast.LENGTH_LONG).show();
            } finally {
                pendingBackupJson = null;
            }
        }
    }

    public class AndroidBridge {
        private final Context context;
        AndroidBridge(Context context) { this.context = context; }

        @JavascriptInterface public boolean isAndroid() { return true; }

        @JavascriptInterface public void saveBackupFile(String json, String suggestedName) {
            runOnUiThread(() -> {
                try {
                    if (json == null || json.trim().isEmpty()) throw new IllegalArgumentException("Backup data is empty.");
                    // Validate that we received a real JSON object before opening the save dialog.
                    JSONObject check = new JSONObject(json);
                    if (!"MaoozOS".equals(check.optString("app"))) throw new IllegalArgumentException("This is not a MaoozOS backup.");
                    pendingBackupJson = json;
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("application/json");
                    i.putExtra(Intent.EXTRA_TITLE, (suggestedName == null || suggestedName.trim().isEmpty()) ? "MaoozOS-full-backup.json" : suggestedName);
                    startActivityForResult(i, SAVE_BACKUP);
                } catch (Exception e) {
                    Toast.makeText(context, "Backup export could not start: " + (e.getMessage() == null ? "Unknown error." : e.getMessage()), Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface public void requestNotifications() {
            runOnUiThread(() -> {
                try {
                    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
                        return;
                    }
                    NotificationHelper.ensureChannel(context);
                    Toast.makeText(context, "MaoozOS Android notifications are enabled.", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(context, "Notifications could not be enabled. In-app alerts are still available.", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface public void postNotification(String title, String message) {
            runOnUiThread(() -> {
                try {
                    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(context, "Notification permission is not enabled. In-app alert shown instead.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    NotificationHelper.ensureChannel(context);
                    Intent open = new Intent(context, MainActivity.class);
                    open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    int notificationId = (int)(System.currentTimeMillis() & 0x7fffffff);
                    PendingIntentHolder.send(context, title == null ? "MaoozOS" : title, message == null ? "MaoozOS reminder" : message, open, notificationId);
                } catch (Exception e) {
                    Toast.makeText(context, "Native notification failed. In-app alert remains available.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface public void openNotificationSettings() {
            runOnUiThread(() -> {
                Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(i);
            });
        }

        @JavascriptInterface public void openExactAlarmSettings() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 31) {
                    try {
                        Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                        i.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    } catch (Exception e) {
                        Toast.makeText(context, "Exact-alarm settings are not available on this device.", Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(context, "This Android version does not require exact-alarm access.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface public void testNotification() {
            runOnUiThread(() -> {
                try {
                    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(context, "Allow MaoozOS notifications first, then test again.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    NotificationHelper.ensureChannel(context);
                    Intent open = new Intent(context, MainActivity.class);
                    PendingIntentHolder.send(context, "MaoozOS test notification", "Native Android notifications are working.", open);
                } catch (Exception e) {
                    Toast.makeText(context, "Test notification failed safely.", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface public void scheduleTestReminder(long delayMs) {
            try {
                long safeDelay = Math.max(5_000L, Math.min(delayMs, 24L * 60L * 60L * 1000L));
                long fireAt = System.currentTimeMillis() + safeDelay;
                ReminderReceiver.schedule(context, "__test__", fireAt, "MaoozOS test reminder", "This notification was scheduled by MaoozOS.", 0L, 0L, false, null, null, 0L, 0L);
            } catch (Exception e) {
                Toast.makeText(context, "Test reminder could not be scheduled: " + (e.getMessage() == null ? "Unknown error." : e.getMessage()), Toast.LENGTH_LONG).show();
            }
        }

        @JavascriptInterface public void syncReminders(String json) {
            try {
                JSONArray jobs = new JSONArray(json == null ? "[]" : json);
                android.content.SharedPreferences prefs = getSharedPreferences("maoozos_native_reminders", MODE_PRIVATE);
                java.util.Set<String> desired = new java.util.HashSet<>();
                long now = System.currentTimeMillis();
                for (int i = 0; i < jobs.length(); i++) {
                    JSONObject o = jobs.getJSONObject(i);
                    String id = o.getString("id");
                    desired.add(id);
                    long classAt = o.optLong("classAt", 0L);
                    long leadMinutes = Math.max(0L, o.optLong("leadMinutes", 0L));
                    long fireAt = o.optLong("fireAt", 0L);
                    if (classAt > 0L) fireAt = classAt - (leadMinutes * 60_000L);
                    if (fireAt <= now) continue;
                    String title = o.optString("title", "MaoozOS reminder");
                    String message = o.optString("message", "Upcoming reminder");
                    long periodMs = o.optLong("periodMs", 0L);
                    long endAt = o.optLong("endAt", 0L);
                    boolean quiet = o.optBoolean("quietHours", false);
                    String qs = o.optString("quietStart", null);
                    String qe = o.optString("quietEnd", null);
                    String existing = prefs.getString("record_" + id, null);
                    boolean same = false;
                    if (existing != null) {
                        try {
                            JSONObject old = new JSONObject(existing);
                            same = old.optLong("fireAt", Long.MIN_VALUE) == fireAt
                                    && old.optLong("classAt", Long.MIN_VALUE) == classAt
                                    && old.optLong("leadMinutes", Long.MIN_VALUE) == leadMinutes
                                    && old.optLong("periodMs", Long.MIN_VALUE) == periodMs
                                    && old.optLong("endAt", Long.MIN_VALUE) == endAt;
                        } catch (Exception ignored) {}
                    }
                    if (!same) {
                        ReminderReceiver.schedule(context, id, fireAt, title, message, periodMs, endAt, quiet, qs, qe, classAt, leadMinutes);
                    }
                }
                java.util.Map<String, ?> all = prefs.getAll();
                for (String key : all.keySet()) {
                    if (!key.startsWith("record_")) continue;
                    String id = key.substring("record_".length());
                    if (!desired.contains(id)) ReminderReceiver.cancel(context, id);
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(context, "Reminder sync could not be completed. Your timetable is still saved.", Toast.LENGTH_LONG).show());
            }
        }
    }

    static final class PendingIntentHolder {
        static void send(Context context, String title, String message, Intent open) {
            send(context, title, message, open, 99991);
        }

        static void send(Context context, String title, String message, Intent open, int notificationId) {
            NotificationHelper.ensureChannel(context);
            android.app.PendingIntent content = android.app.PendingIntent.getActivity(
                    context, (notificationId & 0x7fffffff), open,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
            );
            android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                    ? new android.app.Notification.Builder(context, NotificationHelper.CHANNEL_ID)
                    : new android.app.Notification.Builder(context);
            builder.setSmallIcon(R.drawable.ic_maoozos_notification)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(new android.app.Notification.BigTextStyle().bigText(message))
                    .setContentIntent(content)
                    .setAutoCancel(true)
                    .setPriority(android.app.Notification.PRIORITY_HIGH);
            android.app.NotificationManager manager = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(notificationId, builder.build());
        }
    }
}
