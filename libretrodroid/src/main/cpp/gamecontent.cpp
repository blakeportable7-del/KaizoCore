/*
 *     KaizoCore addition (rc34, 2026-10-03) to LibretroDroid. GPL-3.0, as the rest of this module.
 */

#include "gamecontent.h"

#include <cerrno>
#include <cstring>
#include <fcntl.h>
#include <stdexcept>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

#include "log.h"

namespace libretrodroid {

GameContent::~GameContent() {
    release();
}

GameContent::GameContent(GameContent&& other) noexcept {
    *this = std::move(other);
}

GameContent& GameContent::operator=(GameContent&& other) noexcept {
    if (this != &other) {
        release();
        mapping = other.mapping;
        mappedLength = other.mappedLength;
        heap = std::move(other.heap);
        bytes = other.bytes;
        length = other.length;
        other.mapping = nullptr;
        other.mappedLength = 0;
        std::vector<char>().swap(other.heap);
        other.bytes = nullptr;
        other.length = 0;
    }
    return *this;
}

GameContent GameContent::fromPath(const std::string& path) {
    int fd = open(path.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        throw std::runtime_error(std::string("Cannot open the game: ") + strerror(errno));
    }
    return fromFd(fd);
}

GameContent GameContent::fromFd(int fd) {
    struct Closer {
        int fd;
        ~Closer() { if (fd >= 0) close(fd); }
    } closer { fd };

    struct stat st {};
    if (fstat(fd, &st) == 0 && S_ISREG(st.st_mode) && st.st_size > 0) {
        auto size = static_cast<size_t>(st.st_size);
        void* m = mmap(nullptr, size, PROT_READ, MAP_PRIVATE, fd, 0);
        if (m != MAP_FAILED) {
            // The core reads it once, front to back, while it copies it.
            madvise(m, size, MADV_SEQUENTIAL);
            GameContent content;
            content.mapping = m;
            content.mappedLength = size;
            content.bytes = m;
            content.length = size;
            return content;
        }
        LOGW("Cannot map the game (%s), reading it instead", strerror(errno));
    }

    // Not a plain file, or it would not map: read it to the end.
    std::vector<char> buffer;
    if (st.st_size > 0) buffer.reserve(static_cast<size_t>(st.st_size));
    char chunk[1 << 16];
    while (true) {
        ssize_t n = read(fd, chunk, sizeof(chunk));
        if (n == 0) break;
        if (n < 0) {
            if (errno == EINTR) continue;
            throw std::runtime_error(std::string("Cannot read the game: ") + strerror(errno));
        }
        buffer.insert(buffer.end(), chunk, chunk + n);
    }
    if (buffer.empty()) throw std::runtime_error("The game file is empty");
    return fromBytes(std::move(buffer));
}

GameContent GameContent::fromBytes(std::vector<char> bytes) {
    GameContent content;
    content.heap = std::move(bytes);
    content.bytes = content.heap.empty() ? nullptr : content.heap.data();
    content.length = content.heap.size();
    return content;
}

void GameContent::dropResidentPages() {
    if (mapping != nullptr) {
        // A private, read-only file mapping: the pages go back to the page cache, and a later read brings them back
        // from the file (madvise(2)).
        madvise(mapping, mappedLength, MADV_DONTNEED);
    }
}

void GameContent::release() {
    if (mapping != nullptr) {
        munmap(mapping, mappedLength);
        mapping = nullptr;
        mappedLength = 0;
    }
    // swap, not clear(): clear() keeps the capacity, and the capacity is the game.
    std::vector<char>().swap(heap);
    bytes = nullptr;
    length = 0;
}

}
