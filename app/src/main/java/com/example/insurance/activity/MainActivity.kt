package com.example.insurance.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import com.example.insurance.ui.theme.InsuranceTheme
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class MainActivity : ComponentActivity() {
    private var mTess //Tess API reference
            : TessBaseAPI? = null
    var datapath = "$filesDir/tesseract/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkFile(File(datapath + "tessdata/"))
        //이미지 디코딩을 위한 초기화
        //언어파일 경로

        //트레이닝데이터가 카피되어 있는지 체크
        checkFile(File(datapath + "tessdata/"))

        //Tesseract API 언어 세팅
        val lang = "kor"

        //OCR 세팅
        mTess = TessBaseAPI()
        mTess!!.init(datapath, lang)

        setContent {
            InsuranceTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Frame(context = this) {

                    }
                }
            }
        }
    }

    private val langFileName = "kor.traineddata"
    private fun copyFiles() {
        try {
            val filepath = datapath + "tessdata/" + langFileName
            val assetManager = assets
            val instream: InputStream = assetManager.open(langFileName)
            val outstream: OutputStream = FileOutputStream(filepath)
            val buffer = ByteArray(1024)
            var read: Int
            while (instream.read(buffer).also { read = it } != -1) {
                outstream.write(buffer, 0, read)
            }
            outstream.flush()
            outstream.close()
            instream.close()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun checkFile(dir: File) {
        //디렉토리가 없으면 디렉토리를 만들고 그후에 파일을 카피
        if (!dir.exists() && dir.mkdirs()) {
            copyFiles()
        }
        //디렉토리가 있지만 파일이 없으면 파일카피 진행
        if (dir.exists()) {
            val datafilepath = datapath + "tessdata/" + langFileName
            val datafile = File(datafilepath)
            if (!datafile.exists()) {
                copyFiles()
            }
        }
    }

    @Composable
    fun Frame(context: Context, onImageChanged: ((Bitmap) -> Unit)) {
        val bitmap = remember {
            mutableStateOf<Bitmap?>(null)
        }

        val text = remember {
            mutableStateOf<String>("")
        }

        Box {
            TranslationImage(bitmap = bitmap.value)
            TranslationText(text = text.value)
            ImageColumn(context) {
                bitmap.value = it
                onImageChanged(it)
                mTess!!.setImage(it)
                text.value = mTess!!.utF8Text
            }
        }
    }

    @Composable
    fun TranslationImage(bitmap: Bitmap?) {
        if (bitmap != null)
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = "")
    }

    @Composable
    fun TranslationText(text: String) {
        Text(text = text)
    }

    @Composable
    fun ClickableButton(txt: String, onClick: (() -> Unit)) {
        Button(onClick = { onClick() }) {
            Text(text = txt)
        }
    }

    @Composable
    fun ImageColumn(context: Context, onPhotoChanged: ((Bitmap) -> Unit)) {
        val takePhotoFromAlbumIntent =
            Intent(Intent.ACTION_GET_CONTENT, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                type = "image/*"
                action = Intent.ACTION_GET_CONTENT
                putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    arrayOf("image/jpeg", "image/png", "image/bmp", "image/webp")
                )
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
            }

        val takePhotoFromCameraLauncher = // 카메라로 사진 찍어서 가져오기
            rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { takenPhoto ->
                if (takenPhoto != null) {
                    onPhotoChanged(takenPhoto)
                } else {
                    Toast.makeText(context, "img not found", Toast.LENGTH_LONG).show()
                }
            }

        val takePhotoFromAlbumLauncher = // 갤러리에서 사진 가져오기
            rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    result.data?.data?.let { uri ->
                        onPhotoChanged(uri.parseBitmap(context))
                    } ?: run {
                        Toast.makeText(context, "img not found", Toast.LENGTH_LONG).show()
                    }
                } else if (result.resultCode != Activity.RESULT_CANCELED) {
                    Toast.makeText(context, "img not found", Toast.LENGTH_LONG).show()
                }
            }
        Column {
            ClickableButton(txt = "Camera") {
                takePhotoFromCameraLauncher.launch()
            }

            ClickableButton(txt = "Gallery") {
                takePhotoFromAlbumLauncher.launch(takePhotoFromAlbumIntent)
            }
        }
    }

    @SuppressLint("NewApi")
    private fun Uri.parseBitmap(context: Context): Bitmap {
        return when (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // 28
            true -> {
                val source = ImageDecoder.createSource(context.contentResolver, this)
                ImageDecoder.decodeBitmap(source)
            }

            else -> {
                MediaStore.Images.Media.getBitmap(context.contentResolver, this)
            }
        }
    }
}