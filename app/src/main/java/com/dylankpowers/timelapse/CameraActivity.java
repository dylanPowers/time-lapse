package com.dylankpowers.timelapse;

import android.Manifest;
import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
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
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;


public class CameraActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener, SensorEventListener {
    private static final String TAG = "TimeLapseActivity";

    private boolean mCameraReady = false;
    private View mPreviewOverlay;
    private RecordingButton mStartRecordingButton;
    private Animation mRecordingButtonInAnim;
    private Animation mRecordingButtonOutAnim;
    private Animation mPreviewOverlayFadeOut;
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
        mPreviewOverlay = findViewById(R.id.camera_preview_overlay);
        mStartRecordingButton = (RecordingButton) findViewById(R.id.start_recording_button);
        mStartRecordingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recordButtonClicked();
            }
        });

        mRecordingButtonInAnim = AnimationUtils.loadAnimation(this, R.anim.recording_button_in);
        mRecordingButtonOutAnim = AnimationUtils.loadAnimation(this, R.anim.recording_button_out);
        mPreviewOverlayFadeOut = AnimationUtils.loadAnimation(this, R.anim.preview_overlay_fade_out);
        mPreviewOverlayFadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mPreviewOverlay.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

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
            if (!mCurrentlyRecording && !mPendingRecordingStart){
                Log.d(TAG, "Starting recording");
                mPendingRecordingStart = true;
                mRecordingButtonOutAnim.setAnimationListener(new Animation.AnimationListener() {

                    @Override
                    public void onAnimationStart(Animation animation) {}

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        mStartRecordingButton.setRecording(true);
                        mStartRecordingButton.startAnimation(mRecordingButtonInAnim);
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {}
                });
                circularReveal();
                mStartRecordingButton.startAnimation(mRecordingButtonOutAnim);
                mCaptureService.startRecording(new TimeLapseCapture.SimpleCallback() {
                    @Override
                    public void onEvent() {
//                new Handler(getMainLooper()).post(new Runnable() {
//                    @Override
//                    public void run() {
                        mPendingRecordingStart = false;
                        mCurrentlyRecording = true;
                    }
                });
            } else if (mCurrentlyRecording && !mPendingRecordingStop) {
                mPendingRecordingStop = true;
                Log.d(TAG, "Stopping recording");

                mRecordingButtonOutAnim.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) { }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        mStartRecordingButton.setRecording(false);
                        mStartRecordingButton.startAnimation(mRecordingButtonInAnim);
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }
                });
                circularReveal();
                mStartRecordingButton.startAnimation(mRecordingButtonOutAnim);
//                new Handler(getMainLooper()).post(new Runnable() {
//                    @Override
//                    public void run() {
                mCaptureService.stopRecording(new TimeLapseCapture.SimpleCallback() {
                    @Override
                    public void onEvent() {
                        mPendingRecordingStop = false;
                        mCurrentlyRecording = false;
                    }
                });
            }
        }
    }

    private void circularReveal() {
        int x = mStartRecordingButton.getLeft() + mStartRecordingButton.getWidth() / 2;
        int y = mStartRecordingButton.getTop() + mStartRecordingButton.getHeight() / 2;

        // get the final radius for the clipping circle
        float finalRadius = (float) Math.hypot(x, mPreviewOverlay.getHeight() / 2);

        // create the animator for this view (the start radius is zero)
        Animator anim =
            ViewAnimationUtils.createCircularReveal(mPreviewOverlay, x, y, 0, finalRadius);
        anim.setDuration(200);

        // make the view visible and start the animation
        mPreviewOverlay.setVisibility(View.VISIBLE);
        anim.start();
        anim.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) { }

            @Override
            public void onAnimationEnd(Animator animation) {
                mPreviewOverlay.startAnimation(mPreviewOverlayFadeOut);
            }

            @Override
            public void onAnimationCancel(Animator animation) { }

            @Override
            public void onAnimationRepeat(Animator animation) { }
        });
    }

    private void unbindTimeLapseCaptureService() {
        if (mCaptureServiceBound) {
            // Detach our existing connection.
            unbindService(mCaptureServiceConnection);
            mCaptureServiceBound = false;
        }
    }
}
