# NetXMS 6 AI Subsystem — Data Flow and Security Analysis

*Revision reflects security enhancements in PRs #3154 (SSL verification) and #3155 (AI access right).*

## 1. Architecture Overview

The NetXMS AI subsystem consists of these core components:

| Component | Location | Purpose |
|-----------|----------|---------|
| AI Core | `src/server/core/ai_core.cpp` | Chat management, function calling loop, provider dispatch |
| Provider Abstraction | `src/server/core/ai_provider.cpp` | HTTP transport to LLM providers |
| Provider Implementations | `ai_provider_openai.cpp`, `ai_provider_anthropic.cpp`, `ai_provider_ollama.cpp` | Format-specific API adapters |
| AI Tools Module | `src/server/aitools/` | 100+ tool/function registrations across 9 source files |
| AI Tasks | `src/server/core/ai_tasks.cpp` | Autonomous background task scheduler |
| AI Messages | `src/server/core/ai_messages.cpp` | Persistent message/notification system |
| Agent AI Tools | `src/agent/subagents/aifileops/` | Agent-side file and log operations |
| WebAPI | `src/server/webapi/ai.cpp` | REST API endpoints |
| CLI Client | `src/client/nxai/` | Python terminal client (`nxai`) |

Three LLM provider types are supported (`ai_provider.h:32-37`):
- **Ollama** — local/self-hosted (default URL: `http://127.0.0.1:11434/api/chat`)
- **OpenAI** — OpenAI API and any OpenAI-compatible endpoint
- **Anthropic** — Anthropic Messages API

## 2. What Data Is Transmitted to the LLM Provider

Every API call to the configured LLM provider transmits a JSON payload containing:

### 2.1 System Prompt (sent with every request)

The system prompt is constructed in the `Chat` constructor (`ai_core.cpp:732-773`) and includes:
- **Static instructions** about NetXMS terminology, object types, capabilities, skill management, and response guidelines (~5KB of text, `ai_core.cpp:85-260`)
- **NetXMS concepts** — object model definitions, capability mappings, DCI concepts
- **Security boundary prompt** — anti-jailbreak instructions (`ai_core.cpp:227-233`)
- **Available skills list** — names and descriptions of loaded AI skills
- **Additional custom prompts** — any prompts added via `AddAIAssistantPrompt()`

If the chat is created with an **object context** (e.g., for a specific node), the full JSON representation of that object is included in the system prompt (`ai_core.cpp:749-757`). This may contain: object name, alias, GUID, class, status, custom attributes, comments, geolocation data, AI hints.

If **event data** is associated, the event's JSON representation is included (`ai_core.cpp:758-764`).

If the chat is **bound to an incident** (`ai_core.cpp:791-843`), the system prompt includes:
- Full incident JSON (ID, creation time, state, assigned user, title, linked alarms)
- Source object context (full JSON of the incident's source node/device)

### 2.2 Conversation History (cumulative)

Every message in the conversation is sent with every request. The `m_messages` JSON array accumulates all user prompts, all assistant responses, and all tool call requests and their results. Function call results (which may contain live operational data) become part of the conversation context and are retransmitted on every subsequent API call within the same chat session.

### 2.3 Tool/Function Declarations

The complete set of available function schemas is sent as the `tools` parameter (`ai_core.cpp:1313`). This includes function names, descriptions, and parameter schemas for all registered functions (100+ when all skills are loaded). The function declarations themselves are metadata and don't contain operational data.

### 2.4 Function Call Results (the primary data exposure vector)

The LLM uses tool calling to request data from NetXMS. The server executes the function locally and sends the result back to the LLM as a tool response message (`ai_core.cpp:1350-1352`). The following data categories can be retrieved and transmitted:

| Source File | Functions | Data Exposed |
|------------|-----------|--------------|
| `aitools/objects.cpp` | find-objects, get-object, get-node-interfaces, get-node-hardware-components, get-node-software-packages, explain-object-status, start/end-maintenance, set/get/remove-object-ai-data | Node names, IPs, MAC addresses, SNMP settings, capabilities, status, hardware serial numbers, firmware versions, installed software with versions, geolocation, custom attributes |
| `aitools/alarms.cpp` | alarm-list | Active alarm messages, severity, source objects, timestamps |
| `aitools/logs.cpp` | list-logs, get-log-schema, search-log, search-syslog, search-windows-events, search-snmp-traps, search-events, get-log-statistics, correlate-logs, analyze-log-patterns | Syslog messages, Windows Event Log entries (including security events), SNMP trap data, NetXMS system events, cross-source correlation results |
| `aitools/incidents.cpp` | get-incident-details, get-incident-related-events, get-incident-history, get-incident-topology-context, add-incident-comment, link-alarm-to-incident, assign-incident, suggest-incident-assignee, get-open-incidents, create-incident | Incident details with assigned user, linked alarm messages, comments with user IDs, L2 neighbors, routing tables, subnet/peer topology |
| `aitools/datacoll.cpp` | get-metrics, get-metric-details, get-historical-data, get/add/delete-threshold, create/edit/delete-metric | Current and historical performance data (up to 1000 data points), metric configuration, transformation scripts, threshold definitions |
| `aitools/snmp.cpp` | snmp-read, snmp-walk | Direct SNMP OID reads and MIB walks (up to 1000 entries) — can enumerate system descriptions, interface tables, routing tables |
| `aitools/devbackup.cpp` | get-backup-status, get-backup-list, get-backup-content, start-backup, compare-backups | **Full device configuration content** (up to 64KB) — routinely contains password hashes, SNMP community strings, BGP configs, ACLs, VPN tunnels |
| `aitools/evproc.cpp` | get-event-template, get-event-processing-policy, get-event-processing-actions, create/modify/delete-event-template | Complete event processing policy, event template definitions, server action configurations |
| `aitools/nc.cpp` | send-notification, get-notification-channels, clear-notification-channel-queue | Channel configurations; can trigger notification sending to recipient addresses |
| `ai_remote.cpp` + `agent/subagents/aifileops/` | get-node-ai-tools, execute-agent-tool | File content from monitored systems (within agent-configured paths), log file grep/tail/head/time-range extraction |

Additionally, **NXSL scripts** marked with `ai_tool` metadata can be called by the LLM (`ai_core.cpp:531-586`); their output is sent verbatim to the provider.

### 2.5 Prompt Injection Guard Requests

When enabled (default: on), the guard sends the user's prompt to a separate LLM call for classification (`ai_core.cpp:1146-1241`). This uses the "guard" or "fast" provider slot. Only the user's message text is sent — no function results or conversation history.

## 3. When Data Is Transmitted

### 3.1 Interactive User Prompts (Primary)

Data is sent when a user sends a message through the WebAPI (`POST /v1/ai/chat/:id/message`), the `nxai` CLI client, or the management console. Each message triggers up to **10 iterations** of function calling (WebAPI) or **32 iterations** (NXCP client) where the LLM can request and receive data.

### 3.2 Background AI Tasks (Autonomous)

The AI task scheduler (`ai_tasks.cpp:259-291`) polls every **30 seconds**. Scheduled tasks execute autonomously with up to **64 iterations** of function calling. Background tasks operate without user interaction, have the same function access as interactive chats, and preserve state (memento) between executions. They are created by the LLM itself (via `register-ai-task`), by server-side code, or by users through the management console.

### 3.3 Automatic Incident AI Analysis

When configured in event processing rules (`epp.cpp:1341-1343`), incident creation can automatically trigger AI analysis (`ai_core.cpp:2081-2099`). This background operation loads the `incident-analysis` skill and retrieves incident details, linked alarms, related events, and network topology, sending it to the LLM. Configurable depth: quick (0), standard (1), thorough (2).

### 3.4 Skill Delegation

When a skill is delegated (`delegate-to-skill`), a separate Chat context is created, the task is sent to the LLM, and a summarized result is returned. This generates an additional LLM call triggered by the primary chat interaction.

### 3.5 Anomaly Detection Profiles

The anomaly profile generator (`anomaly_profile.cpp`) uses statistical analysis locally and does **not** send data to the LLM.

## 4. Data Protection and Controls

### 4.1 Data Filtering

There is **no data filtering, minimization, masking, or redaction** layer between function call results and the LLM provider. Function results are sent verbatim (`ai_core.cpp:1352`). The one exception is device backup content, which is truncated at 64KB.

### 4.2 User Access Control — `SYSTEM_ACCESS_USE_AI_ASSISTANT`

A dedicated system access right `SYSTEM_ACCESS_USE_AI_ASSISTANT` (`nxcldefs.h`, bit 53) gates all AI operations. Users must have this right to use any AI feature. The check is enforced at:

**NXCP session handlers** (9 handlers in `session.cpp`):
`queryAiAssistant`, `createAiAssistantChat`, `clearAiAssistantChat`, `deleteAiAssistantChat`, `requestAiAssistantComment`, `getAiAssistantFunctions`, `callAiAssistantFunction`, `addAiAgentTask`, `handleAiQuestionResponse`

**WebAPI REST handlers** (7 handlers in `webapi/ai.cpp`):
All AI chat endpoints (create, send message, get status, poll question, answer question, clear, delete)

Users without this right receive `RCC_ACCESS_DENIED` (NXCP) or HTTP 403 (WebAPI). The right is configurable in the management console under System Rights, labeled "Use AI assistant".

Note: `getAiAgentTasks` and `deleteAiAgentTask` are separately gated by the existing `SYSTEM_ACCESS_MANAGE_AI_TASKS` right. AI message handlers (`getAIMessages`, `setAIMessageStatus`, `deleteAIMessage`) remain ungated since they handle passive notifications that may have been generated by background tasks.

### 4.3 Object-Level Access Control

All AI tool functions enforce NetXMS role-based access control. The `userId` of the authenticated user is passed to every function handler. Functions check `checkAccessRights(userId, OBJECT_ACCESS_READ)` for object access and `GetEffectiveSystemRights(userId)` for system-level operations (e.g., `SYSTEM_ACCESS_EPP`, `SYSTEM_ACCESS_SEND_NOTIFICATION`). The AI assistant can only access data the authenticated user already has permission to view.

### 4.4 TLS Certificate Verification

SSL certificate verification is **enabled by default** for all HTTPS connections to LLM providers. Per-provider configuration:

```ini
[AI/Provider/MyProvider]
VerifySSL = yes    # default; set to "no" for self-signed certificate endpoints
```

When enabled, `CURLOPT_SSL_VERIFYHOST` is set to `2` (verify hostname matches certificate) and `CURLOPT_SSL_VERIFYPEER` is set to `1` (verify certificate chain). Administrators using self-signed certificates on private OpenAI-compatible endpoints can disable verification per provider.

### 4.5 Prompt Injection Guard

Enabled by default (`ai_core.cpp:2367`), configurable via:

```ini
[AI]
PromptInjectionGuard = true|false
```

Uses a separate LLM call to classify user messages. When injection is detected, a reinforcement message is added to the system prompt. The guard "fails open" — if the LLM call fails, the user message proceeds normally.

### 4.6 Security Boundary Prompt

A static anti-jailbreak prompt is appended to every chat's system context (`ai_core.cpp:227-233`), instructing the LLM to refuse role redefinition, instruction extraction, and restriction bypass attempts.

### 4.7 Provider Slot Configuration

Providers can be assigned to specific slots: `interactive`, `background`, `fast`, `analytical`, `guard`, `default`. This allows routing different operation types to different providers (e.g., local Ollama for guard checks, cloud provider for interactive chat).

### 4.8 Configuring for Local/Self-Hosted Only

By configuring only an Ollama provider pointing to a local instance, all AI traffic stays on-premises:

```ini
[AI/Provider/Local]
Type = ollama
URL = http://127.0.0.1:11434/api/chat
Model = llama3.2
Slots = default,interactive,background,guard,fast,analytical
```

### 4.9 Disabling AI Features

- **No providers configured**: `InitAIAssistant()` returns false and the AI component is not registered
- **No functions registered**: If no functions or skills are loaded, the AI assistant remains disabled
- **User-level**: Revoke `SYSTEM_ACCESS_USE_AI_ASSISTANT` from users/groups who should not have AI access
- **Background tasks**: Setting `ThreadPool.AITasks.BaseSize = 0` and `MaxSize = 0` disables background task execution
- **Agent-side tools**: Not loading the `aifileops` subagent prevents agent-side file/log access
- **Automatic incident analysis**: Not setting the `RF_AI_ANALYZE_INCIDENT` flag on EPP rules prevents automatic analysis

## 5. Local Storage and Logging

### 5.1 Debug Logging

At debug level 7+, full request and response payloads are logged to the server debug log. At debug level 8, the raw HTTP request/response bodies are logged (`ai_provider.cpp:159, 226`). At standard production levels (1-5), only metadata is logged (function names, response lengths, errors).

### 5.2 AI Task Execution Log

Background task executions are logged to the `ai_task_execution_log` database table containing: record ID, timestamp, task ID, description, user ID, status, iteration count, explanation text. Task state (memento and prompt) is persisted in the `ai_tasks` table.

### 5.3 AI Messages

AI-generated messages and approval requests are stored in the database with configurable expiration. Cleanup interval is set via `AI.MessageCleanupInterval` (default: 60 minutes).

### 5.4 Chat Session History

Chat sessions are held **in memory only**. Conversation history is not persisted to disk or database. Sessions are destroyed on explicit deletion or server restart.

### 5.5 Token Usage Counters

Per-provider and global token usage counters are maintained in memory and are not persisted.

## 6. Data Exposure Summary

| Data Category | Transmitted | Trigger | Restriction Mechanism |
|---|---|---|---|
| User prompts/messages | Yes | Every interactive request | `SYSTEM_ACCESS_USE_AI_ASSISTANT` |
| Conversation history | Yes (cumulative) | Every request in session | Clear chat between topics |
| Object names, IPs, classes | Yes (via tool calls) | LLM calls find-objects, get-object | RBAC on objects |
| Hardware/software inventory | Yes (via tool calls) | LLM calls inventory functions | RBAC; don't load inventory skill |
| Interface details, MACs | Yes (via tool calls) | LLM calls get-node-interfaces | RBAC on objects |
| Network topology, routing | Yes (via tool calls) | LLM calls topology functions | RBAC on objects |
| Alarm messages | Yes (via tool calls) | LLM calls alarm-list | RBAC on objects |
| Syslog/Windows events/traps | Yes (via tool calls) | LLM calls log search functions | RBAC + log access rights |
| Performance/metric data | Yes (via tool calls) | LLM calls get-metrics, get-historical-data | RBAC on objects |
| Device configurations | Yes (via tool calls, up to 64KB) | LLM calls get-backup-content | RBAC on objects |
| SNMP device data | Yes (via tool calls) | LLM calls snmp-read/snmp-walk | RBAC + SNMP access |
| Event processing policy | Yes (via tool calls) | LLM calls get-event-processing-policy | `SYSTEM_ACCESS_EPP` |
| Incident details, comments | Yes (via tool calls) | LLM calls incident functions | RBAC on incident source objects |
| User IDs | Yes (in incident/comment data) | Incident-related functions | RBAC |
| Agent-side file content | Yes (via tool calls) | LLM calls execute-agent-tool | RBAC + agent subagent config |
| NXSL script output | Yes (via tool calls) | LLM calls script functions | Script must be marked `ai_tool` |
| Notification recipients | Yes (when sending) | LLM calls send-notification | `SYSTEM_ACCESS_SEND_NOTIFICATION` |

## 7. Remaining Recommendations

The two implemented enhancements (SSL verification default-on and `SYSTEM_ACCESS_USE_AI_ASSISTANT`) address the transport security gap and the user-level access control gap. The following items remain as potential future improvements:

1. **Configurable function/skill allowlist/blocklist** — Let administrators disable specific skills (e.g., `device-backup`) or individual functions (e.g., `get-backup-content`, `snmp-walk`) without code changes. Single dispatch point at `Chat::callFunction()` makes this straightforward.

2. **Device backup credential redaction** — `get-backup-content` returns raw device configs that routinely contain password hashes and community strings. Regex-based scrubbing of known credential patterns before returning results would address the highest-sensitivity data exposure.

3. **AI audit log** — A dedicated database table recording who initiated each chat, which functions were called (name + target object, not full payloads), and whether the session was interactive or background. Provides compliance traceability without the storage overhead of full payload logging.

4. **Configurable max response size** — Global or per-function cap on function result size before it enters the conversation context. Limits data exposure per interaction and reduces token usage.
