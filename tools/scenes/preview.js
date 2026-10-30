// Renders every activity at a few frames into one sheet, for checking by eye.
// Usage: node preview.js out.png
const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");
(async () => {
  const src = fs.readFileSync(path.join(__dirname, "activities.js"), "utf8");
  const b = await chromium.launch();
  const p = await b.newPage();
  const errs = []; p.on("pageerror", e => errs.push(e.message));
  await p.setContent(`<canvas id=c></canvas><script>${src}</script>`);
  const url = await p.evaluate(() => {
    const A = ACTIVITIES, S = 3, frames = [0, 6, 12, 18], hues = [270, 0, 200, 140, 30, 220, 45, 255, null];
    const c = document.getElementById("c");
    c.width = A.W * S * frames.length; c.height = A.H * S * A.LIST.length;
    const ctx = c.getContext("2d");
    ctx.imageSmoothingEnabled = false;
    const t = document.createElement("canvas"); t.width = A.W; t.height = A.H;
    const tc = t.getContext("2d");
    const art = document.createElement("canvas"); art.width = A.W; art.height = A.H;
    const ac = art.getContext("2d");
    A.LIST.forEach((a, row) => frames.forEach((f, col) => {
      ac.clearRect(0, 0, A.W, A.H);
      A.paint(ac, a.id, f);
      A.drawSky(tc, hues[row]);
      tc.drawImage(art, 0, 0);
      ctx.drawImage(t, col * A.W * S, row * A.H * S, A.W * S, A.H * S);
    }));
    return c.toDataURL();
  });
  fs.writeFileSync(process.argv[2], Buffer.from(url.split(",")[1], "base64"));
  console.log(errs.length ? errs : "ok");
  await b.close();
})();
