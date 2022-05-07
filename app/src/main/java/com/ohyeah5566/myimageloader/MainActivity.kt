package com.ohyeah5566.myimageloader

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.ohyeah5566.myimageloader.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext


const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.imageView.setOnClickListener {
            binding.imageView.setImageResource(0)
        }

        //從url取得 inputStream 並減少圖片大小 顯示在imageView上
        //76.14844MB -> ~10MB
        binding.loadImageManually.setOnClickListener {
            lifecycleScope.launch() {
                binding.imageView.setImageBitmap(loadImageWithReducePixel(binding.imageView).await())
            }
        }
        binding.loadImageGlide.setOnClickListener {
            //透過addListener RequestListener
            //2.9663086 MB
            Glide.with(binding.imageView)
                .asBitmap()
                .load("https://images.pexels.com/photos/842711/pexels-photo-842711.jpeg")
                .into(binding.imageView)
        }

//      讀取在app scope internal的檔案 不需要權限
        binding.loadImageInternal.setOnClickListener {
            val fileName = "internalImg.jpg"
            val file = File(filesDir, fileName)
            if (!file.exists()) return@setOnClickListener
            //closeable.use
            openFileInput(fileName).use {
                Glide.with(this@MainActivity)
                    .load(it.readBytes())
                    .into(binding.imageView)
            }
        }
//      讀取App scope external 不需要權限
        binding.loadImageExternal.setOnClickListener {
            val file = File(externalMediaDirs?.first(), "externalImg.jpg")
            val file2 = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "externalImgP.jpg")
            Glide.with(this)
                .load(file)
                .into(binding.imageView)
        }

/**     read file from public picture dir
 *      如果file是自己app提供的 不用權限
 *      如果要讀取其他app的 要READ_EXTERNAL_STORAGE */
        binding.loadImagePic.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                readPublicPictureFileByContentResolver(binding.imageView)
            } else {
                readPublicPictureFileByEnvironment(binding.imageView)
            }
        }

        /**
         * 將圖片 儲存到internal空間
         */
        binding.downloadBtnInternal.setOnClickListener {
            //https://developer.android.com/training/data-storage
            lifecycleScope.launch(Dispatchers.IO) {
                val fileName = "internalImg.jpg"
                val fileBytesArray = getBytes()
//                val file = File(filesDir,"internalImg.jpg") 比較陽春的方法
                openFileOutput(fileName, Context.MODE_PRIVATE).use {
                    it.write(fileBytesArray)
                }
            }
        }

        /**
         * 將圖片 儲存到app scope external空間
         * clear data or uninstall 資料都會消失
         */
        binding.downloadBtnExternal.setOnClickListener {
            //https://developer.android.com/training/data-storage
            lifecycleScope.launch(Dispatchers.IO) {
                //file  Path:storage/sdcard/Android/media/$packageName/$fileName
                val file = File(externalMediaDirs?.first(), "externalImg.jpg")
                //file2 Path:storage/sdcard/Android/data/$packageName/files/Pictures  Environment.DIRECTORY_PICTURES
                val file2 = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "externalImgP.jpg")

                val bytes = getBytes()
                file.save(bytes)
                file2.save(bytes)
            }
        }

        /**
         * 將file儲存到永久共享的空間(shared-storage)
         * Q 以下需要WRITE_EXTERNAL_PERMISSION
         */
        binding.downloadBtnPicDirPublic.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveFileToPublicPictureDirQ()
                } else {
                    saveFileToPublicPictureDirBelowQ()
                }
            }
        }
    }

    /**
     * Android Q以上 用contentResolver的方式inert到 public picture dir
     * 不用WRITE_EXTERNAL_PERMISSION
     * clear data or uninstall 資訊依然存在
     */
    //sdcard/Pictures/$filename
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveFileToPublicPictureDirQ() {
        val resolver = contentResolver
        val contentValues = ContentValues()
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "imageLoader.jpg")
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
        contentValues.put(
            MediaStore.MediaColumns.RELATIVE_PATH,
            Environment.DIRECTORY_PICTURES
        )
        val imageUri: Uri? =
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        resolver.openOutputStream(imageUri!!).use{
            it?.write(getBytes())
        }
    }


    /**
     * Android Q以下的儲存方式
     * 需要 WRITE_EXTERNAL_PERMISSION
     */
    private fun saveFileToPublicPictureDirBelowQ(){
        //Path:storage/sdcard/Pictures/$fileName
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "externalImgP.jpg"
        )
        file.save(getBytes())
    }

    /**
     * 取得inputStream
     * 不做任何處理 就decode成bitmap
     */
    fun loadImageWithoutHandle() = lifecycleScope.async(Dispatchers.IO) {
        Log.d(TAG, "current thread, $this")
        val url = URL("https://images.pexels.com/photos/842711/pexels-photo-842711.jpeg")
        val httpURLConnection = url.openConnection() as HttpURLConnection
        Log.d(TAG, "response code:${httpURLConnection.responseCode}")
        val inputStream = httpURLConnection.inputStream
        val bitmap = BitmapFactory.decodeStream(inputStream)
        httpURLConnection.disconnect()
        Log.d(TAG, "${byteToMB(bitmap.byteCount)} MB") //~64MB
        bitmap
    }


    /**
     * write file
     */
    fun File.save(byteArray:ByteArray){
        try{
            createNewFile()
        } catch (ex:Exception){
        }
        val fots = FileOutputStream(this)
        fots.write(byteArray)
        fots.close()
    }

    /**
     * 先取得圖片的長寬 再看imageView的長寬
     * 算出可以減少多少比例 再給inSampleSize
     * 因為inputStream只能decode一次
     * 第二次decode會得到null
     * 所以這邊用 inputStream.readBytes()
     */
    fun loadImageWithReducePixel(imageView: ImageView) = lifecycleScope.async(Dispatchers.IO) {
        Log.d(TAG, "current thread, $this")
        //原圖尺寸 5472×3648
        val url = URL("https://images.pexels.com/photos/842711/pexels-photo-842711.jpeg")
        val httpURLConnection = url.openConnection() as HttpURLConnection

        Log.d(TAG, "response code:${httpURLConnection.responseCode}")
        httpURLConnection.requestMethod = "GET"
        val inputStream = httpURLConnection.inputStream
        val bytesArray = inputStream.readBytes()
        httpURLConnection.disconnect()

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true //只拿資訊,不產生bitmap,避免無謂的記憶體佔用
        }
        BitmapFactory.decodeByteArray(bytesArray, 0, bytesArray.size, options)
        val srcHeight: Int = options.outHeight
        val srcWidth: Int = options.outWidth
        val reqHeight = imageView.height
        val reqWidth = imageView.width

        options.apply {
            inSampleSize = getSimpleSize(srcHeight, srcWidth, reqHeight, reqWidth)
            inPreferredConfig = Bitmap.Config.RGB_565
            inJustDecodeBounds = false
        }

        val bitmap = BitmapFactory.decodeByteArray(bytesArray, 0, bytesArray.size, options)
        Log.d(TAG, "${byteToMB(bitmap.byteCount)} MB") //~10MB
        bitmap
    }

    fun getBytes(): ByteArray {
        Log.d(TAG, "current thread, $this")
        val url = URL("https://images.pexels.com/photos/842711/pexels-photo-842711.jpeg")
        val httpURLConnection = url.openConnection() as HttpURLConnection

        Log.d(TAG, "response code:${httpURLConnection.responseCode}")
        httpURLConnection.requestMethod = "GET"
        val inputStream = httpURLConnection.inputStream
        val bytesArray = inputStream.readBytes()
        httpURLConnection.disconnect()
        return bytesArray
    }

    /**
     * android Q以上
     * 透過contentResolver取得share scope external 圖片
     * 在沒給權限的情況下 可以讀到自己寫的file 不過重新安裝後 就沒辦法了
     */
    private fun readPublicPictureFileByContentResolver(imageView: ImageView) {
        val resolver = contentResolver
//queryArgs = queryArgs1+selectionArgs
        val queryArgs = MediaStore.Images.Media.DISPLAY_NAME + "='imageLoader.jpg'"
        val queryArgs1 = MediaStore.Images.Media.DISPLAY_NAME + "=?"
        val selectionArgs = arrayOf("imageLoader.jpg")
        val projection = arrayOf(
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.MIME_TYPE
        )
        val cursor = resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, //返回資料的內容 null=全拿,拿projection以外的回得到null
            queryArgs1,  //query string
            selectionArgs, //取代queryArgs的?參數的
            null //排序
        )
        while (cursor?.moveToNext() == true) {
            val columnIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID)
            val imageId = cursor.getString(columnIndex)
            val uriImage =
                Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageId)
            Glide.with(imageView)
                .load(uriImage)
                .into(imageView)
            Log.d(TAG, "$uriImage")
        }
        cursor?.close()
    }

    /**
     * android Q以下
     * 透過Environment取得圖片
     * 如果沒給權限 想讀取不同app存放的資料會出現 open failed: EACCES (Permission denied)
     *            可以讀取自己寫出的file 不過重新安裝後 就沒辦法了
     */
    private fun readPublicPictureFileByEnvironment(imageView: ImageView) {
        val fileName = "externalImgP.jpg"
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            fileName
        )
        Glide.with(imageView)
            .load(file)
            .into(imageView)
    }

    //req 1440x1440
    //src 5472×3648
    fun getSimpleSize(srcHeight: Int, srcWidth: Int, reqHeight: Int, reqWidth: Int): Int {
        return 3
    }

    private fun byteToMB(byteCount: Int): Float {
        return byteCount / (1024f * 1024f)
    }
}