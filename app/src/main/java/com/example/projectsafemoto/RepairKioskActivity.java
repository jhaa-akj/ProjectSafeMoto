package com.example.projectsafemoto;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.admin.DevicePolicyManager;
import android.app.ActivityManager;
import android.os.Build;

public class RepairKioskActivity extends AppCompatActivity {

    private static final int EXIT_AUTH_REQUEST_CODE = 101;
    private TextView logView;
    private DevicePolicyManager dpm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_repair_kiosk);

        // --- Start Lock Task Mode ---
        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        boolean shouldLock = getIntent().getBooleanExtra("START_LOCK_TASK", false);

        if (shouldLock && !isInLockTaskMode()) {
            startLockTask();
        }
        // -----------------------------

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true /* enabled by default */) {
            @Override
            public void handleOnBackPressed() {
                // This code runs INSTEAD of the default back action
                Toast.makeText(RepairKioskActivity.this, "Access Denied: Restricted Mode Active", Toast.LENGTH_SHORT).show();
                // We do NOT call remove() or disable(), so it stays blocked.
            }
        });

        logView = findViewById(R.id.tv_logs);

        // FAKE DIAGNOSTIC BUTTON
        findViewById(R.id.btn_run_diagnostics).setOnClickListener(v -> {
            logView.setText("> Running CPU Stress Test...\n> CPU OK.\n> Checking Storage Integrity...\n> Storage OK (User partition encrypted).\n> DIAGNOSTICS COMPLETE.");
        });

        // EXIT BUTTON
        findViewById(R.id.btn_exit_repair).setOnClickListener(v -> {
            // Require authentication to exit as well!
            initiateExitAuthentication();
        });
    }

    private boolean isInLockTaskMode() {
        // We need ActivityManager to check the state
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            return false;
        }

        // The getLockTaskModeState() method was added in API 23 (Android 6.0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                // Check the state using ActivityManager
                return am.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE;
            } catch (Exception e) {
                e.printStackTrace();
                // If something goes wrong, assume we're not in lock mode
                return false;
            }
        }

        // For APIs 21 & 22, there is no reliable public method to check.
        // Returning 'false' is a safe fallback for the demo.
        return false;
    }

    private void initiateExitAuthentication() {
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (km.isKeyguardSecure()) {
            Intent authIntent = km.createConfirmDeviceCredentialIntent("Exit Repair Mode", "Verify owner identity to restore data.");
            startActivityForResult(authIntent, EXIT_AUTH_REQUEST_CODE);
        } else {
            exitRepairMode();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EXIT_AUTH_REQUEST_CODE && resultCode == RESULT_OK) {
            exitRepairMode();
        }
    }

    private void exitRepairMode() {
        // --- Stop Lock Task Mode FIRST ---
        if (isInLockTaskMode()) {
            stopLockTask();
        }
        // ---------------------------------

        SharedPreferences prefs = getSharedPreferences("MotoRepairPrefs", MODE_PRIVATE);
        prefs.edit().putBoolean("isRepairModeActive", false).apply();

        Toast.makeText(this, "Restoring user data...", Toast.LENGTH_LONG).show();

        Intent intent = new Intent(this, FakeSettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

}
