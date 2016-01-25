package com.dylankpowers.timelapse;

import android.Manifest;
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
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;


public class CameraActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener, SensorEventListener {
    private static final String TAG = "TimeLapseActivity";

    private boolean mCameraReady = false;
    private RecordingButton mStartRecordingButton;
//    private FloatingActionButton mStopRecordingButton;
    private Animation mRecordingButtonInAnim;
    private Animation mRecordingButtonOutAnim;
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
        mStartRecordingButton = (RecordingButton) findViewById(R.id.start_recording_button);
        mStartRecordingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recordButtonClicked();
            }
        });

//        mStopRecordingButton = (FloatingActionButton) findViewById(R.id.stop_recording_button);
//        mStopRecordingButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                recordButtonClicked();
//            }
//        });

        mRecordingButtonInAnim = AnimationUtils.loadAnimation(this, R.anim.recording_button_in);
        mRecordingButtonOutAnim = AnimationUtils.loadAnimation(this, R.anim.recording_button_out);


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
//                final Animation startRecordingAnim =

//                final Animation startAnim = AnimationUtils.loadAnimation(this, R.anim.start_recording);
//                mStartRecordingButton.setAnimation(startAnim);
//                mStartRecordingButton.setClipBounds(new Rect(-27, 27, 27, -27));

//                mStartRecordingButton.setVisibility(View.INVISIBLE);
//                long start = System.currentTimeMillis();
//                stopAnim.setZAdjustment(Animation.ZORDER_TOP);

//                long loadAnimationTime = System.currentTimeMillis();
//                mStopRecordingButton.setVisibility(View.VISIBLE);
//                mStopRecordingButton.requestFocus();
//                mStopRecordingButton.bringToFront();
//                mStopRecordingButton.getParent().requestLayout();
//                ((FrameLayout) mStopRecordingButton.getParent()).invalidate();
//
//                mStartRecordingButton.setVisibility(View.VISIBLE);
//                        mStartRecordingButton.setRecording(true);
                mRecordingButtonOutAnim.setAnimationListener(new Animation.AnimationListener() {

                    @Override
                    public void onAnimationStart(Animation animation) {
                        Log.d(TAG, "You know 1");
                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
//                        mStartRecordingButton.setVisibility(View.GONE);
//                        mStopRecordingButton.setElevation(1.0f);
                        Log.d(TAG, "Starting record animation stopped???");
                        mStartRecordingButton.setRecording(true);
//                        mStartRecordingButton.refreshDrawableState();
                        mStartRecordingButton.startAnimation(mRecordingButtonInAnim);
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                        Log.d(TAG, "REPEATING animation");
                    }
                });
                mStartRecordingButton.startAnimation(mRecordingButtonOutAnim);
//                mStopRecordingButton.setAnimation(mStartRecordingButtonAnim);
//                mStopRecordingButton.setElevation(0.0f);
//                mStartRecordingButton.invalidate();
//                mRecordingButtonOutAnim.startNow();
//                getWindow().setSharedElementsUseOverlay(false);
//                ObjectAnimator scaleXAnim = ObjectAnimator.ofFloat(mStopRecordingButton, "scaleX", 0.0f, 1.0f);
//                scaleXAnim.start();
//                long setEverythingTime = System.currentTimeMillis();
//                Log.d(TAG, "Load: " + (loadAnimationTime - start) +
//                        " - Setting: " + (setEverythingTime - start));
//                mCaptureService.startRecording(new TimeLapseCapture.SimpleCallback() {
//                    @Override
//                    public void onEvent() {
                new Handler(getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        mPendingRecordingStart = false;
                        mCurrentlyRecording = true;
                    }
                });
            } else if (mCurrentlyRecording && !mPendingRecordingStop) {
                mPendingRecordingStop = true;
                Log.d(TAG, "Stopping recording");
//                        mStartRecordingButton.setRecording(false);

//                final Animation stopAnim = AnimationUtils.loadAnimation(this, R.anim.start_recording_stop_button);
//                mStartRecordingButton.setVisibility(View.VISIBLE);
                mRecordingButtonOutAnim.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                        Log.d(TAG, "You know 2");
                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
//                        mStopRecordingButton.setVisibility(View.GONE);
                        Log.d(TAG, "Stopping recording animation stopped??");
                        mStartRecordingButton.setRecording(false);
                        mStartRecordingButton.startAnimation(mRecordingButtonInAnim);
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }
                });
//                mStartRecordingButton.setAnimation(mRecordingButtonOutAnim);
                mStartRecordingButton.startAnimation(mRecordingButtonOutAnim);
//                mRecordingButtonOutAnim.setRepeatMode(Animation.REVERSE);
//                mRecordingButtonOutAnim.setRepeatCount(1);
//                mStopRecordingButton.setAnimation(mStopRecordingButtonAnim);
//                mStopRecordingButtonAnim.startNow();
//                mStartRecordingButton.setAnimation(stopAnim);
                new Handler(getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
//                mCaptureService.stopRecording(new TimeLapseCapture.SimpleCallback() {
//                    @Override
//                    public void onEvent() {
                        mPendingRecordingStop = false;
                        mCurrentlyRecording = false;
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
