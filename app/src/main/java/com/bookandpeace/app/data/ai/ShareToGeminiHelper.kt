package com.bookandpeace.app.data.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object ShareToGeminiHelper {

    private const val GEMINI_PACKAGE = "com.google.android.apps.bard"
    const val AI_STUDIO_KEY_URL = "https://aistudio.google.com/apikey"

    fun isGeminiAppInstalled(context: Context): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(GEMINI_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            true
        }.recoverCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(GEMINI_PACKAGE, 0)
            true
        }.getOrDefault(false)
    }

    fun copyPromptAndOpenGeminiApp(
        context: Context,
        bookTitle: String,
        author: String,
        fullText: String,
        styleMode: String
    ): Boolean {
        val prompt = VanVoPromptBuilder.buildShareablePromptForGeminiApp(
            bookTitle = bookTitle,
            author = author,
            bookContent = fullText,
            styleMode = styleMode
        )

        // Always copy to clipboard too so user can paste effortlessly if Gemini opens to chat box
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("VanVoPrompt", prompt))

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Kịch bản Văn Vở: $bookTitle")
            putExtra(Intent.EXTRA_TEXT, prompt)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (isGeminiAppInstalled(context)) {
                setPackage(GEMINI_PACKAGE)
            }
        }

        return runCatching {
            context.startActivity(shareIntent)
            true
        }.recoverCatching {
            val chooser = Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, prompt)
                },
                "Gửi sang Gemini để viết kịch bản Văn Vở"
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    fun shareAudioFile(context: Context, file: File, title: String) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Chia sẻ file Sách nói"))
    }

    fun openAiStudioApiKeyPage(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AI_STUDIO_KEY_URL)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun readClipboardText(context: Context): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotBlank() }
    }

    fun extractGeminiApiKeyFromText(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val regex = Regex("AIza[0-9A-Za-z_\\-]{25,}")
        return regex.find(text)?.value
    }
}
