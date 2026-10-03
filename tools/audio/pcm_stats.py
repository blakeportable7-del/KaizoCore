"""What the DS core's raw sound carries: its resolution, its spectrum, and the floor between notes."""
import sys
import numpy as np
from scipy import signal

RATE = 32824
for path in sys.argv[1:]:
    a = np.fromfile(path, dtype='<i2').reshape(-1, 2).astype(np.float64)
    L = a[:, 0]
    nz = L[L != 0]
    print('==', path.split('/')[-1], '%.1f s' % (len(L) / RATE))
    print('  level %.1f dBFS, peak %d, distinct values %d' % (10 * np.log10(np.mean(L * L) / 32768.0 ** 2), np.abs(L).max(), len(np.unique(L))))
    for q in (64, 32, 16, 8, 4, 2):
        print('  share of non-zero samples on multiples of %2d: %.4f' % (q, np.mean(np.mod(nz, q) == 0)))
    f, p = signal.welch(L, RATE, nperseg=8192)
    tot = p.sum()
    bands = [(0, 2000), (2000, 4000), (4000, 8000), (8000, 12000), (12000, 16412)]
    print('  energy by band (0-2k, 2-4k, 4-8k, 8-12k, 12-16.4k), dB of the whole: ' +
          ' '.join('%6.1f' % (10 * np.log10(p[(f >= lo) & (f < hi)].sum() / tot)) for lo, hi in bands))
    # The floor between notes: the quietest tenth of 50 ms windows, and how flat (noise-like) its spectrum is.
    w = RATE // 20
    lv = np.array([np.mean(L[i:i + w] ** 2) for i in range(0, len(L) - w, w)])
    quiet = np.argsort(lv)[: max(1, len(lv) // 10)]
    floor = np.concatenate([L[i * w:(i + 1) * w] for i in quiet])
    print('  quietest tenth of 50 ms windows: %.1f dBFS' % (10 * np.log10(max(np.mean(floor ** 2), 1e-12) / 32768.0 ** 2)))
