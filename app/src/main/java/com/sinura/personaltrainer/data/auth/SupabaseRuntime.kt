package com.sinura.personaltrainer.data.auth

import com.sinura.personaltrainer.data.sync.SupabaseRestClient
import com.sinura.personaltrainer.data.sync.SupabaseSyncRemote
import com.sinura.personaltrainer.data.sync.SyncRemotePort
import com.sinura.personaltrainer.domain.AccountAuthPort
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth

/**
 * One Supabase client for Auth and sync PostgREST. Keeps the session token shared.
 */
class SupabaseRuntime(
    supabaseUrl: String,
    supabaseAnonKey: String,
) {
    val client = createSupabaseClient(
        supabaseUrl = supabaseUrl,
        supabaseKey = supabaseAnonKey,
    ) {
        install(Auth)
    }

    private val supabaseAuth = SupabaseAccountAuth(client)
    val auth: AccountAuthPort = supabaseAuth

    val syncRemote: SyncRemotePort = SupabaseSyncRemote(
        rest = SupabaseRestClient(supabaseUrl, supabaseAnonKey),
        accessToken = { supabaseAuth.accessTokenOrNull() },
    )
}
