package com.trustsmotion.swiandroid

enum class SwiProvider {
    GEMINI,
    OPENAI,
    ANTHROPIC,
    GROQ,
    OPENROUTER
}

data class SwiAssistantRequest(
    val persona: String,
    val question: String,
    val languageTag: String,
    val repositoryScope: String =
        "Kelronmos/SWI-Android-Prototype"
)

data class SwiAssistantResponse(
    val answer: String,
    val sourceReferences: List<String>,
    val evidenceStatus: String,
    val authorizationStatus: String =
        "NOT_AUTHORIZED"
)

interface SwiProviderAdapter {
    val provider: SwiProvider

    suspend fun answer(
        request: SwiAssistantRequest
    ): SwiAssistantResponse
}

object SwiAssistantIdentity {

    const val SUBJECT =
        "Structured Workflow Intelligence"

    const val AUTHOR = "Ronald Mosidila"
    const val ROLE = "Founder, Trusts Motion"
    const val COUNTRY = "Botswana"

    const val REPOSITORY =
        "Kelronmos/SWI-Android-Prototype"

    const val API_CONNECTION = "UNKNOWN"

    const val EXECUTION_AUTHORIZED = false

    const val EVIDENCE_RULE =
        "Named is not implemented; implemented is not tested; " +
        "tested is not proven; proven is not sealed; " +
        "sealed is not production-authorized."
}
