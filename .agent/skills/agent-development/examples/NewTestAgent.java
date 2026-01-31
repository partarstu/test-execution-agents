package org.tarik.ta;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.tarik.ta.agents.NewActionAgent;
import org.tarik.ta.core.model.DefaultToolErrorHandler;
import org.tarik.ta.core.tools.InheritanceAwareToolProvider;
import org.tarik.ta.dto.NewActionResult;
import org.tarik.ta.tools.NewAgentTools;

import java.util.List;

import static org.tarik.ta.core.model.ModelFactory.createChatModel;

public class NewTestAgent {
    
    public static NewActionAgent createAgent(String systemPrompt) {
        ChatModel chatModel = createChatModel(systemPrompt);
        
        var tools = List.of(
            new NewAgentTools()
            // Add more tool instances as needed
        );
        
        return AiServices.builder(NewActionAgent.class)
                .chatModel(chatModel)
                .toolProvider(new InheritanceAwareToolProvider<>(tools, NewActionResult.class))
                .toolErrorHandler(new DefaultToolErrorHandler())
                .build();
    }
}
