package org.staacks.alpharemote.ui

import android.widget.TextView
import androidx.databinding.BindingAdapter

@BindingAdapter("android:text")
fun setTextFromInteger(view: TextView, value: Int?) {
    value?.let {
        if (!it.toString().contentEquals(view.text))
            view.text = it.toString()
    }
}
