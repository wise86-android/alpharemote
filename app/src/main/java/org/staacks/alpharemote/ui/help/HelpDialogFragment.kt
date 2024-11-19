package org.staacks.alpharemote.ui.help

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import org.staacks.alpharemote.databinding.DialogFragmentHelpBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class HelpDialogFragment : BottomSheetDialogFragment() {
    private var _binding: DialogFragmentHelpBinding? = null
    private val binding get() = _binding!!
    private var setupTitleId: Int? = null
    private var setupTextId: Int? = null

    fun setContent(titleId: Int, textId: Int) = also {
        setupTitleId = titleId
        setupTextId = textId
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
         val helpViewModel = ViewModelProvider(this)[HelpDialogViewModel::class.java]
        setupTextId?.let {
            helpViewModel.textId = it
        }
        setupTitleId?.let {
            helpViewModel.titleId = it
        }

        _binding = DialogFragmentHelpBinding.inflate(layoutInflater)
        binding.viewModel = helpViewModel
        binding.fragment = this
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun openURL(target: String) {
        val uri = Uri.parse(target)
        val intent = Intent(Intent.ACTION_VIEW, uri)
        startActivity(intent)
    }
}