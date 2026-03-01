package com.cyanbridge.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cyanbridge.databinding.FragmentDevicesBinding
import com.cyanbridge.databinding.ItemDeviceBinding
import com.cyanbridge.glasses.GlassesConnectionState
import com.cyanbridge.glasses.GlassesDevice
import kotlinx.coroutines.launch

class DevicesFragment : Fragment() {

    private var _binding: FragmentDevicesBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: MainViewModel
    private val adapter = DeviceAdapter { device ->
        vm.bleManager.connect(device.address, device.name)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDevicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm = (requireActivity() as MainActivity).viewModel

        binding.deviceList.layoutManager = LinearLayoutManager(requireContext())
        binding.deviceList.adapter = adapter

        binding.btnScan.setOnClickListener {
            vm.bleManager.startScan()
        }

        binding.btnDisconnect.setOnClickListener {
            vm.bleManager.disconnect()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.foundDevices.collect { devices ->
                adapter.submitList(devices)
                binding.emptyText.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.connectionState.collect { state ->
                when (state) {
                    is GlassesConnectionState.Scanning -> {
                        binding.btnScan.text = "🔍 Scanning..."
                        binding.btnScan.isEnabled = false
                        binding.scanProgress.visibility = View.VISIBLE
                    }
                    is GlassesConnectionState.Connected -> {
                        binding.btnScan.text = "🔍 Scan Again"
                        binding.btnScan.isEnabled = true
                        binding.scanProgress.visibility = View.GONE
                        binding.connectedCard.visibility = View.VISIBLE
                        binding.connectedName.text = "✅ ${state.name}"
                        binding.connectedAddr.text = state.address
                    }
                    else -> {
                        binding.btnScan.text = "🔍 Scan for Glasses"
                        binding.btnScan.isEnabled = true
                        binding.scanProgress.visibility = View.GONE
                        binding.connectedCard.visibility = View.GONE
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class DeviceAdapter(private val onConnect: (GlassesDevice) -> Unit) :
    RecyclerView.Adapter<DeviceAdapter.VH>() {

    private var items: List<GlassesDevice> = emptyList()

    fun submitList(list: List<GlassesDevice>) {
        items = list
        notifyDataSetChanged()
    }

    inner class VH(val binding: ItemDeviceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val d = items[position]
        holder.binding.deviceName.text = "🥽 ${d.name}"
        holder.binding.deviceAddress.text = d.address
        holder.binding.deviceRssi.text = "${d.rssi} dBm"
        holder.binding.root.setOnClickListener { onConnect(d) }
    }

    override fun getItemCount() = items.size
}
