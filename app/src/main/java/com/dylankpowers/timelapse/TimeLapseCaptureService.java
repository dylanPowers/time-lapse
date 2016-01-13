package com.dylankpowers.timelapse;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.view.Surface;


public class TimeLapseCaptureService extends Service {
    private static final String TAG = "TimeLapseCaptureService";

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
        CameraManager cMan = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        mCapture = new TimeLapseCapture(cMan);
    }

    public void openCamera(Surface previewSurface) {
        mCapture.open(previewSurface);
    }

    public void closeCamera() {
        mCapture.close();
    }
}