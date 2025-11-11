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

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

public class RepairKioskActivity extends AppCompatActivity {

    private static final int EXIT_AUTH_REQUEST_CODE = 101;
    private TextView logView;
    private DevicePolicyManager dpm;
    private TextView tvChargingStatus;
    private MediaPlayer mediaPlayer;

    // --- For Intruder Selfie ---
    private IntruderSelfieCapturer selfieCapturer;
    private ActivityResultLauncher<String> requestCameraLauncher;

    // --- For Charging Port Test ---
    private ChargingReceiver chargingReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_repair_kiosk);

        // --- Setup Camera Permission Launcher ---
        requestCameraLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        // Permission granted, initialize the camera
                        selfieCapturer = new IntruderSelfieCapturer(this);
                    } else {
                        Toast.makeText(this, "Camera permission needed for intruder selfies", Toast.LENGTH_LONG).show();
                    }
                });

        // --- Check Camera Permission on Start ---
        checkCameraPermissionAndInit();

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
        tvChargingStatus = findViewById(R.id.tv_charging_status);
        // --- Initialize Charging Receiver ---
        chargingReceiver = new ChargingReceiver();

        // FAKE DIAGNOSTIC BUTTON
        findViewById(R.id.btn_run_diagnostics).setOnClickListener(v -> {
            logView.setText("> Running CPU Stress Test...\n> CPU OK.\n> Checking Storage Integrity...\n> Storage OK (User partition encrypted).\n> DIAGNOSTICS COMPLETE.");
        });

        // SIMULATED Camera Test (Disables Gallery)
        findViewById(R.id.btn_test_camera).setOnClickListener(v -> {
            // We just show a Toast to simulate this restricted access
            Toast.makeText(this, "Camera opened in diagnostic mode (Gallery access disabled)", Toast.LENGTH_LONG).show();
            logView.setText("> Camera hardware test requested...\n> Camera OK.");
        });

        // NEW: Speaker Test
        findViewById(R.id.btn_test_speaker).setOnClickListener(v -> {
            testSpeaker();
        });

        // NEW: Report App Issue
        findViewById(R.id.btn_report_issue).setOnClickListener(v -> {
            showAppIssueDialog();
        });

        // EXIT BUTTON
        findViewById(R.id.btn_exit_repair).setOnClickListener(v -> {
            // Require authentication to exit as well!
            initiateExitAuthentication();
        });
    }

    private void checkCameraPermissionAndInit() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            selfieCapturer = new IntruderSelfieCapturer(this);
        } else {
            requestCameraLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void testSpeaker() {
        // Stop any previous playback
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        // Create and start playback (replace R.raw.beep with your sound file)
        mediaPlayer = MediaPlayer.create(this, R.raw.beep);
        if (mediaPlayer == null) {
            logView.setText("> Speaker test failed: Sound file not found (add to res/raw/beep.mp3)");
            return;
        }
        mediaPlayer.setOnCompletionListener(mp -> {
            logView.setText("> Speaker test complete.");
            mp.release();
            mediaPlayer = null;
        });
        logView.setText("> Playing test sound...");
        mediaPlayer.start();
    }

    private void showAppIssueDialog() {
        // These are fake apps for the demo
        final String[] apps = {"Phone", "Messages", "Camera", "Browser"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Which app is not working?")
                .setItems(apps, (dialog, which) -> {
                    String selectedApp = apps[which];
                    logView.setText("> Technician logged issue with: " + selectedApp);
                    Toast.makeText(this, "Logged issue with: " + selectedApp, Toast.LENGTH_SHORT).show();
                });
        builder.create().show();
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
        if (requestCode == EXIT_AUTH_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // User passed auth, exit mode
                exitRepairMode();
            } else {
                // *** AUTHENTICATION FAILED! ***
                Toast.makeText(this, "Authentication Failed!", Toast.LENGTH_SHORT).show();
                if (selfieCapturer != null) {
                    selfieCapturer.takePhoto();
                }
            }
        }
    }

    public class ChargingReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            if (action.equals(Intent.ACTION_POWER_CONNECTED)) {
                tvChargingStatus.setText("Charging Port: CONNECTED");
                tvChargingStatus.setTextColor(0xFF00FF00); // Green
                logView.setText("> Charging port test: PASSED (Connected)");
            } else if (action.equals(Intent.ACTION_POWER_DISCONNECTED)) {
                tvChargingStatus.setText("Charging Port: DISCONNECTED");
                tvChargingStatus.setTextColor(0xFFFF0000); // Red
            }
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

    // --- Activity Lifecycle for Charging Receiver ---

    @Override
    protected void onResume() {
        super.onResume();
        // Register the receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(chargingReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister to save battery
        unregisterReceiver(chargingReceiver);

        // Release media player
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

}
