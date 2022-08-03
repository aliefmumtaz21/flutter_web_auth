package com.linusu.flutter_web_auth

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri

import androidx.browser.customtabs.CustomTabsIntent

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar

class FlutterWebAuthPlugin(private var context: Context? = null, private var channel: MethodChannel? = null): MethodCallHandler, FlutterPlugin {
  companion object {
    val callbacks = mutableMapOf<String, Result>()

    @JvmStatic
    fun registerWith(registrar: Registrar) {
        val plugin = FlutterWebAuthPlugin()
        plugin.initInstance(registrar.messenger(), registrar.context())
    }
  }

  fun initInstance(messenger: BinaryMessenger, context: Context) {
      this.context = context
      channel = MethodChannel(messenger, "flutter_web_auth")
      channel?.setMethodCallHandler(this)
  }

  override public fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
      initInstance(binding.getBinaryMessenger(), binding.getApplicationContext())
  }

  override public fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
      context = null
      channel = null
  }

  private fun getCustomTabsPackages(context: Context, url: Uri): List<ResolveInfo> {
    val pm: PackageManager = context.packageManager
    // Get default VIEW intent handler.
    val activityIntent = Intent(Intent.ACTION_VIEW, url)
    // Get all apps that can handle VIEW intents.
    val resolvedActivityList: List<ResolveInfo> = pm.queryIntentActivities(activityIntent, 0)
    return resolvedActivityList.filter {
        val serviceIntent = Intent()
        serviceIntent.action = "android.support.customtabs.action.CustomTabsService"
        serviceIntent.setPackage(it.activityInfo.packageName)
        // Check if this package also resolves the Custom Tabs service.
        pm.resolveService(serviceIntent, 0) != null
    }
}

  override fun onMethodCall(call: MethodCall, resultCallback: Result) {
    when (call.method) {
        "authenticate" -> {
          val url = Uri.parse(call.argument("url"))
          val callbackUrlScheme = call.argument<String>("callbackUrlScheme")!!
          val preferEphemeral = call.argument<Boolean>("preferEphemeral")!!
          val forceOpenInBrowser = call.argument<Boolean>("forceOpenInBrowser")!!

          callbacks[callbackUrlScheme] = resultCallback

          val intent = CustomTabsIntent.Builder().build()
          val keepAliveIntent = Intent(context, KeepAliveService::class.java)

          intent.intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
          if (preferEphemeral) {
              intent.intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
          }

          if (forceOpenInBrowser) {
            val customTabsPackages = getCustomTabsPackages(context!!, url)
            if (customTabsPackages.isNullOrEmpty()) {
              resultCallback.success(null)
            } else {
              val packageName = customTabsPackages.first().activityInfo.packageName
              intent.intent.setPackage(packageName)
            }
          }

          intent.intent.putExtra("android.support.customtabs.extra.KEEP_ALIVE", keepAliveIntent)
          intent.launchUrl(context!!, url)
        }
        "cleanUpDanglingCalls" -> {
          callbacks.forEach{ (_, danglingResultCallback) ->
              danglingResultCallback.error("CANCELED", "User canceled login", null)
          }
          callbacks.clear()
          resultCallback.success(null)
        }
        else -> resultCallback.notImplemented()
    }
  }
}
