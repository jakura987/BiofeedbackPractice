package com.intellizondy.biofeedback.base

import android.view.View
import android.view.ViewGroup
import androidx.databinding.ViewDataBinding

abstract class Widget {
    abstract val rootView: View

    open fun mergeInto(parent: ViewGroup) {
        rootView.removeParent().let {
            parent.addView(it)
        }
    }


    private fun View.removeParent(): View {
        if (this.parent is ViewGroup) {
            (this.parent as ViewGroup).removeView(this)
        }
        return this
    }
}


open class LazyStatefulWidget<T : ViewDataBinding>(
    val bindingProvider: () -> T,
) : Widget() {
    val binding: T by lazy {
        bindingProvider()
    }

    fun setState(stateFn: (T) -> Unit) {
        binding.let(stateFn)
        binding.executePendingBindings()
    }

    override val rootView: View
        get() = binding.root
}