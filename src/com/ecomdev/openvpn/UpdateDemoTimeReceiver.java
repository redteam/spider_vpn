package com.ecomdev.openvpn;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;

/**
 * Created by ymka on 03.08.14.
 */
public class UpdateDemoTimeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final int demoHours = context.getResources().getInteger(R.integer.demoTime);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        int leftHours = preferences.getInt(Constants.PREF_LEFT_HOURS, demoHours);
        leftHours--;
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(Constants.PREF_LEFT_HOURS, leftHours);
        if (leftHours == 0) {
            Intent timeIntent = new Intent(context, UpdateDemoTimeReceiver.class);
            PendingIntent broadcast = PendingIntent.getBroadcast(context, Constants.UPDATE_DEMO_TIME_RECEIVER_NUM, timeIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            alarmManager.cancel(broadcast);

            editor.putBoolean(Constants.PREF_IS_TIMEOUT, true);
        }

        editor.commit();
        Intent messageIntent = new Intent(Constants.TIMER_MESSAGE);
        messageIntent.putExtra(Constants.LEFT_HOURS, leftHours);
        LocalBroadcastManager.getInstance(context).sendBroadcast(messageIntent);
    }
}
