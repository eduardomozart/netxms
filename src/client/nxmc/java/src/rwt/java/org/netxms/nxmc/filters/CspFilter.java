package org.netxms.nxmc.filters;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

public class CspFilter implements Filter
{
   private static final String DISABLED = "DISABLED";

   private final Map<String, String> headers = new LinkedHashMap<>();

   @Override
   public void init(FilterConfig cfg) throws ServletException
   {
      Map<String, String> defaults = new LinkedHashMap<>();
      defaults.put("Content-Security-Policy",
            "default-src 'self'; " +
            "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
            "style-src 'self' 'unsafe-inline'; " +
            "img-src 'self' data:; " +
            "font-src 'self' data:; " +
            "connect-src 'self'; " +
            "frame-ancestors 'self'; " +
            "form-action 'self'; " +
            "base-uri 'self'");
      defaults.put("X-Content-Type-Options", "nosniff");
      defaults.put("X-Frame-Options", "SAMEORIGIN");
      defaults.put("Referrer-Policy", "strict-origin-when-cross-origin");
      defaults.put("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
      defaults.put("X-Permitted-Cross-Domain-Policies", "none");
      defaults.put("Content-Security-Policy-Report-Only", null);

      for (Map.Entry<String, String> e : defaults.entrySet())
      {
         String param = cfg.getInitParameter(e.getKey());
         if (param != null)
            headers.put(e.getKey(), param.trim());
         else if (e.getValue() != null)
            headers.put(e.getKey(), e.getValue());
      }
   }

   @Override
   public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
         throws IOException, ServletException
   {
      if (res instanceof HttpServletResponse)
      {
         HttpServletResponse httpRes = (HttpServletResponse)res;
         for (Map.Entry<String, String> e : headers.entrySet())
         {
            if (!DISABLED.equals(e.getValue()))
               httpRes.setHeader(e.getKey(), e.getValue());
         }
      }
      chain.doFilter(req, res);
   }

   @Override
   public void destroy()
   {
   }
}
