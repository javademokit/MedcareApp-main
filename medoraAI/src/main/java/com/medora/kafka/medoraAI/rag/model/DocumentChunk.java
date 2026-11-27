package com.medora.kafka.medoraAI.rag.model;

public class DocumentChunk {
	private String id;
	private String content;
	private String source;
	private int page;
	private float relevanceScore;

	public DocumentChunk() {}

	public DocumentChunk(String id, String content, String source, int page) {
		this.id = id;
		this.content = content;
		this.source = source;
		this.page = page;
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }
	public String getContent() { return content; }
	public void setContent(String content) { this.content = content; }
	public String getSource() { return source; }
	public void setSource(String source) { this.source = source; }
	public int getPage() { return page; }
	public void setPage(int page) { this.page = page; }
	public float getRelevanceScore() { return relevanceScore; }
	public void setRelevanceScore(float relevanceScore) { this.relevanceScore = relevanceScore; }
}
