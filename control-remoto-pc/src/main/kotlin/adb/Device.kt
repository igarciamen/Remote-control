package adb

import kotlinx.serialization.Serializable

@Serializable
data class Device(
    val name: String,
    val ip: String,
    val port: Int = 5555
)