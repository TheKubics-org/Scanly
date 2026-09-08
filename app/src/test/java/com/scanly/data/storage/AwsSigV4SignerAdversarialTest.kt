package com.scanly.data.storage

import com.scanly.data.storage.s3.AwsSigV4Signer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class AwsSigV4SignerAdversarialTest {

    private val fixedDate = Date(1700000000000L) // 2023-11-14T22:13:20Z
    private val accessKey = "TESTACCESSKEY123"
    private val secretKey = "TESTSECRETKEY456/EXAMPLE"
    private val emptySha = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

    // 1. Path special characters tests
    @Test
    fun testStandardPathSigning() {
        val url = "https://s3.amazonaws.com/bucket/document.pdf"
        val headers = AwsSigV4Signer.sign(
            method = "GET",
            url = url,
            headers = emptyMap(),
            payloadSha256 = emptySha,
            date = fixedDate,
            accessKeyId = accessKey,
            secretAccessKey = secretKey
        )
        assertNotNull(headers["Authorization"])
        assertTrue(headers["Authorization"]!!.contains("Credential=$accessKey/20231114/auto/s3/aws4_request"))
    }

    @Test
    fun testSubsecondTimestampVariationsProduceSameSignature() {
        // AWS SigV4 date is second-resolution (yyyyMMdd'T'HHmmss'Z').
        // Two timestamps within the same second (e.g. ms = 100 vs ms = 900) should yield identical signatures.
        val date1 = Date(1700000000100L)
        val date2 = Date(1700000000900L)
        val url = "https://s3.amazonaws.com/bucket/doc.pdf"

        val res1 = AwsSigV4Signer.sign("GET", url, emptyMap(), emptySha, date1, accessKey, secretKey)
        val res2 = AwsSigV4Signer.sign("GET", url, emptyMap(), emptySha, date2, accessKey, secretKey)

        assertEquals(res1["x-amz-date"], res2["x-amz-date"])
        assertEquals(res1["Authorization"], res2["Authorization"])
    }

    @Test
    fun testHeaderCaseInsensitivityAndNormalization() {
        // Headers with uppercase, lowercase, mixed case should normalize to lowercase signed headers
        val mixedHeaders = mapOf(
            "Content-Type" to "application/pdf",
            "X-Amz-Storage-Class" to "STANDARD",
            "HOST" to "s3.amazonaws.com"
        )

        val result = AwsSigV4Signer.sign(
            method = "PUT",
            url = "https://s3.amazonaws.com/bucket/doc.pdf",
            headers = mixedHeaders,
            payloadSha256 = emptySha,
            date = fixedDate,
            accessKeyId = accessKey,
            secretAccessKey = secretKey
        )

        val auth = result["Authorization"]!!
        // SignedHeaders should list headers sorted in lowercase
        assertTrue(auth.contains("SignedHeaders=content-type;host;x-amz-content-sha256;x-amz-date;x-amz-storage-class"))
    }

    @Test
    fun testHeaderValueTrimming() {
        // Header values with leading or trailing whitespace should be trimmed in canonical headers
        val headersWithWhitespace = mapOf(
            "x-amz-meta-author" to "   Scanly User   "
        )
        val headersTrimmed = mapOf(
            "x-amz-meta-author" to "Scanly User"
        )

        val res1 = AwsSigV4Signer.sign("PUT", "https://s3.amazonaws.com/bucket/doc.pdf", headersWithWhitespace, emptySha, fixedDate, accessKey, secretKey)
        val res2 = AwsSigV4Signer.sign("PUT", "https://s3.amazonaws.com/bucket/doc.pdf", headersTrimmed, emptySha, fixedDate, accessKey, secretKey)

        assertEquals(res1["Authorization"], res2["Authorization"])
    }

    @Test
    fun testEmptyVsNonEmptyPayloadSha256() {
        val nonEmptysha = AwsSigV4Signer.sha256Hex("PDF Content Bytes")
        assertNotEquals(emptySha, nonEmptysha)

        val resEmpty = AwsSigV4Signer.sign("PUT", "https://s3.amazonaws.com/bucket/doc.pdf", emptyMap(), emptySha, fixedDate, accessKey, secretKey)
        val resNonEmpty = AwsSigV4Signer.sign("PUT", "https://s3.amazonaws.com/bucket/doc.pdf", emptyMap(), nonEmptysha, fixedDate, accessKey, secretKey)

        assertNotEquals(resEmpty["Authorization"], resNonEmpty["Authorization"])
        assertEquals(emptySha, resEmpty["x-amz-content-sha256"])
        assertEquals(nonEmptysha, resNonEmpty["x-amz-content-sha256"])
    }

    @Test
    fun testQueryStringOrderingAndEncoding() {
        // Query parameters should be sorted by key, then by value
        val urlUnordered = "https://s3.amazonaws.com/bucket?max-keys=10&delimiter=/&prefix=scans/"
        val urlOrdered = "https://s3.amazonaws.com/bucket?delimiter=/&max-keys=10&prefix=scans/"

        val res1 = AwsSigV4Signer.sign("GET", urlUnordered, emptyMap(), emptySha, fixedDate, accessKey, secretKey)
        val res2 = AwsSigV4Signer.sign("GET", urlOrdered, emptyMap(), emptySha, fixedDate, accessKey, secretKey)

        assertEquals(res1["Authorization"], res2["Authorization"])
    }

    @Test
    fun testEmptyValuedQueryParameter() {
        // AWS SigV4 specification: parameters without values must include '=' in canonical query (e.g. 'acl=')
        val url = "https://s3.amazonaws.com/bucket/doc.pdf?acl"
        val res = AwsSigV4Signer.sign("GET", url, emptyMap(), emptySha, fixedDate, accessKey, secretKey)
        assertNotNull(res["Authorization"])
    }

    @Test
    fun testCustomPortInUrl() {
        // S3 with custom port (e.g. MinIO on port 9000) should include port in Host header
        val url = "http://192.168.1.100:9000/mybucket/doc.pdf"
        val res = AwsSigV4Signer.sign("GET", url, emptyMap(), emptySha, fixedDate, accessKey, secretKey)

        assertEquals("192.168.1.100:9000", res["host"])
        assertTrue(res["Authorization"]!!.contains("host"))
    }

    @Test
    fun testDefaultPortsOmittedFromHostHeader() {
        val urlHttps = "https://s3.amazonaws.com:443/bucket/doc.pdf"
        val resHttps = AwsSigV4Signer.sign("GET", urlHttps, emptyMap(), emptySha, fixedDate, accessKey, secretKey)
        assertEquals("s3.amazonaws.com", resHttps["host"])

        val urlHttp = "http://s3.amazonaws.com:80/bucket/doc.pdf"
        val resHttp = AwsSigV4Signer.sign("GET", urlHttp, emptyMap(), emptySha, fixedDate, accessKey, secretKey)
        assertEquals("s3.amazonaws.com", resHttp["host"])
    }

    // Stress testing path characters: spaces, multi-byte UTF-8, punctuation
    @Test
    fun testPathWithRawSpaceThrowsOrEncodes() {
        // When url contains raw space: "https://s3.amazonaws.com/bucket/my scan.pdf"
        // java.net.URI(url) throws URISyntaxException if unencoded.
        try {
            AwsSigV4Signer.sign("GET", "https://s3.amazonaws.com/bucket/my scan.pdf", emptyMap(), emptySha, fixedDate, accessKey, secretKey)
        } catch (e: Exception) {
            println("Caught expected or unexpected exception for raw space in URL: ${e.javaClass.simpleName}: ${e.message}")
            assertTrue("Expected URISyntaxException or IllegalArgumentException for raw unencoded space",
                e is java.net.URISyntaxException || e is IllegalArgumentException)
        }
    }

    @Test
    fun testPathWithEncodedSpaces() {
        // Pre-encoded space '%20'
        val url = "https://s3.amazonaws.com/bucket/my%20scan.pdf"
        val res = AwsSigV4Signer.sign("GET", url, emptyMap(), emptySha, fixedDate, accessKey, secretKey)
        assertNotNull(res["Authorization"])
    }

    @Test
    fun testPathWithMultiByteUtf8() {
        // Multi-byte UTF-8 in URL path: Japanese / Cyrillic
        try {
            val url = "https://s3.amazonaws.com/bucket/スキャン_2026.pdf"
            val res = AwsSigV4Signer.sign("GET", url, emptyMap(), emptySha, fixedDate, accessKey, secretKey)
            assertNotNull(res["Authorization"])
        } catch (e: Exception) {
            println("Caught exception for multi-byte UTF-8 in URL: ${e.javaClass.simpleName}: ${e.message}")
            assertTrue(e is java.net.URISyntaxException || e is IllegalArgumentException)
        }
    }

    @Test
    fun testPathWithPercentEncodedUtf8() {
        // Percent-encoded UTF-8: '%E3%82%B9'
        val url = "https://s3.amazonaws.com/bucket/%E3%82%B9%E3%82%AD%E3%83%A3%E3%83%B3.pdf"
        val res = AwsSigV4Signer.sign("GET", url, emptyMap(), emptySha, fixedDate, accessKey, secretKey)
        assertNotNull(res["Authorization"])
    }

    @Test
    fun testConsecutiveSlashesInPath() {
        val url = "https://s3.amazonaws.com/bucket//folder//doc.pdf"
        val res = AwsSigV4Signer.sign("GET", url, emptyMap(), emptySha, fixedDate, accessKey, secretKey)
        assertNotNull(res["Authorization"])
    }

    @Test
    fun testTildeAndAsteriskInQuery() {
        // Test query parameters with tilde and asterisk
        val url = "https://s3.amazonaws.com/bucket?filter=~temp*&other=value"
        val res = AwsSigV4Signer.sign("GET", url, emptyMap(), emptySha, fixedDate, accessKey, secretKey)
        assertNotNull(res["Authorization"])
    }
}
