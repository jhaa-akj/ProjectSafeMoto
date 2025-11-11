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

import android.Manifest; // Add this
import android.content.ActivityNotFoundException;
import android.speech.RecognizerIntent; // Add this
import androidx.activity.result.ActivityResultLauncher; // Add this
import androidx.activity.result.contract.ActivityResultContracts; // Add this
import androidx.core.content.ContextCompat;
import android.content.pm.PackageManager;
import java.util.ArrayList;
import java.util.Locale;

public class FakeSettingsActivity extends AppCompatActivity {

    private static final int AUTH_REQUEST_CODE = 100;
    private static final int ADMIN_REQUEST_CODE = 101;
    private SharedPreferences prefs;
    private DevicePolicyManager dpm;
    private ComponentName deviceAdminReceiver;

    private ActivityResultLauncher<String> requestAudioPermissionLauncher;
    private ActivityResultLauncher<Intent> speechRecognizerLauncher;

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

        // --- Setup Permission Launcher ---
        requestAudioPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        launchSpeechRecognizer();
                    } else {
                        Toast.makeText(this, "Audio permission is required for voice commands", Toast.LENGTH_SHORT).show();
                    }
                });

        // --- Setup Speech Recognizer Launcher ---
        speechRecognizerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        ArrayList<String> matches = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

                        // DEMO MODE: If any speech is detected, trigger the mode.
                        if (matches != null && !matches.isEmpty()) {
                            String spokenText = matches.get(0); // Get what they said

                            // Show the judge what was heard (this is good for the demo)
                            Toast.makeText(this, "Command received: '" + spokenText + "'", Toast.LENGTH_SHORT).show();

                            // Immediately start the authentication flow
                            initiateAuthentication();

                        } else {
                            // This would happen if they open the mic but say nothing
                            Toast.makeText(this, "No speech detected.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

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

        findViewById(R.id.btn_voice_command).setOnClickListener(v -> {
            checkAudioPermissionAndLaunch();
        });
    }

    private void checkAudioPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            launchSpeechRecognizer();
        } else {
            // Request permission
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void launchSpeechRecognizer() {
        Intent speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say 'Enter Repair Mode'");
        try {
            speechRecognizerLauncher.launch(speechIntent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Speech recognition not supported on this device", Toast.LENGTH_SHORT).show();
        }
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

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        // Check if the key pressed is Volume Up
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {

            // Show a Toast to prove it worked
            Toast.makeText(this, "Volume Up pressed... launching voice command!", Toast.LENGTH_SHORT).show();

            // Call the same method the mic button calls
            checkAudioPermissionAndLaunch();

            // 'return true' means we "consumed" this key press.
            // The system volume will NOT change.
            return true;
        }

        // For any other key, do the default action
        return super.onKeyDown(keyCode, event);
    }
}