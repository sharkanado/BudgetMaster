package com.example.budgetmaster.ui.components

import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.budgetmaster.R
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

class BudgetSplitMembersAdapter(
    private val members: List<BudgetMemberItem>,
    private val selected: MutableSet<String>,
    private val sharesByUid: MutableMap<String, Double>,
    private val totalProvider: () -> Double,
    private val onCheckedChanged: (uid: String, checked: Boolean) -> Unit,
    private val onShareEditedValid: (uid: String, newValue: Double) -> Unit,
    private val onStartEditing: () -> Unit,
    private val onStopEditing: () -> Unit
) : RecyclerView.Adapter<BudgetSplitMembersAdapter.VH>() {

    private val syms = DecimalFormatSymbols(Locale.getDefault()).apply {
        decimalSeparator = '.'
        groupingSeparator = ' '
    }
    private val df2 = DecimalFormat("0.00").apply {
        decimalFormatSymbols = syms
        isGroupingUsed = false
    }

    private var internalUpdate = false
    private var editingUid: String? = null

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkBox: CheckBox = itemView.findViewById(R.id.memberCheckbox)
        val name: TextView = itemView.findViewById(R.id.memberName)
        val email: TextView = itemView.findViewById(R.id.memberEmail)
        val amountEdit: EditText = itemView.findViewById(R.id.memberShareEdit)
        var tw: TextWatcher? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_budget_member_checkbox, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = members.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val m = members[position]
        val uid = m.uid

        holder.name.text = m.name
        holder.email.text = m.email

        // Checkbox handling
        holder.checkBox.setOnCheckedChangeListener(null)
        val isChecked = selected.contains(uid)
        holder.checkBox.isChecked = isChecked
        applyEnabledState(holder, isChecked)

        holder.checkBox.setOnCheckedChangeListener { _, checked ->
            onCheckedChanged(uid, checked)
            applyEnabledState(holder, checked)
        }

        holder.tw?.let { holder.amountEdit.removeTextChangedListener(it) }

        val value = sharesByUid[uid] ?: 0.0
        setEditTextSafely(holder.amountEdit, df2.format(value))

        holder.amountEdit.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                editingUid = uid
                onStartEditing()
            } else if (editingUid == uid) {
                editingUid = null
                onStopEditing()
            }
        }

        holder.amountEdit.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                v.clearFocus()
                true
            } else false
        }
        holder.amountEdit.setOnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                v.clearFocus(); true
            } else false
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (internalUpdate) return

                val cur = s?.toString() ?: ""
                if (cur.contains(',')) {
                    val sel = holder.amountEdit.selectionStart
                    internalUpdate = true
                    val fixed = cur.replace(',', '.')
                    holder.amountEdit.setText(fixed)
                    val newSel = (sel + (fixed.length - cur.length)).coerceIn(0, fixed.length)
                    holder.amountEdit.setSelection(newSel)
                    internalUpdate = false
                    return
                }

                if (editingUid != uid) return
                if (!holder.amountEdit.isEnabled) return

                val raw = (s?.toString() ?: "").replace(',', '.')
                val total = totalProvider()
                val parsed = raw.toDoubleOrNull() ?: return

                if (parsed <= 0.0) {
                    holder.amountEdit.error =
                        holder.itemView.context.getString(R.string.error_negative_not_allowed)
                    return
                }
                if (parsed > total) {
                    holder.amountEdit.error =
                        holder.itemView.context.getString(R.string.error_exceeds_total)
                    return
                }

                holder.amountEdit.error = null

                onShareEditedValid(uid, parsed)

                refreshVisibleSharesExcept(holder.itemView.parent as RecyclerView, uid)
            }
        }
        holder.amountEdit.addTextChangedListener(watcher)
        holder.tw = watcher
    }

    private fun applyEnabledState(holder: VH, enabled: Boolean) {
        holder.amountEdit.isEnabled = enabled
        holder.amountEdit.isFocusable = enabled
        holder.amountEdit.isFocusableInTouchMode = enabled
        holder.amountEdit.isCursorVisible = enabled
        holder.amountEdit.alpha = if (enabled) 1f else 0.5f

        if (!enabled) {
            if (holder.amountEdit.isFocused) {
                holder.amountEdit.clearFocus()
                if (editingUid == members.getOrNull(holder.adapterPosition)?.uid) {
                    editingUid = null
                    onStopEditing()
                }
            }
            holder.amountEdit.error = null
        }
    }

    /**
     * Update visible holders' EditTexts to reflect the new shares,
     * skipping the row with [skipUid]. This is intended for the
     * "editing a single row" case to avoid cursor jumps.
     */
    fun refreshVisibleSharesExcept(recyclerView: RecyclerView, skipUid: String?) {
        recyclerView.post {
            if (recyclerView.isComputingLayout) {
                recyclerView.post { refreshVisibleSharesExcept(recyclerView, skipUid) }
                return@post
            }
            internalUpdate = true
            try {
                val childCount = recyclerView.childCount
                for (i in 0 until childCount) {
                    val child = recyclerView.getChildAt(i) ?: continue
                    val holder = recyclerView.getChildViewHolder(child) as? VH ?: continue

                    val pos = holder.adapterPosition
                    if (pos == RecyclerView.NO_POSITION || pos < 0 || pos >= members.size) continue

                    val uid = members[pos].uid
                    if (uid == skipUid) continue
                    if (holder.amountEdit.isFocused) continue

                    val target = df2.format(sharesByUid[uid] ?: 0.0)
                    if (holder.amountEdit.text.toString() != target) {
                        holder.amountEdit.setText(target)
                        holder.amountEdit.setSelection(target.length)
                    }

                    applyEnabledState(holder, selected.contains(uid))
                }
            } finally {
                internalUpdate = false
            }
        }
    }

    private fun setEditTextSafely(edit: EditText, text: String) {
        internalUpdate = true
        try {
            if (edit.text.toString() != text) {
                edit.setText(text)
                edit.setSelection(text.length)
            }
        } finally {
            internalUpdate = false
        }
    }
}