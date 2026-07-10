package com.example.eyeprotect.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

fun ImageProxy.toRotatedBitmap(rotationDegrees: Int): Bitmap {
    val nv21 = yuv420ToNv21(this)
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val stream = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, stream)
    val bitmap = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size())

    if (rotationDegrees == 0) {
        return bitmap
    }

    val matrix = Matrix().apply {
        postRotate(rotationDegrees.toFloat())
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun yuv420ToNv21(image: ImageProxy): ByteArray {
    val ySize = image.width * image.height
    val uvSize = image.width * image.height / 4
    val nv21 = ByteArray(ySize + uvSize * 2)

    copyPlane(
        buffer = image.planes[0].buffer,
        width = image.width,
        height = image.height,
        rowStride = image.planes[0].rowStride,
        pixelStride = image.planes[0].pixelStride,
        output = nv21,
        outputOffset = 0,
        outputPixelStride = 1
    )

    val chromaHeight = image.height / 2
    val chromaWidth = image.width / 2
    copyPlane(
        buffer = image.planes[2].buffer,
        width = chromaWidth,
        height = chromaHeight,
        rowStride = image.planes[2].rowStride,
        pixelStride = image.planes[2].pixelStride,
        output = nv21,
        outputOffset = ySize,
        outputPixelStride = 2
    )
    copyPlane(
        buffer = image.planes[1].buffer,
        width = chromaWidth,
        height = chromaHeight,
        rowStride = image.planes[1].rowStride,
        pixelStride = image.planes[1].pixelStride,
        output = nv21,
        outputOffset = ySize + 1,
        outputPixelStride = 2
    )

    return nv21
}

private fun copyPlane(
    buffer: ByteBuffer,
    width: Int,
    height: Int,
    rowStride: Int,
    pixelStride: Int,
    output: ByteArray,
    outputOffset: Int,
    outputPixelStride: Int
) {
    val row = ByteArray(rowStride)
    var outputIndex = outputOffset

    for (rowIndex in 0 until height) {
        val rowLength = if (pixelStride == 1 && outputPixelStride == 1) {
            width
        } else {
            (width - 1) * pixelStride + 1
        }
        buffer.position(rowIndex * rowStride)
        buffer.get(row, 0, rowLength)

        for (columnIndex in 0 until width) {
            output[outputIndex] = row[columnIndex * pixelStride]
            outputIndex += outputPixelStride
        }
    }
}
