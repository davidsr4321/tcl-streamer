// The top-shelf scenes: one pixel-art activity per app, 24 frames each, 240×75 pixels.
// The sky (or the wall, indoors) is left see-through so the launcher can fill it with the
// focused app's own colour. render.js bakes these into the PNG strips the TV app plays back.
const ACTIVITIES = (() => {
  const W = 240, H = 75, FRAMES = 24;

  const PALETTE = {
    S: "#e9b99a", s: "#cf9a7c", E: "#2a1a12", K: "#161616",
    H: "#2b1d16", B: "#3a2418", T: "#a98bd8", P: "#2c3340",
    R: "#d9783a", r: "#f0a060", F: "#c47a58", b: "#c0602c", D: "#4f7a4a", J: "#3d4f6e",
    L: "#5a3424", l: "#7a4a30", G: "#c8a868", m: "#c8706a", N: "#1c1c24",
  };
  const pal = (rows, hair, shirt, pants, blinkAt) => ({
    rows, hair, shirt, pants, blinkAt,
    waist: rows.findIndex(l => l.includes(pants.repeat(4))),
    hand: rows.findIndex((l, i) => i >= 10 && l[0] === "S"),
    walkA: [...rows.slice(0, -2), `..${pants.repeat(3)}...${pants.repeat(2)}..`, ".KKKK...KK.."],
    walkB: [...rows.slice(0, -2), `..${pants.repeat(2)}...${pants.repeat(3)}..`, "..KK...KKKK."],
    crouch: [...rows.slice(0, -3), rows[rows.length - 1]],
  });
  const DARK = pal(["...HHHHHH...","..HHHHHHHH..","..HHHHHHHH..","..HSSSSSSH..","..SESSSSES..",".sSSSSSSSSs.","..BSSssSSB..","..BBBBBBBB..","..BBBSSBBB..","...BBBBBB...","....SSSS....",".TTTTTTTTTT.","TTTTTTTTTTTT","TTTTTTTTTTTT","STTTTTTTTTTS","S.TTTTTTTT.S","..PPPPPPPP..","..PPP..PPP..","..PPP..PPP..","..KKK..KKK.."], "H", "T", "P", 17);
  const GINGER = pal(["..r.RR.rR...","..RRRRRRRR..",".RRrRRRRrRR.","..RSSSSSSR..","..SESSSSES..",".sSFSSSSFSs.","..bSSssSSb..","..bSbbbbSb..","..bbbSSbbb..","...bbbbbb...","....SSSS....",".DDDDDDDDDD.","DDDDDDDDDDDD","SDDDDDDDDDDS","S.DDDDDDDD.S","..JJJJJJJJ..","..JJJ..JJJ..","..KKK..KKK.."], "R", "D", "J", 7);
  // Long curly chestnut hair, round gold glasses, black tee, jeans.
  const SHIR = pal(["...LLLLLL...","..LLlLLlLL..",".LLLLLLLLLL.",".LLSSSSSSLL.",".LGEGSSGEGL.",".LSSSSSSSSL.","LLSSSssSSSLL","LLLSSmmSSLLL","LLL.SSSS.LLL","LLLNNNNNNLLL","LLNNNNNNNNLL","LLNNNNNNNNLL","SLNNNNNNNNLS","S.NNNNNNNN.S","..JJJJJJJJ..","..JJJ..JJJ..","..KKK..KKK.."], "L", "N", "J", 13);
  const SKIN = PALETTE.S;

  // ---- Drawing helpers. One canvas unit is one scene pixel. ----
  function kit(ctx) {
    const rect = (x, y, w, h, c) => { ctx.fillStyle = c; ctx.fillRect(x, y, w, h); };
    const px = (x, y, c) => rect(x, y, 1, 1, c);
    const line = (x0, y0, x1, y1, c) => {
      const n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) || 1;
      for (let i = 0; i <= n; i++) px(Math.round(x0 + (x1 - x0) * i / n), Math.round(y0 + (y1 - y0) * i / n), c);
    };
    const disc = (cx, cy, r, c) => {
      for (let y = -r; y <= r; y++) for (let x = -r; x <= r; x++) if (x * x + y * y <= r * r + r) px(cx + x, cy + y, c);
    };
    const sprite = (rows, x, y, colors) => rows.forEach((l, r) => [...l].forEach((ch, c) => { if (colors[ch]) px(x + c, y + r, colors[ch]); }));
    // A character. o.upTo cuts them off at a row (sitting behind something), o.hands: "LR" kept,
    // o.back draws them from behind, o.cap recolours the top rows (hats, helmets, headbands).
    const person = (p, x, y, f, o = {}) => {
      const rows = o.rows || p.rows;
      const blink = f === p.blinkAt || f === p.blinkAt + 1;
      const hands = o.hands ?? "LR";
      rows.forEach((l, r) => {
        if (o.upTo != null && r >= o.upTo) return;
        for (let c = 0; c < 12; c++) {
          let ch = l[c];
          if (ch === ".") continue;
          if ((r === p.hand || r === p.hand + 1) && ((c === 0 && !hands.includes("L")) || (c === 11 && !hands.includes("R")))) continue;
          let color = PALETTE[ch];
          if (o.back && r < 10) color = (r === 5 && (c === 1 || c === 10)) ? PALETTE.s : PALETTE[p.hair];
          else if (blink && r === 4 && ch === "E") color = PALETTE.s;
          if (o.cap && o.cap.rows.includes(r)) color = o.cap.color;
          if (o.map) color = o.map(ch, r, c, color);
          if (color) px(x + c, y + r, color);
        }
      });
    };
    const hand = (x, y) => rect(x, y, 1, 2, SKIN);
    return { ctx, rect, px, line, disc, sprite, person, hand };
  }

  function rng(seed) {
    return () => { seed = (seed + 0x6D2B79F5) | 0; let t = Math.imul(seed ^ (seed >>> 15), 1 | seed); t ^= t + Math.imul(t ^ (t >>> 7), 61 | t); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
  }
  const ridge = (g, color, base, amp, freq, phase) => {
    for (let x = 0; x < W; x++) {
      const top = Math.round(base + amp * Math.sin(x * freq + phase) + amp * 0.5 * Math.sin(x * freq * 2.3 + phase * 1.7));
      g.rect(x, top, 1, H - top, color);
    }
  };
  const stars = (g, rnd, n, maxY) => { for (let i = 0; i < n; i++) g.px(Math.floor(rnd() * W), Math.floor(rnd() * maxY), `rgba(255,255,255,${0.25 + rnd() * 0.4})`); };
  const cloud = (g, x, y, w) => { g.rect(x, y, w, 2, "rgba(255,255,255,0.18)"); g.rect(x + 2, y - 1, w - 5, 1, "rgba(255,255,255,0.14)"); g.rect(x + 3, y - 2, w - 9, 1, "rgba(255,255,255,0.1)"); };
  // Indoors the wall is a dark glaze over the app's colour.
  const wall = (g, alpha) => g.rect(0, 0, W, 58, `rgba(14,10,18,${alpha})`);
  const tri = (f, period) => { const t = f % period, h = period / 2; return t < h ? t : period - t; };
  const heart = (g, x, y, c) => g.sprite(["r.r", "rrr", ".r."], x, y, { r: c });

  // ---- The nine activities. Characters stay right of x = 140, clear of the app name and title. ----
  const A = {};

  A.couch = (g, f) => {
    wall(g, 0.6);
    for (let x = 0; x < W; x += 6) g.rect(x, 0, 1, 57, "rgba(255,255,255,0.025)");
    g.ctx.clearRect(60, 10, 34, 26);
    g.rect(59, 9, 36, 1, "#2a2030"); g.rect(59, 36, 36, 1, "#2a2030"); g.rect(59, 9, 1, 28, "#2a2030"); g.rect(94, 9, 1, 28, "#2a2030");
    g.rect(76, 10, 1, 26, "#2a2030"); g.rect(60, 22, 34, 1, "#2a2030"); g.rect(57, 37, 40, 2, "#3a2e40");
    g.px(66, 14, "rgba(255,255,255,0.7)"); g.px(86, 17, "rgba(255,255,255,0.5)"); g.px(81, 28, "rgba(255,255,255,0.4)");
    // Floor, rug and skirting.
    g.rect(0, 58, W, 17, "#3a2a20");
    for (const y of [62, 67, 72]) g.rect(0, y, W, 1, "#30231a");
    g.rect(0, 57, W, 1, "#4e3e34");
    g.rect(150, 61, 78, 6, "#6a3a3a"); g.rect(152, 62, 74, 4, "#7c4646");
    // A framed picture, a floor lamp and a plant.
    g.rect(176, 13, 26, 15, "#8a6a3a"); g.rect(178, 15, 22, 11, "#2c4a6a");
    g.line(178, 25, 186, 18, "#d8e0e8"); g.line(186, 18, 192, 25, "#d8e0e8"); g.rect(179, 24, 20, 2, "#3e6a3a");
    g.rect(229, 24, 1, 34, "#5a5a60"); g.rect(226, 58, 7, 1, "#3a3a40");
    g.rect(225, 18, 9, 2, "#e8c98a"); g.rect(224, 20, 11, 4, "#d8b070");
    g.rect(210, 14, 30, 30, "rgba(255,220,150,0.05)");
    g.rect(140, 50, 7, 8, "#8a5a3a"); g.rect(139, 50, 9, 1, "#a06a42");
    for (const [x, y] of [[141, 46], [143, 44], [145, 46], [142, 48], [144, 47], [146, 45], [140, 47], [143, 42]]) g.px(x, y, "#4e8a46");
    // Couch back, the two of them, then the cushions in front.
    g.rect(158, 41, 60, 13, "#2f4a62"); g.rect(158, 41, 60, 1, "#3e6080"); g.rect(187, 42, 1, 11, "#27405a");
    g.person(DARK, 166, 38, f, { upTo: DARK.waist, hands: "L" });
    const reach = f >= 6 && f <= 15;
    g.person(GINGER, 196, 39, f, { upTo: GINGER.waist, hands: reach ? "R" : "LR", map: (ch, r, c, col) => (r === 8 && (c === 5 || c === 6) && (f === 13 || f === 15)) ? PALETTE.s : col });
    // Dark-hair holds the remote.
    g.hand(177, 52); g.rect(178, 51, 1, 3, "#1a1a1e"); if (f % 8 < 2) g.px(178, 50, "#ff4040");
    // The popcorn bowl, and Ginger dipping into it.
    g.rect(181, 50, 10, 4, "#c23a3a"); for (let x = 182; x < 191; x += 3) g.rect(x, 50, 1, 4, "#f0e8e0");
    for (const [x, y, c] of [[182, 49, "#fff4c8"], [184, 48, "#f2d27a"], [186, 49, "#fff4c8"], [188, 48, "#fff4c8"], [189, 49, "#f2d27a"], [185, 49, "#fff4c8"]]) g.px(x, y, c);
    if (f >= 6 && f <= 8) g.hand(193, 51);
    else if (f >= 9 && f <= 10) { g.hand(190, 48); g.px(189, 48, "#fff4c8"); }
    else if (f >= 11 && f <= 15) { g.hand(199, 48); if (f < 13) g.px(200, 47, "#fff4c8"); }
    g.rect(154, 44, 7, 15, "#2a4258"); g.rect(154, 44, 7, 1, "#3e6080");
    g.rect(215, 44, 7, 15, "#2a4258"); g.rect(215, 44, 7, 1, "#3e6080");
    g.rect(160, 54, 56, 5, "#3e6080"); g.rect(188, 54, 1, 5, "#2f4a62"); g.rect(158, 59, 60, 2, "#2a4258");
    g.rect(158, 61, 2, 2, "#1a1a1a"); g.rect(220, 61, 2, 2, "#1a1a1a");
    // The TV they're watching is off to the side; its light flickers over them.
    g.rect(150, 30, 80, 30, ["rgba(106,168,255,0.07)", "rgba(255,255,255,0.05)", "rgba(255,154,106,0.07)", "rgba(154,255,154,0.05)"][Math.floor(f / 6)]);
  };

  A.eating = (g, f) => {
    const rnd = rng(11);
    stars(g, rnd, 18, 16);
    g.disc(34, 40, 9, "#ffd27a"); g.rect(22, 37, 26, 1, "rgba(255,180,90,0.5)");
    cloud(g, 150, 12, 20); cloud(g, 196, 7, 14);
    ridge(g, "#2d3b2a", 44, 3, 0.04, 1);
    ridge(g, "#24321f", 50, 2, 0.07, 2.5);
    g.rect(0, 58, W, 17, "#2c4a26");
    for (let i = 0; i < 70; i++) g.px(Math.floor(rnd() * W), 58 + Math.floor(rnd() * 17), "#3d6332");
    // A tree to sit under, with fireflies.
    g.rect(226, 28, 4, 30, "#4a3220"); g.disc(228, 22, 11, "#2f5a2c"); g.disc(224, 18, 5, "#3a6a34"); g.disc(233, 24, 4, "#3a6a34");
    for (const [x, y, on] of [[214, 30, 0], [236, 36, 8], [220, 40, 16]]) if ((f - on + 24) % 24 < 8) g.px(x, y, "#e8f07a");
    g.person(DARK, 164, 38, f, { upTo: DARK.waist, hands: f < 12 ? "L" : "LR", map: (ch, r, c, col) => (r === 8 && (c === 5 || c === 6) && (f === 6 || f === 8)) ? PALETTE.s : col });
    g.person(GINGER, 196, 39, f, { upTo: GINGER.waist, hands: f >= 12 ? "R" : "LR", map: (ch, r, c, col) => (r === 8 && (c === 5 || c === 6) && (f === 18 || f === 20)) ? PALETTE.s : col });
    // Picnic table with a checked cloth.
    g.rect(156, 54, 60, 2, "#8a5a32");
    for (let x = 156; x < 216; x += 2) g.rect(x, 54, 1, 4, ((x / 2) % 2) ? "#c84040" : "#f0e8e0");
    g.rect(160, 58, 2, 5, "#6a4224"); g.rect(210, 58, 2, 5, "#6a4224");
    // Pizza on a board, a cup and a can.
    g.rect(181, 53, 12, 1, "#a07040"); g.rect(182, 52, 10, 1, "#e8b04a"); g.px(181, 52, "#c8843a"); g.px(192, 52, "#c8843a");
    for (const x of [184, 187, 190]) g.px(x, 52, "#c0392b");
    g.rect(159, 51, 3, 3, "#e0e0e0"); g.rect(212, 50, 2, 4, "#c0392b"); g.px(212, 50, "#d8d8d8");
    // Each takes a slice in turn: hand to the pizza, up to the mouth, chew, back down.
    const bite = (t, from, mouthX, mouthY) => {
      if (t < 3) g.hand(from, 52);
      else if (t < 5) { g.hand(Math.round((from + mouthX) / 2), 49); g.px(Math.round((from + mouthX) / 2) - 1, 49, "#e8b04a"); }
      else if (t < 10) { g.hand(mouthX, mouthY); if (t < 7) g.px(mouthX - Math.sign(mouthX - from) , mouthY, "#e8b04a"); }
      else g.hand(from, 51);
    };
    if (f < 12) bite(f, 180, 172, 46); else bite(f - 12, 194, 199, 47);
  };

  A.reading = (g, f) => {
    const rnd = rng(23);
    wall(g, 0.62);
    // An arched window between tall bookshelves.
    g.ctx.clearRect(178, 10, 26, 22); g.ctx.clearRect(180, 8, 22, 2); g.ctx.clearRect(183, 7, 16, 1);
    g.rect(190, 7, 1, 25, "#3a2a20"); g.rect(178, 20, 26, 1, "#3a2a20"); g.rect(176, 32, 30, 2, "#4a3424");
    g.px(184, 12, "rgba(255,255,255,0.6)"); g.px(198, 16, "rgba(255,255,255,0.5)");
    const shelf = (x0, w) => {
      g.rect(x0, 6, w, 52, "#3a2618"); g.rect(x0, 6, w, 1, "#5a3a26");
      for (let y = 15; y <= 55; y += 10) {
        g.rect(x0, y, w, 1, "#5a3a26");
        for (let x = x0 + 1; x < x0 + w - 1;) {
          const bw = 1 + Math.floor(rnd() * 2), bh = 6 + Math.floor(rnd() * 3);
          g.rect(x, y - bh, bw, bh, ["#8a3a3a", "#3a5a8a", "#4a7a4a", "#c8a040", "#6a4a8a", "#2a6a6a", "#a05a3a"][Math.floor(rnd() * 7)]);
          x += bw + (rnd() < 0.15 ? 1 : 0);
        }
      }
    };
    shelf(0, 54); shelf(144, 16); shelf(222, 18);
    g.rect(0, 58, W, 17, "#2a1e18");
    for (const y of [63, 69]) g.rect(0, y, W, 1, "#241912");
    g.rect(158, 60, 60, 5, "#5a2a2a"); g.rect(160, 61, 56, 3, "#6a3434");
    // Two armchairs and a lamp on a side table between them.
    const chairBack = (x) => { g.rect(x, 40, 22, 14, "#6a2e2e"); g.rect(x, 40, 22, 1, "#8a4040"); };
    const chairFront = (x) => {
      g.rect(x - 2, 47, 4, 11, "#5a2626"); g.rect(x + 20, 47, 4, 11, "#5a2626");
      g.rect(x - 2, 47, 4, 1, "#8a4040"); g.rect(x + 20, 47, 4, 1, "#8a4040");
      g.rect(x, 54, 22, 5, "#7a3636"); g.rect(x - 1, 59, 2, 2, "#1a1210"); g.rect(x + 21, 59, 2, 2, "#1a1210");
    };
    chairBack(161); chairBack(193);
    g.person(DARK, 166, 38, f, { upTo: DARK.waist, hands: "" });
    g.person(GINGER, 198, 39, f, { upTo: GINGER.waist, hands: "" });
    // Open books held up in both hands; each turns a page now and then.
    const book = (x, cover, flipAt) => {
      g.rect(x, 49, 7, 5, cover); g.rect(x + 1, 49, 5, 4, "#f0e8d8"); g.rect(x + 3, 49, 1, 4, "#c8bca8");
      for (let r = 50; r < 53; r++) { g.rect(x + 1, r, 2, 1, "rgba(80,70,60,0.35)"); g.rect(x + 4, r, 2, 1, "rgba(80,70,60,0.35)"); }
      const t = f - flipAt;
      if (t >= 0 && t < 4) g.rect(x + 5 - t, 48, 1, 4, "#ffffff");
      g.hand(x - 1, 50); g.hand(x + 7, 50);
    };
    book(169, "#3a5a8a", 10); book(201, "#8a3a3a", 20);
    g.rect(186, 50, 6, 1, "#4a3020"); g.rect(188, 51, 2, 7, "#4a3020"); g.rect(186, 58, 6, 1, "#4a3020");
    g.rect(188, 45, 2, 5, "#7a7a80"); g.rect(185, 41, 8, 4, "#e8c98a"); g.rect(186, 40, 6, 1, "#f0d8a0");
    g.rect(176, 34, 26, 22, `rgba(255,220,150,${f % 8 < 4 ? 0.06 : 0.075})`);
    chairFront(161); chairFront(193);
    // Dust drifting in the lamplight.
    for (const [x, y0, s] of [[182, 36, 0], [195, 40, 5], [178, 44, 11]]) g.px(x, y0 - Math.floor(((f + s) % 24) / 6), "rgba(255,240,200,0.5)");
  };

  A.programming = (g, f) => {
    wall(g, 0.66);
    // A big screen on the wall, scrolling code, with a blinking cursor.
    g.rect(150, 7, 78, 32, "#0c0c10"); g.rect(152, 9, 74, 28, "#0b1712");
    const r = rng(5);
    const lines = Array.from({ length: 8 }, () => {
      const indent = Math.floor(r() * 4) * 3, parts = [];
      let x = 0; const n = 1 + Math.floor(r() * 3);
      for (let i = 0; i < n; i++) { const w = 3 + Math.floor(r() * 9); parts.push([x, w, ["#7ee787", "#79c0ff", "#ff7b72", "#d2a8ff", "#e3b341"][Math.floor(r() * 5)]]); x += w + 2; }
      return { indent, parts };
    });
    const shift = Math.floor(f / 3);
    for (let i = 0; i < 8; i++) {
      const l = lines[(i + shift) % 8];
      for (const [x, w, c] of l.parts) g.rect(155 + l.indent + x, 11 + i * 3, Math.min(w, 66 - l.indent - x), 1, c);
    }
    if (f % 6 < 3) g.rect(155, 33, 2, 2, "#e8e8e8");
    g.rect(186, 39, 6, 2, "#1a1a20"); g.rect(183, 41, 12, 1, "#1a1a20");
    // A poster and a pinned note on the left.
    g.rect(118, 14, 14, 18, "#1c2a40"); g.disc(125, 21, 3, "#e3b341"); g.rect(120, 27, 10, 1, "#79c0ff"); g.rect(121, 29, 8, 1, "#79c0ff");
    g.rect(0, 58, W, 17, "#1e1a22");
    for (const y of [63, 70]) g.rect(0, y, W, 1, "#19151c");
    g.person(DARK, 162, 38, f, { upTo: DARK.waist, hands: "" });
    g.person(GINGER, 202, 39, f, { upTo: GINGER.waist, hands: "" });
    // Screen light on their faces.
    g.rect(164, 41, 8, 7, "rgba(120,200,255,0.10)"); g.rect(204, 42, 8, 7, "rgba(120,200,255,0.10)");
    // The desk, two laptops seen from behind, and hands busy on the keys.
    g.rect(148, 54, 84, 2, "#6a4a32"); g.rect(150, 56, 80, 4, "#4a3424"); g.rect(151, 60, 2, 3, "#3a2818"); g.rect(227, 60, 2, 3, "#3a2818");
    const laptop = (x, glow) => { g.rect(x, 48, 10, 6, "#9aa0aa"); g.rect(x, 48, 10, 1, "#b8bec8"); g.px(x + 4, 50, glow); g.px(x + 5, 50, glow); g.px(x + 4, 51, glow); g.px(x + 5, 51, glow); g.rect(x - 1, 54, 12, 1, "#7a8088"); };
    laptop(163, "#7ee787"); laptop(203, "#79c0ff");
    const typing = (x, y, t) => { g.rect(x, y + (t % 2), 1, 1, SKIN); g.rect(x, y + 1 - (t % 2) + 1, 1, 0, SKIN); g.px(x, y + (t % 2), SKIN); g.px(x + 13, y + ((t + 1) % 2), SKIN); };
    typing(161, 52, f); typing(201, 52, f + 1);
    // A rubber duck for debugging, and coffee steaming.
    g.rect(154, 52, 3, 2, "#f2d23a"); g.px(155, 51, "#f2d23a"); g.px(154, 51, "#f2d23a"); g.px(153, 52, "#e8862a"); g.px(154, 51, "#1a1a1a");
    g.rect(188, 51, 3, 3, "#d8d8d8"); g.px(191, 52, "#d8d8d8"); g.rect(188, 51, 3, 1, "#5a3a20");
    for (const s of [0, 3]) { const t = (f + s * 4) % 12; if (t < 8) g.px(189 + (t % 4 < 2 ? 0 : 1), 50 - Math.floor(t / 2), `rgba(255,255,255,${0.45 - t * 0.05})`); }
    // A desk lamp on the right.
    g.rect(222, 53, 6, 1, "#3a3a40"); g.line(224, 52, 222, 46, "#3a3a40"); g.line(222, 46, 227, 43, "#3a3a40"); g.rect(226, 43, 4, 2, "#e3b341");
  };

  A.cat = (g, f) => {
    const rnd = rng(31);
    cloud(g, 150, 12, 22); cloud(g, 40, 8, 18); cloud(g, 100, 16, 12);
    // Fence, a house with a porch, grass and flowers.
    g.rect(0, 47, 205, 1, "#6a5040"); g.rect(0, 53, 205, 1, "#6a5040");
    for (let x = 1; x < 205; x += 6) { g.rect(x, 44, 3, 14, "#7a5c46"); g.px(x + 1, 43, "#7a5c46"); g.rect(x, 44, 1, 14, "#8a6a52"); }
    g.rect(206, 18, 34, 40, "#c8b090");
    for (let y = 20; y < 58; y += 3) g.rect(206, y, 34, 1, "#b09878");
    g.rect(203, 14, 37, 4, "#7a3a2a"); g.rect(205, 12, 35, 2, "#8a4a36");
    g.rect(217, 32, 12, 26, "#6a4030"); g.rect(218, 33, 10, 24, "#7a4a38"); g.px(226, 45, "#e3b341");
    g.rect(208, 24, 7, 7, "#2a3a4a"); g.rect(211, 24, 1, 7, "#c8b090"); g.rect(208, 27, 7, 1, "#c8b090");
    g.rect(204, 56, 36, 2, "#9a8a7a");
    g.rect(0, 58, W, 17, "#3d6a32");
    for (let i = 0; i < 60; i++) g.px(Math.floor(rnd() * W), 58 + Math.floor(rnd() * 17), "#4e7e40");
    for (let i = 0; i < 26; i++) { const x = Math.floor(rnd() * 200); g.px(x, 57, ["#e85a6a", "#f2d23a", "#f0a0d0", "#ffffff"][i % 4]); g.px(x, 58, "#2e5a28"); }
    // Dark-hair crouches to stroke a grey cat, which purrs out hearts.
    const strokeX = 171 + tri(f, 12);
    g.person(DARK, 156, 41, f, { rows: DARK.crouch, hands: "L" });
    g.rect(168, 54, strokeX - 168, 1, SKIN); g.rect(strokeX, 53, 2, 2, SKIN);
    const happy = true;
    g.rect(166, 57, 18, 1, "rgba(0,0,0,0.25)");
    const catRows = [".k.k.......", ".kkk.......", "kekek......", "pkkkkkkkk..", ".kkkkkkkkk.", "..k.k..k.k."];
    const tailUp = f % 8 < 4;
    // Draw the cat first so the stroking hand sits on its back.
    g.sprite(catRows, 170, 52, { k: "#26262e", e: happy ? "#3a3a46" : "#c8e04a", p: "#f0a0b0" });
    for (const [x, y] of [[172, 55], [175, 55], [178, 56]]) g.px(x, y, "#3a3a46");
    if (tailUp) { g.px(180, 54, "#26262e"); g.px(181, 53, "#26262e"); g.px(181, 52, "#26262e"); }
    else { g.px(180, 55, "#26262e"); g.px(181, 55, "#26262e"); g.px(182, 54, "#26262e"); }
    g.rect(strokeX, 53, 2, 2, SKIN); g.rect(168, 54, strokeX - 168, 1, SKIN);
    for (const s of [0, 12]) { const t = (f + 24 - s) % 24; if (t < 12) heart(g, 176 + (s ? 4 : 0), 49 - Math.floor(t * 0.75), `rgba(255,107,138,${1 - t / 12})`); }
    // Ginger cradles a kitten.
    g.person(GINGER, 190, 40, f);
    const kTail = f % 6 < 3;
    g.sprite([".....o.o", "o....ooo", ".ooooooe", ".ooooooo", "..o..o.."], 192, 50, { o: "#e0903a", e: "#2a1a12" });
    g.px(194, 51, "#c06a20"); g.px(196, 51, "#c06a20");
    g.px(192, kTail ? 49 : 51, "#e0903a");
  };

  A.hiking = (g, f) => {
    cloud(g, 120, 10, 18); cloud(g, 200, 6, 24);
    // Snowy peaks, a pine ridge, and the trail sliding by.
    const peak = (cx, top, half, c1, c2) => {
      for (let y = top; y < 50; y++) {
        const w = Math.round((y - top) * half / (50 - top));
        g.rect(cx - w, y, w, 1, c1); g.rect(cx, y, w + 1, 1, c2);
        if (y - top < 5) { g.rect(cx - w, y, w * 2 + 1, 1, "#e8eef4"); }
      }
    };
    peak(40, 12, 44, "#3a4a5e", "#2e3b4c"); peak(110, 20, 40, "#34445a", "#2a3848"); peak(180, 8, 52, "#3e4e64", "#303e50"); peak(236, 18, 36, "#34445a", "#2a3848");
    const rnd = rng(41);
    ridge(g, "#1f3a2c", 47, 2, 0.08, 0.3);
    for (let i = 0; i < 26; i++) { const x = Math.floor(rnd() * W), h = 5 + Math.floor(rnd() * 6); for (let y = 0; y < h; y++) g.rect(x - Math.floor(y / 2.2), 47 - h + y, Math.floor(y / 2.2) * 2 + 1, 1, "#1a3326"); }
    g.rect(0, 56, W, 19, "#5a4632"); g.rect(0, 56, W, 1, "#3d5a30");
    for (let tile = 0; tile < W + 24; tile += 24) {
      const sx = (o) => ((tile + o - f) % W + W) % W;
      g.rect(sx(3), 60, 2, 1, "#7a6650"); g.rect(sx(15), 66, 3, 1, "#7a6650"); g.px(sx(9), 63, "#4a3828");
      g.px(sx(9), 55, "#4e7a3e"); g.px(sx(20), 55, "#4e7a3e"); g.px(sx(21), 54, "#4e7a3e");
    }
    const step = Math.floor(f / 3) % 2;
    const walker = (p, x, y, pack, cap) => {
      g.rect(x + 1, y + 9, 10, 3, pack);
      g.person(p, x, y, f, { rows: step ? p.walkB : p.walkA, cap });
      g.rect(x + 3, y + 11, 1, 4, "#3a2418"); g.rect(x + 8, y + 11, 1, 4, "#3a2418");
    };
    walker(DARK, 165, 38, "#b05a2a", { rows: [0, 1], color: "#c8b080" });
    g.rect(165, 40, 12, 1, "#b8a070");
    walker(GINGER, 192, 40, "#2a6a8a");
    // Ginger's walking pole swings with the step.
    g.line(204, 53, 205 + step, 58, "#8a6a4a");
    // Birds flapping high up.
    for (const [x, y, s] of [[150, 14, 0], [158, 18, 2]]) {
      if ((f + s) % 4 < 2) { g.px(x, y, "#1a1a20"); g.px(x + 1, y + 1, "#1a1a20"); g.px(x + 2, y, "#1a1a20"); }
      else { g.px(x, y + 1, "#1a1a20"); g.px(x + 1, y, "#1a1a20"); g.px(x + 2, y + 1, "#1a1a20"); }
    }
  };

  A.running = (g, f) => {
    const rnd = rng(53);
    stars(g, rnd, 12, 12);
    // A city skyline at dawn, a line of trees, then the running track.
    for (let x = 0; x < W;) {
      const bw = 8 + Math.floor(rnd() * 10), bh = 10 + Math.floor(rnd() * 22);
      g.rect(x, 50 - bh, bw, bh, rnd() < 0.5 ? "#1c2230" : "#222a3a");
      for (let wy = 50 - bh + 2; wy < 48; wy += 3) for (let wx = x + 2; wx < x + bw - 1; wx += 2) if (rnd() < 0.25) g.px(wx, wy, "rgba(242,212,138,0.8)");
      x += bw;
    }
    for (let x = 0; x < W; x += 9) g.disc(x + 4, 48, 4, "#1f3a26");
    g.rect(0, 51, W, 3, "#3d6a32");
    g.rect(0, 54, W, 21, "#a04a3a");
    for (const y of [54, 62, 70]) g.rect(0, y, W, 1, "#e8e0d8");
    for (let x = -12; x < W + 12; x += 12) { const sx = ((x - 2 * f) % W + W) % W; g.rect(sx, 66, 6, 1, "rgba(232,224,216,0.5)"); }
    for (let x = 0; x < W; x += 24) { const sx = ((x - 2 * f) % W + W) % W; g.px(sx, 58, "#8a3e30"); g.px((sx + 11) % W, 73, "#8a3e30"); }
    // Lamp posts along the track.
    for (const x of [148, 228]) { g.rect(x, 30, 1, 21, "#3a3a44"); g.rect(x - 1, 29, 3, 1, "#3a3a44"); g.px(x, 31, "#ffe9a8"); }
    const run = (p, x, y, t, band) => {
      const stride = Math.floor(t / 2) % 2;
      const dy = stride ? 0 : -1;
      g.person(p, x, y + dy, f, { rows: stride ? p.walkB : p.walkA, hands: "", cap: { rows: [2], color: band } });
      const hy = y + dy + p.hand;
      g.hand(x, hy + (stride ? -2 : 1)); g.hand(x + 11, hy + (stride ? 1 : -2));
    };
    run(DARK, 168, 41, f, "#d04040");
    run(GINGER, 194, 43, f + 2, "#f0f0f0");
    // Speed lines and a drop of sweat.
    for (const [y, o] of [[46, 0], [51, 2], [56, 1]]) if ((f + o) % 3) g.rect(154 + (f + o) % 4, y, 8, 1, "rgba(255,255,255,0.3)");
    if (f % 12 < 6) g.px(206, 45 + f % 6, "#9ad8ff");
  };

  A.swimming = (g, f) => {
    g.disc(70, 16, 8, "#ffe9a8"); g.disc(70, 16, 11, "rgba(255,233,168,0.15)");
    cloud(g, 150, 10, 20); cloud(g, 210, 16, 16); cloud(g, 20, 8, 14);
    g.rect(0, 46, W, 29, "#1f5a8a"); g.rect(0, 46, W, 1, "#3a7ab0");
    g.rect(0, 60, W, 8, "#1a4e7a"); g.rect(0, 68, W, 7, "#164266");
    // A beach with a palm on the left.
    for (let y = 48; y < H; y++) { const w = Math.round(90 + (y - 48) * 1.2); g.rect(0, y, w, 1, "#e3cc94"); g.rect(w, y, 2, 1, "#c8b07a"); }
    g.line(38, 50, 41, 34, "#8a6a3a"); g.line(39, 50, 42, 34, "#7a5a2e"); g.line(41, 34, 43, 30, "#8a6a3a");
    for (const [dx, dy] of [[-8, 3], [8, 4], [-6, -3], [7, -2], [0, -5]]) g.line(43, 30, 43 + dx, 30 + dy, "#3d7a3a");
    g.px(42, 31, "#5a3a20"); g.px(44, 31, "#5a3a20");
    // Ripples drifting each way.
    for (const [y, dir, o] of [[49, 1, 0], [53, -1, 7], [57, 1, 13], [63, -1, 4], [71, 1, 18]]) {
      for (let x = 0; x < W + 24; x += 24) { const sx = ((x + o + dir * f) % W + W) % W; if (sx > 96) g.rect(sx, y, 4, 1, "rgba(160,210,250,0.45)"); }
    }
    // Swimmers: head and goggles above the water, arms wheeling over in turn.
    const swim = (p, x, t, goggle) => {
      const bob = Math.floor(t / 6) % 2;
      const y = 40 + bob;
      g.person(p, x, y, f, { upTo: 10, map: (ch, r, c, col) => (r === 4 && ((c >= 2 && c <= 4) || (c >= 7 && c <= 9))) ? goggle : col });
      g.rect(x + 1, 50, 10, 1, "rgba(220,240,255,0.6)");
      const phase = t % 24;
      const arm = (side) => {
        const ax = side > 0 ? x + 11 : x;
        const k = phase % 12;
        if (k < 6) { const lift = [1, 3, 5, 6, 5, 3][k]; g.line(ax, 49, ax + side * (1 + Math.floor(k / 2)), 49 - lift, SKIN); }
        else if (k < 8) { g.px(ax + side * 3, 49, "#ffffff"); g.px(ax + side * 4, 48, "rgba(255,255,255,0.7)"); g.px(ax + side * 2, 47, "rgba(255,255,255,0.6)"); }
      };
      arm(phase < 12 ? 1 : -1);
    };
    swim(DARK, 160, f, "#3ab0e0");
    swim(GINGER, 196, f + 6, "#f2d23a");
    // A beach ball bobbing, and a gull.
    const by = 45 + (Math.floor(f / 8) % 2);
    g.disc(226, by, 2, "#f0f0f0"); g.rect(224, by, 5, 1, "#e04040"); g.px(226, by - 2, "#3a7ad8"); g.px(226, by + 2, "#3a7ad8");
    if (f % 4 < 2) { g.px(140, 12, "#f0f0f0"); g.px(141, 13, "#f0f0f0"); g.px(142, 12, "#f0f0f0"); }
    else { g.px(140, 13, "#f0f0f0"); g.px(141, 12, "#f0f0f0"); g.px(142, 13, "#f0f0f0"); }
  };

  A.climbing = (g, f) => {
    const rnd = rng(67);
    cloud(g, 40, 12, 20); cloud(g, 120, 6, 16);
    ridge(g, "#3a3048", 38, 5, 0.03, 1.2);
    ridge(g, "#2e2638", 46, 3, 0.06, 0.4);
    g.rect(0, 58, W, 17, "#4a4038");
    for (let i = 0; i < 40; i++) g.px(Math.floor(rnd() * W), 58 + Math.floor(rnd() * 17), "#5e5248");
    // The cliff face, with cracks and ledges.
    for (let y = 4; y < 58; y++) { const x0 = 170 - Math.round(4 * Math.sin(y * 0.2)) - (y > 40 ? Math.floor((y - 40) / 2) : 0); g.rect(x0, y, W - x0, 1, "#6a5a50"); g.px(x0, y, "#86766a"); }
    for (let i = 0; i < 24; i++) { const x = 176 + Math.floor(rnd() * 62), y = 6 + Math.floor(rnd() * 50); g.rect(x, y, 2 + Math.floor(rnd() * 3), 1, rnd() < 0.5 ? "#4e4038" : "#86766a"); }
    for (const [x, y] of [[178, 22], [214, 30], [188, 44], [226, 16]]) { g.rect(x, y, 6, 1, "#86766a"); g.rect(x, y + 1, 6, 1, "#4e4038"); }
    for (const [x, y] of [[198, 12], [206, 26], [194, 36], [209, 40]]) g.px(x, y, "rgba(255,255,255,0.5)");
    g.rect(199, 3, 4, 2, "#a0a0a8");
    // Dark-hair climbs, seen from behind, reaching up with one hand then the other.
    const side = f < 12 ? -1 : 1;
    const cy = 16 + (f < 12 ? 0 : 1), cx = 196;
    g.line(201, 5, 202, cy + 15, "#e0c040");
    g.person(DARK, cx, cy, f, { back: true, hands: side < 0 ? "R" : "L", rows: f % 12 < 6 ? DARK.walkA : DARK.walkB, cap: { rows: [0, 1], color: "#e04040" } });
    const ax = side < 0 ? cx : cx + 11;
    g.rect(ax, cy + 7, 1, 5, PALETTE.T); g.rect(ax + side, cy + 4, 1, 3, SKIN);
    g.px(cx + 10, cy + 16, "#f0f0f0");
    // Ginger belays from the ground, hands working the rope.
    const by = 40;
    g.line(201, 5, 189, by + 13, "#e0c040");
    g.person(GINGER, 178, by, f, { hands: "L", cap: { rows: [0, 1], color: "#f2d23a" } });
    const pull = Math.floor(f / 6) % 2;
    g.hand(189, by + 12 + pull);
    // A pebble skitters down.
    if (f >= 4 && f < 12) g.px(206, cy + 20 + (f - 4) * 3, "#8a7a6a");
  };

  // Shir chases the other two round and round a big tree. 48 frames make one lap.
  A.chase = (g, f) => {
    const rnd = rng(77);
    cloud(g, 40, 10, 18); cloud(g, 120, 14, 14); cloud(g, 210, 6, 20);
    ridge(g, "#2f5a2c", 46, 2, 0.05, 0.8);
    for (let x = 0; x < W; x += 11) g.disc(x + 5, 47, 5, "#264a24");
    g.rect(0, 52, W, 23, "#3d6a32");
    for (let i = 0; i < 70; i++) g.px(Math.floor(rnd() * W), 52 + Math.floor(rnd() * 23), "#4e7e40");
    for (let i = 0; i < 18; i++) { const x = Math.floor(rnd() * W); g.px(x, 53 + Math.floor(rnd() * 20), ["#f2d23a", "#ffffff", "#e85a6a"][i % 3]); }
    // A trodden ring of earth round the tree.
    const cx = 188, rx = 36;
    for (let a = 0; a < 360; a += 2) { const t = a * Math.PI / 180; g.rect(Math.round(cx + rx * Math.cos(t)) - 1, Math.round(58 + 3 * Math.sin(t)), 4, 1, "#5a4a32"); }
    const tree = () => {
      g.rect(cx - 3, 26, 6, 31, "#4a3220"); g.rect(cx - 3, 26, 2, 31, "#5a4028"); g.rect(cx - 5, 55, 10, 2, "#4a3220");
      g.disc(cx, 20, 15, "#2f5a2c"); g.disc(cx - 8, 15, 8, "#3a6a34"); g.disc(cx + 9, 18, 7, "#3a6a34"); g.disc(cx + 2, 10, 7, "#3f7438");
      for (const [x, y] of [[cx - 6, 22], [cx + 5, 12], [cx + 10, 24], [cx - 11, 14]]) g.px(x, y, "#e85a6a");
    };
    const lap = (f / 48) * Math.PI * 2;
    const runners = [
      { p: DARK, a: lap, chased: true },
      { p: GINGER, a: lap - 0.55, chased: true },
      { p: SHIR, a: lap - 1.45, chased: false },
    ].map(r => ({ ...r, depth: Math.sin(r.a) }));
    const draw = (r) => {
      const x = Math.round(cx + rx * Math.cos(r.a)) - 6;
      const feet = Math.round(58 + 3 * Math.sin(r.a));
      const stride = Math.floor((f + (r.chased ? 0 : 1)) / 2) % 2;
      const y = feet - r.p.rows.length - (stride ? 0 : 1);
      // Dust kicked up behind.
      const dir = -Math.sin(r.a) >= 0 ? 1 : -1;
      g.px(x + 6 - dir * 7, feet - 1, "rgba(200,180,140,0.6)"); if (stride) g.px(x + 6 - dir * 9, feet - 2, "rgba(200,180,140,0.35)");
      g.person(r.p, x, y, f % 24, { rows: stride ? r.p.walkB : r.p.walkA, hands: "" });
      const hy = y + r.p.hand;
      if (r.chased) {
        // Hands up in a panic.
        g.hand(x, hy - 4 + stride); g.hand(x + 11, hy - 3 - stride);
      } else {
        // Shir reaches out after them.
        g.hand(x + 6 + dir * 6, hy - 1); g.hand(x + 6 + dir * 7, hy - 2);
      }
      return { x, y };
    };
    const behind = runners.filter(r => r.depth < 0).sort((a, b) => a.depth - b.depth);
    const front = runners.filter(r => r.depth >= 0).sort((a, b) => a.depth - b.depth);
    const at = new Map();
    behind.forEach(r => at.set(r.p, draw(r)));
    tree();
    front.forEach(r => at.set(r.p, draw(r)));
    // Ginger keeps glancing back: a little "!" pops up now and then.
    const gp = at.get(GINGER);
    if (f % 16 < 6) { g.rect(gp.x + 5, gp.y - 7, 2, 4, "#ffffff"); g.rect(gp.x + 5, gp.y - 2, 2, 1, "#ffffff"); }
  };

  const LIST = [
    { id: "couch", name: "On the couch" },
    { id: "eating", name: "Picnic" },
    { id: "reading", name: "Reading" },
    { id: "programming", name: "Programming" },
    { id: "cat", name: "Petting a cat" },
    { id: "chase", name: "Chase", frames: 48 },
    { id: "hiking", name: "Hiking" },
    { id: "running", name: "Running" },
    { id: "swimming", name: "Swimming" },
    { id: "climbing", name: "Climbing" },
  ];

  // The see-through parts get eight bands of the app's own colour, darkest at the top.
  const BAND_V = [0.10, 0.13, 0.17, 0.21, 0.26, 0.31, 0.37, 0.43];
  function skyBands(hue) {
    const s = hue == null ? 0.12 : 0.6, h = hue ?? 0;
    return BAND_V.map(v => {
      const f = (n) => { const k = (n + h / 60) % 6; return v - v * s * Math.max(0, Math.min(k, 4 - k, 1)); };
      return `rgb(${Math.round(f(5) * 255)},${Math.round(f(3) * 255)},${Math.round(f(1) * 255)})`;
    });
  }
  function drawSky(ctx, hue) {
    skyBands(hue).forEach((c, i) => { ctx.fillStyle = c; ctx.fillRect(0, i * 8, W, i === 7 ? H - 56 : 8); });
  }

  const framesOf = (id) => LIST.find(a => a.id === id)?.frames ?? FRAMES;
  return { W, H, FRAMES, LIST, framesOf, paint: (ctx, id, f) => A[id](kit(ctx), f), drawSky, skyBands };
})();
if (typeof module !== "undefined") module.exports = ACTIVITIES;
