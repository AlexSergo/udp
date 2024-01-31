package com.example.udpapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.util.Log
import androidx.core.app.ActivityCompat


class CameraService(private val cameraManager: CameraManager, private val cameraID: String,
                    private val context: Context, private val cameraCallback: CameraCallback) {
    private val LOG_TAG = "myLogs"
    private var mCameraDevice: CameraDevice? = null
    private var mCaptureSession: CameraCaptureSession? = null
    var myCameras: Array<CameraService>? = null

    private val CAMERA1 = 0
    private val CAMERA2 = 1

    val isOpen: Boolean
        get() = mCameraDevice != null

    fun openCamera() {
        try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            cameraManager.openCamera(cameraID, mCameraCallback, null)
        } catch (_: CameraAccessException) {}
    }


    fun closeCamera() {
        if (mCameraDevice != null) {
            mCameraDevice!!.close()
            mCameraDevice = null
        }
    }

    private val mCameraCallback: CameraDevice.StateCallback =
        object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                mCameraDevice = camera
                Log.i(LOG_TAG, "Open camera  with id:" + mCameraDevice!!.id)
                cameraCallback.createCameraPreviewSession(mCameraDevice!!)
            }

            override fun onDisconnected(camera: CameraDevice) {
                mCameraDevice!!.close()
                Log.i(LOG_TAG, "disconnect camera  with id:" + mCameraDevice!!.id)
                mCameraDevice = null
            }

            override fun onError(camera: CameraDevice, error: Int) {
                Log.i(LOG_TAG, "error! camera id:" + camera.id + " error:" + error)
            }
        }
}