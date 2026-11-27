package com.medora.kafka.medoraAI.rag.model;

import java.util.List;

public class QuestionResponse {
	private String answer;
	private double confidence;
	private List<String> sources;
	private String disclaimer;
	private boolean autoAddedToKB;

	public String getAnswer() { return answer; }
	public void setAnswer(String answer) { this.answer = answer; }
	public double getConfidence() { return confidence; }
	public void setConfidence(double confidence) { this.confidence = confidence; }
	public List<String> getSources() { return sources; }
	public void setSources(List<String> sources) { this.sources = sources; }
	public String getDisclaimer() { return disclaimer; }
	public void setDisclaimer(String disclaimer) { this.disclaimer = disclaimer; }
	public boolean isAutoAddedToKB() { return autoAddedToKB; }
	public void setAutoAddedToKB(boolean autoAddedToKB) { this.autoAddedToKB = autoAddedToKB; }
}
