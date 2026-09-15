package com.asus.recosmart.domain.model

data class CameraSettingOption(
    val displayName: String,
    val wireValue: String
)

data class CameraSettingSpec(
    val key: String,
    val label: String,
    val description: String,
    val options: List<CameraSettingOption>,
    val isEditable: Boolean = true,
    val currentWireValue: String = ""
) {
    val currentDisplayValue: String
        get() = options.find { it.wireValue == currentWireValue }?.displayName ?: currentWireValue
}
