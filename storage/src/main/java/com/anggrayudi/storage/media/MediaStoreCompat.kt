package com.anggrayudi.storage.media

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.os.Environment
import android.provider.BaseColumns
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import com.anggrayudi.storage.extension.trimFileSeparator
import com.anggrayudi.storage.file.DocumentFileCompat
import com.anggrayudi.storage.file.DocumentFileCompat.removeForbiddenCharsFromFilename
import com.anggrayudi.storage.file.PublicDirectory
import com.anggrayudi.storage.file.autoIncrementFileName
import com.anggrayudi.storage.file.canModify
import java.io.File

/**
 * Created on 05/09/20
 * @author Anggrayudi H
 */
object MediaStoreCompat {

    @JvmStatic
    val volumeName: String
        @SuppressLint("InlinedApi")
        get() = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) MediaStore.VOLUME_EXTERNAL else MediaStore.VOLUME_EXTERNAL_PRIMARY

    @JvmStatic
    @RequiresApi(Build.VERSION_CODES.Q)
    fun createDownload(context: Context, file: FileDescription): MediaFile? {
        return createMedia(context, MediaType.DOWNLOADS, Environment.DIRECTORY_DOWNLOADS, file)
    }

    @JvmOverloads
    @JvmStatic
    fun createImage(context: Context, file: FileDescription, relativeParentDirectory: ImageMediaDirectory? = null): MediaFile? {
        return createMedia(context, MediaType.IMAGE, relativeParentDirectory?.folderName, file)
    }

    @JvmOverloads
    @JvmStatic
    fun createAudio(context: Context, file: FileDescription, relativeParentDirectory: AudioMediaDirectory? = null): MediaFile? {
        return createMedia(context, MediaType.AUDIO, relativeParentDirectory?.folderName, file)
    }

    @JvmOverloads
    @JvmStatic
    fun createVideo(context: Context, file: FileDescription, relativeParentDirectory: VideoMediaDirectory? = null): MediaFile? {
        return createMedia(context, MediaType.VIDEO, relativeParentDirectory?.folderName, file)
    }

    @Suppress("DEPRECATION")
    private fun createMedia(context: Context, mediaType: MediaType, folderName: String?, file: FileDescription): MediaFile? {
        val dateCreated = System.currentTimeMillis()
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, file.mimeType)
            put(MediaStore.MediaColumns.DATE_ADDED, dateCreated)
            put(MediaStore.MediaColumns.DATE_MODIFIED, dateCreated)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relativePath = if (folderName != null) "$folderName/${file.subFolder}".trimFileSeparator() else null
            contentValues.apply {
                put(MediaStore.MediaColumns.OWNER_PACKAGE_NAME, context.packageName)
                if (relativePath != null) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                }
            }
            var existingMedia = fromBasePath(context, mediaType, "$relativePath/${file.name}")
            when {
                existingMedia?.empty == true -> existingMedia
                existingMedia?.exists == true -> {
                    val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(file.mimeType) ?: file.name.substringAfterLast('.', "")
                    val baseName = file.name.substringBeforeLast('.')
                    val prefix = "$baseName ("
                    val lastFile = fromFileNameContains(context, mediaType, baseName)
                        .filter { relativePath == null || relativePath == it.relativePath.removeSuffix("/") }
                        .mapNotNull { it.name }
                        .filter {
                            it.startsWith(prefix) && (DocumentFileCompat.FILE_NAME_DUPLICATION_REGEX_WITH_EXTENSION.matches(it)
                                    || DocumentFileCompat.FILE_NAME_DUPLICATION_REGEX_WITHOUT_EXTENSION.matches(it))
                        }
                        .maxOfOrNull { it }
                        .orEmpty()
                    var count = lastFile.substringAfterLast('(', "")
                        .substringBefore(')', "")
                        .toIntOrNull() ?: 0

                    existingMedia = fromFileName(context, mediaType, "$baseName ($count).$ext".trimEnd('.'))
                    if (existingMedia != null) {
                        existingMedia
                    } else {
                        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "$baseName (${++count}).$ext".trimEnd('.'))
                        MediaFile(context, context.contentResolver.insert(mediaType.writeUri, contentValues) ?: return null)
                    }
                }
                else -> MediaFile(context, context.contentResolver.insert(mediaType.writeUri, contentValues) ?: return null)
            }
        } else {
            val publicDirectory = Environment.getExternalStoragePublicDirectory(folderName)
            if (publicDirectory.canModify) {
                var media = File("$publicDirectory/${file.subFolder}", file.name)
                val parentFile = media.parentFile ?: return null
                parentFile.mkdirs()
                if (media.exists()) {
                    val filename = parentFile.autoIncrementFileName(file.name)
                    contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    media = File(parentFile, filename)
                }
                context.contentResolver.insert(mediaType.writeUri, contentValues)?.let {
                    media.createNewFile()
                    MediaFile(context, it)
                }
            } else {
                null
            }
        }
    }

    @JvmStatic
    fun fromMediaId(context: Context, mediaType: MediaType, id: String): MediaFile {
        return MediaFile(context, mediaType.writeUri.buildUpon().appendPath(id).build())
    }

    @JvmStatic
    fun fromFileName(context: Context, mediaType: MediaType, name: String): MediaFile? {
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        return context.contentResolver.query(mediaType.readUri, arrayOf(BaseColumns._ID), selection, arrayOf(name), null)?.use {
            fromCursorToMediaFile(context, mediaType, it)
        }
    }

    /**
     * @param basePath is relative path + filename
     * @return `null` if base path does not contain relative path or the media is not found
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    @JvmStatic
    fun fromBasePath(context: Context, mediaType: MediaType, basePath: String): MediaFile? {
        val cleanBasePath = basePath.removeForbiddenCharsFromFilename().trimFileSeparator()
        val relativePath = cleanBasePath.substringBeforeLast('/', "")
        if (relativePath.isEmpty()) {
            return null
        }
        val filename = cleanBasePath.substringAfterLast('/')
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        return context.contentResolver.query(mediaType.readUri, arrayOf(BaseColumns._ID), selection, arrayOf(filename, "$relativePath/"), null)?.use {
            fromCursorToMediaFile(context, mediaType, it)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun mediaTypeFromRelativePath(cleanRelativePath: String) = when (cleanRelativePath) {
        Environment.DIRECTORY_DCIM, Environment.DIRECTORY_PICTURES -> MediaType.IMAGE
        Environment.DIRECTORY_MOVIES, Environment.DIRECTORY_DCIM -> MediaType.VIDEO
        Environment.DIRECTORY_MUSIC, Environment.DIRECTORY_PODCASTS, Environment.DIRECTORY_RINGTONES,
        Environment.DIRECTORY_ALARMS, Environment.DIRECTORY_NOTIFICATIONS -> MediaType.AUDIO
        Environment.DIRECTORY_DOWNLOADS -> MediaType.DOWNLOADS
        else -> null
    }

    /**
     * @see MediaStore.MediaColumns.RELATIVE_PATH
     */
    @JvmStatic
    @RequiresApi(Build.VERSION_CODES.Q)
    fun fromRelativePath(context: Context, publicDirectory: PublicDirectory) = fromRelativePath(context, publicDirectory.folderName)

    /**
     * @see MediaStore.MediaColumns.RELATIVE_PATH
     */
    @JvmStatic
    @RequiresApi(Build.VERSION_CODES.Q)
    fun fromRelativePath(context: Context, relativePath: String): List<MediaFile> {
        val cleanRelativePath = relativePath.trimFileSeparator()
        val mediaType = mediaTypeFromRelativePath(cleanRelativePath) ?: return emptyList()
        val relativePathWithSlashSuffix = relativePath.trimEnd('/') + '/'
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} IN(?, ?)"
        val selectionArgs = arrayOf(relativePathWithSlashSuffix, cleanRelativePath)
        return context.contentResolver.query(mediaType.readUri, arrayOf(BaseColumns._ID), selection, selectionArgs, null)?.use {
            fromCursorToMediaFiles(context, mediaType, it)
        }.orEmpty()
    }

    /**
     * @see MediaStore.MediaColumns.RELATIVE_PATH
     */
    @JvmStatic
    @RequiresApi(Build.VERSION_CODES.Q)
    fun fromRelativePath(context: Context, relativePath: String, name: String): MediaFile? {
        val cleanRelativePath = relativePath.trimFileSeparator()
        val mediaType = mediaTypeFromRelativePath(cleanRelativePath) ?: return null
        val relativePathWithSlashSuffix = relativePath.trimEnd('/') + '/'
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} IN(?, ?)"
        val selectionArgs = arrayOf(name, relativePathWithSlashSuffix, cleanRelativePath)
        return context.contentResolver.query(mediaType.readUri, arrayOf(BaseColumns._ID), selection, selectionArgs, null)?.use {
            fromCursorToMediaFile(context, mediaType, it)
        }
    }

    @JvmStatic
    fun fromFileNameContains(context: Context, mediaType: MediaType, containsName: String): List<MediaFile> {
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%$containsName%'"
        return context.contentResolver.query(mediaType.readUri, arrayOf(BaseColumns._ID), selection, null, null)?.use {
            fromCursorToMediaFiles(context, mediaType, it)
        }.orEmpty()
    }

    @JvmStatic
    fun fromMimeType(context: Context, mediaType: MediaType, mimeType: String): List<MediaFile> {
        val selection = "${MediaStore.MediaColumns.MIME_TYPE} = ?"
        return context.contentResolver.query(mediaType.readUri, arrayOf(BaseColumns._ID), selection, arrayOf(mimeType), null)?.use {
            fromCursorToMediaFiles(context, mediaType, it)
        }.orEmpty()
    }

    @JvmStatic
    fun fromMediaType(context: Context, mediaType: MediaType): List<MediaFile> {
        return context.contentResolver.query(mediaType.readUri, arrayOf(BaseColumns._ID), null, null, null)?.use {
            fromCursorToMediaFiles(context, mediaType, it)
        }.orEmpty()
    }

    private fun fromCursorToMediaFiles(context: Context, mediaType: MediaType, cursor: Cursor): List<MediaFile> {
        if (cursor.moveToFirst()) {
            val columnId = cursor.getColumnIndex(BaseColumns._ID)
            val mediaFiles = ArrayList<MediaFile>(cursor.count)
            do {
                val mediaId = cursor.getString(columnId)
                mediaFiles.add(fromMediaId(context, mediaType, mediaId))
            } while (cursor.moveToNext())
            return mediaFiles
        }
        return emptyList()
    }

    private fun fromCursorToMediaFile(context: Context, mediaType: MediaType, cursor: Cursor): MediaFile? {
        return if (cursor.moveToFirst()) {
            val mediaId = cursor.getString(cursor.getColumnIndex(BaseColumns._ID))
            fromMediaId(context, mediaType, mediaId)
        } else null
    }
}