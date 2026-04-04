/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 * Project: Nekolu
 */

package com.zademy.nekolu.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Centralizes application properties required to bootstrap and operate the shared TDLib client.
 */
@Configuration
public class TelegramConfig {

    private final int apiId;
    private final String apiHash;
    private final String databaseDirectory;
    private final String filesDirectory;
    private final String systemLanguageCode;
    private final String deviceModel;
    private final String systemVersion;
    private final String applicationVersion;
    private final boolean useTestDc;
    private final boolean useSecretChats;
    private final boolean useMessageDatabase;
    private final boolean useChatInfoDatabase;
    private final boolean useFileDatabase;
    private final long userId;

    public TelegramConfig(
            @Value("${telegram.api.id}") int apiId,
            @Value("${telegram.api.hash}") String apiHash,
            @Value("${telegram.database-directory}") String databaseDirectory,
            @Value("${telegram.files-directory}") String filesDirectory,
            @Value("${telegram.system.language-code}") String systemLanguageCode,
            @Value("${telegram.system.device-model}") String deviceModel,
            @Value("${telegram.system.version}") String systemVersion,
            @Value("${telegram.app.version}") String applicationVersion,
            @Value("${telegram.use-test-dc}") boolean useTestDc,
            @Value("${telegram.use-secret-chats}") boolean useSecretChats,
            @Value("${telegram.use-message-database}") boolean useMessageDatabase,
            @Value("${telegram.use-chat-info-database}") boolean useChatInfoDatabase,
            @Value("${telegram.use-file-database}") boolean useFileDatabase,
            @Value("${telegram.user.id}") long userId) {
        this.apiId = apiId;
        this.apiHash = apiHash;
        this.databaseDirectory = databaseDirectory;
        this.filesDirectory = filesDirectory;
        this.systemLanguageCode = systemLanguageCode;
        this.deviceModel = deviceModel;
        this.systemVersion = systemVersion;
        this.applicationVersion = applicationVersion;
        this.useTestDc = useTestDc;
        this.useSecretChats = useSecretChats;
        this.useMessageDatabase = useMessageDatabase;
        this.useChatInfoDatabase = useChatInfoDatabase;
        this.useFileDatabase = useFileDatabase;
        this.userId = userId;
    }

    public int getApiId() {
        return apiId;
    }

    public String getApiHash() {
        return apiHash;
    }

    public String getDatabaseDirectory() {
        return databaseDirectory;
    }

    public String getFilesDirectory() {
        return filesDirectory;
    }

    public String getSystemLanguageCode() {
        return systemLanguageCode;
    }

    public String getDeviceModel() {
        return deviceModel;
    }

    public String getSystemVersion() {
        return systemVersion;
    }

    public String getApplicationVersion() {
        return applicationVersion;
    }

    public boolean isUseTestDc() {
        return useTestDc;
    }

    public boolean isUseSecretChats() {
        return useSecretChats;
    }

    public boolean isUseMessageDatabase() {
        return useMessageDatabase;
    }

    public boolean isUseChatInfoDatabase() {
        return useChatInfoDatabase;
    }

    public boolean isUseFileDatabase() {
        return useFileDatabase;
    }

    public long getUserId() {
        return userId;
    }
}
