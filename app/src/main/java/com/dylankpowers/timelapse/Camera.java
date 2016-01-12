package com.dylankpowers.timelapse;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
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
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
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


  private final TextureView.SurfaceTextureListener
      mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
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
        matrix.postScale(width / (float) height, 1.0f, centerX, centerY);
      } else if (Surface.ROTATION_180 == rotation) {
          matrix.postRotate(180, centerX, centerY);
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

  private final CameraDevice.StateCallback mCameraStateCallback = new CameraDevice.StateCallback() {
    @Override
    public void onOpened(CameraDevice camera) {
      Log.d(TAG, "Camera Opened");
      try {
        final CameraDevice finalCamera = camera;
        final Surface surface = new Surface(mPreviewView.getSurfaceTexture());
        camera.createCaptureSession(Arrays.asList(surface),
            new CameraCaptureSession.StateCallback() {

              @Override
              public void onConfigured(CameraCaptureSession session) {
                Log.d(TAG, "configured");
                CaptureRequest.Builder previewRequestBuilder;
                try {
                  previewRequestBuilder = finalCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                } catch (CameraAccessException e) {
                  e.printStackTrace();
                  return;
                }

                previewRequestBuilder.addTarget(surface);
                try {
                  session.setRepeatingRequest(previewRequestBuilder.build(), mCaptureCallback, new Handler(getMainLooper()));
                } catch (CameraAccessException e) {
                  e.printStackTrace();
                }
              }

              @Override
              public void onConfigureFailed(CameraCaptureSession session) {
                Log.d(TAG, "Configure failed. We suck");
              }
            }, null);
      } catch (CameraAccessException e) {
        Log.e(TAG, "Thingys go boom!", e);
      }
    }

    @Override
    public void onDisconnected(CameraDevice camera) {
      Log.d(TAG, "Camera Disconnected");
    }

    @Override
    public void onError(CameraDevice camera, int error) {
      Log.d(TAG, "Camera Error: " + error);
    }
  };

  private TextureView mPreviewView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_camera);

    mPreviewView = (TextureView) findViewById(R.id.camera_preview);
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

  private void doCameraThangs() {
    CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

    String[] cameraIdList;
    try {
      cameraIdList = manager.getCameraIdList();
    } catch (CameraAccessException e) {
      Log.e(TAG, "", e);
      return;
    }

    for (String cameraId: cameraIdList) {
      Log.d(TAG, cameraId);

      CameraCharacteristics characteristics;
      try {
        characteristics = manager.getCameraCharacteristics(cameraId);
      } catch (CameraAccessException e) {
        Log.e(TAG, "", e);
        return;
      }

      int cameraDir = characteristics.get(CameraCharacteristics.LENS_FACING);
      final Handler handler = new Handler(this.getMainLooper());
      if (cameraDir == CameraMetadata.LENS_FACING_BACK) {
        Log.d(TAG, "Back camera");
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
          try {
            manager.openCamera(cameraId, mCameraStateCallback, handler);
          } catch (CameraAccessException e) {
            Log.e(TAG, "Errors n such", e);
          }
        } else {
          Log.d(TAG, "Camera permission denied :(");
          requestPermissions(new String[]{ Manifest.permission.CAMERA }, 0);
        }
      } else if (cameraDir == CameraMetadata.LENS_FACING_FRONT) {
        Log.d(TAG, "Front camera");
      }
    }
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

  private CameraCaptureSession.CaptureCallback mCaptureCallback
      = new CameraCaptureSession.CaptureCallback() {

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
}
