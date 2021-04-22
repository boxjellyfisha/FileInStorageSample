package com.kelly.storagetest

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageManager.ACTION_MANAGE_STORAGE
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.io.File
import java.util.*

object PermissionChecker {

    private val TAG = PermissionChecker::class.java.simpleName

    val internet = arrayOf(Manifest.permission.INTERNET)
    val location = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    val storage = arrayOf(Manifest.permission.INTERNET,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE)

    private const val MIN_AVAILABLE_SPACE_MB = 32 // Currently, this is updated size of Bite!

    fun checkPermission(permissions: Array<out String>, fragment: Fragment,  requestCode: Int): Boolean {
        if(isPermissionGranted(permissions, fragment.requireContext()))
            return true
        else requestPermissions(permissions, fragment, requestCode)
        return false
    }

    fun checkPermission(permissions: Array<out String>, activity: Activity,  requestCode: Int): Boolean {
        if(isPermissionGranted(permissions, activity))
            return true
        else requestPermissions(permissions, activity, requestCode)
        return false
    }

    fun isPermissionGranted(context: Context, vararg permissions: String): Boolean {
        return isPermissionGranted(permissions, context)
    }

    private fun isPermissionGranted(permissions: Array<out String>, context: Context): Boolean {
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    /**
     * * Dynamic Request permissions (> Android 6.0)
     * 1. camera permission
     * 2. external storage permission
     * 3. find location
     *
     * @param permissions The permission to request.
     * @return True if already grant. False otherwise.
     */
    fun requestPermissions(activity: Activity, requestCode: Int, vararg permissions: String): Boolean {
        return requestPermissions(permissions, activity, requestCode)
    }

    private fun requestPermissions(permissions: Array<out String>, activity: Activity, requestCode: Int): Boolean {
        val deniedPermissions = ArrayList<String>()
        for (permission in permissions) {
            if (activity.checkSelfPermission(permission) == PackageManager.PERMISSION_DENIED)
                deniedPermissions.add(permission)
        }
        if (!deniedPermissions.isEmpty()) {
            activity.requestPermissions(
                    deniedPermissions.toTypedArray(),
                    requestCode)
            return false
        } else {
            return true
        }
    }

    private fun requestPermissions(permissions: Array<out String>, fragment: Fragment, requestCode: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val deniedPermissions = ArrayList<String>()
            for (permission in permissions) {
                if (fragment.requireActivity().checkSelfPermission(permission) == PackageManager.PERMISSION_DENIED)
                    deniedPermissions.add(permission)
            }
            if (!deniedPermissions.isEmpty()) {
                fragment.requestPermissions(
                        deniedPermissions.toTypedArray(),
                        requestCode)
                return false
            } else {
                return true
            }
        } else {
            return true
        }
    }

    /**
     * Check permission granted.
     *
     * @param grantResults
     * @return
     */
    fun isAllPermissionGranted(grantResults: IntArray?): Boolean {
        if(grantResults == null)
            return false
        for (grantPermission in grantResults) {
            if (grantPermission == PackageManager.PERMISSION_DENIED)
                return false
        }
        return true
    }

    /**
     * Check the available space in local storage.
     *
     * @return space with MB
     */
    fun checkAvailableLocalStorage(): Boolean {
        val dataFree = Environment.getDataDirectory().freeSpace.div(1000).div(1000)
        val path = Environment.getExternalStorageDirectory()
        val stat = StatFs(path.path)
        val blockSize = stat.blockSizeLong
        val availableBlocks = stat.availableBlocksLong
        val megaAvailable = availableBlocks * blockSize / 1048576 // 1048576 = 1024 * 1024 KB
        Log.i(TAG, megaAvailable.toString() + "MB")
        return megaAvailable > MIN_AVAILABLE_SPACE_MB || dataFree > 300
    }

    // App needs 10 MB within internal storage.
    private const val NUM_BYTES_NEEDED_FOR_MY_APP = 1024 * 1024 * 10L;

    @TargetApi(Build.VERSION_CODES.O)
    fun checkAllocate(applicationContext: Context, filesDir: File) {
        val storageManager = applicationContext.getSystemService<StorageManager>(StorageManager::class.java)!!
        val appSpecificInternalDirUuid: UUID = storageManager.getUuidForPath(filesDir)
        val availableBytes: Long =
            storageManager.getAllocatableBytes(appSpecificInternalDirUuid)
        if (availableBytes >= NUM_BYTES_NEEDED_FOR_MY_APP) {
            storageManager.allocateBytes(
                appSpecificInternalDirUuid, NUM_BYTES_NEEDED_FOR_MY_APP)
        } else {
            val storageIntent = Intent().apply {
                // To request that the user remove all app cache files instead, set
                // "action" to ACTION_CLEAR_APP_CACHE.
                action = ACTION_MANAGE_STORAGE
            }
        }
    }

    fun externalMemoryAvailable(): Boolean {
        return android.os.Environment.getExternalStorageState() == android.os.Environment.MEDIA_MOUNTED
    }
}
