package com.kelly.storagetest

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.net.toFile
import timber.log.Timber
import java.io.File
import java.io.FileFilter

object FileSearcher {

    fun findAppPhotos(context: Context, subFolder: String) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            findAllPhotoInAppQ(context, subFolder)
        else
            findAllPhotoInApp(context, subFolder)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun findAllPhotoInAppQ(context: Context, subFolder: String) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_ID
        )

        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(FileSaver.getGalleryRelativePathQ(context, subFolder))

        // Display videos in alphabetical order based on their display name.
        val sortOrder = "${MediaStore.Images.Media.DISPLAY_NAME} DESC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, selection, selectionArgs, sortOrder)?.use {
            Timber.i("query finish!")
            while (it.moveToNext()) {
                    Timber.i(it.getString(it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)) + " " +
                             it.getString(it.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)))
                }
                it.close()
            }
    }

    private fun findAllPhotoInApp(context: Context, subFolder: String) {
        val projection = arrayOf(
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN)

        val root = FileSaver.getGalleryRelativePath(context, subFolder)
        if(root.isDirectory)
            root.listFiles(FileFilter { it.isDirectory })?.forEach { file ->
                Timber.i(file.absolutePath)
            }
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(FileSaver.getGalleryRelativePathQ(context, subFolder))

        // Display videos in alphabetical order based on their display name.
        val sortOrder = "${MediaStore.Images.Media.DISPLAY_NAME} DESC"

        context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder)?.use {
            while (it.moveToNext()) {
                Timber.i(it.getString(it.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)))
            }
            it.close()
        }
        Timber.i("query finish!")
    }

    fun isFileExist(context: Context, uri: Uri): Boolean {
        if(uri.scheme == "content") {
            context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DISPLAY_NAME), null, null, null)?.use {
                val isExist = it.count > 0
                it.close()
                return isExist
            }
            return false
        } else
            return File(uri.path?:"").exists()
    }

    fun getFileContentUri(ctx: Context, subFolder: String, fileName: String): Uri? {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ctx.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.RELATIVE_PATH} = ?",
                arrayOf(FileSaver.getGalleryRelativePathQ(ctx, subFolder)), null)?.use {
                if(it.moveToFirst()) {
                    val id = it.getLong(it.getColumnIndex(MediaStore.Images.Media._ID))
                    return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                }
            }
            return null
        } else {
            val path = FileSaver.getGalleryRelativePath(ctx, subFolder).absolutePath + File.separator + fileName
            return filePathToContentUri(ctx, path)
        }
    }

    fun filePathToContentUri(ctx: Context, path: String): Uri? {
        ctx.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DATA} = ?", arrayOf(path), null)?.use {
            if(it.moveToFirst()) {
                val id = it.getLong(it.getColumnIndex(MediaStore.Images.Media._ID))
                return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            }
        }
        return null
    }

    fun getFilePathUri(ctx: Context, subFolder: String, fileName: String): Uri? {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return null
        val path = FileSaver.getGalleryRelativePath(ctx, subFolder).absolutePath + File.separator + fileName
        return Uri.parse(path)
    }
}