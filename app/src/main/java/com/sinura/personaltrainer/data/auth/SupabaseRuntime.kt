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
    private val rest = SupabaseRestClient(supabaseUrl, supabaseAnonKey)

    val client = createSupabaseClient(
        supabaseUrl = supabaseUrl,
        supabaseKey = supabaseAnonKey,
    ) {
        install(Auth)
    }

    private val supabaseAuth = SupabaseAccountAuth(client, rest)
    val auth: AccountAuthPort = supabaseAuth

    val syncRemote: SyncRemotePort = SupabaseSyncRemote(
        rest = rest,
        accessToken = { supabaseAuth.accessTokenOrNull() },
    )
}
