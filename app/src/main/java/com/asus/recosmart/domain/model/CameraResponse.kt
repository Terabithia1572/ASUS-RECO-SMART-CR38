package com.asus.recosmart.domain.model

data class CameraResponse(
    val msgId: Int,
    val rval: Int,
    val token: Int = 0,
    val param: String? = null,
    val type: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val apiVer: String? = null,
    val fwVer: String? = null,
    val appType: String? = null,
    val logo: String? = null,
    val chip: String? = null,
    val http: String? = null,
    val rawResponse: String = "",
    val timestampMs: Long = System.currentTimeMillis()
) {
    val isSuccess: Boolean
        get() = rval == 0
}
