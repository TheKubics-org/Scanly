package com.scanly.data.storage

import com.scanly.data.storage.s3.AwsSigV4Signer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class AwsSigV4SignerTest {

    @Test
    fun testEmptyPayloadSha256() {
        // Standard known SHA-256 for empty string
        val expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val actual = AwsSigV4Signer.sha256Hex("")
        assertEquals(expected, actual)
    }

    @Test
    fun testKnownStringSha256() {
        // Echo of 'Hello Scanly'
        val text = "Hello Scanly"
        val hash = AwsSigV4Signer.sha256Hex(text)
        assertNotNull(hash)
        assertEquals(64, hash.length)
    }

    @Test
    fun testSignRequestHeadersGenerated() {
        val fixedDate = Date(1700000000000L) // 2023-11-14T22:13:20Z
        val url = "https://1234567890abcdef.r2.cloudflarestorage.com/my-bucket/scans/test.pdf"
        val emptySha = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

        val signedHeaders = AwsSigV4Signer.sign(
            method = "PUT",
            url = url,
            headers = mapOf("Content-Type" to "application/pdf"),
            payloadSha256 = emptySha,
            date = fixedDate,
            accessKeyId = "AKIAIOSFODNN7EXAMPLE",
            secretAccessKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
            region = "auto",
            service = "s3"
        )

        assertTrue(signedHeaders.containsKey("x-amz-date"))
        assertTrue(signedHeaders.containsKey("x-amz-content-sha256"))
        assertTrue(signedHeaders.containsKey("Authorization"))
        assertEquals(emptySha, signedHeaders["x-amz-content-sha256"])

        val authHeader = signedHeaders["Authorization"]!!
        assertTrue(authHeader.startsWith("AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/"))
        assertTrue(authHeader.contains("SignedHeaders="))
        assertTrue(authHeader.contains("Signature="))
    }

    @Test
    fun testDeterministicSignature() {
        val fixedDate = Date(1609459200000L) // 2021-01-01T00:00:00Z
        val url = "https://s3.us-east-1.amazonaws.com/examplebucket/test.txt"
        val payloadHash = AwsSigV4Signer.sha256Hex("test content")

        val result1 = AwsSigV4Signer.sign(
            method = "GET",
            url = url,
            headers = emptyMap(),
            payloadSha256 = payloadHash,
            date = fixedDate,
            accessKeyId = "KEY123",
            secretAccessKey = "SECRET456",
            region = "us-east-1"
        )

        val result2 = AwsSigV4Signer.sign(
            method = "GET",
            url = url,
            headers = emptyMap(),
            payloadSha256 = payloadHash,
            date = fixedDate,
            accessKeyId = "KEY123",
            secretAccessKey = "SECRET456",
            region = "us-east-1"
        )

        assertEquals(result1["Authorization"], result2["Authorization"])
        assertEquals(result1["x-amz-date"], result2["x-amz-date"])
    }
}
