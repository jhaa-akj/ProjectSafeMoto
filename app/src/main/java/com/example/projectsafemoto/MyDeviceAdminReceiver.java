package com.example.projectsafemoto;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

// This class is mostly a "listener" for admin events.
// It tells the system your app is an admin.
public class MyDeviceAdminReceiver extends DeviceAdminReceiver {
    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Toast.makeText(context, "Device Admin: Enabled", Toast.LENGTH_SHORT).show();
    }
}