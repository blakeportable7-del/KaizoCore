// LOCAL MODIFICATION (KaizoCore, rc32 audit P3 #93): a range test that cannot wrap.
//
// `address + length <= start + size` overflows for an address near 2^64 (a jlong of -1 becomes
// 0xFFFFFFFFFFFFFFFF), passes, and the copy then reads or writes 32 MB before the buffer. Written as
// differences, nothing here can wrap.
#ifndef LIBRETRODROID_MEMRANGE_H
#define LIBRETRODROID_MEMRANGE_H

#include <cstdint>

namespace libretrodroid {

inline bool rangeInside(uint64_t address, uint64_t length, uint64_t start, uint64_t size) {
    return address >= start && length <= size && address - start <= size - length;
}

}

#endif //LIBRETRODROID_MEMRANGE_H
