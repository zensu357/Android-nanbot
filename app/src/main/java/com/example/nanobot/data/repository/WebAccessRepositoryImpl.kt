package com.example.nanobot.data.repository

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.web.WebAccessService
import com.example.nanobot.core.web.WebFetchResult
import com.example.nanobot.core.web.WebSearchResult
import com.example.nanobot.domain.repository.WebAccessRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebAccessRepositoryImpl @Inject constructor(
    private val webAccessService: WebAccessService
) : WebAccessRepository {
    override suspend fun fetch(url: String, maxChars: Int, config: AgentConfig?): WebFetchResult {
        return webAccessService.fetch(url, maxChars, config)
    }

    override suspend fun search(query: String, limit: Int, config: AgentConfig?): WebSearchResult {
        return webAccessService.search(query, limit, config)
    }
}
