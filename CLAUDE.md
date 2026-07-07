# CLAUDE.md

This file provides guidance to Claude Code when working with this repository.

## Build Commands

```bash
mvn clean compile
mvn test
mvn clean package
mvn clean package -Dproguard.skip=true
mvn spring-boot:run -Dspring-boot.run.profiles=dev
java -jar target/application.jar --spring.profiles.active=dev
```

## Project Overview

This is a Spring Boot 3.2.0 web service on Java 17. It provides a configurable data analysis platform with MySQL, ClickHouse, Redis, retrieval APIs, chat sessions, AI agents, MCP tool integration, and dashboard/system management.

Key features:
- Multi-datasource architecture
- Retrieval APIs for business data
- AI chat and agent workflows through Spring AI
- MCP tool injection per agent type
- Redis vector store support for RAG
- ECharts rendering support for historical or non-inspection chart messages

## Architecture

The application uses separate JPA configurations:

- MySQL: configuration data, users, roles, menus, sessions, rules
- ClickHouse: time-series and event data
- Redis: cache and optional vector storage

Main code layout:

```text
src/main/java/com/coolxer/
├── controller/          # REST API endpoints
├── service/             # Business logic
│   ├── dih/             # AI chat, agent, MCP, RAG services
│   ├── retrieval/       # Data retrieval services
│   └── config/          # Configuration management
├── dao/                 # MySQL and ClickHouse data access
├── configuration/       # Spring configuration
├── aop/                 # Cross-cutting concerns
└── commons/             # Shared utilities
```

## AI Integration

AI features use Spring AI and OpenAI-compatible models.

Important services:
- `AIChatService`: streaming chat, memory, attachments, and RAG
- `InspectionAgent`: retrieval-only inspection agent
- `PromptDrivenAgentRuntime`: shared prompt-driven agent runtime
- `AgentMcpToolService`: resolves MCP tools by agent type
- `AgentLlmService`: generic synchronous/streaming LLM wrapper for non-chat flows

Inspection agent behavior:
- Uses only read-only retrieval MCP tools
- Does not generate or execute SQL
- Does not produce chart messages
- Returns text or Markdown analysis

## API Structure

APIs follow `/api/v1/{module}/{resource}`.

Main modules:
- `/api/v1/dih/`: AI chat, sessions, skills, MCP
- `/api/v1/business/asset/`: asset management
- `/api/v1/business/operation/`: operational events
- `/api/v1/business/risk/`: risk management
- `/api/v1/retrieval/`: retrieval interfaces
- `/api/v1/system/`: system management

## Development Notes

- Bean names use the full class path through `UniqueBeanNameGenerator`.
- Service layer generally follows the interface plus `impl` pattern.
- Lombok is used extensively.
- Redis vector store is optional and controlled by embedding/RAG configuration.
- Do not add direct database query generation inside inspection agents; expose read-only retrieval through MCP instead.

## Testing

Use focused tests during development:

```bash
mvn -DskipTests compile
mvn -Dtest=AgentMcpToolServiceTest,SkillServiceTest test
```

Full `mvn test` may require local database or network access depending on the test set.
