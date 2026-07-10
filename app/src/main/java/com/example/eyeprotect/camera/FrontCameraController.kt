package com.example.eyeprotect.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.eyeprotect.vision.EyePositionAnalyzer
import com.example.eyeprotect.vision.EyeFrameResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FrontCameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var analyzer: EyePositionAnalyzer? = null
    private var analysisExecutor: ExecutorService? = null

    fun startPreview(
        previewView: PreviewView,
        onFrameResult: (EyeFrameResult) -> Unit,
        onReady: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        stopPreview()

        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                try {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val executor = Executors.newSingleThreadExecutor()
                    val eyeAnalyzer = EyePositionAnalyzer(
                        onResult = { result ->
                            ContextCompat.getMainExecutor(context).execute {
                                onFrameResult(result)
                            }
                        },
                        onError = { throwable ->
                            ContextCompat.getMainExecutor(context).execute {
                                onError(throwable)
                            }
                        }
                    )
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(executor, eyeAnalyzer)
                        }

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        imageAnalysis
                    )

                    analyzer = eyeAnalyzer
                    analysisExecutor = executor
                    cameraProvider = provider
                    onReady()
                } catch (throwable: Throwable) {
                    onError(throwable)
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun stopPreview() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        analyzer?.close()
        analyzer = null
        analysisExecutor?.shutdown()
        analysisExecutor = null
    }
}
