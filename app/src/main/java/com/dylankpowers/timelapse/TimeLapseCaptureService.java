package com.dylankpowers.timelapse;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraManager;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;


public class TimeLapseCaptureService extends Service {
    private static final String TAG = "TimeLapseCaptureService";

    private HandlerThread mBackgroundThread;
    private final IBinder mBinder = new ServiceBinder();
    private TimeLapseCapture mCapture;

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + ": " + intent);
        return START_STICKY;
    }

    public class ServiceBinder extends Binder {
        TimeLapseCaptureService getService() {
            return TimeLapseCaptureService.this;
        }
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "Created");
        CameraManager cMan = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Display defaultDisplay = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        mBackgroundThread = new HandlerThread("TimeLapseCaptureBackground");
        mBackgroundThread.start();
        Handler backgroundHandler = new Handler(mBackgroundThread.getLooper());
        mCapture = new TimeLapseCapture(cMan, backgroundHandler,
                defaultDisplay, getContentResolver());
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Destroyed");
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void openCamera(final Surface previewSurface,
                           final TimeLapseCapture.SimpleCallback callback) {
        mCapture.open(previewSurface, callback);
    }

    public void closeCamera() {
        mCapture.close();
    }

    public void isRecording(TimeLapseCapture.IsRecordingCallback callback) {
        mCapture.isRecording(callback);
    }

    public void startRecording(TimeLapseCapture.SimpleCallback callback) {
        mCapture.startRecording(callback);
    }

    public void stopRecording(TimeLapseCapture.SimpleCallback callback) {
        mCapture.stopRecording(callback);
    }
}
