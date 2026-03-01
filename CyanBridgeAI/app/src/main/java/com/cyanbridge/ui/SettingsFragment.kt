package com.cyanbridge.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.cyanbridge.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: MainViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm = (requireActivity() as MainActivity).viewModel

        // Load saved keys (masked)
        binding.geminiKeyInput.setText(if (vm.prefs.geminiKey.isNotBlank()) "••••••••" else "")
        binding.openaiKeyInput.setText(if (vm.prefs.openaiKey.isNotBlank()) "••••••••" else "")
        binding.claudeKeyInput.setText(if (vm.prefs.claudeKey.isNotBlank()) "••••••••" else "")
        binding.googleTranslateInput.setText(if (vm.prefs.googleTranslateKey.isNotBlank()) "••••••••" else "")

        binding.btnSaveKeys.setOnClickListener {
            fun read(field: com.google.android.material.textfield.TextInputEditText, saved: String): String {
                val t = field.text?.toString()?.trim() ?: ""
                return if (t == "••••••••") saved else t
            }
            vm.saveKeys(
                gemini = read(binding.geminiKeyInput, vm.prefs.geminiKey),
                openai = read(binding.openaiKeyInput, vm.prefs.openaiKey),
                claude = read(binding.claudeKeyInput, vm.prefs.claudeKey),
                googleTranslate = read(binding.googleTranslateInput, vm.prefs.googleTranslateKey)
            )
        }

        // Info links
        binding.geminiHelpText.text = "Get free key: aistudio.google.com"
        binding.openaiHelpText.text = "Get key: platform.openai.com"
        binding.claudeHelpText.text = "Get key: console.anthropic.com"
        binding.googleTranslateHelpText.text = "Optional. Free 500k chars/month. console.cloud.google.com"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
