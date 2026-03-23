/*
** NetXMS Session Agent
** Copyright (C) 2003-2026 Victor Kirhenshtein
**
** This program is free software; you can redistribute it and/or modify
** it under the terms of the GNU General Public License as published by
** the Free Software Foundation; either version 2 of the License, or
** (at your option) any later version.
**
** This program is distributed in the hope that it will be usefu,,
** but ITHOUT ANY WARRANTY; without even the implied warranty of
** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
** GNU General Public License for more details.
**
** You should have received a copy of the GNU General Public License
** along with this program; if not, write to the Free Software
** Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
**
** File: screenshot.cpp
**
**/

#include "nxsagent.h"

#ifdef _WIN32

/**
 * Take screenshot (Windows)
 */
void TakeScreenshot(NXCPMessage *response)
{
   uint32_t rcc = ERR_INTERNAL_ERROR;

   HDC dc = CreateDC(_T("DISPLAY"), nullptr, nullptr, nullptr);
   if (dc != nullptr)
   {
      HDC memdc = CreateCompatibleDC(dc);
      if (memdc != nullptr)
      {
         int x = GetSystemMetrics(SM_XVIRTUALSCREEN);
         int y = GetSystemMetrics(SM_YVIRTUALSCREEN);
         int cx = GetSystemMetrics(SM_CXVIRTUALSCREEN);
         int cy = GetSystemMetrics(SM_CYVIRTUALSCREEN);

         HBITMAP bitmap = CreateCompatibleBitmap(dc, cx, cy);
         if (bitmap != nullptr)
         {
            SelectObject(memdc, bitmap);
            BitBlt(memdc, 0, 0, cx, cy, dc, x, y, SRCCOPY | CAPTUREBLT);
         }

         DeleteDC(memdc);

         ByteStream *png = SaveBitmapToPng(bitmap);
         if (png != nullptr)
         {
            rcc = ERR_SUCCESS;
            response->setField(VID_FILE_DATA, png->buffer(), png->size());
            delete png;
         }
         DeleteObject(bitmap);
      }
      DeleteDC(dc);
   }

   response->setField(VID_RCC, rcc);
}

#else /* not _WIN32 */

#if HAVE_X11
#include <X11/Xlib.h>
#include <X11/Xutil.h>
#endif

#if HAVE_SDBUS
bool TakeWaylandScreenshot(NXCPMessage *response);
#endif

/**
 * Check if running in a Wayland session
 */
static bool IsWaylandSession()
{
   const char *sessionType = getenv("XDG_SESSION_TYPE");
   return (sessionType != nullptr) && !strcmp(sessionType, "wayland");
}

#if HAVE_X11

/**
 * Take screenshot via X11
 */
static bool TakeX11Screenshot(NXCPMessage *response)
{
   Display *display = XOpenDisplay(nullptr);
   if (display == nullptr)
   {
      nxlog_debug(3, _T("TakeX11Screenshot: XOpenDisplay failed"));
      return false;
   }

   Window root = DefaultRootWindow(display);
   XWindowAttributes attrs;
   if (!XGetWindowAttributes(display, root, &attrs))
   {
      nxlog_debug(3, _T("TakeX11Screenshot: XGetWindowAttributes failed"));
      XCloseDisplay(display);
      return false;
   }

   int width = attrs.width;
   int height = attrs.height;

   XImage *image = XGetImage(display, root, 0, 0, width, height, AllPlanes, ZPixmap);
   if (image == nullptr)
   {
      nxlog_debug(3, _T("TakeX11Screenshot: XGetImage failed"));
      XCloseDisplay(display);
      return false;
   }

   nxlog_debug(5, _T("TakeX11Screenshot: captured %dx%d image, depth=%d, bpp=%d"),
      width, height, image->depth, image->bits_per_pixel);

   bool success = false;
   if (image->bits_per_pixel == 32)
   {
      ByteStream *png = EncodePngFromPixels(
         reinterpret_cast<const uint8_t*>(image->data),
         width, height, image->bytes_per_line, false);
      if (png != nullptr)
      {
         response->setField(VID_RCC, ERR_SUCCESS);
         response->setField(VID_FILE_DATA, png->buffer(), png->size());
         delete png;
         success = true;
      }
   }
   else
   {
      nxlog_debug(3, _T("TakeX11Screenshot: unsupported bits_per_pixel %d"), image->bits_per_pixel);
   }

   XDestroyImage(image);
   XCloseDisplay(display);
   return success;
}

#endif /* HAVE_X11 */

/**
 * Take screenshot (Linux)
 */
void TakeScreenshot(NXCPMessage *response)
{
   bool success = false;

#if HAVE_SDBUS
   if (IsWaylandSession())
   {
      nxlog_debug(5, _T("TakeScreenshot: attempting Wayland capture"));
      success = TakeWaylandScreenshot(response);
   }
#endif

#if HAVE_X11
   if (!success)
   {
      nxlog_debug(5, _T("TakeScreenshot: attempting X11 capture"));
      success = TakeX11Screenshot(response);
   }
#endif

   if (!success)
   {
      response->setField(VID_RCC, ERR_INTERNAL_ERROR);
   }
}

#endif /* _WIN32 */
