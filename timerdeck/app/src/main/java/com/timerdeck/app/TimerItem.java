package com.timerdeck.app;

import org.json.JSONException;
import org.json.JSONObject;

/** One named timer. */
final class TimerItem {
    static final int IDLE = 0;
    static final int RUNNING = 1;
    static final int PAUSED = 2;
    static final int RINGING = 3;

    int id;
    String label = "";
    long durationMs;
    int state = IDLE;
    long endAt;        // when a running timer finishes (wall clock ms)
    long remainingMs;  // time left while paused

    long remainingNow(long now) {
        switch (state) {
            case RUNNING: return Math.max(0, endAt - now);
            case PAUSED:  return remainingMs;
            case RINGING: return 0;
            default:      return durationMs;
        }
    }

    String displayLabel() {
        return (label == null || label.trim().isEmpty()) ? "Timer" : label.trim();
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("label", label == null ? "" : label);
        o.put("durationMs", durationMs);
        o.put("state", state);
        o.put("endAt", endAt);
        o.put("remainingMs", remainingMs);
        return o;
    }

    static TimerItem fromJson(JSONObject o) {
        TimerItem t = new TimerItem();
        t.id = o.optInt("id");
        t.label = o.optString("label", "");
        t.durationMs = o.optLong("durationMs");
        t.state = o.optInt("state", IDLE);
        t.endAt = o.optLong("endAt");
        t.remainingMs = o.optLong("remainingMs");
        return t;
    }
}
