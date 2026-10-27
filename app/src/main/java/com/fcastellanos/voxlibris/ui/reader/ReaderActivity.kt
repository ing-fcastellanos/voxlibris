package com.fcastellanos.voxlibris.ui.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import com.fcastellanos.voxlibris.R

/** Pantalla de lectura. Solo aloja a [ReaderFragment]; la lógica vive en el fragment y su ViewModel. */
class ReaderActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1L)
        if (bookId < 0) {
            finish()
            return
        }
        // El contenido se dibuja debajo de las barras del sistema; el fragment aplica los insets.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(FragmentContainerView(this).apply { id = R.id.reader_container })
        if (savedInstanceState == null) {
            supportFragmentManager.commitNow {
                replace(R.id.reader_container, ReaderFragment::class.java, ReaderFragment.arguments(bookId), ReaderFragment.TAG)
            }
        }
    }

    companion object {
        private const val EXTRA_BOOK_ID = "book_id"

        fun intent(context: Context, bookId: Long): Intent =
            Intent(context, ReaderActivity::class.java).putExtra(EXTRA_BOOK_ID, bookId)
    }
}
