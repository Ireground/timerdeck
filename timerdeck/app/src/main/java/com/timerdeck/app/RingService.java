package com.timerdeck.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

import java.util.LinkedHashSet;
import java.util.List;

/** Plays the alarm sound and shows the "Timer done" notification. */
public class RingService extends Service {
    static final String ACTION_RING = "com.timerdeck.app.RING";
    static final String ACTION_STOP_ONE = "com.timerdeck.app.STOP_ONE";
    static final String ACTION_STOP_ALL = "com.timerdeck.app.STOP_ALL";

    private static final String CHANNEL = "ringing";
    private static final String FALLBACK_CHANNEL = "ringing_fallback";
    private static final int NOTIF_ID = 42;
    private static final long AUTO_STOP_MS = 3 * 60 * 1000L;

    private final LinkedHashSet<Integer> ringing = new LinkedHashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private ToneGenerator tone;
    private Vibrator vibrator;
    private boolean inForeground = false;

    private final Runnable autoStop = this::stopAll;

    private final Runnable beep = new Runnable() {
        @Override
        public void run() {
            if (tone != null) {
                tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600);
                handler.postDelayed(this, 1200);
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_RING.equals(action)) {
            ringing.add(intent.getIntExtra("id", -1));
            Notification n = buildNotification();
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIF_ID, n);
            }
            inForeground = true;
            startSound();
            handler.removeCallbacks(autoStop);
            handler.postDelayed(autoStop, AUTO_STOP_MS);
        } else if (ACTION_STOP_ONE.equals(action)) {
            int id = intent.getIntExtra("id", -1);
            ringing.remove(id);
            markIdle(id);
            if (ringing.isEmpty()) {
                finish();
            } else {
                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm != null) nm.notify(NOTIF_ID, buildNotification());
            }
        } else if (ACTION_STOP_ALL.equals(action)) {
            stopAll();
        } else if (ringing.isEmpty()) {
            finish();
        }
        return START_NOT_STICKY;
    }

    private void stopAll() {
        ringing.clear();
        markIdle(-1);
        finish();
    }

    /** Puts a ringing timer (or all of them when id is -1) back to its start time. */
    private void markIdle(int id) {
        List<TimerItem> list = TimerStore.load(this);
        boolean changed = false;
        for (TimerItem t : list) {
            if (t.state == TimerItem.RINGING && (id == -1 || t.id == id)) {
                t.state = TimerItem.IDLE;
                t.remainingMs = t.durationMs;
                changed = true;
            }
        }
        if (changed) TimerStore.save(this, list);
    }

    private void finish() {
        stopSound();
        handler.removeCallbacks(autoStop);
        if (inForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            inForeground = false;
        }
        stopSelf();
    }

    private Notification buildNotification() {
        ensureChannels(this);
        List<TimerItem> list = TimerStore.load(this);
        StringBuilder names = new StringBuilder();
        for (int id : ringing) {
            TimerItem t = TimerStore.find(list, id);
            if (t == null) continue;
            if (names.length() > 0) names.append(", ");
            names.append(t.displayLabel());
        }

        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stop = new Intent(this, RingService.class);
        stop.setAction(ACTION_STOP_ALL);
        PendingIntent stopPi = PendingIntent.getService(this, 2, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Action stopAction = new Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_timer), "Stop", stopPi).build();

        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle(ringing.size() > 1 ? "Timers done" : "Timer done")
                .setContentText(names.length() > 0 ? names.toString() : "Time's up")
                .setCategory(Notification.CATEGORY_ALARM)
                .setOngoing(true)
                .setContentIntent(openPi)
                .setFullScreenIntent(openPi, true)
                .addAction(stopAction)
                .build();
    }

    private static AudioAttributes alarmAttributes() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
    }

    private static Uri alarmUri() {
        Uri u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        return u;
    }

    private void startSound() {
        if (player == null && tone == null) {
            try {
                player = new MediaPlayer();
                player.setAudioAttributes(alarmAttributes());
                player.setDataSource(this, alarmUri());
                player.setLooping(true);
                player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
                player.prepare();
                player.start();
            } catch (Exception e) {
                if (player != null) {
                    player.release();
                    player = null;
                }
                try {
                    tone = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
                    handler.post(beep);
                } catch (Exception ignored) {
                    tone = null;
                }
            }
        }
        if (vibrator == null) {
            vibrator = getSystemService(Vibrator.class);
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 700, 500}, 0),
                        alarmAttributes());
            }
        }
    }

    private void stopSound() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
        handler.removeCallbacks(beep);
        if (tone != null) {
            tone.release();
            tone = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
    }

    @Override
    public void onDestroy() {
        stopSound();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    static void ensureChannels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Ringing timers",
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setSound(null, null);
            ch.enableVibration(false);
            nm.createNotificationChannel(ch);
        }
        if (nm.getNotificationChannel(FALLBACK_CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(FALLBACK_CHANNEL, "Timer alerts (backup)",
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setSound(alarmUri(), alarmAttributes());
            ch.enableVibration(true);
            nm.createNotificationChannel(ch);
        }
    }

    /** Used only if Android refuses to start the ringing service. */
    static void fallbackNotify(Context c, String label) {
        ensureChannels(c);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        Intent open = new Intent(c, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(c, 3, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, FALLBACK_CHANNEL)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle("Timer done")
                .setContentText(label)
                .setCategory(Notification.CATEGORY_ALARM)
                .setContentIntent(openPi)
                .setAutoCancel(true)
                .build();
        nm.notify((int) (System.currentTimeMillis() % 100000) + 100, n);
    }
}
