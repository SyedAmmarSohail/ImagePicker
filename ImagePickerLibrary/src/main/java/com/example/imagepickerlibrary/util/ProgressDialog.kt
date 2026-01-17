package com.example.imagepickerlibrary.util

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import com.example.imagepickerlibrary.R
import com.example.imagepickerlibrary.databinding.DialogProgressBinding

object ProgressDialog {
    fun showProgressDialog(
        context: Context,
        msg: String,
        isCancelable: Boolean = false
    ): Dialog {

        val binding = DialogProgressBinding.inflate(LayoutInflater.from(context))

        val d = Dialog(context)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT));
        d.setContentView(binding.root)
        d.setCancelable(isCancelable)

        binding.tvMsg.text = msg

        return d
    }
}