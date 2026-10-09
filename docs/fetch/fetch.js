(function () {
  if (window.hljs) hljs.highlightAll();
  document.querySelectorAll("pre.src").forEach(function (pre) {
    var code = pre.querySelector("code");
    if (!code || pre.querySelector(".gutter")) return;
    var raw = code.innerText.replace(/\n$/, "");
    var count = raw.length ? raw.split("\n").length : 1;
    var gutter = document.createElement("div");
    gutter.className = "gutter";
    gutter.setAttribute("aria-hidden", "true");
    var numbers = [];
    for (var i = 1; i <= count; i++) numbers.push(i);
    gutter.textContent = numbers.join("\n");
    pre.insertBefore(gutter, code);
  });
})();
