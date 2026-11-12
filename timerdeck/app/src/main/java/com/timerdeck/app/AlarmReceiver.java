package com.timerdeck.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.List;

/** Called by Android when a timer reaches zero. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        int id = intent.getIntExtra("id", -1);
        if (id < 0) return;

        List<TimerItem> list = TimerStore.load(c);
        TimerItem t = TimerStore.find(list, id);
        if (t == null || t.state != TimerItem.RUNNING) return;

        t.state = TimerItem.RINGING;
        TimerStore.save(c, list);

        Intent s = new Intent(c, RingService.class);
        s.setAction(RingService.ACTION_RING);
        s.putExtra("id", id);
        try {
            c.startForegroundService(s);
        } catch (Exception e) {
            RingService.fallbackNotify(c, t.displayLabel());
        }
    }
}
