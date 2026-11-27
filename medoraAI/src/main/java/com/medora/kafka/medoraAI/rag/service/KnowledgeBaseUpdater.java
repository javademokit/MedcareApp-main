package com.medora.kafka.medoraAI.rag.service;

import com.medora.kafka.medoraAI.rag.model.DocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Appends new medical information to the knowledge base text file
 * and adds the new content to the Lucene search index in real-time.
 * This allows the RAG system to learn from open-source fallback answers.
 */
@Service
public class KnowledgeBaseUpdater {

	private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseUpdater.class);
	private static final String KB_FILE_PATH = "src/main/resources/diseases_knowledge_base.pdf.txt";

	private final DocumentIndexService indexService;

	public KnowledgeBaseUpdater(DocumentIndexService indexService) {
		this.indexService = indexService;
	}

	/**
	 * Appends new medical information to the knowledge base file and re-indexes it.
	 *
	 * @param topic the medical topic name (e.g., "Malaria", "Ebola")
	 * @param content the medical content to append
	 * @return true if successfully added, false if the topic already exists
	 */
	public boolean addNewTopic(String topic, String content) {
		try {
			Path filePath = Paths.get(KB_FILE_PATH);

			// Check if the topic already exists in the file to avoid duplicates
			if (Files.exists(filePath)) {
				String existingContent = Files.readString(filePath).toLowerCase();
				String topicLower = topic.toLowerCase();
				// Check if the topic already has a section header
				if (existingContent.contains("# " + topicLower) ||
					existingContent.contains("## " + topicLower) ||
					existingContent.contains("### " + topicLower)) {
					log.info("Topic '{}' already exists in knowledge base, skipping update", topic);
					return false;
				}
			}

			// Append new topic to the knowledge base file
			String newSection = "\n\n# " + topic + "\n" + content + "\n";
			try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "rw")) {
				raf.seek(raf.length()); // Move to end of file
				raf.writeBytes(newSection);
			}

			log.info("Appended topic '{}' to knowledge base file ({} chars)", topic, content.length());

			// Split the new content into chunks and add to Lucene index
			addChunksForContent(topic, content);

			log.info("Successfully added and indexed new topic: {}", topic);
			return true;
		} catch (IOException e) {
			log.error("Failed to update knowledge base with topic '{}': {}", topic, e.getMessage());
			return false;
		}
	}

	/**
	 * Splits content into paragraph-based chunks and adds them to the Lucene index.
	 */
	private void addChunksForContent(String topic, String content) {
		try {
			// Split by paragraphs (double newlines)
			String[] paragraphs = content.split("\\n\\n+", -1);
			int pageNum = 1;
			StringBuilder currentChunk = new StringBuilder();
			int chunkSize = 800;

			// Start with the topic name as context
			currentChunk.append(topic).append(": ");

			for (String para : paragraphs) {
				String trimmed = para.trim();
				if (trimmed.isEmpty()) continue;

				// If adding this paragraph would exceed chunk size, flush current chunk
				if (currentChunk.length() + trimmed.length() > chunkSize && currentChunk.length() > 50) {
					DocumentChunk chunk = new DocumentChunk(
						UUID.randomUUID().toString(),
						currentChunk.toString().trim(),
						"diseases_knowledge_base (auto-added: " + topic + ")",
						pageNum++
					);
					indexService.addChunk(chunk);
					currentChunk.setLength(0);
					currentChunk.append(topic).append(": ");
				}
				currentChunk.append(trimmed).append(" ");
			}

			// Don't forget the last chunk
			if (currentChunk.length() > 50) {
				DocumentChunk chunk = new DocumentChunk(
					UUID.randomUUID().toString(),
					currentChunk.toString().trim(),
					"diseases_knowledge_base (auto-added: " + topic + ")",
					pageNum++
				);
				indexService.addChunk(chunk);
			}

			log.info("Indexed {} chunks for new topic: {}", pageNum, topic);
		} catch (Exception e) {
			log.error("Failed to index chunks for topic '{}': {}", topic, e.getMessage());
		}
	}
}
