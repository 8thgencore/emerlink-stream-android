package net.emerlink.stream.service.media

import android.util.Log
import com.pedro.library.base.recording.RecordController

class RecordingListener : RecordController.Listener {
    companion object {
        private const val TAG = "RecordingListener"
    }

    override fun onStatusChange(status: RecordController.Status) {
        Log.d(TAG, status.name)
    }

    override fun onError(e: Exception?) {
        super.onError(e)
        Log.d(TAG, "Recording error", e)
    }
}
