package com.highgreat.sven.reinforceapk;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.i("Sven","activity:"+getApplication());
        Log.i("Sven","activity:"+getApplicationContext());
        Log.i("Sven","activity:"+getApplicationInfo().className);

        startService(new Intent(this, MyService.class));

        Intent intent = new Intent("com.highgreat.sven.test");
        intent.setComponent(new ComponentName(getPackageName(), MyBroadCastReciver.class.getName
                ()));
        sendBroadcast(intent);

        getContentResolver().delete(Uri.parse("content://com.highgreat.sven.reinforceapk.MyProvider"), null,
                null);
        
    }
}
