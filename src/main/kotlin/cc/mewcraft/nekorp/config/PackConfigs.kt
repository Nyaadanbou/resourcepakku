package cc.mewcraft.nekorp.config

interface PackConfigs : Iterable<PackConfig> {
    companion object {
        fun of(configs: List<PackConfig>): PackConfigs = PackConfigsImpl(configs)
    }

    val configs: List<PackConfig>

    fun isEmpty(): Boolean = configs.isEmpty()
}

data object EmptyPackConfigs : PackConfigs {
    override val configs: List<PackConfig> = emptyList()
    override fun iterator(): Iterator<PackConfig> = emptyList<PackConfig>().iterator()
}

private data class PackConfigsImpl(override val configs: List<PackConfig>) : PackConfigs {
    override fun iterator(): Iterator<PackConfig> = configs.iterator()
}