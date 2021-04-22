package com.kelly.storagetest

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.FileDescriptor
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit


@SuppressLint("CheckResult")
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.plant(Timber.DebugTree())

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        PermissionChecker.checkPermission(PermissionChecker.storage, this, PERMISSION)

        tv_private.setOnClickListener {
            goSelectFile(SELECT_RESULT_PRIVATE)
        }

        tv_public.setOnClickListener {
            goSelectFile(SELECT_RESULT_PUBLIC)
        }

        tv_r_public.setOnClickListener {
            val uri = FileSearcher.getFilePathUri(this, "cat", "public.jpg")
            if(uri != null) {
                Timber.i(uri.toString())
                FileSaver.removeFile(this, uri)
                    .subscribeOn(Schedulers.io())
                    .subscribe({ showSavingData(uri) }, { Timber.e(it) })
            }
        }

        tv_log_public.setOnClickListener {
            val uri = FileSearcher.getFileContentUri(this, "cat", "public.jpg")
            showSavingData(uri)
        }
    }

    private fun showSavingData(data: Pair<Uri, String>) {
        iv_tmp.setImageURI(data.first)
        tv_tmp.text = "${data.first.path} : ${data.second}"
    }

    private fun showSavingData(uri: Uri?) {
        if(uri != null)
            iv_tmp.setImageURI(uri)
        else
            iv_tmp.setImageResource(android.R.color.darker_gray)
        tv_tmp.text = uri?.path ?:"No uri"
    }

    private val PERMISSION = 1001
    private val SELECT_RESULT_PRIVATE = 1002
    private val SELECT_RESULT_PUBLIC = 1003
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Timber.i("Back to activity")
        super.onActivityResult(requestCode, resultCode, data)
        if(resultCode == Activity.RESULT_OK) {
            var uri: Uri? = null
            if (data != null) {
                uri = data.data ?: return
                Timber.i("Start to load file")
                when(requestCode) {
                    SELECT_RESULT_PRIVATE -> onClickSavePrivate(uri)
                    SELECT_RESULT_PUBLIC -> onClickSavePublic(uri)
                }
            }
        }
    }

    /**
     * in android Q -> private.jpg save in
     * /files_root/files/Pictures/private.jpg
     * => /Android/data/com.kelly.storagetest/files/Pictures/private.jpg
     */
    private fun onClickSavePrivate(source: Uri) {
        loadImageFromUri(source)
            .flatMap { FileSaver.saveImageFilePrivate(this, "hello", it, "private.jpg") }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { tv_private.isEnabled = false }
            .doFinally{ tv_private.isEnabled = true }
            .subscribe({ showSavingData(it) },
                       { Timber.e(it) })
    }

    /**
     * in android Q -> public.jpg save in
     * /Pictures/app name/cat/public.jpg (can create the cat dir automatically)
     */
    private fun onClickSavePublic(source: Uri) {
        loadImageFromUri(source)
            .flatMap { FileSaver.saveImageFile(this, "cat", it, "public.jpg") }
            .delay(300, TimeUnit.MILLISECONDS)
            .flatMap { FileSaver.saveSpecifyTag(this, "HelloCat", it).map { tag -> Pair(it, tag) } }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { tv_public.isEnabled = false }
            .doFinally{ tv_public.isEnabled = true }
            .subscribe({ showSavingData(it) },
                       { Timber.e(it) })
    }

    private fun loadImageFromUri(source: Uri): Single<ByteArray> {
        return Single.create<ByteArray> {
            try {
                Timber.i("load file from media: $source")
                val tmp = getBytes(contentResolver.openInputStream(source))
                if (tmp != null) it.onSuccess(tmp)
                else it.onError(Throwable("array is null"))
            } catch (e: IOException) {
                it.onError(e)
            }
        }
    }

    private fun goSelectFile(requestCode: Int) {
        startActivityForResult(getSelectImage(this), requestCode)
    }

    private fun getSelectImage(activity: Activity): Intent? {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        }
        return if (intent.resolveActivity(activity.packageManager) != null) intent else null
    }

    @Throws(IOException::class)
    private fun getBitmapFromUri(uri: Uri): Bitmap? {
        val parcelFileDescriptor =
            contentResolver.openFileDescriptor(uri, "r")
        val fileDescriptor: FileDescriptor = parcelFileDescriptor!!.fileDescriptor
        val image = BitmapFactory.decodeFileDescriptor(fileDescriptor)
        parcelFileDescriptor.close()
        return image
    }

    @Throws(IOException::class)
    fun getBytes(inputStream: InputStream?): ByteArray? {
        if(inputStream == null) throw IOException("null input")
        val byteBuffer = ByteArrayOutputStream()
        val bufferSize = 1024
        val buffer = ByteArray(bufferSize)
        var len = 0
        while (inputStream.read(buffer).also { len = it } != -1) {
            byteBuffer.write(buffer, 0, len)
        }
        return byteBuffer.toByteArray()
    }
}