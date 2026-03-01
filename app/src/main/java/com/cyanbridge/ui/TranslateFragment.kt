package com.cyanbridge.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.cyanbridge.databinding.FragmentTranslateBinding
import com.cyanbridge.translation.Languages
import kotlinx.coroutines.launch

class TranslateFragment : Fragment() {

    private var _binding: FragmentTranslateBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: MainViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTranslateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm = (requireActivity() as MainActivity).viewModel

        // Language picker
        val langNames = Languages.all.map { "${it.flag} ${it.name}" }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, langNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.langSpinner.adapter = adapter

        // Set current selection
        val currentIndex = Languages.all.indexOfFirst { it.code == vm.translator.targetLang }
        if (currentIndex >= 0) binding.langSpinner.setSelection(currentIndex)

        binding.langSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long) {
                vm.setTranslationTarget(Languages.all[pos].code)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // Translate typed text
        binding.btnTranslate.setOnClickListener {
            val text = binding.inputText.text?.toString()?.trim() ?: return@setOnClickListener
            if (text.isNotBlank()) {
                binding.resultCard.visibility = View.VISIBLE
                binding.resultText.text = "Translating..."
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = vm.translator.translate(text)
                    binding.resultText.text = result.translated
                }
            }
        }

        // Translate from glasses photo (OCR)
        binding.btnPhotoTranslate.setOnClickListener {
            binding.resultCard.visibility = View.VISIBLE
            binding.resultText.text = "Taking photo and extracting text..."
            vm.translateLastPhoto()
        }

        // Speak the result
        binding.btnSpeak.setOnClickListener {
            val text = binding.resultText.text?.toString() ?: return@setOnClickListener
            vm.translator.speak(text)
        }

        // Auto-speak toggle
        binding.autoSpeakSwitch.isChecked = vm.prefs.autoSpeakTranslation
        binding.autoSpeakSwitch.setOnCheckedChangeListener { _, checked ->
            vm.prefs.autoSpeakTranslation = checked
        }

        // Observe translation results
        viewLifecycleOwner.lifecycleScope.launch {
            vm.lastTranslation.collect { result ->
                if (result != null) {
                    binding.resultCard.visibility = View.VISIBLE
                    binding.resultText.text = result
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
