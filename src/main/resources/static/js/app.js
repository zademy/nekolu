/**
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

document.addEventListener("DOMContentLoaded", () => {
  const sidebarToggle = document.getElementById("sidebarToggle");
  const sidebar = document.getElementById("sidebar");
  const sidebarBackdrop = document.getElementById("sidebarBackdrop");
  const statusEl = document.getElementById("connectionStatus");
  const topbar = document.querySelector(".topbar");

  window.softReload = function () {
    window.location.reload();
  }

  function openSidebar() {
    if (!sidebar) return;
    sidebar.classList.add("show");
    sidebarBackdrop?.classList.add("show");
    document.body.classList.add("sidebar-open");
  }

  function closeSidebar() {
    if (!sidebar) return;
    sidebar.classList.remove("show");
    sidebarBackdrop?.classList.remove("show");
    document.body.classList.remove("sidebar-open");
  }

  if (sidebarToggle && sidebar) {
    sidebarToggle.addEventListener("click", () => {
      if (sidebar.classList.contains("show")) {
        closeSidebar();
        return;
      }
      openSidebar();
    });

    sidebarBackdrop?.addEventListener("click", closeSidebar);

    document.addEventListener("click", (event) => {
      if (
        window.innerWidth > 1024 ||
        !sidebar.classList.contains("show") ||
        sidebar.contains(event.target) ||
        sidebarToggle.contains(event.target)
      ) {
        return;
      }
      closeSidebar();
    });
  }

  class ModalShim {
    static instances = new WeakMap();

    constructor(element) {
      this.element = element;
      ModalShim.instances.set(element, this);
    }

    show() {
      if (!this.element) return;
      this.element.style.display = "flex";
      this.element.classList.add("show");
      document.body.classList.add("modal-open");
    }

    hide() {
      if (!this.element) return;
      this.element.classList.remove("show");
      this.element.style.display = "none";
      if (!document.querySelector(".modal.show")) {
        document.body.classList.remove("modal-open");
      }
      this.element.dispatchEvent(new Event("hidden.bs.modal"));
    }

    static getInstance(element) {
      if (!element) return null;
      return ModalShim.instances.get(element) || new ModalShim(element);
    }
  }

  window.bootstrap = {
    Modal: ModalShim,
  };

  document.addEventListener("click", (event) => {
    const dismissButton = event.target.closest('[data-bs-dismiss="modal"]');
    if (dismissButton) {
      const modal = dismissButton.closest(".modal");
      ModalShim.getInstance(modal)?.hide();
      return;
    }

    const modalSurface = event.target.classList?.contains("modal")
      ? event.target
      : null;
    if (modalSurface) {
      ModalShim.getInstance(modalSurface)?.hide();
    }
  });

  document.addEventListener("keydown", (event) => {
    if (event.key !== "Escape") return;
    const openModal = document.querySelector(".modal.show");
    if (openModal) {
      ModalShim.getInstance(openModal)?.hide();
      return;
    }
    closeSidebar();
  });

  window.showToast = function (message, type = "info") {
    const container = document.getElementById("toastContainer");
    if (!container) return;

    const icons = {
      success: "fa-circle-check",
      error: "fa-circle-xmark",
      info: "fa-circle-info",
      warning: "fa-triangle-exclamation",
    };

    const toastEl = document.createElement("div");
    toastEl.className = `toast toast-tg toast-${type}`;
    toastEl.setAttribute("role", "status");
    toastEl.innerHTML = `
      <div class="toast-body">
        <i class="fa-solid ${icons[type] || icons.info}"></i>
        <span>${message}</span>
      </div>
    `;

    container.appendChild(toastEl);
    requestAnimationFrame(() => toastEl.classList.add("show"));

    const removeToast = () => {
      toastEl.classList.remove("show");
      window.setTimeout(() => toastEl.remove(), 220);
    };

    window.setTimeout(removeToast, 4200);
    toastEl.addEventListener("click", removeToast);
  };

  async function checkConnection() {
    if (!statusEl) return;
    const dot = statusEl.querySelector(".status-dot");
    const text = statusEl.querySelector(".status-text");

    try {
      const isUp = await TelegramAPI.healthCheck();
      if (isUp) {
        dot.className = "status-dot status-online";
        text.textContent = "Connected";
        return;
      }
      dot.className = "status-dot status-error";
      text.textContent = "Disconnected";
    } catch {
      dot.className = "status-dot status-error";
      text.textContent = "Error";
    }
  }

  checkConnection();
  window.setInterval(checkConnection, 30000);

  function syncTopbarState() {
    if (!topbar) return;
    topbar.classList.toggle("scrolled", window.scrollY > 12);
  }

  syncTopbarState();
  window.addEventListener("scroll", syncTopbarState, { passive: true });

  window.formatBytes = function (bytes) {
    if (!bytes || bytes === 0) return "0 B";
    const units = ["B", "KB", "MB", "GB", "TB"];
    const index = Math.floor(Math.log(bytes) / Math.log(1024));
    return (bytes / Math.pow(1024, index)).toFixed(1) + " " + units[index];
  };

  window.formatDate = function (timestamp) {
    if (!timestamp) return "-";
    const date =
      typeof timestamp === "number" && timestamp < 9999999999999
        ? new Date(timestamp * 1000)
        : new Date(timestamp);

    return date.toLocaleDateString("en-US", {
      year: "numeric",
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  };

  window.getFileTypeIcon = function (type) {
    const icons = {
      PHOTO: "fa-image",
      VIDEO: "fa-video",
      AUDIO: "fa-music",
      DOCUMENT: "fa-file-lines",
      VOICE: "fa-microphone",
      VIDEO_NOTE: "fa-circle-play",
      FILE: "fa-file",
    };
    return icons[type] || "fa-file";
  };

  window.getFileTypeClass = function (type) {
    const classes = {
      PHOTO: "photo",
      VIDEO: "video",
      AUDIO: "audio",
      DOCUMENT: "document",
      VOICE: "voice",
      VIDEO_NOTE: "video",
      FILE: "document",
    };
    return classes[type] || "document";
  };

  window.escapeHtml = function (text) {
    if (!text) return "";
    const div = document.createElement("div");
    div.textContent = text;
    return div.innerHTML;
  };

  window.formatDuration = function (seconds) {
    if (!seconds || seconds <= 0) return "";
    const hrs = Math.floor(seconds / 3600);
    const mins = Math.floor((seconds % 3600) / 60);
    const secs = Math.floor(seconds % 60);
    if (hrs > 0) {
      return `${hrs}:${mins.toString().padStart(2, "0")}:${secs.toString().padStart(2, "0")}`;
    }
    return `${mins}:${secs.toString().padStart(2, "0")}`;
  };

  window.formatFileSize = window.formatBytes;
});
