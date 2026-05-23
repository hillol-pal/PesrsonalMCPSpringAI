# Book Finder MCP Server

A **Spring AI** MCP (Model Context Protocol) server that 
exposes personal usage tools.

Works on **macOS**, **Linux**, and **Windows** — accepts all native path formats.

## Tools exposed

| Tool | Description |
|---|---|
| `find_books_by_topic` | Fast search — checks file names |
| `list_all_books` | Inventory of all book files without a topic filter |


### Supported file formats
`pdf`,  `txt`, `md`, `epub`, `docx`, `doc`, `rtf`, `odt`
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

This produces `target/xxx.jar`.
 
---

## Run (standalone)

```bash
java -jar target/xxx.jar
```

The server starts in **STDIO mode** — it reads/writes JSON-RPC over stdin/stdout
and is meant to be launched by an MCP client (Claude Desktop, etc.), not run
manually in a terminal.
 
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
        "/ABSOLUTE/PATH/TO/book-finder-mcp-1.0.0.jar"
      ]
    }
  }
}
```

> **Windows tip:** use forward slashes or escaped back-slashes in the JSON path,
> e.g. `"C:/Users/Alice/book-finder-mcp-1.0.0.jar"`.

Restart Claude Desktop — the three tools will be available automatically.
 
---

## Example prompts for client

Once connected through MCP clients, you can use prompts like these to invoke the tools:

```
Find all machine learning books in ~/Books
```

```
List every book in /Users/alice/Documents/Library
```

```
Deep scan C:\Users\Bob\eBooks for anything about Spring Boot
```

```
Which books about concurrency are in ~/Documents? Search inside the files too.
```
 
---

## Project structure

```
book-finder-mcp/
├── pom.xml
└── src/
    └── main/
        ├── java/com/example/bookfinder/
        │   ├── BookFinderMcpApplication.java   ← Spring Boot entry point
        │   ├── config/
        │   │   └── McpToolConfig.java           ← Registers tools with Spring AI
        │   ├── model/
        │   │   ├── BookResult.java              ← Result record
        │   │   └── SearchOptions.java           ← Search configuration
        │   ├── service/
        │   │   ├── BookSearchService.java        ← Core search logic
        │   │   ├── ContentExtractor.java         ← PDF / ePub / TXT text extraction
        │   │   └── PathUtils.java               ← Cross-platform path resolution
        │   └── tool/
        │       └── BookFinderTool.java           ← @Tool annotated MCP tools
        └── resources/
            └── application.properties
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

2. Update `application.properties`:
   ```properties
   spring.ai.mcp.server.transport=SSE
   server.port=8080
   # Re-enable logging for HTTP mode
   logging.level.root=INFO
   spring.main.banner-mode=console
   ```

3. Point your MCP client at `http://localhost:8080/sse`.
---

## Notes

- **PDF content search**: only the first 30 pages are read per file to keep memory usage bounded.
- **Encrypted PDFs**: files with non-empty passwords are skipped gracefully.
- **Permissions**: directories that are not readable are silently skipped.
- **Logging**: all logging is disabled in STDIO mode to prevent corrupting the JSON-RPC stream. Enable it only in SSE mode.