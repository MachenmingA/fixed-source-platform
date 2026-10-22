package com.fixedsource.mobile

import android.app.Application
import com.fixedsource.mobile.data.Api
import com.fixedsource.mobile.data.CacheStore
import com.fixedsource.mobile.data.SessionStore

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        SessionStore.init(this)
        CacheStore.init(this)
        Api.init()
    }
}
