/**
 * NetXMS - open source network management system
 * Copyright (C) 2003-2026 Raden Solutions
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.netxms.nxmc.base.login;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
// com.sun.net.httpserver is part of the JDK standard library (module jdk.httpserver) since Java 6
// and is reliably available on JDK 17+ as required by this project.
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for CAS Single Sign-On authentication in the desktop client.
 *
 * Flow:
 *  1. Start a minimal HTTP server on 127.0.0.1 with an OS-assigned ephemeral port (port 0).
 *  2. Open the system browser to the CAS login URL with service=http://127.0.0.1:&lt;port&gt;/cas-callback.
 *  3. Wait for the browser to redirect back with the Service Ticket (ST-...).
 *  4. Shut down the local server and return the ticket and service URL to the caller.
 */
public class CASLoginHelper
{
   private static final Logger logger = LoggerFactory.getLogger(CASLoginHelper.class);

   /** Maximum seconds to wait for the browser to return the CAS ticket. */
   private static final int TICKET_WAIT_TIMEOUT_SECONDS = 120;

   /** Path on which the local HTTP server listens for the CAS callback. */
   private static final String CALLBACK_PATH = "/cas/callback";

   /**
    * Result of a CAS authentication attempt.
    */
   public static class CASResult
   {
      /** CAS Service Ticket (ST-...) returned by the CAS server. */
      public final String ticket;

      /** The service URL that was registered with CAS (must be sent to the NetXMS server for validation). */
      public final String serviceUrl;

      CASResult(String ticket, String serviceUrl)
      {
         this.ticket = ticket;
         this.serviceUrl = serviceUrl;
      }
   }

   /**
    * Perform CAS authentication by opening the system browser and waiting for the CAS callback.
    *
    * @param casBaseUrl base URL of the CAS server (e.g. {@code https://cas.example.com/cas})
    * @return {@link CASResult} with ticket and service URL on success, or {@code null} if the
    *         flow was cancelled, timed out, or encountered an error
    */
   public static CASResult performCASLogin(String casBaseUrl)
   {
      if ((casBaseUrl == null) || casBaseUrl.isEmpty())
      {
         logger.error("CAS base URL is not configured");
         return null;
      }

      // Strip trailing slash from base URL
      if (casBaseUrl.endsWith("/"))
         casBaseUrl = casBaseUrl.substring(0, casBaseUrl.length() - 1);

      // Shared state between HTTP handler and main thread
      final AtomicReference<String> ticketRef = new AtomicReference<>(null);
      final CountDownLatch latch = new CountDownLatch(1);

      // Start local HTTP server bound to loopback only.
      // Use port 0 so the OS assigns an available ephemeral port atomically,
      // avoiding the race condition of probe-then-bind.
      HttpServer httpServer;
      try
      {
         httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      }
      catch(IOException e)
      {
         logger.error("Cannot start local CAS callback HTTP server", e);
         return null;
      }

      // Retrieve the actual port assigned by the OS
      int port = httpServer.getAddress().getPort();
      final String serviceUrl = "http://127.0.0.1:" + port + CALLBACK_PATH;

      httpServer.createContext(CALLBACK_PATH, new HttpHandler() {
         @Override
         public void handle(HttpExchange exchange) throws IOException
         {
            String query = exchange.getRequestURI().getRawQuery();
            String ticket = extractQueryParam(query, "ticket");

            String responseBody;
            int statusCode;
            if ((ticket != null) && (ticket.startsWith("ST-") || ticket.startsWith("PT-")))
            {
               ticketRef.set(ticket);
               responseBody = "<html><body><h2>Authentication successful</h2>"
                     + "<p>You can close this browser tab and return to NetXMS.</p></body></html>";
               statusCode = 200;
            }
            else
            {
               responseBody = "<html><body><h2>Authentication failed</h2>"
                     + "<p>No valid CAS ticket received. Please close this tab and try again.</p></body></html>";
               statusCode = 400;
            }

            byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody())
            {
               os.write(responseBytes);
            }
            latch.countDown();
         }
      });
      httpServer.start();
      logger.debug("CAS callback server started on port " + port);

      try
      {
         // Build CAS login URL
         String casLoginUrl = casBaseUrl + "/login?service=" + encodeUrl(serviceUrl);
         logger.info("Opening browser for CAS login: " + casLoginUrl);

         if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
         {
            logger.error("Desktop.browse() is not supported on this platform");
            return null;
         }

         try
         {
            Desktop.getDesktop().browse(new URI(casLoginUrl));
         }
         catch(Exception e)
         {
            logger.error("Cannot open browser for CAS login", e);
            return null;
         }

         // Wait for the callback
         boolean received = latch.await(TICKET_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
         if (!received)
         {
            logger.warn("Timed out waiting for CAS callback (waited " + TICKET_WAIT_TIMEOUT_SECONDS + "s)");
            return null;
         }

         String ticket = ticketRef.get();
         if (ticket == null)
         {
            logger.warn("CAS callback received but no valid ticket found");
            return null;
         }

         logger.debug("CAS ticket received (length=" + ticket.length() + ")");
         return new CASResult(ticket, serviceUrl);
      }
      catch(InterruptedException e)
      {
         Thread.currentThread().interrupt();
         logger.warn("CAS login interrupted");
         return null;
      }
      finally
      {
         // Always shut down the callback server
         httpServer.stop(1);
         logger.debug("CAS callback server stopped");
      }
   }

   /**
    * Extract a named parameter value from a URL query string.
    *
    * @param query raw query string (may be null)
    * @param name  parameter name
    * @return decoded parameter value, or null if not present
    */
   private static String extractQueryParam(String query, String name)
   {
      if (query == null)
         return null;
      Map<String, String> params = new HashMap<>();
      for(String pair : query.split("&"))
      {
         int idx = pair.indexOf('=');
         if (idx > 0)
         {
            String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
            String val = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
            params.put(key, val);
         }
      }
      return params.get(name);
   }

   /**
    * Percent-encode a URL for use as a query parameter value.
    * Only encodes characters that must be encoded in a query parameter value.
    *
    * @param url URL to encode
    * @return percent-encoded URL
    */
   private static String encodeUrl(String url)
   {
      StringBuilder sb = new StringBuilder();
      for(char c : url.toCharArray())
      {
         if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
               || c == '-' || c == '_' || c == '.' || c == '~')
         {
            sb.append(c);
         }
         else
         {
            byte[] bytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
            for(byte b : bytes)
            {
               sb.append(String.format("%%%02X", b & 0xFF));
            }
         }
      }
      return sb.toString();
   }
}
