package io.github.dokuendev.dokuen.plugins.dictionary.jpdb

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

class FakeHttpURLConnection(
    url: URL,
    private val responseCodeVal: Int,
    private val responseBody: String,
    private val errorBody: String = ""
) : HttpURLConnection(url) {
    val requestHeaders = mutableMapOf<String, String>()
    val outputStream = ByteArrayOutputStream()

    override fun connect() {}
    override fun disconnect() {}
    override fun usingProxy(): Boolean = false

    override fun getResponseCode(): Int = responseCodeVal

    override fun getInputStream(): InputStream {
        if (responseCodeVal >= 400) throw java.io.IOException("HTTP error $responseCodeVal")
        return ByteArrayInputStream(responseBody.toByteArray(Charsets.UTF_8))
    }

    override fun getErrorStream(): InputStream {
        val body = errorBody.ifEmpty { responseBody }
        return ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
    }

    override fun getOutputStream(): OutputStream {
        return outputStream
    }

    override fun setRequestProperty(key: String, value: String) {
        requestHeaders[key] = value
    }

    override fun getRequestProperty(key: String): String? {
        return requestHeaders[key]
    }
}
