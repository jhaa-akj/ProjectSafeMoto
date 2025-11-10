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

public class FakeSettingsActivity extends AppCompatActivity {

    private static final int AUTH_REQUEST_CODE = 100;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // PERSISTENCE CHECK: If already in repair mode, jump straight there.
        // This prevents judges from just hitting "back" to escape the demo.
        prefs = getSharedPreferences("MotoRepairPrefs", MODE_PRIVATE);
        if (prefs.getBoolean("isRepairModeActive", false)) {
            startKioskActivity();
            finish(); // Close this activity so they can't go back to it
            return;
        }

        setContentView(R.layout.activity_fake_settings);

        findViewById(R.id.btn_repair_mode).setOnClickListener(v -> {
            // 1. Trigger real Android authentication (PIN/Pattern/Biometric)
            initiateAuthentication();
        });
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
        if (requestCode == AUTH_REQUEST_CODE && resultCode == RESULT_OK) {
            // Auth success!
            activateRepairMode();
        }
    }

    private void activateRepairMode() {
        // Save state so if app restarts, we are still in repair mode
        prefs.edit().putBoolean("isRepairModeActive", true).apply();

        Toast.makeText(this, "Rebooting into Repair Mode...", Toast.LENGTH_LONG).show();

        // Small delay to simulate a system switch/reboot
        new android.os.Handler().postDelayed(() -> {
            startKioskActivity();
            finish();
        }, 2000);
    }

    private void startKioskActivity() {
        Intent intent = new Intent(this, RepairKioskActivity.class);
        // Clear the back stack so they can't go back to settings
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}