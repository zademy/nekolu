/**
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

(() => {
  class GridTableAdapter {
    constructor(selector, config = {}) {
      this.tableElement =
        typeof selector === "string"
          ? document.querySelector(selector)
          : selector;
      if (!this.tableElement) {
        throw new Error("Grid target not found");
      }

      this.config = config;
      this.id =
        this.tableElement.id ||
        `grid-${Math.random().toString(36).slice(2, 10)}`;
      this.currentData = [];
      this.pendingData = null;
      this.listeners = { draw: [] };
      this.pageLength = config.pageLength || 20;
      this.headerText = Array.from(
        this.tableElement.querySelectorAll("thead th"),
      ).map((th) => th.textContent.trim());
      this.columnDefs = new Map(
        (config.columnDefs || []).map((definition) => [
          definition.targets,
          definition,
        ]),
      );

      this.wrapper = document.createElement("div");
      this.wrapper.id = `${this.id}_wrapper`;
      this.wrapper.className = "tg-grid-wrapper";
      this.tableElement.replaceWith(this.wrapper);

      const self = this;
      const rowsAccessor = function () {
        return {
          data() {
            return {
              toArray() {
                return [...self.currentData];
              },
            };
          },
        };
      };

      rowsAccessor.add = function (rows) {
        self.pendingData = Array.isArray(rows) ? [...rows] : [];
        return self;
      };

      this.rows = rowsAccessor;
      this.ajax = {
        reload: (_callback, resetPaging = true) => this.reload(resetPaging),
      };

      this.reload(true);
    }

    async reload(resetPaging = true) {
      if (resetPaging) {
        this.currentPage = 0;
      }

      if (this.pendingData) {
        this.currentData = [...this.pendingData];
        this.pendingData = null;
        this.render();
        return;
      }

      if (!this.config.ajax?.url) {
        this.currentData = [];
        this.render();
        return;
      }

      try {
        const url = new URL(this.resolveUrl(), window.location.origin);
        const params =
          typeof this.config.ajax.data === "function"
            ? this.config.ajax.data({})
            : this.config.ajax.data || {};

        Object.entries(params).forEach(([key, value]) => {
          if (value === undefined || value === null || value === "") {
            return;
          }
          url.searchParams.set(key, value);
        });

        this.wrapper.classList.add("is-loading");
        const response = await fetch(url.toString());
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`);
        }

        const json = await response.json();
        const extracted =
          typeof this.config.ajax.dataSrc === "function"
            ? this.config.ajax.dataSrc(json)
            : json;

        this.currentData = Array.isArray(extracted) ? extracted : [];
        this.render();
      } catch (error) {
        this.currentData = [];
        this.renderError(error);
      } finally {
        this.wrapper.classList.remove("is-loading");
      }
    }

    clear() {
      this.pendingData = [];
      return this;
    }

    draw() {
      if (this.pendingData) {
        this.currentData = [...this.pendingData];
        this.pendingData = null;
      }
      this.render();
      return this;
    }

    on(eventName, callback) {
      if (!this.listeners[eventName]) {
        this.listeners[eventName] = [];
      }
      this.listeners[eventName].push(callback);
      return this;
    }

    resolveUrl() {
      return typeof this.config.ajax.url === "function"
        ? this.config.ajax.url()
        : this.config.ajax.url;
    }

    render() {
      if (typeof gridjs === "undefined") {
        this.renderError(new Error("Grid.js no está disponible"));
        return;
      }

      this.wrapper.innerHTML = "";

      const columns = this.headerText.map((header, index) => ({
        id: this.createColumnId(header, index),
        name: header || `Columna ${index + 1}`,
        sort: false,
      }));

      const data = this.currentData.map((row) =>
        (this.config.columns || []).map((column, index) =>
          this.normalizeCell(this.renderCell(row, column, index)),
        ),
      );

      this.grid = new gridjs.Grid({
        columns,
        data,
        pagination: {
          enabled: this.config.paging !== false,
          limit: this.pageLength,
          summary: this.config.info !== false,
        },
        sort: false,
        search: false,
        className: {
          container: "tg-grid-container",
          table: "tg-grid-table",
          th: "tg-grid-head",
          td: "tg-grid-cell",
          pagination: "tg-grid-pagination",
        },
        language: {
          noRecordsFound:
            this.config.language?.emptyTable ||
            this.config.language?.zeroRecords ||
            "No hay registros",
          pagination: {
            previous: this.config.language?.paginate?.previous || "Anterior",
            next: this.config.language?.paginate?.next || "Siguiente",
            showing: "Mostrando",
            results: () => "resultados",
            of: "de",
            to: "a",
          },
        },
      });

      this.grid.render(this.wrapper);
      this.dispatch("draw");
    }

    renderError(error) {
      this.wrapper.innerHTML = `
        <div class="empty-state">
          <i class="fa-solid fa-triangle-exclamation"></i>
          <h3>Error cargando la tabla</h3>
          <p>${window.escapeHtml ? window.escapeHtml(error.message) : error.message}</p>
        </div>
      `;
      this.dispatch("draw");
    }

    renderCell(row, column, index) {
      const definition = this.columnDefs.get(index);
      const dataKey = column?.data;
      const sourceData =
        dataKey === null || dataKey === undefined ? row : row[dataKey];

      if (typeof definition?.render === "function") {
        return definition.render(sourceData, "display", row);
      }

      if (dataKey === null || dataKey === undefined) {
        return "";
      }

      return sourceData ?? "";
    }

    normalizeCell(value) {
      if (value === null || value === undefined) {
        return "";
      }

      if (typeof value === "string" && /<[^>]+>/.test(value)) {
        return gridjs.html(value);
      }

      return value;
    }

    createColumnId(header, index) {
      const normalized = (header || "")
        .normalize("NFD")
        .replace(/[\u0300-\u036f]/g, "")
        .replace(/[^a-zA-Z0-9_$]+/g, "");

      if (normalized && /^[a-zA-Z_$]/.test(normalized)) {
        return normalized;
      }

      return `col${index + 1}`;
    }

    dispatch(eventName) {
      (this.listeners[eventName] || []).forEach((callback) => callback());
    }
  }

  window.DataTable = GridTableAdapter;
})();
