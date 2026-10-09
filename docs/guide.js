(function () {
  var langKey = "fetch-docs-lang";
  var seenKey = "fetch-docs-seen";
  var seen = {};
  try {
    seen = JSON.parse(localStorage.getItem(seenKey) || "{}") || {};
  } catch (e) {
    seen = {};
  }

  function remember() {
    try {
      localStorage.setItem(seenKey, JSON.stringify(seen));
    } catch (e) {}
  }

  function setSnippet(snippet, lang) {
    snippet.setAttribute("data-lang", lang);
    snippet.querySelectorAll("[data-pick]").forEach(function (btn) {
      btn.setAttribute("aria-pressed", btn.getAttribute("data-pick") === lang ? "true" : "false");
    });
  }

  function setAll(lang) {
    document.querySelectorAll(".snippet").forEach(function (snippet) {
      setSnippet(snippet, lang);
    });
    document.querySelectorAll(".lang-global [data-pick]").forEach(function (btn) {
      btn.setAttribute("aria-pressed", btn.getAttribute("data-pick") === lang ? "true" : "false");
    });
    try {
      localStorage.setItem(langKey, lang);
    } catch (e) {}
  }

  document.querySelectorAll(".snippet").forEach(function (snippet) {
    snippet.querySelectorAll("[data-pick]").forEach(function (btn) {
      btn.addEventListener("click", function () {
        setSnippet(snippet, btn.getAttribute("data-pick"));
      });
    });
    var copy = snippet.querySelector(".copy");
    if (!copy) return;
    copy.addEventListener("click", function () {
      var lang = snippet.getAttribute("data-lang") || "kotlin";
      var pre = snippet.querySelector("pre." + lang);
      var block = pre ? pre.querySelector("code") : null;
      var text = block && block.dataset.raw ? block.dataset.raw : (pre ? pre.innerText : "");
      var done = function () {
        copy.classList.add("done");
        copy.textContent = "Copied";
        setTimeout(function () {
          copy.classList.remove("done");
          copy.textContent = "Copy";
        }, 1400);
      };
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(done);
      }
    });
  });

  document.querySelectorAll(".lang-global [data-pick]").forEach(function (btn) {
    btn.addEventListener("click", function () {
      setAll(btn.getAttribute("data-pick"));
    });
  });

  var saved = "kotlin";
  try {
    saved = localStorage.getItem(langKey) || "kotlin";
  } catch (e) {}
  setAll(saved === "java" ? "java" : "kotlin");

  if (window.hljs) hljs.highlightAll();
  document.querySelectorAll(".snippet pre").forEach(function (pre) {
    var code = pre.querySelector("code");
    if (!code || pre.querySelector(".gutter")) return;
    var raw = code.innerText.replace(/\n$/, "");
    code.dataset.raw = raw;
    var count = raw.length ? raw.split("\n").length : 1;
    var gutter = document.createElement("div");
    gutter.className = "gutter";
    gutter.setAttribute("aria-hidden", "true");
    var numbers = [];
    for (var i = 1; i <= count; i++) numbers.push(i);
    gutter.textContent = numbers.join("\n");
    pre.insertBefore(gutter, code);
  });

  var search = document.getElementById("find");
  if (search) {
    search.addEventListener("input", function () {
      var q = search.value.trim().toLowerCase();
      document.querySelectorAll(".side a[href^='#']").forEach(function (link) {
        link.hidden = q.length > 0 && link.textContent.toLowerCase().indexOf(q) === -1;
      });
    });
  }

  var steps = document.querySelectorAll(".step");
  var total = steps.length;
  var bar = document.querySelector(".bar i");
  var count = document.getElementById("seen-count");
  var seal = document.querySelector(".seal");
  var rail = document.getElementById("rail-links");
  var railTitle = document.getElementById("rail-title");
  var sideLinks = {};
  document.querySelectorAll(".side a[href^='#']").forEach(function (link) {
    sideLinks[link.getAttribute("href").slice(1)] = link;
  });

  function paint() {
    var n = 0;
    steps.forEach(function (step) {
      if (seen[step.id]) n += 1;
    });
    var pct = total ? Math.round((n / total) * 100) : 0;
    if (bar) bar.style.width = pct + "%";
    if (count) count.textContent = n + " of " + total + " steps along the path";
    if (seal) seal.classList.toggle("complete", total > 0 && n >= total);
    document.querySelectorAll(".chapters a").forEach(function (link) {
      var id = (link.getAttribute("href") || "").slice(1);
      var chapter = document.getElementById(id);
      if (!chapter) return;
      var ids = Array.prototype.map.call(chapter.querySelectorAll(".step"), function (step) {
        return step.id;
      });
      link.classList.toggle("seen", ids.length > 0 && ids.every(function (sid) { return seen[sid]; }));
    });
    Object.keys(sideLinks).forEach(function (id) {
      sideLinks[id].classList.toggle("seen", !!seen[id]);
    });
  }

  function fillRail(chapter) {
    if (!rail || !chapter) return;
    var title = chapter.querySelector(".chapter-head h2");
    if (railTitle && title) railTitle.textContent = title.textContent;
    rail.innerHTML = "";
    chapter.querySelectorAll(".step h3").forEach(function (heading) {
      var step = heading.closest(".step");
      var link = document.createElement("a");
      var badge = heading.querySelector(".badge");
      var title = badge
        ? heading.textContent.replace(badge.textContent, "").replace(/\s+/g, " ").trim()
        : heading.textContent.replace(/\s+/g, " ").trim();
      link.href = "#" + step.id;
      link.textContent = badge ? badge.textContent + "  " + title : title;
      rail.appendChild(link);
    });
  }

  var watcher = new IntersectionObserver(function (entries) {
    entries.forEach(function (entry) {
      if (!entry.isIntersecting) return;
      var step = entry.target;
      seen[step.id] = 1;
      remember();
      paint();
      Object.keys(sideLinks).forEach(function (id) {
        sideLinks[id].classList.toggle("active", id === step.id);
      });
      var chapter = step.closest(".chapter");
      if (!chapter) return;
      document.querySelectorAll(".chapters a").forEach(function (link) {
        link.classList.toggle("here", link.getAttribute("href") === "#" + chapter.id);
      });
      if (rail && rail.getAttribute("data-chapter") !== chapter.id) {
        rail.setAttribute("data-chapter", chapter.id);
        fillRail(chapter);
      }
      document.querySelectorAll("#rail-links a").forEach(function (link) {
        link.classList.toggle("active", link.getAttribute("href") === "#" + step.id);
      });
    });
  }, { rootMargin: "-12% 0px -40% 0px", threshold: 0.05 });

  steps.forEach(function (step) { watcher.observe(step); });
  document.querySelectorAll(".reveal").forEach(function (el) {
    var show = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        entry.target.classList.add("in");
        show.unobserve(entry.target);
      });
    }, { threshold: 0.15 });
    show.observe(el);
  });
  paint();
  var first = document.querySelector(".chapter");
  if (first) fillRail(first);
})();
