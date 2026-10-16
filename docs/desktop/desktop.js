"use strict";

const themes = {
  light: ["Cobalt", "crisp blue on bright, porcelain surfaces."],
  dark: ["Tidal", "cool cyan over deep ocean surfaces."],
  black: ["Amber", "warm gold against a charcoal workspace."],
  forest: ["Forest", "fresh greens with rich botanical depth."],
  orchid: ["Orchid", "soft violet over a deep plum foundation."],
  rose: ["Rose", "expressive pink on warm, light surfaces."]
};
const themeImage = document.querySelector("#theme-image");
const themeCaption = document.querySelector("#theme-caption");
document.querySelectorAll("[data-theme]").forEach(button => {
  button.addEventListener("click", () => {
    const key = button.dataset.theme;
    const [name, description] = themes[key];
    document.querySelectorAll("[data-theme]").forEach(item => item.setAttribute("aria-pressed", String(item === button)));
    themeImage.src = `images/theme-${key}.png`;
    themeImage.alt = `${name} theme preview with sample downloads`;
    themeCaption.textContent = `${name} — ${description}`;
  });
});
document.querySelectorAll("[data-platform]").forEach(button => {
  button.addEventListener("click", () => {
    document.querySelectorAll("[data-platform]").forEach(item => item.setAttribute("aria-pressed", String(item === button)));
    document.querySelectorAll(".platform-panel").forEach(panel => { panel.hidden = panel.id !== `platform-${button.dataset.platform}`; });
  });
});
const viewer = document.querySelector("#image-viewer");
if (typeof viewer.showModal === "function") {
  document.querySelectorAll("[data-lightbox]").forEach(link => {
    link.addEventListener("click", event => {
      event.preventDefault();
      const image = link.querySelector("img");
      const expanded = document.querySelector("#expanded-image");
      expanded.src = link.href;
      expanded.alt = image.alt;
      document.querySelector("#expanded-caption").textContent = image.alt;
      viewer.showModal();
    });
  });
  document.querySelector("#close-viewer").addEventListener("click", () => viewer.close());
  viewer.addEventListener("click", event => {
    const bounds = viewer.getBoundingClientRect();
    if (event.target === viewer && (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom)) viewer.close();
  });
}
