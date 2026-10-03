"""Check the native libraries in a built APK. Usage: python tools/check_apk_libs.py <apk>

Fails (exit 1) when a 64-bit library has a LOAD segment aligned below 16 KB, which a 16 KB page phone cannot load
(rc32 audit P3 #11), or when liblibretrodroid.so still carries its debug info (P2 #4, P3 #10). The emulator cores are
shipped unstripped on purpose (app/build.gradle.kts keepDebugSymbols), so only their alignment is checked.
"""
import struct
import sys
import zipfile

PT_LOAD = 1
STRIPPED = ('liblibretrodroid.so',)


def elf_facts(data):
    """(lowest LOAD alignment, section names) of a 64-bit little-endian ELF."""
    if data[:4] != b'\x7fELF' or data[4] != 2 or data[5] != 1:
        raise ValueError('not a 64-bit little-endian ELF')
    e_phoff, = struct.unpack_from('<Q', data, 0x20)
    e_shoff, = struct.unpack_from('<Q', data, 0x28)
    e_phentsize, e_phnum, e_shentsize, e_shnum, e_shstrndx = struct.unpack_from('<HHHHH', data, 0x36)
    aligns = []
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        p_type, = struct.unpack_from('<I', data, off)
        if p_type == PT_LOAD:
            p_align, = struct.unpack_from('<Q', data, off + 0x30)
            aligns.append(p_align)
    names = []
    if e_shoff and e_shnum and e_shstrndx < e_shnum:
        stro, = struct.unpack_from('<Q', data, e_shoff + e_shstrndx * e_shentsize + 0x18)
        for i in range(e_shnum):
            name_off, = struct.unpack_from('<I', data, e_shoff + i * e_shentsize)
            end = data.index(b'\0', stro + name_off)
            names.append(data[stro + name_off:end].decode('ascii', 'replace'))
    return (min(aligns) if aligns else 0), names


def main(apk):
    bad = []
    with zipfile.ZipFile(apk) as z:
        for info in sorted(z.infolist(), key=lambda i: i.filename):
            n = info.filename
            if not (n.startswith('lib/') and n.endswith('.so')):
                continue
            abi = n.split('/')[1]
            data = z.read(n)
            align, names = elf_facts(data)
            debug = '.debug_info' in names
            print('%-48s %9d bytes  align %6d  %s' % (n, len(data), align, 'debug info' if debug else 'stripped'))
            if abi in ('arm64-v8a', 'x86_64') and align < 16384:
                bad.append('%s: LOAD aligned to %d, a 16 KB page phone cannot load it' % (n, align))
            if n.split('/')[-1] in STRIPPED and debug:
                bad.append('%s: still carries its debug info' % n)
    for b in bad:
        print('FAIL', b)
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1]))
