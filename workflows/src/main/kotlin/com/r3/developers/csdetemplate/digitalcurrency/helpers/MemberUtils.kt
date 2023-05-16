package com.r3.developers.csdetemplate.digitalcurrency.helpers

import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.util.EncodingUtils
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.membership.MemberInfo
import java.security.MessageDigest
import java.security.PublicKey

fun MemberLookup.findInfo(key: PublicKey): MemberInfo {
    return this.lookup(key) ?: throw IllegalArgumentException("Unable to find member with key: ${key.toStringShort()}")
}

fun PublicKey.toStringShort(): String {
    val md = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name)
    val sha256Bytes = md.digest(encoded)
    return "DL" + EncodingUtils.toBase58(sha256Bytes)
}
