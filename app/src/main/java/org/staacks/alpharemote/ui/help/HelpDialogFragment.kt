package org.staacks.alpharemote.ui.help

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.net.toUri
import org.staacks.alpharemote.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme
import androidx.compose.ui.res.stringResource

class HelpDialogFragment : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val titleId = requireArguments().getInt(ARG_TITLE_ID)
        val textId = requireArguments().getInt(ARG_TEXT_ID)

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BluetoothRemoteForSonyCamerasTheme {
                    HelpScreen(
                        title = stringResource(titleId),
                        helpText = stringResource(textId),
                        onFaqClick = { openURL(getString(R.string.faq_url)) }
                    )
                }
            }
        }
    }

    private fun openURL(target: String) {
        val intent = Intent(Intent.ACTION_VIEW, target.toUri())
        startActivity(intent)
    }

    companion object {
        private const val ARG_TITLE_ID = "arg_title_id"
        private const val ARG_TEXT_ID = "arg_text_id"

        /**
         * Creates a new instance of [HelpDialogFragment] with the given resource IDs.
         */
        fun newInstance(titleId: Int, textId: Int): HelpDialogFragment {
            return HelpDialogFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_TITLE_ID, titleId)
                    putInt(ARG_TEXT_ID, textId)
                }
            }
        }
    }
}
