// Bakes each activity into a grid of its frames, six to a row (1440 px wide, 75 px per row of
// frames), with the sky left
// see-through, for the TV app to play back. Usage: node render.js <output dir>
const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");
(async () => {
  const out = process.argv[2];
  fs.mkdirSync(out, { recursive: true });
  const src = fs.readFileSync(path.join(__dirname, "activities.js"), "utf8");
  const b = await chromium.launch();
  const p = await b.newPage();
  const errs = []; p.on("pageerror", e => errs.push(e.message));
  await p.setContent(`<script>${src}</script>`);
  const sheets = await p.evaluate(() => {
    const A = ACTIVITIES;
    return A.LIST.map(a => {
      const c = document.createElement("canvas");
      const frames = A.framesOf(a.id);
      c.width = A.W * 6; c.height = A.H * Math.ceil(frames / 6);
      const ctx = c.getContext("2d");
      for (let f = 0; f < frames; f++) {
        ctx.save();
        ctx.translate((f % 6) * A.W, Math.floor(f / 6) * A.H);
        ctx.beginPath(); ctx.rect(0, 0, A.W, A.H); ctx.clip();
        A.paint(ctx, a.id, f);
        ctx.restore();
      }
      return [a.id, c.toDataURL("image/png")];
    });
  });
  for (const [id, url] of sheets) fs.writeFileSync(path.join(out, `${id}.png`), Buffer.from(url.split(",")[1], "base64"));
  console.log(errs.length ? errs : `wrote ${sheets.length} sheets`);
  await b.close();
})();
