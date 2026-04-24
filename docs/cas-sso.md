# CAS Single Sign-On (SSO) for NetXMS

This document describes how to configure and use Central Authentication Service (CAS) Single Sign-On for the NetXMS Desktop Client (NXMC) and Server Core.

## Overview

NetXMS supports CAS 2.0 Service Ticket validation. When a client requests login with auth type `NETXMS_AUTH_TYPE_SSO_TICKET` (= 2), the server:

1. Receives the CAS Service Ticket (ST-...) from the client.
2. Validates the ticket against the configured CAS server endpoint.
3. Extracts the user name from the CAS response (`<cas:user>`).
4. Authenticates the user in the NetXMS user database (bypassing local password / LDAP checks).

## Server Configuration

The following configuration keys (set in the NetXMS server configuration database via `nxdbmgr` or the management console) control CAS validation:

| Key | Default | Description |
|-----|---------|-------------|
| `CAS.Host` | `localhost` | Hostname of the CAS server. |
| `CAS.Port` | `8443` | HTTPS port of the CAS server. |
| `CAS.Service` | `http://127.0.0.1:10080/nxmc` | Default CAS service URL used during ticket validation. See note below. |
| `CAS.ValidateURL` | `/cas/serviceValidate` | Path on the CAS server for ticket validation. |
| `CAS.TrustedCACert` | *(empty)* | Path to the PEM-encoded CA certificate file used to verify the CAS server TLS certificate. |
| `CAS.AllowedProxies` | *(empty)* | Comma-separated list of allowed CAS proxy URLs. Leave empty to disallow proxy tickets. |

> **Note on `CAS.Service`:** When the desktop client performs CAS login using the browser-redirect flow (see below), it sends its own ephemeral `service` URL alongside the ticket. The server uses the client-supplied URL for that login request instead of `CAS.Service`. `CAS.Service` is only used when the client does *not* supply a service URL (e.g. when the ticket is obtained by some other means).

### Example: Setting CAS configuration via management console

Navigate to **Configuration → Server Configuration** and set the keys above, or use `nxadm`:

```
nxadm -s <server> -u admin -P <password> -c "set CAS.Host cas.example.com"
nxadm -s <server> -u admin -P <password> -c "set CAS.Port 443"
nxadm -s <server> -u admin -P <password> -c "set CAS.ValidateURL /cas/serviceValidate"
nxadm -s <server> -u admin -P <password> -c "set CAS.TrustedCACert /etc/ssl/certs/ca-bundle.crt"
```

## Desktop Client (NXMC) – CAS Login Flow

### How it works

When the `--cas=<url>` argument is provided (or the `netxms.cas` JVM property is set), NXMC performs CAS authentication automatically at startup:

1. An ephemeral HTTP server is started on `127.0.0.1` (loopback only) on a randomly available port.
2. The system default browser is opened to `<cas-url>/login?service=http://127.0.0.1:<port>/cas-callback`.
3. The user authenticates in the browser. CAS redirects back to the callback URL with a `ticket=ST-...` query parameter.
4. NXMC captures the ticket and shuts down the local HTTP server.
5. NXMC sends a `CMD_LOGIN` message to the NetXMS server with:
   - `VID_AUTH_TYPE = 2` (SSO_TICKET)
   - `VID_PASSWORD = ST-...` (the CAS Service Ticket)
   - `VID_CAS_SERVICE_URL = http://127.0.0.1:<port>/cas-callback` (the service URL used for the browser redirect)
6. The server validates the ticket using the supplied service URL and logs the user in.

If CAS authentication fails (timeout, ticket not received, CAS server unreachable), the standard login dialog is shown as a fallback.

### Running NXMC with CAS

```bash
# Using command-line argument
java -jar nxmc.jar -server=netxms.example.com -cas=https://cas.example.com/cas

# Using JVM system properties
java -Dnetxms.server=netxms.example.com \
     -Dnetxms.cas=https://cas.example.com/cas \
     -jar nxmc.jar
```

### Parameters

| Parameter / JVM Property | Description |
|--------------------------|-------------|
| `-cas=<url>` / `netxms.cas` | CAS server base URL (e.g. `https://cas.example.com/cas`). Enables CAS SSO flow. |
| `-server=<host[:port]>` / `netxms.server` | NetXMS server address. **Required** when using CAS. |

## Building the Desktop Client (NXMC)

### Prerequisites

- JDK 17+
- Maven 3.6+

### Build steps

```bash
# 1. Build the base library
mvn -f src/java-common/netxms-base/pom.xml install

# 2. Build the client library
mvn -f src/client/java/netxms-client/pom.xml install

# 3. Build the desktop client (standalone JAR)
mvn -f src/client/nxmc/java/pom.xml clean package -Pdesktop -Pstandalone

# The resulting JAR is at:
# src/client/nxmc/java/target/netxms-nxmc-standalone-*.jar
```

### Running the built client

```bash
java -jar src/client/nxmc/java/target/netxms-nxmc-standalone-*.jar
```

## Running the NetXMS Server with Docker

### Using Docker Compose

A minimal Docker Compose setup for the NetXMS server:

```yaml
# docker-compose.yml
version: "3.8"
services:
  netxms-server:
    image: netxms/server:latest
    ports:
      - "4701:4701"   # client connections
      - "4702:4702"   # agent connections
    volumes:
      - netxms-data:/var/lib/netxms
    environment:
      - DB_DRIVER=sqlite
      - DB_NAME=/var/lib/netxms/netxms.db
      # CAS configuration (optional)
      - CAS_HOST=cas.example.com
      - CAS_PORT=443
      - CAS_VALIDATE_URL=/cas/serviceValidate

volumes:
  netxms-data:
```

```bash
docker compose up -d
```

### Building a custom server image from source

```bash
# Configure with server and agent support
./init-source-tree
./configure --prefix=/opt/netxms --with-sqlite --with-server --with-agent --enable-debug

# Build
make -j$(nproc)

# Create a Docker image using the Dockerfile in the repository root (if present)
# or build manually:
make install DESTDIR=/tmp/netxms-root
docker build -t netxms-server:local -f Dockerfile .
```

## Protocol Details

### NXCP Fields

| Field | ID | Type | Description |
|-------|----|------|-------------|
| `VID_AUTH_TYPE` | 275 | Int16 | Authentication type. Set to `2` for CAS/SSO ticket. |
| `VID_PASSWORD` | 2 | String | CAS Service Ticket (e.g. `ST-...` or `PT-...`). |
| `VID_CAS_SERVICE_URL` | 980 | String | (Optional) CAS service URL used during browser redirect. Server uses this for ticket validation instead of the configured `CAS.Service`. |
| `VID_LOGIN_NAME` | 1 | String | User name (may be empty for CAS; the server fills it from the CAS response). |

### Auth Type Constants

| Constant | Value | Description |
|----------|-------|-------------|
| `NETXMS_AUTH_TYPE_PASSWORD` | 0 | Password authentication |
| `NETXMS_AUTH_TYPE_CERTIFICATE` | 1 | Certificate authentication |
| `NETXMS_AUTH_TYPE_SSO_TICKET` | 2 | SSO / CAS ticket authentication |
| `NETXMS_AUTH_TYPE_TOKEN` | 3 | Authentication token |
| `NETXMS_AUTH_TYPE_CAS` | 2 | Alias for `NETXMS_AUTH_TYPE_SSO_TICKET` |

## Security Considerations

- The local callback HTTP server binds to `127.0.0.1` (loopback) only, preventing remote access.
- The CAS ticket is not logged in full (only its length is logged at debug level).
- TLS certificate verification for the CAS server is controlled by `CAS.TrustedCACert`. Set this to your CA bundle in production.
- Tickets with unexpected format (not starting with `ST-` or `PT-`) are rejected before forwarding to the server.
- The callback server times out after 120 seconds; if no ticket is received, the flow falls back to the standard login dialog.
