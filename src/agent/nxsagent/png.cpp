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
** File: png.cpp
**
**/

#include "nxsagent.h"
#include <png.h>

/**
 * Write PNG data
 */
static void WriteData(png_structp png_ptr, png_bytep data, png_size_t length)
{
   static_cast<ByteStream*>(png_get_io_ptr(png_ptr))->write(data, length);
}

/**
 * Flush write buffer
 */
static void FlushBuffer(png_structp png_ptr)
{
}

/**
 * Encode raw BGRA pixel buffer to PNG.
 * pixels - raw pixel data in BGRA format (4 bytes per pixel)
 * width, height - image dimensions
 * stride - bytes per row (may include padding)
 * bottomUp - if true, rows are stored bottom-to-top (Windows DIB format)
 * Returns ByteStream with PNG data or nullptr on failure.
 */
ByteStream *EncodePngFromPixels(const uint8_t *pixels, int width, int height, int stride, bool bottomUp)
{
   png_structp png_ptr = png_create_write_struct(PNG_LIBPNG_VER_STRING, nullptr, nullptr, nullptr);
   if (png_ptr == nullptr)
      return nullptr;

   png_infop info_ptr = png_create_info_struct(png_ptr);
   if (info_ptr == nullptr)
   {
      png_destroy_write_struct(&png_ptr, nullptr);
      return nullptr;
   }

   ByteStream *pngData = nullptr;
   BYTE *rowBuf = static_cast<BYTE*>(MemAlloc(width * 3));
   if (rowBuf == nullptr)
   {
      png_destroy_write_struct(&png_ptr, &info_ptr);
      return nullptr;
   }

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
   png_set_write_fn(png_ptr, pngData, WriteData, FlushBuffer);
   png_write_info(png_ptr, info_ptr);

   for (int y = 0; y < height; y++)
   {
      int srcRow = bottomUp ? (height - y - 1) : y;
      const uint8_t *src = pixels + srcRow * stride;

      // Convert BGRA to RGB
      for (int x = 0; x < width; x++)
      {
         rowBuf[x * 3 + 0] = src[x * 4 + 2]; // R (from B position in BGRA)
         rowBuf[x * 3 + 1] = src[x * 4 + 1]; // G
         rowBuf[x * 3 + 2] = src[x * 4 + 0]; // B (from R position in BGRA)
      }
      png_write_row(png_ptr, rowBuf);
   }

   png_write_end(png_ptr, info_ptr);

cleanup:
   png_destroy_write_struct(&png_ptr, &info_ptr);
   MemFree(rowBuf);
   return pngData;
}

#ifdef _WIN32

/**
 * Save given bitmap as PNG (Windows-specific)
 */
ByteStream *SaveBitmapToPng(HBITMAP hBitmap)
{
   BITMAP bitmap;
   GetObject(hBitmap, sizeof(bitmap), (LPSTR)&bitmap);

   DWORD scanlineSize = ((bitmap.bmWidth * 4) + (4 - 1)) & ~(4 - 1);
   DWORD bufferSize = scanlineSize * bitmap.bmHeight;
   BYTE *buffer = static_cast<BYTE*>(MemAlloc(bufferSize));
   if (buffer == nullptr)
      return nullptr;

   HDC hDC = GetDC(nullptr);

   BITMAPINFO bitmapInfo;
   memset(&bitmapInfo, 0, sizeof(bitmapInfo));
   bitmapInfo.bmiHeader.biSize = sizeof(bitmapInfo.bmiHeader);
   bitmapInfo.bmiHeader.biWidth = bitmap.bmWidth;
   bitmapInfo.bmiHeader.biHeight = bitmap.bmHeight;
   bitmapInfo.bmiHeader.biPlanes = 1;
   bitmapInfo.bmiHeader.biBitCount = 32;
   bitmapInfo.bmiHeader.biCompression = BI_RGB;
   bitmapInfo.bmiHeader.biClrUsed = 0;
   bitmapInfo.bmiHeader.biClrImportant = 0;
   if (!GetDIBits(hDC, hBitmap, 0, bitmap.bmHeight, buffer, &bitmapInfo, DIB_RGB_COLORS))
   {
      ReleaseDC(nullptr, hDC);
      MemFree(buffer);
      return nullptr;
   }

   ReleaseDC(nullptr, hDC);

   ByteStream *pngData = EncodePngFromPixels(buffer, bitmap.bmWidth, bitmap.bmHeight,
      static_cast<int>(scanlineSize), true);

   MemFree(buffer);
   return pngData;
}

#endif /* _WIN32 */
