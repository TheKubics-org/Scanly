package com.thekubics.scanly.domain.repository

import com.thekubics.scanly.domain.model.Document
import com.thekubics.scanly.domain.model.Page
import kotlinx.coroutines.flow.Flow

/**
 * Interface defining data operations for [Document] and [Page] entities.
 */
interface DocumentRepository {

    /**
     * Retrieves all active documents (not trashed).
     */
    fun getAllDocuments(): Flow<List<Document>>

    /**
     * Retrieves a single document by its ID.
     */
    fun getDocumentById(id: String): Flow<Document?>

    /**
     * Retrieves documents belonging to a specific folder.
     */
    fun getDocumentsByFolder(folderId: String): Flow<List<Document>>

    /**
     * Searches for documents matching the given query in title or OCR text.
     */
    fun searchDocuments(query: String): Flow<List<Document>>

    /**
     * Retrieves documents that are currently in the trash.
     */
    fun getTrashedDocuments(): Flow<List<Document>>

    /**
     * Creates a new document from the given page image paths.
     */
    suspend fun createDocument(title: String, pageImagePaths: List<String>): Document

    /**
     * Imports PDF / PNG / JPG / JPEG files into a new document (PDF pages are rasterized).
     */
    suspend fun importFiles(title: String, fileUris: List<String>): Document

    /**
     * Updates an existing document's metadata.
     */
    suspend fun updateDocument(document: Document)

    /**
     * Renames an existing document.
     */
    suspend fun renameDocument(docId: String, newTitle: String)

    /**
     * Moves a document to a specific folder, or removes it from any folder if null.
     */
    suspend fun moveToFolder(docId: String, folderId: String?)

    /**
     * Moves a document to the trash.
     */
    suspend fun moveToTrash(docId: String)

    /**
     * Restores a document from the trash to the active list.
     */
    suspend fun restoreFromTrash(docId: String)

    /**
     * Permanently deletes a document and its associated files.
     */
    suspend fun permanentlyDelete(docId: String)

    /**
     * Purges documents that have been in the trash longer than the retention period.
     */
    suspend fun purgeOldTrash()

    /**
     * Empties all documents currently in the trash, permanently deleting and shredding their files.
     */
    suspend fun emptyAllTrash()

    /**
     * Merges multiple documents into a single document.
     */
    suspend fun mergeDocuments(docIds: List<String>, newTitle: String): Document

    /**
     * Splits a document into two documents at the given page index.
     * The page at [splitAtPage] will become the first page of the new document.
     */
    suspend fun splitDocument(docId: String, splitAtPage: Int): Pair<Document, Document>

    /**
     * Retrieves all pages belonging to a specific document, ordered by page number.
     */
    fun getPages(documentId: String): Flow<List<Page>>

    /**
     * Updates an existing page's metadata or filter settings.
     */
    suspend fun updatePage(page: Page)

    /**
     * Deletes a specific page from a document.
     */
    suspend fun deletePage(pageId: String)

    /**
     * Duplicates an existing page within the same document.
     */
    suspend fun duplicatePage(pageId: String)

    /**
     * Reorders pages within a document to match the provided list of IDs.
     */
    suspend fun reorderPages(documentId: String, pageIds: List<String>)

    /**
     * Appends new pages to the end of an existing document.
     */
    suspend fun addPages(documentId: String, pageImagePaths: List<String>)

    /**
     * Updates extracted OCR text for a document and page to enable full-text search.
     */
    suspend fun updateOcrText(documentId: String, pageId: String, ocrText: String)

    /**
     * Regenerates thumbnails for all non-trashed documents when the thumbnail
     * generation version stored in prefs is older than [Constants.THUMBNAIL_VERSION].
     * Returns the number of documents whose thumbnails were regenerated (0 if no-op).
     */
    suspend fun regenerateThumbnailsIfStale(): Int
}
