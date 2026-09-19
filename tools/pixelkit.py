"""Pixel kit for the 32x32 item / block art (Ninjacat Skies family style).

Rules baked in: native 32x32, alpha 0 or 255, light from the top-left, 5-tone
hue-shifted ramps (shadows cooler, highlights warmer), 1 px outline tinted
toward the item's own hue from the ink base #111a22, no noise, no AA.

A drawing is a Canvas; parts are masks (sets of pixels rasterised from
polygons / ellipses / thick lines / pixel maps) filled by a shader:
  flat(tone)      one ramp tone
  pillow(ramp)    dome shading from the mask's own distance field, so any
                  silhouette (pod, leaf, plate) gets rounded top-left light
  sphere(ramp)    ellipse-normal shading for round things
  bevel(ramp)     flat face, light top-left edge, dark bottom-right edge
Each part can ink-separate itself from what is already drawn (sep=True), and
Canvas.image() adds the tinted outer outline.
"""
from __future__ import annotations

import colorsys
import math

from PIL import Image, ImageDraw

INK = (0x11, 0x1A, 0x22)
CLEAR = (0, 0, 0, 0)
N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))


def hexc(h: str) -> tuple[int, int, int]:
	h = h.lstrip("#")
	return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t: float):
	return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def _hue_toward(h: float, target: float, deg: float) -> float:
	d = (target - h + 0.5) % 1.0 - 0.5
	step = deg / 360.0
	return (h + max(-abs(d), min(abs(d), step)) * (1 if d >= 0 else -1)) % 1.0


def ramp(base, shift: float = 9.0, sat_cap: float = 0.62, lo: float = 0.24, hi: float = 0.36):
	"""Five tones, darkest first: hue slides to blue in shadow, to yellow in light."""
	if isinstance(base, str):
		base = hexc(base)
	h, s, v = colorsys.rgb_to_hsv(*(c / 255 for c in base))
	out = []
	for k in (-2, -1, 0, 1, 2):
		if k < 0:
			hh = _hue_toward(h, 0.64, shift * -k) if s > 0.06 else h
			ss = min(sat_cap + 0.08, s + 0.05 * -k)
			vv = v * (1 - lo * -k)
		elif k > 0:
			hh = _hue_toward(h, 0.15, shift * k) if s > 0.06 else h
			ss = max(0.0, s - 0.1 * k)
			vv = v + (1 - v) * hi * k
		else:
			hh, ss, vv = h, min(s, sat_cap + 0.04), v
		ss = min(ss, sat_cap + 0.08)
		out.append(tuple(round(c * 255) for c in colorsys.hsv_to_rgb(hh, ss, min(1.0, vv))))
	return out


def ink_of(c):
	"""Outline colour for a region of colour c: ink pulled a little toward its hue."""
	return mix(INK, c, 0.22)


# ---------------------------------------------------------------- masks

def _raster(draw_fn, w=32, h=32) -> set:
	im = Image.new("L", (w, h), 0)
	draw_fn(ImageDraw.Draw(im))
	px = im.load()
	return {(x, y) for y in range(h) for x in range(w) if px[x, y]}


def poly(points, w=32, h=32) -> set:
	return _raster(lambda d: d.polygon([tuple(p) for p in points], fill=255, outline=255), w, h)


def ellipse(box, w=32, h=32) -> set:
	return _raster(lambda d: d.ellipse(box, fill=255), w, h)


def rect(x0, y0, x1, y1) -> set:
	return {(x, y) for y in range(y0, y1 + 1) for x in range(x0, x1 + 1)}


def line(points, width=1, w=32, h=32) -> set:
	pts = [tuple(p) for p in points]
	if width == 1:
		return _raster(lambda d: d.line(pts, fill=255, width=1), w, h)
	return _raster(lambda d: d.line(pts, fill=255, width=width, joint="curve"), w, h)


def pmap(rows, ox=0, oy=0, ch="#") -> set:
	"""Mask from a text pixel map: every `ch` is a pixel."""
	return {(ox + x, oy + y) for y, r in enumerate(rows) for x, c in enumerate(r) if c in ch}


def grow(mask: set, n=1) -> set:
	m = set(mask)
	for _ in range(n):
		m |= {(x + dx, y + dy) for x, y in m for dx, dy in N4}
	return m


def shrink(mask: set, n=1) -> set:
	m = set(mask)
	for _ in range(n):
		m = {p for p in m if all((p[0] + dx, p[1] + dy) in m for dx, dy in N4)}
	return m


def edge(mask: set, side: str = "all") -> set:
	"""Pixels of the mask on its border; side 'tl' (top/left) or 'br' (bottom/right)."""
	dirs = {"all": N4, "tl": ((-1, 0), (0, -1)), "br": ((1, 0), (0, 1)),
			"t": ((0, -1),), "b": ((0, 1),), "l": ((-1, 0),), "r": ((1, 0),)}[side]
	return {(x, y) for x, y in mask if any((x + dx, y + dy) not in mask for dx, dy in dirs)}


def shift(mask: set, dx: int, dy: int) -> set:
	return {(x + dx, y + dy) for x, y in mask}


def mirror_x(mask: set, w=32) -> set:
	return {(w - 1 - x, y) for x, y in mask}


# ---------------------------------------------------------------- canvas

class Canvas:
	def __init__(self, w=32, h=32):
		self.w, self.h = w, h
		self.px: dict = {}

	def inside(self, p) -> bool:
		return 0 <= p[0] < self.w and 0 <= p[1] < self.h

	def put(self, x, y, c):
		if self.inside((x, y)):
			self.px[(x, y)] = tuple(c[:3])

	def fill(self, mask, c):
		for p in mask:
			self.put(*p, c)

	def erase(self, mask):
		for p in mask:
			self.px.pop(p, None)

	def separate(self, mask, c=None):
		"""Ink line just outside `mask` wherever something is already drawn there."""
		ring = grow(mask) - mask
		for p in ring:
			if p in self.px:
				self.px[p] = ink_of(c if c is not None else self.px[p])

	def part(self, mask, shader, sep=True, sep_colour=None):
		mask = {p for p in mask if self.inside(p)}
		if sep:
			self.separate(mask, sep_colour)
		shader(self, mask)
		return mask

	def image(self, outline=True, outline_mode="tint") -> Image.Image:
		im = Image.new("RGBA", (self.w, self.h), CLEAR)
		px = im.load()
		for (x, y), c in self.px.items():
			px[x, y] = (*c, 255)
		if outline:
			ring = {}
			for (x, y), c in self.px.items():
				for dx, dy in N4:
					q = (x + dx, y + dy)
					if self.inside(q) and q not in self.px:
						ring.setdefault(q, []).append(c)
			for (x, y), cs in ring.items():
				avg = tuple(sum(c[i] for c in cs) // len(cs) for i in range(3))
				px[x, y] = (*(ink_of(avg) if outline_mode == "tint" else INK), 255)
		return im


# ---------------------------------------------------------------- shaders

LIGHT = (-0.55, -0.62, 0.56)


def _norm(v):
	n = math.sqrt(sum(a * a for a in v)) or 1.0
	return tuple(a / n for a in v)


def flat(c):
	return lambda cv, m: cv.fill(m, c)


def _tone(r, i, cuts, spec):
	if spec and i > cuts[0]:
		return r[4]
	if i > cuts[1]:
		return r[3]
	if i > cuts[2]:
		return r[2]
	if i > cuts[3]:
		return r[1]
	return r[0]


def sphere(r, spec=True, cuts=(0.93, 0.55, 0.05, -0.45), box=None):
	lx, ly, lz = _norm(LIGHT)

	def run(cv, m):
		xs = [p[0] for p in m]; ys = [p[1] for p in m]
		x0, y0, x1, y1 = box or (min(xs), min(ys), max(xs), max(ys))
		cx, cy = (x0 + x1 + 1) / 2, (y0 + y1 + 1) / 2
		rx, ry = max(1, (x1 - x0 + 1) / 2), max(1, (y1 - y0 + 1) / 2)
		for x, y in m:
			nx, ny = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
			d = nx * nx + ny * ny
			nz = math.sqrt(max(0.0, 1 - d)) if d < 1 else 0.0
			n = _norm((nx, ny, nz + 0.05))
			cv.put(x, y, _tone(r, n[0] * lx + n[1] * ly + n[2] * lz, cuts, spec))
	return run


def _dist(m):
	"""City-block distance from each mask pixel to the outside."""
	d = {p: 0 for p in m if any((p[0] + dx, p[1] + dy) not in m for dx, dy in N4)}
	frontier = list(d)
	while frontier:
		nxt = []
		for x, y in frontier:
			for dx, dy in N4:
				q = (x + dx, y + dy)
				if q in m and q not in d:
					d[q] = d[(x, y)] + 1
					nxt.append(q)
		frontier = nxt
	return d


def pillow(r, depth=3.0, spec=False, cuts=(0.95, 0.5, 0.02, -0.5)):
	"""Dome shading for any silhouette: height from the distance field, lit top-left."""
	lx, ly, lz = _norm(LIGHT)

	def run(cv, m):
		d = _dist(m)
		hgt = {p: math.sqrt(min(v + 1, depth) / depth) for p, v in d.items()}
		for (x, y), hv in hgt.items():
			gx = hgt.get((x + 1, y), 0) - hgt.get((x - 1, y), 0)
			gy = hgt.get((x, y + 1), 0) - hgt.get((x, y - 1), 0)
			n = _norm((-gx, -gy, 0.9))
			cv.put(x, y, _tone(r, n[0] * lx + n[1] * ly + n[2] * lz - 0.5, cuts, spec))
	return run


def bevel(r, face=2, light=3, dark=1, width=1):
	def run(cv, m):
		cv.fill(m, r[face])
		inner = set(m)
		for _ in range(width):
			tl, br = edge(inner, "tl"), edge(inner, "br")
			cv.fill(br, r[dark])
			cv.fill(tl - br, r[light])
			inner = shrink(inner)
	return run


def bands(r, axis="y", stops=None):
	"""Linear bands across a mask (cylinder-ish): tones listed light to dark along axis."""
	stops = stops or [3, 2, 2, 1]

	def run(cv, m):
		k = 0 if axis == "x" else 1
		lo, hi = min(p[k] for p in m), max(p[k] for p in m)
		span = max(1, hi - lo + 1)
		for p in m:
			cv.put(*p, r[stops[min(len(stops) - 1, (p[k] - lo) * len(stops) // span)]])
	return run


def paint(cv: Canvas, rows, legend: dict, ox=0, oy=0):
	"""Hand pixel map: each character maps to a colour in `legend` ('.'/' ' = skip)."""
	for y, row in enumerate(rows):
		for x, ch in enumerate(row):
			if ch in legend:
				cv.put(ox + x, oy + y, legend[ch])


def dither_free(im: Image.Image) -> Image.Image:
	"""Remove lone single pixels whose 4 neighbours all share one other colour."""
	px = im.load()
	w, h = im.size
	fix = []
	for y in range(1, h - 1):
		for x in range(1, w - 1):
			c = px[x, y]
			if c[3] == 0:
				continue
			ns = [px[x + dx, y + dy] for dx, dy in N4]
			if all(n == ns[0] for n in ns) and ns[0] != c and ns[0][3] and c not in ns:
				fix.append((x, y, ns[0]))
	for x, y, c in fix:
		px[x, y] = c
	return im
