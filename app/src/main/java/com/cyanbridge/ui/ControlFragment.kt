package com.cyanbridge.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.cyanbridge.ai.AiProvider
import com.cyanbridge.databinding.FragmentControlBinding
import com.cyanbridge.glasses.GlassesConnectionState
import kotlinx.coroutines.launch

class ControlFragment : Fragment() {

    private var _binding: FragmentControlBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: MainViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentControlBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm = (requireActivity() as MainActivity).viewModel

        // ── Connection status ──
        viewLifecycleOwner.lifecycleScope.launch {
            vm.connectionState.collect { state ->
                when (state) {
                    is GlassesConnectionState.Connected -> {
                        binding.statusChip.text = "● Connected: ${state.name}"
                        binding.statusChip.setChipBackgroundColorResource(android.R.color.holo_green_dark)
                    }
                    is GlassesConnectionState.Connecting -> {
                        binding.statusChip.text = "⟳ Connecting..."
                        binding.statusChip.setChipBackgroundColorResource(android.R.color.holo_orange_dark)
                    }
                    else -> {
                        binding.statusChip.text = "○ Disconnected"
                        binding.statusChip.setChipBackgroundColorResource(android.R.color.darker_gray)
                    }
                }
            }
        }

        // ── Battery + media stats ──
        viewLifecycleOwner.lifecycleScope.launch {
            vm.glassesStatus.collect { status ->
                binding.batteryText.text = if (status.isCharging)
                    "⚡ ${status.batteryLevel}%"
                else
                    "🔋 ${status.batteryLevel}%"
                binding.batteryBar.progress = status.batteryLevel
                binding.photoCountText.text = "${status.photoCount}"
                binding.videoCountText.text = "${status.videoCount}"
                binding.audioCountText.text = "${status.audioCount}"
            }
        }

        // ── Recording state ──
        viewLifecycleOwner.lifecycleScope.launch {
            vm.isRecordingVideo.collect { recording ->
                binding.btnVideo.text = if (recording) "⏹ Stop Video" else "🎬 Video"
                binding.btnVideo.alpha = if (recording) 1f else 0.85f
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.isRecordingAudio.collect { recording ->
                binding.btnAudio.text = if (recording) "⏹ Stop Audio" else "🎙️ Audio"
            }
        }

        // ── AI Provider buttons ──
        binding.btnGemini.setOnClickListener { vm.setActiveProvider(AiProvider.GEMINI) }
        binding.btnOpenai.setOnClickListener { vm.setActiveProvider(AiProvider.OPENAI) }
        binding.btnClaude.setOnClickListener { vm.setActiveProvider(AiProvider.CLAUDE) }

        // ── Capture buttons ──
        binding.btnPhoto.setOnClickListener { vm.takePhoto() }
        binding.btnVideo.setOnClickListener { vm.toggleVideo() }
        binding.btnAudio.setOnClickListener { vm.toggleAudio() }
        binding.btnAiImage.setOnClickListener { vm.generateAiImage() }

        // ── Refresh ──
        binding.btnRefresh.setOnClickListener {
            vm.bleManager.getBattery()
            vm.bleManager.getMediaCount()
        }

        binding.btnSyncTime.setOnClickListener {
            vm.bleManager.syncTime()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
