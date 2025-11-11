package com.core.utils


import android.text.Editable
import android.text.TextWatcher

/**
 * A small utility TextWatcher that only needs an `onTextChanged` lambda.
 * Example:
 * editText.addTextChangedListener(SimpleTextWatcher { text -> viewModel.updateValue(id, text) })
 */
class SimpleTextWatcher(private val onTextChanged: (String) -> Unit) : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { /* no-op */ }
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        onTextChanged.invoke(s?.toString() ?: "")
    }
    override fun afterTextChanged(s: Editable?) { /* no-op */ }
}
