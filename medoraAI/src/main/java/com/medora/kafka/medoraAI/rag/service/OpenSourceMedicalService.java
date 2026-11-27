package com.medora.kafka.medoraAI.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class OpenSourceMedicalService {

	private static final Logger log = LoggerFactory.getLogger(OpenSourceMedicalService.class);
	private static final String WIKIMEDIA_API = "https://en.wikipedia.org/w/api.php";
	private static final String PUBMED_API = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi";

	private final WebClient webClient;
	private final ObjectMapper objectMapper;

	public OpenSourceMedicalService(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
		this.webClient = WebClient.builder()
			.codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
			.build();
	}

	/**
	 * Searches Wikipedia for medical information about a topic.
	 * Returns a summary extracted from the Wikipedia article.
	 */
	public String searchWikipedia(String topic) {
		try {
			log.info("Searching Wikipedia for medical topic: {}", topic);

			// Step 1: Search for the article
			String searchUrl = UriComponentsBuilder.fromHttpUrl(WIKIMEDIA_API)
				.queryParam("action", "query")
				.queryParam("list", "search")
				.queryParam("srsearch", topic)
				.queryParam("srnamespace", "0")
				.queryParam("srlimit", "1")
				.queryParam("format", "json")
				.build().toUriString();

			String searchResponse = webClient.get()
				.uri(searchUrl)
				.retrieve()
				.bodyToMono(String.class)
				.timeout(java.time.Duration.ofSeconds(5))
				.block();

			if (searchResponse == null) {
				log.warn("No response from Wikipedia search for: {}", topic);
				return null;
			}

			JsonNode searchNode = objectMapper.readTree(searchResponse);
			JsonNode searchResults = searchNode.path("query").path("search");

			if (searchResults.isEmpty()) {
				log.warn("No Wikipedia results found for: {}", topic);
				return null;
			}

			String title = searchResults.get(0).path("title").asText();
			log.info("Found Wikipedia article: {}", title);

			// Step 2: Extract the summary/extract from the article
			String extractUrl = UriComponentsBuilder.fromHttpUrl(WIKIMEDIA_API)
				.queryParam("action", "query")
				.queryParam("titles", title)
				.queryParam("prop", "extracts")
				.queryParam("exintro", "true")
				.queryParam("explaintext", "true")
				.queryParam("exsentences", "15")
				.queryParam("format", "json")
				.build().toUriString();

			String extractResponse = webClient.get()
				.uri(extractUrl)
				.retrieve()
				.bodyToMono(String.class)
				.timeout(java.time.Duration.ofSeconds(5))
				.block();

			if (extractResponse == null) {
				log.warn("No extract response from Wikipedia for: {}", title);
				return null;
			}

			JsonNode extractNode = objectMapper.readTree(extractResponse);
			JsonNode pages = extractNode.path("query").path("pages");

			// Get the first (and only) page
			for (JsonNode page : pages) {
				String extract = page.path("extract").asText("");
				if (!extract.isBlank()) {
					// Clean up the extract
					String cleaned = extract.trim();
					// Limit to reasonable length
					if (cleaned.length() > 1000) {
						cleaned = cleaned.substring(0, 1000) + "...";
					}
					log.info("Retrieved Wikipedia summary ({} chars) for: {}", cleaned.length(), topic);
					return cleaned;
				}
			}

			return null;
		} catch (Exception e) {
			log.error("Error searching Wikipedia for '{}': {}", topic, e.getMessage());
			return null;
		}
	}

	/**
	 * Searches PubMed for medical abstracts related to a topic.
	 * Returns a summary of findings from PubMed.
	 */
	public String searchPubMed(String topic) {
		try {
			log.info("Searching PubMed for medical topic: {}", topic);

			// Step 1: Search PubMed for relevant articles
			String searchUrl = UriComponentsBuilder.fromHttpUrl("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi")
				.queryParam("db", "pmc")
				.queryParam("term", topic)
				.queryParam("retmax", "3")
				.queryParam("sort", "relevance")
				.queryParam("retmode", "json")
				.build().toUriString();

			String searchResponse = webClient.get()
				.uri(searchUrl)
				.retrieve()
				.bodyToMono(String.class)
				.timeout(java.time.Duration.ofSeconds(5))
				.block();

			if (searchResponse == null) {
				log.warn("No response from PubMed search for: {}", topic);
				return null;
			}

			JsonNode searchNode = objectMapper.readTree(searchResponse);
			JsonNode ids = searchNode.path("esearchresult").path("idlist");

			if (ids.isEmpty()) {
				log.warn("No PubMed results found for: {}", topic);
				return null;
			}

			// Step 2: Get summaries for the found articles
			List<String> summaries = new ArrayList<>();
			int count = 0;
			for (JsonNode id : ids) {
				if (count >= 2) break; // Limit to 2 summaries

				String summaryUrl = UriComponentsBuilder.fromHttpUrl(PUBMED_API)
					.queryParam("db", "pmc")
					.queryParam("id", id.asText())
					.queryParam("retmode", "json")
					.build().toUriString();

				String summaryResponse = webClient.get()
					.uri(summaryUrl)
					.retrieve()
					.bodyToMono(String.class)
					.timeout(java.time.Duration.ofSeconds(3))
					.block();

				if (summaryResponse != null) {
					try {
						JsonNode summaryNode = objectMapper.readTree(summaryResponse);
						JsonNode doc = summaryNode.path("result").path(id.asText());
						if (doc != null && !doc.isMissingNode()) {
							String title = doc.path("title").asText("");
							String abstractText = doc.path("abstractText").asText("");
							if (!abstractText.isBlank()) {
								summaries.add("Title: " + title + "\n" + abstractText);
								count++;
							}
						}
					} catch (Exception e) {
						log.warn("Error parsing PubMed summary: {}", e.getMessage());
					}
				}
			}

			if (!summaries.isEmpty()) {
				String combined = String.join("\n\n---\n\n", summaries);
				if (combined.length() > 1000) {
					combined = combined.substring(0, 1000) + "...";
				}
				log.info("Retrieved PubMed summaries for: {}", topic);
				return combined;
			}

			return null;
		} catch (Exception e) {
			log.error("Error searching PubMed for '{}': {}", topic, e.getMessage());
			return null;
		}
	}

	/**
	 * Tries Wikipedia first, then falls back to PubMed.
	 * Returns the first successful result.
	 */
	public String searchMedicalKnowledge(String topic) {
		log.info("Searching open-source medical knowledge for: {}", topic);

		// Try Wikipedia first (faster, more general)
		String wikipediaResult = searchWikipedia(topic);
		if (wikipediaResult != null && !wikipediaResult.isBlank()) {
			return "Source: Wikipedia\n" + wikipediaResult;
		}

		// Fall back to PubMed (more specialized, but slower)
		String pubmedResult = searchPubMed(topic);
		if (pubmedResult != null && !pubmedResult.isBlank()) {
			return "Source: PubMed (Medical Research)\n" + pubmedResult;
		}

		log.warn("No open-source medical knowledge found for: {}", topic);
		return null;
	}
}
