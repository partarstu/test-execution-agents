package org.tarik.ta.knowledge_graph;

import java.util.concurrent.atomic.AtomicBoolean;

public class Neo4jMigrationState {
    private final AtomicBoolean succeeded = new AtomicBoolean(false);

    public boolean isSucceeded() {
        return succeeded.get();
    }

    public void markSucceeded() {
        succeeded.set(true);
    }
}
