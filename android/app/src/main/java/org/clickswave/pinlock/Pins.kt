package org.clickswave.pinlock

import okhttp3.CertificatePinner

// Layer two of two. OkHttp checks the server's chain against these hashes after
// the platform has already accepted it. Same list as network_security_config.xml.
object Pins {
    val roots = listOf(
        "GTS Root R1" to "hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc=",
        "GTS Root R4" to "mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=",
        "ISRG Root X1" to "C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=",
        "ISRG Root X2" to "diGVwiVYbubAI3RW4hB9xU8e/CH2GnkuvVFZE8zmgzI=",
        "SSL.com TLS ECC Root CA 2022" to "G/ANXI8TwJTdF+AFBM8IiIUPEv0Gf6H5LA/b9guG4yE=",
        "SSL.com TLS RSA Root CA 2022" to "K89VOmb1cJAN3TK6bf4ezAbJGC1mLcG2Dh97dnwr3VQ=",
    )

    fun pinner(host: String): CertificatePinner =
        CertificatePinner.Builder()
            .apply { roots.forEach { (_, hash) -> add(host, "sha256/$hash") } }
            .build()
}
