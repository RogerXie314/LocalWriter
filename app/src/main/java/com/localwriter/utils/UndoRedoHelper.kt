package com.localwriter.utils

import android.text.TextWatcher
import android.text.Editable
import android.widget.EditText
import java.util.LinkedList

/**
 * EditText 撤销/重做扩展
 * 基于 TextWatcher 记录每次文字变更，支持最多 200 步撤销栈。
 */
class UndoRedoHelper(private val editText: EditText) {

    private data class EditAction(
        val start: Int,
        val before: CharSequence,
        val after: CharSequence
    )

    private val undoStack = LinkedList<EditAction>()
    private val redoStack = LinkedList<EditAction>()
    private var isUndoRedo = false
    private var lastStart = 0
    private var lastBefore: CharSequence = ""
    private val maxHistorySize = 200

    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            if (!isUndoRedo) {
                lastStart = start
                lastBefore = s.subSequence(start, start + count)
            }
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            if (!isUndoRedo) {
                val after = s.subSequence(start, start + count)
                undoStack.push(EditAction(lastStart, lastBefore, after))
                redoStack.clear()
                if (undoStack.size > maxHistorySize) {
                    undoStack.removeLast()
                }
            }
        }

        override fun afterTextChanged(s: Editable) {}
    }

    init {
        editText.addTextChangedListener(watcher)
    }

    fun canUndo() = undoStack.isNotEmpty()
    fun canRedo() = redoStack.isNotEmpty()

    fun undo() {
        if (undoStack.isEmpty()) return
        val action = undoStack.pop()
        redoStack.push(action)
        isUndoRedo = true
        editText.text.replace(action.start, action.start + action.after.length, action.before)
        editText.setSelection((action.start + action.before.length).coerceIn(0, editText.text.length))
        isUndoRedo = false
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val action = redoStack.pop()
        undoStack.push(action)
        isUndoRedo = true
        editText.text.replace(action.start, action.start + action.before.length, action.after)
        editText.setSelection((action.start + action.after.length).coerceIn(0, editText.text.length))
        isUndoRedo = false
    }

    fun clearHistory() {
        undoStack.clear()
        redoStack.clear()
    }
}
