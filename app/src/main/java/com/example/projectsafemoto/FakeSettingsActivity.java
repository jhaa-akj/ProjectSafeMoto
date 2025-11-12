package com.example.projectsafemoto;

import android.Manifest;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.Locale;

public class FakeSettingsActivity extends AppCompatActivity {

    private static final int AUTH_REQUEST_CODE = 100;
    private static final int ADMIN_REQUEST_CODE = 101; // For Device Admin

    private SharedPreferences prefs;
    private DevicePolicyManager dpm;
    private ComponentName deviceAdminReceiver;

    private ActivityResultLauncher<String> requestAudioPermissionLauncher;
    private ActivityResultLauncher<Intent> speechRecognizerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fake_settings);

        // --- Setup the Toolbar ---
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // -------------------------

        // --- Setup Persistence & Device Admin ---
        prefs = getSharedPreferences("MotoRepairPrefs", MODE_PRIVATE);
        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        deviceAdminReceiver = new ComponentName(this, MyDeviceAdminReceiver.class);
        // --------------------------------------

        // PERSISTENCE CHECK: If already in repair mode, jump straight there.
        if (prefs.getBoolean("isRepairModeActive", false)) {
            if (dpm.isDeviceOwnerApp(getPackageName()) || dpm.isAdminActive(deviceAdminReceiver)) {
                startKioskActivity(true); // Start and LOCK
            } else {
                startKioskActivity(false); // Start but DON'T lock
            }
            finish();
            return;
        }

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
                        if (matches != null && !matches.isEmpty()) {
                            String spokenText = matches.get(0);
                            Toast.makeText(this, "Command received: '" + spokenText + "'", Toast.LENGTH_SHORT).show();
                            // Bypass PIN for voice
                            activateRepairMode();
                        } else {
                            Toast.makeText(this, "No speech detected.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });


        // --- REAL BUTTON: Repair Mode ---
        findViewById(R.id.btn_repair_mode).setOnClickListener(v -> {
            if (!dpm.isAdminActive(deviceAdminReceiver)) {
                promptToEnableAdmin();
            } else {
                initiateAuthentication();
            }
        });

        // --- FAKE BUTTON LISTENERS ---
        // We create a dummy listener to show a Toast.
        View.OnClickListener fakeFeatureListener = v -> {
            Toast.makeText(this, "This feature is for demo purposes only.", Toast.LENGTH_SHORT).show();
        };

        // Attach listener to all fake items
        findViewById(R.id.fake_screen_lock).setOnClickListener(fakeFeatureListener);
        findViewById(R.id.fake_fingerprint).setOnClickListener(fakeFeatureListener);
        findViewById(R.id.fake_security_update).setOnClickListener(fakeFeatureListener);
        findViewById(R.id.fake_permission).setOnClickListener(fakeFeatureListener);
        findViewById(R.id.fake_dashboard).setOnClickListener(fakeFeatureListener);

        // --- NEW FAKE ITEMS ---
        findViewById(R.id.fake_find_device).setOnClickListener(fakeFeatureListener);
        findViewById(R.id.fake_app_lock).setOnClickListener(fakeFeatureListener);
        findViewById(R.id.fake_sim_lock).setOnClickListener(fakeFeatureListener);
        findViewById(R.id.fake_encryption).setOnClickListener(fakeFeatureListener);
        findViewById(R.id.fake_alerts).setOnClickListener(fakeFeatureListener);
        findViewById(R.id.fake_app_pinning).setOnClickListener(fakeFeatureListener);
        // ---------------------------
    }

    // --- Toolbar Menu Methods ---
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.settings_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_voice) {
            checkAudioPermissionAndLaunch();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // --- Volume Key Trigger ---
    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
            Toast.makeText(this, "Volume Up pressed... launching voice command!", Toast.LENGTH_SHORT).show();
            checkAudioPermissionAndLaunch();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // --- All other helper methods (unchanged) ---

    private void checkAudioPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            launchSpeechRecognizer();
        } else {
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
            startKioskActivity(true);
            finish();
        }, 2000);
    }

    private void startKioskActivity(boolean lock) {
        Intent intent = new Intent(this, RepairKioskActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra("START_LOCK_TASK", lock);
        startActivity(intent);
    }
}