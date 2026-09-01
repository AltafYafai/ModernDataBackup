package com.xayah.core.ui.util
sealed class OperationState { object Idle : OperationState(); data class Loading(val progress: Int) : OperationState(); data class Success(val result: String) : OperationState(); data class Error(val message: String) : OperationState() }
