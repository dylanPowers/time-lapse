package com.dylankpowers.timelapse;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
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
import android.provider.MediaStore;
import android.support.annotation.InterpolatorRes;
import android.support.annotation.NonNull;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.view.Display;
import android.view.Surface;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;

public class TimeLapseCapture {
    private static final String TAG = "TimeLapseCapture";
    private static final int VIDEO_FPS = 60;

    private Handler mBackgroundHandler;
    private CameraDevice mCamera;
    private CameraManager mCameraManager;
    private CameraCharacteristics mCameraCharacteristics;
    private SimpleCallback mCameraReadyCallback;
    private CameraCaptureSession mCaptureSession;
    private ContentResolver mContentResolver;
    private Context mContext;
    private boolean mCurrentlyRecording = false;
    private Display mDefaultDisplay;
    private Surface mPreviewSurface;
    private MediaRecorder mVideo;
    private SimpleCallback mVideoRecorderStarted;
    private SimpleCallback mVideoRecorderStopped;

    public TimeLapseCapture(CameraManager cameraManager,
                            Handler backgroundHandler,
                            Display defaultDisplay, ContentResolver contentResolver,
                            Context ctx) {
        mCameraManager = cameraManager;
        mBackgroundHandler = backgroundHandler;
        mDefaultDisplay = defaultDisplay;
        mContentResolver = contentResolver;
        mContext = ctx;
    }

    private final CameraDevice.StateCallback
            mCameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCamera = camera;
            mCameraReadyCallback.onEvent();
            mCameraReadyCallback = null;

            createPreviewCaptureSession();
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

    public void close() {
        if (mVideo != null) {
            mBackgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mCurrentlyRecording) {
                        mVideo.stop();
                        mCurrentlyRecording = false;
                    }

                    mVideo.release();
                    mVideo = null;
                }
            });
        }

        if (mCaptureSession != null) {
            mCaptureSession.close();
        }

        if (mCamera != null) {
            mCamera.close();
        }
    }

    private void createRecordingCaptureSession() {
        if (mCaptureSession != null) {
            mCaptureSession.close();
        }
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
                    mVideoRecorderStarted.onEvent();
                    mVideoRecorderStarted = null;
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.d(TAG, "Camera capture session configure failed.");
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            throw new RuntimeException("Can't access the camera.", e);
        }
    }

    private void createPreviewCaptureSession() {
        if (mCaptureSession != null) {
            mCaptureSession.close();
        }
        try {
            mCamera.createCaptureSession(
                    Arrays.asList(mPreviewSurface),
                    new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    onCaptureSessionConfigured(session, CameraDevice.TEMPLATE_PREVIEW);

                    if (mVideoRecorderStopped != null) {
                        mCurrentlyRecording = false;
                        Log.d(TAG, "Video recorder stopped.");
                        mVideoRecorderStopped.onEvent();
                        mVideoRecorderStopped = null;
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.d(TAG, "Camera capture session configure failed.");
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            throw new RuntimeException("Can't access the camera.", e);
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

    public void isRecording(final IsRecordingCallback callback) {
        // Run on the background thread or else concurrency issues could result
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onReply(mCurrentlyRecording);
            }
        });
    }

    private void onCaptureSessionConfigured(CameraCaptureSession session, int sessionTemplateType) {
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
        } catch (CameraAccessException e) {
            throw new RuntimeException("Can't access the camera", e);
        }
    }

    public void open(Surface previewSurface, SimpleCallback callback) {
        mPreviewSurface = previewSurface;
        mCameraReadyCallback = callback;

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

    public void startRecording(SimpleCallback callback) {
        mVideoRecorderStarted = callback;
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                createRecordingCaptureSession();
            }
        });
    }

    public void stopRecording(SimpleCallback callback) {
        mVideoRecorderStopped = callback;
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    mVideo.stop();
                } catch (RuntimeException e) {
                    Log.d(TAG, "Nothing was recorded");
                }

                createPreviewCaptureSession();
            }
        });
    }

    private void setupVideoRecorder() {
        mVideo = new MediaRecorder();
        mVideo.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mVideo.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mVideo.setCaptureRate(15);

        int videoOrientation = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) - 90;
        int deviceRotation = mDefaultDisplay.getRotation();
        if (deviceRotation == Surface.ROTATION_270) {
            videoOrientation = (videoOrientation + 180) % 360;
        }

        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_1080P);
        mVideo.setOrientationHint(videoOrientation);
        mVideo.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        double log2FrameRateRatio = Math.log10(VIDEO_FPS / profile.videoFrameRate) / Math.log10(2);
        double bitrateChangeRatio = Math.pow(1.5, log2FrameRateRatio);
        mVideo.setVideoEncodingBitRate((int) (profile.videoBitRate * bitrateChangeRatio));
        mVideo.setVideoFrameRate(VIDEO_FPS);
        mVideo.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
        String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                .getAbsolutePath();
        String filePath = path + "/time-lapse.mp4";
        mVideo.setOutputFile(filePath);
        try {
            mVideo.prepare();
        } catch (IOException e) {
            throw new RuntimeException("Unable to prepare the video recorder.", e);
        }

//        MediaMetadata.
        ContentValues values = new ContentValues(5);
        values.put(MediaStore.MediaColumns.HEIGHT, profile.videoFrameHeight);
        values.put(MediaStore.MediaColumns.WIDTH, profile.videoFrameWidth);
        values.put(MediaStore.Video.Media.RESOLUTION,
                profile.videoFrameWidth + "x" + profile.videoFrameHeight);
        values.put(MediaStore.MediaColumns.DATA, filePath);
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
//        MediaMe
//        Uri filePathUri = FileProvider.getUriForFile(mContext, "com.dylankpowers.timelapse", new File(filePath));
//        Uri filePathUri = Uri.parse("content://com.dylankpowers.timelapse" + filePath);
//        Log.d(TAG, "File path uri: " + filePathUri);

//        Uri mediaTable = Uri.parse("content://media/external/video/media");
        Uri mediaTable = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        Log.d(TAG, "mediaTable: " + mediaTable.toString());
        String query =  MediaStore.MediaColumns.DATA + " = '" + filePath + "'";
        Log.d(TAG, "Rows updated: " + mContentResolver.update(mediaTable, values, query, null));
        Cursor result = mContentResolver.query(mediaTable, new String[] { "_id" }, query, null, null);
        result.moveToFirst();
        String id = result.getString(0);

        Uri contentUri = mediaTable.buildUpon().appendPath(id).build(); //mContentResolver.insert(mediaTable, values);
        Log.d(TAG, "contentUri: " + contentUri.toString());
        mContentResolver.update(contentUri, values, null, null);

    }

    public interface SimpleCallback {
        void onEvent();
    }

    public interface IsRecordingCallback {
        void onReply(boolean currentlyRecording);
    }
}
