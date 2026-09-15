package com.asus.recosmart.domain.model

data class CameraSetting(
    val key: String,
    val value: String,
    val label: String = key,
    val options: List<String> = emptyList(),
    val description: String = ""
)
