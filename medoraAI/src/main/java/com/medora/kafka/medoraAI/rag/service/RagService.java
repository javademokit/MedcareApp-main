package com.medora.kafka.medoraAI.rag.service;

import com.medora.kafka.medoraAI.rag.model.DocumentChunk;
import com.medora.kafka.medoraAI.rag.model.QuestionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RagService {

	private static final Logger log = LoggerFactory.getLogger(RagService.class);

	private final DocumentIndexService indexService;
	private final OpenSourceMedicalService openSourceService;
	private final KnowledgeBaseUpdater kbUpdater;

	public RagService(DocumentIndexService indexService, OpenSourceMedicalService openSourceService, KnowledgeBaseUpdater kbUpdater) {
		this.indexService = indexService;
		this.openSourceService = openSourceService;
		this.kbUpdater = kbUpdater;
	}

	public QuestionResponse ask(String question) {
		QuestionResponse response = new QuestionResponse();

		// Step 1: Search local knowledge base first
		List<DocumentChunk> chunks = indexService.search(question, 5);

		// Step 2: Extract primary keyword from the query
		String primaryKeyword = extractPrimaryKeyword(question);
		log.info("Primary keyword for '{}': '{}'", question, primaryKeyword);

		// Check if any chunk actually has the primary subject as the main topic
		// (not just mentioned in passing as an example)
		boolean hasRelevantContent = false;
		if (!chunks.isEmpty() && !primaryKeyword.isEmpty()) {
			for (DocumentChunk chunk : chunks) {
				String contentLower = chunk.getContent().toLowerCase();
				String trimmedLower = contentLower.trim();
				// Check if the keyword appears as a section header (# keyword) or at the start as a definition
				boolean isSectionHeader = trimmedLower.startsWith("#" + primaryKeyword) || trimmedLower.startsWith("# " + primaryKeyword);
				boolean isDefinitionStart = trimmedLower.matches("(?i).*\\b" + Pattern.quote(primaryKeyword) + "\\b\\s+(is|are)\\s.*");
				boolean appearsMultipleTimes = countOccurrences(contentLower, primaryKeyword) >= 2;
				// The keyword must be the main topic, not just a passing mention
				if (isSectionHeader || isDefinitionStart || appearsMultipleTimes) {
					hasRelevantContent = true;
					break;
				}
			}
		}

		if (!chunks.isEmpty() && hasRelevantContent) {
			// Found relevant results in local knowledge base
			double avgScore = 0.7;

			// For "what is X" questions, prioritize chunks that start with a definition of X
			List<DocumentChunk> sortedChunks = prioritizeDefinitionChunks(chunks, primaryKeyword);

			String answer = sortedChunks.stream()
				.map(DocumentChunk::getContent)
				.collect(Collectors.joining(" "));
			// Clean up answer: remove leading/trailing punctuation and whitespace
			answer = answer.replaceAll("^[.\\s,;:]+", "").trim();
			// If answer still starts with a lowercase letter after punctuation, capitalize it
			if (!answer.isEmpty() && Character.isLowerCase(answer.charAt(0))) {
				answer = Character.toUpperCase(answer.charAt(0)) + answer.substring(1);
			}
			// Truncate at a sentence boundary if too long
			if (answer.length() > 800) {
				int lastPeriod = answer.lastIndexOf('.', 800);
				if (lastPeriod > 400) {
					answer = answer.substring(0, lastPeriod + 1);
				} else {
					answer = answer.substring(0, 800) + "...";
				}
			}
			response.setAnswer(answer);
			response.setConfidence(avgScore);
			response.setSources(sortedChunks.stream()
				.map(c -> c.getSource() + " (page " + c.getPage() + ")")
				.collect(Collectors.toList()));
			log.info("Local knowledge base returned {} relevant chunks for: {}", sortedChunks.size(), question);
		} else {
			// Step 3: Fall back to open-source medical knowledge
			String reason = chunks.isEmpty() ? "no results" : "no relevant content matching query keywords";
			log.info("Local knowledge base: {} for '{}', searching open-source medical data...", reason, question);
			String openSourceAnswer = openSourceService.searchMedicalKnowledge(question);

			if (openSourceAnswer != null && !openSourceAnswer.isBlank()) {
				// Extract the topic name from the query for KB storage
				String topic = primaryKeyword.isEmpty() ? question : primaryKeyword;
				// Clean the answer content (remove "Source: ..." prefix for storage)
				String contentToStore = openSourceAnswer;
				if (contentToStore.startsWith("Source: ")) {
					int firstNewline = contentToStore.indexOf('\n');
					if (firstNewline > 0) {
						contentToStore = contentToStore.substring(firstNewline + 1);
					}
				}

				// Try to add the new topic to the knowledge base
				boolean added = kbUpdater.addNewTopic(topic, contentToStore);
				response.setAutoAddedToKB(added);

				String answerText = openSourceAnswer.length() > 1000
					? openSourceAnswer.substring(0, 1000) + "..." : openSourceAnswer;
				response.setAnswer(answerText);
				response.setConfidence(added ? 0.5 : 0.4); // Slightly higher if saved to KB
				response.setSources(List.of("Open-source medical knowledge (Wikipedia/PubMed)" + (added ? " — added to local knowledge base" : "")));
				log.info("Open-source fallback returned answer for: {} (added to KB: {})", question, added);
			} else {
				// No results anywhere
				response.setAnswer(
					"I don't have information about that topic in my knowledge base or from open-source medical sources. " +
					"Please try rephrasing your question or contact a doctor for personalized medical advice.");
				response.setConfidence(0.0);
				response.setSources(List.of());
				log.warn("No results found for: {}", question);
			}
		}

		response.setDisclaimer("This is general information, not medical advice. Consult a doctor for personalized guidance.");
		return response;
	}

	/**
	 * Extracts the primary subject keyword from a question by removing stop words.
	 * For "what is X" questions, returns X.
	 * For "what are the symptoms of X" questions, returns X.
	 */
	private String extractPrimaryKeyword(String question) {
		String queryLower = question.toLowerCase();

		// Try "what is X" pattern first
		Matcher whatIsMatcher = Pattern.compile("(?i)\\bwhat\\s+(?:is|are)\\s+(?:the\\s+)?(?:symptoms\\s+(?:of|for|in)?\\s+|causes\\s+(?:of|for|in)?\\s+|treatment\\s+(?:for|of)?\\s+)?(\\w+)").matcher(queryLower);
		if (whatIsMatcher.find()) {
			String subject = whatIsMatcher.group(1);
			if (subject.length() >= 3) return subject;
		}

		// Fallback: extract all keywords and find the last meaningful one (usually the subject)
		List<String> keywords = List.of(queryLower.split("\\s+"))
			.stream()
			.filter(w -> w.replaceAll("[^a-z]", "").length() >= 3)
			.map(w -> w.replaceAll("[^a-z]", ""))
			.collect(Collectors.toList());

		List<String> stopWords = List.of("what", "are", "the", "is", "it", "how", "why", "when",
			"which", "who", "does", "do", "can", "has", "have", "been", "being", "this", "that",
			"from", "with", "about", "between", "through", "during", "before", "after", "into",
			"like", "just", "also", "very", "more", "most", "some", "any", "all", "each", "every",
			"symptoms", "causes", "treatment", "signs", "effects", "side", "risks");

		// Return the LAST meaningful keyword (subject is usually at the end of the question)
		String primaryKeyword = keywords.stream()
			.filter(kw -> !stopWords.contains(kw))
			.reduce((first, last) -> last)
			.orElse(keywords.isEmpty() ? "" : keywords.get(0));

		return primaryKeyword;
	}

	/**
	 * Counts how many times a word appears in text.
	 */
	private int countOccurrences(String text, String word) {
		if (text == null || word == null || word.isEmpty()) return 0;
		int count = 0;
		Pattern pattern = Pattern.compile("\\b" + Pattern.quote(word) + "\\b", Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(text);
		while (matcher.find()) count++;
		return count;
	}

	/**
	 * For "what is X" questions, prioritizes chunks that contain a definition of X
	 * (e.g., "X is a..." or "# X\nX is..."). Other chunks follow by relevance score.
	 */
	private List<DocumentChunk> prioritizeDefinitionChunks(List<DocumentChunk> chunks, String keyword) {
		if (keyword == null || keyword.isEmpty()) {
			return chunks;
		}

		List<DocumentChunk> definitionChunks = new java.util.ArrayList<>();
		List<DocumentChunk> otherChunks = new java.util.ArrayList<>();

		Pattern defPattern = Pattern.compile("(?i)\\b" + Pattern.quote(keyword) + "\\b\\s+(is|are)\\s", Pattern.CASE_INSENSITIVE);

		for (DocumentChunk chunk : chunks) {
			String content = chunk.getContent();
			String trimmed = content.trim();
			// Check if this chunk starts with or near the beginning contains a definition pattern
			Matcher matcher = defPattern.matcher(trimmed);
			if (matcher.find() && matcher.start() < 100) {
				definitionChunks.add(chunk);
			} else {
				otherChunks.add(chunk);
			}
		}

		// Definition chunks first, then others
		List<DocumentChunk> result = new java.util.ArrayList<>();
		result.addAll(definitionChunks);
		result.addAll(otherChunks);
		return result;
	}
}
