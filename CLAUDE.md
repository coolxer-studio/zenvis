# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Compile the project
mvn clean compile

# Run tests
mvn test

# Package the application (includes ProGuard obfuscation)
mvn clean package

# Package without obfuscation (faster, for development)
mvn clean package -Dproguard.skip=true

# Run with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# Or
java -jar target/application.jar --spring.profiles.active=dev
```

## Project Overview

This is a Spring Boot 3.2.0 web service (Java 17) that provides an intelligent inspection platform with AI capabilities. It integrates with OpenAI through Spring AI for AI features including chat, code completion, and intelligent inspection agents.

**Key Features:**
- Multi-datasource architecture (MySQL + ClickHouse + Redis)
- AI-powered natural language to SQL conversion (NL2SQL)
- Intelligent inspection agents with ReAct pattern
- Vector-based semantic search using Redis
- Real-time data visualization with ECharts
- Comprehensive asset, risk, and operational event management

## Architecture

### Dual Datasource Configuration

The application uses separate databases with JPA:

- **MySQL** (`com.coolxer.dao.mysql`): Configuration data, user management, sessions, rules
  - Entity packages: `com.coolxer.dao.mysql.entity`
  - Repository packages: `com.coolxer.dao.mysql.repository`
  - Transaction manager: `mysqlTransactionManager`

- **ClickHouse** (`com.coolxer.dao.clickhouse`): Time-series data, events, assets, risks
  - Entity packages: `com.coolxer.dao.clickhouse.entity`
  - Repository packages: `com.coolxer.dao.clickhouse.repository`
  - Transaction manager: `clickHouseTransactionManager` (Primary)

### Layer Structure

```
src/main/java/com/coolxer/
├── controller/          # REST API endpoints
│   ├── business/        # Business domain controllers
│   │   ├── asset/       # Asset management (API, App, File, Host, IoT, Mobile, PC, etc.)
│   │   ├── operation/   # Operational events (ANR, Crash, Network, Performance, etc.)
│   │   └── risk/        # Risk management (Attack, Baseline, Data, Vulnerability, etc.)
│   ├── dih/             # AI/DIH chat and session endpoints
│   ├── retrieval/       # Data retrieval endpoints
│   ├── policy/          # Policy configuration
│   ├── dashboard/       # Dashboard management
│   └── system/          # System management (User, Role, Menu, Dashboard, etc.)
├── service/             # Business logic layer (interface + impl pattern)
│   ├── dih/             # AI services (Chat, CodeComplete, Agent)
│   │   ├── agent/       # ReAct Agent for intelligent inspection
│   │   │   ├── nl2sql/  # Natural language to SQL conversion
│   │   │   ├── rag/     # Retrieval Augmented Generation
│   │   │   └── converter/ # Data visualization converters
│   │   └── rag/         # RAG implementation
│   ├── retrieval/       # Data query and retrieval services
│   └── config/          # Configuration management
├── dao/                 # Data access layer
│   ├── mysql/           # MySQL entities and repositories
│   └── clickhouse/      # ClickHouse entities and repositories
├── configuration/       # Spring configurations
│   ├── datasouce/       # Dual datasource JPA configurations
│   └── extend/          # Extension and plugin configurations
├── aop/                 # Cross-cutting concerns (logging, exception handling, auth)
└── commons/             # Shared utilities, enums, exceptions
```

### AI Integration (Spring AI OpenAI)

The application uses Spring AI's official OpenAI integration for AI capabilities:

- **OpenAI Chat Model**: Primary LLM integration for chat and reasoning
- **Vector Store**: Redis-based vector storage for RAG (Retrieval Augmented Generation)
- **Chat Memory**: MySQL-backed conversation history persistence
- **NL2SQL**: Natural language to SQL conversion with schema understanding

Key AI services:
- `InspectionAgent`: Main intelligent inspection agent with NL2SQL capabilities
- `AIChatService`: Streaming chat with RAG support
- `AIAgentService`: ReAct agent for intelligent system inspection
- `RedisNl2sqlService`: Natural language to SQL conversion service
- `RedisVectorManagementService`: Vector storage and retrieval management

### AI Agent Workflow

The InspectionAgent follows this workflow:

```
User Query → Query Rewrite → Keyword Extraction → Schema Recall → 
SQL Generation → SQL Validation → SQL Execution → Result Visualization → Memory Storage
```

**Key Components:**
- **Query Rewrite**: Enhances query using conversation context
- **Schema Recall**: Retrieves relevant table/column schemas using vector search
- **SQL Generation**: Converts natural language to SQL using LLM
- **SQL Validation**: Security checks to prevent SQL injection
- **Result Visualization**: Automatic chart generation or table display
- **Memory Management**: Stores conversation history for multi-turn dialogue

### API Structure

APIs follow the pattern: `/api/v1/{module}/{resource}`

Main API modules:
- `/api/v1/dih/`: AI chat and agent endpoints
  - `POST /chat` - Intelligent chat interface
  - `POST /chat/stream` - Streaming chat interface
  - `POST /nl2sql/query` - NL2SQL query interface
- `/api/v1/business/asset/`: Asset management
- `/api/v1/business/operation/`: Operational events
- `/api/v1/business/risk/`: Risk management
- `/api/v1/retrieval/`: Data retrieval interfaces
- `/api/v1/system/`: System management

## Git Commit Convention

Follow conventional commits format: `<type>: <description>`

Allowed types:
- `feat`: New feature
- `fix`: Bug fix
- `test`: Test-related changes
- `docs`: Documentation changes (including comments)
- `style`: Code formatting (no logic change)
- `refactor`: Code refactoring
- `chore`: Dependencies, config, build scripts

Description requirements:
- Lowercase first letter, no trailing punctuation
- Under 50 characters, specific and clear

## Key Configuration Files

- `pom.xml`: Maven dependencies and build configuration
- `proguard.cfg`: ProGuard obfuscation rules
- `src/main/resources/application.properties`: Base configuration
- `src/main/resources/application-{profile}.properties`: Profile-specific config
- `src/main/resources/models.yaml`: AI model configurations
- `src/main/resources/agent_prompt/`: Agent prompt templates
- `src/main/resources/schema/`: JSON schemas for data validation

## Development Notes

- The project uses ProGuard for code obfuscation during packaging
- Bean names use full class path (see `UniqueBeanNameGenerator` in Application.java)
- Service layer follows interface-implementation pattern with `impl` subdirectory
- Lombok is used extensively for reducing boilerplate
- Redis is used for both caching and vector storage (RAG)
- ClickHouse is the primary database for time-series data
- AI features require valid OpenAI API key and model configuration
- Vector embeddings are stored in Redis for semantic search

## AI Agent Development

When working with AI agents:

1. **NL2SQL Pipeline**: The `RedisNl2sqlService` handles the complete NL2SQL workflow
2. **Schema Management**: Use `RedisSchemaService` for database schema operations
3. **Vector Storage**: `RedisVectorManagementService` manages vector embeddings
4. **Chat Memory**: `ChatMemory` interface handles conversation persistence
5. **Safety**: Always use `SqlSafeValidator` before executing generated SQL

## Important File Locations

- **AI Agents**: `src/main/java/com/coolxer/service/dih/agent/`
- **Controllers**: `src/main/java/com/coolxer/controller/`
- **Configuration**: `src/main/java/com/coolxer/configuration/`
- **Resources**: `src/main/resources/`
- **Deployment**: `deploy/` directory contains docker-compose and configs

## Testing AI Features

To test AI features locally:
1. Configure OpenAI API key and model names in `application.properties`
2. Ensure Redis is running for vector storage
3. Use the `/api/v1/dih/chat` endpoint for testing
4. Check logs for detailed AI pipeline information

## Documentation

- **README.md**: Project overview and getting started
- **agents.md**: Detailed AI agent architecture documentation
- **CONTRIBUTING.md**: Contribution guidelines
- **API Documentation**: Available at `/swagger-ui/index.html` when running
