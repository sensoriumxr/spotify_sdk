package de.minimalme.spotify_sdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector.ConnectionListener
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.android.appremote.api.error.*
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import de.minimalme.spotify_sdk.subscriptions.*
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import kotlinx.event.SetEvent
import kotlinx.event.event
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.PluginRegistry.Registrar

class SpotifySdkPlugin : MethodCallHandler, FlutterPlugin, ActivityAware, PluginRegistry.ActivityResultListener {

    companion object {
        /** Plugin registration.  */
        @SuppressWarnings("deprecation")
        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val instance = SpotifySdkPlugin()
            instance.onAttachedToEngine(registrar.context(), registrar.messenger())
        }
    }

    // application context
    private var applicationContext : Context? = null
    private var applicationActivity : Activity? = null

    // method channel
    private var methodChannel: MethodChannel? = null
    private val channelName = "spotify_sdk"
    private val loggingTag = "spotify_sdk"

    // event channels
    private var playerContextChannel : EventChannel? = null
    private var playerStateChannel : EventChannel? = null
    private var capabilitiesChannel : EventChannel? = null
    private var userStatusChannel : EventChannel? = null
    private var connectionStatusChannel : EventChannel? = null

    private val playerContextSubscription = "player_context_subscription"
    private val playerStateSubscription = "player_state_subscription"
    private val capabilitiesSubscription = "capabilities_subscription"
    private val userStatusSubscription = "user_status_subscription"
    private val connectionStatusSubscription = "connection_status_subscription"

    //connecting
    private val methodConnectToSpotify = "connectToSpotify"
    private val methodGetAccessToken = "getAccessToken"
    private val methodDisconnectFromSpotify = "disconnectFromSpotify"

    // connectApi
    private val methodSwitchToLocalDevice = "switchToLocalDevice"

    //playerApi
    private val methodGetCrossfadeState = "getCrossfadeState"
    private val methodGetPlayerState = "getPlayerState"
    private val methodPlay = "play"
    private val methodPause = "pause"
    private val methodQueueTrack = "queueTrack"
    private val methodResume = "resume"
    private val methodSeekToRelativePosition = "seekToRelativePosition"
    private val methodSetPodcastPlaybackSpeed = "setPodcastPlaybackSpeed"
    private val methodSkipNext = "skipNext"
    private val methodSkipPrevious = "skipPrevious"
    private val methodSkipToIndex = "skipToIndex"
    private val methodSeekTo = "seekTo"
    private val methodToggleRepeat = "toggleRepeat"
    private val methodToggleShuffle = "toggleShuffle"
    private val methodSetShuffle = "setShuffle"
    private val methodSetRepeatMode = "setRepeatMode"
    private val methodIsSpotifyAppActive = "isSpotifyAppActive"

    //userApi
    private val methodAddToLibrary = "addToLibrary"
    private val methodRemoveFromLibrary = "removeFromLibrary"
    private val methodGetCapabilities = "getCapabilities"
    private val methodGetLibraryState = "getLibraryState"

    //imagesApi
    private val methodGetImage = "getImage"

    private val paramClientId = "clientId"
    private val paramRedirectUrl = "redirectUrl"
    private val paramScope = "scope"
    private val paramSpotifyUri = "spotifyUri"
    private val paramImageUri = "imageUri"
    private val paramImageDimension = "imageDimension"
    private val paramPositionedMilliseconds = "positionedMilliseconds"
    private val paramRelativeMilliseconds = "relativeMilliseconds"
    private val paramPodcastPlaybackSpeed = "podcastPlaybackSpeed"
    private val paramTrackIndex = "trackIndex"
    private val paramRepeatMode = "repeatMode"
    private val paramShuffle = "shuffle"

    private val errorConnecting = "errorConnecting"
    private val errorDisconnecting = "errorDisconnecting"
    private val errorConnection = "errorConnection"
    private val errorAuthenticationToken = "authenticationTokenError"

    private var connStatusEventChannel: SetEvent<ConnectionStatusChannel.ConnectionEvent> = event()

    private val requestCodeAuthentication = 1337

    private var pendingOperation: PendingOperation? = null
    private var spotifyAppRemote: SpotifyAppRemote? = null
    private var spotifyPlayerApi: SpotifyPlayerApi? = null
    private var spotifyConnectApi: SpotifyConnectApi? = null
    private var spotifyUserApi: SpotifyUserApi? = null
    private var spotifyImagesApi: SpotifyImagesApi? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        onAttachedToEngine(binding.applicationContext, binding.binaryMessenger)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {

        applicationContext = null

        methodChannel?.setMethodCallHandler(null)
        methodChannel = null

        playerContextChannel?.setStreamHandler(null)
        playerStateChannel?.setStreamHandler(null)
        capabilitiesChannel?.setStreamHandler(null)
        userStatusChannel?.setStreamHandler(null)
        connectionStatusChannel?.setStreamHandler(null)

        playerContextChannel = null
        playerStateChannel = null
        capabilitiesChannel = null
        userStatusChannel = null
        connectionStatusChannel = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        binding.addActivityResultListener(this)
        applicationActivity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        applicationActivity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        applicationActivity = binding.activity
    }

    override fun onDetachedFromActivity() {
        applicationActivity = null
    }

    override fun onMethodCall(call: MethodCall, result: Result) {

        if (spotifyAppRemote != null) {
            spotifyPlayerApi = SpotifyPlayerApi(spotifyAppRemote, result)
            spotifyUserApi = SpotifyUserApi(spotifyAppRemote, result)
            spotifyImagesApi = SpotifyImagesApi(spotifyAppRemote, result)
            spotifyConnectApi = SpotifyConnectApi(spotifyAppRemote, result)
        }

        when (call.method) {
            //connecting to spotify
            methodConnectToSpotify -> connectToSpotify(call.argument(paramClientId), call.argument(paramRedirectUrl), result)
            methodGetAccessToken -> getAccessToken(call.argument(paramClientId), call.argument(paramRedirectUrl), call.argument(paramScope), result)
            methodDisconnectFromSpotify -> disconnectFromSpotify(result)
            //connectApi calls
            methodSwitchToLocalDevice -> spotifyConnectApi?.switchToLocalDevice()
            //playerApi calls
            methodGetCrossfadeState -> spotifyPlayerApi?.getCrossfadeState()
            methodGetPlayerState -> spotifyPlayerApi?.getPlayerState()
            methodPlay -> spotifyPlayerApi?.play(call.argument(paramSpotifyUri))
            methodPause -> spotifyPlayerApi?.pause()
            methodQueueTrack -> spotifyPlayerApi?.queue(call.argument(paramSpotifyUri))
            methodResume -> spotifyPlayerApi?.resume()
            methodSeekTo -> spotifyPlayerApi?.seekTo(call.argument(paramPositionedMilliseconds))
            methodSeekToRelativePosition -> spotifyPlayerApi?.seekToRelativePosition(call.argument(paramRelativeMilliseconds))
            methodSetPodcastPlaybackSpeed -> spotifyPlayerApi?.setPodcastPlaybackSpeed(call.argument(paramPodcastPlaybackSpeed))
            methodSkipNext -> spotifyPlayerApi?.skipNext()
            methodSkipPrevious -> spotifyPlayerApi?.skipPrevious()
            methodSkipToIndex -> spotifyPlayerApi?.skipToIndex(call.argument(paramSpotifyUri), call.argument(paramTrackIndex))
            methodToggleShuffle -> spotifyPlayerApi?.toggleShuffle()
            methodSetShuffle -> spotifyPlayerApi?.setShuffle(call.argument(paramShuffle))
            methodToggleRepeat -> spotifyPlayerApi?.toggleRepeat()
            methodSetRepeatMode -> spotifyPlayerApi?.setRepeatMode(call.argument(paramRepeatMode))
            methodIsSpotifyAppActive -> spotifyPlayerApi?.isSpotifyAppActive()
            //userApi calls
            methodAddToLibrary -> spotifyUserApi?.addToUserLibrary(call.argument(paramSpotifyUri))
            methodRemoveFromLibrary -> spotifyUserApi?.removeFromUserLibrary(call.argument(paramSpotifyUri))
            methodGetCapabilities -> spotifyUserApi?.getCapabilities()
            methodGetLibraryState -> spotifyUserApi?.getLibraryState(call.argument(paramSpotifyUri))
            //imageApi calls
            methodGetImage -> spotifyImagesApi?.getImage(call.argument(paramImageUri), call.argument(paramImageDimension))
            // method call is not implemented yet
            else -> result.notImplemented()
        }
    }

    private fun onAttachedToEngine(applicationContext: Context, messenger: BinaryMessenger) {

        this.applicationContext = applicationContext

        methodChannel = MethodChannel(messenger, channelName)
        methodChannel?.setMethodCallHandler(this)

        playerContextChannel = EventChannel(messenger, playerContextSubscription)
        playerStateChannel = EventChannel(messenger, playerStateSubscription)
        capabilitiesChannel = EventChannel(messenger, capabilitiesSubscription)
        userStatusChannel = EventChannel(messenger, userStatusSubscription)
        connectionStatusChannel = EventChannel(messenger, connectionStatusSubscription)

        connectionStatusChannel?.setStreamHandler(ConnectionStatusChannel(connStatusEventChannel))
    }

    //-- Method implementations
    private fun connectToSpotify(clientId: String?, redirectUrl: String?, result: Result) {

        if (clientId.isNullOrBlank() || redirectUrl.isNullOrBlank()) {
            result.error(errorConnecting, "client id or redirectUrl are not set or have invalid format", "")
        } else {
            val connectionParams = ConnectionParams.Builder(clientId)
                    .setRedirectUri(redirectUrl)
                    .showAuthView(true)
                    .build()
            SpotifyAppRemote.disconnect(spotifyAppRemote)
            var initiallyConnected = false
            SpotifyAppRemote.connect(applicationContext, connectionParams,
                    object : ConnectionListener {
                        override fun onConnected(spotifyAppRemoteValue: SpotifyAppRemote) {
                            spotifyAppRemote = spotifyAppRemoteValue

                            playerContextChannel?.setStreamHandler(PlayerContextChannel(spotifyAppRemote!!.playerApi))
                            Log.i(loggingTag, "Set stream handler for PlayerContextChannel")
                            playerStateChannel?.setStreamHandler(PlayerStateChannel(spotifyAppRemote!!.playerApi))
                            Log.i(loggingTag, "Set stream handler for PlayerStateChannel")
                            capabilitiesChannel?.setStreamHandler(CapabilitiesChannel(spotifyAppRemote!!.userApi))
                            Log.i(loggingTag, "Set stream handler for CapabilitiesChannel")
                            userStatusChannel?.setStreamHandler(UserStatusChannel(spotifyAppRemote!!.userApi))
                            Log.i(loggingTag, "Set stream handler for UserStatusChannel")
                            initiallyConnected = true

                            Log.i(loggingTag, "App Remote successfully connected")
                            // emit connection established event
                            connStatusEventChannel(ConnectionStatusChannel.ConnectionEvent(true, "Successfully connected to Spotify.", null, null))
                            // method success
                            result.success(true)
                        }

                        override fun onFailure(throwable: Throwable) {
                            val errorDetails = throwable.toString()
                            // determine the error
                            val errorMessage: String
                            val errorCode: String
                            var connected = false
                            when (throwable) {
                                is SpotifyDisconnectedException, is SpotifyConnectionTerminatedException -> {
                                    // The Spotify app was/is disconnected by the Spotify app.
                                    // This indicates typically that the Spotify app was closed by the user or for other reasons.
                                    // You need to reconnect to continue using Spotify App Remote.
                                    errorMessage = "The Spotify app was/is disconnected by the Spotify app.Reconnect necessary"
                                    errorCode = "SpotifyDisconnectedException"
                                }
                                is CouldNotFindSpotifyApp -> {
                                    errorMessage = "The Spotify app is not installed on the device"
                                    errorCode = "CouldNotFindSpotifyApp"
                                }
                                is AuthenticationFailedException -> {
                                    errorMessage = "Partner app failed to authenticate with Spotify. Check client credentials and make sure your app is registered correctly at developer.spotify.com"
                                    errorCode = "AuthenticationFailedException"
                                }
                                is UserNotAuthorizedException -> {
                                    errorMessage = "Indicates the user did not authorize this client of App Remote to use Spotify on the users behalf."
                                    errorCode = "UserNotAuthorizedException"
                                }
                                is UnsupportedFeatureVersionException -> {
                                    errorMessage = "Spotify app can't support requested features. User should update Spotify app."
                                    errorCode = "UnsupportedFeatureVersionException"
                                    connected = true
                                }
                                is OfflineModeException -> {
                                    errorMessage = "Spotify user has set their Spotify app to be in offline mode"
                                    errorCode = "OfflineModeException"
                                    connected = true
                                }
                                is NotLoggedInException -> {
                                    errorMessage = "User has logged out from Spotify."
                                    errorCode = "NotLoggedInException"
                                }
                                is SpotifyRemoteServiceException -> {
                                    errorMessage = "Encapsulates possible SecurityException and IllegalStateException errors."
                                    errorCode = "SpotifyRemoteServiceException"
                                }
                                else -> {
                                    errorMessage = "Something went wrong connecting spotify remote"
                                    errorCode = errorConnection
                                }
                            }
                            Log.e(loggingTag, errorMessage)
                            // notify plugin
                            if (initiallyConnected) {
                                // emit connection error event
                                connStatusEventChannel(ConnectionStatusChannel.ConnectionEvent(connected, errorMessage, errorCode, errorDetails))
                            } else {
                                // throw exception as the connect method
                                result.error(errorCode, errorMessage, errorDetails)
                            }
                        }
                    })
        }
    }

    private fun getAccessToken(clientId: String?, redirectUrl: String?, scope: String?, result: Result) {
        if (applicationActivity == null) {
            throw IllegalStateException("getAccessToken needs a foreground activity")
        }

        if (clientId.isNullOrBlank() || redirectUrl.isNullOrBlank()) {
            result.error(errorConnecting, "client id or redirectUrl are not set or have invalid format", "")
        } else {
            //Convert String? scope to Array. Delimiter set as comma ","
            val scopeArray = scope?.split(",")?.toTypedArray()
            methodConnectToSpotify.checkAndSetPendingOperation(result)

            val builder = AuthorizationRequest.Builder(clientId, AuthorizationResponse.Type.TOKEN, redirectUrl)
            builder.setScopes(scopeArray)
            val request = builder.build()

            AuthorizationClient.openLoginActivity(applicationActivity, requestCodeAuthentication, request)
        }
    }

    private fun disconnectFromSpotify(result: Result) {
        if (spotifyAppRemote != null && spotifyAppRemote!!.isConnected) {
            SpotifyAppRemote.disconnect(spotifyAppRemote)

            // emit connection terminated event
            connStatusEventChannel(ConnectionStatusChannel.ConnectionEvent(false, "Successfully disconnected from Spotify.", null, null))
            // method success
            result.success(true)
        } else if (!spotifyAppRemote!!.isConnected) {
            result.error(errorDisconnecting, "could not disconnect spotify remote", "you are not connected, no need to disconnect")
        } else {
            result.error(errorDisconnecting, "could not disconnect spotify remote", "spotifyAppRemote is not set")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (pendingOperation == null) {
            return false
        }
        return when (requestCode) {
            requestCodeAuthentication -> {
                authFlow(resultCode, data)
                return true
            }
            else -> false
        }
    }

    private fun authFlow(resultCode: Int, data: Intent?) {

        val response: AuthorizationResponse = AuthorizationClient.getResponse(resultCode, data)
        val result = pendingOperation!!.result
        pendingOperation = null

        when (response.type) {
            AuthorizationResponse.Type.TOKEN -> {
                result.success(response.accessToken)
            }
            AuthorizationResponse.Type.ERROR -> result.error(errorAuthenticationToken, "Authentication went wrong", response.error)
            else -> result.notImplemented()
        }
    }

    private fun String.checkAndSetPendingOperation(result: Result) {

        check(pendingOperation == null)
        {
            "Concurrent operations detected: " + pendingOperation?.method.toString() + ", " + this
        }
        pendingOperation = PendingOperation(this, result)
    }
}

private class PendingOperation internal constructor(val method: String, val result: Result)
