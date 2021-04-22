package com.kelly.storagetest

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import io.reactivex.Completable
import io.reactivex.Single
import timber.log.Timber
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

object FileSaver {

	/**
	 * App-specific files
	 * Files meant for your app's use only
	 * From internal storage, getFilesDir() or getCacheDir()
	 * From external storage, getExternalFilesDir() or getExternalCacheDir()
	 * Never needed for internal storage
	 * Not needed for external storage when your app is used on devices that run Android 4.4 (API level 19) or higher
	 * No  (other apps can access)
	 * Yes (removed on app uninstall)
	 */
	fun saveImageFilePrivate(context: Context, udid: String, data: ByteArray, fileName: String): Single<Uri> {
		return Single.create {
			if(PermissionChecker.checkAvailableLocalStorage()) {
				val image = getCreateImageFile(context, udid, fileName)
				if(image == null) it.onError(Throwable("uri is null"))
				else {
					val sOut = FileOutputStream(image.first)
					try {
						sOut.write(data)
						it.onSuccess(image.second)
					} catch (ex: Exception) {
						it.onError(ex)
					} finally {
						sOut.close()
					}
				}
			} else
				it.onError(Throwable("Storage is filled"))
		}
	}

	private fun getCreateImageFile(context: Context, udid: String, fileName: String): Pair<File, Uri>? {
		val photoFile: File? = try {
			getPhotoFile(context, udid, fileName)
		} catch (ex: IOException) {
			// Error occurred while creating the File
			null
		}

		photoFile?.also {
			return Pair(it, FileProvider.getUriForFile(
				context,
				context.getString(R.string.provider_author),
				it))
		}
		return null
	}

	@Throws(IOException::class)
	private fun getPhotoFileTmp(context: Context, prefix: String, suffix: String): File {
		val storageDir: File? = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
		return File.createTempFile(
			prefix, /* prefix */
			suffix, /* suffix */
			storageDir /* directory */
		)
	}

	@SuppressLint("SimpleDateFormat")
	@Throws(IOException::class)
	private fun getPhotoFileTmp(context: Context): File {
		val timeStamp: String? = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
		return getPhotoFileTmp(context, "${timeStamp}_tmp", ".jpg")
	}

	/**
	 * Save the file on external storage in private. Other package should get the share Uri.
	 */
	private fun getPhotoFile(context: Context, udid: String, fileName: String): File {
		val storageDir: File? = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
		val cacheDir = File(storageDir, udid)
		if (!cacheDir.exists()) cacheDir.mkdirs()
		return File(cacheDir, fileName)
	}

	/**
	 * Media
	 * Shareable media files (images, audio files, videos)
	 * MediaStore API
	 * READ_EXTERNAL_STORAGE when accessing other apps' files on Android 11 (API level 30) or higher
	 * READ_EXTERNAL_STORAGE or WRITE_EXTERNAL_STORAGE when accessing other apps' files on Android 10 (API level 29)
	 * Permissions are required for all files on Android 9 (API level 28) or lower
	 * Yes, though the other app needs the READ_EXTERNAL_STORAGE permission
	 * No (delete only by user)
	 */
	fun saveImageFile(context: Context, udid: String, data: ByteArray, fileName: String): Single<Uri> {
		return Single.create<Uri> {
			if(PermissionChecker.checkAvailableLocalStorage()) {
				try {
					val saving = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
						saveFileQ(context, data, "image/*", udid, fileName)
					else
						saveFile(context, data, "image/*", udid, fileName)
					if(saving == null) it.onError(Throwable("uri is null"))
					else it.onSuccess(saving)
				} catch (e: IOException) {
					it.onError(e)
				}
			} else
				it.onError(Throwable("Storage is filled"))
		}.doOnSubscribe { Timber.i("$udid \n Start to save file... $fileName") }
	}

	//todo how to check the file name is same? how to resolve this problem
	@Throws(IOException::class)
	private fun saveFile(context: Context, data: ByteArray, mimeType: String, relativeLocation: String, displayName: String): Uri? {
		val absolutePath = getGalleryRelativePath(context, relativeLocation).absolutePath + File.separator + displayName
		val contentValues = ContentValues()
		contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
		contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
		contentValues.put(MediaStore.MediaColumns.DATA, absolutePath)
		return if(!FileSearcher.isFileExist(context, Uri.parse(absolutePath)))
			copyFileToUri(context, contentValues, data)
		else
			Uri.parse(absolutePath)
	}

	fun getGalleryRelativePath(ctx: Context, dicName: String): File {
		val rootFolder = File(Environment.getExternalStorageDirectory().absolutePath +
				File.separator + Environment.DIRECTORY_PICTURES +
				File.separator + ctx.getString(R.string.app_name).let {
			if(dicName.isNotEmpty()) it + File.separator + dicName else it
		})
		Timber.d("rootFolder : $rootFolder")
		if(!rootFolder.exists()) rootFolder.mkdirs()
		return rootFolder
	}

	@RequiresApi(Build.VERSION_CODES.Q)
	@Throws(IOException::class)
	private fun saveFileQ(context: Context, data: ByteArray, mimeType: String, folderName: String, displayName: String): Uri? {
		val relativeLocation = getGalleryRelativePathQ(context, folderName)
		val contentValues = ContentValues()
		contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
		contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
		contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, relativeLocation)
		return copyFileToUri(context, contentValues, data)
	}

	fun getGalleryRelativePathQ(context: Context, dicName: String): String {
		return Environment.DIRECTORY_PICTURES +
				File.separator + context.getString(R.string.app_name).let {
					if(dicName.isNotEmpty()) it + File.separator + dicName else it
				}
	}

	private fun copyFileToUri(
		context: Context,
		contentValues: ContentValues,
		data: ByteArray
	): Uri? {
		val resolver = context.contentResolver
		var stream: OutputStream? = null
		var uri: Uri? = null
		try {
			val contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
			uri = resolver.insert(contentUri, contentValues)
			if (uri == null) {
				throw IOException("Failed to create new MediaStore record.")
			}
			stream = resolver.openOutputStream(uri)
			if (stream == null) {
				throw IOException("Failed to get output stream.")
			}

			stream.write(data)

		} catch (e: IOException) {
			if (uri != null) {
				// Don't leave an orphan entry in the MediaStore
				resolver.delete(uri, null, null)
			}
			throw e
		} finally {
			stream?.close()
		}
		return uri
	}

	fun removeFile(context: Context, uri: Uri): Completable {
		return Completable.create {
			if(uri.scheme == "content") {
				context.contentResolver.delete(uri, null, null)
			} else {
				val absolutePath = uri.path
				if(absolutePath?.isNotEmpty() == true) {
					FileSearcher.filePathToContentUri(context, absolutePath).also { contentUri ->
						if(contentUri != null)
							context.contentResolver.delete(contentUri, null, null)
						else {
							val f = File(absolutePath)
							if(f.exists()) f.delete()
						}
					}
				}
			}
			it.onComplete()
		}
	}

	//todo how to insert the video file
	private fun insertVideo(ctx: Context, strTitle: String, strPath: String, fileSize: String, videoDuration: String, type: String) {
		val contentResolver = ctx.contentResolver
		val newValues = ContentValues(8)
		newValues.put(MediaStore.Video.Media.TITLE, "")
		newValues.put(MediaStore.Video.Media.ALBUM, "")
		newValues.put(MediaStore.Video.Media.ARTIST, "")
		newValues.put(MediaStore.Video.Media.DISPLAY_NAME, "")
		newValues.put(MediaStore.Video.Media.MIME_TYPE, "video/avi")
		newValues.put(MediaStore.Video.Media.DATA, strPath)
		newValues.put(MediaStore.Video.Media.SIZE, 0)
		//newValues.put(MediaStore.Video.Media.DURATION, videoDuration);
		val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, newValues)
		ctx.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse(strPath)))
		//Log.i("insertMediaStore()", "uri:" + uri.toString());
	}

	fun saveSpecifyTag(context: Context, tag: String, uri: Uri): Single<String> {
		return Single.create {
			val savingData = if (uri.scheme == "content") savingEXIFTagQ(context, uri, tag)
							else savingEXIFTag(uri, tag)

			it.onSuccess(savingData)
		}
	}

	/**
	 * todo use ExifInterface.TAG_IMAGE_DESCRIPTION
	 */
	private fun savingEXIFTag(uri: Uri, tag: String
	): String {
		val exif = uri.path?.let { ExifInterface(it) }
		exif?.setAttribute(ExifInterface.TAG_USER_COMMENT, tag)
		exif?.saveAttributes()
		val savingData = exif?.getAttribute(ExifInterface.TAG_USER_COMMENT) ?: "No data"
		return savingData
	}

	private fun savingEXIFTagQ(
		context: Context,
		uri: Uri,
		tag: String
	): String {
		val exif = context.contentResolver.openFileDescriptor(uri, "rw")?.fileDescriptor?.let {
			ExifInterface(it)
		}
		exif?.setAttribute(ExifInterface.TAG_USER_COMMENT, tag)
		exif?.saveAttributes()
		return exif?.getAttribute(ExifInterface.TAG_USER_COMMENT) ?: "No data"
	}
}