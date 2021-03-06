/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.tracing.opentracing;

import java.util.concurrent.Callable;

import org.apache.cxf.tracing.Traceable;
import org.apache.cxf.tracing.TracerContext;

import io.opentracing.ActiveSpan;
import io.opentracing.ActiveSpan.Continuation;
import io.opentracing.Tracer;

public class OpenTracingContext implements TracerContext {
    private final Tracer tracer;
    private final Continuation continuation;

    public OpenTracingContext(final Tracer tracer) {
        this(tracer, null);
    }

    public OpenTracingContext(final Tracer tracer, final Continuation continuation) {
        this.tracer = tracer;
        this.continuation = continuation;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ActiveSpan startSpan(final String description) {
        return newOrChildSpan(description, null);
    }

    @Override
    public <T> T continueSpan(final Traceable<T> traceable) throws Exception {
        ActiveSpan scope = null;
        
        if (tracer.activeSpan() == null && continuation != null) {
            scope = continuation.activate();
        }

        try {
            return traceable.call(new OpenTracingContext(tracer));
        } finally {
            if (continuation != null && scope != null) {
                scope.deactivate();
            }
        }
    }

    @Override
    public <T> Callable<T> wrap(final String description, final Traceable<T> traceable) {
        final Callable<T> callable = new Callable<T>() {
            @Override
            public T call() throws Exception {
                return traceable.call(new OpenTracingContext(tracer));
            }
        };

        // Carry over parent from the current thread
        final ActiveSpan parent = tracer.activeSpan();
        return () -> {
            try (ActiveSpan span = newOrChildSpan(description, parent)) {
                return callable.call();
            }
        };
    }

    @Override
    public void annotate(String key, String value) {
        final ActiveSpan current = tracer.activeSpan();
        if (current != null) {
            current.setTag(key, value);
        }
    }

    @Override
    public void timeline(String message) {
        final ActiveSpan current = tracer.activeSpan();
        if (current != null) {
            current.log(message);
        }
    }
    
    private ActiveSpan newOrChildSpan(final String description, final ActiveSpan parent) {
        if (parent == null) {
            return tracer.buildSpan(description).startActive();
        } else {
            return tracer.buildSpan(description).asChildOf(parent).startActive();
        }
    }
}
