package com.acash.bechdo.fragments.mainactivity

import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.fragment.app.Fragment
import com.acash.bechdo.R
import com.acash.bechdo.activities.MainActivity
import com.acash.bechdo.activities.createProgressDialog
import com.bumptech.glide.Glide
import com.google.android.gms.tasks.Continuation
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.UploadTask
import kotlinx.android.synthetic.main.fragment_edit_profile.*
import java.io.ByteArrayOutputStream

class EditProfileFragment : Fragment() {
    private val auth by lazy {
        FirebaseAuth.getInstance()
    }

    private val storage by lazy {
        FirebaseStorage.getInstance()
    }

    private val database by lazy {
        FirebaseFirestore.getInstance()
    }

    private lateinit var resultLauncher: ActivityResultLauncher<Intent>
    private lateinit var newDp: Uri
    private lateinit var newDpUrl: String
    private var isNewDpSelected = false
    private lateinit var progressDialog: ProgressDialog
    private lateinit var year: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == AppCompatActivity.RESULT_OK) {
                    result.data?.data?.let { uri ->
                        newDp = uri
                        image_view.setImageURI(uri)
                        isNewDpSelected = true
                    }
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_edit_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as MainActivity).currentUserInfo?.let { user ->

            if(::newDp.isInitialized){
                image_view.setImageURI(newDp)
            }else user.downloadUrlDp.let { url ->
                if (url != "") {
                    Glide.with(requireContext()).load(url)
                        .placeholder(R.drawable.defaultavatar)
                        .error(R.drawable.defaultavatar).into(image_view)
                }
            }

            nameEt.setText(user.name)
            dobEt.setText(user.dob)
            clgEt.setText(user.clg)
            emailEt.setText(auth.currentUser?.email)

            yearRadio.children
                .toList()
                .filter {
                    (it as RadioButton).text == user.year
                }
                .forEach {
                    (it as RadioButton).isChecked = true
                }
        }

        editPicBtn.setOnClickListener {
            checkPermissionsForImage()
        }

        saveBtn.setOnClickListener {
            year =
                (requireActivity().findViewById<RadioButton>(yearRadio.checkedRadioButtonId)).text.toString()

            if (isNewDpSelected || nameEt.text.toString() != (activity as MainActivity).currentUserInfo?.name ||
                year != (activity as MainActivity).currentUserInfo?.year
            ) {
                progressDialog =
                    requireContext().createProgressDialog("Saving Data, Please wait...", false)
                progressDialog.show()

                if (isNewDpSelected) {
                    uploadDp()
                } else {
                    updateDataOnly()
                }
            }else Toast.makeText(requireContext(),"No changes detected",Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadDp() {
        val ref = storage.reference.child("uploads/" + auth.uid.toString() + "/Dp")

        val bitmap: Bitmap = if (Build.VERSION.SDK_INT <= 28) {
            MediaStore.Images.Media.getBitmap(requireContext().contentResolver, newDp)
        } else {
            val source = ImageDecoder.createSource(requireContext().contentResolver, newDp)
            ImageDecoder.decodeBitmap(source)
        }

        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 25, baos)
        val fileInBytes = baos.toByteArray()

        val uploadTask = ref.putBytes(fileInBytes)

        uploadTask.continueWithTask(Continuation<UploadTask.TaskSnapshot, Task<Uri>> { task ->
            if (!task.isSuccessful) {
                progressDialog.dismiss()
                Toast.makeText(requireContext(), task.exception?.message, Toast.LENGTH_SHORT).show()
            }
            return@Continuation ref.downloadUrl
        }).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                newDpUrl = task.result.toString()
                updateDpNData()
            } else {
                progressDialog.dismiss()
                Toast.makeText(requireContext(), task.exception?.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateDpNData() {
        database.collection("users").document(auth.uid.toString())
            .update(
                mapOf(
                    "downloadUrlDp" to newDpUrl,
                    "name" to nameEt.text.toString(),
                    "year" to year
                )
            )
            .addOnCompleteListener { task ->

                if (task.isSuccessful) {
                    (activity as MainActivity).apply {
                        currentUserInfo?.let { user ->
                            user.downloadUrlDp = newDpUrl
                            user.name = nameEt.text.toString()
                            user.year = year
                        }
                        setDp()
                        setName()
                    }
                    isNewDpSelected = false
                    progressDialog.dismiss()

                    Toast.makeText(
                        requireContext(),
                        "User info updated successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {

                    if((activity as MainActivity).currentUserInfo?.downloadUrlDp != ""){
                        isNewDpSelected = false
                        (activity as MainActivity).setDp()
                    }

                    progressDialog.dismiss()
                    Toast.makeText(requireContext(), task.exception?.message, Toast.LENGTH_SHORT)
                        .show()
                }
            }
    }

    private fun updateDataOnly() {
        database.collection("users").document(auth.uid.toString())
            .update(
                mapOf(
                    "name" to nameEt.text.toString(),
                    "year" to year
                )
            )
            .addOnSuccessListener {
                (activity as MainActivity).apply {
                    currentUserInfo?.let { user ->
                        user.name = nameEt.text.toString()
                        user.year = year
                    }
                    setName()
                }

                progressDialog.dismiss()

                Toast.makeText(
                    requireContext(),
                    "User info updated successfully",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener {
                progressDialog.dismiss()
                Toast.makeText(requireContext(), it.message, Toast.LENGTH_SHORT).show()
            }
    }

    private fun checkPermissionsForImage() {
        if (requireContext().checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            activity?.requestPermissions(
                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                121
            )
        }

        if (requireContext().checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            activity?.requestPermissions(
                arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                131
            )
        }

        if (requireContext().checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            && requireContext().checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        ) {
            selectImageFromGallery()
        }
    }

    private fun selectImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        resultLauncher.launch(intent)
    }
}