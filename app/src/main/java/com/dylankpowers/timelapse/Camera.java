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
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import java.io.IOException;
import java.util.Arrays;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class Camera extends AppCompatActivity {
    private static final String TAG = "Time Lapse";

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private CameraDevice mCamera;
    private CameraManager mCameraManager;
    private CameraCaptureSession mPreviewCaptureSession;
    private Surface mPreviewSurface;
    private TextureView mPreviewView;
    private MediaRecorder mVideo;

    private final CameraDevice.StateCallback
            mCameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "Camera Opened");

            mCamera = camera;

            mVideo = new MediaRecorder();
            mVideo.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mVideo.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mVideo.setCaptureRate(15);
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            int orientation = ORIENTATIONS.get(rotation);
            mVideo.setOrientationHint(orientation);
            mVideo.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mVideo.setVideoEncodingBitRate(70000000);
            mVideo.setVideoFrameRate(60);
            mVideo.setVideoSize(3840, 2160);
            String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath();
            String filePath = path + "/time-lapse.mp4";
            mVideo.setOutputFile(filePath);
            try {
                mVideo.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                camera.createCaptureSession(
                        Arrays.asList(mPreviewSurface, mVideo.getSurface()),
                        mCaptureSessionStateCallback, null );
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
                                        @NonNull CaptureResult partialResult) { }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) { }
    };

    private final CameraCaptureSession.StateCallback
            mCaptureSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            Log.d(TAG, "configured");
            mPreviewCaptureSession = session;

            CaptureRequest.Builder previewRequestBuilder;
            try {
                previewRequestBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            } catch (CameraAccessException e) {
                e.printStackTrace();
                return;
            }

            previewRequestBuilder.addTarget(mPreviewSurface);
            previewRequestBuilder.addTarget(mVideo.getSurface());
            try {
                session.setRepeatingRequest(previewRequestBuilder.build(), mCaptureCallback, new Handler(getMainLooper()));
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

            mVideo.start();   // Recording is now started
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

            openCamera();
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
            closeCamera();
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) { }
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

        if (!mPreviewView.isAvailable()) {
            mPreviewView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "pause");
        super.onPause();
    }

    private void closeCamera() {
        Log.d(TAG, "Closing camera");
        if (mVideo != null) {
            mVideo.stop();
        }


        if (mPreviewCaptureSession != null) {
            mPreviewCaptureSession.close();
        }

        if (mCamera != null) {
            mCamera.close();
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
            openCamera();
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

    private void openCamera() {
        String rearCameraId = findRearCameraId();

        Handler handler = new Handler(getMainLooper());
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            try {
                mCameraManager.openCamera(rearCameraId, mCameraStateCallback, handler);
            } catch (CameraAccessException e) {
                Log.e(TAG, "Errors n such", e);
            }
        } else {
            Log.d(TAG, "Camera permission denied :(");
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
        }
    }
}
