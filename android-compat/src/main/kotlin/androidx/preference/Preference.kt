@file:Suppress("UNUSED_PARAMETER")

package androidx.preference

import android.content.Context
import android.util.AttributeSet

open class Preference @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
) {
    private val mContext: Context? = context

    fun getContext(): Context? = mContext

    var key: String? = null
    var title: CharSequence? = null
    var summary: CharSequence? = null
    var isVisible: Boolean = true
    var isEnabled: Boolean = true
    var isSelectable: Boolean = true
    var isPersistent: Boolean = false
    var order: Int = 0
    var defaultValue: Any? = null

    var summaryProvider: Any? = null
    var isIconSpaceReserved: Boolean = true
    var isSingleLineTitle: Boolean = true
    var isRecycleEnabled: Boolean = true

    var onPreferenceChangeListener: OnPreferenceChangeListener? = null
    var onPreferenceClickListener: OnPreferenceClickListener? = null

    fun setTitle(resId: Int) {}
    fun setSummary(resId: Int) {}
    fun setLayoutResource(resId: Int) {}
    fun setWidgetLayoutResource(resId: Int) {}
    fun setDependency(dependencyKey: String?) {}
    fun setIcon(iconRes: Int) {}

    fun getSharedPreferences(): android.content.SharedPreferences? = null

    interface OnPreferenceChangeListener {
        fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean
    }

    interface OnPreferenceClickListener {
        fun onPreferenceClick(preference: Preference): Boolean
    }
}

open class PreferenceGroup @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    private val children = mutableListOf<Preference>()

    fun addPreference(preference: Preference): Boolean {
        children.add(preference)
        return true
    }

    fun removePreference(preference: Preference): Boolean = children.remove(preference)

    fun getPreferenceCount(): Int = children.size

    fun getPreference(index: Int): Preference? = children.getOrNull(index)

    fun isOnSameScreenAsChildren(): Boolean = false

    fun setInitialExpandedChildrenCount(count: Int) {}

    fun setOrderingAsAdded(orderingAsAdded: Boolean) {}

    fun getPreferences(): List<Preference> = children.toList()
}

class PreferenceCategory @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
) : PreferenceGroup(context, attrs)

class PreferenceScreen @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
) : PreferenceGroup(context, attrs)

open class TwoStatePreference @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {
    var isChecked: Boolean = false
    var summaryOn: CharSequence? = null
    var summaryOff: CharSequence? = null
}

class SwitchPreferenceCompat @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
) : TwoStatePreference(context, attrs)

open class DialogPreference @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {
    var dialogTitle: CharSequence? = null
    var dialogMessage: CharSequence? = null
    var dialogIcon: Int = 0
    var dialogLayoutResource: Int = 0
    var positiveButtonText: CharSequence? = null
    var negativeButtonText: CharSequence? = null

    fun setDialogTitle(resId: Int) {}
    fun setDialogMessage(resId: Int) {}
    fun setPositiveButtonText(resId: Int) {}
    fun setNegativeButtonText(resId: Int) {}
}

class EditTextPreference @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
) : DialogPreference(context, attrs) {

    var text: String? = null
    var onBindEditTextListener: OnBindEditTextListener? = null

    interface OnBindEditTextListener {
        fun onBindEditText(editText: Any?)
    }
}

open class ListPreference @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {
    var entries: Array<CharSequence>? = null
    var entryValues: Array<CharSequence>? = null
    var value: String? = null
}

class MultiSelectListPreference @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {
    var entries: Array<CharSequence>? = null
    var entryValues: Array<CharSequence>? = null
    var values: MutableSet<String> = mutableSetOf()
}

class CheckBoxPreference @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
) : TwoStatePreference(context, attrs)

class SwitchPreference @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
) : TwoStatePreference(context, attrs)

class SeekBarPreference @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {
    var value: Int = 0
    var max: Int = 100
    var min: Int = 0
    var showSeekBarValue: Boolean = true
}

class DropDownPreference @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
) : ListPreference(context, attrs)
