package com.example.audiodemo

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Created by xuhao on 2021/3/15.
 */
object PermissionUtils {
    fun requestPermission(
        activity: Activity?,
        permission: String,
        requestCode: Int
    ): Boolean {
        //判断Android版本是否大于23
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val checkCallPhonePermission =
                ContextCompat.checkSelfPermission(activity!!, permission)
            if (checkCallPhonePermission != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    activity!!,
                    arrayOf(permission),
                    requestCode
                )
                return false
            }
        }
        return true
    }
}