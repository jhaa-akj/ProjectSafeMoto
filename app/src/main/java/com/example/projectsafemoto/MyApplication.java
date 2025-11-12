package com.example.projectsafemoto;

import android.app.Application;
import com.google.android.material.color.DynamicColors;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // This line applies the user's wallpaper colors to your app
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}