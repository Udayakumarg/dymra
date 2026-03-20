# Render.com Deployment Guide

## Overview
This application is configured for production deployment on Render.com with Java 17, Maven, Spring Boot 3.2.4, and PostgreSQL/Redis backend services.

## Build & Runtime Configuration

### Dockerfile Changes
✅ **Java Version**: Updated to **Java 17** (from Java 21)
- Build stage: `maven:3.9-eclipse-temurin-17`
- Runtime stage: `eclipse-temurin:17-jre-alpine`

✅ **Removed mvnw References**: No longer depends on `mvnw` or `.mvn/` folder
- Uses system Maven instead: `mvn dependency:go-offline` and `mvn clean package`

✅ **Dynamic Port Binding**: Supports Render's dynamic PORT assignment
- `ENV PORT=8080` defaults to 8080
- ENTRYPOINT: `java $JAVA_OPTS -jar app.jar --server.port=$PORT`

✅ **Multi-Stage Optimization**:
- Dependency caching: `mvn dependency:go-offline` pulls dependencies layer before source copy
- Smaller runtime image: Only JRE (not JDK) in final stage
- Alpine base: Minimal image footprint (~130MB)

✅ **Container Best Practices**:
- Non-root user: `appuser` with minimal permissions
- Health check: Uses `/actuator/health` endpoint
- JVM tuning: G1GC, container awareness, 75% RAM allocation

---

## Spring Boot Configuration

### application.yml Changes
✅ **Dynamic Port**: Changed from hardcoded `port: 8080` to `port: ${PORT:8080}`
✅ **Address Binding**: Added `address: 0.0.0.0` to listen on all interfaces (required for container)

```yaml
server:
  port: ${PORT:8080}
  address: 0.0.0.0
  compression:
    enabled: true
```

---

## Required Environment Variables (Set in Render Dashboard)

| Variable | Example | Required | Notes |
|----------|---------|----------|-------|
| `PORT` | `10000` | No | Auto-set by Render; application reads `$PORT` or defaults to 8080 |
| `DB_URL` | `jdbc:postgresql://host:5432/db` | Yes | PostgreSQL connection string |
| `DB_USER` | `postgres` | Yes | Database username |
| `DB_PASS` | `***` | Yes | Database password |
| `REDIS_HOST` | `redis-host.render.internal` | Yes | Redis hostname |
| `REDIS_PORT` | `6379` | No | Redis port (defaults to 6379) |
| `REDIS_PASSWORD` | `***` | No | Redis password (empty string if none) |
| `JWT_SECRET` | `your-256-bit-secret` | Yes | Minimum 256-bit random string |
| `JWT_EXPIRY_MS` | `86400000` | No | Token expiry in milliseconds (24h default) |
| `ES_HOST` | `elasticsearch-host.internal` | Yes | Elasticsearch hostname |
| `ES_PORT` | `9200` | No | Elasticsearch port (defaults to 9200) |
| `ES_USER` | `elastic` | Yes | Elasticsearch username |
| `ES_PASS` | `***` | Yes | Elasticsearch password |
| `WA_PROVIDER` | `gupshup` | No | WhatsApp provider (mock/gupshup/twilio) |
| `WA_API_URL` | `https://api.gupshup.io/...` | No | WhatsApp API endpoint |
| `WA_API_KEY` | `***` | Conditional | Required if WA_PROVIDER != mock |
| `WA_SOURCE` | `+919999999999` | No | WhatsApp source number |

---

## Deployment Steps on Render

### 1. Create Web Service
- **Name**: `tirupurconnect`
- **Region**: Choose closest to your users
- **Docker**: Select "Docker" as environment
- **Dockerfile Path**: `./Dockerfile` (default)

### 2. Set Environment Variables
Copy all required variables from table above into Render dashboard. Set sensitive values via **Render Secrets** (not plain text).

### 3. Connect Databases
In Render dashboard, link:
- **PostgreSQL**: Private service, ensure `DB_URL`, `DB_USER`, `DB_PASS` are set
- **Redis**: Private service, ensure `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` are set
- **Elasticsearch**: Private service (if using), ensure `ES_HOST`, `ES_USER`, `ES_PASS` are set

### 4. Deploy
Push to your connected Git repository. Render will:
1. Pull source code
2. Build Docker image using `Dockerfile`
3. Expose service on dynamic `PORT` environment variable
4. Run container with all env vars
5. Health check via `/actuator/health` endpoint

---

## Verification Checklist

After deployment:

```bash
# 1. Check health endpoint
curl https://your-app.onrender.com/actuator/health

# 2. Check info endpoint
curl https://your-app.onrender.com/actuator/info

# 3. Monitor metrics
curl https://your-app.onrender.com/actuator/prometheus

# 4. Check logs in Render Dashboard
# Verify no connection errors to PostgreSQL/Redis/Elasticsearch
```

---

## Build Cache Optimization

The Dockerfile is optimized for build cache:
1. **Layer 1**: Base Maven image (reused across builds)
2. **Layer 2**: Copy `pom.xml` only → Run `mvn dependency:go-offline` (cached if pom.xml unchanged)
3. **Layer 3**: Copy `src/` → Run `mvn clean package` (only rebuilds if source changed)
4. **Layer 4**: New runtime image, copy jar from builder (minimal overhead)

**Result**: Builds after code changes take ~60-90 seconds (vs ~3-5 min on first build).

---

## Troubleshooting

### Container crashes immediately
- Check `JAVA_OPTS` isn't causing JVM to exceed container memory
- Verify PostgreSQL connection string in `DB_URL`
- Check logs in Render dashboard for specific errors

### Port binding error
- Ensure `server.address: 0.0.0.0` is set in `application.yml`
- Verify `--server.port=$PORT` in Dockerfile ENTRYPOINT

### Health check failing
- Ensure actuator endpoints are exposed: `management.endpoints.web.exposure.include: health`
- Check if Spring Boot is fully started within 40s timeout (increase `--start-period` if needed)

### Database connection timeout
- Verify PostgreSQL is running in Render
- Check `DB_URL` format: `jdbc:postgresql://hostname:5432/dbname`
- Ensure database credentials are correct

---

## Production Notes

✅ **Do not modify**:
- `Dockerfile` structure (multi-stage build is required)
- `server.address: 0.0.0.0` (containers must listen on all interfaces)
- `${PORT}` environment variable usage

✅ **Best practices**:
- Use **Render Secrets** for `JWT_SECRET`, database passwords, API keys
- Enable HTTPS (automatic on Render)
- Monitor metrics via `/actuator/prometheus` dashboard
- Set appropriate resource limits (CPU/RAM) based on load

✅ **Scaling**:
- For high traffic, increase "Instance Type" in Render dashboard
- Add external PostgreSQL/Redis replicas for redundancy
- Consider load balancer if needed


