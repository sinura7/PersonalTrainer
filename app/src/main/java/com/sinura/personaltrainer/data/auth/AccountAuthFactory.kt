package com.sinura.personaltrainer.data.auth

import com.sinura.personaltrainer.BuildConfig
import com.sinura.personaltrainer.domain.AccountAuthPort

object AccountAuthFactory {
    fun create(): AccountAuthPort {
        val url = BuildConfig.SUPABASE_URL.trim()
        val key = BuildConfig.SUPABASE_ANON_KEY.trim()
        return if (url.isEmpty() || key.isEmpty()) {
            UnconfiguredAccountAuth()
        } else {
            SupabaseAccountAuth(supabaseUrl = url, supabaseAnonKey = key)
        }
    }
}
