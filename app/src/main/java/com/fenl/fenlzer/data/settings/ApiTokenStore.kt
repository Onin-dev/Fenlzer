package com.fenl.fenlzer.data.settings

interface ApiTokenStore {
    fun saveToken(token: String)

    fun getToken(): String?

    fun clearToken()
}
