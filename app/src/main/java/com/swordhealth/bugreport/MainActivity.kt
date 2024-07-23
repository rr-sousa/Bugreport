package com.swordhealth.bugreport

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BugreportManager
import android.os.BugreportParams
import android.os.Bundle
import android.os.IncidentManager
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.swordhealth.bugreport.databinding.ActivityMainBinding
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var buttonRequestBugDeviceAdmin: Button
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var bugreportReceiver: ComponentName

    private lateinit var buttonStartBugManager: Button

    companion object {
        private const val TAG: String = "BugreportMain"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        // Check current user
        val currentUser = ActivityManager.getService().currentUser
        Log.d(TAG, "Current user: $currentUser")
        Log.d(TAG, "Current user is admin? " + currentUser.isAdmin.toString())

        // Check and request DUMP permission
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.DUMP
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "NO PERMISSION: DUMP")
        } else {
            Log.d(TAG, "I have permission: DUMP")
        }

        devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        bugreportReceiver = ComponentName(this, DeviceAdminReceiver::class.java)
        buttonRequestBugDeviceAdmin = findViewById(R.id.buttonRequest)
        buttonRequestBugDeviceAdmin.setOnClickListener {
            if (!devicePolicyManager.isDeviceOwnerApp(packageName)){
                Toast.makeText(this, "App is not device owner", Toast.LENGTH_LONG).show()
            } else {
                requestBugReport()
            }
        }

        buttonStartBugManager = findViewById(R.id.buttonStart)
        buttonStartBugManager.setOnClickListener {
            startBugreport()
        }

    }

    //////////////////// DevicePolicyManager approach
    private fun requestBugReport() {
        val bugreportStatus = devicePolicyManager.requestBugreport(bugreportReceiver)
        if (bugreportStatus) {
            //buttonRequestBugDeviceAdmin.setEnabled(false) or something similar
            Log.i(TAG, "Bug report requested")
            Toast.makeText(this, "Bug report requested", Toast.LENGTH_LONG).show()
        } else {
            Log.w(TAG, "There's already one bug reporting running!")
            Toast.makeText(this, "Bug report already in progress", Toast.LENGTH_LONG).show()
        }

        // Approve bug report sharing
        //sendBroadcast(Intent(DevicePolicyManager.ACTION_BUGREPORT_SHARING_ACCEPTED), Manifest.permission.TRIGGER_SHELL_BUGREPORT)
        val myIntent = Intent()
        myIntent.setAction("com.android.server.action.REMOTE_BUGREPORT_SHARING_ACCEPTED")
        sendBroadcast(myIntent)
    }


    //////////////////// BugreportManager approach
    @SuppressLint("WrongConstant")
    private fun startBugreport() {
        val bugreportManager = getSystemService(BUGREPORT_SERVICE) as BugreportManager

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm")
        val current = LocalDateTime.now().format(formatter)
        val outputDir = filesDir
        val bugreportFileName = "bugreport_$current.zip"
        val bugreportFile = File(outputDir, bugreportFileName)
        val bugreportFd = ParcelFileDescriptor.open(
            bugreportFile,
            ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE
        )

        val executor = Executors.newSingleThreadExecutor()

        // Other modes can be used, but without adding BUGREPORT_FLAG_DEFER_CONSENT
        val bugreportParams = BugreportParams(BugreportParams.BUGREPORT_MODE_FULL)
        Log.d(TAG, "Mode Bugreport: " + bugreportParams.mode.toString())

        val bugreportCallback = object : BugreportManager.BugreportCallback() {
            override fun onFinished() {
                super.onFinished()
                // Handle the bugreport file, e.g., upload it to a server or save it locally
                Log.d(TAG, "Bug report saved on: $outputDir/$bugreportFileName")
            }

            override fun onError(errorCode: Int) {
                super.onError(errorCode)
                // Handle the error, e.g., show a message to the user
                when (errorCode) {
                    BUGREPORT_ERROR_USER_DENIED_CONSENT -> Log.e(TAG,"User did not consent to sharing the bugreport")
                    BUGREPORT_ERROR_USER_CONSENT_TIMED_OUT -> Log.e(TAG, "The consent timed out")
                    BUGREPORT_ERROR_ANOTHER_REPORT_IN_PROGRESS -> Log.e( TAG,"Try later, as only one bugreport can be in progress at a time.")
                    else -> {
                        Log.e(TAG, "Error code not know: $errorCode")
                    }
                }
            }
        }

        bugreportManager.startBugreport(
            bugreportFd,
            null,
            bugreportParams,
            executor,
            bugreportCallback
        )

        Log.i(TAG, "Requested Bug report")
        // Approve bug report sharing
        approvePendingReports()
    }

    @SuppressLint("WrongConstant")
    private fun approvePendingReports() {
        var pendingReportApproved = false
        val im: IncidentManager?
        im = getSystemService(INCIDENT_SERVICE) as IncidentManager

        while (!pendingReportApproved) {
            /// TO IMPROVE, not the best way for sure, but it's needed so that im.pendingReports is not empty
            try {
                Thread.sleep(5000)
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            }
            Log.d(TAG, "Slept 5 seconds")
            ////////////
            val pendingReportList = im.pendingReports
            for (i in pendingReportList) {
                val pendingReport = i.uri
                val pendingReportString = pendingReport.toString()
                Log.d(TAG, "Pending report to approve: $pendingReportString")
                if (pendingReportString.contains("android.os.IncidentManager/pending")
                    && pendingReportString.contains("pkg=$packageName")
                    && pendingReportString.contains("flags=1")
                ) {
                    im.approveReport(pendingReport)
                    pendingReportApproved = true
                    Log.d(TAG, "Report approved: $pendingReportString")
                }
            }
        }
    }

}