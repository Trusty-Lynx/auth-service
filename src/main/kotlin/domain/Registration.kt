package de.trustylynx.domain

/** Bereits validierte Registrierungsdaten. */
class NewAccount(
    val email: String,
    val saltLogin: ByteArray,
    val kdf: KdfParams,
    val loginKey: ByteArray,
    val device: NewDevice,
) {
    override fun toString(): String = "NewAccount(device=$device)"
}

class NewDevice(
    val id: String,
    val name: String,
    val publicKey: ByteArray,
) {
    override fun toString(): String = "NewDevice(id=$id)"
}

data class RegisteredAccount(
    val userId: String,
    val token: String,
)
