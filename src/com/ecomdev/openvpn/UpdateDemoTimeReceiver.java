package com.ecomdev.openvpn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created by ymka on 03.08.14.
 */
public class UpdateDemoTimeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("qwe", "update " + System.currentTimeMillis());
        Intent intent1 = new Intent(context, DemoService.class);
        DemoService.LocalBinder iBinder = ((DemoService.LocalBinder)peekService(context, intent1));

        DemoService service = iBinder.getService();
        service.updateTime();


    }
}
