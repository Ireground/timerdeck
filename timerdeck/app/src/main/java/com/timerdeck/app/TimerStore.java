package com.timerdeck.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Saves the timer list on the device and schedules the system alarms. */
final class TimerStore {
    private static final String PREFS = "timerdeck";

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static synchronized List<TimerItem> load(Context c) {
        List<TimerItem> list = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("timers", "[]"));
            for (int i = 0; i < a.length(); i++) {
                list.add(TimerItem.fromJson(a.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    static synchronized void save(Context c, List<TimerItem> list) {
        JSONArray a = new JSONArray();
        for (TimerItem t : list) {
            try {
                a.put(t.toJson());
            } catch (Exception ignored) {
            }
        }
        SharedPreferences p = prefs(c);
        p.edit()
                .putString("timers", a.toString())
                .putLong("rev", p.getLong("rev", 0) + 1)
                .commit();
    }

    /** Changes every time the list is saved, so the screen knows to refresh. */
    static synchronized long rev(Context c) {
        return prefs(c).getLong("rev", 0);
    }

    static synchronized int nextId(Context c) {
        SharedPreferences p = prefs(c);
        int id = p.getInt("nextId", 1);
        p.edit().putInt("nextId", id + 1).commit();
        return id;
    }

    static TimerItem find(List<TimerItem> list, int id) {
        for (TimerItem t : list) {
            if (t.id == id) return t;
        }
        return null;
    }

    private static PendingIntent alarmIntent(Context c, int id) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction("com.timerdeck.app.FIRE");
        i.setData(Uri.parse("timer://" + id));
        i.putExtra("id", id);
        return PendingIntent.getBroadcast(c, id, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context c, TimerItem t) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        PendingIntent pi = alarmIntent(c, t.id);
        try {
            Intent open = new Intent(c, MainActivity.class);
            PendingIntent show = PendingIntent.getActivity(c, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(t.endAt, show), pi);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.endAt, pi);
        }
    }

    static void cancel(Context c, int id) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am != null) am.cancel(alarmIntent(c, id));
    }
}
