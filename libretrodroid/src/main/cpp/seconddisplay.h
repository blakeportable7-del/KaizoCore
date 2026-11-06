/*
 * KaizoCore (2026-10-23): a DS's bottom screen on a second display (a dual-screen handheld such as the AYN Thor). The
 * core still draws one frame holding both screens; this draws a band of that frame's texture to a second window surface
 * (an Android Presentation's SurfaceView on the other display), on the GL thread, with the same context, right after
 * the main frame. The main surface is made current again before the frame ends, so GLSurfaceView's swap is unchanged.
 *
 * 2026-11-06 (Thor test build): the window is kept, and its EGL surface is made at the next frame drawn, with whatever
 * context is current then. Set while the app was in the background (no context current, the main surface gone) it used
 * to be dropped, so the bottom screen never came back after the app returned. A new context (GLSurfaceView lost the
 * old one) makes a new surface and program. LibretroDroid owns this object, not Video, so a new Video keeps it.
 */
#ifndef LIBRETRODROID_SECONDDISPLAY_H
#define LIBRETRODROID_SECONDDISPLAY_H

#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <android/native_window.h>
#include <array>
#include <atomic>
#include <mutex>

namespace libretrodroid {

class SecondDisplay {
public:
    ~SecondDisplay();

    /**
     * Shows rows [top, bottom) of the frame (fractions, 0 the frame's top) on [window], from the next frame drawn;
     * null [window] stops. Takes [window]'s reference. Any thread that does not have this surface current.
     */
    void set(ANativeWindow* window, float top, float bottom);

    /** A window is set and has not been given up on. */
    bool wanted();

    /** Draws the band of [texture] that the main frame draws with [vertices] and [coordinates] (two triangles each). */
    void draw(GLuint texture, const std::array<float, 12>& vertices, const std::array<float, 12>& coordinates);

    /** Frames drawn on the window since it was set; 0 with none set; -1 once it was given up on (EGL refused it). */
    long framesDrawn() const { return frames.load(); }

private:
    void releaseSurface();
    bool makeSurface(EGLDisplay currentDisplay, EGLContext context);
    bool ensureProgram(EGLContext context);
    void failed(const char* what, EGLint error);

    std::mutex mutex;
    ANativeWindow* window = nullptr;
    EGLDisplay display = EGL_NO_DISPLAY;
    EGLSurface surface = EGL_NO_SURFACE;
    EGLContext surfaceContext = EGL_NO_CONTEXT;
    EGLContext programContext = EGL_NO_CONTEXT;
    GLuint program = 0;
    GLint positionHandle = -1;
    GLint coordinateHandle = -1;
    GLint textureHandle = -1;
    float top = 0.5f;
    float bottom = 1.0f;
    /** EGL refusals since the window was set, and frames to wait before the next try. */
    int failures = 0;
    int retryIn = 0;
    std::atomic<long> frames { 0 };
};

}

#endif
