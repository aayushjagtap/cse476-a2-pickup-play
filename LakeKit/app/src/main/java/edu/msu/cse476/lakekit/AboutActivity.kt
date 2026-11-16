package edu.msu.cse476.lakekit

import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        val web = findViewById<WebView>(R.id.aboutWeb)
        val html = "<html><body style=\"font-family:sans-serif;padding:16px\">" +
            getString(R.string.about_text) + "</body></html>"
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }
}
