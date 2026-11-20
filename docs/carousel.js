// Scroll-snap carousel: the track scrolls natively (swipe, trackpad, keyboard);
// the arrows and dots scroll it, and the dots follow whichever slide is in view.
for (const root of document.querySelectorAll(".carousel")) {
  const track = root.querySelector(".track");
  const slides = [...track.children];
  const dots = root.querySelector(".dots");
  const go = (i) => slides[(i + slides.length) % slides.length].scrollIntoView({ block: "nearest", inline: "center" });
  let current = 0;
  slides.forEach((s, i) => {
    const b = document.createElement("button");
    b.setAttribute("aria-label", s.querySelector("h3").textContent);
    b.onclick = () => go(i);
    dots.append(b);
  });
  const mark = () => [...dots.children].forEach((b, i) => b.setAttribute("aria-current", i === current));
  const io = new IntersectionObserver((entries) => {
    for (const e of entries) if (e.isIntersecting) { current = slides.indexOf(e.target); mark(); }
  }, { root: track, threshold: 0.6 });
  slides.forEach((s) => io.observe(s));
  root.querySelector(".prev").onclick = () => go(current - 1);
  root.querySelector(".next").onclick = () => go(current + 1);
  mark();
}
