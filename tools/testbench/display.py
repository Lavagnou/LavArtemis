"""Is the console display on?  Qt Quick stops laying windows out when Windows switches the display off, which
turns a run into a flaky red (see README, "Ecran allume").

GUID_CONSOLE_DISPLAY_STATE cannot be polled: it is delivered as a WM_POWERBROADCAST / PBT_POWERSETTINGCHANGE
message to a window registered with RegisterPowerSettingNotification, and Windows sends the *current* value
right after the registration.  So: a message-only window, register, pump messages for a moment, read the value
(0 off, 1 on, 2 dimmed).  No answer in time = unknown = treated as on (never refuse on a guess).
This only reads the state; it never changes it (no SetThreadExecutionState, no wake-up)."""
import ctypes
import sys
import time
from ctypes import wintypes

OFF, ON, DIMMED = 0, 1, 2
_WM_POWERBROADCAST, _PBT_POWERSETTINGCHANGE, _HWND_MESSAGE = 0x218, 0x8013, -3


class _GUID(ctypes.Structure):
    _fields_ = [("a", ctypes.c_uint32), ("b", ctypes.c_uint16), ("c", ctypes.c_uint16), ("d", ctypes.c_ubyte * 8)]


class _PowerSetting(ctypes.Structure):
    _fields_ = [("guid", _GUID), ("size", wintypes.DWORD), ("data", ctypes.c_ubyte * 4)]


_CONSOLE_DISPLAY_STATE = _GUID(0x6fe69556, 0x704a, 0x47a0, (ctypes.c_ubyte * 8)(0x8f, 0x24, 0xc2, 0x8d, 0x93, 0x6f, 0xda, 0x47))


def display_state(timeout=1.5):
    """OFF / ON / DIMMED, or None when Windows did not say (non-Windows, no answer)."""
    if sys.platform != "win32":
        return None
    u32, k32 = ctypes.windll.user32, ctypes.windll.kernel32
    LRESULT = ctypes.c_ssize_t
    WNDPROC = ctypes.WINFUNCTYPE(LRESULT, wintypes.HWND, ctypes.c_uint, wintypes.WPARAM, wintypes.LPARAM)
    u32.DefWindowProcW.restype = LRESULT
    u32.DefWindowProcW.argtypes = [wintypes.HWND, ctypes.c_uint, wintypes.WPARAM, wintypes.LPARAM]
    u32.UnregisterClassW.argtypes = [wintypes.LPCWSTR, wintypes.HINSTANCE]
    u32.DestroyWindow.argtypes = [wintypes.HWND]
    u32.PeekMessageW.argtypes = [ctypes.POINTER(wintypes.MSG), wintypes.HWND, ctypes.c_uint, ctypes.c_uint, ctypes.c_uint]
    u32.DispatchMessageW.argtypes = [ctypes.POINTER(wintypes.MSG)]
    got = []

    def proc(hwnd, msg, wp, lp):
        if msg == _WM_POWERBROADCAST and wp == _PBT_POWERSETTINGCHANGE and lp:
            ps = ctypes.cast(lp, ctypes.POINTER(_PowerSetting)).contents
            if bytes(ps.guid.d) == bytes(_CONSOLE_DISPLAY_STATE.d) and ps.guid.a == _CONSOLE_DISPLAY_STATE.a:
                got.append(int.from_bytes(bytes(ps.data), "little"))
            return 1
        return u32.DefWindowProcW(hwnd, msg, wp, lp)

    cb = WNDPROC(proc)

    class WNDCLASS(ctypes.Structure):
        _fields_ = [("style", ctypes.c_uint), ("lpfnWndProc", WNDPROC), ("cbClsExtra", ctypes.c_int),
                    ("cbWndExtra", ctypes.c_int), ("hInstance", wintypes.HINSTANCE), ("hIcon", wintypes.HANDLE),
                    ("hCursor", wintypes.HANDLE), ("hbrBackground", wintypes.HANDLE),
                    ("lpszMenuName", wintypes.LPCWSTR), ("lpszClassName", wintypes.LPCWSTR)]

    k32.GetModuleHandleW.restype = wintypes.HINSTANCE
    hinst = k32.GetModuleHandleW(None)
    name = f"LavTestbenchDisplayProbe{time.monotonic_ns()}"
    wc = WNDCLASS(0, cb, 0, 0, hinst, None, None, None, None, name)
    u32.RegisterClassW.argtypes = [ctypes.POINTER(WNDCLASS)]
    if not u32.RegisterClassW(ctypes.byref(wc)):
        return None
    u32.CreateWindowExW.restype = wintypes.HWND
    u32.CreateWindowExW.argtypes = [wintypes.DWORD, wintypes.LPCWSTR, wintypes.LPCWSTR, wintypes.DWORD,
                                    ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_int, wintypes.HWND,
                                    wintypes.HANDLE, wintypes.HINSTANCE, wintypes.LPVOID]
    hwnd = u32.CreateWindowExW(0, name, name, 0, 0, 0, 0, 0, _HWND_MESSAGE, None, hinst, None)
    if not hwnd:
        u32.UnregisterClassW(name, hinst)
        return None
    u32.RegisterPowerSettingNotification.restype = wintypes.HANDLE
    u32.RegisterPowerSettingNotification.argtypes = [wintypes.HANDLE, ctypes.POINTER(_GUID), wintypes.DWORD]
    u32.UnregisterPowerSettingNotification.argtypes = [wintypes.HANDLE]
    reg = u32.RegisterPowerSettingNotification(hwnd, ctypes.byref(_CONSOLE_DISPLAY_STATE), 0)  # DEVICE_NOTIFY_WINDOW_HANDLE
    try:
        if reg:
            msg = wintypes.MSG()
            end = time.monotonic() + timeout
            while not got and time.monotonic() < end:
                while u32.PeekMessageW(ctypes.byref(msg), hwnd, 0, 0, 1):
                    u32.DispatchMessageW(ctypes.byref(msg))
                time.sleep(0.02)
    finally:
        if reg:
            u32.UnregisterPowerSettingNotification(reg)
        u32.DestroyWindow(hwnd)
        u32.UnregisterClassW(name, hinst)
    return got[0] if got else None


def display_is_off():
    return display_state() == OFF


if __name__ == "__main__":
    print({OFF: "off", ON: "on", DIMMED: "dimmed", None: "unknown"}[display_state()])
