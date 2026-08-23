package com.maoozos.app;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONObject;

public class ReminderReceiver extends BroadcastReceiver {
    private static final String PREFS = "maoozos_native_reminders";
    private static final String RECORD = "record_";

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationHelper.ensureChannel(context);

        String id = intent.getStringExtra("id");
        String title = intent.getStringExtra("title");
        String message = intent.getStringExtra("message");
        long periodMs = intent.getLongExtra("periodMs", 0L);
        long classAt = intent.getLongExtra("classAt", 0L);
        long leadMinutes = intent.getLongExtra("leadMinutes", 0L);
        long endAt = intent.getLongExtra("endAt", 0L);
        boolean quietHours = intent.getBooleanExtra("quietHours", false);
        String quietStart = intent.getStringExtra("quietStart");
        String quietEnd = intent.getStringExtra("quietEnd");

        if (id == null) return;

        // Post through a helper activity intent so tapping the notification opens MaoozOS.
        android.app.Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new android.app.Notification.Builder(context, NotificationHelper.CHANNEL_ID);
        } else {
            builder = new android.app.Notification.Builder(context);
        }

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(
                context,
                id.hashCode() & 0x7fffffff,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String safeTitle = title == null ? "MaoozOS reminder" : title;
        String safeMessage = message == null ? "You have an upcoming item." : message;
        builder.setSmallIcon(R.drawable.ic_maoozos_notification)
                .setContentTitle(safeTitle)
                .setContentText(safeMessage)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(safeMessage))
                .setAutoCancel(true)
                .setContentIntent(content)
                .setPriority(android.app.Notification.PRIORITY_HIGH)
                .setCategory(android.app.Notification.CATEGORY_REMINDER);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (!inQuietHours(quietHours, quietStart, quietEnd)) {
            if (manager != null) manager.notify(id.hashCode() & 0x7fffffff, builder.build());
        }

        // Re-schedule recurring reminders without depending on the WebView being alive.
        if (periodMs > 0) {
            long baseClassAt = classAt > 0 ? classAt : System.currentTimeMillis();
            long nextClassAt = baseClassAt + periodMs;
            long next = leadMinutes > 0 ? nextClassAt - (leadMinutes * 60_000L) : nextClassAt;
            while (next <= System.currentTimeMillis()) { next += periodMs; }
            if (endAt <= 0 || next <= endAt) {
                schedule(context, id, next, title, message, periodMs, endAt, quietHours, quietStart, quietEnd);
            } else {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(RECORD + id).apply();
            }
        }
    }

    public static void schedule(Context context, String id, long fireAt, String title, String message, long periodMs, long endAt) {
        schedule(context, id, fireAt, title, message, periodMs, endAt, false, null, null, 0L, 0L);
    }

    public static void schedule(Context context, String id, long fireAt, String title, String message, long periodMs, long endAt, boolean quietHours, String quietStart, String quietEnd) {
        schedule(context, id, fireAt, title, message, periodMs, endAt, quietHours, quietStart, quietEnd, 0L, 0L);
    }

    public static void schedule(Context context, String id, long fireAt, String title, String message, long periodMs, long endAt, boolean quietHours, String quietStart, String quietEnd, long classAt, long leadMinutes) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("id", id);
        intent.putExtra("title", title);
        intent.putExtra("message", message);
        intent.putExtra("periodMs", periodMs);
        intent.putExtra("classAt", classAt);
        intent.putExtra("leadMinutes", leadMinutes);
        intent.putExtra("endAt", endAt);
        intent.putExtra("quietHours", quietHours);
        intent.putExtra("quietStart", quietStart);
        intent.putExtra("quietEnd", quietEnd);

        int requestCode = id.hashCode() & 0x7fffffff;
        PendingIntent pending = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarm.canScheduleExactAlarms()) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending);
        } else {
            alarm.set(AlarmManager.RTC_WAKEUP, fireAt, pending);
        }

        JSONObject obj = new JSONObject();
        try {
            obj.put("id", id);
            obj.put("fireAt", fireAt);
            obj.put("classAt", classAt);
            obj.put("leadMinutes", leadMinutes);
            obj.put("title", title);
            obj.put("message", message);
            obj.put("periodMs", periodMs);
            obj.put("endAt", endAt);
            obj.put("quietHours", quietHours);
            obj.put("quietStart", quietStart);
            obj.put("quietEnd", quietEnd);
        } catch (Exception ignored) {}
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(RECORD + id, obj.toString()).apply();
    }

    private static boolean inQuietHours(boolean enabled, String start, String end) {
        if (!enabled || start == null || end == null || start.length() != 5 || end.length() != 5) return false;
        try {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            int now = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE);
            int s = Integer.parseInt(start.substring(0, 2)) * 60 + Integer.parseInt(start.substring(3));
            int e = Integer.parseInt(end.substring(0, 2)) * 60 + Integer.parseInt(end.substring(3));
            return s == e ? false : (s < e ? now >= s && now < e : now >= s || now < e);
        } catch (Exception ignored) { return false; }
    }

    public static void cancel(Context context, String id) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pending = PendingIntent.getBroadcast(
                context,
                id.hashCode() & 0x7fffffff,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm != null) alarm.cancel(pending);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(RECORD + id).apply();
    }
}
