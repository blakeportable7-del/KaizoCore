/*
 *     KaizoCore addition (rc34, 2026-10-03) to LibretroDroid. GPL-3.0, as the rest of this module.
 */

#ifndef LIBRETRODROID_GAMECONTENT_H
#define LIBRETRODROID_GAMECONTENT_H

#include <cstddef>
#include <string>
#include <vector>

namespace libretrodroid {

/**
 * KaizoCore addition: the game's bytes for a core that takes its content as data (need_fullpath false).
 *
 * A core copies what it plays from (melonDS into its own cartridge ROM), and LibretroDroid used to read the whole
 * file into the heap and hold that beside the core's copy until the core was torn down: 512 MB twice for Black 2 and
 * White 2, 1.07 GB of native heap on 2026-10-03. The bytes cannot simply go once retro_load_game returns, which is
 * what libretro.h promises a core that does not ask for persistent data: the classic melonDS core keeps the
 * retro_game_info pointer and reads the ROM through it again on retro_reset (its retro_reset is NDS::Reset() then
 * NDS::LoadROM(cached_info->data, cached_info->size, ...)).
 *
 * So the file is mapped read-only instead of read, and dropResidentPages() hands the mapping's pages back once the
 * core has its copy. The pointer stays valid for the whole game; a later read (a reset) maps the pages in again from
 * the file. The mapping costs page cache, which the system drops when it needs the room, never anonymous memory,
 * and loading a 512 MB game no longer makes a second anonymous 512 MB beside the core's. A file that cannot be
 * mapped (a pipe from a content provider) is read into the heap and kept, as before. release(), or the destructor,
 * gives back either one.
 */
class GameContent {
public:
    GameContent() = default;
    ~GameContent();
    GameContent(GameContent&& other) noexcept;
    GameContent& operator=(GameContent&& other) noexcept;
    GameContent(const GameContent&) = delete;
    GameContent& operator=(const GameContent&) = delete;

    /** The whole file at [path]. Throws std::runtime_error when it cannot be opened or read. */
    static GameContent fromPath(const std::string& path);

    /** The whole file behind [fd], which this takes over and closes, whatever happens. */
    static GameContent fromFd(int fd);

    /** Bytes already in memory (a game handed over as a byte array), owned from now on. */
    static GameContent fromBytes(std::vector<char> bytes);

    const void* data() const { return bytes; }
    size_t size() const { return length; }
    bool isMapped() const { return mapping != nullptr; }

    /**
     * The mapping's pages leave this process; they stay in the page cache, and data() stays valid: the next read
     * maps them in again from the file. Call it once the core has copied what it keeps. A heap copy stays as it is.
     */
    void dropResidentPages();

    /** Unmaps or frees the bytes; data() is null and size() 0 afterwards. Safe to call again. */
    void release();

private:
    void* mapping = nullptr;      // from mmap, or null
    size_t mappedLength = 0;
    std::vector<char> heap;       // the read() fallback or handed-over bytes, or empty
    const void* bytes = nullptr;
    size_t length = 0;
};

}

#endif //LIBRETRODROID_GAMECONTENT_H
