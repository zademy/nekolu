/**
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

/**
 * Nekolu API JavaScript client.
 * Consumes the REST API from Thymeleaf-based frontend screens.
 */
const TelegramAPI = {
  baseUrl: "/api/telegram",

  /**
   * Upload a file to Telegram.
   */
  async uploadFile(formData) {
    const response = await fetch(`${this.baseUrl}/files/upload`, {
      method: "POST",
      body: formData,
    });
    if (!response.ok)
      throw new Error(`Error uploading file: ${response.statusText}`);
    return response.json();
  },

  /**
   * List files with advanced filters.
   */
  async listFiles(params = {}) {
    const query = new URLSearchParams();
    if (params.type) query.set("type", params.type);
    if (params.limit) query.set("limit", params.limit);
    if (params.offset) query.set("offset", params.offset);
    if (params.sort) query.set("sort", params.sort);
    if (params.minDate) query.set("minDate", params.minDate);
    if (params.maxDate) query.set("maxDate", params.maxDate);
    if (params.minSize) query.set("minSize", params.minSize);
    if (params.maxSize) query.set("maxSize", params.maxSize);
    if (params.chatId) query.set("chatId", params.chatId);
    if (params.filenameContains)
      query.set("filenameContains", params.filenameContains);

    const response = await fetch(`${this.baseUrl}/files?${query.toString()}`);
    if (!response.ok) throw new Error(`Error ${response.status}`);
    return response.json();
  },

  /**
   * Fetch file details.
   */
  async getFileInfo(fileId) {
    const response = await fetch(`${this.baseUrl}/files/${fileId}`);
    if (!response.ok) throw new Error(`Error ${response.status}`);
    return response.json();
  },

  /**
   * Start a file download.
   */
  async downloadFile(fileId) {
    const response = await fetch(`${this.baseUrl}/files/${fileId}/download`, {
      method: "POST",
    });
    if (!response.ok) throw new Error(`Error ${response.status}`);
    return response.json();
  },

  /**
   * Create a batch download job.
   */
  async createBatchJob(fileIds) {
    const response = await fetch(`${this.baseUrl}/files/batch-download`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ fileIds }),
    });
    if (!response.ok) throw new Error(`Error ${response.status}`);
    return response.json();
  },

  /**
   * Fetch file statistics.
   */
  async getStats() {
    const response = await fetch(`${this.baseUrl}/files/stats`);
    if (!response.ok) throw new Error(`Error ${response.status}`);
    return response.json();
  },

  /**
   * Fetch the full statistics bundle (files, storage, network, and limits).
   */
  async getFullStats() {
    const response = await fetch(`${this.baseUrl}/files/stats/full`);
    if (!response.ok) throw new Error(`Error ${response.status}`);
    return response.json();
  },

  /**
   * Export files.
   */
  async exportFiles(format = "json", type = null) {
    const query = new URLSearchParams({ format });
    if (type) query.set("type", type);
    const response = await fetch(
      `${this.baseUrl}/files/export?${query.toString()}`,
    );
    if (!response.ok) throw new Error(`Error ${response.status}`);
    return response.json();
  },

  /**
   * Run a lightweight health check.
   */
  async healthCheck() {
    try {
      const response = await fetch("/actuator/health", {
        signal: AbortSignal.timeout(3000),
      });
      return response.ok;
    } catch {
      return false;
    }
  },

  /**
   * Delete multiple files in a batch request.
   */
  async bulkDelete(items, permanent = false) {
    const response = await fetch(`${this.baseUrl}/files/bulk-delete`, {
      method: "DELETE",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ items, permanent }),
    });
    if (!response.ok) throw new Error(`Error ${response.status}`);
    return response.json();
  },

  /**
   * Create a new folder backed by a private Telegram channel.
   */
  async createFolder(title, description) {
    const response = await fetch(`${this.baseUrl}/folders`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ title, description }),
    });

    const data = await response.json();

    if (!response.ok) {
      throw new Error(data.message || `Error ${response.status}`);
    }

    return data;
  },

  /**
   * List all folders backed by private Telegram channels.
   */
  async listFolders() {
    const response = await fetch(`${this.baseUrl}/folders`);
    const data = await response.json();
    if (!response.ok) {
      throw new Error(data.message || `Error ${response.status}`);
    }
    return data;
  },

  /**
   * Delete a folder by its chat ID.
   */
  async deleteFolder(chatId) {
    const response = await fetch(`${this.baseUrl}/folders/${chatId}`, {
      method: "DELETE",
    });
    const data = await response.json();
    if (!response.ok) {
      throw new Error(data.message || `Error ${response.status}`);
    }
    return data;
  },

  /**
   * List files that belong to a specific folder.
   * Uses the dedicated endpoint that searches inside the chat with SearchChatMessages.
   */
  async listFolderFiles(chatId, params = {}) {
    const query = new URLSearchParams();
    if (params.type) query.set("type", params.type);
    if (params.limit) query.set("limit", params.limit);
    const url = `${this.baseUrl}/files/folders/${chatId}/files${query.toString() ? "?" + query.toString() : ""}`;
    const response = await fetch(url);
    const data = await response.json();
    if (!response.ok) {
      throw new Error(data.message || `Error ${response.status}`);
    }
    return data;
  },

  async listTrash() {
    const response = await fetch(`${this.baseUrl}/files/trash`);
    if (!response.ok) throw new Error(`Error ${response.status}`);
    return response.json();
  },

  async restoreFile(fileId) {
    const response = await fetch(`${this.baseUrl}/files/${fileId}/restore`, {
      method: "POST",
    });
    const data = await response.json();
    if (!response.ok) {
      throw new Error(data.message || `Error ${response.status}`);
    }
    return data;
  },

  async moveFile(fileId, virtualPath) {
    const response = await fetch(`${this.baseUrl}/files/${fileId}/move`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ virtualPath }),
    });
    const data = await response.json();
    if (!response.ok) {
      throw new Error(data.message || `Error ${response.status}`);
    }
    return data;
  },

  async archiveFile(fileId, archived = true) {
    const response = await fetch(
      `${this.baseUrl}/files/${fileId}/archive?archived=${archived}`,
      { method: "POST" },
    );
    const data = await response.json();
    if (!response.ok) {
      throw new Error(data.message || `Error ${response.status}`);
    }
    return data;
  },
};
