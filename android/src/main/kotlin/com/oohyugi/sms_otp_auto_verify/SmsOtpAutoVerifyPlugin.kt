package com.oohyugi.sms_otp_auto_verify

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import com.google.android.gms.auth.api.credentials.Credential
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.auth.api.phone.SmsRetrieverClient
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import com.google.android.gms.auth.api.credentials.Credentials
import com.google.android.gms.auth.api.credentials.HintRequest

/** SmsOtpAutoVerifyPlugin */
class SmsOtpAutoVerifyPlugin : FlutterPlugin, MethodCallHandler, MySmsListener, ActivityAware {
    private var channel: MethodChannel? = null
    private var pendingResult: MethodChannel.Result? = null
    private var receiver: SmsBroadcastReceiver? = null
    private var alreadyCalledSmsRetrieve = false
    private var client: SmsRetrieverClient? = null
    private var activity: Activity? = null
    private var binding: ActivityPluginBinding? = null

    private val activityResultListener: PluginRegistry.ActivityResultListener =
        object : PluginRegistry.ActivityResultListener {
            override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
                if (requestCode == REQUEST_RESOLVE_HINT) {
                    if (resultCode == Activity.RESULT_OK && data != null) {
                        val credential: Credential? = data.getParcelableExtra(Credential.EXTRA_KEY)
                        val phoneNumber: String? = credential?.id
                        pendingResult?.success(phoneNumber)
                    } else {
                        pendingResult?.success(null)
                    }
                    return true
                }
                return false
            }
        }

    companion object {
        private const val channelName = "sms_otp_auto_verify"
        private const val REQUEST_RESOLVE_HINT = 1256

        // Remove the setup method
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(binding.binaryMessenger, channelName)
        channel?.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel?.setMethodCallHandler(null)
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

    private fun requestHint() {
        if (!isSimSupport()) {
            pendingResult?.success(null)
            return
        }

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

    private fun isSimSupport(): Boolean {
        val telephonyManager: TelephonyManager =
            activity!!.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return (telephonyManager.simState == TelephonyManager.SIM_STATE_ABSENT)
    }

    private fun startListening() {
        activity?.let {
            client = SmsRetriever.getClient(it)
        }
        val task = client?.startSmsRetriever()
        task?.addOnSuccessListener {
            unregister()
            Log.e(javaClass::getSimpleName.name, "task started")
            receiver?.setSmsListener(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity?.registerReceiver(receiver, IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION), Context.RECEIVER_EXPORTED)
            } else {
                activity?.registerReceiver(receiver, IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION))
            }
        }
    }

    private fun unregister() {
        alreadyCalledSmsRetrieve = false
        receiver?.let {
            try {
                activity?.unregisterReceiver(it)
                Log.d(javaClass::getSimpleName.name, "task stopped")
                receiver = null
            } catch (e: Exception) {
            }
        }
    }

    override fun onOtpReceived(message: String?) {
        message?.let {
            if (!alreadyCalledSmsRetrieve) {
                pendingResult?.success(it)
                alreadyCalledSmsRetrieve = true
            } else {
                Log.d("onOtpReceived: ", "already called")
            }
        }
    }

    override fun onOtpTimeout() {
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        this.binding = binding
        binding.addActivityResultListener(activityResultListener)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        unregister()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
        this.binding = binding
        binding.addActivityResultListener(activityResultListener)
    }

    override fun onDetachedFromActivity() {
        unregister()
    }
}
