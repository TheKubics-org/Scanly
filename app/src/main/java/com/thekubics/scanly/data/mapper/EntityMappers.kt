package com.thekubics.scanly.data.mapper

import com.thekubics.scanly.data.local.entity.CloudDocumentEntity
import com.thekubics.scanly.data.local.entity.DocumentEntity
import com.thekubics.scanly.data.local.entity.FolderEntity
import com.thekubics.scanly.data.local.entity.PageEntity
import com.thekubics.scanly.domain.model.CloudDocument
import com.thekubics.scanly.domain.model.Document
import com.thekubics.scanly.domain.model.FilterType
import com.thekubics.scanly.domain.model.Folder
import com.thekubics.scanly.domain.model.Page
import com.thekubics.scanly.domain.model.SyncStatus

fun DocumentEntity.toDomain(): Document {
    return Document(
        id = this.id,
        title = this.title,
        folderId = this.folderId,
        pageCount = this.pageCount,
        thumbnailPath = this.thumbnailPath,
        pdfPath = this.pdfPath,
        ocrText = this.ocrText,
        isEncrypted = this.isEncrypted,
        isTrashed = this.isTrashed,
        trashedAt = this.trashedAt,
        syncStatus = runCatching { SyncStatus.valueOf(this.syncStatus) }.getOrDefault(SyncStatus.LOCAL),
        cloudId = this.cloudId,
        fileSize = this.fileSize,
        lastSyncedAt = this.lastSyncedAt,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt
    )
}

fun Document.toEntity(): DocumentEntity {
    return DocumentEntity(
        id = this.id,
        title = this.title,
        folderId = this.folderId,
        pageCount = this.pageCount,
        thumbnailPath = this.thumbnailPath,
        pdfPath = this.pdfPath,
        ocrText = this.ocrText,
        isEncrypted = this.isEncrypted,
        isTrashed = this.isTrashed,
        trashedAt = this.trashedAt,
        syncStatus = this.syncStatus.name,
        cloudId = this.cloudId,
        fileSize = this.fileSize,
        lastSyncedAt = this.lastSyncedAt,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt
    )
}

fun PageEntity.toDomain(): Page {
    return Page(
        id = this.id,
        documentId = this.documentId,
        pageNumber = this.pageNumber,
        originalImagePath = this.originalImagePath,
        processedImagePath = this.processedImagePath,
        thumbnailPath = this.thumbnailPath,
        width = this.width,
        height = this.height,
        rotation = this.rotation,
        filter = try { FilterType.valueOf(this.filter) } catch (e: IllegalArgumentException) { FilterType.ORIGINAL },
        brightness = this.brightness,
        contrast = this.contrast,
        ocrText = this.ocrText,
        ocrConfidence = this.ocrConfidence,
        createdAt = this.createdAt
    )
}

fun Page.toEntity(): PageEntity {
    return PageEntity(
        id = this.id,
        documentId = this.documentId,
        pageNumber = this.pageNumber,
        originalImagePath = this.originalImagePath,
        processedImagePath = this.processedImagePath,
        thumbnailPath = this.thumbnailPath,
        width = this.width,
        height = this.height,
        rotation = this.rotation,
        filter = this.filter.name,
        brightness = this.brightness,
        contrast = this.contrast,
        ocrText = this.ocrText,
        ocrConfidence = this.ocrConfidence,
        createdAt = this.createdAt
    )
}

fun FolderEntity.toDomain(): Folder {
    return Folder(
        id = this.id,
        name = this.name,
        color = this.color,
        documentCount = this.documentCount,
        createdAt = this.createdAt
    )
}

fun Folder.toEntity(): FolderEntity {
    return FolderEntity(
        id = this.id,
        name = this.name,
        color = this.color,
        documentCount = this.documentCount,
        createdAt = this.createdAt
    )
}

fun CloudDocumentEntity.toDomain(): CloudDocument {
    return CloudDocument(
        id = this.id,
        localDocumentId = this.localDocumentId,
        title = this.title,
        fileType = this.fileType,
        pageCount = this.pageCount,
        fileSize = this.fileSize,
        thumbnailUrl = this.thumbnailUrl,
        cloudFileUrl = this.cloudFileUrl,
        uploadDate = this.uploadDate,
        syncStatus = runCatching { SyncStatus.valueOf(this.syncStatus) }.getOrDefault(SyncStatus.SYNCED)
    )
}

fun CloudDocument.toEntity(userId: String): CloudDocumentEntity {
    return CloudDocumentEntity(
        id = this.id,
        userId = userId,
        localDocumentId = this.localDocumentId,
        title = this.title,
        fileType = this.fileType,
        pageCount = this.pageCount,
        fileSize = this.fileSize,
        thumbnailUrl = this.thumbnailUrl,
        cloudFileUrl = this.cloudFileUrl,
        uploadDate = this.uploadDate,
        syncStatus = this.syncStatus.name
    )
}
