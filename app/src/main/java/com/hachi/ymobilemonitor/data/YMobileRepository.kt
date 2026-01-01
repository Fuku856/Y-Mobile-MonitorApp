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
            if (!step2Resp.isSuccessful) return false

            // 簡易チェック: ログイン画面に戻されていないか確認
            // リダイレクトで login.php に戻っている場合は失敗とみなす
            val finalUrl = step2Resp.request.url.toString()
            if (finalUrl.contains("login.php")) {
                return false
            }
            
            return true

        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }
    }

    // データ取得処理
    fun fetchData(): Result<YMobileData> {
        try {
            // 3. データ取得トークン取得
            val step3Req = Request.Builder()
                .url("https://my.ymobile.jp/muc/d/webLink/doSend/MRERE0000")
                .build()
            
            val step3Resp = client.newCall(step3Req).execute()
            if (!step3Resp.isSuccessful) {
                return Result.failure(Exception("Step 3 Failed: HTTP ${step3Resp.code}"))
            }

            val step3Html = step3Resp.body?.string() ?: return Result.failure(Exception("Step 3 Failed: Empty Body"))
            val step3Soup = Jsoup.parse(step3Html)
            val inputs = step3Soup.select("input[type=hidden]")
            
            if (inputs.size < 2) {
                val foundNames = inputs.map { it.attr("name") }.joinToString(", ")
                return Result.failure(Exception("Step 3 Failed: Missing hidden inputs (found: ${inputs.size}, names: [$foundNames])"))
            }
            
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
            if (!step4Resp.isSuccessful) {
                return Result.failure(Exception("Step 4 Failed: HTTP ${step4Resp.code}"))
            }

            val step4Html = step4Resp.body?.string() ?: return Result.failure(Exception("Step 4 Failed: Empty Body"))
            
            // 5. 解析
            return parseData(step4Html)

        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure(e)
        }
    }

    private fun parseData(html: String): Result<YMobileData> {
        try {
            val soup = Jsoup.parse(html)
            val ds = soup.selectFirst(".list-toggle-content.js-toggle-content.m-top-20") 
                ?: return Result.failure(Exception("Parse Failed: toggle content not found"))
            
            val tables = ds.select("table")
            if (tables.size < 4) {
                return Result.failure(Exception("Parse Failed: insufficient tables (found: ${tables.size})"))
            }

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
            
            return Result.success(YMobileData(
                remainingGb = (Math.round(remaining * 100.0) / 100.0),
                totalGb = total,
                usedGb = used,
                percentage = percentage,
                updatedAt = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                kurikoshiGb = kurikoshi,
                kihonGb = kihon,
                yuryouGb = yuryou
            ))

        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure(e)
        }
    }
}
