package com.dylankpowers.timelapse;

import android.content.Context;
import android.support.design.widget.FloatingActionButton;
import android.util.AttributeSet;

public class RecordingButton extends FloatingActionButton {
    private static final int[] STATE_RECORDING = { R.attr.state_recording };

    private boolean mIsRecording = false;
    public void setRecording(boolean isRecording) {
        mIsRecording = isRecording;
        refreshDrawableState();
    }

    public RecordingButton(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
    }

    @Override
    public int[] onCreateDrawableState(int extraSpace) {
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        if (mIsRecording) {
            mergeDrawableStates(drawableState, STATE_RECORDING);
        }

        return drawableState;
    }
}
