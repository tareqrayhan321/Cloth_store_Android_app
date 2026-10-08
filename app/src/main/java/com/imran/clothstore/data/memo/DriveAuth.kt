package com.imran.clothstore.data.memo

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

/**
 * Google Drive-এর জন্য OAuth access token আনে (AuthorizationClient)।
 * স্কোপ drive.file — অ্যাপ নিজে যে ফাইল বানায় শুধু সেগুলোতেই অ্যাক্সেস; ড্রাইভের বাকি কিছু দেখতে পায় না।
 */
object DriveAuth {
    const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

    sealed interface Result {
        data class Token(val value: String) : Result
        /** প্রথমবার (বা অনুমতি তুলে নেওয়া হলে) ইউজারের সম্মতি লাগে — pendingIntent লঞ্চ করতে হবে */
        data class NeedsConsent(val pendingIntent: PendingIntent) : Result
        data class Failed(val message: String) : Result
    }

    private fun request() = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
        .build()

    suspend fun authorize(context: Context): Result = try {
        val res = Identity.getAuthorizationClient(context).authorize(request()).await()
        val pi = res.pendingIntent
        val token = res.accessToken
        when {
            res.hasResolution() && pi != null -> Result.NeedsConsent(pi)
            token != null -> Result.Token(token)
            else -> Result.Failed("Google Drive টোকেন পাওয়া যায়নি")
        }
    } catch (e: Exception) {
        Result.Failed(e.message ?: "Google অনুমোদন ব্যর্থ")
    }

    /** সম্মতি-স্ক্রিন থেকে ফেরার পর ফলাফল পড়ে */
    fun fromConsentResult(context: Context, data: Intent?): Result = try {
        val res = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
        val token = res.accessToken
        if (token != null) Result.Token(token) else Result.Failed("অনুমতি দেওয়া হয়নি")
    } catch (e: Exception) {
        Result.Failed(e.message ?: "অনুমতি দেওয়া হয়নি")
    }
}
