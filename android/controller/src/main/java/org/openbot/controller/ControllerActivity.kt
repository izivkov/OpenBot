/*
 * Developed for the OpenBot project (https://openbot.org) by:
 *
 * Ivo Zivkov
 * izivkov@gmail.com
 *
 * Date: 2020-12-27, 10:59 p.m.
 */

package org.openbot.controller

import android.annotation.SuppressLint
import android.app.Activity
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter.formatIpAddress
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.appcompat.app.AppCompatDelegate
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import org.openbot.controller.customComponents.DualDriveSeekBar
import org.openbot.controller.databinding.ActivityFullscreenBinding
import org.openbot.controller.utils.EventProcessor
import org.openbot.controller.utils.Utils
import java.util.*


class ControllerActivity : /*AppCompat*/ Activity() { // for some reason AppCompatActivity gives errors in the IDE, but it does compile,
    private val TAG = "ControllerActivity"
    private lateinit var binding: ActivityFullscreenBinding
    private lateinit var screenManager: ScreenManager

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFullscreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        screenManager = ScreenManager(binding)

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        createAppEventsSubscription()

        binding.leftDriveControl.setDirection(DualDriveSeekBar.LeftOrRight.LEFT)
        binding.rightDriveControl.setDirection(DualDriveSeekBar.LeftOrRight.RIGHT)

        screenManager.hideControls()
        // screenManager.showControls()

        hideSystemUI()

        BotDataListener.init()
        binding.videoView.init(binding)
    }

    private fun getLocalIpAddress(): String? {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val ipAddress: Int = wifiManager.connectionInfo.ipAddress
        val ip = String.format("%d.%d.%d.%d", ipAddress and 0xff, ipAddress shr 8 and 0xff, ipAddress shr 16 and 0xff, ipAddress shr 24 and 0xff)
        return ip
    }

    private fun createAppEventsSubscription(): Disposable =
            EventProcessor.connectionEventFlowable
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnNext {
                        Log.i(TAG, "Got ${it} event")

                        when (it) {
                            EventProcessor.ProgressEvents.ConnectionSuccessful -> {
                                Utils.beep()
                                screenManager.showControls()
                                Log.i(null, "IP address: " + getLocalIpAddress())
                                // binding.videoView.start("rtsp://wowzaec2demo.streamlock.net/vod/mp4:BigBuckBunny_115k.mov")
                                // binding.videoView.start("rtsp://192.168.1.102:1935")
                            }
                            EventProcessor.ProgressEvents.ConnectionStarted -> {
                            }
                            EventProcessor.ProgressEvents.ConnectionFailed -> {
                                screenManager.hideControls()
                            }
                            EventProcessor.ProgressEvents.StartAdvertising -> {
                                screenManager.hideControls()
                            }
                            EventProcessor.ProgressEvents.Disconnected -> {
                                screenManager.hideControls()
                                NearbyConnection.connect(this)
                            }
                            EventProcessor.ProgressEvents.StopAdvertising -> {
                            }
                            EventProcessor.ProgressEvents.AdvertisingFailed -> {
                                screenManager.hideControls()
                            }
                        }
                    }
                    .subscribe(
                            { },
                            { throwable ->
                                Log.d(
                                        "EventsSubscription",
                                        "Got error on subscribe: $throwable"
                                )
                            })

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                window.navigationBarColor = getColor(R.color.colorPrimaryDark)
                it.hide(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        }
    }

    @Override
    override fun onPause() {
        super.onPause()
        binding.videoView.release ()
        NearbyConnection.disconnect()
    }

    @Override
    override fun onResume() {
        super.onResume()
        hideSystemUI()
        NearbyConnection.connect(this)
    }

    companion object
}
