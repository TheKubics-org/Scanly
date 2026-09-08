package com.scanly.data.storage.s3

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.TreeMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Pure-Kotlin zero-bloat AWS Signature Version 4 signer for S3-compatible endpoints.
 * Operates without external AWS SDK dependencies.
 */
object AwsSigV4Signer {

    private const val ALGORITHM = "AWS4-HMAC-SHA256"
    private const val HMAC_SHA256 = "HmacSHA256"

    /**
     * Signs an HTTP request and returns headers including `x-amz-date`, `x-amz-content-sha256`,
     * and `Authorization`.
     */
    fun sign(
        method: String,
        url: String,
        headers: Map<String, String>,
        payloadSha256: String,
        date: Date = Date(),
        accessKeyId: String,
        secretAccessKey: String,
        region: String = "auto",
        service: String = "s3"
    ): Map<String, String> {
        val uri = URI(url)
        val isoDateFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val dateStampFormat = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val amzDate = isoDateFormat.format(date)
        val dateStamp = dateStampFormat.format(date)

        val updatedHeaders = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER).apply {
            putAll(headers)
            put("x-amz-date", amzDate)
            put("x-amz-content-sha256", payloadSha256)
            if (!containsKey("host")) {
                val portSuffix = if (uri.port != -1 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""
                put("host", "${uri.host}$portSuffix")
            }
        }

        // Canonical URI
        val canonicalUri = getCanonicalUri(uri.rawPath)

        // Canonical Query String
        val canonicalQuery = getCanonicalQueryString(uri.rawQuery)

        // Canonical Headers & Signed Headers
        val canonicalHeadersBuilder = StringBuilder()
        val signedHeadersList = mutableListOf<String>()

        for ((key, value) in updatedHeaders) {
            val lowerKey = key.lowercase(Locale.US)
            signedHeadersList.add(lowerKey)
            canonicalHeadersBuilder.append(lowerKey).append(":").append(value.trim()).append("\n")
        }
        val signedHeaders = signedHeadersList.joinToString(";")

        // Canonical Request
        val canonicalRequest = listOf(
            method.uppercase(Locale.US),
            canonicalUri,
            canonicalQuery,
            canonicalHeadersBuilder.toString(),
            signedHeaders,
            payloadSha256
        ).joinToString("\n")

        val canonicalRequestHash = sha256Hex(canonicalRequest.toByteArray(StandardCharsets.UTF_8))

        // Credential Scope
        val credentialScope = "$dateStamp/$region/$service/aws4_request"

        // String to Sign
        val stringToSign = listOf(
            ALGORITHM,
            amzDate,
            credentialScope,
            canonicalRequestHash
        ).joinToString("\n")

        // Calculate Signature
        val signingKey = getSignatureKey(secretAccessKey, dateStamp, region, service)
        val signature = bytesToHex(hmacSha256(signingKey, stringToSign.toByteArray(StandardCharsets.UTF_8)))

        // Authorization Header
        val authorizationHeader = "$ALGORITHM Credential=$accessKeyId/$credentialScope, " +
                "SignedHeaders=$signedHeaders, Signature=$signature"

        return updatedHeaders.toMutableMap().apply {
            put("Authorization", authorizationHeader)
        }
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return bytesToHex(digest.digest(bytes))
    }

    fun sha256Hex(text: String): String {
        return sha256Hex(text.toByteArray(StandardCharsets.UTF_8))
    }

    private fun getCanonicalUri(path: String?): String {
        if (path.isNullOrEmpty()) return "/"
        return path.split("/").joinToString("/") { segment ->
            if (segment.isEmpty()) "" else URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }
    }

    private fun getCanonicalQueryString(query: String?): String {
        if (query.isNullOrEmpty()) return ""
        val params = query.split("&").filter { it.isNotEmpty() }
        val sortedParams = params.map { param ->
            val parts = param.split("=", limit = 2)
            val key = URLEncoder.encode(parts[0], "UTF-8").replace("+", "%20")
            val value = if (parts.size > 1) URLEncoder.encode(parts[1], "UTF-8").replace("+", "%20") else ""
            key to value
        }.sortedWith(compareBy({ it.first }, { it.second }))

        return sortedParams.joinToString("&") { "${it.first}=${it.second}" }
    }

    private fun getSignatureKey(key: String, dateStamp: String, regionName: String, serviceName: String): ByteArray {
        val kSecret = ("AWS4$key").toByteArray(StandardCharsets.UTF_8)
        val kDate = hmacSha256(kSecret, dateStamp.toByteArray(StandardCharsets.UTF_8))
        val kRegion = hmacSha256(kDate, regionName.toByteArray(StandardCharsets.UTF_8))
        val kService = hmacSha256(kRegion, serviceName.toByteArray(StandardCharsets.UTF_8))
        return hmacSha256(kService, "aws4_request".toByteArray(StandardCharsets.UTF_8))
    }

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_SHA256)
        mac.init(SecretKeySpec(key, HMAC_SHA256))
        return mac.doFinal(data)
    }

    fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        val hexArray = "0123456789abcdef".toCharArray()
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = hexArray[v ushr 4]
            hexChars[i * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }
}
