package app.kaori.reader.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

/**
 * Import EPUB-ów: albo wskazany folder przez SAF, albo skan katalogu Downloads.
 *
 * Android 15 nie pozwala wybrać korzenia Downloads przez SAF ("privacy"),
 * dlatego dla skanu całego folderu korzystamy z MANAGE_EXTERNAL_STORAGE.
 */
object FolderImport {

    /** Rekurencyjnie zbiera pliki .epub z Downloads (i podfolderów). */
    fun scanDownloads(): List<Uri> {
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return walk(root).map { Uri.fromFile(it) }
    }

    /** Rekurencyjnie zbiera pliki .epub z Documents. */
    fun scanDocuments(): List<Uri> {
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        return walk(root).map { Uri.fromFile(it) }
    }

    private fun walk(dir: File?, depth: Int = 0): List<File> {
        if (dir == null || !dir.isDirectory || depth > 4) return emptyList()
        val files = dir.listFiles() ?: return emptyList()
        val out = mutableListOf<File>()
        for (f in files) {
            if (f.isDirectory) {
                if (!f.name.startsWith(".")) out += walk(f, depth + 1)
            } else if (f.name.lowercase().endsWith(".epub") && f.length() > 20_000) {
                out += f
            }
        }
        return out
    }

    /** All pliki .epub leżące bezpośrednio we wskazanym folderze. */
    fun epubUris(context: Context, tree: Uri): List<Uri> {
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
            ?: return emptyList()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val out = mutableListOf<Uri>()
        context.contentResolver.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2) ?: ""
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) continue
                if (!name.lowercase().endsWith(".epub")) continue
                out += DocumentsContract.buildDocumentUriUsingTree(tree, id)
            }
        }
        return out
    }
}
