package com.app.android_client_certificate

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.os.Build
import android.view.View
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.security.*
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*
import javax.net.ssl.*

import android.net.http.SslError
import android.webkit.*
import android.widget.Toast
import com.app.android_client_certificate.databinding.ActivityMainBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

import java.security.cert.X509Certificate


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    var privateKey: PrivateKey? = null
    private var mCertificates: Array<X509Certificate?>? = null
    private var sslSocketFactory: SSLSocketFactory? = null
    var x509TrustManager: X509TrustManager? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpClient() //setting the certificates and sslSocketFactory
        setUpRetrofit()
        val webSettings = binding.webview.settings
        setUpWebsite(webSettings)
    }

    private fun setUpRetrofit() {
        val apiInterface =
            ApiInterface.create(sslSocketFactory, x509TrustManager).getServerResponse()
        apiInterface?.enqueue(object : Callback<String> {
            override fun onResponse(call: Call<String>?, response: Response<String>?) {
                if (response?.body() != null) {
                    Toast.makeText(
                        this@MainActivity,
                        response.body().toString(),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(this@MainActivity, "ERROR", Toast.LENGTH_SHORT).show()

                }
            }

            override fun onFailure(call: Call<String>?, t: Throwable?) {
                Toast.makeText(this@MainActivity, "Failure", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun setUpClient() {
        try {
            val SECRET =
                "secret" // You may also store this String somewhere more secure.
            val certificateFactory: CertificateFactory = CertificateFactory.getInstance("X.509")
            // Get private key
            val privateKeyInputStream: InputStream = resources.openRawResource(R.raw.cloudflarecrt)
            val privateKeyByteArray =
                ByteArray(privateKeyInputStream.available())
            privateKeyInputStream.read(privateKeyByteArray)
            val privateKeyContent = String(privateKeyByteArray, Charset.defaultCharset())
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace(System.lineSeparator().toRegex(), "")
                .replace("-----END PRIVATE KEY-----", "")
            val rawPrivateKeyByteArray: ByteArray =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Base64.getDecoder().decode(privateKeyContent)
                } else {
                    android.util.Base64.decode(privateKeyContent, android.util.Base64.DEFAULT)
                }
            val keyFactory: KeyFactory = KeyFactory.getInstance("RSA")
            val keySpec = PKCS8EncodedKeySpec(rawPrivateKeyByteArray)

            // Get certificate

            val certificateInputStream: InputStream = resources.openRawResource(R.raw.cloudflare)
            val certificate: Certificate =
                certificateFactory.generateCertificate(certificateInputStream)

            // Set up KeyStore
            val keyStore: KeyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            keyStore.load(null, SECRET.toCharArray())
            keyStore.setKeyEntry(
                "client",
                keyFactory.generatePrivate(keySpec),
                SECRET.toCharArray(),
                arrayOf(certificate)
            )

            //get privatekey and certificate for setting up client certificate for webview
            val aliases = keyStore.aliases()
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement()
                val key = keyStore.getKey(alias, SECRET.toCharArray())
                if (key is PrivateKey) {
                    privateKey = key
                    val arrayOfCertificate = keyStore.getCertificateChain(alias)
                    mCertificates = arrayOfNulls(arrayOfCertificate.size)
                    for (i in mCertificates!!.indices) {
                        mCertificates!![i] = arrayOfCertificate[i] as X509Certificate
                    }
                    break
                }

            }
            certificateInputStream.close()


            // Set up Trust Managers
            val trustManagerFactory: TrustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trustManagerFactory.init(null as KeyStore?)
            val trustManagers = trustManagerFactory.trustManagers

            // Set up Key Managers
            val keyManagerFactory: KeyManagerFactory =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            keyManagerFactory.init(keyStore, SECRET.toCharArray())
            val keyManagers: Array<KeyManager> = keyManagerFactory.keyManagers

            // Obtain SSL Socket Factory for retrofit calls
            val sslContext: SSLContext = SSLContext.getInstance("TLS")
            sslContext.init(keyManagers, trustManagers, SecureRandom())
            x509TrustManager = trustManagers[0] as X509TrustManager
            sslSocketFactory = sslContext.socketFactory

        } catch (e: CertificateException) {
            e.printStackTrace()

        } catch (e: IOException) {
            e.printStackTrace()

        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()

        } catch (e: KeyStoreException) {
            e.printStackTrace()

        } catch (e: UnrecoverableKeyException) {
            e.printStackTrace()

        } catch (e: KeyManagementException) {
            e.printStackTrace()

        } catch (e: InvalidKeySpecException) {
            e.printStackTrace()

        }
    }

    private fun setUpWebsite(webSettings: WebSettings) {
        webSettings.javaScriptEnabled = true
        webSettings.builtInZoomControls = false
        webSettings.displayZoomControls = false
        webSettings.loadWithOverviewMode = true
        webSettings.javaScriptCanOpenWindowsAutomatically = true
        webSettings.useWideViewPort = true
        webSettings.domStorageEnabled = true
        webSettings.allowContentAccess = true;
        webSettings.allowFileAccess = true;
        webSettings.setSupportMultipleWindows(true)
        binding.webview.overScrollMode = View.OVER_SCROLL_NEVER
        binding.webview.webViewClient = WebViewController(privateKey, mCertificates)
        binding.webview.loadUrl("https://mobile-sec-test.portal.sgd-cloud.com/");
    }

    class WebViewController(privateKey: PrivateKey?, certificates: Array<X509Certificate?>?) :
        WebViewClient() {
        var mPrivateKey = privateKey
        var mCertificates = certificates
        override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean {
            view.loadUrl(url!!)
            return true
        }

        override fun onReceivedSslError(
            view: WebView?,
            handler: SslErrorHandler,
            error: SslError?
        ) {
            handler.proceed() // Ignore SSL certificate errors
        }

        override fun onReceivedClientCertRequest(view: WebView?, request: ClientCertRequest) {
            if (mPrivateKey != null && mCertificates != null && mCertificates!!.isNotEmpty()) {
                request.proceed(mPrivateKey, mCertificates);
            } else {
                request.cancel();
            }
        }
    }


}