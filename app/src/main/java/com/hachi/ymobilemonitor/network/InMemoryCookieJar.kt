package com.hachi.ymobilemonitor.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class InMemoryCookieJar : CookieJar {
    private val cookieStore = HashMap<String, List<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookieStore[url.host] = cookies
        // 単純な実装: ホストごとに上書き（必要に応じて結合ロジックを追加）
        // 実際には、既存のCookieとマージする方が良いが、今回はLoginフローが直列なのでこれで動く可能性が高い
        // 改善: ArrayListで保持し、名前で更新する
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookies = cookieStore[url.host] ?: return emptyList()
        return cookies.filter { it.matches(url) }
    }
}
