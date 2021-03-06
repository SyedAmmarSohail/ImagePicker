package com.example.imagepickerlibrary.image_picker.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.example.imagepickerlibrary.R
import com.example.imagepickerlibrary.image_picker.ImagePicker
import com.example.imagepickerlibrary.image_picker.ProviderHelper
import com.example.imagepickerlibrary.image_picker.model.ImageProvider
import com.example.imagepickerlibrary.util.FileUtil
import com.otaliastudios.cameraview.CameraListener
import com.otaliastudios.cameraview.CameraView
import com.otaliastudios.cameraview.FileCallback
import com.otaliastudios.cameraview.PictureResult
import com.otaliastudios.cameraview.controls.Facing
import com.otaliastudios.cameraview.controls.Mode
import com.otaliastudios.cameraview.size.SizeSelectors
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal class ImageProviderFragment : Fragment() {
    private lateinit var container: RelativeLayout
    private val providerHelper by lazy { ProviderHelper(requireActivity() as AppCompatActivity) }

    private var captureImageUri: Uri? = null

    var height = 0
    var width = 0

    var isFrontFacing = false
    var isPictureCaptured = false

    private val pref by lazy {
        requireContext().getSharedPreferences("propicker", Context.MODE_PRIVATE)
    }

    // Create time-stamped output file to hold the image
    lateinit var photoFile: File

    private var mListener: OnFragmentInteractionListener? = null


    // CameraX
    private var imageCapture: ImageCapture? = null
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var viewFinder: PreviewView
    private lateinit var cameraView: CameraView
    private var displayId: Int = -1
    private var camera: Camera? = null

    private var gallleryIcon = R.drawable.gallery
    private var cameraSwitchIcon = R.drawable.switch_camera
    private var imageProvider: ImageProvider = ImageProvider.BOTH_WITH_CUSTOM

    private val displayManager by lazy {
        requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }


    /**
     * We need a display listener for orientation changes that do not trigger a configuration
     * change, for example if we choose to override config change in manifest or for 180-degree
     * orientation changes.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) = view?.let { view ->
            if (displayId == this@ImageProviderFragment.displayId) {
                Log.d(TAG, "Rotation changed: ${view.display.rotation}")
                imageCapture?.targetRotation = view.display.rotation
            }
        } ?: Unit
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        arguments?.apply {
            gallleryIcon = getInt(ImagePicker.EXTRA_GALLERY_ICON)
            cameraSwitchIcon = getInt(ImagePicker.EXTRA_CAMERA_SWITCH_ICON)
            imageProvider = getSerializable(ImagePicker.EXTRA_IMAGE_PROVIDER) as ImageProvider

        }
        return inflater.inflate(R.layout.fragment_image_provider, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        container = view as RelativeLayout
        val frameLayout = container.findViewById<FrameLayout>(R.id.fLayout)
        val relativeMatchParent = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        )
        photoFile = FileUtil.getImageOutputDirectory(requireContext())
        /**if build version greater than lollipop, use CameraX otherwise Camera1 Api*/
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            viewFinder = PreviewView(requireContext())
            viewFinder.layoutParams = relativeMatchParent
            frameLayout.addView(viewFinder)
            viewFinder.visibility = View.VISIBLE
            viewFinder.post {
                setupCamera()
            }
        } else {
            cameraView = container.findViewById(R.id.cameraView)
            cameraView.visibility = View.VISIBLE
            setUpCameraOne()

        }
        updateCameraUI()

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Every time the orientation of device changes, update rotation for use cases
        displayManager.registerDisplayListener(displayListener, null)

    }

    private fun setUpCameraOne() {
        cameraView.mode = Mode.PICTURE
        //            if (options.getMode() === Options.Mode.Picture) {
//                camera.setAudio(Audio.OFF)
//            }
        val width = SizeSelectors.minWidth(width)
        val height = SizeSelectors.minHeight(height)
        val dimensions =
            SizeSelectors.and(width, height) // Matches sizes bigger than width X height

        val ratio = SizeSelectors.aspectRatio(
            com.otaliastudios.cameraview.size.AspectRatio.of(1, 2),
            0f
        ) // Matches 1:2 sizes.

        val ratio3 = SizeSelectors.aspectRatio(
            com.otaliastudios.cameraview.size.AspectRatio.of(2, 3),
            0f
        ) // Matches 2:3 sizes.

        val ratio2 = SizeSelectors.aspectRatio(
            com.otaliastudios.cameraview.size.AspectRatio.of(9, 16),
            0f
        ) // Matches 9:16 sizes.

        val result = SizeSelectors.or(
            SizeSelectors.and(ratio, dimensions),
            SizeSelectors.and(ratio2, dimensions),
            SizeSelectors.and(ratio3, dimensions)
        )
//        cameraView.setPictureSize(result)
        cameraView.setLifecycleOwner(this)

        cameraView.addCameraListener(object : CameraListener() {
            override fun onPictureTaken(result: PictureResult) {
                super.onPictureTaken(result)

                result.toFile(photoFile, FileCallback {
                    captureImageUri = Uri.fromFile(it)

                    captureImageUri?.let {
                        lifecycleScope.launch {
                            providerHelper.performCameraOperation(captureImageUri!!)
                        }
                    }
                })

            }
        })
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        updateCameraUI()
        updateCameraSwitchButton()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun updateCameraUI() {

        val fabCamera = container.findViewById<ImageView>(R.id.fabCamera)
        val fabCameraBg = container.findViewById<ImageView>(R.id.fabCameraBg)

        fabCamera.setOnTouchListener { v, event ->
            when (event?.action) {
                MotionEvent.ACTION_DOWN -> {
                    fabCameraBg.visibility = View.VISIBLE
                    fabCameraBg.animate().scaleX(1.2f).scaleY(1.2f).setDuration(300)
                        .setInterpolator(AccelerateDecelerateInterpolator()).start()
                    fabCamera.animate().scaleX(1.2f).scaleY(1.2f).setDuration(300)
                        .setInterpolator(AccelerateDecelerateInterpolator()).start()

                }
                MotionEvent.ACTION_UP -> {
                    fabCameraBg.visibility = View.GONE
                    fabCameraBg.animate().scaleX(1f).scaleY(1f).setDuration(300)
                        .setInterpolator(AccelerateDecelerateInterpolator()).start()
                    fabCamera.animate().scaleX(1f).scaleY(1f).setDuration(300)
                        .setInterpolator(AccelerateDecelerateInterpolator()).start()

                }
            }

            v?.onTouchEvent(event) ?: true
        }

        container.findViewById<ImageView>(R.id.fabCamera).setOnClickListener {

            /** Check build version to take picture*/
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                takePhoto(photoFile)
            } else {
                cameraView.takePicture()
            }
        }
        val flipCamera = container.findViewById<ImageView>(R.id.flipCamera)
        flipCamera.setImageResource(cameraSwitchIcon)
        flipCamera.setOnClickListener {
            /** Check build version to flip camera*/
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                flipCamera()
            else
                flipCameraOne()
        }

        /** For zoom camera*/
/*        container.findViewById<SeekBar>(R.id.zoomSeekBar)
            .setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    camera!!.cameraControl.setLinearZoom(progress / 100.toFloat())
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })*/
        val btnGallery = container.findViewById<ImageView>(R.id.btnGallery)

        if (imageProvider == ImageProvider.CAMERA) {
            btnGallery.visibility = View.GONE
        } else {
            btnGallery?.setImageResource(gallleryIcon)
            btnGallery?.setOnClickListener {
                if (mListener != null) {
                    mListener!!.onFragmentInteraction()
                }
            }
        }

    }

    private fun flipCameraOne() {
        if (isFrontFacing) {
            isFrontFacing = false
            cameraView.facing = Facing.BACK
        } else {
            isFrontFacing = true
            cameraView.facing = Facing.FRONT
        }
    }

    private fun takePhoto(photoFile: File) {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Setup image capture metadata
        val metadata = ImageCapture.Metadata().apply {

            // Mirror image when using the front camera
            isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
        }
        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
            .setMetadata(metadata)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {

                    captureImageUri = Uri.fromFile(photoFile)

                    captureImageUri?.let {
                        lifecycleScope.launch {
                            providerHelper.performCameraOperation(captureImageUri!!)
                        }
                    }
                }
            })
    }

    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            cameraProvider = cameraProviderFuture.get()

            // Select lensFacing depending on the available cameras
            lensFacing = when {
                hasBackCamera() -> CameraSelector.LENS_FACING_BACK
                hasFrontCamera() -> CameraSelector.LENS_FACING_FRONT
                else -> throw IllegalStateException("Back and front camera are unavailable")
            }

            // Enable or disable switching between cameras
            updateCameraSwitchButton()

            // Build and bind the camera use cases
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(requireContext()))

        val scaleGestureDetector = ScaleGestureDetector(requireContext(), pinchZoomListener)

        viewFinder.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            return@setOnTouchListener true
        }
    }

    private val pinchZoomListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val currentZoomRatio = camera!!.cameraInfo.zoomState.value?.zoomRatio ?: 0F
            val delta = detector.scaleFactor
            camera!!.cameraControl.setZoomRatio(currentZoomRatio * delta)
            return true
        }
    }

    private var orientationEventListener: OrientationEventListener? = null

    private fun bindCameraUseCases() {
        // Get screen metrics used to setup camera for full screen resolution
        val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
        Log.d(TAG, "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")

        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
        Log.d(TAG, "Preview aspect ratio: $screenAspectRatio")

        val rotation = viewFinder.display.rotation


        // Preview
        val preview = Preview.Builder()
            // We request aspect ratio but no resolution
            .setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation
            .setTargetRotation(rotation)
            .build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetAspectRatio(screenAspectRatio)
            .build()

        orientationEventListener = object : OrientationEventListener(requireContext()) {
            override fun onOrientationChanged(orientation: Int) {
                // Monitors orientation values to determine the target rotation value
                val rotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }

                try {
                    if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                        if (rotation == Surface.ROTATION_0)
                            pref.edit().putBoolean("front_camera_vertical", true).apply()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                imageCapture?.targetRotation = rotation
            }
        }
        orientationEventListener?.enable()


        // Select back camera as a default
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        try {
            // Unbind use cases before rebinding
            cameraProvider.unbindAll()

            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageCapture
            )

            camera?.let {
                initFlash()
            }

        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    /** Returns true if the device has an available back camera. False otherwise */
    private fun hasBackCamera() = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)

    /** Returns true if the device has an available front camera. False otherwise */
    private fun hasFrontCamera() = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)

    /** Enabled or disabled a button to switch cameras depending on the available cameras */
    private fun updateCameraSwitchButton() {
        val switchCamerasButton = view?.findViewById<ImageView>(R.id.flipCamera)
        try {
            switchCamerasButton?.isEnabled = hasBackCamera() && hasFrontCamera()
        } catch (exception: CameraInfoUnavailableException) {
            switchCamerasButton?.isEnabled = false
        }
    }

    private fun flipCamera() {
        lensFacing = if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
        // Re-bind use cases to update selected camera
        bindCameraUseCases()
    }

    private fun initFlash() {
        val btnFlash = view?.findViewById<ImageView>(R.id.btnFlash)

        /**Check build version for flash*/
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (camera!!.cameraInfo.hasFlashUnit()) {
//            btnFlash?.visibility = View.VISIBLE

                btnFlash?.setOnClickListener {
                    camera!!.cameraControl.enableTorch(camera!!.cameraInfo.torchState.value == TorchState.OFF)
                }
            } else btnFlash?.visibility = View.GONE

            camera!!.cameraInfo.torchState.observe(viewLifecycleOwner, Observer { torchState ->
                if (torchState == TorchState.OFF) {
                    btnFlash?.setImageResource(R.drawable.ic_baseline_flash_on_24)
                } else {
                    btnFlash?.setImageResource(R.drawable.ic_baseline_flash_off_24)
                }
            })
        }

    }

    /**
     *  [androidx.camera.core.ImageAnalysisConfig] requires enum value of
     *  [androidx.camera.core.AspectRatio]. Currently it has values of 4:3 & 16:9.
     *
     *  Detecting the most suitable ratio for dimensions provided in @params by counting absolute
     *  of preview ratio to one of the provided values.
     *
     *  @param width - preview width
     *  @param height - preview height
     *  @return suitable aspect ratio
     */
    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    // For Ucrop Result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        lifecycleScope.launch {
            providerHelper.handleUCropResult(requestCode, resultCode, data, captureImageUri)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        displayManager.unregisterDisplayListener(displayListener)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            cameraView.destroy()
    }

    override fun onStop() {
        super.onStop()
        orientationEventListener?.disable()
        orientationEventListener = null
    }

    companion object {
        @JvmStatic
        fun newInstance() = ImageProviderFragment()

        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0

        private const val TAG = "ImageProviderFragment"
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFragmentInteractionListener) {
            mListener = context
        } else {
            throw RuntimeException(requireContext().toString() + " must implement OnFragmentInteractionListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        mListener = null
    }

    interface OnFragmentInteractionListener {
        fun onFragmentInteraction()
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            cameraView.open()
        }
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            cameraView.close()
        }
    }

    fun getScreenSize(activity: Activity) {
        val displayMetrics = DisplayMetrics()
        activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
        height = displayMetrics.heightPixels
        width = displayMetrics.widthPixels
    }

}

