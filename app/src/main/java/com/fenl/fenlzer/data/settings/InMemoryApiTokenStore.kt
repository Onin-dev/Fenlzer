package com.fenl.fenlzer.data.settings

class InMemoryApiTokenStore(
    initialToken: String? = null
) : ApiTokenStore {
    private var token: String? = initialToken

    override fun saveToken(token: String) {
        this.token = token
    }

    override fun getToken(): String? = token

    override fun clearToken() {
        token = null
    }
}
