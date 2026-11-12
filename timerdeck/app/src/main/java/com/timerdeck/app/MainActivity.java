package com.timerdeck.app;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class MainActivity extends Activity {

    private static final int BG = 0xFF121417;
    private static final int CARD = 0xFF1E2228;
    private static final int CARD_RINGING = 0xFF4A1C20;
    private static final int TEXT = 0xFFECEFF4;
    private static final int DIM = 0xFF8A94A6;
    private static final int GREEN = 0xFF2EBD6B;
    private static final int AMBER = 0xFFE0A030;
    private static final int RED = 0xFFE5484D;
    private static final int BLUE = 0xFF3B82F6;
    private static final int GREY_BTN = 0xFF3A404A;

    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;

    private LinearLayout listBox;
    private List<TimerItem> timers = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private long knownRev = -1;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 200);
        }
    };

    private static class Row {
        TimerItem t;
        LinearLayout card;
        TextView label;
        TextView time;
        Button main;
        Button reset;
        int shownState = -1;
    }

    // ---------------------------------------------------------------- setup

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(16), dp(16), dp(8));

        TextView title = new TextView(this);
        title.setText("Timerdeck");
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, WRAP, 1f));

        Button add = makeButton("+  New timer", BLUE);
        add.setOnClickListener(v -> showEditor(null));
        header.addView(add);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        listBox.setPadding(dp(12), dp(4), dp(12), dp(24));
        scroll.addView(listBox);
        root.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        setContentView(root);
        askPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        knownRev = -1;
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    private void askPermissions() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = getSystemService(AlarmManager.class);
            if (am != null && !am.canScheduleExactAlarms()) {
                new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                        .setTitle("Allow alarms")
                        .setMessage("So timers ring on time even when the screen is off, "
                                + "please allow \"Alarms & reminders\" for Timerdeck.")
                        .setPositiveButton("Open settings", (d, w) -> {
                            try {
                                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        Uri.parse("package:" + getPackageName())));
                            } catch (Exception ignored) {
                            }
                        })
                        .setNegativeButton("Later", null)
                        .show();
            }
        }
    }

    // ---------------------------------------------------------------- list display

    private void refresh() {
        long r = TimerStore.rev(this);
        if (r != knownRev) {
            knownRev = r;
            timers = TimerStore.load(this);
            rebuild();
        } else {
            updateRows();
        }
    }

    private void rebuild() {
        listBox.removeAllViews();
        rows.clear();
        if (timers.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No timers yet.\nTap  \"+ New timer\"  to add one.");
            empty.setTextColor(DIM);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(80), dp(16), dp(16));
            listBox.addView(empty);
        } else {
            for (TimerItem t : timers) {
                Row r = makeRow(t);
                rows.add(r);
                listBox.addView(r.card);
            }
        }
        updateRows();

        boolean anyRinging = false;
        for (TimerItem t : timers) {
            if (t.state == TimerItem.RINGING) anyRinging = true;
        }
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(anyRinging);
            setTurnScreenOn(anyRinging);
        }
    }

    private Row makeRow(TimerItem t) {
        Row r = new Row();
        r.t = t;

        r.card = new LinearLayout(this);
        r.card.setOrientation(LinearLayout.HORIZONTAL);
        r.card.setGravity(Gravity.CENTER_VERTICAL);
        r.card.setPadding(dp(16), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(0, dp(6), 0, dp(6));
        r.card.setLayoutParams(lp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);

        r.label = new TextView(this);
        r.label.setTextColor(DIM);
        r.label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        r.label.setSingleLine(true);
        texts.addView(r.label);

        r.time = new TextView(this);
        r.time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        r.time.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        r.time.setSingleLine(true);
        texts.addView(r.time);

        r.card.addView(texts, new LinearLayout.LayoutParams(0, WRAP, 1f));

        r.reset = makeButton("↺", GREY_BTN);
        r.reset.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(56), dp(56));
        rp.setMargins(0, 0, dp(8), 0);
        r.card.addView(r.reset, rp);

        r.main = makeButton("Start", GREEN);
        r.card.addView(r.main, new LinearLayout.LayoutParams(dp(120), dp(56)));

        final int id = t.id;
        r.main.setOnClickListener(v -> onMain(id));
        r.reset.setOnClickListener(v -> onReset(id));
        r.card.setOnClickListener(v -> {
            TimerItem cur = TimerStore.find(timers, id);
            if (cur != null) showEditor(cur);
        });
        r.card.setOnLongClickListener(v -> {
            confirmDelete(id);
            return true;
        });
        return r;
    }

    private void updateRows() {
        long now = System.currentTimeMillis();
        for (Row r : rows) {
            TimerItem t = r.t;
            long rem = t.remainingNow(now);
            boolean ringing = t.state == TimerItem.RINGING
                    || (t.state == TimerItem.RUNNING && rem <= 0);
            int shown = ringing ? TimerItem.RINGING : t.state;

            r.label.setText(t.displayLabel() + "   ·   " + format(t.durationMs));
            r.time.setText(ringing ? "Done!" : format(rem));

            if (shown != r.shownState) {
                r.shownState = shown;
                switch (shown) {
                    case TimerItem.RUNNING:
                        style(r, CARD, GREEN, "Pause", AMBER, true);
                        break;
                    case TimerItem.PAUSED:
                        style(r, CARD, AMBER, "Resume", GREEN, true);
                        break;
                    case TimerItem.RINGING:
                        style(r, CARD_RINGING, RED, "Stop", RED, false);
                        break;
                    default:
                        style(r, CARD, TEXT, "Start", GREEN, false);
                        break;
                }
            }
        }
    }

    private void style(Row r, int cardColor, int timeColor, String mainText, int mainColor,
                       boolean showReset) {
        r.card.setBackground(rounded(cardColor, dp(16)));
        r.time.setTextColor(timeColor);
        r.main.setText(mainText);
        r.main.setBackground(rounded(mainColor, dp(12)));
        r.reset.setVisibility(showReset ? View.VISIBLE : View.INVISIBLE);
    }

    // ---------------------------------------------------------------- actions

    /** Loads the latest list, changes one timer, saves and redraws. */
    private void mutate(int id, Consumer<TimerItem> change) {
        List<TimerItem> list = TimerStore.load(this);
        TimerItem t = TimerStore.find(list, id);
        if (t == null) return;
        change.accept(t);
        commit(list);
    }

    private void commit(List<TimerItem> list) {
        TimerStore.save(this, list);
        timers = list;
        knownRev = TimerStore.rev(this);
        rebuild();
    }

    private void onMain(int id) {
        final long now = System.currentTimeMillis();
        TimerItem cur = TimerStore.find(TimerStore.load(this), id);
        if (cur == null) return;
        if (cur.state == TimerItem.RINGING
                || (cur.state == TimerItem.RUNNING && cur.endAt <= now)) {
            stopRinging(id);
            return;
        }
        mutate(id, x -> {
            if (x.state == TimerItem.IDLE) {
                x.endAt = now + x.durationMs;
                x.state = TimerItem.RUNNING;
                TimerStore.schedule(this, x);
            } else if (x.state == TimerItem.PAUSED) {
                x.endAt = now + x.remainingMs;
                x.state = TimerItem.RUNNING;
                TimerStore.schedule(this, x);
            } else if (x.state == TimerItem.RUNNING) {
                x.remainingMs = Math.max(0, x.endAt - now);
                x.state = TimerItem.PAUSED;
                TimerStore.cancel(this, x.id);
            }
        });
    }

    private void onReset(int id) {
        stopRinging(id);
    }

    /** Back to the start time; also silences it if it is ringing. */
    private void stopRinging(int id) {
        mutate(id, x -> {
            TimerStore.cancel(this, x.id);
            x.state = TimerItem.IDLE;
            x.remainingMs = x.durationMs;
        });
        Intent s = new Intent(this, RingService.class);
        s.setAction(RingService.ACTION_STOP_ONE);
        s.putExtra("id", id);
        try {
            startService(s);
        } catch (Exception ignored) {
        }
    }

    private void confirmDelete(int id) {
        TimerItem cur = TimerStore.find(timers, id);
        if (cur == null) return;
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Delete \"" + cur.displayLabel() + "\"?")
                .setPositiveButton("Delete", (d, w) -> deleteTimer(id))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteTimer(int id) {
        stopRinging(id);
        TimerStore.cancel(this, id);
        List<TimerItem> list = TimerStore.load(this);
        TimerItem t = TimerStore.find(list, id);
        if (t != null) list.remove(t);
        commit(list);
    }

    // ---------------------------------------------------------------- add / edit dialog

    private void showEditor(TimerItem existing) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(12), dp(24), dp(4));

        EditText name = new EditText(this);
        name.setHint("Label (e.g. Pizza, Render check)");
        name.setSingleLine(true);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        if (existing != null) name.setText(existing.label);
        box.addView(name);

        long dur = existing != null ? existing.durationMs : 5 * 60 * 1000L;
        long totalSec = dur / 1000;

        LinearLayout pickers = new LinearLayout(this);
        pickers.setOrientation(LinearLayout.HORIZONTAL);
        pickers.setGravity(Gravity.CENTER);
        pickers.setPadding(0, dp(12), 0, 0);
        NumberPicker h = picker(23, (int) (totalSec / 3600));
        NumberPicker m = picker(59, (int) ((totalSec % 3600) / 60));
        NumberPicker s = picker(59, (int) (totalSec % 60));
        pickers.addView(pickerColumn(h, "hours"));
        pickers.addView(pickerColumn(m, "min"));
        pickers.addView(pickerColumn(s, "sec"));
        box.addView(pickers);

        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(existing == null ? "New timer" : "Edit timer")
                .setView(box)
                .setPositiveButton("OK", (d, w) -> saveFromEditor(existing, name, h, m, s, false))
                .setNeutralButton("OK + Start", (d, w) -> saveFromEditor(existing, name, h, m, s, true))
                .setNegativeButton("Cancel", null);

        AlertDialog dialog = b.create();

        if (existing != null) {
            TextView del = new TextView(this);
            del.setText("Delete this timer");
            del.setTextColor(RED);
            del.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            del.setPadding(0, dp(18), 0, dp(6));
            final int id = existing.id;
            del.setOnClickListener(v -> {
                dialog.dismiss();
                confirmDelete(id);
            });
            box.addView(del);
        }
        dialog.show();
    }

    private void saveFromEditor(TimerItem existing, EditText name, NumberPicker h, NumberPicker m,
                                NumberPicker s, boolean start) {
        final long dur = (h.getValue() * 3600L + m.getValue() * 60L + s.getValue()) * 1000L;
        if (dur <= 0) {
            Toast.makeText(this, "Set a time first", Toast.LENGTH_SHORT).show();
            return;
        }
        final String label = name.getText().toString().trim();
        final long now = System.currentTimeMillis();

        if (existing == null) {
            List<TimerItem> list = TimerStore.load(this);
            TimerItem t = new TimerItem();
            t.id = TimerStore.nextId(this);
            t.label = label;
            t.durationMs = dur;
            t.remainingMs = dur;
            t.state = TimerItem.IDLE;
            if (start) {
                t.state = TimerItem.RUNNING;
                t.endAt = now + dur;
                TimerStore.schedule(this, t);
            }
            list.add(t);
            commit(list);
            return;
        }

        final int id = existing.id;
        if (existing.state == TimerItem.RINGING) stopRinging(id);
        mutate(id, x -> {
            boolean sameTime = x.durationMs == dur;
            x.label = label;
            if (sameTime && !start) return; // only the name changed: leave it running
            TimerStore.cancel(this, x.id);
            x.durationMs = dur;
            x.remainingMs = dur;
            x.state = TimerItem.IDLE;
            if (start) {
                x.state = TimerItem.RUNNING;
                x.endAt = now + dur;
                TimerStore.schedule(this, x);
            }
        });
    }

    private NumberPicker picker(int max, int value) {
        NumberPicker p = new NumberPicker(this);
        p.setMinValue(0);
        p.setMaxValue(max);
        p.setValue(value);
        p.setFormatter(v -> String.format(Locale.US, "%02d", v));
        return p;
    }

    private LinearLayout pickerColumn(NumberPicker p, String unit) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(10), 0, dp(10), 0);
        col.addView(p);
        TextView t = new TextView(this);
        t.setText(unit);
        t.setTextColor(DIM);
        t.setGravity(Gravity.CENTER);
        col.addView(t);
        return col;
    }

    // ---------------------------------------------------------------- helpers

    private Button makeButton(String text, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(rounded(color, dp(12)));
        b.setStateListAnimator(null);
        b.setPadding(dp(16), 0, dp(16), 0);
        return b;
    }

    private static GradientDrawable rounded(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private static String format(long ms) {
        long sec = (ms + 999) / 1000;
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.US, "%d:%02d", m, s);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
