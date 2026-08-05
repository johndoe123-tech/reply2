package com.example.data.supabase

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClientProvider {
    val client = createSupabaseClient(
        supabaseUrl = "https://eqhjjkwajrlxuaduskwf.supabase.co",
        supabaseKey = "sb_publishable_5GKWGL1SdDTK9tCSl2pA1A_oq9cOdMg"
    ) {
        install(Auth)
        install(Postgrest)
    }
}
