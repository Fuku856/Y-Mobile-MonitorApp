package com.hachi.ymobilemonitor.data

import com.hachi.ymobilemonitor.network.InMemoryCookieJar
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.util.concurrent.TimeUnit

data class YMobileData(
    val remainingGb: Double,
    val totalGb: Double,
    val usedGb: Double,
    val percentage: Double,
    val updatedAt: String,
    // 詳細
    val kurikoshiGb: Double,
    val kihonGb: Double,
    val yuryouGb: Double
)

class YMobileRepository {
    private val cookieJar = InMemoryCookieJar()
    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // ログイン処理
    fun login(id: String, pass: String): Boolean {
        try {
            // 1. ログインページ初期アクセス (ticket取得)
            val step1Req = Request.Builder()
                .url("https://my.ymobile.jp/muc/d/webLink/doSend/MWBWL0130")
                .build()
            
            val step1Resp = client.newCall(step1Req).execute()
            if (!step1Resp.isSuccessful) return false
            
            val step1Html = step1Resp.body?.string() ?: return false
            val step1Soup = Jsoup.parse(step1Html)
            val ticket = step1Soup.select("input[type=hidden]").first()?.attr("value") ?: return false

            // 2. ログイン実行
            val formBody = FormBody.Builder()
                .add("telnum", id)
                .add("password", pass)
                .add("ticket", ticket)
                .build()

            val step2Req = Request.Builder()
                .url("https://id.my.ymobile.jp/sbid_auth/type1/2.0/login.php")
                .post(formBody)
                .build()
            
            val step2Resp = client.newCall(step2Req).execute()
            return step2Resp.isSuccessful
            // 成功判定はレスポンスURLやCookieの状態などで行う必要があるが、一旦ステータスコードで判定
            // 実際はリダイレクトされるので、最終URLをチェックすると確実

        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }
    }

    // データ取得処理
    fun fetchData(): YMobileData? {
        try {
            // 3. データ取得トークン取得
            val step3Req = Request.Builder()
                .url("https://my.ymobile.jp/muc/d/webLink/doSend/MRERE0000")
                .build()
            
            val step3Resp = client.newCall(step3Req).execute()
            val step3Html = step3Resp.body?.string() ?: return null
            val step3Soup = Jsoup.parse(step3Html)
            val inputs = step3Soup.select("input[type=hidden]")
            
            if (inputs.size < 2) return null
            
            val mfiv = inputs[0].attr("value")
            val mfym = inputs[1].attr("value")

            // 4. データページ取得
            val formBody = FormBody.Builder()
                .add("mfiv", mfiv)
                .add("mfym", mfym)
                .build()

            val step4Req = Request.Builder()
                .url("https://re61.my.ymobile.jp/resfe/top/")
                .post(formBody)
                .build()
            
            val step4Resp = client.newCall(step4Req).execute()
            val step4Html = step4Resp.body?.string() ?: return null
            
            // 5. 解析
            return parseData(step4Html)

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun parseData(html: String): YMobileData? {
        try {
            val soup = Jsoup.parse(html)
            val ds = soup.selectFirst(".list-toggle-content.js-toggle-content.m-top-20") ?: return null
            val tables = ds.select("table")
            if (tables.size < 4) return null

            fun clean(text: String?) = text?.replace("\t", "")?.replace("\n", "")?.replace("GB", "")?.trim()?.toDoubleOrNull() ?: 0.0

            // テーブル順序 (Pythonスクリプト準拠)
            // 0: 繰越
            val kurikoshi = clean(tables[0].select("tbody td").text())
            
            // 1: 基本 (2行目のtd)
            val trs = tables[1].select("tbody tr")
            val kihon = if (trs.size >= 2) clean(trs[1].select("td").text()) else 0.0

            // 2: 有料
            val yuryou = clean(tables[2].select("tbody tr td").text())

            // 3: 使用済み
            val used = clean(tables[3].select("tbody tr td").text())

            // 計算
            val remaining = kihon + kurikoshi - used
            val total = kihon + kurikoshi
            val percentage = if (total > 0) (used / total) * 100 else 0.0
            
            return YMobileData(
                remainingGb = (Math.round(remaining * 100.0) / 100.0),
                totalGb = total,
                usedGb = used,
                percentage = percentage,
                updatedAt = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                kurikoshiGb = kurikoshi,
                kihonGb = kihon,
                yuryouGb = yuryou
            )

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
