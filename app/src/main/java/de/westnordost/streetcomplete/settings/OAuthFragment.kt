package de.westnordost.streetcomplete.settings

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.westnordost.streetcomplete.*
import de.westnordost.streetcomplete.databinding.FragmentOauthBinding
import de.westnordost.streetcomplete.ktx.toBcp47LanguageTag
import de.westnordost.streetcomplete.ktx.toast
import de.westnordost.streetcomplete.ktx.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import oauth.signpost.OAuthConsumer
import oauth.signpost.OAuthProvider
import oauth.signpost.exception.OAuthCommunicationException
import oauth.signpost.exception.OAuthExpectationFailedException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/** Fragment that manages the OAuth 1 authentication process in a webview*/
class OAuthFragment : Fragment(R.layout.fragment_oauth),
    BackPressedListener,
    HasTitle
{
    @Inject internal lateinit var consumerProvider: Provider<OAuthConsumer>
    @Inject internal lateinit var provider: OAuthProvider
    @Inject @field:Named("OAuthCallbackScheme") internal lateinit var callbackScheme: String
    @Inject @field:Named("OAuthCallbackHost") internal lateinit var callbackHost: String

    private val binding by viewBinding(FragmentOauthBinding::bind)

    interface Listener {
        fun onOAuthSuccess(consumer: OAuthConsumer)
        fun onOAuthFailed(e: Exception?)
    }
    private val listener: Listener? get() = parentFragment as? Listener ?: activity as? Listener
    private val callbackUrl get() = "$callbackScheme://$callbackHost"
    private val webViewClient: OAuthWebViewClient = OAuthWebViewClient()

    override val title: String get() = getString(R.string.user_login)

    private lateinit var consumer: OAuthConsumer
    private var authorizeUrl: String? = null
    private var oAuthVerifier: String? = null

    init {
        Injector.applicationComponent.inject(this)
    }

    /* --------------------------------------- Lifecycle --------------------------------------- */

    override fun onCreate(inState: Bundle?) {
        super.onCreate(inState)
        if (inState != null) {
            consumer = inState.getSerializable(CONSUMER) as OAuthConsumer
            authorizeUrl = inState.getString(AUTHORIZE_URL)
            oAuthVerifier = inState.getString(OAUTH_VERIFIER)
        } else {
            consumer = consumerProvider.get()
            authorizeUrl = null
            oAuthVerifier = null
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.webView.settings.userAgentString = ApplicationConstants.USER_AGENT
        binding.webView.settings.javaScriptEnabled = true
        binding.webView.settings.allowContentAccess = true
        binding.webView.settings.setSupportZoom(false)
        binding.webView.webViewClient = webViewClient
        lifecycleScope.launch { continueAuthentication() }
    }

    override fun onPause() {
        super.onPause()
        binding.webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
    }

    override fun onBackPressed(): Boolean {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
            return true
        }
        return false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putSerializable(CONSUMER, consumer)
        outState.putString(AUTHORIZE_URL, authorizeUrl)
        outState.putString(OAUTH_VERIFIER, oAuthVerifier)
        super.onSaveInstanceState(outState)
    }

    /* ------------------------------------------------------------------------------------------ */

    private suspend fun continueAuthentication() {
        try {
            if (authorizeUrl == null) {
                binding.progressView?.visibility = View.VISIBLE
                authorizeUrl = withContext(Dispatchers.IO) {
                    provider.retrieveRequestToken(consumer, callbackUrl)
                }
                binding.progressView?.visibility = View.INVISIBLE
            }
            val authorizeUrl = authorizeUrl
            if (authorizeUrl != null && oAuthVerifier == null) {
                binding.webView.visibility = View.VISIBLE
                binding.webView.loadUrl(
                    authorizeUrl,
                    mutableMapOf("Accept-Language" to Locale.getDefault().toBcp47LanguageTag())
                )
                oAuthVerifier = webViewClient.awaitOAuthCallback()
                binding.webView.visibility = View.INVISIBLE
            }
            if (oAuthVerifier != null) {
                binding.progressView?.visibility = View.VISIBLE
                withContext(Dispatchers.IO) {
                    provider.retrieveAccessToken(consumer, oAuthVerifier)
                }
                listener?.onOAuthSuccess(consumer)
                binding.progressView?.visibility = View.INVISIBLE
            }
        }
        catch (e: Exception) {
            activity?.toast(R.string.oauth_communication_error, Toast.LENGTH_LONG)
            Log.e(TAG, "Error during authorization", e)
            listener?.onOAuthFailed(e)
        }
    }

    /* ---------------------------------------------------------------------------------------- */

    companion object {
        const val TAG = "OAuthDialogFragment"

        private const val CONSUMER = "consumer"
        private const val AUTHORIZE_URL = "authorize_url"
        private const val OAUTH_VERIFIER = "oauth_verifier"
    }

    private inner class OAuthWebViewClient : WebViewClient() {

        private var continutation: Continuation<String>? = null
        suspend fun awaitOAuthCallback(): String = suspendCoroutine { continutation = it }

        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            val uri = url?.toUri() ?: return false
            if (!uri.isHierarchical) return false
            if (uri.scheme != callbackScheme || uri.host != callbackHost) return false
            val verifier = uri.getQueryParameter(OAUTH_VERIFIER)
            if (verifier != null) {
                continutation?.resume(verifier)
            } else {
                continutation?.resumeWithException(
                    OAuthExpectationFailedException("oauth_verifier parameter not set by provider")
                )
            }
            return true
        }

        override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, url: String?) {
            continutation?.resumeWithException(
                OAuthCommunicationException("Error for URL $url","$description")
            )
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            binding.progressView?.visibility = View.VISIBLE
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            binding.progressView?.visibility = View.INVISIBLE
        }
    }
}
