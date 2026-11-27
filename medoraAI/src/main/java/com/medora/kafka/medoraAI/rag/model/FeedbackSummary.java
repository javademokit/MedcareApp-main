package com.medora.kafka.medoraAI.rag.model;

import java.util.List;

public class FeedbackSummary {

	private int totalFeedback;
	private int positiveFeedback;
	private int negativeFeedback;
	private double satisfactionRate;
	private List<TopQuestion> topQuestions;

	public int getTotalFeedback() { return totalFeedback; }
	public void setTotalFeedback(int totalFeedback) { this.totalFeedback = totalFeedback; }
	public int getPositiveFeedback() { return positiveFeedback; }
	public void setPositiveFeedback(int positiveFeedback) { this.positiveFeedback = positiveFeedback; }
	public int getNegativeFeedback() { return negativeFeedback; }
	public void setNegativeFeedback(int negativeFeedback) { this.negativeFeedback = negativeFeedback; }
	public double getSatisfactionRate() { return satisfactionRate; }
	public void setSatisfactionRate(double satisfactionRate) { this.satisfactionRate = satisfactionRate; }
	public List<TopQuestion> getTopQuestions() { return topQuestions; }
	public void setTopQuestions(List<TopQuestion> topQuestions) { this.topQuestions = topQuestions; }

	public static class TopQuestion {
		private String question;
		private long count;

		public TopQuestion() {}
		public TopQuestion(String question, long count) {
			this.question = question;
			this.count = count;
		}

		public String getQuestion() { return question; }
		public void setQuestion(String question) { this.question = question; }
		public long getCount() { return count; }
		public void setCount(long count) { this.count = count; }
	}
}
