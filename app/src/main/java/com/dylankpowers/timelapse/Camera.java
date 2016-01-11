package com.dylankpowers.timelapse;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
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
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;

import java.util.Arrays;
import java.util.List;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class Camera extends AppCompatActivity {
  private static final String TAG = "Time Lapse";

  /**
   * Whether or not the system UI should be auto-hidden after
   * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
   */
  private static final boolean AUTO_HIDE = true;

  /**
   * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
   * user interaction before hiding the system UI.
   */
  private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

  /**
   * Some older devices needs a small delay between UI widget updates
   * and a change of the status and navigation bar.
   */
  private static final int UI_ANIMATION_DELAY = 300;
  private final Handler mHideHandler = new Handler();
//  private View mContentView;
  private final Runnable mHidePart2Runnable = new Runnable() {
    @SuppressLint("InlinedApi")
    @Override
    public void run() {
      // Delayed removal of status and navigation bar

      // Note that some of these constants are new as of API 16 (Jelly Bean)
      // and API 19 (KitKat). It is safe to use them, as they are inlined
      // at compile-time and do nothing on earlier devices.
      mTextureView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
          | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
  };
  private View mControlsView;
  private final Runnable mShowPart2Runnable = new Runnable() {
    @Override
    public void run() {
      // Delayed display of UI elements
      ActionBar actionBar = getSupportActionBar();
      if (actionBar != null) {
        actionBar.show();
      }
      mControlsView.setVisibility(View.VISIBLE);
    }
  };
  private boolean mVisible;
  private final Runnable mHideRunnable = new Runnable() {
    @Override
    public void run() {
      hide();
    }
  };
  /**
   * Touch listener to use for in-layout UI controls to delay hiding the
   * system UI. This is to prevent the jarring behavior of controls going away
   * while interacting with activity UI.
   */
  private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
      if (AUTO_HIDE) {
        delayedHide(AUTO_HIDE_DELAY_MILLIS);
      }
      return false;
    }
  };

  private CameraPreviewSurfaceListener mSurfaceTextureListener = new CameraPreviewSurfaceListener();
  private TextureView mTextureView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_camera);

    mVisible = true;
    mControlsView = findViewById(R.id.fullscreen_content_controls);
    mTextureView = (TextureView) findViewById(R.id.camera_preview);


    // Set up the user interaction to manually show or hide the system UI.
    mTextureView.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        toggle();
      }
    });


    // Upon interacting with UI controls, delay any scheduled hide()
    // operations to prevent the jarring behavior of controls going away
    // while interacting with the UI.
    findViewById(R.id.dummy_button).setOnTouchListener(mDelayHideTouchListener);

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
            final AppCompatActivity that = this;
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
              @Override
              public void onOpened(CameraDevice camera) {
                Log.d(TAG, "Camera Opened");
                try {
                  final CameraDevice finalCamera = camera;
                  final Surface surface = new Surface(mTextureView.getSurfaceTexture());
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
                            session.setRepeatingRequest(previewRequestBuilder.build(), mCaptureCallback, handler);
                          } catch (CameraAccessException e) {
                            e.printStackTrace();
                          }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                          Log.d(TAG, "Configure failed. We suck dicks");
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
            }, handler);
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

    // Trigger the initial hide() shortly after the activity has been
    // created, to briefly hint to the user that UI controls
    // are available.
    delayedHide(100);
  }

  @Override
  protected void onResume() {
    super.onResume();

    if (mTextureView.isAvailable()) {
      doCameraThangs();
    } else {
      mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
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

  private void toggle() {
    if (mVisible) {
      hide();
    } else {
      show();
    }
  }

  private void hide() {
    // Hide UI first
    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.hide();
    }
    mControlsView.setVisibility(View.GONE);
    mVisible = false;

    // Schedule a runnable to remove the status and navigation bar after a delay
    mHideHandler.removeCallbacks(mShowPart2Runnable);
    mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
  }

  @SuppressLint("InlinedApi")
  private void show() {
    // Show the system bar
    mTextureView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    mVisible = true;

    // Schedule a runnable to display UI elements after a delay
    mHideHandler.removeCallbacks(mHidePart2Runnable);
    mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
  }

  /**
   * Schedules a call to hide() in [delay] milliseconds, canceling any
   * previously scheduled calls.
   */
  private void delayedHide(int delayMillis) {
    mHideHandler.removeCallbacks(mHideRunnable);
    mHideHandler.postDelayed(mHideRunnable, delayMillis);
  }

  class CameraPreviewSurfaceListener implements TextureView.SurfaceTextureListener {

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
      doCameraThangs();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
      return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture texture) {
    }

  };

  private CameraCaptureSession.CaptureCallback mCaptureCallback
      = new CameraCaptureSession.CaptureCallback() {

    private void process(CaptureResult result) {
      Log.d(TAG, "processing....");
    }

    @Override
    public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                    @NonNull CaptureRequest request,
                                    @NonNull CaptureResult partialResult) {
      process(partialResult);
    }

    @Override
    public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                   @NonNull CaptureRequest request,
                                   @NonNull TotalCaptureResult result) {
      process(result);
    }

  };
}
