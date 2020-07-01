package com.example.flutterapp

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import androidx.annotation.NonNull
import io.flutter.Log
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugins.GeneratedPluginRegistrant
import kotlinx.coroutines.Runnable
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.torchvision.TensorImageUtils
import java.io.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import android.opengl.ETC1.getWidth

import android.opengl.ETC1.getHeight

import android.R.attr.bitmap





class MainActivity: FlutterActivity() {
    private val CHANNEL = "samples.flutter.dev/battery"
    private val neuralNetFrameQueue = LinkedBlockingQueue<Runnable>(1);
    private val neuralNetThreadPool: ThreadPoolExecutor = ThreadPoolExecutor(
            1,       // Initial pool size
            1,       // Max pool size
            10,
            java.util.concurrent.TimeUnit.SECONDS,
            neuralNetFrameQueue);

    private lateinit var flutterChannel : MethodChannel

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        val COUNT = 5


        GeneratedPluginRegistrant.registerWith(flutterEngine);
        flutterChannel = MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), CHANNEL);

        flutterChannel.setMethodCallHandler(
                        MethodChannel.MethodCallHandler { call: MethodCall?, result: MethodChannel.Result? ->
                            if (call!!.method.equals("getPrediction")) {
                                try {
                                    neuralNetThreadPool.execute {
                                        val start = System.currentTimeMillis()
                                        val plateY: ByteArray? = call!!.argument("Y");
                                        val plateU: ByteArray? = call!!.argument("U");
                                        val plateV: ByteArray? = call!!.argument("V");
                                        val width: Int? = call!!.argument("width");
                                        val height: Int? = call!!.argument("height");
                                        Log.d("XD", "XXXXXXXXXXXXXDDDD");
                                        val yuv: ByteArray = plateY!! + plateV!! + plateU!!;
                                        val x: YuvImage = YuvImage(yuv, ImageFormat.NV21, width!!, height!!, null);
                                        Log.d("ZZZ", ""+x.getHeight())
                                        Log.d("ZZZ", ""+x.getWidth())
                                        val x1: Int;
                                        val x2: Int;
                                        val y1: Int;
                                        val y2: Int;
                                        if (width > height) {
                                            x1 = (width - height)/2;
                                            x2 = x1+height;
                                            y1 = 0;
                                            y2 = height;
                                        } else {
                                            x1 = 0;
                                            x2 = width;
                                            y1 = (height - width)/2;
                                            y2 = y1+width;
                                        }
                                        val r: Rect = Rect(x1, y1, x2, y2);
                                        Log.d("rect", ""+x1 + " " + x2 + " " + y1 + " " + y2);
                                        //val r: Rect = Rect(x1, y1, x2, y2);
                                        val bs: ByteArrayOutputStream = ByteArrayOutputStream();
                                        x.compressToJpeg(r, 100, bs);

                                        //val pathToFile : String? = call!!.argument("file");
                                        val res: Int = getBatteryLevel(bs);
                                        Log.d("CZAS", "${System.currentTimeMillis() - start}");
                                        Handler(Looper.getMainLooper()).post {
                                            flutterChannel.invokeMethod("predictionResult", mapOf("result" to res));
                                        }

                                    };
                                } catch(e: Exception){
                                    result!!.success(false);
                                }

                                result!!.success(true)
                            } else {
                                result!!.notImplemented()
                            }
                        }
                )

    }

    //private fun getBatteryLevel(pathToFile: String): Int {
    private fun getBatteryLevel(binaryStream: ByteArrayOutputStream): Int {
        var istream = ByteArrayInputStream(binaryStream.toByteArray())

//        val fis: File = File(context.getFilesDir().path, "fileIs.jpg");
//        fis.createNewFile();
//        val fos: FileOutputStream = FileOutputStream(fis);
//        fos.write(binaryStream.toByteArray());
//
//        val fs : FileInputStream = FileInputStream(File(context.getFilesDir().path, "fileIs.png"))
//        val tmpOutStream = ByteArrayOutputStream();
//        var batteryLevel = -1
//        Log.d("DIRECTORY", "${context.getFilesDir().path}");

        //var matrix = Matrix();
        //matrix.postRotate(90f);


        var bitmap = BitmapFactory.decodeStream(istream);
//        if(bitmap == null){
//            return 1;
//        }
        //bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false);
        bitmap = Bitmap.createScaledBitmap(bitmap,  400, 400, false);
        //var c = java.io.FileOutputStream(File(context.getFilesDir().path, "money.jpg"))
       // bitmap.compress(Bitmap.CompressFormat.PNG, 50, bitmap);

//        bit
//        map = bitmap.decodeStream(istream);

        //var bitmap = BitmapFactory.decodeStream(getAssets().open(pathToFile))
        val module: Module = Module.load(assetFilePath(this, "rn18cpu.pt"))
        val inputTensor: Tensor = TensorImageUtils.bitmapToFloat32Tensor(bitmap,
                floatArrayOf(0.5f, 0.5f, 0.5f), floatArrayOf(0.5f, 0.5f, 0.5f))

        val outputTensor: Tensor = module.forward(IValue.from(inputTensor)).toTensor()
        val scores: FloatArray = outputTensor.getDataAsFloatArray()
        val result: DoubleArray = DoubleArray(scores.size)
        var maxInd: Int = -1
        var max: Float = 0.0f
        for(i in 0..scores.size-1){
            if(max < scores[i]){
                max = scores[i]
                maxInd = i
            }
            result[i] = scores[i].toDouble()
        }
        val maxIdx = scores.indices.maxBy { scores[it] } ?: -1
        Log.d("Prediction", "Class: ${maxInd} ---- ${scores[maxInd]}")
        for(s in scores) {
            Log.d("class$s", "$s");
        }
            return maxIdx
    }

    @Throws(IOException::class)
    fun assetFilePath(context: Context, assetName: String?): String? {
        val file = File(context.getFilesDir(), assetName)
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath()
        }
        context.getAssets().open(assetName).use({ `is` ->
            FileOutputStream(file).use({ os ->
                val buffer = ByteArray(4 * 1024)
                var read: Int = -1
                while (`is`.read(buffer).also({ read = it }) != -1) {
                    os.write(buffer, 0, read)
                }
                os.flush()
            })
            return file.getAbsolutePath()
        })
    }

}
