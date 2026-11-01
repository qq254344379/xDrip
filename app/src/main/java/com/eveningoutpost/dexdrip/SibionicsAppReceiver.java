package com.eveningoutpost.dexdrip;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;

import com.eveningoutpost.dexdrip.models.BgReading;
import com.eveningoutpost.dexdrip.models.JoH;
import com.eveningoutpost.dexdrip.models.Sensor;
import com.eveningoutpost.dexdrip.models.UserError.Log;
import com.eveningoutpost.dexdrip.utilitymodels.Intents;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Receives glucose broadcasts from the patched SiBionics (硅基动感) official app.
 *
 * The official SiBionics app patched with the cgmpatches project (see
 * patches/sibionics in this repository) sends an ordered broadcast with
 * action {@link Intents#XDRIP_PLUS_SI_APP} containing either:
 *  - direct extras: "sgv", "direction", "date" (+ "manongCode" verification), or
 *  - a JSON payload: collection = "entries", data = [{"type":"sgv","sgv":...,"direction":...,"date":...}]
 *
 * Readings are inserted through the standard xDrip pipeline so all display,
 * alarms, uploads and AAPS inter-app features work out of the box.
 */
public class SibionicsAppReceiver extends BroadcastReceiver {

    private static final String TAG = "SibionicsApp";
    private static final String MANONG_CODE = "magic372manongxyes";
    private static final Object lock = new Object();

    @Override
    public void onReceive(final Context context, final Intent intent) {
        new Thread() {
            @Override
            public void run() {
                PowerManager.WakeLock wl = JoH.getWakeLock("Sibionics-receiver", 60000);
                synchronized (lock) {
                    try {
                        if ((intent == null) || (intent.getAction() == null)) return;
                        if (!Intents.XDRIP_PLUS_SI_APP.equals(intent.getAction())) return;

                        final Bundle bundle = intent.getExtras();
                        if (bundle == null) return;

                        final String code = bundle.getString("manongCode");
                        if ((code == null) || (!code.equals(MANONG_CODE))) {
                            Log.d(TAG, "Ignoring SiBionics broadcast with invalid manongCode");
                            return;
                        }

                        if (!Sensor.isActive()) {
                            Home.toaststaticnext("Please use: Start Sensor from the menu for best results!");
                        }

                        // direct extra style (patched app Aab.smali)
                        if (bundle.containsKey("sgv") && bundle.containsKey("date")) {
                            final double sgv = bundle.getDouble("sgv", Double.NaN);
                            final long date = bundle.getLong("date", 0L);
                            final String direction = bundle.getString("direction");
                            if (!Double.isNaN(sgv) && date > 0) {
                                insertReading(sgv, direction, date);
                            }
                        }

                        // collection/data JSON style (compatible with other patched apps)
                        final String collection = bundle.getString("collection");
                        if ("entries".equals(collection)) {
                            final String data = bundle.getString("data");
                            if ((data != null) && (data.length() > 0)) {
                                try {
                                    final JSONArray array = new JSONArray(data);
                                    final JSONObject obj = array.getJSONObject(0);
                                    if ("sgv".equals(obj.optString("type"))) {
                                        insertReading(obj.getDouble("sgv"),
                                                obj.optString("direction", null),
                                                obj.getLong("date"));
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Got exception processing entries: " + e);
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Got exception in SiBionics receiver: " + e);
                    } finally {
                        if ((wl != null) && (wl.isHeld())) wl.release();
                    }
                }
            }
        }.start();
    }

    private void insertReading(double sgv, String direction, long date) {
        try {
            final BgReading bgr = BgReading.bgReadingInsertFromG5(sgv, date, "SiBionics");
            if (bgr != null) {
                if (direction != null) {
                    try {
                        bgr.calculated_value_slope = BgReading.slopefromName(direction);
                    } catch (Exception e) {
                        Log.d(TAG, "Could not map direction: " + direction);
                    }
                }
                bgr.source_info = "SiBionics";
                bgr.save();
                Log.d(TAG, "Inserted SiBionics reading: " + sgv + " @ " + JoH.dateTimeText(date));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error inserting SiBionics reading: " + e);
        }
    }
}
