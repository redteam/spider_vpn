package com.ecomdev.openvpn;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import com.ecomdev.openvpn.fragments.Utils;

/**
 * Created by ymka on 03.08.14.
 */
public class DemoService extends Service {

    private final LocalBinder mBinder = new LocalBinder();
    private UpdateTimeListener mListener;

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void setListener(UpdateTimeListener listener) {
        mListener = listener;
    }

    public void updateTime() {
        final int demoHours = getResources().getInteger(R.integer.demoTime);
        //hours in milliseconds
        int demoTime = demoHours * 60 * 60 * 1000;
        SharedPreferences preferences = getSharedPreferences(Constants.sMAIN_SHARED_PREFERENCE, MODE_PRIVATE);
        long startDemoTime = preferences.getLong(Constants.sPREF_START_DEMO_TIME, 0);
        boolean isTimePassed = Utils.isDifferenceNotExceed(System.currentTimeMillis(), startDemoTime, demoTime);
        if (isTimePassed) {
            preferences.edit().putBoolean(Constants.sPREF_IS_TIMEOUT, true);

            Intent intent = new Intent(getApplicationContext(), UpdateDemoTimeReceiver.class);
            PendingIntent broadcast = PendingIntent.getBroadcast(getApplicationContext(), Constants.sUPDATE_DEMO_TIME_RECEIVER_NUM, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            AlarmManager alarmManager = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
            alarmManager.cancel(broadcast);

            mListener.timeOut();
        } else {
            preferences.edit().putInt(Constants.sPREF_LEFT_HOURS, demoHours - 1);
            mListener.updateDemoHours();
        }


    }


    public class LocalBinder extends Binder {
        public DemoService getService() {
            return DemoService.this;
        }
    }

    public interface UpdateTimeListener {
        
        public void updateDemoHours();
        public void timeOut();

    }
}
