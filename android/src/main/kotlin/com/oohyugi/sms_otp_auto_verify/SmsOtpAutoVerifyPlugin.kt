package com.oohyugi.sms_otp_auto_verify

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
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
import io.flutter.plugin.common.BinaryMessenger
import com.google.android.gms.auth.api.credentials.Credential
import android.content.IntentSender

class SmsOtpAutoVerifyPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
    private var channel: MethodChannel? = null
    private var pendingResult: MethodChannel.Result? = null
    private var receiver: SmsBroadcastReceiver? = null
    private var alreadyCalledSmsRetrieve = false
    private var client: SmsRetrieverClient? = null
    private var activity: Activity? = null

    companion object {
        private const val channelName = "sms_otp_auto_verify"
        private const val REQUEST_RESOLVE_HINT = 1256
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(binding.binaryMessenger, channelName)
        channel?.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel = null
        unregister()
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getAppSignature" -> {
                activity?.let {
                    val signature = AppSignatureHelper(it).getAppSignatures()[0]
                    result.success(signature)
                }
            }
            "startListening" -> {
                this.pendingResult = result
                receiver = SmsBroadcastReceiver()
                startListening()
            }
            "stopListening" -> {
                pendingResult = null
                unregister()
            }
            "requestPhoneHint" -> {
                this.pendingResult = result
                requestHint()
            }
            else -> result.notImplemented()
        }
    }

    private fun startListening() {
        activity?.let {
            client = SmsRetriever.getClient(it)
        }
        val task = client?.startSmsRetriever()
        task?.addOnSuccessListener {
            unregister()
            receiver?.setSmsListener(this)
            activity?.registerReceiver(receiver, IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION))
        }
    }

    private fun unregister() {
        alreadyCalledSmsRetrieve = false
        receiver?.let {
            try {
                activity?.unregisterReceiver(it)
                receiver = null
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    private fun requestHint() {
        val hintRequest = HintRequest.Builder()
            .setPhoneNumberIdentifierSupported(true)
            .build()

        activity?.apply {
            val intent = Credentials.getClient(this).getHintPickerIntent(hintRequest)
            try {
                startIntentSenderForResult(
                    intent.intentSender,
                    REQUEST_RESOLVE_HINT, null, 0, 0, 0
                )
            } catch (e: IntentSender.SendIntentException) {
                e.printStackTrace()
            }
        }
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        unregister()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivity() {
        unregister()
    }
}
