"""Source of truth for the Chocobos Reborn item, crop and block icons (32x32).

Hand-authored pixel art as code, in the Ninjacat Skies family style (see
tools/pixelkit.py for the rules). Replaces the Meshy downsample path
(tools/meshy_icons.py) and the 16 px painters (tools/paint_item_art.py,
tools/draw_boost_pad.py) for every texture listed in ITEMS / BLOCKS.

    python tools/pixel_items.py            # write every texture
    python tools/pixel_items.py gp saddle  # only names containing these

The bird (entity atlases, mesh, eyes) is not drawn here and never will be.
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

from PIL import Image

from pixelkit import (INK, Canvas, bands, bevel, edge, ellipse, flat, grow, hexc, ink_of, line, mix,
					  paint, pillow, pmap, poly, ramp, rect, shift, shrink, sphere)

ROOT = Path(__file__).resolve().parents[1]
TEX = ROOT / "src/main/resources/assets/chocobosreborn/textures"

# ---------------------------------------------------------------- family palette
# Muted jewel tones on dark ink: copper, gold, bone, teal (tribal-power lattice.py values).
COPPER = ramp("#c08a4e")
GOLD = ramp("#d2a54a", sat_cap=0.66)
BONE = ramp("#d8cfae", sat_cap=0.3)
TEAL = ramp("#3f8f86")
NAVY = ramp("#243548", sat_cap=0.5)
LEATHER = ramp("#7a4a2c")
LEATHER_RED = ramp("#8a3e2c")
TWINE = ramp("#b39a64", sat_cap=0.45)
STEM = ramp("#6e8a4a")


def leaf(a, b, w, serr=0.0, lobes=0, shape=0.8, n=14, tip_round=False):
	"""Lens-shaped leaf mask from base a to tip b, half-width w.

	serr: sawtooth depth on alternate samples; lobes: number of wavy bumps."""
	ax, ay = a
	bx, by = b
	L = math.hypot(bx - ax, by - ay) or 1
	ux, uy = (bx - ax) / L, (by - ay) / L
	px, py = -uy, ux
	left, right = [], []
	for i in range(n + 1):
		t = i / n
		prof = w * (math.sin(math.pi * min(1, t * (1.0 if not tip_round else 0.8))) ** shape)
		if tip_round and t > 0.8:
			prof = w * math.sqrt(max(0, 1 - ((t - 0.8) / 0.2) ** 2)) * math.sin(math.pi * 0.64) ** shape
		if lobes:
			prof *= 0.78 + 0.22 * abs(math.sin(math.pi * lobes * t))
		if serr and 0 < i < n and i % 2:
			prof += serr
		cx, cy = ax + ux * L * t, ay + uy * L * t
		left.append((cx + px * prof, cy + py * prof))
		right.append((cx - px * prof, cy - py * prof))
	return poly(left + right[::-1])


def rib(cv, a, b, c, frac=0.75):
	ax, ay = a
	bx, by = b
	cv.fill(line([a, (ax + (bx - ax) * frac, ay + (by - ay) * frac)]), c)


def leaf_shade(r, a, b):
	"""Classic pixel leaf: the half facing the top-left light one tone up, a shadow
	edge on the far half, a lit edge on the near half toward the tip."""
	ax, ay = a
	bx, by = b
	L = math.hypot(bx - ax, by - ay) or 1
	ux, uy = (bx - ax) / L, (by - ay) / L
	nx, ny = -uy, ux
	lit_sign = 1 if (nx * -0.6 + ny * -0.8) > 0 else -1

	def run(cv, m):
		border = edge(m)
		for x, y in m:
			s = ((x + 0.5 - ax) * nx + (y + 0.5 - ay) * ny) * lit_sign
			t = ((x + 0.5 - ax) * ux + (y + 0.5 - ay) * uy) / L
			if s > 0.5:
				c = r[4] if (x, y) in border and 0.25 < t < 0.8 else r[3]
			else:
				c = r[1] if (x, y) in border else r[2]
			cv.put(x, y, c)
	return run


def draw_leaf(cv, a, b, w, r, ribc=1, sep=True, frac=0.75, **kw):
	m = cv.part(leaf(a, b, w, **kw), leaf_shade(r, a, b), sep=sep)
	if ribc is not None:
		cv.fill(line([a, (a[0] + (b[0] - a[0]) * frac, a[1] + (b[1] - a[1]) * frac)]) & m, r[ribc])
	return m


# ---------------------------------------------------------------- nuts

def nut_carob():
	"""A carob pod: long, curved, bead-bumped."""
	cv, r = Canvas(), ramp("#7a4a2c")
	spine = [(4, 27), (8, 20), (13, 14), (19, 9), (26, 5)]
	body = line(spine, width=8)
	for x, y in ((8, 20), (13, 14), (19, 9)):
		body |= ellipse((x - 4, y - 5, x + 3, y + 2))
	cv.part(body, pillow(r, depth=3))
	for x, y in ((8, 20), (13, 14), (19, 9), (24, 6)):           # seed bumps along the pod
		cv.fill(ellipse((x - 2, y - 3, x + 1, y)) & body, r[3])
		cv.put(x - 1, y - 2, r[4])
	cv.fill(line([(6, 28), (10, 23), (15, 18), (21, 13), (27, 9)]) & body, r[0])  # seam on the shadow side
	cv.part(line([(27, 5), (30, 2)], width=2), flat(STEM[1]))
	return cv


def nut_lasan():
	cv, r = Canvas(), ramp("#7f9a74", sat_cap=0.35)
	cv.part(ellipse((6, 7, 25, 27)), sphere(r))
	cv.fill(line([(16, 8), (15, 13), (16, 19), (15, 26)]), r[1])
	cv.put(14, 12, r[3]); cv.put(14, 18, r[3])
	cv.part(pmap(["##", "##"], 15, 4), flat(r[0]))
	return cv


def acorn(cv, body_r, cap_r, body_box, cap_box):
	cv.part(ellipse(body_box), sphere(body_r))
	x0, y0, x1, y1 = body_box
	cv.fill(pmap(["#"], (x0 + x1) // 2, y1 - 1), body_r[0])
	cap = ellipse(cap_box)
	cv.part(cap, sphere(cap_r, spec=False))
	cx0, cy0, cx1, cy1 = cap_box
	for y in range(cy0 + 2, cy1, 3):                            # scale rows on the cup
		for x in range(cx0 + 2 + (y // 3) % 2 * 2, cx1 - 1, 4):
			if (x, y) in cap and (x + 1, y) in cap:
				cv.put(x, y, cap_r[0]); cv.put(x + 1, y - 1, cap_r[3])
	cv.part(rect((cx0 + cx1) // 2, cy0 - 3, (cx0 + cx1) // 2 + 1, cy0), flat(cap_r[1]))


def nut_luchile():
	cv = Canvas()
	acorn(cv, ramp("#9a9444", sat_cap=0.55), ramp("#6a5a34"), (9, 12, 23, 29), (7, 7, 25, 16))
	return cv


def nut_saraha():
	cv, r = Canvas(), ramp("#a4462f")
	body = ellipse((5, 8, 26, 29)) | poly([(9, 12), (16, 3), (23, 12)])
	cv.part(body, sphere(r, box=(5, 6, 26, 29)))
	cv.part(rect(7, 26, 24, 28) & body, bands(BONE, stops=[2, 1]))       # pale hilum base
	cv.part(pmap(["#", "#"], 16, 1), flat(r[0]))
	return cv


def nut_pepio():
	cv, r = Canvas(), ramp("#d09a6c", sat_cap=0.5)
	body = poly([(16, 3), (20, 8), (24, 15), (25, 21), (22, 27), (16, 29), (10, 27), (7, 21), (8, 15), (12, 8)])
	cv.part(body, pillow(r, depth=4, spec=True))
	cv.fill(line([(16, 6), (15, 12), (16, 19), (17, 26)]), r[1])
	for y in (10, 15, 20, 24):                                  # pitted almond shell
		cv.put(12 + (y % 3), y, r[1]); cv.put(20 - (y % 2), y + 1, r[1])
	return cv


def nut_porov():
	cv, r = Canvas(), ramp("#5f7389", sat_cap=0.35)
	body = ellipse((5, 6, 26, 27))
	cv.part(body, sphere(r))
	cv.fill(line([(16, 6), (16, 27)]), r[0])
	for y0 in range(9, 26, 5):                                  # walnut lobes either side of the seam
		cv.fill(line([(9, y0), (12, y0 + 2), (14, y0)]) & body, r[1])
		cv.fill(line([(18, y0 + 1), (20, y0 + 3), (23, y0 + 1)]) & body, r[1])
	return cv


def nut_pram():
	cv, r = Canvas(), ramp("#9a80b8", sat_cap=0.4)
	cv.part(ellipse((6, 9, 25, 29)), sphere(r))
	cv.fill(line([(16, 11), (14, 16), (14, 22)]), r[1])
	cv.part(line([(16, 10), (17, 5)], width=2), flat(LEATHER[1]))
	draw_leaf(cv, (18, 6), (27, 3), 3, ramp("#5e8a44"))
	return cv


def nut_zeio():
	cv, r, husk = Canvas(), ramp("#d0a03c", sat_cap=0.6), ramp("#7a8a3e")
	cv.part(ellipse((8, 11, 24, 29)), sphere(r))
	frill = poly([(5, 13), (8, 7), (11, 10), (13, 4), (16, 8), (19, 4), (21, 10), (24, 7), (27, 13), (22, 16), (16, 14), (10, 16)])
	cv.part(frill, pillow(husk, depth=2))
	cv.part(rect(15, 1, 16, 4), flat(husk[1]))
	return cv


# ---------------------------------------------------------------- greens

def tie(cv, x0, y0, x1, c=TWINE):
	cv.part(rect(x0, y0, x1, y0 + 1), bands(c, stops=[3, 1]))


def green_gysahl():
	"""The recognisable one: a pale turnip bulb under a crown of frilled leaves."""
	cv, g = Canvas(), ramp("#6f9e4a", sat_cap=0.55)
	for a, b, w in (((13, 18), (3, 8), 3.2), ((19, 18), (29, 8), 3.2), ((14, 17), (8, 2), 3.2), ((18, 17), (24, 2), 3.2)):
		draw_leaf(cv, a, b, w, g, lobes=3, shape=0.6)
	draw_leaf(cv, (16, 18), (16, 2), 3.6, ramp("#7fae50", sat_cap=0.55), lobes=3, shape=0.6)
	bulb = poly([(11, 18), (21, 18), (24, 22), (21, 27), (17, 29), (15, 29), (11, 27), (8, 22)])
	cv.part(bulb, pillow(ramp("#dcdcb0", sat_cap=0.3), depth=3, spec=True))
	cv.fill(line([(12, 19), (20, 19)]) & bulb, ramp("#a8c070")[2])   # green blush under the leaves
	cv.part(line([(16, 29), (17, 31)]), flat(BONE[1]), sep=False)
	return cv


def green_curiel():
	"""Curly teal kale: three broad crinkled leaves on pale stalks."""
	cv, g = Canvas(), ramp("#3f8f86", sat_cap=0.5)
	for a, b, w in (((13, 27), (3, 10), 5.5), ((19, 27), (29, 10), 5.5), ((16, 26), (16, 2), 6)):
		draw_leaf(cv, a, b, w, g, lobes=5, shape=0.55, serr=1.2, n=20, frac=0.85)
	cv.part(line([(14, 26), (16, 31), (18, 26)], width=2), flat(BONE[2]))
	return cv


def green_krakka():
	"""Dark sea-green, a star of narrow saw-toothed blades."""
	cv, g = Canvas(), ramp("#2f6f5e", sat_cap=0.5)
	for a, b, w in (((15, 27), (2, 15), 2.4), ((17, 27), (30, 15), 2.4), ((15, 26), (6, 3), 2.4),
					((17, 26), (26, 3), 2.4), ((16, 27), (16, 1), 2.6)):
		draw_leaf(cv, a, b, w, g, serr=1.4, n=18, frac=0.9)
	cv.part(rect(15, 26, 17, 30), bands(STEM, axis="x", stops=[3, 2, 1]))
	return cv


def heart(cx, cy, s, flip=False):
	m = ellipse((cx - s, cy - s, cx, cy)) | ellipse((cx, cy - s, cx + s, cy)) | poly(
		[(cx - s, cy - s // 2), (cx + s, cy - s // 2), (cx, cy + s)])
	return {(x, 2 * cy - y) for x, y in m} if flip else m


def leaflet(cx, cy, ang, reach=12, lobe=4.2):
	"""Heart-shaped clover leaflet pointing away from (cx, cy) at angle `ang` degrees."""
	a = math.radians(ang)
	dx, dy = math.cos(a), math.sin(a)
	px, py = -dy, dx
	m = set()
	for s in (1, -1):
		lx, ly = cx + dx * (reach - lobe) + px * lobe * 0.85 * s, cy + dy * (reach - lobe) + py * lobe * 0.85 * s
		m |= ellipse((lx - lobe, ly - lobe, lx + lobe, ly + lobe))
	m |= poly([(cx + dx * 2, cy + dy * 2), (cx + dx * (reach - lobe) + px * lobe * 1.6, cy + dy * (reach - lobe) + py * lobe * 1.6),
			   (cx + dx * (reach - lobe) - px * lobe * 1.6, cy + dy * (reach - lobe) - py * lobe * 1.6)])
	notch = line([(cx + dx * (reach - 1), cy + dy * (reach - 1)), (cx + dx * (reach + 2), cy + dy * (reach + 2))])
	return m - notch, (cx + dx * reach, cy + dy * reach)


def green_mimett():
	"""Clover: three heart leaflets with a pale chevron, on a thin stalk."""
	cv, g = Canvas(), ramp("#5e9a3e", sat_cap=0.5)
	cx, cy = 16, 15
	cv.part(line([(cx, cy), (15, 23), (12, 30)], width=2), flat(STEM[1]))
	for ang in (-150, -30, 90):
		m, tip = leaflet(cx, cy, ang)
		cv.part(m, leaf_shade(g, (cx, cy), tip))
		a = math.radians(ang)
		mid = (cx + math.cos(a) * 6, cy + math.sin(a) * 6)
		px, py = -math.sin(a) * 2.5, math.cos(a) * 2.5
		cv.fill((line([(mid[0] + px, mid[1] + py), (mid[0] + math.cos(a) * 2, mid[1] + math.sin(a) * 2),
						(mid[0] - px, mid[1] - py)])) & m, g[4])
	cv.fill(rect(15, 14, 16, 15), g[0])
	return cv


def green_pahsana():
	"""Purple basil sprig: paired pointed leaves up a dark stem."""
	cv, g = Canvas(), ramp("#7a5a9a", sat_cap=0.45)
	cv.part(line([(16, 31), (16, 6)], width=2), flat(ramp("#4e3e52")[2]))
	for y, reach in ((26, 12), (18, 10), (11, 8)):
		draw_leaf(cv, (15, y), (15 - reach, y - 5), reach * 0.24 + 0.9, g, frac=0.8)
		draw_leaf(cv, (17, y), (17 + reach, y - 5), reach * 0.24 + 0.9, g, frac=0.8)
	draw_leaf(cv, (16, 9), (16, 1), 2.4, g)
	return cv


def green_reagan():
	"""Rust-orange palmate leaf (five pointed lobes) on a woody stalk."""
	cv, g = Canvas(), ramp("#c0703a", sat_cap=0.55)
	hub = (16, 20)
	cv.part(line([hub, (12, 26), (8, 31)], width=2), flat(LEATHER[2]))
	cv.part(ellipse((11, 14, 21, 24)), flat(g[2]))
	for tip, w in (((3, 16), 4.0), ((29, 16), 4.0), ((7, 5), 4.4), ((26, 5), 4.4), ((16, 1), 4.6)):
		draw_leaf(cv, hub, tip, w, g, sep=False, serr=0.8, shape=1.1, n=12, frac=0.85)
	cv.put(*hub, g[0])
	return cv


def green_sylkis():
	"""Pale silky blades, a bunch tied with copper wire."""
	cv, g = Canvas(), ramp("#86bc9a", sat_cap=0.4)
	for top in ((3, 5), (8, 2), (13, 1), (19, 1), (24, 2), (29, 5)):
		m = line([(16, 21), (16 + (top[0] - 16) * 0.4, 13), top], width=2)
		cv.part(m, lambda cv_, mm, top=top: (cv_.fill(mm, g[2]), cv_.fill(edge(mm, "tl"), g[3]),
											 cv_.fill({top}, g[4])), sep=True)
	stalks = set()
	for x in range(13, 20):
		stalks |= line([(x, 21), (x + (x - 16) * 0.3, 30)])
	cv.part(stalks, bands(g, axis="x", stops=[3, 2, 2, 1]))
	tie(cv, 12, 21, 20, COPPER)
	return cv


def green_tantal():
	"""Olive vine with heart-shaped leaves and two dark berries."""
	cv, g = Canvas(), ramp("#8a8a3a", sat_cap=0.5)
	vine = [(5, 31), (9, 25), (13, 20), (18, 15), (23, 9), (26, 4)]
	cv.part(line(vine, width=2), flat(STEM[0]))
	for (cx, cy, s), stem in (((8, 17, 5), (10, 23)), ((23, 20, 5), (15, 18)), ((12, 8, 4), (18, 14))):
		m = heart(cx, cy, s, flip=False)
		cv.part(m, leaf_shade(g, (cx, cy + s), (cx, cy - s)))
		cv.fill(line([(cx, cy + s - 1), (cx, cy - s + 2)]) & m, g[1])
		cv.fill(line([stem, (cx, cy + s)]) - m, STEM[1])
	for x, y in ((25, 3), (28, 6)):
		cv.part(ellipse((x - 2, y - 2, x + 1, y + 1)), sphere(ramp("#8a3a44"), cuts=(0.8, 0.4, -0.1, -0.6)))
	return cv


def seeds():
	"""Four pale teardrop seeds, each with a green germ spot."""
	cv, r = Canvas(), ramp("#c2aa6c", sat_cap=0.45)
	for a, b in (((6, 13), (12, 4)), ((25, 11), (18, 4)), ((9, 28), (14, 18)), ((27, 27), (20, 19))):
		m = cv.part(leaf(a, b, 3.2, shape=0.6), pillow(r, depth=2, spec=True))
		cv.put(a[0] + (b[0] - a[0]) // 4, a[1] + (b[1] - a[1]) // 4, STEM[2])
	return cv


# ---------------------------------------------------------------- tack and armour

def buckle(cv, x, y, r=GOLD, w=5, h=4):
	"""Square buckle with a tongue: ink frame, lit top-left."""
	frame = rect(x, y, x + w - 1, y + h - 1) - rect(x + 1, y + 1, x + w - 2, y + h - 2)
	cv.part(frame, bevel(r, face=2, light=4, dark=1))
	cv.fill(rect(x + w // 2, y + 1, x + w // 2, y + h - 2), r[1])


def rivet(cv, x, y, r):
	cv.put(x, y, r[4]); cv.put(x + 1, y, r[2]); cv.put(x, y + 1, r[2]); cv.put(x + 1, y + 1, r[0])


BARDING = {
	#            base colour, ramp kwargs,           trim ramp
	"leather": ("#8a5a34", {}, GOLD),
	"iron": ("#8d98a2", {"sat_cap": 0.14, "shift": 4}, ramp("#5a6570", sat_cap=0.2)),
	"diamond": ("#4fb4ac", {"sat_cap": 0.5}, GOLD),
	"netherite": ("#4c4450", {"sat_cap": 0.18}, GOLD),
}


def barding(kind):
	"""Chocobo barding: a curved neck guard (crinet) over a chest plate; one silhouette
	logic for the set, each tier with its own edge, trim and surface."""
	base, kw, trim = BARDING[kind]
	r = ramp(base, **kw)
	cv = Canvas()
	# breast plate seen from the front: collar notch for the neck, wide shoulders,
	# material-specific hem
	top = [(3, 13), (3, 7), (8, 4), (11, 8), (14, 10), (18, 10), (21, 8), (24, 4), (29, 7), (29, 13)]
	if kind == "leather":        # scalloped tassel hem
		hem = [(28, 25), (26, 28), (24, 25), (22, 28), (20, 25), (18, 28), (16, 25), (14, 28), (12, 25), (10, 28), (8, 25), (6, 28), (5, 25)]
	elif kind == "iron":         # straight hem with dagged points
		hem = [(28, 26), (27, 29), (25, 26), (22, 26), (21, 29), (19, 26), (13, 26), (12, 29), (10, 26), (7, 26), (6, 29), (5, 26)]
	elif kind == "diamond":      # faceted V
		hem = [(28, 24), (22, 27), (16, 30), (10, 27), (5, 24)]
	else:                        # jagged spikes
		hem = [(28, 24), (27, 30), (24, 26), (21, 30), (18, 26), (16, 31), (14, 26), (11, 30), (8, 26), (5, 30), (5, 24)]
	hem = [(round(3 + (x - 5) * 26 / 23), y) for x, y in hem]
	plate = poly(top + hem)
	cv.part(plate, pillow(r, depth=4, cuts=(0.95, 0.4, -0.05, -0.55)))
	inner = shrink(plate, 2)
	collar = line([(7, 4), (11, 8), (14, 10), (18, 10), (21, 8), (25, 4)], width=3) & plate
	cr = r if kind in ("leather", "iron") else trim
	cv.part(collar, bevel(cr, face=2 if kind != "leather" else 1, light=3, dark=0), sep=False)
	cv.fill(edge(collar, "br") & edge(plate, "t"), cr[3])
	if kind == "leather":
		stitch = {p for p in edge(inner) if (p[0] + p[1]) % 3 == 0 and p[1] < 25}
		cv.fill(stitch, r[4])
		cv.put(9, 3, r[4])
		for x in (2, 25):
			cv.part(rect(x, 16, x + 4, 17), flat(r[0]), sep=False)
		buckle(cv, 13, 16, trim, w=6, h=5)
	elif kind == "iron":
		cv.fill(line([(16, 12), (16, 27)]) & inner, r[3])
		cv.fill(line([(17, 12), (17, 27)]) & inner, r[1])
		cv.fill(line([(4, 19), (28, 19)]) & plate, r[1])
		cv.fill(line([(4, 18), (28, 18)]) & plate, r[3])
		for x, y in ((6, 13), (25, 13), (6, 22), (25, 22), (11, 25), (20, 25)):
			rivet(cv, x, y, r)
	elif kind == "diamond":
		cv.fill(edge(plate, "t") | (edge(plate) & {p for p in plate if p[1] > 22}), trim[2])
		cv.fill(edge(plate, "t"), trim[3])
		for a, b in (((6, 12), (16, 22)), ((26, 12), (16, 22)), ((16, 12), (16, 27))):
			cv.fill(line([a, b]) & inner, r[1])
		cv.fill(poly([(8, 13), (14, 13), (15, 20)]) & inner, r[4])
		cv.fill(poly([(18, 13), (24, 13), (18, 18)]) & inner, r[3])
	else:
		cv.fill(edge(plate, "t"), trim[2])
		cv.fill({p for p in edge(plate, "t") if p[0] < 14}, trim[3])
		for x in range(7, 27, 5):
			cv.fill(line([(x, 14), (x + 2, 22)]) & inner, r[0])
			cv.put(x - 1, 14, r[3])
		cv.fill(line([(4, 23), (28, 23)]) & plate, trim[1])
	return cv


def saddle():
	"""Side view: stitched leather seat, raised cantle and pommel, teal blanket,
	flap, girth strap with a brass buckle and a stirrup."""
	cv = Canvas()
	blanket = rect(3, 14, 28, 22)
	cv.part(blanket, bands(TEAL, stops=[3, 2, 2, 1]))
	cv.fill(line([(3, 22), (28, 22)]), BONE[2])
	cv.fill({(x, 23) for x in range(4, 28, 2)}, BONE[1])                     # fringe
	seat = poly([(4, 5), (7, 4), (9, 9), (14, 11), (20, 10), (24, 7), (26, 5), (28, 7), (27, 12), (24, 15), (8, 15), (4, 12)])
	cv.part(seat, pillow(LEATHER, depth=3, spec=True))
	cv.fill(line([(8, 12), (14, 13), (20, 13), (25, 11)]) & seat, LEATHER[3])   # seat stitching highlight
	flap = poly([(9, 15), (22, 15), (22, 24), (19, 27), (12, 27), (9, 24)])
	cv.part(flap, pillow(ramp("#6a3c24"), depth=3))
	cv.fill({p for p in edge(shrink(flap)) if (p[0] + p[1]) % 2 == 0}, LEATHER[3])
	cv.part(rect(15, 24, 16, 28), flat(LEATHER[1]))                        # stirrup leather
	stirrup = rect(12, 28, 19, 31) - rect(14, 29, 17, 30)
	cv.part(stirrup, bevel(GOLD, face=2, light=4, dark=1))
	buckle(cv, 13, 18, GOLD, w=6, h=5)
	return cv


def saddlebags():
	"""Two leather pouches on a shared strap, flaps with brass buckles."""
	cv = Canvas()
	cv.part(line([(8, 11), (10, 5), (16, 3), (22, 5), (24, 11)], width=2), bands(LEATHER, stops=[3, 1]))
	for x0 in (2, 17):
		bag = poly([(x0, 11), (x0 + 12, 11), (x0 + 13, 26), (x0 + 11, 29), (x0 + 2, 29), (x0, 26)])
		cv.part(bag, pillow(LEATHER, depth=4))
		cv.fill(line([(x0 + 1, 25), (x0 + 12, 25)]) & bag, LEATHER[1])
		flap = poly([(x0 - 1, 9), (x0 + 13, 9), (x0 + 13, 17), (x0 + 6, 21), (x0 - 1, 17)])
		cv.part(flap, pillow(ramp("#6a3c24"), depth=3, spec=True))
		cv.fill({p for p in edge(shrink(flap)) if (p[0] + p[1]) % 2 == 0 and p[1] > 10}, LEATHER[3])
		buckle(cv, x0 + 4, 16, GOLD)
	return cv


# ---------------------------------------------------------------- trinkets

TOE_PRINT = ["#...#...#",
			 ".#..#..#.",
			 "..#.#.#..",
			 "...###...",
			 "....#....",
			 "....#...."]


def chocobo_lure():
	"""A violet charm on a looped cord, capped in copper, with a yellow feather."""
	cv = Canvas()
	cord = line([(12, 1), (9, 3), (9, 6), (12, 8), (15, 9)]) | line([(12, 1), (15, 2), (16, 5), (16, 9)])
	cv.part(cord, flat(BONE[2]))
	feather = leaf((20, 20), (28, 30), 2.6, serr=0.6, shape=0.7)
	cv.part(feather, leaf_shade(ramp("#e0b43a", sat_cap=0.6), (20, 20), (28, 30)))
	cv.fill(line([(20, 20), (27, 29)]) & feather, ramp("#e0b43a")[1])
	cv.part(ellipse((7, 12, 25, 30)), sphere(ramp("#7a5aa8", sat_cap=0.45), cuts=(0.9, 0.5, 0.0, -0.5)))
	cv.fill({(12, 17), (13, 16), (12, 18)}, BONE[4])
	cap = poly([(11, 9), (21, 9), (23, 13), (9, 13)])
	cv.part(cap, bevel(COPPER, face=2, light=3, dark=1))
	cv.fill(rect(15, 10, 16, 10), COPPER[4])
	return cv


def pocketwatch():
	"""Brass case on a ring bow, bone dial, navy hands at ten past ten."""
	cv = Canvas()
	cv.part(ellipse((12, 0, 19, 6)) - ellipse((14, 2, 17, 4)), flat(GOLD[2]))
	cv.fill({(13, 1), (14, 0), (15, 0)}, GOLD[4])
	cv.part(rect(14, 5, 17, 7), bevel(GOLD, face=2, light=3, dark=1))
	cv.part(ellipse((3, 7, 28, 31)), sphere(GOLD, cuts=(0.95, 0.5, 0.0, -0.55)))
	face = ellipse((7, 11, 24, 27))
	cv.part(face, pillow(BONE, depth=3, cuts=(2, 0.55, -0.4, -0.9)))
	cx, cy = 15.5, 19
	for ang in range(0, 360, 30):                                     # hour ticks, long at 12/3/6/9
		a = math.radians(ang)
		n = 2 if ang % 90 == 0 else 1
		for k in range(n):
			cv.put(round(cx + math.cos(a) * (7 - k)), round(cy + math.sin(a) * (7 - k)), NAVY[2 if n == 2 else 3])
	cv.fill(line([(15, 19), (11, 16)]), NAVY[1])                      # hour hand to 10
	cv.fill(line([(16, 19), (20, 14)]), NAVY[1])                      # minute hand to 2
	cv.fill(rect(15, 18, 16, 19), COPPER[1])
	cv.fill({(10, 13), (11, 13), (10, 14)}, (255, 252, 240))           # glass glint
	return cv


def gp():
	"""A thick gold coin stamped with the three-toed chocobo print."""
	cv = Canvas()
	cv.part(ellipse((3, 6, 28, 30)), flat(GOLD[0]))                      # coin edge (thickness)
	cv.fill({(x, y) for x in range(4, 28, 2) for y in range(24, 31)} & ellipse((3, 6, 28, 30)) - ellipse((3, 3, 28, 27)), GOLD[1])
	face = ellipse((3, 3, 28, 27))
	cv.part(face, sphere(GOLD, spec=False, cuts=(2, 0.35, -0.2, -0.75)), sep=False)
	ring = ellipse((6, 6, 25, 24)) - ellipse((7, 7, 24, 23))
	cv.fill(ring, GOLD[1])
	cv.fill(shift(ring & {p for p in ring if p[0] + p[1] > 31}, 0, 0), GOLD[1])
	cv.fill({p for p in ring if p[0] + p[1] < 26}, GOLD[4])
	print_ = pmap(TOE_PRINT, 11, 10)
	cv.fill(shift(print_, 1, 1), GOLD[4])                                 # embossed: lit lower-right lip
	cv.fill(print_, GOLD[0])
	return cv


def chocobo_almanac():
	"""Teal leather field journal: copper corners, gold toe-print, bone pages, ribbon."""
	cv = Canvas()
	cover = ramp("#2f6f68", sat_cap=0.5)
	cv.part(rect(8, 5, 27, 28), bands(BONE, axis="x", stops=[3, 3, 2, 1]))          # page block
	for y in range(7, 28, 3):
		cv.fill(rect(24, y, 27, y), BONE[1])
	cv.part(rect(4, 3, 25, 29), bevel(cover, face=2, light=3, dark=1, width=2))
	cv.part(rect(4, 3, 7, 29), bands(cover, axis="x", stops=[1, 2, 1, 0]), sep=False)   # spine
	for y in (6, 15, 25):
		cv.fill(rect(4, y, 7, y), GOLD[2]); cv.fill(rect(4, y + 1, 7, y + 1), GOLD[0])
	for x, y in ((22, 3), (22, 26)):                                                # copper corners
		cv.part(rect(x, y, x + 3, y + 3), bevel(COPPER, face=2, light=4, dark=1), sep=False)
	cv.fill(pmap(TOE_PRINT, 11, 11), GOLD[3])
	cv.fill(shift(pmap(TOE_PRINT, 11, 11), 1, 1) - pmap(TOE_PRINT, 11, 11), cover[0])
	cv.fill(rect(10, 9, 21, 9) | rect(10, 20, 21, 20), GOLD[1])                     # tooled rules
	cv.part(rect(18, 0, 19, 3) | {(18, 4)}, flat(ramp("#a8403a")[2]), sep=False)    # ribbon
	cv.put(19, 0, ramp("#a8403a")[3])
	return cv


# ---------------------------------------------------------------- crop stages

GYSAHL = ramp("#6f9e4a", sat_cap=0.55)
GYSAHL_HI = ramp("#7fae50", sat_cap=0.55)
BULB = ramp("#dcdcb0", sat_cap=0.3)

# Per stage: (leaves as (base, tip, half-width)), bulb box or None. Ground is row 31.
CROP = [
	([((15, 31), (10, 24), 2.4), ((17, 31), (22, 24), 2.4)], None),
	([((15, 31), (7, 22), 2.8), ((17, 31), (25, 22), 2.8), ((16, 31), (14, 18), 2.6), ((16, 31), (19, 19), 2.6)], None),
	([((15, 31), (4, 19), 3.0), ((17, 31), (28, 19), 3.0), ((15, 30), (10, 12), 3.0), ((17, 30), (22, 12), 3.0),
	  ((16, 30), (16, 10), 3.0)], None),
	([((14, 27), (3, 13), 3.2), ((18, 27), (29, 13), 3.2), ((15, 26), (9, 6), 3.2), ((17, 26), (23, 6), 3.2),
	  ((16, 26), (16, 4), 3.4)], (11, 25, 21, 32)),
	([((13, 22), (2, 9), 3.4), ((19, 22), (30, 9), 3.4), ((14, 21), (8, 2), 3.4), ((18, 21), (24, 2), 3.4),
	  ((16, 21), (16, 1), 3.6)], (9, 20, 23, 32)),
]


def crop_stage(n):
	"""Gysahl growing: two seed leaves -> a crown of frilled leaves over a pale bulb.
	Same leaves, ramp and bulb as the gysahl_green item."""
	cv = Canvas()
	leaves, bulb = CROP[n]
	for i, (a, b, w) in enumerate(leaves):
		r = GYSAHL_HI if i == len(leaves) - 1 and n >= 2 else GYSAHL
		draw_leaf(cv, a, b, w, r, lobes=3 if n >= 2 else 0, shape=0.6)
	if bulb:
		x0, y0, x1, y1 = bulb
		m = ellipse((x0, y0, x1, y1 + (y1 - y0) // 2))
		cv.part(m, pillow(BULB, depth=3, spec=n == 4))
		cv.fill(line([(x0 + 2, y0 + 1), (x1 - 2, y0 + 1)]) & m, ramp("#a8c070")[2])
	return cv


# ---------------------------------------------------------------- square gates

GATE_GEM = {
	"square_gate": ramp("#4f9a5a", sat_cap=0.5),          # entry: emerald
	"square_gate_short": ramp("#d2a54a", sat_cap=0.62),   # short course: topaz
	"square_gate_long": ramp("#4a78b0", sat_cap=0.5),     # long course: sapphire
	"square_gate_return": ramp("#a8483a", sat_cap=0.55),  # return: garnet
}
GATE_GLYPH = {
	"square_gate": ["....##....", "...####...", "..######..", ".########.", "##########",
					".########.", "..######..", "...####...", "....##...."],
	"square_gate_short": ["....##....", "...####...", "..######..", ".###..###.", "###....###",
						  "##......##", "..........", "..........", ".........."],
	"square_gate_long": ["....##....", "...####...", "..##..##..", ".##....##.", "....##....",
						 "...####...", "..##..##..", ".##....##.", ".........."],
	"square_gate_return": ["...####...", ".########.", "###....###", "##......##", "##......##",
						   "##.....###", "......#####", ".......###.", "........#.."],
}


def square_gate(name):
	"""Navy stone block, copper frame and corner rivets, a recessed panel with the
	gate's jewel glyph (entry gem / one chevron / two chevrons / turn-back)."""
	cv = Canvas()
	cv.fill(rect(0, 0, 31, 31), NAVY[1])
	cv.part(rect(0, 0, 31, 31), bevel(COPPER, face=2, light=3, dark=0, width=2), sep=False)
	cv.part(rect(2, 2, 29, 29), bevel(NAVY, face=2, light=3, dark=0), sep=False)
	for x, y in ((4, 4), (26, 4), (4, 26), (26, 26)):
		cv.fill(rect(x, y, x + 1, y + 1), COPPER[1]); cv.put(x, y, COPPER[4])
	cv.part(rect(7, 7, 24, 24), bevel(NAVY, face=0, light=0, dark=3), sep=False)   # recess: lit lower-right lip
	cv.fill(rect(8, 8, 23, 23), mix(NAVY[0], (0, 0, 0), 0.25))
	g = GATE_GEM[name]
	glyph = pmap(GATE_GLYPH[name], 11, 11)
	sh = pmap(GATE_GLYPH[name], 12, 12) - glyph
	cv.fill(sh, g[0])
	for x, y in glyph:
		t = (x - 11) + (y - 11)
		cv.put(x, y, g[4] if t <= 3 else g[3] if t <= 7 else g[2] if t <= 12 else g[1])
	return cv


# ---------------------------------------------------------------- boost pad (animated, 4 frames)

def boost_pad():
	"""Navy course plate with copper rails and gold chevrons scrolling north."""
	frames, period = 4, 16
	im = Image.new("RGBA", (32, 32 * frames))
	for f in range(frames):
		cv = Canvas()
		cv.fill(rect(0, 0, 31, 31), NAVY[2])
		for y in range(32):
			for x in range(32):
				k = int(abs(x - 15.5))
				if 3 <= x <= 28:
					ph = (y + f * 4 - int(k * 0.8)) % period
					c = {0: GOLD[4], 1: GOLD[3], 2: GOLD[2], 3: GOLD[1], 4: NAVY[0]}.get(ph)
					if c:
						cv.put(x, y, c)
		cv.fill(rect(0, 0, 1, 31) | rect(30, 0, 31, 31), COPPER[1])
		cv.fill(rect(1, 0, 1, 31), COPPER[3])
		cv.fill(rect(30, 0, 30, 31), COPPER[0])
		cv.fill(rect(2, 0, 2, 31) | rect(29, 0, 29, 31), NAVY[0])
		im.paste(cv.image(outline=False), (0, 32 * f))
	return im


# ---------------------------------------------------------------- almanac GUI (256x256, 9-sliced at 16 px)

def almanac_gui():
	"""Same geometry as the old leather journal (AlmanacScreen UVs), recoloured to the
	family's navy panel with a copper frame and a teal inner rule."""
	cv = Canvas(256, 256)
	panel0, panel1 = mix(NAVY[0], INK_DEEP, 0.3), NAVY[1]
	cv.fill(rect(0, 0, 255, 255), panel0)
	cv.fill(rect(5, 5, 250, 250), panel1)
	cv.fill(rect(10, 10, 245, 11) | rect(10, 10, 11, 245), COPPER[2])
	cv.fill(rect(10, 244, 245, 245) | rect(244, 10, 245, 245), COPPER[1])
	cv.fill(rect(13, 13, 242, 13) | rect(13, 13, 13, 242) | rect(13, 242, 242, 242) | rect(242, 13, 242, 242), TEAL[1])
	cv.fill(rect(14, 14, 241, 241), mix(NAVY[0], INK_DEEP, 0.5))
	cv.fill(rect(16, 16, 239, 239), NAVY[0])
	for x0, y0 in ((6, 6), (238, 6), (6, 238), (238, 238)):
		cv.fill(rect(x0, y0, x0 + 11, y0 + 11), COPPER[1])
		cv.fill(rect(x0 + 1, y0 + 1, x0 + 10, y0 + 10), COPPER[2])
		cv.put(x0 + 2, y0 + 2, COPPER[4])
	cv.fill(rect(14, 14, 18, 241), mix(NAVY[0], INK_DEEP, 0.6))
	return cv.image(outline=False)


INK_DEEP = (10, 14, 20)


# ---------------------------------------------------------------- registry (texture path -> drawing)


ITEMS = {
	"item/carob_nut": nut_carob, "item/lasan_nut": nut_lasan, "item/luchile_nut": nut_luchile,
	"item/pepio_nut": nut_pepio, "item/porov_nut": nut_porov, "item/pram_nut": nut_pram,
	"item/saraha_nut": nut_saraha, "item/zeio_nut": nut_zeio,
	"item/gysahl_green": green_gysahl, "item/curiel_green": green_curiel, "item/krakka_green": green_krakka,
	"item/mimett_green": green_mimett, "item/pahsana_green": green_pahsana, "item/reagan_green": green_reagan,
	"item/sylkis_green": green_sylkis, "item/tantal_green": green_tantal, "item/gysahl_green_seeds": seeds,
	"item/leather_chocobo_armor": lambda: barding("leather"), "item/iron_chocobo_armor": lambda: barding("iron"),
	"item/diamond_chocobo_armor": lambda: barding("diamond"), "item/netherite_chocobo_armor": lambda: barding("netherite"),
	"item/chocobo_saddle": saddle, "item/saddlebags": saddlebags, "item/chocobo_lure": chocobo_lure,
	"item/chocobo_pocketwatch": pocketwatch, "item/gp": gp, "item/chocobo_almanac": chocobo_almanac,
}


def render(fn):
	out = fn()
	return out.image() if isinstance(out, Canvas) else out


BLOCKS = {f"block/gysahl_green{n}": (lambda n=n: crop_stage(n)) for n in range(5)}
BLOCKS.update({f"block/{name}": (lambda name=name: square_gate(name)) for name in GATE_GEM})
BLOCKS["block/boost_pad"] = boost_pad
GUI = {"gui/almanac": almanac_gui}

REGISTRY = [(k, (lambda f=f: render(f))) for k, f in {**ITEMS, **BLOCKS, **GUI}.items()]


def check(name, im):
	"""Family rules the art must keep: hard alpha, and the texture size the game expects."""
	alphas = {a for a, n in enumerate(im.getchannel("A").histogram()) if n}
	assert alphas <= {0, 255}, f"{name}: soft alpha {sorted(alphas)}"
	want = (256, 256) if name.startswith("gui/") else (32, 128) if name == "block/boost_pad" else (32, 32)
	assert im.size == want, f"{name}: {im.size} != {want}"


def main(argv):
	wrote = 0
	for name, fn in REGISTRY:
		if argv and not any(k in name for k in argv):
			continue
		im = fn()
		check(name, im)
		dest = TEX / f"{name}.png"
		dest.parent.mkdir(parents=True, exist_ok=True)
		im.save(dest)
		wrote += 1
		print("wrote", dest.relative_to(ROOT).as_posix(), im.size)
	print(f"{wrote} textures")


if __name__ == "__main__":
	main(sys.argv[1:])
