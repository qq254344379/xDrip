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
import com.eveningoutpost.dexdrip.utils.DexCollectionType;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Receives glucose broadcasts from the Ottai (欧态) official app.
 *
 * The Ottai app (China: ottai.com.cn, International: com.ottai.seas) can share
 * glucose data with AAPS ("Share with AAPS" in Profile > Ottai hub). AAPS
 * listens on {@link Intents#XDRIP_PLUS_OTTAI_APP} (China) and
 * {@link Intents#XDRIP_PLUS_OTTAI_APP_INTL} (International) with the same
 * payload format as other patched CGM apps:
 *  - collection = "entries", data = [{"type":"sgv","sgv":...,"direction":...,"date":...}]
 * xDrip+ reuses these exact AAPS broadcast actions so a single source feeds
 * both AAPS and xDrip+.
 *
 * Readings are inserted through the standard xDrip pipeline so all display,
 * alarms, uploads and AAPS inter-app features work out of the box.
 */
public class OttaiAppReceiver extends BroadcastReceiver {

    private static final String TAG = "OttaiApp";
    private static final Object lock = new Object();

    @Override
    public void onReceive(final Context context, final Intent intent) {
        // Only process the broadcast when the matching Ottai source is
        // selected in the data source list (like AidexReceiver / LibreReceiver).
        final String sourceName;
        switch (DexCollectionType.getDexCollectionType()) {
            case OttaiReceiver:
                sourceName = "Ottai";
                break;
            default:
                Log.d(TAG, "Received Ottai broadcast, but no Ottai source selected as collector.");
                return;
        }
        new Thread() {
            @Override
            public void run() {
                PowerManager.WakeLock wl = JoH.getWakeLock("Ottai-receiver", 60000);
                synchronized (lock) {
                    try {
                        if ((intent == null) || (intent.getAction() == null)) return;
                        if (!Intents.XDRIP_PLUS_OTTAI_APP.equals(intent.getAction())
                                && !Intents.XDRIP_PLUS_OTTAI_APP_INTL.equals(intent.getAction())
                                && !Intents.XDRIP_PLUS_OTTAI_XDRIP_APP.equals(intent.getAction())) return;

                        final Bundle bundle = intent.getExtras();
                        if (bundle == null) return;

                        if (!Sensor.isActive()) {
                            Home.toaststaticnext("Please use: Start Sensor from the menu for best results!");
                        }

                        // direct extra style (kept for compatibility with patched apps)
                        if (bundle.containsKey("sgv") && bundle.containsKey("date")) {
                            final double sgv = bundle.getDouble("sgv", Double.NaN);
                            final long date = bundle.getLong("date", 0L);
                            final String direction = bundle.getString("direction");
                            if (!Double.isNaN(sgv) && date > 0) {
                                insertReading(sgv, direction, date, sourceName);
                            }
                        }

                        // collection/data JSON style (AAPS compatible payload)
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
                                                obj.getLong("date"),
                                                sourceName);
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Got exception processing entries: " + e);
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Got exception in Ottai receiver: " + e);
                    } finally {
                        if ((wl != null) && (wl.isHeld())) wl.release();
                    }
                }
            }
        }.start();
    }

    private void insertReading(double sgv, String direction, long date, final String sourceName) {
        try {
            final BgReading bgr = BgReading.bgReadingInsertFromG5(sgv, date, sourceName);
            if (bgr != null) {
                if (direction != null) {
                    try {
                        bgr.calculated_value_slope = BgReading.slopefromName(direction);
                    } catch (Exception e) {
                        Log.d(TAG, "Could not map direction: " + direction);
                    }
                }
                bgr.source_info = sourceName;
                bgr.save();
                Log.d(TAG, "Inserted " + sourceName + " reading: " + sgv + " @ " + JoH.dateTimeText(date));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error inserting " + sourceName + " reading: " + e);
        }
    }
}
