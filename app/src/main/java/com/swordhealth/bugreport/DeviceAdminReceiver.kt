package com.swordhealth.bugreport

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream


class DeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private var TAG: String = "BugreportReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        StringBuilder().apply {
            append("Action: ${intent.action}\n")
            append("URI: ${intent.toUri(Intent.URI_INTENT_SCHEME)}\n")
            toString().also { log ->
                Log.d(TAG, log)
            }
        }

        if (intent.action.toString() == "android.app.action.BUGREPORT_SHARE") {
            val bugreportFileName = intent.data.toString().split("/").last()
            Log.i(TAG, "Bug report file name: $bugreportFileName")
            copyUriToCache(context, intent.data!!, bugreportFileName)
        }

    }


    override fun onBugreportSharingDeclined(context: Context, intent: Intent) {
        Log.e(TAG, "Bug report sharing declined")
        Toast.makeText(context, "Bug report sharing declined", Toast.LENGTH_LONG).show()
    }


    override fun onBugreportShared(context: Context, intent: Intent, bugreportHash: String) {
        Log.e(TAG, "Bug report sharing accepted")
        Toast.makeText(context, "Bug report sharing accepted", Toast.LENGTH_LONG).show()

        val bugreportFileName = intent.data.toString().split("/").last()
        Log.i(TAG, "Bug report file name: $bugreportFileName")
        copyUriToCache(context, intent.data!!, bugreportFileName)
    }


    override fun onBugreportFailed(context: Context, intent: Intent, failureCode: Int) {
        Log.e(TAG, "Bug report failed with code: $failureCode")
        Toast.makeText(context, "Please, restart your tablet and try again!", Toast.LENGTH_LONG).show()
    }


    private fun copyUriToCache(context: Context, fileUri: Uri, cacheFileName: String): File? {
        val cacheDir = context.cacheDir
        val cacheFile = File(cacheDir, cacheFileName)

        try {
            context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                FileOutputStream(cacheFile).use { outputStream ->
                    copyStream(inputStream, outputStream)
                    Log.d(TAG, "Bug report saved on: " + context.cacheDir.toString())

                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Bug report could NOT be saved! Please, try again!")
            e.printStackTrace()
            return null
        }

        return cacheFile
    }

    private fun copyStream(input: InputStream, output: OutputStream) {
        Log.d(TAG, "Copying bug report...")
        val buffer = ByteArray(1024)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
        }
    }

}





