/*
 * KaizoCore (2026-10-23): see seconddisplay.h.
 */
#include "seconddisplay.h"
#include "log.h"

#include <algorithm>

namespace libretrodroid {

static const char* VERTEX = R"(
attribute vec4 vPosition;
attribute vec2 vCoordinate;
varying vec2 coordinate;
void main() {
    gl_Position = vPosition;
    coordinate = vCoordinate;
}
)";

static const char* FRAGMENT = R"(
precision mediump float;
uniform sampler2D texture;
varying vec2 coordinate;
void main() {
    gl_FragColor = vec4(texture2D(texture, coordinate).rgb, 1.0);
}
)";

static GLuint compile(GLenum type, const char* source) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);
    GLint ok = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        LOGE("second display: shader did not compile");
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

SecondDisplay::~SecondDisplay() {
    set(nullptr, 0.0f, 0.0f);
}

/** At most this many EGL refusals in a row before the window is given up on, until the next set. */
static const int MAX_FAILURES = 5;
/** Frames between tries after a refusal (about half a second). */
static const int RETRY_FRAMES = 30;

void SecondDisplay::releaseSurface() {
    if (surface != EGL_NO_SURFACE) {
        // Never current here (draw restores the main surface first), so it goes at once. A display torn down with the
        // old context already freed it; the error is ignored.
        eglDestroySurface(display, surface);
        surface = EGL_NO_SURFACE;
    }
    surfaceContext = EGL_NO_CONTEXT;
}

void SecondDisplay::set(ANativeWindow* newWindow, float newTop, float newBottom) {
    std::lock_guard<std::mutex> lock(mutex);
    releaseSurface();
    if (window != nullptr) {
        ANativeWindow_release(window);
        window = nullptr;
    }
    top = std::clamp(newTop, 0.0f, 1.0f);
    bottom = std::clamp(newBottom, top, 1.0f);
    failures = 0;
    retryIn = 0;
    frames.store(0);
    window = newWindow;
    if (window != nullptr) {
        LOGI("second display: window set (%d x %d), rows %.2f to %.2f", ANativeWindow_getWidth(window),
             ANativeWindow_getHeight(window), top, bottom);
    } else {
        LOGI("second display: off");
    }
}

bool SecondDisplay::wanted() {
    std::lock_guard<std::mutex> lock(mutex);
    return window != nullptr && frames.load() >= 0;
}

void SecondDisplay::failed(const char* what, EGLint error) {
    releaseSurface();
    failures++;
    if (failures >= MAX_FAILURES) {
        LOGE("second display: %s (0x%x), given up after %d tries", what, error, failures);
        frames.store(-1);
    } else {
        LOGE("second display: %s (0x%x), trying again", what, error);
        retryIn = RETRY_FRAMES;
    }
}

bool SecondDisplay::makeSurface(EGLDisplay currentDisplay, EGLContext context) {
    // The same config as the context, so the context can be made current on the new surface.
    EGLint configId = 0;
    eglQueryContext(currentDisplay, context, EGL_CONFIG_ID, &configId);
    EGLint attributes[] = { EGL_CONFIG_ID, configId, EGL_NONE };
    EGLConfig config;
    EGLint count = 0;
    if (!eglChooseConfig(currentDisplay, attributes, &config, 1, &count) || count < 1) {
        failed("no config for the context", eglGetError());
        return false;
    }
    EGLSurface newSurface = eglCreateWindowSurface(currentDisplay, config, window, nullptr);
    if (newSurface == EGL_NO_SURFACE) {
        failed("eglCreateWindowSurface failed", eglGetError());
        return false;
    }
    display = currentDisplay;
    surface = newSurface;
    surfaceContext = context;
    LOGI("second display: surface made, rows %.2f to %.2f", top, bottom);
    return true;
}

bool SecondDisplay::ensureProgram(EGLContext context) {
    // A program belongs to the context it was made in; a new context needs its own (the old one went with its context).
    if (programContext != context) {
        program = 0;
        programContext = context;
    }
    if (program != 0) return true;
    GLuint vertex = compile(GL_VERTEX_SHADER, VERTEX);
    GLuint fragment = compile(GL_FRAGMENT_SHADER, FRAGMENT);
    if (vertex == 0 || fragment == 0) return false;
    program = glCreateProgram();
    glAttachShader(program, vertex);
    glAttachShader(program, fragment);
    glLinkProgram(program);
    glDeleteShader(vertex);
    glDeleteShader(fragment);
    GLint ok = 0;
    glGetProgramiv(program, GL_LINK_STATUS, &ok);
    if (!ok) {
        glDeleteProgram(program);
        program = 0;
        return false;
    }
    positionHandle = glGetAttribLocation(program, "vPosition");
    coordinateHandle = glGetAttribLocation(program, "vCoordinate");
    textureHandle = glGetUniformLocation(program, "texture");
    return true;
}

void SecondDisplay::draw(GLuint texture, const std::array<float, 12>& vertices, const std::array<float, 12>& coordinates) {
    std::lock_guard<std::mutex> lock(mutex);
    if (window == nullptr || frames.load() < 0) return;

    EGLDisplay currentDisplay = eglGetCurrentDisplay();
    EGLContext context = eglGetCurrentContext();
    EGLSurface mainDraw = eglGetCurrentSurface(EGL_DRAW);
    EGLSurface mainRead = eglGetCurrentSurface(EGL_READ);
    if (currentDisplay == EGL_NO_DISPLAY || context == EGL_NO_CONTEXT || mainDraw == EGL_NO_SURFACE) return;

    // A surface made with another context (GLSurfaceView made a new one) is made again with this one.
    if (surface != EGL_NO_SURFACE && (context != surfaceContext || currentDisplay != display)) releaseSurface();
    if (surface == EGL_NO_SURFACE) {
        if (retryIn > 0) { retryIn--; return; }
        if (!makeSurface(currentDisplay, context)) return;
        if (!eglMakeCurrent(display, surface, surface, context)) {
            EGLint error = eglGetError();
            eglMakeCurrent(currentDisplay, mainDraw, mainRead, context);
            failed("eglMakeCurrent failed", error);
            return;
        }
        // Never wait for the second display: a frame it has no room for is dropped, the game is not held back.
        eglSwapInterval(display, 0);
    } else if (!eglMakeCurrent(display, surface, surface, context)) {
        EGLint error = eglGetError();
        eglMakeCurrent(currentDisplay, mainDraw, mainRead, context);
        failed("eglMakeCurrent failed", error);
        return;
    }

    // Each corner of the main quad keeps its texture u; its v is moved into the band: a corner on the frame's top edge
    // (vertex y > 0) takes the band's top, one on the bottom edge the band's bottom. Orientation-free: the frame's
    // top is wherever the main draw puts it.
    float vTop = 0, vBottom = 0;
    int nTop = 0, nBottom = 0;
    for (int i = 0; i < 6; i++) {
        if (vertices[i * 2 + 1] > 0) { vTop += coordinates[i * 2 + 1]; nTop++; }
        else { vBottom += coordinates[i * 2 + 1]; nBottom++; }
    }
    if (nTop > 0) vTop /= nTop;
    if (nBottom > 0) vBottom /= nBottom;
    std::array<float, 12> quad {};
    std::array<float, 12> coords {};
    for (int i = 0; i < 6; i++) {
        bool isTop = vertices[i * 2 + 1] > 0;
        quad[i * 2] = vertices[i * 2] > 0 ? 1.0f : -1.0f;
        quad[i * 2 + 1] = isTop ? 1.0f : -1.0f;
        coords[i * 2] = coordinates[i * 2];
        float fraction = isTop ? top : bottom;
        coords[i * 2 + 1] = vTop + (vBottom - vTop) * fraction;
    }

    EGLint width = 0, height = 0;
    eglQuerySurface(display, surface, EGL_WIDTH, &width);
    eglQuerySurface(display, surface, EGL_HEIGHT, &height);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glViewport(0, 0, width, height);
    glClearColor(0, 0, 0, 1);
    glClear(GL_COLOR_BUFFER_BIT);
    // The band at the DS screen's 4:3, as large as fits, centred.
    // The core's DS frame stacks two 256x192 screens (256x384); the band is its width by (bottom - top) of its height.
    float aspect = (256.0f / 384.0f) / std::max(0.05f, bottom - top);
    int w = width, h = height;
    if (width > height * aspect) w = (int) (height * aspect); else h = (int) (width / aspect);
    glViewport((width - w) / 2, (height - h) / 2, w, h);

    if (ensureProgram(context)) {
        glUseProgram(program);
        glVertexAttribPointer(positionHandle, 2, GL_FLOAT, GL_FALSE, 0, quad.data());
        glEnableVertexAttribArray(positionHandle);
        glVertexAttribPointer(coordinateHandle, 2, GL_FLOAT, GL_FALSE, 0, coords.data());
        glEnableVertexAttribArray(coordinateHandle);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture);
        glUniform1i(textureHandle, 0);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glDisableVertexAttribArray(positionHandle);
        glDisableVertexAttribArray(coordinateHandle);
        glBindTexture(GL_TEXTURE_2D, 0);
        glUseProgram(0);
    }
    bool swapped = eglSwapBuffers(display, surface);
    EGLint swapError = swapped ? EGL_SUCCESS : eglGetError();
    eglMakeCurrent(currentDisplay, mainDraw, mainRead, context);
    if (swapped) {
        frames++;
        failures = 0;
    } else {
        // The window went away under it (the display removed before the app heard): made again on a later frame, or
        // given up on until the next set.
        failed("eglSwapBuffers failed", swapError);
    }
}

}
