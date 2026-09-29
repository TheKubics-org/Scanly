package com.thekubics.scanly.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thekubics.scanly.domain.model.DateFilter
import com.thekubics.scanly.domain.model.Document
import com.thekubics.scanly.domain.repository.DocumentRepository
import com.thekubics.scanly.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val documentRepository: DocumentRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _dateFilter = MutableStateFlow(DateFilter.ALL)
    val dateFilter: StateFlow<DateFilter> = _dateFilter.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<Document>> = combine(
        _searchQuery
            .debounce(300L)
            .onEach { _isSearching.value = true }
            .flatMapLatest { query ->
                if (query.isBlank()) flowOf(emptyList())
                else documentRepository.searchDocuments(query)
            },
        _dateFilter
    ) { results, dateFilter ->
        val windowStart = DateUtils.dateFilterStart(dateFilter)
        if (windowStart == null) results
        else results.filter { it.updatedAt >= windowStart }
    }
        .onEach { _isSearching.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateQuery(query: String) {
        _searchQuery.value = query
    }

    fun setDateFilter(filter: DateFilter) {
        _dateFilter.value = filter
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }
}
