package com.timerdeck.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.List;

/** After a restart, puts running timers back on the system alarm schedule. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        List<TimerItem> list = TimerStore.load(c);
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (TimerItem t : list) {
            if (t.state == TimerItem.RUNNING && t.endAt > now) {
                TimerStore.schedule(c, t);
            } else if (t.state == TimerItem.RUNNING || t.state == TimerItem.RINGING) {
                t.state = TimerItem.IDLE;
                t.remainingMs = t.durationMs;
                changed = true;
            }
        }
        if (changed) TimerStore.save(c, list);
    }
}
