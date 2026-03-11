package net.loeu.wallybudget.data.snapshot

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.io.OutputStream

interface DocumentUriGateway {
    fun openInputStream(uri: Uri): InputStream?
    fun openOutputStream(uri: Uri): OutputStream?
}

class AndroidDocumentUriGateway(
    context: Context
) : DocumentUriGateway {
    private val contentResolver: ContentResolver = context.contentResolver

    override fun openInputStream(uri: Uri): InputStream? = contentResolver.openInputStream(uri)

    override fun openOutputStream(uri: Uri): OutputStream? = contentResolver.openOutputStream(uri)
}
