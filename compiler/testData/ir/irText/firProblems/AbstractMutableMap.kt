// WITH_RUNTIME

class MyMap<K : Any, V : Any> : AbstractMutableMap<K, V>() {
    override fun put(key: K, value: V): V? = null

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = mutableSetOf()
}
