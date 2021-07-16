package de.westnordost.streetcomplete.quests.note_discussion

import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.westnordost.streetcomplete.ApplicationConstants.ATTACH_PHOTO_MAXWIDTH
import de.westnordost.streetcomplete.ApplicationConstants.ATTACH_PHOTO_MAXHEIGHT
import de.westnordost.streetcomplete.ApplicationConstants.ATTACH_PHOTO_QUALITY
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.osmnotes.deleteImages
import de.westnordost.streetcomplete.databinding.FragmentAttachPhotoBinding
import de.westnordost.streetcomplete.ktx.toast
import de.westnordost.streetcomplete.ktx.viewBinding
import de.westnordost.streetcomplete.util.AdapterDataChangedWatcher
import de.westnordost.streetcomplete.util.decodeScaledBitmapAndNormalize
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class AttachPhotoFragment : Fragment() {

    val imagePaths: List<String> get() = noteImageAdapter.list
    private var photosListView : RecyclerView? = null
    private var hintView : TextView? = null

    private val binding by viewBinding(FragmentAttachPhotoBinding::bind)

    private var currentImagePath: String? = null

    private lateinit var noteImageAdapter: NoteImageAdapter

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture(), ::onTakePhotoResult)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_attach_photo, container, false)

        // see #1768: Android KitKat and below do not recognize letsencrypt certificates
        val isPreLollipop = Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
        val hasCamera = requireActivity().packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        if (isPreLollipop || !hasCamera) {
            view.visibility = View.GONE
        }
        photosListView = view.findViewById(R.id.gridView)
        hintView = view.findViewById(R.id.photosAreUsefulExplanation)
        return view
    }

    private fun updateHintVisibility(){
        val isImagePathsEmpty = imagePaths.isEmpty()
        photosListView?.isGone = isImagePathsEmpty
        hintView?.isGone = !isImagePathsEmpty
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.takePhotoButton.setOnClickListener { takePhoto() }

        val paths: ArrayList<String>
        if (savedInstanceState != null) {
            paths = savedInstanceState.getStringArrayList(PHOTO_PATHS)!!
            currentImagePath = savedInstanceState.getString(CURRENT_PHOTO_PATH)
        } else {
            paths = ArrayList()
            currentImagePath = null
        }

        noteImageAdapter = NoteImageAdapter(paths, requireContext())
        binding.gridView.layoutManager = LinearLayoutManager(
            context,
            LinearLayoutManager.HORIZONTAL,
            false
        )
        binding.gridView.adapter = noteImageAdapter
        noteImageAdapter.registerAdapterDataObserver(AdapterDataChangedWatcher { updateHintVisibility() })
        updateHintVisibility()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(PHOTO_PATHS, ArrayList(imagePaths))
        outState.putString(CURRENT_PHOTO_PATH, currentImagePath)
    }

    private fun takePhoto() {
        try {
            val photoFile = createImageFile()
            val photoUri = if (Build.VERSION.SDK_INT > 21) {
                // Use FileProvider for getting the content:// URI, see:
                // https://developer.android.com/training/camera/photobasics.html#TaskPath
                FileProvider.getUriForFile(requireContext(), getString(R.string.fileprovider_authority), photoFile)
            } else {
                photoFile.toUri()
            }
            currentImagePath = photoFile.path
            takePhoto.launch(photoUri)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Could not find a camera app", e)
            context?.toast(R.string.no_camera_app)
        } catch (e: IOException) {
            Log.e(TAG, "Unable to create file for photo", e)
            context?.toast(R.string.quest_leave_new_note_create_image_error)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Unable to create file for photo", e)
            context?.toast(R.string.quest_leave_new_note_create_image_error)
        }
    }

    private fun onTakePhotoResult(saved: Boolean) {
        if (saved) {
            try {
                val path = currentImagePath!!
                val bitmap = decodeScaledBitmapAndNormalize(path, ATTACH_PHOTO_MAXWIDTH, ATTACH_PHOTO_MAXHEIGHT) ?: throw IOException()
                val out = FileOutputStream(path)
                bitmap.compress(Bitmap.CompressFormat.JPEG, ATTACH_PHOTO_QUALITY, out)

                noteImageAdapter.list.add(path)
                noteImageAdapter.notifyItemInserted(imagePaths.size - 1)
            } catch (e: IOException) {
                Log.e(TAG, "Unable to rescale the photo", e)
                context?.toast(R.string.quest_leave_new_note_create_image_error)
                removeCurrentImage()
            }
        } else {
            removeCurrentImage()
        }
        currentImagePath = null
    }

    private fun removeCurrentImage() {
        currentImagePath?.let {
            val photoFile = File(it)
            if (photoFile.exists()) {
                photoFile.delete()
            }
        }
    }

    private fun createImageFile(): File {
        val directory = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val imageFileName = "photo_" + System.currentTimeMillis() + ".jpg"
        val file = File(directory, imageFileName)
        if(!file.createNewFile()) throw IOException("Photo file with exactly the same name already exists")
        return file
    }

    fun deleteImages() {
        deleteImages(imagePaths)
    }

    companion object {

        private const val TAG = "AttachPhotoFragment"

        private const val PHOTO_PATHS = "photo_paths"
        private const val CURRENT_PHOTO_PATH = "current_photo_path"
    }
}
