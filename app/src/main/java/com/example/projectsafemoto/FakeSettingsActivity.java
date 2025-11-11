package com.example.projectsafemoto;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;

public class FakeSettingsActivity extends AppCompatActivity {

    private static final int AUTH_REQUEST_CODE = 100;
    private static final int ADMIN_REQUEST_CODE = 101;
    private SharedPreferences prefs;
    private DevicePolicyManager dpm;
    private ComponentName deviceAdminReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // PERSISTENCE CHECK: If already in repair mode, jump straight there.
        // This prevents judges from just hitting "back" to escape the demo.
        prefs = getSharedPreferences("MotoRepairPrefs", MODE_PRIVATE);
        // --- Setup for Device Admin ---
        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        deviceAdminReceiver = new ComponentName(this, MyDeviceAdminReceiver.class);
        // ------------------------------

        // Check persistence *first*
        if (prefs.getBoolean("isRepairModeActive", false)) {
            // Check if we are the active admin, if not, we can't start lock task
            if (dpm.isDeviceOwnerApp(getPackageName()) || dpm.isAdminActive(deviceAdminReceiver)) {
                startKioskActivity(true); // Start and LOCK
            } else {
                startKioskActivity(false); // Start but DON'T lock
            }
            finish();
            return;
        }

        setContentView(R.layout.activity_fake_settings);

        findViewById(R.id.btn_repair_mode).setOnClickListener(v -> {
            // 1. Check if we are an admin first
            if (!dpm.isAdminActive(deviceAdminReceiver)) {
                // If not, ask for permission
                promptToEnableAdmin();
            } else {
                // 2. If we are, proceed to auth
                initiateAuthentication();
            }
        });
    }

    private void promptToEnableAdmin() {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminReceiver);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Repair Mode needs Admin permission to lock the screen for the technician.");
        startActivityForResult(intent, ADMIN_REQUEST_CODE);
    }

    private void initiateAuthentication() {
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (km.isKeyguardSecure()) {
            Intent authIntent = km.createConfirmDeviceCredentialIntent("Enable Repair Mode", "Enter PIN to lock data.");
            startActivityForResult(authIntent, AUTH_REQUEST_CODE);
        } else {
            // Fallback for demo devices with no PIN set
            Toast.makeText(this, "Demo: Skipping Auth (No PIN set)", Toast.LENGTH_SHORT).show();
            activateRepairMode();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == ADMIN_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Admin permission granted!", Toast.LENGTH_SHORT).show();
                // Now proceed to authentication
                initiateAuthentication();
            } else {
                Toast.makeText(this, "Admin permission is required for Repair Mode", Toast.LENGTH_LONG).show();
            }
        }

        if (requestCode == AUTH_REQUEST_CODE && resultCode == RESULT_OK) {
            activateRepairMode();
        }
    }

    private void activateRepairMode() {
        prefs.edit().putBoolean("isRepairModeActive", true).apply();
        Toast.makeText(this, "Rebooting into Repair Mode...", Toast.LENGTH_LONG).show();

        new android.os.Handler().postDelayed(() -> {
            startKioskActivity(true); // Start and LOCK
            finish();
        }, 2000);
    }

    private void startKioskActivity(boolean lock) {
        Intent intent = new Intent(this, RepairKioskActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        // Pass a flag to tell the Kiosk to lock itself
        intent.putExtra("START_LOCK_TASK", lock);

        startActivity(intent);
    }
}