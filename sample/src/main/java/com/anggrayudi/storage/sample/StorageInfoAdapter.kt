package com.anggrayudi.storage.sample

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Environment
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.anggrayudi.storage.extension.isDownloadsDocument
import com.anggrayudi.storage.extension.isTreeDocumentFile
import com.anggrayudi.storage.file.DocumentFileCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Created on 12/14/20
 * @author Anggrayudi H
 */
class StorageInfoAdapter(
    private val context: Context,
    private val ioScope: CoroutineScope,
    private val uiScope: CoroutineScope
) : RecyclerView.Adapter<StorageInfoAdapter.ViewHolder>() {

    private val storageIds = DocumentFileCompat.getStorageIds(context)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.view_item_storage_info, parent, false))
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        ioScope.launch {
            val storageId = storageIds[position]
            val storageName = if (storageId == DocumentFileCompat.PRIMARY) "External Storage" else storageId
            val storageCapacity = Formatter.formatFileSize(context, DocumentFileCompat.getStorageCapacity(context, storageId))
            val storageUsedSpace = Formatter.formatFileSize(context, DocumentFileCompat.getUsedSpace(context, storageId))
            val storageFreeSpace = Formatter.formatFileSize(context, DocumentFileCompat.getFreeSpace(context, storageId))
            uiScope.launch {
                holder.run {
                    tvStorageName.text = storageName
                    tvStorageCapacity.text = "Capacity: $storageCapacity"
                    tvStorageUsedSpace.text = "Used Space: $storageUsedSpace"
                    tvStorageFreeSpace.text = "Free Space: $storageFreeSpace"
                    btnShowGrantedUri.setOnClickListener { showGrantedUris(it.context, storageId) }
                    if (storageId == DocumentFileCompat.PRIMARY && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        // No URI permission required for external storage
                        btnShowGrantedUri.visibility = View.GONE
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun showGrantedUris(context: Context, storageId: String) {
        val grantedUris = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission && it.isWritePermission && it.uri.isTreeDocumentFile }
            .map {
                if (it.uri.isDownloadsDocument) {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
                } else {
                    val uriPath = it.uri.path!! // e.g. /tree/primary:Music
                    val storageId = uriPath.substringBefore(':').substringAfterLast('/')
                    val rootFolder = uriPath.substringAfter(':', "")
                    if (storageId == DocumentFileCompat.PRIMARY) {
                        "${Environment.getExternalStorageDirectory()}/$rootFolder"
                    } else {
                        "/storage/$storageId/$rootFolder"
                    }
                }
            }
        if (grantedUris.isEmpty()) {
            MaterialDialog(context)
                .message(text = "No URI permission granted on \"$storageId\"")
                .positiveButton()
                .show()
        } else {
            MaterialDialog(context)
                .title(text = "Granted URIs")
                .listItems(items = grantedUris)
                .show()
        }
    }

    override fun getItemCount() = storageIds.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        internal val tvStorageName = view.findViewById<TextView>(R.id.tvStorageName)
        internal val tvStorageCapacity = view.findViewById<TextView>(R.id.tvStorageCapacity)
        internal val tvStorageUsedSpace = view.findViewById<TextView>(R.id.tvStorageUsedSpace)
        internal val tvStorageFreeSpace = view.findViewById<TextView>(R.id.tvStorageFreeSpace)
        internal val btnShowGrantedUri = view.findViewById<Button>(R.id.btnShowGrantedUri)
    }
}