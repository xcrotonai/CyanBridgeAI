package com.cyanbridge.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cyanbridge.databinding.FragmentChatBinding
import com.cyanbridge.databinding.ItemMessageAiBinding
import com.cyanbridge.databinding.ItemMessageUserBinding
import kotlinx.coroutines.launch

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: MainViewModel
    private val adapter = ChatAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm = (requireActivity() as MainActivity).viewModel

        binding.chatList.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.chatList.adapter = adapter

        // Send button
        binding.btnSend.setOnClickListener {
            val text = binding.chatInput.text?.toString()?.trim() ?: return@setOnClickListener
            if (text.isNotBlank()) {
                vm.sendMessage(text)
                binding.chatInput.setText("")
            }
        }

        // Quick action chips
        binding.chipDescribe.setOnClickListener { vm.sendMessage("Describe what I'm looking at right now") }
        binding.chipReadText.setOnClickListener { vm.sendMessage("Read any text visible to the glasses camera") }
        binding.chipPhoto.setOnClickListener { vm.takePhoto() }
        binding.chipClear.setOnClickListener { vm.clearHistory() }

        // Observe messages
        viewLifecycleOwner.lifecycleScope.launch {
            vm.chatMessages.collect { messages ->
                adapter.submitList(messages)
                binding.chatList.scrollToPosition(messages.size - 1)
            }
        }

        // Thinking indicator
        viewLifecycleOwner.lifecycleScope.launch {
            vm.isAiThinking.collect { thinking ->
                binding.thinkingIndicator.visibility = if (thinking) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<ChatMessage> = emptyList()

    fun submitList(list: List<ChatMessage>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) = if (items[position].isUser) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == 1) {
            UserVH(ItemMessageUserBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        } else {
            AiVH(ItemMessageAiBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = items[position]
        when (holder) {
            is UserVH -> holder.binding.messageText.text = msg.text
            is AiVH -> {
                holder.binding.messageText.text = msg.text
                holder.binding.providerLabel.text = msg.providerName
            }
        }
    }

    override fun getItemCount() = items.size

    inner class UserVH(val binding: ItemMessageUserBinding) : RecyclerView.ViewHolder(binding.root)
    inner class AiVH(val binding: ItemMessageAiBinding) : RecyclerView.ViewHolder(binding.root)
}
