package com.example.projectsafemoto;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.Executor;
import androidx.camera.core.Preview;
import androidx.camera.view.PreviewView;
import androidx.annotation.Nullable;

public class IntruderSelfieCapturer {

    private static final String TAG = "SelfieCapturer";
    private ImageCapture imageCapture;
    private Context context;
    private PreviewView previewView;

    public IntruderSelfieCapturer(Context context, @Nullable PreviewView previewView) {
        this.context = context;
        this.previewView = previewView; // Store the PreviewView
        startCamera((AppCompatActivity) context);
    }

    private void startCamera(AppCompatActivity activity) {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);
        Executor cameraExecutor = ContextCompat.getMainExecutor(context);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Build ImageCapture (unchanged)
                imageCapture = new ImageCapture.Builder().build();

                // Select front camera (unchanged)
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build();

                // Unbind all (unchanged)
                cameraProvider.unbindAll();

                // --- NEW: Bind Preview if it exists ---
                if (previewView != null) {
                    // Build the Preview use case
                    Preview preview = new Preview.Builder().build();
                    preview.setSurfaceProvider(previewView.getSurfaceProvider());

                    // Bind all three: lifecycle, selector, preview, AND capture
                    cameraProvider.bindToLifecycle(activity, cameraSelector, preview, imageCapture);
                    Log.d(TAG, "CameraX bound with Preview and Capture.");
                } else {
                    // Original behavior (no preview)
                    cameraProvider.bindToLifecycle(activity, cameraSelector, imageCapture);
                    Log.d(TAG, "CameraX bound for selfie capture only.");
                }

            } catch (Exception e) {
                Log.e(TAG, "CameraX binding failed", e);
            }
        }, cameraExecutor);
    }

    public void takePhoto() {
        if (imageCapture == null) {
            Log.e(TAG, "ImageCapture is not ready.");
            return;
        }

        // Create time-stamped output file
        File photoFile = new File(context.getFilesDir(), // App-private storage
                new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                        .format(System.currentTimeMillis()) + "-intruder.jpg");

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        // Take the picture
        imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        String msg = "Intruder detected! Photo saved: " + photoFile.getName();
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
                        Log.d(TAG, msg);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Photo capture failed: " + exception.getMessage(), exception);
                    }
                }
        );
    }
}