package com.example.breathedetection_with_python

import android.hardware.camera2.CameraDevice
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.opencv.android.OpenCVLoader
import java.io.File
import org.python.util.PythonInterpreter
import org.python.core.PySystemState
import java.nio.ByteBuffer
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.commons.io.output.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {
    private val CAMERA_PERMISSION_REQUEST_CODE = 100
    private val WIDTH = 640
    private val HEIGHT = 480

    private lateinit var pythonInterpreter: PythonInterpreter
    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var breathingRateTextView: TextView
    private lateinit var cameraDevice: CameraDevice
    private lateinit var imageReader: ImageReader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        // logcat test
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Unable to load OpenCV")
        } else {

            Log.d("OpenCV", "OpenCV successfully loaded")
        }
        setContentView(R.layout.activity_main)
        previewView = findViewById(R.id.previewView)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // 初始化 Jython
        initPython()
        startCamera()
    }


    private fun processImage(image: Image) {
        // 將 Image 轉換為 ByteArray
        val buffer: ByteBuffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.capacity())
        buffer.get(bytes)

        val python = Python.getInstance()
        val pyModule = python.getModule("breathing_rate")

        // 調用 Python 腳本中的 `process_frame`
        val breathingRate = pyModule.callAttr("process_frame", bytes, WIDTH, HEIGHT).toDouble()

        runOnUiThread {
            breathingRateTextView.text = "呼吸率: ${String.format("%.2f", breathingRate)} 次/分鐘"
        }
    }

    private fun updateBreathingRate() {
        val python = Python.getInstance()
        val pyModule = python.getModule("breathing_rate")

        val breathingRate = pyModule.callAttr("get_breathing_rate").toDouble()
        runOnUiThread {
            breathingRateTextView.text = "呼吸率: ${String.format("%.2f", breathingRate)} 次/分鐘"
        }
    }

    //init and permissions
    private fun initPython() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview
                )
            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

}