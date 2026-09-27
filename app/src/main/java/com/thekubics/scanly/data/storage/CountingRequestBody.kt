package com.thekubics.scanly.data.storage

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer

/**
 * Wraps a [RequestBody] and reports real upload progress while the underlying
 * bytes are written to the network. The wrapped delegate's bytes are passed
 * through unchanged, so signed payloads (AWS SigV4) and Content-Length stay valid.
 */
class CountingRequestBody(
    private val delegate: RequestBody,
    private val progressCallback: (bytesSent: Long, totalBytes: Long) -> Unit
) : RequestBody() {

    private var bytesWritten = 0L

    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun isOneShot(): Boolean = delegate.isOneShot()

    override fun writeTo(sink: BufferedSink) {
        val totalBytes = delegate.contentLength()
        val countingSink = object : ForwardingSink(sink) {
            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                bytesWritten += byteCount
                progressCallback(bytesWritten, totalBytes)
            }
        }
        val buffered = countingSink.buffer()
        delegate.writeTo(buffered)
        buffered.flush()
    }
}