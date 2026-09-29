package com.thekubics.scanly.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thekubics.scanly.domain.model.DateFilter
import com.thekubics.scanly.domain.model.Document
import com.thekubics.scanly.domain.model.Folder
import com.thekubics.scanly.domain.repository.DocumentRepository
import com.thekubics.scanly.domain.repository.FolderRepository
import com.thekubics.scanly.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortOrder { DATE_DESC, DATE_ASC, NAME_ASC, NAME_DESC, PAGE_COUNT }
enum class ViewType { GRID, LIST }

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val folderRepository: FolderRepository
) : ViewModel() {

    init {
        viewModelScope.launch {
            documentRepository.regenerateThumbnailsIfStale()
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DATE_DESC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _dateFilter = MutableStateFlow(DateFilter.ALL)
    val dateFilter: StateFlow<DateFilter> = _dateFilter.asStateFlow()

    private val _viewType = MutableStateFlow(ViewType.GRID)
    val viewType: StateFlow<ViewType> = _viewType.asStateFlow()

    val folders: StateFlow<List<Folder>> = folderRepository.getAllFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredDocuments: StateFlow<List<Document>> = combine(
        documentRepository.getAllDocuments(),
        _searchQuery,
        _sortOrder,
        _dateFilter
    ) { docs, query, order, dateFilter ->
        val windowStart = DateUtils.dateFilterStart(dateFilter)
        val filtered = docs
            .filter { windowStart == null || it.updatedAt >= windowStart }
            .let { list ->
                if (query.isBlank()) list
                else list.filter { it.title.contains(query, ignoreCase = true) }
            }

        when (order) {
            SortOrder.DATE_DESC -> filtered.sortedByDescending { it.updatedAt }
            SortOrder.DATE_ASC -> filtered.sortedBy { it.updatedAt }
            SortOrder.NAME_ASC -> filtered.sortedBy { it.title }
            SortOrder.NAME_DESC -> filtered.sortedByDescending { it.title }
            SortOrder.PAGE_COUNT -> filtered.sortedByDescending { it.pageCount }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun setDateFilter(filter: DateFilter) {
        _dateFilter.value = filter
    }

    fun toggleViewType() {
        _viewType.value = if (_viewType.value == ViewType.GRID) ViewType.LIST else ViewType.GRID
    }

    fun moveToTrash(docId: String) {
        viewModelScope.launch {
            documentRepository.moveToTrash(docId)
        }
    }

    fun renameDocument(docId: String, newTitle: String) {
        viewModelScope.launch {
            documentRepository.renameDocument(docId, newTitle)
        }
    }

    fun moveToFolder(docId: String, folderId: String?) {
        viewModelScope.launch {
            documentRepository.moveToFolder(docId, folderId)
        }
    }

    fun importFiles(uris: List<String>, title: String = "Imported Document", onDone: (String?) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val doc = documentRepository.importFiles(title, uris)
                onDone(doc.id)
            } catch (_: Exception) {
                onDone(null)
            }
        }
    }
}
