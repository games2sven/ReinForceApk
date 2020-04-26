package com.highgreat.sven.reinforceapk;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;


public class MyService extends Service {


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i("Sven", "service:" + getApplication());
        Log.i("Sven", "service:" + getApplicationContext());
        Log.i("Sven", "service:" + getApplicationInfo().className);
    }
}
