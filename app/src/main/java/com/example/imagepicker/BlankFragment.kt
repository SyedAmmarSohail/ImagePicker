package com.example.imagepicker

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.imagepickerlibrary.image_picker.ImagePicker


class BlankFragment : Fragment() {


    private lateinit var imageBuilder: ImagePicker.Builder

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment

        imageBuilder = ImagePicker.with(this)
            .setCompletionHandler { resultCode, data ->
            if (resultCode == AppCompatActivity.RESULT_OK && data != null) {
                val picker = ImagePicker.getPickerData(data)
                view?.findViewById<ImageView>(R.id.iv)?.setImageURI(picker?.uri)
            }
        }
            .bothWithCustom()
            .cropOval()
            .setGalleryIcon(R.drawable.gallery)
            .setCameraSwitchIcon(R.drawable.switch_camera)


        return inflater.inflate(R.layout.fragment_blank, container, false)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.btnShowCameraOnlyWithCrop)?.setOnClickListener {
            imageBuilder.start()
        }
    }
}

