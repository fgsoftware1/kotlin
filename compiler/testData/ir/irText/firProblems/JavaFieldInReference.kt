// FILE: SourceValue.java

import java.util.Set;

public class SourceValue {
    public final Set<Node> nodes;
}

// FILE: JavaFieldInReference.kt
// WITH_RUNTIME

class Node

fun use(sources: List<SourceValue>): List<Node> {
    return sources.flatMap(SourceValue::nodes)
}