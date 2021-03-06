package edu.itq.antwort.Activities

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.ClipData
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.google.android.material.chip.Chip
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.picasso.Picasso
import de.hdodenhof.circleimageview.CircleImageView
import edu.itq.antwort.Methods
import edu.itq.antwort.R
import edu.itq.antwort.databinding.ActivityNewQuestionBinding
import android.text.Editable
import android.util.Log
import android.view.KeyEvent
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.net.toFile
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.gson.Gson
import edu.itq.antwort.Classes.NotificationData
import edu.itq.antwort.Classes.PushNotification
import edu.itq.antwort.Classes.RetrofitInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.LinearLayoutManager
import edu.itq.antwort.Adapters.FileAdapter
import java.io.File
import java.nio.file.Files
import android.webkit.MimeTypeMap

import android.content.ContentResolver





class QuestionScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewQuestionBinding
    private val db = FirebaseFirestore.getInstance()
    private var topicsList : MutableList<CharSequence> = mutableListOf()
    val id: String = db.collection("Questions").document().id

    lateinit var storage : StorageReference
    val fileBack = 1
    private var fileData : Uri? = null
    private var clipData : ClipData? = null
    private var dataUri : ArrayList<Uri> = ArrayList()
    private var ogFileNames : ArrayList <String> = ArrayList()

    private lateinit var fileAdapter : FileAdapter

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        
        binding = ActivityNewQuestionBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        val bundle = intent.extras
        val email = bundle?.getString("email")
        storage = FirebaseStorage.getInstance().reference.child("Files/$id")

        supportActionBar?.hide()


        // setup
        setup(email!!)

        db.collection("Users").document(email).get().addOnSuccessListener {

            postQuestion(email, it.get("name") as String, it.get("topics") as MutableList<CharSequence>)

        }//obtenemos el nombre del usuario

        binding.etNewQuestionTopics.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {}
            override fun beforeTextChanged(
                s: CharSequence,
                start: Int,
                count: Int,
                after: Int ) {

            }
            override fun onTextChanged(
                s: CharSequence, start: Int,
                before: Int, count: Int
            ) {
                if(s.endsWith(' ') && (s.isNotBlank())){
                    addTag(s)
                    binding.etNewQuestionTopics.text.clear()
                }
            }
        })


        binding.etNewQuestionTopics.setOnKeyListener(object : View.OnKeyListener {
            override fun onKey(v: View?, keyCode: Int, event: KeyEvent): Boolean {
                // if the event is a key down event on the enter button
                if (event.action == KeyEvent.ACTION_DOWN &&
                    keyCode == KeyEvent.KEYCODE_ENTER
                ) {
                    addTag(binding.etNewQuestionTopics.text)
                    binding.etNewQuestionTopics.text.clear()
                    return true
                }
                return false
            }
        })

        binding.ivNewQuestionUpload.setOnClickListener {

            val builder: AlertDialog.Builder = this.let {
                AlertDialog.Builder(it)
            }

            builder.setMessage("Los tama??os de los archivos que se adjunten deben ser menores a 10Mb")
                .setTitle("Limite de tama??o")

            builder.setPositiveButton("ENTENDIDO") { _, _ ->
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.type = "*/*"
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(Intent.createChooser(intent, "Select File"),fileBack)
            }
            val dialog: AlertDialog = builder.create()
            dialog.show()
        }
    }//on onCreate


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if(requestCode == fileBack){

            if (resultCode == RESULT_OK){

                fileData = data!!.data
                clipData = data.clipData

                if(clipData != null)
                {

                    ogFileNames.clear()
                    val clipCount = clipData!!.itemCount
                    Log.d("subir", "clipCount: $clipCount")
                    var currentFile = 0

                    while(currentFile < clipCount)
                    {

                        val file = clipData!!.getItemAt(currentFile).uri
                        val cR: ContentResolver = this.contentResolver
                        val mime = MimeTypeMap.getSingleton()
                        val type = mime.getExtensionFromMimeType(cR.getType(file))

                        val fileName = File(file.path!!).name + "." + type

                        dataUri.add(file)
                        ogFileNames.add(fileName)
                        Log.d("subir", "clipData: $fileName")

                        currentFile++
                    }

                    fileAdapter = FileAdapter(this,ogFileNames)

                    binding.rvFiles.apply {
                        setHasFixedSize(true)
                        layoutManager = LinearLayoutManager(context,LinearLayoutManager.HORIZONTAL,false)
                        adapter = fileAdapter
                    }
                }
                else if(fileData!=null)
                {
                    ogFileNames.clear()
                    val cR: ContentResolver = this.contentResolver
                    val mime = MimeTypeMap.getSingleton()
                    val type = mime.getExtensionFromMimeType(cR.getType(fileData!!))

                    Log.d("subir", "type: ${type}")
                    Log.d("subir", "fileData: ${fileData}")
                    ogFileNames.add(File(fileData!!.path!!).name+"."+type)
                    fileAdapter = FileAdapter(this, ogFileNames)

                        binding.rvFiles.apply {
                            setHasFixedSize(true)
                            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                            adapter = fileAdapter
                        }

                }
            }
        }
    }

    private fun uploadFile(){

        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Subiendo archivos")
        progressDialog.setCancelable(false)
        progressDialog.show()


        var fileName : StorageReference

        if(clipData != null) {
            var currentFile = 0
            dataUri.forEach { file ->

                val cR: ContentResolver = this.contentResolver
                val mime = MimeTypeMap.getSingleton()
                val type = mime.getExtensionFromMimeType(cR.getType(file))

                fileName = storage.child(File(file.path!!).name+"."+type)
                fileName.putFile(file)
                currentFile ++
            }
            db.collection("Questions").document(id).update("files",ogFileNames)
        }

        else if(fileData!=null) {
            val cR: ContentResolver = this.contentResolver
            val mime = MimeTypeMap.getSingleton()
            val type = mime.getExtensionFromMimeType(cR.getType(fileData!!))

            fileName = storage.child(File(fileData!!.path!!).name + "." + type)
            fileName.putFile(fileData!!)
            db.collection("Questions").document(id).update("files", ogFileNames)
        }
    }


    @SuppressLint("InflateParams")
    private fun addTag(s: CharSequence) {
        val tagText = s.toString().lowercase().filter { !it.isWhitespace() }
        val layoutInflater = LayoutInflater.from(baseContext)
        val tag = layoutInflater.inflate(R.layout.item_topic_create, null, false) as Chip
        tag.text = tagText
        tag.setOnCloseIconClickListener {
            val text = (it as Chip).text
            topicsList.remove(text)
            binding.chipGroupNewQuestion.removeView(it)
        }

        if(tagText !in topicsList){
            topicsList.add(tagText)
            binding.chipGroupNewQuestion.addView(tag)
        }
    }

    private fun setup(email: String) {
        binding.edtTitle.requestFocus()
        loadImg(binding.imgQuestionProfile, email)
        binding.imgQuestionBack.setOnClickListener{

            onBackPressed()

        }//regresar a la pantalla anterior

    }//fun

    private fun loadImg(image : CircleImageView, author: String) {

        db.collection("Users").document(author).addSnapshotListener{
                result, error ->
            val urlImg = result!!.get("imgProfile").toString()

            try {
                if(urlImg.isNotEmpty())
                    Picasso.get().load(urlImg).into(image)

            } catch (e: Exception) {
                Picasso.get().load(R.drawable.ic_user_profile).into(image)
            }
        }
    }//load image

    private fun postQuestion(email: String?, name : String, userTopics: MutableList<CharSequence>){

        binding.btnPostQuestion.setOnClickListener {

            if(binding.edtTitle.text.toString().isNotEmpty() && binding.edtDescription.text.toString().isNotEmpty()){


                val timestamp: com.google.firebase.Timestamp = com.google.firebase.Timestamp.now()
                val likes: ArrayList<String> = ArrayList()
                val dislikes: ArrayList<String> = ArrayList()

                db.collection("Questions").document(id).set(

                    hashMapOf(

                        "id" to id,
                        "name" to name,
                        "date" to timestamp,
                        "likes" to likes,
                        "dislikes" to dislikes,
                        "answers" to 0,
                        "author" to email,
                        "title" to binding.edtTitle.text.toString(),
                        "description" to binding.edtDescription.text.toString(),
                        "topics" to topicsList

                    )//hashMap

                )//set

                db.collection("Users").document(Methods.getEmail(this)!!).update("questions", FieldValue.increment(1))



                val intent = Intent(this, HomeActivity::class.java).apply {

                    putExtra("email", email)

                }//intent

                startActivity(intent)

                uploadFile()

                this.finish()

            }//los campos requeridos no estan vacios

            else{

                val toast = Toast.makeText(applicationContext, "No dejes ning??n campo vac??o", Toast.LENGTH_SHORT)
                toast.show()

                if(binding.edtTitle.text.toString().isEmpty()){

                    binding.edtTitle.requestFocus()

                }//el titulo esta vac??o

                else {

                    binding.edtDescription.requestFocus()

                }//la descripci??n esta vacia

            }//los campos requeridos estan vacios

        }//setOnClickListener

    }//postQuestion

}//class
