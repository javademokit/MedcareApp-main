package com.medora.kafka.medoraAI.rag.service;

import com.medora.kafka.medoraAI.rag.model.DocumentChunk;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentIndexService {

	private static final Logger log = LoggerFactory.getLogger(DocumentIndexService.class);

	private Directory indexDirectory;
	private IndexWriter indexWriter;
	private IndexSearcher indexSearcher;
	private DirectoryReader lastReader;

	@PostConstruct
	public void init() {
		try {
			indexDirectory = new ByteBuffersDirectory();
			IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
			config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
			indexWriter = new IndexWriter(indexDirectory, config);
			lastReader = DirectoryReader.open(indexWriter);
			indexSearcher = new IndexSearcher(lastReader);
		} catch (IOException e) {
			log.error("Failed to initialize Lucene index", e);
		}
	}

	public void addChunk(DocumentChunk chunk) {
		try {
			Document doc = new Document();
			doc.add(new StringField("id", chunk.getId(), Field.Store.YES));
			doc.add(new TextField("content", chunk.getContent(), Field.Store.YES));
			doc.add(new StringField("source", chunk.getSource(), Field.Store.YES));
			doc.add(new IntField("page", chunk.getPage(), Field.Store.YES));
			indexWriter.addDocument(doc);
			indexWriter.commit();
			refreshSearcher();
		} catch (IOException e) {
			log.error("Failed to add chunk", e);
		}
	}

	public List<DocumentChunk> search(String query, int topK) {
		List<DocumentChunk> results = new ArrayList<>();
		try {
			refreshSearcher();
			if (indexSearcher == null) return results;
			QueryParser parser = new QueryParser("content", new StandardAnalyzer());
			Query q = parser.parse(query);
			TopDocs hits = indexSearcher.search(q, topK * 2); // Fetch more to filter by score
			for (ScoreDoc hit : hits.scoreDocs) {
				// Only include results with meaningful relevance score
				if (hit.score > 0.5f) {
					Document doc = indexSearcher.doc(hit.doc);
					DocumentChunk chunk = new DocumentChunk(
						doc.get("id"),
						doc.get("content"),
						doc.get("source"),
						Integer.parseInt(doc.get("page"))
					);
					chunk.setRelevanceScore(hit.score);
					results.add(chunk);
				}
				// Stop if we have enough high-quality results
				if (results.size() >= topK) break;
			}
			log.info("Search for '{}': found {} results (score > 0.5) out of {} hits",
				query, results.size(), hits.scoreDocs.length);
		} catch (Exception e) {
			log.error("Search failed", e);
		}
		return results;
	}

	private void refreshSearcher() throws IOException {
		if (indexWriter != null && lastReader != null) {
			DirectoryReader newReader = DirectoryReader.openIfChanged(lastReader, indexWriter);
			if (newReader != null) {
				lastReader.close();
				lastReader = newReader;
				indexSearcher = new IndexSearcher(lastReader);
			}
		}
	}
}
