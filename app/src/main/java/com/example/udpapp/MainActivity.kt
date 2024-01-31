package com.example.udpapp


import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.CamcorderProfile
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.udpapp.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pub.devrel.easypermissions.EasyPermissions
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.nio.ByteBuffer


interface CameraCallback{
    fun createCameraPreviewSession(mCameraDevice: CameraDevice? = null)
}

class MainActivity : AppCompatActivity(), CameraCallback {

    private var mCameraDevice: CameraDevice? = null
    private val LOG_TAG = "myLogs"
    private var myCameras: List<CameraService>? = null
    private var mCameraManager: CameraManager? = null
    private val CAMERA1 = 0
    private val CAMERA2 = 1

    private var mCurrentFile: File? = null
    private var mMediaRecorder: MediaRecorder? = null
    private var mCaptureSession: CameraCaptureSession? = null
    private var count = 1

    private var mBackgroundThread: HandlerThread? = null
    private var mBackgroundHandler: Handler? = null

    private var mCodec: MediaCodec? = null // кодер

    var mEncoderSurface: Surface? = null // Surface как вход данных для кодера

    var outputStream: BufferedOutputStream? = null
    var outPutByteBuffer: ByteBuffer? = null
    var udpSocket: DatagramSocket? = null
    var ip_address = "172.20.5.60"
    var address: InetAddress? = null
    var port = 40001

    private lateinit var binding: ActivityMainBinding

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    private val REQUEST_EXTERNAL_STORAGE = 1


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestRequiresPermissions()

        binding.apply {
            openCamera1.setOnClickListener {
                openCamera(cameraForOpen = CAMERA1, cameraForClose = CAMERA2)

                    setUpMediaCodec()
                setUpMediaRecorder()
            }
            openCamera2.setOnClickListener {
                openCamera(cameraForOpen = CAMERA2, cameraForClose = CAMERA1)
            }

            recordVideo.setOnClickListener{
                if ((myCameras?.get(CAMERA1) != null) && (mMediaRecorder != null)) {
                    mMediaRecorder!!.start()
                }
            }

            stopVideo.setOnClickListener {
                if ((myCameras?.get(CAMERA1) != null) && (mMediaRecorder != null)) {
                    coroutineScope.launch {
                       stopStreamingVideo()
                        stopRecordingVideo()
                    }
                }
            }
        }

        try {
            udpSocket = DatagramSocket()
            Log.i(LOG_TAG, "  создали udp сокет")
        } catch (e: SocketException) {
            Log.i(LOG_TAG, " не создали udp сокет")
        }

        try {
            address = InetAddress.getByName(ip_address)
            Log.i(LOG_TAG, "  есть адрес")
        } catch (_: java.lang.Exception) {
        }

        mCameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            // Получение списка камер с устройства
            val list = mutableListOf<CameraService>()
            mCameraManager!!.cameraIdList.forEach {
                list.add(CameraService(mCameraManager!!, it, applicationContext, this))
            }
            myCameras = list

        } catch (e: CameraAccessException) {
            Log.e(LOG_TAG, e.message!!)
            e.printStackTrace()
        }

       // setUpMediaRecorder()
    }

    private fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("CameraBackground")
        mBackgroundThread?.start()
        mBackgroundHandler = Handler(mBackgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        mBackgroundThread?.quitSafely()
        try {
            mBackgroundThread?.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
    }

    private fun openCamera(cameraForOpen: Int, cameraForClose: Int) {
        if (myCameras?.get(cameraForClose)?.isOpen == true) myCameras!![cameraForClose].closeCamera()
        if (myCameras?.get(cameraForOpen) != null) {
            if (!myCameras!![cameraForOpen].isOpen) myCameras!![cameraForOpen].openCamera()
        }
    }


    private fun requestRequiresPermissions() {
        val perms =
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO)
        if (EasyPermissions.hasPermissions(this, *perms)) {
            // Already have permission, do the thing
            // ...
        } else {
            // Do not have permissions, request them now
            EasyPermissions.requestPermissions(
                this, "",
                1, *perms
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }


    override fun createCameraPreviewSession(cameraDevice: CameraDevice?) {
        val texture = binding.textureView.surfaceTexture
        // texture.setDefaultBufferSize(1920,1080);
        if (cameraDevice != null)
            mCameraDevice = cameraDevice
        val surface = Surface(texture)
        try {
            val mPreviewBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)

            /**Surface for the camera preview set up*/

            /**Surface for the camera preview set up */
            mPreviewBuilder.addTarget(surface)

            /**MediaRecorder setup for surface*/

            /**MediaRecorder setup for surface */
          //  val recorderSurface: Surface = mEncoderSurface!!.surface

            mPreviewBuilder.addTarget(mEncoderSurface!!)
            mPreviewBuilder.addTarget(mMediaRecorder!!.surface)

            coroutineScope.launch {
                mCameraDevice!!.createCaptureSession(
                    listOf(surface, mEncoderSurface!!, mMediaRecorder!!.surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            mCaptureSession = session
                            try {
                                mCaptureSession!!.setRepeatingRequest(
                                    mPreviewBuilder.build(),
                                    null,
                                    mBackgroundHandler
                                )
                            } catch (e: CameraAccessException) {
                                e.printStackTrace()
                            }
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {}
                    }, mBackgroundHandler
                )
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    override fun onPause() {
        if (myCameras?.get(CAMERA1)?.isOpen == true)
            myCameras!![CAMERA1].closeCamera()

        if (myCameras?.get(CAMERA2)?.isOpen == true)
            myCameras!![CAMERA2].closeCamera()

        stopBackgroundThread()
        super.onPause()
    }

    private fun setUpMediaRecorder() {
        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val packageManager = applicationContext.packageManager
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) {
            // Handle the case where the device does not have a microphone
            // Display an error message or disable the audio recording functionality
            return
        }
        mMediaRecorder = MediaRecorder()
        mMediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
        mMediaRecorder!!.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mMediaRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mCurrentFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            "test$count.mp4"
        )
        mMediaRecorder!!.setOutputFile(mCurrentFile!!.absolutePath)
        val profile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P)
        mMediaRecorder!!.setVideoFrameRate(profile.videoFrameRate)
        mMediaRecorder!!.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight)
        mMediaRecorder!!.setVideoEncodingBitRate(profile.videoBitRate)
        mMediaRecorder!!.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mMediaRecorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mMediaRecorder!!.setAudioEncodingBitRate(profile.audioBitRate)
        mMediaRecorder!!.setAudioSamplingRate(profile.audioSampleRate)
        try {
            mMediaRecorder!!.prepare()
            Log.i(LOG_TAG, " запустили медиа рекордер")
        } catch (e: Exception) {
            Log.i(LOG_TAG, "не запустили медиа рекордер")
        }
    }

    private fun setUpMediaCodec() {
        try {
            mCodec = MediaCodec.createEncoderByType("video/avc") // H264 кодек
        } catch (e: java.lang.Exception) {
            Log.i(LOG_TAG, "а нету кодека")
        }
        val width = 320 // ширина видео
        val height = 240 // высота видео
        val colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface // формат ввода цвета
        val videoBitrate = 500000 // битрейт видео в bps (бит в секунду)
        val videoFramePerSecond = 20 // FPS
        val iframeInterval = 2 // I-Frame интервал в секундах
        val format = MediaFormat.createVideoFormat("video/avc", width, height)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
        format.setInteger(MediaFormat.KEY_BIT_RATE, videoBitrate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, videoFramePerSecond)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iframeInterval)
        mCodec!!.configure(
            format,
            null,
            null,
            MediaCodec.CONFIGURE_FLAG_ENCODE
        ) // конфигурируем кодек как кодер
        mEncoderSurface = mCodec!!.createInputSurface() // получаем Surface кодера
        mCodec!!.setCallback(object: MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                Log.i(LOG_TAG, " outDate.length : ")
            }
            override fun onOutputBufferAvailable(
                codec: MediaCodec,
                index: Int,
                info: MediaCodec.BufferInfo
            ) {
                coroutineScope.launch(Dispatchers.IO) {
                    outPutByteBuffer = mCodec!!.getOutputBuffer(index)
                    val outDate = ByteArray(info.size)
                    outPutByteBuffer?.get(outDate)
                    Log.i(LOG_TAG, " outDate.length : " + outDate.size)

                    try {
                        val packet = DatagramPacket(outDate, outDate.size, address, port)
                        udpSocket?.send(packet)
                    } catch (e: IOException) {
                        Log.i(LOG_TAG, " не отправился UDP пакет")
                    }

                    mCodec!!.releaseOutputBuffer(index, false)
                }
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                Log.i(LOG_TAG, "Error: $e")
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                Log.i(LOG_TAG, "encoder output format changed: $format")
            }



        })
        mCodec!!.start() // запускаем кодер
        Log.i(LOG_TAG, "запустили кодек")
    }

     private fun stopRecordingVideo() {
        try {
            mCaptureSession!!.stopRepeating()
            mCaptureSession!!.abortCaptures()
            mCaptureSession!!.close()
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        mMediaRecorder!!.stop()
        mMediaRecorder!!.release()
        count++
        setUpMediaRecorder()
        createCameraPreviewSession()
    }

    private fun stopStreamingVideo() {

        if (mCameraDevice != null && mCodec != null) {
            try {
                mCaptureSession!!.stopRepeating();
                mCaptureSession!!.abortCaptures();
            } catch (e: CameraAccessException) {
                e.printStackTrace();
            }

            mCodec!!.stop()
            mCodec!!.release()
            mEncoderSurface!!.release()
            myCameras?.get(CAMERA1)?.closeCamera()
        }
    }


    inner class EncoderCallback : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {}
        override fun onOutputBufferAvailable(
            codec: MediaCodec,
            index: Int,
            info: MediaCodec.BufferInfo
        ) {
            outPutByteBuffer = mCodec!!.getOutputBuffer(index)
            val outDate = ByteArray(info.size)
            outPutByteBuffer!!.get(outDate)
            Log.i(LOG_TAG, " outDate.length : " + outDate.size)

            try {
                val packet = DatagramPacket(outDate, outDate.size, address, port)
                udpSocket!!.send(packet)
            } catch (e: IOException) {
                Log.i(LOG_TAG, " не отправился UDP пакет")
            }

            mCodec!!.releaseOutputBuffer(index, false)
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            Log.i(LOG_TAG, "Error: $e")
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            Log.i(LOG_TAG, "encoder output format changed: $format")
        }
    }


}