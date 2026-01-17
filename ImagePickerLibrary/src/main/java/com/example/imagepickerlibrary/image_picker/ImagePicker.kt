package com.example.imagepickerlibrary.image_picker


import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.imagepickerlibrary.R
import com.example.imagepickerlibrary.image_picker.model.Picker
import com.example.imagepickerlibrary.image_picker.model.ImageProvider
import com.example.imagepickerlibrary.image_picker.ui.ImagePickerActivity

object ImagePicker {
    internal const val EXTRA_MIME_TYPES = "extra.mime_types"
    internal const val EXTRA_IMAGE_PROVIDER = "extra.image_provider"
    internal const val EXTRA_MULTI_SELECTION = "extra.multi_selection"
    internal const val EXTRA_SELECTED_IMAGES = "extra.selected_images"
    internal const val EXTRA_IMAGE_MAX_SIZE = "extra.image_max_size"
    internal const val EXTRA_CROP = "extra.crop"
    internal const val EXTRA_CROP_OVAL = "extra.crop_oval"
    internal const val EXTRA_CROP_X = "extra.crop_x"
    internal const val EXTRA_CROP_Y = "extra.crop_y"
    internal const val EXTRA_MAX_WIDTH = "extra.max_width"
    internal const val EXTRA_MAX_HEIGHT = "extra.max_height"
    internal const val EXTRA_IS_TO_COMPRESS = "extra._is_to_compress"
    internal const val EXTRA_GALLERY_ICON = "extra.gallery.icon"
    internal const val EXTRA_CAMERA_SWITCH_ICON = "extra.camera_switch.icon"

    fun with(
        fragment: Fragment
    ): Builder {
        return Builder(fragment)
    }

    /**
     * Get all the selected images
     * @param intent
     * */
    @JvmStatic
    fun getSelectedPickerDatas(intent: Intent) =
        intent.getParcelableArrayListExtra<Picker>(EXTRA_SELECTED_IMAGES) ?: ArrayList()

    /**
     * Get all the selected images
     * @param intent
     * */
    @JvmStatic
    fun getPickerData(intent: Intent): Picker? {
        val images = getSelectedPickerDatas(intent)
        return if (images.isNotEmpty()) images[0] else null
    }


    class Builder(val context: Context) {
        constructor(fragment: Fragment) : this(fragment.requireContext()) {
            fragmentLauncher =
                fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    completionHandler.invoke(result.resultCode, result.data)
                }
        }

        private var imageProvider = ImageProvider.BOTH

        private var galleryIcon = R.drawable.gallery
        private var cameraSwitchIcon = R.drawable.switch_camera

        // Mime types restrictions for gallery. by default all mime types are valid
        private var mimeTypes: Array<String> = arrayOf("image/png", "image/jpeg", "image/jpg")

        private lateinit var fragmentLauncher: ActivityResultLauncher<Intent>
        private lateinit var completionHandler: ((resultCode: Int, data: Intent?) -> Unit)

        /*
        * Crop Parameters
        */
        private var cropX: Float = 0f
        private var cropY: Float = 0f
        private var crop: Boolean = false
        private var cropOval: Boolean = false

        // Compress
        private var isToCompress: Boolean = false

        /*
        * Resize Parameters
        */
        private var maxWidth: Int = 0
        private var maxHeight: Int = 0

        // Image selection length
        private var isMultiSelection = false

        /**
         * Max File Size
         */
        private var maxSize: Long = 0

        fun bothWithCustom(): Builder {
            this.imageProvider = ImageProvider.BOTH_WITH_CUSTOM
            return this
        }

        fun setGalleryIcon(galleryIcon: Int): Builder {
            this.galleryIcon = galleryIcon
            return this
        }

        fun setCameraSwitchIcon(cameraSwitchIcon: Int): Builder {
            this.cameraSwitchIcon = cameraSwitchIcon
            return this
        }

        fun setCompletionHandler(handler: ((resultCode: Int, data: Intent?) -> Unit)): Builder {
            completionHandler = handler
            return this
        }

        /**
         * Crop an image and let user set the aspect ratio.
         */
        fun crop(): Builder {
            this.crop = true
            return this
        }


        /**
         * Only Capture image using Camera.
         */
        fun cameraOnly(): Builder {
            this.imageProvider = ImageProvider.CAMERA
            return this
        }


        fun crop(x: Float, y: Float): Builder {
            cropX = x
            cropY = y
            return crop()
        }

        fun compressImage(maxWidth: Int = 612, maxHeight: Int = 816): Builder {
            if (maxHeight > 10 && maxWidth > 10) {
                this.maxWidth = maxWidth
                this.maxHeight = maxHeight
            }

            isToCompress = true
            return this
        }

        /**
         * Allow dimmed layer to have a circle inside
         */
        fun cropOval(): Builder {
            this.cropOval = true
            return crop()
        }

        /**
         * Start Image Picker Activity
         */
        fun start() {
            if (imageProvider == ImageProvider.BOTH) {
                // Pick Image Provider if not specified
                showImageProviderDialog()
            } else {
                startActivity()
            }
        }

        private fun showImageProviderDialog() {
            val v = View.inflate(context, R.layout.dialog_image_picker_chooser, null)

            val d = Dialog(context, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            d.setContentView(v)

            v.findViewById<TextView>(R.id.btnCamera).setOnClickListener {
                imageProvider = ImageProvider.CAMERA
                start()
                d.dismiss()
            }

            v.findViewById<TextView>(R.id.btnGallery).setOnClickListener {
                imageProvider = ImageProvider.GALLERY
                start()
                d.dismiss()
            }

            d.show()

        }


        /**
         * Start ImagePickerActivity with given Argument
         */
        private fun startActivity() {
            val intent = Intent(context, ImagePickerActivity::class.java)
            intent.putExtras(getBundle())
            fragmentLauncher.launch(intent)
        }

        /**
         * Get Bundle for ProImagePickerActivity
         */
        private fun getBundle(): Bundle {
            return Bundle().apply {
                putSerializable(EXTRA_IMAGE_PROVIDER, imageProvider)
                putStringArray(EXTRA_MIME_TYPES, mimeTypes)
                putBoolean(EXTRA_MULTI_SELECTION, isMultiSelection)

                putBoolean(EXTRA_CROP, crop)
                putBoolean(EXTRA_CROP_OVAL, cropOval)
                putBoolean(EXTRA_IS_TO_COMPRESS, isToCompress)

                putLong(EXTRA_IMAGE_MAX_SIZE, maxSize)
                putFloat(EXTRA_CROP_X, cropX)
                putFloat(EXTRA_CROP_Y, cropY)
                putInt(EXTRA_MAX_WIDTH, maxWidth)
                putInt(EXTRA_MAX_HEIGHT, maxHeight)
                putInt(EXTRA_GALLERY_ICON, galleryIcon)
                putInt(EXTRA_CAMERA_SWITCH_ICON, cameraSwitchIcon)

            }
        }

    }

}