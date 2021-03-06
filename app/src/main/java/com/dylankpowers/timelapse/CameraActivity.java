package com.dylankpowers.timelapse;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;


public class CameraActivity extends Activity implements TextureView.SurfaceTextureListener, SensorEventListener {
    private static final String TAG = "TimeLapseActivity";

    private boolean mCameraReady = false;
    private Button mCaptureButton;
    private TimeLapseCaptureService mCaptureService;
    private boolean mCaptureServiceBound;
    private boolean mCurrentlyRecording = false;
    private boolean mOpenCameraWaitingOnServiceConnection = false;
    private boolean mPendingRecordingStop = false;
    private boolean mPendingRecordingStart = false;
    private TextureView mPreviewView;
    private int mPreviousRotation = Surface.ROTATION_0;

    private final ServiceConnection mCaptureServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mCaptureService = ((TimeLapseCaptureService.ServiceBinder) service).getService();
            if (mOpenCameraWaitingOnServiceConnection) {
                openCamera();
                mOpenCameraWaitingOnServiceConnection = false;
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            mCaptureService = null;
        }
    };

    private void bindTimeLapseCaptureService() {
        Intent intent = new Intent(this, TimeLapseCaptureService.class);
        bindService(intent, mCaptureServiceConnection, Context.BIND_AUTO_CREATE);
        mCaptureServiceBound = true;
    }

    private void closeCamera() {
        if (mCaptureService != null) {
            mCaptureService.closeCamera();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { /* Do nothing */ }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        bindTimeLapseCaptureService();

        setContentView(R.layout.activity_camera);
        mPreviewView = (TextureView) findViewById(R.id.camera_preview);
        mCaptureButton = (Button) findViewById(R.id.capture_button);
        mCaptureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recordButtonClicked();
            }
        });

        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindTimeLapseCaptureService();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        final int rotation = getWindowManager().getDefaultDisplay().getRotation();
        if (rotation != mPreviousRotation && !mCurrentlyRecording) {
            setSurfaceTransform(mPreviewView.getWidth(), mPreviewView.getHeight());
            mPreviousRotation = rotation;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permission granted!!!! :)");
            openCamera();
        } else {
            Log.d(TAG, "Permission not granted :(((( " + grantResults[0]);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!mPreviewView.isAvailable()) {
            mPreviewView.setSurfaceTextureListener(this);
        } else {
            setSurfaceTransform(mPreviewView.getWidth(), mPreviewView.getHeight());
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        texture.setDefaultBufferSize(1920, 1080);
        setSurfaceTransform(width, height);
        openCamera();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        setSurfaceTransform(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        closeCamera();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture texture) { }

    private void openCamera() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            openCameraM();
        } else {
            openCameraL();
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void openCameraM() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            openCameraL();
        } else {
            requestPermissions(new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
        }
    }

    private void openCameraL() {
        if (mCaptureService != null) {
            mCaptureService.openCamera(new Surface(mPreviewView.getSurfaceTexture()), new TimeLapseCapture.SimpleCallback() {
                @Override
                public void onEvent() {
                    mCameraReady = true;
                }
            });
        } else {
            mOpenCameraWaitingOnServiceConnection = true;
        }
    }

    private void setSurfaceTransform(int width, int height) {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, width, height);
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
            matrix.postScale(
                    width / (float) height,
                    (height / (float) width) * (width / 1920.0f),
                    centerX, centerY);
        } else {
            matrix.postRotate(90 * rotation, centerX, centerY);
            matrix.postScale(height / 1920.0f, 1.0f, centerX, centerY);
        }
        mPreviewView.setTransform(matrix);
    }

    private void recordButtonClicked() {
        if (mCameraReady) {
            if (mCurrentlyRecording && !mPendingRecordingStop) {
                mPendingRecordingStop = true;
                mCaptureService.stopRecording(new TimeLapseCapture.SimpleCallback() {
                    @Override
                    public void onEvent() {
                        mPendingRecordingStop = false;
                        mCurrentlyRecording = false;
                    }
                });
            } else if (!mCurrentlyRecording && !mPendingRecordingStart){
                mPendingRecordingStart = true;
                mCaptureService.startRecording(new TimeLapseCapture.SimpleCallback() {
                    @Override
                    public void onEvent() {
                        mPendingRecordingStart = false;
                        mCurrentlyRecording = true;
                    }
                });
            }
        }
    }

    private void unbindTimeLapseCaptureService() {
        if (mCaptureServiceBound) {
            // Detach our existing connection.
            unbindService(mCaptureServiceConnection);
            mCaptureServiceBound = false;
        }
    }
}
