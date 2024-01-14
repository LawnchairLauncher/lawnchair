package app.lawnchair.compatlib.eleven;

import android.app.TaskInfo;

import app.lawnchair.compatlib.TaskInfoCompat;

public class TaskInfoCompatVR implements TaskInfoCompat {

    private final TaskInfo taskInfo;

    public TaskInfoCompatVR(TaskInfo taskInfo) {
        this.taskInfo = taskInfo;
    }

    @Override
    public boolean supportsSplitScreenMultiWindow() {
        return taskInfo.supportsSplitScreenMultiWindow;
    }
}
