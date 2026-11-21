package com.shod.client.integration.vk.captcha.android

import com.shod.client.ShodConstants
import com.shod.client.TunnelManager

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CaptchaCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TunnelManager.stop()
        ManlCaptchaWebViewManager.activeActivity?.finish()
        val notifMgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifMgr.cancel(ShodConstants.Notifications.CAPTCHA_NOTIFICATION_ID)
    }
}
