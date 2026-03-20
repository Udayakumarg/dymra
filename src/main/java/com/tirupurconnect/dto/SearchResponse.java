package com.tirupurconnect.dto;
import java.util.List;

public record SearchResponse(String queryText, int total, List<SearchResultResponse> results) {}
