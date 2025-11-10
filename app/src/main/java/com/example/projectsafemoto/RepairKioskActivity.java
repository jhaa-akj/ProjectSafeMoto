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

public class RepairKioskActivity extends AppCompatActivity {

    private static final int EXIT_AUTH_REQUEST_CODE = 101;
    private TextView logView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_repair_kiosk);

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
        // 1. Clear the persistence flag
        SharedPreferences prefs = getSharedPreferences("MotoRepairPrefs", MODE_PRIVATE);
        prefs.edit().putBoolean("isRepairModeActive", false).apply();

        Toast.makeText(this, "Restoring user data...", Toast.LENGTH_LONG).show();

        // 2. Go back to the "Normal" settings view
        Intent intent = new Intent(this, FakeSettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

}
