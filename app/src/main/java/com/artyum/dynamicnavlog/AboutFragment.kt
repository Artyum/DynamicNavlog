package com.artyum.dynamicnavlog

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.artyum.dynamicnavlog.databinding.FragmentAboutBinding

class AboutFragment : Fragment(R.layout.fragment_about) {
    private var _binding: FragmentAboutBinding? = null
    private val bind get() = _binding!!
    private lateinit var vm: GlobalViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return bind.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm = ViewModelProvider(requireActivity())[GlobalViewModel::class.java]
        bind.aboutLayout.keepScreenOn = vm.options.value!!.keepScreenOn
        (activity as MainActivity).displayButtons()

        // Clickable links
        bind.linkManual.movementMethod = LinkMovementMethod.getInstance()
        bind.linkEmail.movementMethod = LinkMovementMethod.getInstance()
        bind.linkChangeLog.movementMethod = LinkMovementMethod.getInstance()
        bind.linkPrivacyPolicy.movementMethod = LinkMovementMethod.getInstance()
        bind.linkPermissions.movementMethod = LinkMovementMethod.getInstance()
    }
}