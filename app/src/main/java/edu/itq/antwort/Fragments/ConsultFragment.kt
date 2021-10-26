package edu.itq.antwort.Fragments

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firebase.ui.firestore.FirestoreRecyclerAdapter
import com.firebase.ui.firestore.FirestoreRecyclerOptions
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import edu.itq.antwort.Adapters.QuestionAdapter
import edu.itq.antwort.Classes.Questions
import edu.itq.antwort.R
import edu.itq.antwort.Utils
import edu.itq.antwort.databinding.FragmentAnswersBinding
import edu.itq.antwort.databinding.FragmentConsultBinding

class ConsultFragment : Fragment() {
    private lateinit var binding: FragmentConsultBinding
    private lateinit var db : FirebaseFirestore
    private lateinit var rev: RecyclerView
    private var questions : MutableList<Questions> = mutableListOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_consult, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = FragmentConsultBinding.bind(view)
        db = FirebaseFirestore.getInstance()
        rev = binding.rvConsult

        val current = Utils.getEmail(requireActivity())

        val query = db.collection("Questions").orderBy("date", Query.Direction.DESCENDING).whereEqualTo("author", current)

        query.get().addOnCompleteListener(OnCompleteListener<QuerySnapshot>() {
            questions.addAll(it.result!!.toObjects(Questions::class.java))
            rev.apply {
                setHasFixedSize(true)
                layoutManager = LinearLayoutManager(context)
                adapter = QuestionAdapter(this@ConsultFragment,questions)
            }
        })
    }

}