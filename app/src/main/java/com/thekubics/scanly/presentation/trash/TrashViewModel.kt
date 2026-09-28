package com.thekubics.scanly.presentation.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thekubics.scanly.domain.model.Document
import com.thekubics.scanly.domain.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val documentRepository: DocumentRepository
) : ViewModel() {

    private val _trashedDocuments = MutableStateFlow<List<Document>>(emptyList())
    val trashedDocuments: StateFlow<List<Document>> = _trashedDocuments.asStateFlow()

    init {
        viewModelScope.launch {
            documentRepository.getTrashedDocuments().collect {
                _trashedDocuments.value = it
            }
        }
    }

    fun restoreDocument(docId: String) {
        viewModelScope.launch {
            documentRepository.restoreFromTrash(docId)
        }
    }

    fun permanentlyDelete(docId: String) {
        viewModelScope.launch {
            documentRepository.permanentlyDelete(docId)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            documentRepository.emptyAllTrash()
        }
    }
}
