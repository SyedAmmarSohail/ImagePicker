package com.example.imagepicker

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.example.imagepickerlibrary.image_picker.ImagePicker

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

//        findViewById<TextView>(R.id.btnShowCameraOnlyWithCrop).setOnClickListener {
//            ImagePicker.with(this)
//                .bothWithCustom()
////                .maxResultSize(200, 200)
////                .compressImage(20, 20)
//                .cropOval()
//                .setGalleryIcon(R.drawable.gallery)
//                .setCameraSwitchIcon(R.drawable.switch_camera)
//                .start { resultCode, data ->
//                    if (resultCode == RESULT_OK && data != null) {
//                        val picker = ImagePicker.getPickerData(data)
//
//                        findViewById<ImageView>(R.id.iv).setImageURI(picker?.uri)
//
//                    }
//                }
//        }
    }
}