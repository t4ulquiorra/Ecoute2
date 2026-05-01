package com.ecoute.music.utils.cipher

import android.util.Log
import java.security.MessageDigest

object FunctionNameExtractor {
    private const val TAG = "Ecoute_CipherFnExtract"

    data class SigFunctionInfo(
        val name: String,
        val constantArg: Int?,
        val constantArgs: List<Int>? = null,
        val preprocessFunc: String? = null,
        val preprocessArgs: List<Int>? = null,
        val isHardcoded: Boolean = false
    )

    data class NFunctionInfo(
        val name: String,
        val arrayIndex: Int?,
        val constantArgs: List<Int>? = null,
        val isHardcoded: Boolean = false
    )

    data class HardcodedPlayerConfig(
        val sigFuncName: String,
        val sigConstantArg: Int?,
        val sigConstantArgs: List<Int>? = null,
        val sigPreprocessFunc: String? = null,
        val sigPreprocessArgs: List<Int>? = null,
        val nFuncName: String,
        val nArrayIndex: Int?,
        val nConstantArgs: List<Int>?,
        val signatureTimestamp: Int
    )

    data class PlayerAnalysis(
        val playerHash: String?,
        val hasQArrayObfuscation: Boolean,
        val sigInfo: SigFunctionInfo?,
        val nFuncInfo: NFunctionInfo?,
        val signatureTimestamp: Int?
    )

    private val KNOWN_PLAYER_CONFIGS = mapOf(
        "74edf1a3" to HardcodedPlayerConfig(
            sigFuncName = "JI", sigConstantArg = 48,
            sigConstantArgs = listOf(48, 1918),
            sigPreprocessFunc = "f1", sigPreprocessArgs = listOf(1, 6528),
            nFuncName = "GU", nArrayIndex = null,
            nConstantArgs = listOf(6, 6010), signatureTimestamp = 20522
        ),
        "f4c47414" to HardcodedPlayerConfig(
            sigFuncName = "hJ", sigConstantArg = 6,
            sigConstantArgs = listOf(6),
            sigPreprocessFunc = null, sigPreprocessArgs = null,
            nFuncName = "", nArrayIndex = null,
            nConstantArgs = null, signatureTimestamp = 20543
        )
    )

    private val Q_ARRAY_PATTERN = Regex("""var\s+Q\s*=\s*"[^"]+"\s*\.\s*split\s*\(\s*"\}"\s*\)""")

    private val PLAYER_HASH_PATTERNS = listOf(
        Regex("""jsUrl['":\s]+[^"']*?/player/([a-f0-9]{8})/"""),
        Regex("""player_ias\.vflset/[^/]+/([a-f0-9]{8})/"""),
        Regex("""/s/player/([a-f0-9]{8})/""")
    )

    private val SIG_FUNCTION_PATTERNS = listOf(
        Regex("""&&\s*\(\s*[a-zA-Z0-9$]+\s*=\s*([a-zA-Z0-9$]+)\s*\(\s*(\d+)\s*,\s*decodeURIComponent\s*\(\s*[a-zA-Z0-9$]+\s*\)"""),
        Regex("""&&\s*\(\s*[a-zA-Z0-9$]+\s*=\s*([a-zA-Z0-9$]+)\s*\(\s*(\d+)\s*,\s*decodeURIComponent\s*\(\s*[a-zA-Z0-9$]+\s*\.\s*[a-z]\s*\)"""),
        Regex("""\b[cs]\s*&&\s*[adf]\.set\([^,]+\s*,\s*encodeURIComponent\(([a-zA-Z0-9$]+)\("""),
        Regex("""\b[a-zA-Z0-9]+\s*&&\s*[a-zA-Z0-9]+\.set\([^,]+\s*,\s*encodeURIComponent\(([a-zA-Z0-9$]+)\("""),
        Regex("""\bm=([a-zA-Z0-9${'$'}]{2,})\(decodeURIComponent\(h\.s\)\)"""),
        Regex("""\bc\s*&&\s*d\.set\([^,]+\s*,\s*(?:encodeURIComponent\s*\()([a-zA-Z0-9$]+)\("""),
        Regex("""\bc\s*&&\s*[a-z]\.set\([^,]+\s*,\s*encodeURIComponent\(([a-zA-Z0-9$]+)\("""),
    )

    private val N_FUNCTION_PATTERNS = listOf(
        Regex("""\.get\("n"\)\)&&\(b=([a-zA-Z0-9$]+)(?:\[(\d+)\])?\(([a-zA-Z0-9])\)"""),
        Regex("""\.get\("n"\)\)\s*&&\s*\(([a-zA-Z0-9$]+)\s*=\s*([a-zA-Z0-9$]+)(?:\[(\d+)\])?\(\1\)"""),
        Regex("""\.get\("n"\);if\([a-zA-Z0-9$]+\)\s*\{[^}]*match"""),
        Regex("""\(\s*([a-zA-Z0-9$]+)\s*=\s*String\.fromCharCode\(110\)"""),
        Regex("""([a-zA-Z0-9$]+)\s*=\s*function\([a-zA-Z0-9]\)\s*\{[^}]*?enhanced_except_"""),
    )

    fun hasQArrayObfuscation(playerJs: String): Boolean = Q_ARRAY_PATTERN.containsMatchIn(playerJs)

    fun extractPlayerHash(playerJs: String): String? {
        for (pattern in PLAYER_HASH_PATTERNS) {
            val match = pattern.find(playerJs)
            if (match != null) return match.groupValues[1]
        }
        val digest = MessageDigest.getInstance("MD5").digest(playerJs.take(10000).toByteArray())
        return digest.take(4).joinToString("") { "%02x".format(it) }
    }

    fun getHardcodedConfig(hash: String): HardcodedPlayerConfig? = KNOWN_PLAYER_CONFIGS[hash]

    fun extractSigFunctionInfo(playerJs: String, knownHash: String? = null): SigFunctionInfo? {
        for (pattern in SIG_FUNCTION_PATTERNS) {
            val match = pattern.find(playerJs) ?: continue
            val name = match.groupValues[1]
            val constantArg = if (match.groupValues.size > 2) match.groupValues[2].toIntOrNull() else null
            if (name.isNotEmpty()) return SigFunctionInfo(name, constantArg)
        }
        if (hasQArrayObfuscation(playerJs)) {
            val hash = knownHash ?: extractPlayerHash(playerJs)
            val config = hash?.let { getHardcodedConfig(it) } ?: return null
            return SigFunctionInfo(
                name = config.sigFuncName,
                constantArg = config.sigConstantArg,
                constantArgs = config.sigConstantArgs,
                preprocessFunc = config.sigPreprocessFunc,
                preprocessArgs = config.sigPreprocessArgs,
                isHardcoded = true
            )
        }
        return null
    }

    fun extractNFunctionInfo(playerJs: String, knownHash: String? = null): NFunctionInfo? {
        for ((index, pattern) in N_FUNCTION_PATTERNS.withIndex()) {
            val match = pattern.find(playerJs) ?: continue
            return when (index) {
                0 -> NFunctionInfo(match.groupValues[1], match.groupValues[2].toIntOrNull())
                1 -> NFunctionInfo(match.groupValues[2], match.groupValues[3].toIntOrNull())
                else -> NFunctionInfo(match.groupValues[1], null)
            }
        }
        if (hasQArrayObfuscation(playerJs)) {
            val hash = knownHash ?: extractPlayerHash(playerJs)
            val config = hash?.let { getHardcodedConfig(it) } ?: return null
            return NFunctionInfo(config.nFuncName, config.nArrayIndex, config.nConstantArgs, isHardcoded = true)
        }
        return null
    }

    fun extractSignatureTimestamp(playerJs: String): Int? {
        val patterns = listOf(
            Regex("""signatureTimestamp['":\s]+(\d+)"""),
            Regex("""sts['":\s]+(\d+)"""),
            Regex(""""signatureTimestamp"\s*:\s*(\d+)""")
        )
        for (pattern in patterns) {
            val match = pattern.find(playerJs)
            if (match != null) return match.groupValues[1].toIntOrNull()
        }
        return extractPlayerHash(playerJs)?.let { getHardcodedConfig(it)?.signatureTimestamp }
    }

    fun analyzePlayerJs(playerJs: String, knownHash: String? = null): PlayerAnalysis {
        val playerHash = knownHash ?: extractPlayerHash(playerJs)
        return PlayerAnalysis(
            playerHash = playerHash,
            hasQArrayObfuscation = hasQArrayObfuscation(playerJs),
            sigInfo = extractSigFunctionInfo(playerJs, playerHash),
            nFuncInfo = extractNFunctionInfo(playerJs, playerHash),
            signatureTimestamp = extractSignatureTimestamp(playerJs)
        )
    }
}
