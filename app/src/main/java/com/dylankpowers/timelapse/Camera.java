package com.dylankpowers.timelapse;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import java.util.Arrays;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class Camera extends AppCompatActivity {
    private static final String TAG = "Time Lapse";

    private CameraDevice mCamera;
    private CameraManager mCameraManager;
    private Surface mPreviewSurface;
    private TextureView mPreviewView;

    private final CameraDevice.StateCallback
            mCameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "Camera Opened");

            mCamera = camera;
            try {


                camera.createCaptureSession(Arrays.asList(mPreviewSurface), mCaptureSessionStateCallback, null);
            } catch (CameraAccessException e) {
                Log.e(TAG, "Thingys go boom!", e);
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.d(TAG, "Camera Disconnected");
            camera.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.d(TAG, "Camera Error: " + error);
            camera.close();
        }
    };


    private CameraCaptureSession.CaptureCallback
            mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
        }
    };

    private final CameraCaptureSession.StateCallback
            mCaptureSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            Log.d(TAG, "configured");
            CaptureRequest.Builder previewRequestBuilder;
            try {
                previewRequestBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            } catch (CameraAccessException e) {
                e.printStackTrace();
                return;
            }

            previewRequestBuilder.addTarget(mPreviewSurface);
            try {
                session.setRepeatingRequest(previewRequestBuilder.build(), mCaptureCallback, new Handler(getMainLooper()));
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Log.d(TAG, "Configure failed. We suck");
        }
    };

    private final TextureView.SurfaceTextureListener
            mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            texture.setDefaultBufferSize(1920, 1080);
            mPreviewSurface = new Surface(texture);

            doCameraThangs();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            Matrix matrix = new Matrix();
            RectF viewRect = new RectF(0, 0, width, height);
            float centerX = viewRect.centerX();
            float centerY = viewRect.centerY();
            if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
                matrix.postRotate(90 * (rotation - 2), centerX, centerY);
                matrix.postScale(width / (float) height, (height / (float) width) * (width / 1920.0f), centerX, centerY);
            } else {
                matrix.postRotate(90 * rotation, centerX, centerY);
                matrix.postScale(height / 1920.0f, 1.0f, centerX, centerY);
            }
            mPreviewView.setTransform(matrix);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_camera);

        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        mPreviewView = (TextureView) findViewById(R.id.camera_preview);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mPreviewView.isAvailable()) {
            doCameraThangs();
        } else {
            mPreviewView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    private void doCameraThangs() {
        String rearCameraId = findRearCameraId();

        Handler handler = new Handler(getMainLooper());
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            try {
                mCameraManager.openCamera(rearCameraId, mCameraStateCallback, handler);
            } catch (CameraAccessException e) {
                Log.e(TAG, "Errors n such", e);
            }
        } else {
            Log.d(TAG, "Camera permission denied :(");
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 0);
        }

        CameraCharacteristics cameraCharacteristics;
        try {
            cameraCharacteristics = mCameraManager.getCameraCharacteristics(rearCameraId);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }

        StreamConfigurationMap configs = cameraCharacteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        int[] supportedOutputFormats = configs.getOutputFormats();
        for (int format : supportedOutputFormats) {
            Log.d(TAG, "Supported format: " + format);
            Size[] sizes = configs.getOutputSizes(format);
            for (Size size : sizes) {
                Log.d(TAG, size.toString() + "\t -- Stall " + configs.getOutputStallDuration(format, size));
            }
        }
    }

    private String findRearCameraId() {
        String[] cameraIdList;
        try {
            cameraIdList = mCameraManager.getCameraIdList();
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }

        for (String cameraId : cameraIdList) {
            CameraCharacteristics cameraCharacteristics;
            try {
                cameraCharacteristics = mCameraManager.getCameraCharacteristics(cameraId);
            } catch (CameraAccessException e) {
                throw new RuntimeException(e);
            }

            int cameraDir = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
            if (cameraDir == CameraMetadata.LENS_FACING_BACK) {
                return cameraId;
            }
        }

        throw new RuntimeException("The rear camera was not found");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permission granted!!!! :)))");
            doCameraThangs();
        } else {
            Log.d(TAG, "Permission not granted :(((( " + grantResults[0]);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            mPreviewView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }
}
