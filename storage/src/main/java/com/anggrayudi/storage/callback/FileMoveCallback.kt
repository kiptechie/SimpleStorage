package com.anggrayudi.storage.callback

import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.media.MediaFile

/**
 * Created on 17/08/20
 *
 * @author Anggrayudi H
 */
interface FileMoveCallback : FileCallback {
    /**
     * Given `freeSpace` and `fileSize`, then you decide whether the copy process will be continued or not.
     * You can give space tolerant here, e.g. 100MB.
     * Only called when you to move the file into different storage ID.
     *
     * @param freeSpace of target path
     * @return `true` to continue process
     */
    override fun onCheckFreeSpace(freeSpace: Long, fileSize: Long): Boolean

    /**
     * @param file can be [DocumentFile] or [MediaFile]
     * @return Time interval to watch file copy progress in milliseconds, otherwise `0` if you don't want to watch at all.
     * Setting negative value will cancel the action.
     */
    @JvmDefault
    fun onStartMoving(file: Any): Long = 0

    /**
     * Only called if the returned [onStartMoving] greater than `0`
     *
     * @param progress   in percent
     * @param writeSpeed in bytes
     */
    @JvmDefault
    override fun onReport(progress: Float, bytesMoved: Long, writeSpeed: Int) {
        // default implementation
    }

    /**
     * @param file can be [DocumentFile] or [MediaFile]
     * @param file newly moved file
     */
    @JvmDefault
    fun onCompleted(file: Any) {
        // default implementation
    }
}