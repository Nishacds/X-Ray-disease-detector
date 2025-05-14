package com.example.diseasepredictionapp

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.diseasepredictionapp.ml.XrayModel
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var resView: TextView
    private lateinit var selectButton: Button
    private lateinit var predictButton: Button
    private lateinit var sharePdfButton: Button

    private var selectedImageBitmap: Bitmap? = null
    private val IMAGE_PICK_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        resView = findViewById(R.id.resView)
        selectButton = findViewById(R.id.selectButton)
        predictButton = findViewById(R.id.predictButton)
        sharePdfButton = findViewById(R.id.sharePdfButton)

        selectButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, IMAGE_PICK_CODE)
        }

        predictButton.setOnClickListener {
            predictImage()
        }

        sharePdfButton.setOnClickListener {
            sharePdfReport()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == IMAGE_PICK_CODE && resultCode == Activity.RESULT_OK && data != null) {
            val selectedImageUri: Uri? = data.data
            try {
                selectedImageBitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, selectedImageUri)
                imageView.setImageBitmap(selectedImageBitmap)
                resView.text = ""
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun predictImage() {
        try {
            if (selectedImageBitmap == null) {
                resView.text = "Please select an image first!"
                return
            }

            val model = XrayModel.newInstance(this)

            val resizedBitmap = Bitmap.createScaledBitmap(selectedImageBitmap!!, 224, 224, true)
            val grayscaleBitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)

            for (i in 0 until 224) {
                for (j in 0 until 224) {
                    val pixel = resizedBitmap.getPixel(i, j)
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    val gray = (0.3 * r + 0.59 * g + 0.11 * b).toInt()
                    val newPixel = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
                    grayscaleBitmap.setPixel(i, j, newPixel)
                }
            }

            val byteBuffer = ByteBuffer.allocateDirect(4 * 224 * 224)
            byteBuffer.order(ByteOrder.nativeOrder())

            for (y in 0 until 224) {
                for (x in 0 until 224) {
                    val pixel = grayscaleBitmap.getPixel(x, y)
                    val r = (pixel shr 16) and 0xFF
                    byteBuffer.putFloat(r / 255.0f)
                }
            }

            val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 224, 224, 1), DataType.FLOAT32)
            inputFeature0.loadBuffer(byteBuffer)

            val outputs = model.process(inputFeature0)
            val outputFeature0 = outputs.outputFeature0AsTensorBuffer
            val outputArray = outputFeature0.floatArray

            val predictionScore = outputArray[0]
            val predictedLabel = if (predictionScore > 0.5) "Pneumonia" else "Normal"
            val confidence = if (predictionScore > 0.5) predictionScore * 100 else (1 - predictionScore) * 100

            resView.text = "Prediction: $predictedLabel\nConfidence: ${"%.2f".format(confidence)}%"

            model.close()

        } catch (e: Exception) {
            resView.text = "Prediction Failed: ${e.message}"
            Log.e("PREDICT_ERROR", "Exception during prediction", e)
        }
    }

    private fun sharePdfReport() {
        val reportText = resView.text.toString()
        if (reportText.isEmpty() || selectedImageBitmap == null) {
            Toast.makeText(this, "No prediction to share!", Toast.LENGTH_SHORT).show()
            return
        }

        val pdfDoc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(300, 600, 1).create()
        val page = pdfDoc.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint().apply {
            var textSize = 14f
        }

        val lines = reportText.split("\n")
        var y = 50
        for (line in lines) {
            canvas.drawText(line, 10f, y.toFloat(), paint)
            y += 25
        }

        pdfDoc.finishPage(page)

        val fileName = "Xray_Report_${System.currentTimeMillis()}.pdf"

        // Save to externalFilesDir, which is allowed in file_paths.xml
        val pdfFile = File(getExternalFilesDir(null), fileName)

        try {
            FileOutputStream(pdfFile).use { pdfDoc.writeTo(it) }
            pdfDoc.close()

            val uri: Uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",  // Must match manifest
                pdfFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Share PDF Report"))

        } catch (e: Exception) {
            pdfDoc.close()
            Toast.makeText(this, "Failed to create PDF: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

}