# Spring AI Personal MCP Server

A **Spring AI** MCP (Model Context Protocol) server that exposes personal tools for
finding PDF books on your local filesystem.

Works on **macOS**, **Linux**, and **Windows** — accepts all native path formats.

## Tools exposed

| Tool | Description |
|---|---|
| `find_books_by_topic` | Fast search — checks PDF file names only |
| `list_all_books` | Inventory of all PDF files without a topic filter |
| `find_books_deep` | Deep search — reads PDF text (up to 5 pages per file) plus file names |

### Supported file format

`pdf`

---

## Prerequisites

| Requirement | Version |
|---|---|
| Java | 21+ |
| Maven | 3.9+ |

---

## Build

```bash
mvn clean package -DskipTests
```

This produces `target/spring-ai-personal-mcp-server-0.0.1-SNAPSHOT.jar`.

---

## Run (standalone)

```bash
java -jar target/spring-ai-personal-mcp-server-0.0.1-SNAPSHOT.jar
```

The server starts in **STDIO mode** — it reads/writes JSON-RPC over stdin/stdout
and is meant to be launched by an MCP client (Claude Desktop, MCP Inspector, etc.),
not run manually in a terminal.

---

## Test with MCP Inspector

Requires [Node.js 22+](https://nodejs.org). After building the JAR, launch
[MCP Inspector](https://github.com/modelcontextprotocol/inspector):

```bash
npx @modelcontextprotocol/inspector java -jar target/spring-ai-personal-mcp-server-0.0.1-SNAPSHOT.jar
```

Or configure it manually in the Inspector UI (**Transport**: STDIO):

| Field | Value |
|---|---|
| Command | `java` |
| Arguments | `-jar /ABSOLUTE/PATH/TO/spring-ai-personal-mcp-server-0.0.1-SNAPSHOT.jar` |

Open **http://localhost:6274**, click **Connect**, then use the **Tools** tab to
invoke `find_books_by_topic`, `list_all_books`, or `find_books_deep` with a real
`directoryPath` on your machine.

---

## Connect to Claude Desktop

Edit your Claude Desktop config file:

**macOS / Linux**
```
~/.config/claude-desktop/claude_desktop_config.json
```

**Windows**
```
%APPDATA%\Claude\claude_desktop_config.json
```

Add this entry inside the `mcpServers` object:

```json
{
  "mcpServers": {
    "personal-mcp": {
      "command": "java",
      "args": [
        "-jar",
        "/ABSOLUTE/PATH/TO/spring-ai-personal-mcp-server-0.0.1-SNAPSHOT.jar"
      ]
    }
  }
}
```

> **Windows tip:** use forward slashes or escaped back-slashes in the JSON path,
> e.g. `"C:/Users/Alice/spring-ai-personal-mcp-server-0.0.1-SNAPSHOT.jar"`.

Restart Claude Desktop — the three tools will be available automatically.

---

## Example prompts for client

Once connected through MCP clients, you can use prompts like these to invoke the tools:

```
Find all machine learning books in ~/Books
```

```
List every PDF in /Users/alice/Documents/Library
```

```
Deep scan C:\Users\Bob\eBooks for anything about Spring Boot
```

```
Which books about concurrency are in ~/Documents? Search inside the PDFs too.
```

---

## Project structure

```
PersonalMCPSpringAI/
├── pom.xml
└── src/
    └── main/
        ├── java/com/hp/mcpserver/
        │   ├── PersonalMCPSpringAI.java      ← Spring Boot entry point
        │   ├── config/
        │   │   └── MCPServerConfig.java       ← Registers tools with Spring AI
        │   ├── models/
        │   │   ├── BookResult.java            ← Result record
        │   │   └── SearchOptions.java         ← Search configuration
        │   ├── service/
        │   │   ├── BookSearchService.java     ← Core search logic
        │   │   └── ContentExtractor.java      ← PDF text extraction
        │   ├── tools/
        │   │   └── BookFinderTool.java        ← @Tool annotated MCP tools
        │   └── util/
        │       └── PathUtils.java             ← Cross-platform path resolution
        └── resources/
            └── application.yaml
```

---

## Switching to SSE / HTTP mode

If you want an HTTP-based MCP server (e.g. for remote access):

1. Replace the `spring-ai-starter-mcp-server` dependency in `pom.xml` with:
   ```xml
   <dependency>
       <groupId>org.springframework.ai</groupId>
       <artifactId>spring-ai-starter-mcp-server-webflux</artifactId>
   </dependency>
   ```

2. Update `application.yaml`:
   ```yaml
   spring:
     ai:
       mcp:
         server:
           transport: SSE
     main:
       web-application-type: reactive
       banner-mode: console
   server:
     port: 8080
   logging:
     level:
       root: INFO
   ```

3. Point your MCP client at `http://localhost:8080/sse`.

---

## Notes

- **PDF content search** (`find_books_deep`): only the first 5 pages are read per file to keep memory usage bounded.
- **Encrypted PDFs**: files with non-empty passwords are skipped gracefully.
- **Permissions**: directories that are not readable are silently skipped.
- **Logging**: all logging is disabled in STDIO mode to prevent corrupting the JSON-RPC stream. Enable it only in SSE mode.