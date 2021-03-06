package com.example.imagepickerlibrary.image_picker.ui


import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.imagepickerlibrary.R
import com.example.imagepickerlibrary.image_picker.ImagePicker
import com.example.imagepickerlibrary.image_picker.ImagePicker.EXTRA_CAMERA_SWITCH_ICON
import com.example.imagepickerlibrary.image_picker.ImagePicker.EXTRA_GALLERY_ICON
import com.example.imagepickerlibrary.image_picker.ImagePicker.EXTRA_IMAGE_PROVIDER
import com.example.imagepickerlibrary.image_picker.ProviderHelper
import com.example.imagepickerlibrary.image_picker.model.ImageProvider
import com.example.imagepickerlibrary.util.ProgressDialog
import kotlinx.coroutines.launch

/** The request code for requesting [Manifest.permission.READ_EXTERNAL_STORAGE] permission. */
private const val PERMISSIONS_REQUEST = 0x1045

internal class ImagePickerActivity : ImageProviderFragment.OnFragmentInteractionListener,
    AppCompatActivity() {
    private val providerHelper by lazy { ProviderHelper(this) }
    private lateinit var imageProvider: ImageProvider
    private lateinit var singleResult: ActivityResultLauncher<Array<String>>
    private lateinit var multipleResult: ActivityResultLauncher<Array<String>>
    private var isRegistered = false
    private var galleryIcon  = R.drawable.gallery
    private var switchCameraIcon = R.drawable.switch_camera

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_picker)

        imageProvider =
            intent?.extras?.getSerializable(ImagePicker.EXTRA_IMAGE_PROVIDER) as ImageProvider


        intent?.extras?.apply {
            galleryIcon = getInt(EXTRA_GALLERY_ICON)
            switchCameraIcon = getInt(EXTRA_CAMERA_SWITCH_ICON)
        }


        prepareGallery(ImageProvider.CAMERA)
        loadProvider(imageProvider)

    }

    private fun loadProvider(provider: ImageProvider) {
        when (provider) {
            ImageProvider.GALLERY -> {
                prepareGallery(ImageProvider.GALLERY)
            }
            ImageProvider.CAMERA -> {
                if (havePermission()) {
                    replaceFragment(ImageProviderFragment.newInstance())
                } else {
                    requestPermissions()
                }
            }
            ImageProvider.BOTH_WITH_CUSTOM -> {
                if (havePermission()) {
                    replaceFragment(ImageProviderFragment.newInstance())
                } else {
                    requestPermissions()
                }
            }
            else -> {
                finish()
            }
        }
    }

    private fun prepareGallery(provider: ImageProvider) {
        val d = if (providerHelper.isToCompress())
            ProgressDialog.showProgressDialog(this, "Compressing....", false)
        else ProgressDialog.showProgressDialog(this, "Processing....", false)

        if (!providerHelper.getMultiSelection()) {
            /**Single choice**/
            if (!isRegistered) {
                isRegistered = true
                singleResult =
                    registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
//                    if (uri == null)
                        uri?.let {
                            lifecycleScope.launch {
                                providerHelper.performGalleryOperationForSingleSelection(uri)
                            }
                        }

                    }
            }

            if (provider == ImageProvider.GALLERY)
                singleResult.launch(providerHelper.getGalleryMimeTypes())
        } else {
            /** Multiple choice**/
            if (!isRegistered) {
                isRegistered = true
                multipleResult =
                    registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                        uris?.let {
                            lifecycleScope.launch {
                                d.show()
                                val images =
                                    providerHelper.performGalleryOperationForMultipleSelection(uris)
                                d.dismiss()
                                providerHelper.setResultAndFinish(images)
                            }
                        }
                    }
            }

            if (provider == ImageProvider.GALLERY)
                multipleResult.launch(providerHelper.getGalleryMimeTypes())
        }

    }

    private fun replaceFragment(fragment: Fragment) {
        val fragmentBundle = Bundle()
        fragmentBundle.putInt(EXTRA_GALLERY_ICON, galleryIcon)
        fragmentBundle.putInt(
            EXTRA_CAMERA_SWITCH_ICON,
            switchCameraIcon
        )
        fragmentBundle.putSerializable(EXTRA_IMAGE_PROVIDER, imageProvider)
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment.javaClass, fragmentBundle)
            .commit()
    }

    /**Permission Sections**/
    private fun havePermission() =
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED) && (ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED) && (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED)
        } else {
            (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED)
        }

    /**
     * Convenience method to request [Manifest.permission.READ_EXTERNAL_STORAGE] permission.
     */
    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (!havePermission()) {
                val permissions = arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.CAMERA
                )
                ActivityCompat.requestPermissions(this, permissions, PERMISSIONS_REQUEST)
            }
        } else {
            if (!havePermission()) {
                val permissions = Manifest.permission.CAMERA

                ActivityCompat.requestPermissions(this, arrayOf(permissions), PERMISSIONS_REQUEST)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            PERMISSIONS_REQUEST -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.isNotEmpty() && havePermission()) {
                    loadProvider(imageProvider)
                } else {
                    // If we weren't granted the permission, check to see if we should show
                    // rationale for the permission.
                    showDialogToAcceptPermissions()
                }
                return
            }
        }

    }

    private val startSettingsForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (havePermission()) {
                replaceFragment(ImageProviderFragment.newInstance())
            } else finish()
        }

    private fun goToSettings() {
        val intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        startSettingsForResult.launch(intent)
    }

    private fun showDialogToAcceptPermissions() {
        showPermissionRationalDialog("You need to allow access to view and capture image")
    }

    private fun showPermissionRationalDialog(msg: String) {
        AlertDialog.Builder(this)
            .setMessage(msg)
            .setPositiveButton(
                "OK"
            ) { dialog, which ->
                goToSettings()
            }
            .setNegativeButton("Cancel") { dialog, which ->
                onBackPressed()
            }
            .create()
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        for (fragment in supportFragmentManager.fragments) {
            fragment.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onFragmentInteraction() {
        prepareGallery(ImageProvider.GALLERY)
    }
}