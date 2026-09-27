package com.thekubics.scanly.presentation.folders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thekubics.scanly.domain.model.Document
import com.thekubics.scanly.domain.repository.DocumentRepository
import com.thekubics.scanly.domain.repository.FolderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FolderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val documentRepository: DocumentRepository,
    private val folderRepository: FolderRepository
) : ViewModel() {

    val folderId: String = checkNotNull(savedStateHandle["folderId"])
    
    private val _documents = MutableStateFlow<List<Document>>(emptyList())
    val documents: StateFlow<List<Document>> = _documents.asStateFlow()
    
    private val _folderName = MutableStateFlow("")
    val folderName: StateFlow<String> = _folderName.asStateFlow()

    private val _folderColor = MutableStateFlow(0xFF4285F4)
    val folderColor: StateFlow<Long> = _folderColor.asStateFlow()

    init {
        viewModelScope.launch {
            documentRepository.getDocumentsByFolder(folderId).collect {
                _documents.value = it
            }
        }
        viewModelScope.launch {
            folderRepository.getFolderById(folderId).collect { folder ->
                folder?.let {
                    _folderName.value = it.name
                    _folderColor.value = it.color
                }
            }
        }
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

    fun removeFromFolder(docId: String) {
        viewModelScope.launch {
            documentRepository.moveToFolder(docId, null)
        }
    }
}
