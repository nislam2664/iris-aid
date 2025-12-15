package com.example.irisaid

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.content.edit


class MainActivity : ComponentActivity() {
    // Instantiate dropdown menu and TextView instances
    private lateinit var spinner: Spinner
    private lateinit var adapter: ArrayAdapter<CharSequence>
    private lateinit var instructionText: TextView

    // ---------------------- OVERRIDE FUNCTIONS ---------------------- //

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Initializes main page activity components (dropdown, instruction text, book covers
        spinner = findViewById(R.id.modeSpinner)
        instructionText = findViewById(R.id.instructionText)
        adapter = ArrayAdapter.createFromResource(
            this,
            R.array.zoom_modes,
            android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val prefs = getSharedPreferences("zoom_prefs", MODE_PRIVATE)
        val savedMode = prefs.getString("current_mode", "🔴 Button + Swipe")
        spinner.setSelection(adapter.getPosition(savedMode))

        // Update spinner across all activity pages where spinner is used to synchronize correctly
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedMode = parent.getItemAtPosition(position).toString()
                prefs.edit { putString("current_mode", selectedMode) }
                Toast.makeText(this@MainActivity, "Selected: $selectedMode", Toast.LENGTH_SHORT).show()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Sets instruction text
        instructionText.text = getString(R.string.test_instructions)

        // Sets on-click listeners to each book cover to open to new activity page
        findViewById<ImageView>(R.id.book1).setOnClickListener { openReadingPage("book1") }
        findViewById<ImageView>(R.id.book2).setOnClickListener { openReadingPage("book2") }
        findViewById<ImageView>(R.id.book3).setOnClickListener { openReadingPage("book3") }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("zoom_prefs", MODE_PRIVATE)
        val savedMode = prefs.getString("current_mode", "🔴 Button + Swipe")
        spinner.setSelection(adapter.getPosition(savedMode))
    }

    // ---------------------- CUSTOM FUNCTIONS ---------------------- //

    // Opens to another activity and changes text excerpt based on chosen book cover
    private fun openReadingPage(bookId: String) {
        val intent = Intent(this, ModeActivity::class.java)
        intent.putExtra("BOOK_ID", bookId)
        startActivity(intent)
    }
}