package com.sethchhim.kuboo_remote.task

import androidx.lifecycle.MutableLiveData
import com.sethchhim.kuboo_remote.KubooRemote
import com.sethchhim.kuboo_remote.model.Book
import com.sethchhim.kuboo_remote.model.Login
import com.sethchhim.kuboo_remote.util.Settings.isDebugOkHttp
import okhttp3.CacheControl
import timber.log.Timber
import java.net.MalformedURLException
import java.net.SocketTimeoutException

class Task_RemoteBookList(kubooRemote: KubooRemote, val login: Login, val stringUrl: String) {

    private val okHttpHelper = kubooRemote.okHttpHelper
    private val parseService = okHttpHelper.parseService

    internal val liveData = MutableLiveData<List<Book>>()

    init {
        kubooRemote.networkIO.execute {
            try {
                val call = okHttpHelper.getCall(login, stringUrl, javaClass.simpleName, cacheControl = CacheControl.FORCE_NETWORK)
                val response = call.execute()
                val inputStream = response.body()?.byteStream()
                if (response.isSuccessful && inputStream != null) {
                    val inputAsString = inputStream.bufferedReader().use { it.readText() }
                    val result = parseService.parseOpds(login, inputAsString)
                    kubooRemote.mainThread.execute { liveData.value = result }
                    inputStream.close()
                    if (isDebugOkHttp) Timber.d("Found remote list: ${result.size} items $stringUrl tag[${call.request().tag()}]")
                } else {
                    kubooRemote.mainThread.execute { liveData.value = null }
                }
                response.close()
            } catch (e: SocketTimeoutException) {
                if (isDebugOkHttp) Timber.w("Connection timed out! $stringUrl")
                kubooRemote.mainThread.execute { liveData.value = null }
            } catch (e: MalformedURLException) {
                if (isDebugOkHttp) Timber.e("URL is bad! $stringUrl")
                kubooRemote.mainThread.execute { liveData.value = null }
            } catch (e: Exception) {
                if (e.message == "Canceled" || e.message == "Socket closed") {
                    if (isDebugOkHttp) Timber.d("Network call canceled. $stringUrl")
                    //no action needed
                } else {
                    e.printStackTrace()
                    if (isDebugOkHttp) Timber.e("Something went wrong! $stringUrl")
                    kubooRemote.mainThread.execute { liveData.value = null }
                }
            }
        }
    }

}