package com.highgreat.sven.reinforceapk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;


public class MyBroadCastReciver extends BroadcastReceiver {


    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i("Sven", "reciver:" + context);
        Log.i("Sven","reciver:" + context.getApplicationContext());
        Log.i("Sven","reciver:" + context.getApplicationInfo().className);

    }
}
