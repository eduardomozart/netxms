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

import java.security.Signature;
import java.security.cert.Certificate;
import org.netxms.client.constants.AuthenticationType;

/**
 * Login credentials container. Encapsulates all data needed to authenticate with the server.
 */
public class LoginCredentials
{
   private final String server;
   private final String loginName;
   private final AuthenticationType authMethod;
   private final String password;
   private final Certificate certificate;
   private final Signature signature;
   private final String casServiceUrl;

   /**
    * Create credentials for password authentication.
    *
    * @param server server address
    * @param loginName login name
    * @param password password
    */
   public LoginCredentials(String server, String loginName, String password)
   {
      this.server = server;
      this.loginName = loginName;
      this.authMethod = AuthenticationType.PASSWORD;
      this.password = password;
      this.certificate = null;
      this.signature = null;
      this.casServiceUrl = null;
   }

   /**
    * Create credentials for certificate authentication.
    *
    * @param server server address
    * @param loginName login name
    * @param certificate certificate
    * @param signature signature
    */
   public LoginCredentials(String server, String loginName, Certificate certificate, Signature signature)
   {
      this.server = server;
      this.loginName = loginName;
      this.authMethod = AuthenticationType.CERTIFICATE;
      this.password = null;
      this.certificate = certificate;
      this.signature = signature;
      this.casServiceUrl = null;
   }

   /**
    * Private constructor for token authentication.
    */
   private LoginCredentials(String server, String token)
   {
      this.server = server;
      this.loginName = token;
      this.authMethod = AuthenticationType.TOKEN;
      this.password = null;
      this.certificate = null;
      this.signature = null;
      this.casServiceUrl = null;
   }

   /**
    * Private constructor for SSO/CAS ticket authentication.
    */
   private LoginCredentials(String server, String loginName, String ticket, String serviceUrl)
   {
      this.server = server;
      this.loginName = loginName;
      this.authMethod = AuthenticationType.SSO_TICKET;
      this.password = ticket;
      this.certificate = null;
      this.signature = null;
      this.casServiceUrl = serviceUrl;
   }

   /**
    * Create credentials for token authentication.
    *
    * @param server server address
    * @param token authentication token
    * @return login credentials
    */
   public static LoginCredentials forToken(String server, String token)
   {
      return new LoginCredentials(server, token);
   }

   /**
    * Create credentials for CAS/SSO ticket authentication.
    *
    * @param server server address
    * @param loginName login name (may be empty; CAS server will provide it)
    * @param ticket CAS Service Ticket (ST-...)
    * @param serviceUrl CAS service URL used during browser redirect (may be null to use server-configured value)
    * @return login credentials
    */
   public static LoginCredentials forSSOTicket(String server, String loginName, String ticket, String serviceUrl)
   {
      return new LoginCredentials(server, loginName, ticket, serviceUrl);
   }

   /**
    * Get server address.
    *
    * @return server address
    */
   public String getServer()
   {
      return server;
   }

   /**
    * Get login name (or token for token authentication).
    *
    * @return login name
    */
   public String getLoginName()
   {
      return loginName;
   }

   /**
    * Get authentication method.
    *
    * @return authentication method
    */
   public AuthenticationType getAuthMethod()
   {
      return authMethod;
   }

   /**
    * Get password. Returns null if authentication method is not PASSWORD.
    *
    * @return password or null
    */
   public String getPassword()
   {
      return password;
   }

   /**
    * Get certificate. Returns null if authentication method is not CERTIFICATE.
    *
    * @return certificate or null
    */
   public Certificate getCertificate()
   {
      return certificate;
   }

   /**
    * Get signature. Returns null if authentication method is not CERTIFICATE.
    *
    * @return signature or null
    */
   public Signature getSignature()
   {
      return signature;
   }

   /**
    * Get CAS service URL. Returns null if not a CAS/SSO ticket authentication or no explicit service URL was set.
    *
    * @return CAS service URL or null
    */
   public String getCasServiceUrl()
   {
      return casServiceUrl;
   }
}
