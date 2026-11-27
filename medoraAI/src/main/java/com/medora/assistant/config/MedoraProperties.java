package com.medora.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "medora")
public class MedoraProperties {

    private Rag rag = new Rag();
    private Chat chat = new Chat();
    private Prompt prompt = new Prompt();
    private Jwt jwt = new Jwt();
    private Admin admin = new Admin();
    private Emergency emergency = new Emergency();
    private Privacy privacy = new Privacy();
    private boolean storeMessageText = true;
    private RateLimit rateLimit = new RateLimit();
    private Uploads uploads = new Uploads();

    public static class Rag {
        private int topK = 5;
        private double similarityThreshold = 0.6;
        private int chunkSizeTokens = 500;
        private int chunkOverlapTokens = 50;
        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }
        public double getSimilarityThreshold() { return similarityThreshold; }
        public void setSimilarityThreshold(double similarityThreshold) { this.similarityThreshold = similarityThreshold; }
        public int getChunkSizeTokens() { return chunkSizeTokens; }
        public void setChunkSizeTokens(int chunkSizeTokens) { this.chunkSizeTokens = chunkSizeTokens; }
        public int getChunkOverlapTokens() { return chunkOverlapTokens; }
        public void setChunkOverlapTokens(int chunkOverlapTokens) { this.chunkOverlapTokens = chunkOverlapTokens; }
    }

    public static class Chat {
        private int historyTurns = 6;
        private double temperature = 0.2;
        private int maxOutputTokens = 800;
        private int llmTimeoutSeconds = 60;
        public int getHistoryTurns() { return historyTurns; }
        public void setHistoryTurns(int historyTurns) { this.historyTurns = historyTurns; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        public int getMaxOutputTokens() { return maxOutputTokens; }
        public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
        public int getLlmTimeoutSeconds() { return llmTimeoutSeconds; }
        public void setLlmTimeoutSeconds(int llmTimeoutSeconds) { this.llmTimeoutSeconds = llmTimeoutSeconds; }
    }

    public static class Prompt {
        private String version = "v1";
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
    }

    public static class Jwt {
        private String secret = "change-me-to-a-secure-random-secret-at-least-32-bytes";
        private int accessMinutes = 30;
        private int refreshDays = 7;
        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public int getAccessMinutes() { return accessMinutes; }
        public void setAccessMinutes(int accessMinutes) { this.accessMinutes = accessMinutes; }
        public int getRefreshDays() { return refreshDays; }
        public void setRefreshDays(int refreshDays) { this.refreshDays = refreshDays; }
    }

    public static class Admin {
        private String email = "admin@medora.ai";
        private String password = "admin123";
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class Emergency {
        private String number = "112";
        public String getNumber() { return number; }
        public void setNumber(String number) { this.number = number; }
    }

    public static class Privacy {
        private boolean maskPii = true;
        public boolean isMaskPii() { return maskPii; }
        public void setMaskPii(boolean maskPii) { this.maskPii = maskPii; }
    }

    public static class RateLimit {
        private int chatPerMinute = 30;
        private int uploadPerHour = 10;
        public int getChatPerMinute() { return chatPerMinute; }
        public void setChatPerMinute(int chatPerMinute) { this.chatPerMinute = chatPerMinute; }
        public int getUploadPerHour() { return uploadPerHour; }
        public void setUploadPerHour(int uploadPerHour) { this.uploadPerHour = uploadPerHour; }
    }

    public static class Uploads {
        private String directory = "/data/uploads";
        public String getDirectory() { return directory; }
        public void setDirectory(String directory) { this.directory = directory; }
    }

    public Rag getRag() { return rag; }
    public void setRag(Rag rag) { this.rag = rag; }
    public Chat getChat() { return chat; }
    public void setChat(Chat chat) { this.chat = chat; }
    public Prompt getPrompt() { return prompt; }
    public void setPrompt(Prompt prompt) { this.prompt = prompt; }
    public Jwt getJwt() { return jwt; }
    public void setJwt(Jwt jwt) { this.jwt = jwt; }
    public Admin getAdmin() { return admin; }
    public void setAdmin(Admin admin) { this.admin = admin; }
    public Emergency getEmergency() { return emergency; }
    public void setEmergency(Emergency emergency) { this.emergency = emergency; }
    public Privacy getPrivacy() { return privacy; }
    public void setPrivacy(Privacy privacy) { this.privacy = privacy; }
    public boolean isStoreMessageText() { return storeMessageText; }
    public void setStoreMessageText(boolean storeMessageText) { this.storeMessageText = storeMessageText; }
    public RateLimit getRateLimit() { return rateLimit; }
    public void setRateLimit(RateLimit rateLimit) { this.rateLimit = rateLimit; }
    public Uploads getUploads() { return uploads; }
    public void setUploads(Uploads uploads) { this.uploads = uploads; }
}
