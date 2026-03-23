/*
** NetXMS Framebuffer Screenshot Subagent
** Copyright (C) 2003-2026 Victor Kirhenshtein
**
** This program is free software; you can redistribute it and/or modify
** it under the terms of the GNU General Public License as published by
** the Free Software Foundation; either version 2 of the License, or
** (at your option) any later version.
**
** This program is distributed in the hope that it will be useful,
** but WITHOUT ANY WARRANTY; without even the implied warranty of
** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
** GNU General Public License for more details.
**
** You should have received a copy of the GNU General Public License
** along with this program; if not, write to the Free Software
** Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
**
** File: fbdev.cpp
**
**/

#include <nms_common.h>
#include <nms_util.h>
#include <nms_agent.h>
#include <netxms-version.h>
#include <png.h>
#include <linux/fb.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <fcntl.h>
#include <unistd.h>

#define DEBUG_TAG _T("fbdev")

/**
 * Maximum number of framebuffer devices to scan
 */
#define MAX_FB_DEVICES 8

/**
 * Tracked framebuffer device
 */
struct FramebufferDevice
{
   TCHAR name[64];
   char devPath[64];
};

static ObjectArray<FramebufferDevice> s_devices(4, 4, Ownership::True);

/**
 * PNG write callback
 */
static void PngWriteData(png_structp png_ptr, png_bytep data, png_size_t length)
{
   static_cast<ByteStream*>(png_get_io_ptr(png_ptr))->write(data, length);
}

/**
 * PNG flush callback
 */
static void PngFlushData(png_structp png_ptr)
{
}

/**
 * Capture screenshot from framebuffer device
 */
static bool CaptureFramebuffer(const char *devPath, NXCPMessage *response)
{
   int fd = open(devPath, O_RDONLY);
   if (fd < 0)
   {
      nxlog_debug_tag(DEBUG_TAG, 4, _T("Cannot open %hs: %hs"), devPath, strerror(errno));
      response->setField(VID_RCC, ERR_INTERNAL_ERROR);
      return true;  // handled but failed
   }

   struct fb_var_screeninfo vinfo;
   struct fb_fix_screeninfo finfo;
   if (ioctl(fd, FBIOGET_VSCREENINFO, &vinfo) < 0 || ioctl(fd, FBIOGET_FSCREENINFO, &finfo) < 0)
   {
      nxlog_debug_tag(DEBUG_TAG, 4, _T("ioctl failed for %hs: %hs"), devPath, strerror(errno));
      close(fd);
      response->setField(VID_RCC, ERR_INTERNAL_ERROR);
      return true;
   }

   int width = static_cast<int>(vinfo.xres);
   int height = static_cast<int>(vinfo.yres);
   int bpp = static_cast<int>(vinfo.bits_per_pixel);
   size_t screenSize = finfo.smem_len;

   nxlog_debug_tag(DEBUG_TAG, 5, _T("Framebuffer %hs: %dx%d, %d bpp, %zu bytes"), devPath, width, height, bpp, screenSize);

   if (bpp != 32 && bpp != 24 && bpp != 16)
   {
      nxlog_debug_tag(DEBUG_TAG, 3, _T("Unsupported bpp %d for %hs"), bpp, devPath);
      close(fd);
      response->setField(VID_RCC, ERR_INTERNAL_ERROR);
      return true;
   }

   void *fbmem = mmap(nullptr, screenSize, PROT_READ, MAP_SHARED, fd, 0);
   if (fbmem == MAP_FAILED)
   {
      nxlog_debug_tag(DEBUG_TAG, 4, _T("mmap failed for %hs: %hs"), devPath, strerror(errno));
      close(fd);
      response->setField(VID_RCC, ERR_INTERNAL_ERROR);
      return true;
   }

   // Encode to PNG
   png_structp png_ptr = png_create_write_struct(PNG_LIBPNG_VER_STRING, nullptr, nullptr, nullptr);
   png_infop info_ptr = (png_ptr != nullptr) ? png_create_info_struct(png_ptr) : nullptr;
   ByteStream *pngData = nullptr;
   BYTE *rowBuf = nullptr;

   if (png_ptr == nullptr || info_ptr == nullptr)
   {
      if (png_ptr != nullptr)
         png_destroy_write_struct(&png_ptr, nullptr);
      munmap(fbmem, screenSize);
      close(fd);
      response->setField(VID_RCC, ERR_INTERNAL_ERROR);
      return true;
   }

   rowBuf = static_cast<BYTE*>(MemAlloc(width * 3));

   if (setjmp(png_jmpbuf(png_ptr)))
   {
      delete_and_null(pngData);
      goto cleanup;
   }

   png_set_IHDR(png_ptr, info_ptr, width, height, 8,
      PNG_COLOR_TYPE_RGB, PNG_INTERLACE_NONE,
      PNG_COMPRESSION_TYPE_DEFAULT, PNG_FILTER_TYPE_DEFAULT);

   pngData = new ByteStream(static_cast<size_t>(width * height * 3 + 4096));
   pngData->setAllocationStep(65536);
   png_set_write_fn(png_ptr, pngData, PngWriteData, PngFlushData);
   png_write_info(png_ptr, info_ptr);

   {
      int stride = static_cast<int>(finfo.line_length);

      for (int y = 0; y < height; y++)
      {
         const uint8_t *src = static_cast<const uint8_t*>(fbmem) + y * stride;

         if (bpp == 32)
         {
            // Use fb_var_screeninfo color offsets for correct pixel format
            int rOff = vinfo.red.offset / 8;
            int gOff = vinfo.green.offset / 8;
            int bOff = vinfo.blue.offset / 8;
            for (int x = 0; x < width; x++)
            {
               rowBuf[x * 3 + 0] = src[x * 4 + rOff];
               rowBuf[x * 3 + 1] = src[x * 4 + gOff];
               rowBuf[x * 3 + 2] = src[x * 4 + bOff];
            }
         }
         else if (bpp == 24)
         {
            int rOff = vinfo.red.offset / 8;
            int gOff = vinfo.green.offset / 8;
            int bOff = vinfo.blue.offset / 8;
            for (int x = 0; x < width; x++)
            {
               rowBuf[x * 3 + 0] = src[x * 3 + rOff];
               rowBuf[x * 3 + 1] = src[x * 3 + gOff];
               rowBuf[x * 3 + 2] = src[x * 3 + bOff];
            }
         }
         else  // bpp == 16, assume RGB565
         {
            for (int x = 0; x < width; x++)
            {
               uint16_t pixel = *reinterpret_cast<const uint16_t*>(src + x * 2);
               rowBuf[x * 3 + 0] = static_cast<BYTE>((pixel >> 8) & 0xF8);  // R
               rowBuf[x * 3 + 1] = static_cast<BYTE>((pixel >> 3) & 0xFC);  // G
               rowBuf[x * 3 + 2] = static_cast<BYTE>((pixel << 3) & 0xF8);  // B
            }
         }

         png_write_row(png_ptr, rowBuf);
      }
   }

   png_write_end(png_ptr, info_ptr);

cleanup:
   png_destroy_write_struct(&png_ptr, &info_ptr);
   MemFree(rowBuf);
   munmap(fbmem, screenSize);
   close(fd);

   if (pngData != nullptr && pngData->size() > 0)
   {
      response->setField(VID_RCC, ERR_SUCCESS);
      response->disableCompression();
      response->setField(VID_FILE_DATA, pngData->buffer(), pngData->size());
      delete pngData;
   }
   else
   {
      delete pngData;
      response->setField(VID_RCC, ERR_INTERNAL_ERROR);
   }
   return true;
}

/**
 * Screenshot provider callback
 */
static bool ScreenshotProvider(const TCHAR *sessionName, NXCPMessage *response)
{
   for (int i = 0; i < s_devices.size(); i++)
   {
      if (!_tcsicmp(s_devices.get(i)->name, sessionName))
      {
         return CaptureFramebuffer(s_devices.get(i)->devPath, response);
      }
   }
   return false;
}

/**
 * Handler for Framebuffer.Count parameter
 */
static LONG H_FramebufferCount(const TCHAR *param, const TCHAR *arg, TCHAR *value, AbstractCommSession *session)
{
   ret_int(value, s_devices.size());
   return SYSINFO_RC_SUCCESS;
}

/**
 * Subagent parameters
 */
static NETXMS_SUBAGENT_PARAM s_parameters[] =
{
   { _T("Framebuffer.Count"), H_FramebufferCount, nullptr, DCI_DT_INT, _T("Number of accessible framebuffer devices") }
};

/**
 * Subagent initialization
 */
static bool SubagentInit(Config *config)
{
   for (int i = 0; i < MAX_FB_DEVICES; i++)
   {
      char devPath[64];
      snprintf(devPath, sizeof(devPath), "/dev/fb%d", i);
      if (access(devPath, R_OK) == 0)
      {
         auto dev = new FramebufferDevice();
         _sntprintf(dev->name, 64, _T("fb%d"), i);
         strncpy(dev->devPath, devPath, sizeof(dev->devPath));
         dev->devPath[sizeof(dev->devPath) - 1] = 0;
         s_devices.add(dev);

         AgentRegisterScreenshotProvider(dev->name, ScreenshotProvider);
         nxlog_write_tag(NXLOG_INFO, DEBUG_TAG, _T("Registered framebuffer device %s (%hs)"), dev->name, devPath);
      }
   }

   if (s_devices.isEmpty())
   {
      nxlog_write_tag(NXLOG_WARNING, DEBUG_TAG, _T("No accessible framebuffer devices found"));
   }

   return true;
}

/**
 * Subagent shutdown
 */
static void SubagentShutdown()
{
   for (int i = 0; i < s_devices.size(); i++)
   {
      AgentUnregisterScreenshotProvider(s_devices.get(i)->name);
   }
}

/**
 * Subagent information
 */
static NETXMS_SUBAGENT_INFO s_info =
{
   NETXMS_SUBAGENT_INFO_MAGIC,
   _T("FBDEV"), NETXMS_VERSION_STRING,
   SubagentInit, SubagentShutdown,
   nullptr,  // command handler
   nullptr,  // notify
   nullptr,  // metric filter
   sizeof(s_parameters) / sizeof(NETXMS_SUBAGENT_PARAM), s_parameters,
   0, nullptr,  // lists
   0, nullptr,  // tables
   0, nullptr,  // actions
   0, nullptr,  // push parameters
   0, nullptr   // AI tools
};

/**
 * Entry point
 */
DECLARE_SUBAGENT_ENTRY_POINT(FBDEV)
{
   *ppInfo = &s_info;
   return true;
}
