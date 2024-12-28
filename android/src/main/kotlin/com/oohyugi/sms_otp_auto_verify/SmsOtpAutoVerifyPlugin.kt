package com.oohyugi.sms_otp_auto_verify

import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.TelephonyManager
import com.google.android.gms.auth.api.credentials.Credentials
import com.google.android.gms.auth.api.credentials.HintRequest
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.auth.api.phone.SmsRetrieverClient
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler

class SmsOtpAutoVerifyPlugin : FlutterPlugin, MethodCallHandler, ActivityAware, MySmsListener {
    private var channel: MethodChannel? = null
    private var receiver: SmsBroadcastReceiver? = null
    private var client: SmsRetrieverClient? = null
    private var activity: Activity? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(binding.binaryMessenger, "sms_otp_auto_verify")
        channel?.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        if (call.method == "startListening") {
            startListening()
        } else {
            result.notImplemented()
        }
    }

    private fun startListening() {
        activity?.let {
            client = SmsRetriever.getClient(it)
        }
        val task = client?.startSmsRetriever()
        task?.addOnSuccessListener {
            receiver = SmsBroadcastReceiver()
            receiver?.setSmsListener(this) // Ensure this matches the expected type
            activity?.registerReceiver(receiver, IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION))
        }
    }

    override fun onSmsReceived(sms: String) {
        // Handle received SMS
    }
}
