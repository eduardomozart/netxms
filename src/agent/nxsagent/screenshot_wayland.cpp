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
** File: screenshot_wayland.cpp
**
**/

#include "nxsagent.h"

#if HAVE_SDBUS

extern "C"
{
#include <systemd/sd-bus.h>
}

/**
 * Take screenshot via GNOME Shell D-Bus interface.
 * Uses org.gnome.Shell.Screenshot.Screenshot() method on the session bus.
 * Returns true if screenshot was taken successfully.
 */
bool TakeWaylandScreenshot(NXCPMessage *response)
{
   sd_bus *bus = nullptr;
   sd_bus_error error = SD_BUS_ERROR_NULL;
   sd_bus_message *reply = nullptr;

   int r = sd_bus_open_user(&bus);
   if (r < 0)
   {
      nxlog_debug(3, _T("TakeWaylandScreenshot: cannot open user session bus: %hs"), strerror(-r));
      return false;
   }

   // Create unique temp file for screenshot output
   char tmpPath[MAX_PATH];
   snprintf(tmpPath, sizeof(tmpPath), "/tmp/nxsagent-screenshot-%d.png", static_cast<int>(getpid()));

   // Call org.gnome.Shell.Screenshot.Screenshot(include_cursor, flash, filename)
   // Returns (success: boolean, filename_used: string)
   r = sd_bus_call_method(bus,
      "org.gnome.Shell.Screenshot",           // service
      "/org/gnome/Shell/Screenshot",           // path
      "org.gnome.Shell.Screenshot",            // interface
      "Screenshot",                            // method
      &error, &reply,
      "bbs",                                   // include_cursor, flash, filename
      0, 0, tmpPath);

   if (r < 0)
   {
      nxlog_debug(3, _T("TakeWaylandScreenshot: D-Bus Screenshot call failed: %hs"), error.message ? error.message : strerror(-r));
      sd_bus_error_free(&error);
      sd_bus_unref(bus);
      return false;
   }

   int success = 0;
   const char *filenameUsed = nullptr;
   r = sd_bus_message_read(reply, "bs", &success, &filenameUsed);
   if (r < 0 || !success)
   {
      nxlog_debug(3, _T("TakeWaylandScreenshot: Screenshot method returned failure (r=%d, success=%d)"), r, success);
      sd_bus_message_unref(reply);
      sd_bus_error_free(&error);
      sd_bus_unref(bus);
      unlink(tmpPath);
      return false;
   }

   nxlog_debug(5, _T("TakeWaylandScreenshot: screenshot saved to %hs"), filenameUsed);

   // Read the PNG file into response
   bool result = false;
   const char *readPath = (filenameUsed != nullptr && filenameUsed[0] != 0) ? filenameUsed : tmpPath;
   FILE *f = fopen(readPath, "rb");
   if (f != nullptr)
   {
      fseek(f, 0, SEEK_END);
      long size = ftell(f);
      fseek(f, 0, SEEK_SET);
      if (size > 0 && size < 64 * 1024 * 1024)  // 64MB sanity limit
      {
         ByteStream png(static_cast<size_t>(size));
         uint8_t buf[8192];
         size_t bytesRead;
         while ((bytesRead = fread(buf, 1, sizeof(buf), f)) > 0)
            png.write(buf, bytesRead);

         response->setField(VID_RCC, ERR_SUCCESS);
         response->setField(VID_FILE_DATA, png.buffer(), png.size());
         result = true;
      }
      else
      {
         nxlog_debug(3, _T("TakeWaylandScreenshot: invalid file size %ld"), size);
      }
      fclose(f);
   }
   else
   {
      nxlog_debug(3, _T("TakeWaylandScreenshot: cannot open screenshot file %hs: %hs"), readPath, strerror(errno));
   }

   unlink(readPath);
   if (strcmp(readPath, tmpPath) != 0)
      unlink(tmpPath);

   sd_bus_message_unref(reply);
   sd_bus_error_free(&error);
   sd_bus_unref(bus);
   return result;
}

#endif /* HAVE_SDBUS */
