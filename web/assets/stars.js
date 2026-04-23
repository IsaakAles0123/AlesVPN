/* Звёздное поле: не рисует, если prefers-reduced-motion */
(function () {
  var c = document.getElementById("star-canvas");
  if (!c) return;
  if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
  var ctx = c.getContext("2d");
  var w, h, d, stars, n = 260;

  function size() {
    d = window.devicePixelRatio || 1;
    w = c.width = window.innerWidth * d;
    h = c.height = window.innerHeight * d;
    c.style.width = window.innerWidth + "px";
    c.style.height = window.innerHeight + "px";
  }

  function gen() {
    stars = [];
    for (var i = 0; i < n; i++) {
      var micro = i < n * 0.45;
      stars.push({
        x: Math.random() * w,
        y: Math.random() * h,
        r: micro ? Math.random() * 0.45 + 0.12 : Math.random() * 1.1 + 0.2,
        a: micro ? 0.08 + Math.random() * 0.28 : 0.12 + Math.random() * 0.5,
        p: Math.random() * 6.28,
      });
    }
  }

  function tick(t) {
    t *= 0.001;
    ctx.clearRect(0, 0, w, h);
    for (var i = 0; i < stars.length; i++) {
      var s = stars[i];
      var f = 0.55 + 0.45 * Math.sin(t * 0.5 + s.p);
      ctx.beginPath();
      ctx.fillStyle = "rgba(255,255,255," + s.a * f + ")";
      ctx.arc(s.x, s.y, s.r, 0, 6.283);
      ctx.fill();
    }
    requestAnimationFrame(tick);
  }

  function run() {
    size();
    gen();
    requestAnimationFrame(tick);
  }

  window.addEventListener("resize", function () {
    size();
    gen();
  });
  run();
})();
