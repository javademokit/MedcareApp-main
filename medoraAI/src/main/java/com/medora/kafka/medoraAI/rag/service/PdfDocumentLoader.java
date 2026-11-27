package com.medora.kafka.medoraAI.rag.service;

import com.medora.kafka.medoraAI.rag.model.DocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.UUID;

@Service
public class PdfDocumentLoader {

	private static final Logger log = LoggerFactory.getLogger(PdfDocumentLoader.class);
	private final DocumentIndexService indexService;

	public PdfDocumentLoader(DocumentIndexService indexService) {
		this.indexService = indexService;
	}

	@PostConstruct
	public void load() {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				new ClassPathResource("diseases_knowledge_base.pdf.txt").getInputStream()))) {
			StringBuilder sb = new StringBuilder();
			reader.lines().forEach(line -> sb.append(line).append("\n"));
			String text = sb.toString();

			// Split by paragraphs (double newlines or section headers) for coherent chunks
			String[] paragraphs = text.split("(?m)^#|^\n\n+", -1);
			int pageNum = 1;
			StringBuilder currentChunk = new StringBuilder();
			int chunkSize = 800; // Target chunk size

			for (String para : paragraphs) {
				String trimmed = para.trim();
				if (trimmed.isEmpty()) continue;

				// If adding this paragraph would exceed chunk size, flush current chunk
				if (currentChunk.length() + trimmed.length() > chunkSize && currentChunk.length() > 0) {
					DocumentChunk chunk = new DocumentChunk(
						UUID.randomUUID().toString(),
						currentChunk.toString().trim(),
						"diseases_knowledge_base",
						pageNum++
					);
					indexService.addChunk(chunk);
					currentChunk.setLength(0);
				}
				currentChunk.append(trimmed).append(" ");
			}

			// Don't forget the last chunk
			if (currentChunk.length() > 0) {
				DocumentChunk chunk = new DocumentChunk(
					UUID.randomUUID().toString(),
					currentChunk.toString().trim(),
					"diseases_knowledge_base",
					pageNum++
				);
				indexService.addChunk(chunk);
			}
			log.info("Loaded disease knowledge base into index with {} paragraph-based chunks", pageNum);
		} catch (IOException e) {
			log.warn("Could not load knowledge base: {}", e.getMessage());
		}
	}
}
