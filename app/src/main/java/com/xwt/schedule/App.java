package com.xwt.schedule;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

public class App extends Application {

    public static final String CHANNEL_REMINDER = "class_reminder";

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_REMINDER,
                    getString(R.string.channel_reminder),
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription(getString(R.string.channel_reminder_desc));
            ch.enableVibration(true);
            ch.enableLights(true);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }
}
