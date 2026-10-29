import numpy as np
from PIL import Image
from scipy.ndimage import gaussian_filter, map_coordinates

N_ALB = 2048   # albedo resolution
N_NR  = 1024   # normal+roughness resolution (both are generated at N_ALB then resized)
N = N_ALB

def pnoise(beta, seed, n=N):
    """Tileable 1/f^beta noise in [0,1] (generated in the frequency domain, so it wraps seamlessly)."""
    rng = np.random.default_rng(seed)
    w = rng.standard_normal((n, n)).astype(np.float32)
    F = np.fft.fft2(w)
    fy = np.fft.fftfreq(n)[:, None]; fx = np.fft.fftfreq(n)[None, :]
    f = np.sqrt(fx * fx + fy * fy); f[0, 0] = 1.0
    F *= 1.0 / (f ** beta)
    F[0, 0] = 0
    a = np.real(np.fft.ifft2(F))
    a -= a.min(); a /= (a.max() + 1e-9)
    return a.astype(np.float32)

def fbm(seed, lo=1.6, hi=2.4, n=N):
    return pnoise(lo, seed, n) * 0.6 + pnoise(hi, seed + 101, n) * 0.4

def smooth(x, a, b):
    t = np.clip((x - a) / (b - a), 0, 1); return t * t * (3 - 2 * t)

def voronoi_edges(cells, seed, n=N, jitter=0.9):
    """Tileable voronoi: returns (F2-F1 edge distance map in [0,1], cell id map, F1)."""
    rng = np.random.default_rng(seed)
    pts = []
    for i in range(cells):
        for j in range(cells):
            pts.append(((i + 0.5 + (rng.random() - 0.5) * jitter) / cells,
                        (j + 0.5 + (rng.random() - 0.5) * jitter) / cells))
    pts = np.array(pts, dtype=np.float32)
    ys, xs = np.mgrid[0:n, 0:n].astype(np.float32) / n
    d1 = np.full((n, n), 9.0, np.float32); d2 = np.full((n, n), 9.0, np.float32)
    ids = np.zeros((n, n), np.int32)
    for k, (px, py) in enumerate(pts):
        dx = np.abs(xs - px); dx = np.minimum(dx, 1 - dx)
        dy = np.abs(ys - py); dy = np.minimum(dy, 1 - dy)
        d = np.sqrt(dx * dx + dy * dy)
        m = d < d1
        d2 = np.where(m, d1, np.minimum(d2, d))
        ids = np.where(m, k, ids)
        d1 = np.where(m, d, d1)
    return (d2 - d1), ids, d1

def warp(img, amt, seed):
    n = img.shape[0]
    wx = (pnoise(2.2, seed) - 0.5) * amt; wy = (pnoise(2.2, seed + 7) - 0.5) * amt
    ys, xs = np.mgrid[0:n, 0:n].astype(np.float32)
    return map_coordinates(img, [(ys + wy) % n, (xs + wx) % n], order=1, mode='wrap')

def normal_from_height(h, strength):
    dx = (np.roll(h, -1, 1) - np.roll(h, 1, 1)) * 0.5
    dy = (np.roll(h, -1, 0) - np.roll(h, 1, 0)) * 0.5
    nx = -dx * strength; ny = -dy * strength
    nz = np.ones_like(nx)
    l = np.sqrt(nx * nx + ny * ny + nz * nz)
    return nx / l, ny / l

def save(name, albedo, height, rough, nstrength):
    a = np.clip(albedo, 0, 1)
    Image.fromarray((a * 255 + 0.5).astype(np.uint8), 'RGB').save(f'{OUT}/{name}.png', optimize=True)
    h = np.array(Image.fromarray(height.astype(np.float32), 'F').resize((N_NR, N_NR), Image.BILINEAR))
    r = np.array(Image.fromarray(rough.astype(np.float32), 'F').resize((N_NR, N_NR), Image.BILINEAR))
    nx, ny = normal_from_height(h, nstrength)
    # RG = tangent-space normal XY, B = roughness. Z is rebuilt in the shader. No alpha channel, so no premultiply issues.
    img = np.stack([nx * 0.5 + 0.5, ny * 0.5 + 0.5, np.clip(r, 0, 1)], -1)
    Image.fromarray((img * 255 + 0.5).astype(np.uint8), 'RGB').save(f'{OUT}/{name}_nr.png', optimize=True)

OUT = '../app/src/main/assets/textures'

# ---------------------------------------------------------------- aged plaster
def plaster():
    n = N
    base = fbm(11)
    mid = pnoise(1.0, 60)                                   # mid-frequency mottling
    sand = pnoise(0.15, 61)                                 # fine sandy grain
    # vertical water streaks: noise blurred strongly along y
    sk = gaussian_filter(pnoise(1.1, 62), (45, 2.5), mode='wrap'); sk = (sk - sk.min()) / (sk.max() - sk.min())
    streaks = smooth(sk, 0.52, 0.78)
    stains = smooth(pnoise(2.4, 12), 0.55, 0.80)
    mould = smooth(fbm(13, 1.1, 1.9), 0.64, 0.84)
    pores = smooth(pnoise(0.35, 14), 0.76, 0.93)
    # sparse, jagged, wandering cracks: voronoi edges warped at two scales, only some of them kept
    e, _, _ = voronoi_edges(5, 15, jitter=1.0)
    c = 1.0 - smooth(e, 0.0, 0.007)
    c = warp(c, 140, 16)
    c = warp(c, 16, 63)
    keep = smooth(pnoise(2.2, 17), 0.50, 0.60)
    width = 0.5 + 0.5 * pnoise(1.0, 64)
    cr = c * keep * width
    # secondary hairline cracks
    e3, _, _ = voronoi_edges(13, 65, jitter=1.0)
    hair = warp(1.0 - smooth(e3, 0.0, 0.0035), 60, 66) * smooth(pnoise(2.4, 67), 0.60, 0.70)
    # peeling paint patches
    peel = smooth(pnoise(2.8, 18), 0.62, 0.70)
    peel_edge = np.clip(peel - gaussian_filter(peel, 3), 0, 1) * 6
    lum = 0.70 + 0.18 * (base - 0.5) * 2 + 0.10 * (mid - 0.5) * 2
    lum *= 1 - 0.30 * stains - 0.25 * mould - 0.28 * streaks
    lum *= 1 - 0.30 * pores - 0.60 * cr - 0.35 * hair
    lum = lum * (1 - peel) + (0.84 + 0.12 * (pnoise(1.0, 19) - 0.5)) * peel
    lum += 0.10 * peel_edge
    lum += (sand - 0.5) * 0.14 + (pnoise(0.1, 20) - 0.5) * 0.06
    tint = np.array([1.00, 0.95, 0.86], np.float32)
    # damp areas get slightly greener / darker
    rgb = lum[..., None] * tint
    rgb[..., 0] *= 1 - 0.10 * mould; rgb[..., 2] *= 1 + 0.05 * mould
    height = 0.50 * base + 0.25 * mid + 0.30 * sand - 0.35 * pores - 1.0 * cr - 0.6 * hair + 0.3 * peel
    rough = 0.82 + 0.10 * (pnoise(1.0, 22) - 0.5) + 0.06 * stains - 0.12 * peel - 0.08 * streaks
    save('plaster', rgb, height, rough, 7.0)

# ---------------------------------------------------------------- old wooden planks
def wood():
    n = N
    ys, xs = np.mgrid[0:n, 0:n].astype(np.float32) / n
    planks = 6
    pid = np.floor(ys * planks).astype(np.int32)
    rng = np.random.default_rng(31)
    shade = 0.82 + 0.28 * rng.random(planks)[pid % planks]
    offs = rng.random(planks)[pid % planks] * 3.0
    local_y = (ys * planks) % 1.0
    # grain: many fine streaks of different scales, plus faint wavy growth rings (low weight so it does not look zebra-striped)
    def streaks(seed, sy, sx):
        r = np.random.default_rng(seed).random((n, n)).astype(np.float32)
        g = gaussian_filter(r, (sy, sx), mode='wrap'); return (g - g.min()) / (g.max() - g.min())
    s1 = streaks(32, 0.7, 70); s2 = streaks(33, 1.6, 110); s3 = streaks(34, 0.4, 45)
    w1 = pnoise(1.8, 35) - 0.5
    rings = np.sin(local_y * 70.0 + w1 * 18.0 + offs * 6.28 + pnoise(1.0, 36) * 9.0) * 0.5 + 0.5
    grain = 0.40 * s1 + 0.28 * s2 + 0.20 * s3 + 0.12 * rings
    grain = (grain - grain.min()) / (grain.max() - grain.min())
    # staggered butt joints between boards (vertical seams, different per plank)
    jx = rng.random(planks)[pid % planks]
    dxj = np.abs(((xs - jx) + 0.5) % 1.0 - 0.5)
    joint = np.exp(-(dxj / 0.0035) ** 2)
    # knots
    kn = np.zeros((n, n), np.float32)
    for k in range(5):
        cx, cy = rng.random(), rng.random()
        dx = np.minimum(np.abs(xs - cx), 1 - np.abs(xs - cx)); dy = np.minimum(np.abs(ys - cy), 1 - np.abs(ys - cy))
        r = np.sqrt((dx * 1.0) ** 2 + (dy * 2.2) ** 2)
        kn += np.exp(-(r / 0.018) ** 2) * 0.8 + np.sin(r * 340) * np.exp(-(r / 0.05) ** 2) * 0.25
    # plank seams, scratches, stains, worn varnish
    seam = np.exp(-(np.minimum(local_y, 1 - local_y) / 0.006) ** 2)
    scr = smooth(gaussian_filter(np.random.default_rng(36).random((n, n)).astype(np.float32), (0.5, 40), mode='wrap'), 0.57, 0.66)
    stain = smooth(pnoise(2.4, 37), 0.55, 0.8)
    lum = (0.34 + 0.34 * grain) * shade
    lum *= 1 - 0.45 * np.clip(kn, 0, 1) - 0.70 * np.clip(seam + joint, 0, 1) - 0.30 * stain
    lum += 0.10 * scr
    lum += (pnoise(0.2, 38) - 0.5) * 0.06
    rgb = np.stack([lum * 1.00, lum * 0.80, lum * 0.60], -1)
    height = 0.30 * grain - 0.30 * np.clip(kn, 0, 1) - 1.0 * np.clip(seam + joint, 0, 1) - 0.12 * scr + 0.1 * pnoise(0.5, 39)
    rough = 0.62 + 0.22 * (1 - grain) + 0.15 * seam + 0.12 * stain - 0.18 * scr
    save('old_wood', rgb, height, rough, 6.0)

# ---------------------------------------------------------------- dirty stone floor tiles
def stone():
    e, ids, f1 = voronoi_edges(5, 41, jitter=0.35)   # large, fairly regular flagstones
    n = N
    grout = 1.0 - smooth(e, 0.0, 0.014)
    rng = np.random.default_rng(42)
    tile_shade = (0.80 + 0.30 * rng.random(ids.max() + 1))[ids]
    tile_tint = (rng.random((ids.max() + 1, 3)) * 0.05 + 0.975)[ids]
    base = fbm(43, 1.3, 2.2)
    speck = smooth(pnoise(0.1, 44), 0.70, 0.90)
    veins = np.abs(np.sin((warp(pnoise(2.0, 45), 90, 46) * 14.0) * np.pi))
    veins = smooth(1 - veins, 0.90, 0.99)
    # cracks crossing tiles
    e2, _, _ = voronoi_edges(11, 47, jitter=1.0)
    cr = warp(1.0 - smooth(e2, 0.0, 0.0045), 80, 48) * smooth(pnoise(2.2, 49), 0.55, 0.75)
    dirt = smooth(fbm(50, 1.5, 2.3), 0.5, 0.85)
    wet = smooth(pnoise(2.7, 51), 0.58, 0.72)             # damp patches
    lum = (0.56 + 0.20 * (base - 0.5) * 2) * tile_shade
    lum *= 1 - 0.40 * dirt - 0.55 * cr - 0.85 * grout
    lum += 0.12 * speck - 0.10 * veins
    lum *= 1 - 0.35 * wet
    lum += (pnoise(0.2, 52) - 0.5) * 0.07
    rgb = lum[..., None] * tile_tint * np.array([1.0, 0.99, 0.96], np.float32)
    height = 0.45 * base + 0.20 * speck - 1.0 * grout - 0.8 * cr + 0.15 * pnoise(0.5, 53) - 0.1 * veins
    rough = 0.70 + 0.15 * (1 - base) - 0.45 * wet + 0.1 * grout - 0.1 * speck
    save('dirty_stone', rgb, height, rough, 8.0)

if __name__ == '__main__':
    import sys
    which = sys.argv[1:] or ['plaster', 'wood', 'stone']
    if 'plaster' in which: plaster()
    if 'wood' in which: wood()
    if 'stone' in which: stone()
    print('done')
