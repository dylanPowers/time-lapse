package com.dylankpowers.timelapse;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Display;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;

public class TimeLapseCapture {
    private static final String TAG = "TimeLapseCapture";
    private static final String STORAGE_DIR =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                .getAbsolutePath() + "/TimeLapse";
    private static final int VIDEO_FPS = 60;
    private static final double CAPTURE_RATE = VIDEO_FPS / 100.0;

    private Handler mBackgroundHandler;
    private CameraDevice mCamera;
    private CameraManager mCameraManager;
    private CameraCharacteristics mCameraCharacteristics;
    private SimpleCallback mCameraReadyCallback;
    private Handler mCameraReadyCallbackHandler;
    private CameraCaptureSession mCaptureSession;
    private boolean mCreatingCaptureSession = false;
    private ContentResolver mContentResolver;
    private boolean mCurrentlyRecording = false;
    private Display mDefaultDisplay;
    private Surface mPreviewSurface;
    private String mRecordingSessionFilepath;
    private CamcorderProfile mRecordingSessionProfile;
    private MediaRecorder mVideo;
    private SimpleCallback mVideoRecorderStarted;
    private Handler mVideoRecorderStartedHandler;
    private SimpleCallback mVideoRecorderStopped;
    private Handler mVideoRecorderStoppedHandler;


    public TimeLapseCapture(CameraManager cameraManager,
                            Handler backgroundHandler,
                            Display defaultDisplay, ContentResolver contentResolver) {
        mCameraManager = cameraManager;
        mBackgroundHandler = backgroundHandler;
        mDefaultDisplay = defaultDisplay;
        mContentResolver = contentResolver;
    }

    private final CameraDevice.StateCallback
            mCameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCamera = camera;
            final SimpleCallback callback = mCameraReadyCallback;
            mCameraReadyCallbackHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onEvent();
                }
            });
            mCameraReadyCallback = null;
            mCameraReadyCallbackHandler = null;

            createPreviewCaptureSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.d(TAG, "Camera Disconnected");
            camera.close();
            mCamera = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.d(TAG, "Camera Error: " + error);
            camera.close();
            mCamera = null;
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

    public synchronized void close() {
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mVideo != null) {
                    if (mCurrentlyRecording) {
                        stopRecordingSync();
                        mCurrentlyRecording = false;
                    }

                    mVideo.release();
                    mVideo = null;
                }

            }
        });

        // The capture session and camera should be closed synchronously
        mCamera.close();
        mCaptureSession = null;
        mCamera = null;
    }

    private synchronized void createRecordingCaptureSession() {
        if (mCreatingCaptureSession) {
            throw new RuntimeException("Capture session already being created");
        }

        if (mCamera != null) {
            setupVideoRecorder();
            try {
                mCamera.createCaptureSession(
                        Arrays.asList(mPreviewSurface, mVideo.getSurface()),
                        new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession session) {
                                onCaptureSessionConfigured(session, CameraDevice.TEMPLATE_RECORD);

                                mVideo.start();
                                mCurrentlyRecording = true;
                                Log.d(TAG, "Video recorder started.");
                                final SimpleCallback callback = mVideoRecorderStarted;
                                mVideoRecorderStartedHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        callback.onEvent();
                                    }
                                });
                                mVideoRecorderStarted = null;
                                mVideoRecorderStartedHandler = null;
                            }

                            @Override
                            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                                Log.d(TAG, "Camera capture session configure failed.");
                            }
                        }, null);
                mCreatingCaptureSession = true;
            } catch (CameraAccessException e) {
                throw new RuntimeException("Can't access the camera.", e);
            }
        }
    }

    private synchronized void createPreviewCaptureSession() {
        if (mCamera != null) {
            try {
                mCamera.createCaptureSession(
                        Arrays.asList(mPreviewSurface),
                        new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession session) {
                                onCaptureSessionConfigured(session, CameraDevice.TEMPLATE_PREVIEW);

                                if (mVideoRecorderStopped != null) {
                                    final SimpleCallback callback = mVideoRecorderStopped;
                                    mVideoRecorderStoppedHandler.post(new Runnable() {                                        @Override
                                        public void run() {
                                            callback.onEvent();
                                        }
                                    });
                                    mVideoRecorderStopped = null;
                                    mVideoRecorderStoppedHandler = null;
                                }
                            }

                            @Override
                            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                                Log.d(TAG, "Camera capture session configure failed.");
                            }
                        }, null);
                mCreatingCaptureSession = true;
            } catch (CameraAccessException e) {
                throw new RuntimeException("Can't access the camera.", e);
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

    private static String generateFilename() {
        String filename = "TimeLapse_";
        Calendar date = new GregorianCalendar();
        filename += date.get(Calendar.YEAR);
        filename += String.format("%02d", date.get(Calendar.MONTH) + 1);
        filename += String.format("%02d", date.get(Calendar.DAY_OF_MONTH));
        filename += "_";
        filename += String.format("%02d", date.get(Calendar.HOUR_OF_DAY));
        filename += String.format("%02d", date.get(Calendar.MINUTE));
        filename += String.format("%02d", date.get(Calendar.SECOND));
        filename += ".mp4";
        return filename;
    }

    private synchronized void onCaptureSessionConfigured(
            CameraCaptureSession session, int sessionTemplateType) {
        Log.d(TAG, "configured");
        mCaptureSession = session;
        CaptureRequest.Builder previewRequestBuilder;
        try {
            previewRequestBuilder = mCamera.createCaptureRequest(sessionTemplateType);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return;
        }

        previewRequestBuilder.addTarget(mPreviewSurface);
        if (sessionTemplateType == CameraDevice.TEMPLATE_RECORD) {
            previewRequestBuilder.addTarget(mVideo.getSurface());
        }

        try {
            session.setRepeatingRequest(
                    previewRequestBuilder.build(),
                    mCaptureCallback, mBackgroundHandler);
            Log.d(TAG, "Set repeating request");
        } catch (CameraAccessException e) {
            throw new RuntimeException("Can't access the camera", e);
        }
        mCreatingCaptureSession = false;
    }

    public void open(Surface previewSurface, SimpleCallback callback) {
        mPreviewSurface = previewSurface;
        mCameraReadyCallback = callback;
        mCameraReadyCallbackHandler = new Handler(Looper.myLooper());

        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                String rearCameraId = findRearCameraId();
                try {
                    mCameraCharacteristics = mCameraManager.getCameraCharacteristics(rearCameraId);
                    mCameraManager.openCamera(rearCameraId, mCameraStateCallback, mBackgroundHandler);
                } catch (CameraAccessException e) {
                    throw new RuntimeException("Unable to access the camera.", e);
                } catch (SecurityException e) {
                    throw new RuntimeException("Security exception opening the camera. " +
                            "This shouldn't have happened!", e);
                }
            }
        });
    }

    public void startRecording(SimpleCallback callback) {
        mVideoRecorderStarted = callback;
        mVideoRecorderStartedHandler = new Handler(Looper.myLooper());

        if (mCreatingCaptureSession) {
            throw new RuntimeException("Invalid recording state.");
        }

        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                createRecordingCaptureSession();
            }
        });
    }

    public void stopRecording(final SimpleCallback callback) {
        mVideoRecorderStopped = callback;
        mVideoRecorderStoppedHandler = new Handler(Looper.myLooper());

        if (mCreatingCaptureSession) {
            throw new RuntimeException("Invalid stopping state.");
        }

        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    mCaptureSession.stopRepeating();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }

                try {
                    stopRecordingSync();
                } catch (RuntimeException e) {
                    Log.d(TAG, "Nothing was recorded");
                }

                mCurrentlyRecording = false;
                Log.d(TAG, "Video recorder stopped.");

                createPreviewCaptureSession();
            }
        });
    }

    private void stopRecordingSync() {
        mVideo.stop();
        ContentValues values = new ContentValues(5);
        values.put(MediaStore.MediaColumns.HEIGHT, mRecordingSessionProfile.videoFrameHeight);
        values.put(MediaStore.MediaColumns.WIDTH, mRecordingSessionProfile.videoFrameWidth);
        values.put(MediaStore.Video.Media.RESOLUTION,
                mRecordingSessionProfile.videoFrameWidth + "x" + mRecordingSessionProfile.videoFrameHeight);
        values.put(MediaStore.MediaColumns.DATA, mRecordingSessionFilepath);
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");

        Uri mediaTable = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        Uri contentUri = mContentResolver.insert(mediaTable, values);
        if (contentUri == null) {
            String query = MediaStore.MediaColumns.DATA + " = '" + mRecordingSessionFilepath + "'";
            mContentResolver.update(mediaTable, values, query, null);
            Cursor result = mContentResolver.query(mediaTable, new String[]{"_id"}, query, null, null);
            result.moveToFirst();
            String id = result.getString(0);
            result.close();

            contentUri = mediaTable.buildUpon().appendPath(id).build();
        }

        mContentResolver.update(contentUri, values, null, null);
    }

    private void setupVideoRecorder() {
        mVideo = new MediaRecorder();
        mVideo.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mVideo.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mVideo.setCaptureRate(CAPTURE_RATE);

        int videoOrientation = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) - 90;
        int deviceRotation = mDefaultDisplay.getRotation();
        if (deviceRotation == Surface.ROTATION_270) {
            videoOrientation = (videoOrientation + 180) % 360;
        }

        mRecordingSessionProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_1080P);
        mVideo.setOrientationHint(videoOrientation);
        mVideo.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        double log2FrameRateRatio = Math.log10(VIDEO_FPS / mRecordingSessionProfile.videoFrameRate) / Math.log10(2);
        double bitrateChangeRatio = Math.pow(1.5, log2FrameRateRatio);
        mVideo.setVideoEncodingBitRate((int) (mRecordingSessionProfile.videoBitRate * bitrateChangeRatio));
        mVideo.setVideoFrameRate(VIDEO_FPS);
        mVideo.setVideoSize(mRecordingSessionProfile.videoFrameWidth, mRecordingSessionProfile.videoFrameHeight);

        mRecordingSessionFilepath = STORAGE_DIR + "/" + generateFilename();
        File storageDir = new File(STORAGE_DIR);
        if (!storageDir.exists()) {
            storageDir.mkdir();
        } else if (storageDir.isFile()) {
            storageDir.delete();
            storageDir.mkdir();
        }

        mVideo.setOutputFile(mRecordingSessionFilepath);
        try {
            mVideo.prepare();
        } catch (IOException e) {
            throw new RuntimeException("Unable to prepare the video recorder.", e);
        }
    }

    public interface SimpleCallback {
        void onEvent();
    }

    public interface IsRecordingCallback {
        void onReply(boolean currentlyRecording);
    }
}
