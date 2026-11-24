package dev.marzahn.sankaku.api

import dev.marzahn.sankaku.api.dto.BlacklistAddResponse
import dev.marzahn.sankaku.api.dto.BlacklistResponse
import io.ktor.client.HttpClient
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

data class BlacklistedTag(val ruleId: String, val usedRules: Int?)

class BlacklistApi(private val client: HttpClient) {
  suspend fun usedRules(): Int? = client.apiRequest<BlacklistResponse>(
    HttpMethod.Get, API_BASE, "/users/blacklist", HeaderSet.V2,
    query = mapOf("limit" to "40", "page" to "1", "entity" to "tags"),
  ).count?.usedRules

  suspend fun hideTag(tagName: String): BlacklistedTag {
    val body = buildJsonObject {
      put("type", "tag")
      putJsonArray("tags") { add(tagName) }
      put("visibility", "hide")
    }
    val response = client.apiRequest<BlacklistAddResponse>(HttpMethod.Post, API_BASE, "/users/blacklist", HeaderSet.V2, body = body)
    val id = response.rule?.id ?: throw ApiError.Http(200, "no new_blacklist")
    return BlacklistedTag(id, response.count?.usedRules)
  }

  suspend fun remove(ruleId: String): Int? =
    client.apiRequest<BlacklistResponse>(HttpMethod.Delete, API_BASE, "/users/blacklist/$ruleId", HeaderSet.V2).count?.usedRules
}
