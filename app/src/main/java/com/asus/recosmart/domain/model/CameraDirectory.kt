package com.asus.recosmart.domain.model

data class CameraDirectory(
    val name: String,
    val remotePath: String = "/tmp/fuse_d/DCIM/$name",
    val timestamp: String = ""
)
