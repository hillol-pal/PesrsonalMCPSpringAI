package com.hp.mcpserver.config;

import com.hp.mcpserver.tools.BookFinderTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MCPServerConfig {

    @Bean
    ToolCallbackProvider toolCallBackProvider(BookFinderTool tool) {
        return MethodToolCallbackProvider
                .builder()
                .toolObjects(tool)
                .build();
    }

}
