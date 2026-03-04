package com.brainbuddy.app.quiz

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brainbuddy.app.core.AppModeManager
import com.brainbuddy.app.core.PremiumStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PastTestDetailState(
    val wrongItems: List<WrongQuestionUiItem> = emptyList(),
    val testId: String = "",
    val showCorrect: Boolean = false
)

class PastTestDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = PastTestDetailRepository(application)
    private val premiumStore = PremiumStore(application)

    private val _state = MutableStateFlow(PastTestDetailState())
    val state: StateFlow<PastTestDetailState> = _state.asStateFlow()

    fun load(testId: String) {
        if (testId.isBlank()) return
        val showCorrect = AppModeManager.isParentMode() && (
            premiumStore.isPremium() ||
            com.brainbuddy.app.core.DailyParentViewQuotaStore(getApplication()).canView()
        )
        _state.update { it.copy(testId = testId, showCorrect = showCorrect) }
        viewModelScope.launch {
            repo.getWrongQuestionItemsFlow(testId, showCorrect).collect { items ->
                _state.update { it.copy(wrongItems = items) }
            }
        }
    }

    fun unlockWrongAnswer(questionId: String, onNeedAd: (() -> Unit), onUnlocked: () -> Unit) {
        val testId = _state.value.testId
        if (testId.isBlank() || questionId.isBlank()) return
        if (premiumStore.isPremium()) {
            viewModelScope.launch {
                repo.unlockWrongAnswer(testId, questionId)
                onUnlocked()
            }
        } else {
            onNeedAd()
        }
    }

    fun performUnlockAfterAd(questionId: String) {
        val testId = _state.value.testId
        if (testId.isBlank() || questionId.isBlank()) return
        viewModelScope.launch {
            repo.unlockWrongAnswer(testId, questionId)
        }
    }
}
