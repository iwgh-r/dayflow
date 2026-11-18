package app.dayflow.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.security.MessageDigest

object AppCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(snapshot: Snapshot): String = json.encodeToString(snapshot.validate())
    fun decode(text: String): Snapshot {
        val element = json.parseToJsonElement(text).jsonObject
        val version = element["schemaVersion"]?.jsonPrimitive?.int ?: 1
        require(version in 1..2) { "지원하지 않는 데이터 버전입니다. 원본은 보존됩니다." }
        val migrated = JsonObject(element + ("schemaVersion" to JsonPrimitive(2)))
        return json.decodeFromJsonElement<Snapshot>(migrated).validate()
    }
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun export(snapshot: Snapshot): String {
        val payload = encode(snapshot)
        return buildJsonObject { put("format", "dayflow-backup-1"); put("sha256", hash(payload)); put("payload", payload) }.toString()
    }
    fun import(text: String): Snapshot {
        require(text.toByteArray(Charsets.UTF_8).size <= 50_000_000) { "백업은 50MB 이하여야 합니다." }
        val envelope = json.parseToJsonElement(text).jsonObject
        require(envelope["format"]?.jsonPrimitive?.content == "dayflow-backup-1") { "Dayflow 백업 파일이 아닙니다." }
        val payload = envelope.getValue("payload").jsonPrimitive.content
        require(envelope["sha256"]?.jsonPrimitive?.content == hash(payload)) { "백업의 무결성 검증에 실패했습니다." }
        return decode(payload)
    }
}
